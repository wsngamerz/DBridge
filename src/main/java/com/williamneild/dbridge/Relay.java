package com.williamneild.dbridge;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.vdurmont.emoji.EmojiParser;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import it.unimi.dsi.fastutil.Pair;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import serverutils.ServerUtilitiesLeaderboards;
import serverutils.data.Leaderboard;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.Universe;
import serverutils.lib.util.StringUtils;

public class Relay extends ListenerAdapter {

    private final Consumer<DBridge.MinecraftChatMessage> sendToMinecraft;
    private final Supplier<String> getPlayerListCommandResponse;

    private final JDA jda;
    private WebhookClient webhookClient;
    private final Guild guild;
    private GuildMessageChannel channel;

    Set<Command.Choice> leaderboardChoices = new HashSet<>();

    public Relay(Consumer<DBridge.MinecraftChatMessage> sendToMinecraft, Supplier<String> getPlayerListCommandResponse)
        throws InterruptedException {
        this.sendToMinecraft = sendToMinecraft;
        this.getPlayerListCommandResponse = getPlayerListCommandResponse;

        class Ipv4OnlyDns implements Dns {

            @NotNull
            @Override
            public List<InetAddress> lookup(@NotNull String hostname) throws UnknownHostException {
                List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
                return addresses.stream()
                    .filter(addr -> addr instanceof java.net.Inet4Address) // Only keep IPv4 addresses
                    .collect(Collectors.toList());
            }
        }

        // custom http client for JDA to increase timeouts
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS);

        // optionally filter results to use just ipv4 addresses
        if (Config.forceIPv4) {
            httpClientBuilder.dns(new Ipv4OnlyDns());
        }

        OkHttpClient httpClient = httpClientBuilder.build();

        DBridge.LOG.info("Logging in to Discord");
        EnumSet<GatewayIntent> intents = EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
        this.jda = JDABuilder.createLight(Config.botToken, intents)
            .setHttpClient(httpClient)
            .setAutoReconnect(true)
            .addEventListeners(this)
            .setActivity(Activity.playing("Minecraft"))
            .build();

        // output the ping to the log
        jda.getRestPing()
            .queue(ping -> DBridge.LOG.info("Logged in with ping: {}", ping));

        // wait for the bot to be logged in
        jda.awaitReady();
        DBridge.LOG.info("Logged in to Discord");

        // get the guild and channel
        this.guild = this.jda.getGuildById(Config.guildId);
        if (this.guild == null) {
            DBridge.LOG.error("Guild not found");
            return;
        }

        this.channel = this.guild.getTextChannelById(Config.channelId);
        if (this.channel == null) {
            DBridge.LOG.error("Channel not found");
            return;
        }

