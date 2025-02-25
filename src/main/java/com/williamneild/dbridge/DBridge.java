package com.williamneild.dbridge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommandManager;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.williamneild.dbridge.commands.RelayReplyCommand;
import com.williamneild.dbridge.listeners.PlayerAchievementListener;
import com.williamneild.dbridge.listeners.PlayerAfkListener;
import com.williamneild.dbridge.listeners.PlayerChatListener;
import com.williamneild.dbridge.listeners.PlayerDeathListener;
import com.williamneild.dbridge.listeners.PlayerJoinLeaveListener;
import com.williamneild.dbridge.listeners.PlayerQuestListener;
import com.williamneild.dbridge.listeners.ServerTickListener;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import it.unimi.dsi.fastutil.Pair;

@Mod(
    modid = DBridge.MOD_ID,
    version = DBridge.MOD_VERSION,
    name = DBridge.MOD_NAME,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class DBridge {

    public static final String MOD_ID = "dbridge";
    public static final String MOD_NAME = "DBridge";
    public static final String MOD_VERSION = Tags.VERSION;
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    public static final int GREEN = 5763719;
    public static final int BLUE = 3447003;
    public static final int RED = 15548997;
    public static final int GOLD = 15844367;
    public static final int PURPLE = 10181046;
    public static final int GREY = 9807270;

    private boolean isEnabled = true;
    private MinecraftServer server;
    private Relay relay;

    @Mod.EventHandler
    public void onPreInitialization(FMLPreInitializationEvent event) {
        if (event.getSide()
            .isClient()) {
            DBridge.LOG.error("This mod is server-side only");
            isEnabled = false;
            return;
        }

        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        DBridge.LOG.info("I am " + DBridge.MOD_NAME + " v" + DBridge.MOD_VERSION);

        // only register listeners if the configuration is valid
        if (Config.botToken.isEmpty() || Config.guildId.isEmpty() || Config.channelId.isEmpty()) {
            DBridge.LOG.error("Configuration is missing required values");
            isEnabled = false;
        }
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        if (!isEnabled) return;
        this.server = event.getServer();

        try {
            this.relay = new Relay(this::sendToMinecraft, this::getPlayerListCommandResponse);
        } catch (Exception e) {
            DBridge.LOG.error("Error while creating relay", e);
            isEnabled = false;
            return;
        }

        // add listeners
        MinecraftForge.EVENT_BUS.register(new PlayerChatListener(this));
        MinecraftForge.EVENT_BUS.register(new PlayerAchievementListener(this, this.server.getConfigurationManager()));
        MinecraftForge.EVENT_BUS.register(new PlayerDeathListener(this));
        MinecraftForge.EVENT_BUS.register(new PlayerQuestListener(this));
        MinecraftForge.EVENT_BUS.register(new PlayerAfkListener(this));
        FMLCommonHandler.instance()
            .bus()
            .register(new PlayerJoinLeaveListener(this));
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerTickListener(this));

        // register commands
        event.registerServerCommand(new RelayReplyCommand(this.relay));

        this.wrapCommandManager();
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        if (!isEnabled) return;
        DBridge.LOG.info("Server started");
        this.relay.sendEmbedToDiscord("Server", "Server started", DBridge.BLUE);
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        if (!isEnabled) return;
        DBridge.LOG.info("Server stopping");
        this.relay.sendEmbedToDiscord("Server", "Server stopping", DBridge.BLUE);
    }

    @Mod.EventHandler
    public void onServerStopped(FMLServerStoppedEvent event) {
        if (!isEnabled) return;
        DBridge.LOG.info("Server stopped");
        this.relay.sendEmbedToDiscord("Server", "Server stopped", DBridge.BLUE);
    }

    public void initCommands() {
        this.relay.initCommands();
    }

    private void wrapCommandManager() {
        // wrap the command manager so we can get events from certain commands such as /say
        ICommandManager commandManager = this.server.getCommandManager();
        if (commandManager instanceof ServerCommandManager serverCommandManager) {
            this.server.commandManager = new ServerCommandManagerWrapper(
                serverCommandManager,
                this.relay::sendToDiscord);
            DBridge.LOG.info("Wrapped command manager");
        } else {
            DBridge.LOG.error("Failed to wrap command manager");
        }
    }

    public static class MinecraftChatMessage {

        public String content;
        public String sender = "";
        public String discordId = "";
        public String replyAuthor = "";
        public List<Pair<String, String>> attachments = new ArrayList<>();

        public MinecraftChatMessage(String content) {
            this.content = content;
        }
    }

    /**
     * Sends a chat message to minecraft
     *
     * @param message The message to send to Minecraft
     */
    public void sendToMinecraft(MinecraftChatMessage message) {
        IChatComponent rootMessage = new ChatComponentText("");

        // sender
        if (!message.sender.isEmpty()) {
            IChatComponent senderComponent = new ChatComponentText("[" + message.sender)
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_PURPLE));

            if (!message.replyAuthor.isEmpty()) {
                IChatComponent replyComponent = new ChatComponentText(" (Reply to " + message.replyAuthor + ")")
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY));
                senderComponent = senderComponent.appendSibling(replyComponent);
            }

            senderComponent = senderComponent
                .appendSibling(new ChatComponentText("]").setChatStyle(senderComponent.getChatStyle()));
            rootMessage = rootMessage.appendSibling(senderComponent);
        }

        // main message
        if (!message.content.isEmpty()) {
            String[] messageLines = message.content.split("\n");
            for (int i = 0; i < messageLines.length; i++) {
                String line = messageLines[i];
                IChatComponent messageComponent = new ChatComponentText(" " + line).setChatStyle(new ChatStyle());

                if (!message.discordId.isEmpty()) {
                    String suggestedCommand = String.format("/relayReply %s ", message.discordId);
                    String hoverText = "Click to reply";
                    ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestedCommand);
                    HoverEvent hoverEvent = new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText(hoverText));
                    ChatStyle chatStyle = messageComponent.getChatStyle()
                        .setChatClickEvent(clickEvent)
                        .setChatHoverEvent(hoverEvent);
                    messageComponent.setChatStyle(chatStyle);
                }

                rootMessage.appendSibling(messageComponent);

                if ((messageLines.length > 1) && (i != messageLines.length - 1)) {
                    this.server.getConfigurationManager()
                        .sendChatMsgImpl(rootMessage, false);
                    rootMessage = new ChatComponentText("");
                }
            }
        }

        // attachments
        if (!message.attachments.isEmpty()) {
            for (int i = 0; i < message.attachments.size(); i++) {
                Pair<String, String> attachment = message.attachments.get(i);
                ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.OPEN_URL, attachment.second());
                HoverEvent hoverEvent = new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentText("View Attachment"));
                ChatStyle chatStyle = new ChatStyle().setColor(EnumChatFormatting.GOLD)
                    .setChatClickEvent(clickEvent)
                    .setChatHoverEvent(hoverEvent);
                IChatComponent attachmentComponent = new ChatComponentText(String.format(" <%s>", attachment.first()))
                    .setChatStyle(chatStyle);

                rootMessage.appendSibling(attachmentComponent);
            }
        }

        this.server.getConfigurationManager()
            .sendChatMsgImpl(rootMessage, false);
    }

    public void sendToDiscord(String playerName, String content) {
        this.relay.sendToDiscord(playerName, content);
    }

    public void sendEmbedToDiscord(String sender, String content, int color) {
        this.relay.sendEmbedToDiscord(sender, content, color);
    }

    public void setActivity(String message) {
        this.relay.setActivity(message);
    }

    public String getPlayerListCommandResponse() {
        Integer playerCount = this.server.getCurrentPlayerCount();
        Integer maxPlayers = this.server.getMaxPlayers();
        String[] players = this.server.getConfigurationManager()
            .getAllUsernames();
        return String
            .format("There are %d/%d players online:\n%s", playerCount, maxPlayers, String.join(", ", players));
    }

    public void processPlayerJoin(String playerName) {
        this.relay.sendEmbedToDiscord(playerName, String.format("*%s joined the game*", playerName), DBridge.GREEN);

        Integer playerCount = this.server.getCurrentPlayerCount();
        Integer maxPlayers = this.server.getMaxPlayers();
        String message = String.format("Minecraft with %d/%d players", playerCount, maxPlayers);

        this.setActivity(message);
    }

    public void processPlayerLeave(String playerName) {
        this.relay.sendEmbedToDiscord(playerName, String.format("*%s left the game*", playerName), DBridge.RED);

        Integer playerCount = this.server.getCurrentPlayerCount() - 1;
        Integer maxPlayers = this.server.getMaxPlayers();
        String message = String.format("Minecraft with %d/%d players", playerCount, maxPlayers);

        this.setActivity(message);
    }
}