        // create the webhook client if the webhook url is set
        if (!Config.webhookUrl.isEmpty()) {
            WebhookClientBuilder builder = new WebhookClientBuilder(Config.webhookUrl);
            builder.setThreadFactory((job) -> {
                Thread thread = new Thread(job);
                thread.setName("DiscordWebhookClient");
                thread.setDaemon(true);
                return thread;
            });
            builder.setWait(true);
            this.webhookClient = builder.build();
        }
    }

    /**
     * Initialize the commands
     * This has to be separate since server utilities doesn't populate the leaderboards until after the server has
     * started
     */
    public void initCommands() {
        DBridge.LOG.info("Registering leaderboard commands");

        // find all the registered leaderboard types and add them to the set
        ServerUtilitiesLeaderboards.LEADERBOARDS.forEach((key, value) -> {
            String k = key.toString();
            leaderboardChoices.add(new Command.Choice(k, k));
        });

        DBridge.LOG.info("Leaderboards: {}", ServerUtilitiesLeaderboards.LEADERBOARDS);
        DBridge.LOG.info("Choices: {}", leaderboardChoices);

        this.guild.updateCommands()
            .addCommands(
                Commands.slash("leaderboard", "Show the leaderboard")
                    .addOptions(
                        new OptionData(OptionType.STRING, "type", "The type of leaderboard")
                            .addChoices(leaderboardChoices)
                            .setRequired(true)),
                Commands.slash("list", "Show the players online"))
            .queue();
    }

    /**
     * Message received event from the Discord bot
     *
     * @param event The message received event
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor()
            .isBot()) return;

        Message message = event.getMessage();
        MessageType type = message.getType();
        List<Message.Attachment> attachments = message.getAttachments();

        if (type != MessageType.DEFAULT && type != MessageType.INLINE_REPLY) return;

        // create the chat message object
        String parsedMessage = EmojiParser.parseToAliases(message.getContentDisplay());
        DBridge.MinecraftChatMessage chatMessage = new DBridge.MinecraftChatMessage(parsedMessage);
        chatMessage.discordId = message.getId();

        // author name (with replies)
        chatMessage.sender = message.getAuthor()
            .getEffectiveName();
        if (type == MessageType.INLINE_REPLY) {
            chatMessage.replyAuthor = getReplyAuthorName(message);
        }

        // add the attachment types to the message
        for (Message.Attachment attachment : attachments) {
            String typeString = attachment.isImage() ? "Image" : (attachment.isVideo() ? "Video" : "File");
            chatMessage.attachments.add(Pair.of(typeString, attachment.getUrl()));
        }

        this.sendToMinecraft.accept(chatMessage);
    }

    @NotNull
    private static String getReplyAuthorName(Message message) {
        Message referencedMessage = message.getReferencedMessage();
        String replyAuthorName;

        if (referencedMessage == null) {
            replyAuthorName = "Unknown";
        } else if (referencedMessage.isWebhookMessage()) {
            User replyAuthor = referencedMessage.getAuthor();
            replyAuthorName = replyAuthor.getName();
        } else {
            User replyAuthor = referencedMessage.getAuthor();
            replyAuthorName = replyAuthor.getEffectiveName();
        }

        return replyAuthorName;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String eventName = event.getName();
        if (eventName.equals("leaderboard")) {
            if (leaderboardChoices.isEmpty()) {
                event.reply("Leaderboards are not available yet")
                    .queue();
                return;
            }

            OptionMapping type = event.getOption("type");
            if (type == null) return;
            String typeString = type.getAsString();

            ResourceLocation leaderboardResource = new ResourceLocation(typeString);
            Leaderboard leaderboard = ServerUtilitiesLeaderboards.LEADERBOARDS.get(leaderboardResource);
            List<ForgePlayer> players = new ArrayList<>(
                Universe.get()
                    .getPlayers());
            players.sort(leaderboard.getComparator());

            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append("Leaderboard: ")
                .append(
                    leaderboard.getTitle()
                        .getUnformattedText())
                .append("\n");
            for (int i = 0; i < players.size(); i++) {
                ForgePlayer p = players.get(i);
                String value = "- #" + StringUtils.add0s(i + 1, players.size())
                    + " "
                    + p.getName()
                    + ": "
                    + leaderboard.createValue(p)
                        .getUnformattedText();
                messageBuilder.append(value)
                    .append("\n");
            }

            event.reply(messageBuilder.toString())
                .queue();
        } else if (eventName.equals("list")) {
            event.reply(this.getPlayerListCommandResponse.get())
                .queue();
        }
    }

    public void setActivity(String activity) {
        this.jda.getPresence()
            .setActivity(Activity.playing(activity));
    }

    public void sendEmbedToDiscord(String sender, String message, Integer color) {
        String avatarUrl = "https://mineskin.eu/avatar/" + sender;
        if (sender.equals("Server")) avatarUrl = "https://mineskin.eu/avatar/MHF_Exclamation";

        WebhookEmbedBuilder embedBuilder = new WebhookEmbedBuilder().setColor(color)
            .setDescription(message);
        WebhookMessageBuilder messageBuilder = new WebhookMessageBuilder().addEmbeds(embedBuilder.build())
            .setUsername(sender)
            .setAvatarUrl(avatarUrl);
        this.webhookClient.send(messageBuilder.build());
    }

    public void sendToDiscord(String sender, String message) {
        if (this.webhookClient == null) return;
        DBridge.LOG.debug("MC -> DC[w]: '{}': {}", sender, message);

        // set the avatar url to the player's skin, override for the server
        String avatarUrl = "https://mineskin.eu/avatar/" + sender;
        if (sender.equals("Server")) avatarUrl = "https://mineskin.eu/avatar/MHF_Exclamation";

        WebhookMessageBuilder webhookMessage = new WebhookMessageBuilder().setUsername(sender)
            .setAvatarUrl(avatarUrl)
            .setContent(message);

        this.webhookClient.send(webhookMessage.build());
    }

    public void reply(String messageId, String sender, String message) {
        String avatarUrl = "https://mineskin.eu/avatar/" + sender;
        if (sender.equals("Server")) avatarUrl = "https://mineskin.eu/avatar/MHF_Exclamation";

        try {
            Message replyeeMessage = this.channel.retrieveMessageById(messageId)
                .complete();
            User replyeeAuthor = replyeeMessage.getAuthor();
            WebhookMessageBuilder webhookMessage = new WebhookMessageBuilder().setUsername(sender)
                .setAvatarUrl(avatarUrl)
                .setContent(
                    String.format(
                        "> <@%s> https://discord.com/channels/%s/%s/%s \n%s",
                        replyeeAuthor.getId(),
                        this.guild.getId(),
                        this.channel.getId(),
                        messageId,
                        message));

            this.webhookClient.send(webhookMessage.build());

            DBridge.MinecraftChatMessage minecraftChatMessage = new DBridge.MinecraftChatMessage(message);
            minecraftChatMessage.replyAuthor = replyeeAuthor.getEffectiveName();
            minecraftChatMessage.sender = sender;
            this.sendToMinecraft.accept(minecraftChatMessage);
        } catch (Exception e) {
            DBridge.LOG.error(e);
        }
    }
}
