package com.williamneild.dbridge;

import java.lang.reflect.Field;
import java.util.IllegalFormatException;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import net.minecraft.command.ICommandManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.CombatTracker;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AchievementEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.events.QuestEvent;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

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

    private MinecraftServer server;
    private Relay relay;

    // delay some tasks until after the server has started
    private int delayTimer = 20 * 10; // 10 seconds
    private Boolean enabled = false;

    public DBridge() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onPreInitialization(FMLPreInitializationEvent event) {
        if (event.getSide()
            .isClient()) return;

        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        DBridge.LOG.info("I am " + DBridge.MOD_NAME + " v" + DBridge.MOD_VERSION);

        if (Config.botToken.isEmpty() || Config.guildId.isEmpty() || Config.channelId.isEmpty()) {
            DBridge.LOG.error("Configuration is missing required values");
            this.enabled = false;
        } else {
            this.enabled = true;
        }
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) throws InterruptedException {
        if (!this.enabled) {
            DBridge.LOG.error("Plugin is disabled due to missing configuration");
            return;
        }

        this.server = event.getServer();
        this.relay = new Relay(this::sendToMinecraft, this::getPlayerListCommandResponse);

        // wrap the command manager so we can get events from certain commands such as /say
        ICommandManager commandManager = this.server.getCommandManager();
        if (commandManager instanceof ServerCommandManager serverCommandManager) {
            ServerCommandManagerWrapper wrapper = new ServerCommandManagerWrapper(
                serverCommandManager,
                this.relay::sendToDiscord);

            try {
                Field commandManagerField = MinecraftServer.class.getDeclaredField("commandManager");
                commandManagerField.setAccessible(true);
                commandManagerField.set(this.server, wrapper);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                DBridge.LOG.error("Failed to wrap command manager", e);
            }

            DBridge.LOG.info("Wrapped command manager");
        } else {
            DBridge.LOG.error("Failed to wrap command manager");
        }
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        if (!this.enabled) return;
        DBridge.LOG.info("Server started");
        this.relay.sendToDiscord("Server started");
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        if (!this.enabled) return;
        this.relay.sendToDiscord("Server stopping");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (!this.enabled) return;
        if (this.delayTimer <= 0) return;
        this.delayTimer--;

        if (this.delayTimer == 0) {
            this.updatePlayerListActivity();
            this.relay.initCommands();
        }
    }

    /**
     * Listens for chat messages and relays them to Discord
     *
     * @param event The chat event
     */
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!this.enabled) return;
        String sender = event.username;
        String message = event.message;
        this.relay.sendToDiscord(sender, message);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!this.enabled) return;
        String name = event.player.getDisplayName();
        this.relay.sendToDiscord(name, String.format("*%s joined the game*", name));
        this.updatePlayerListActivity();
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!this.enabled) return;
        String name = event.player.getDisplayName();
        this.relay.sendToDiscord(name, String.format("*%s left the game*", name));
        this.updatePlayerListActivity();
    }

    @SubscribeEvent
    public void onAchievement(AchievementEvent event) {
        if (!this.enabled) return;
        Achievement achievement = event.achievement;
        StatisticsFile statisticsFile = server.getConfigurationManager()
            .func_152602_a(event.entityPlayer);
        boolean hasRequirements = statisticsFile.canUnlockAchievement(achievement);
        boolean alreadyObtained = statisticsFile.hasAchievementUnlocked(achievement);
        if (hasRequirements && !alreadyObtained) {
            String name = event.entityPlayer.getDisplayName();
            String title = achievement.func_150951_e()
                .getUnformattedText();
            String description = getAchievementDescription(achievement);
            this.relay.sendToDiscord(name, String.format("*%s has earned the achievement [%s]*", name, title));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!this.enabled) return;
        final EntityLivingBase living = event.entityLiving;
        if (!isRealPlayer(living)) return;

        CombatTracker tracker = living.func_110142_aN();
        if (tracker == null) return;

        IChatComponent deathMessageComponent = tracker.func_151521_b();
        if (deathMessageComponent == null) return;

        String deathMessage = deathMessageComponent.getUnformattedText();
        String target = getName(living);
        // Entity sourceEntity = event.source.getSourceOfDamage();
        // String source = sourceEntity != null ? getName(sourceEntity) : null;
        this.relay.sendToDiscord(target, String.format("*%s*", deathMessage));
    }

    @SubscribeEvent
    public void onQuestEvent(QuestEvent event) {
        if (!this.enabled) return;
        if (event.getType() != QuestEvent.Type.COMPLETED) return;

        UUID playerID = event.getPlayerID();
        EntityPlayerMP player = QuestingAPI.getPlayer(playerID);
        if (player == null) return;

        Set<UUID> questIDs = event.getQuestIDs();
        for (UUID questID : questIDs) {
            IQuest quest = QuestDatabase.INSTANCE.get(questID);
            if (quest == null) continue;

            // Skip silent quests
            if (quest.getProperty(NativeProps.SILENT)) continue;

            // get quest name and strip any formatting codes
            String questName = quest.getProperty(NativeProps.NAME);
            questName = questName.replaceAll("§[0-9a-fk-or]", "");
            if (questName.isEmpty()) continue;

            String message = String.format("%s has completed the quest [%s]", player.getDisplayName(), questName);
            this.relay.sendToDiscord(player.getDisplayName(), String.format("*%s*", message));
            this.sendToMinecraft(message);
        }
    }

    /**
     * Sends a chat message to minecraft
     *
     * @param content The message to send to Minecraft
     */
    public void sendToMinecraft(String content) {
        DBridge.LOG.info("DC -> MC: {}", content);

        ChatComponentText chatMessage = new ChatComponentText(content);
        this.server.getConfigurationManager()
            .sendChatMsgImpl(chatMessage, false);
    }

    public String getPlayerListCommandResponse() {
        Integer playerCount = this.server.getCurrentPlayerCount();
        Integer maxPlayers = this.server.getMaxPlayers();
        String[] players = this.server.getConfigurationManager()
            .getAllUsernames();
        return String
            .format("There are %d/%d players online:\n%s", playerCount, maxPlayers, String.join(", ", players));
    }

    public void updatePlayerListActivity() {
        Integer playerCount = this.server.getCurrentPlayerCount();
        Integer maxPlayers = this.server.getMaxPlayers();
        String message = String.format("Minecraft with %d/%d players", playerCount, maxPlayers);

        this.relay.setActivity(message);
    }

    /**
     * Wraps the command manager so we can intercept and listen for certain commands
     */
    public static class ServerCommandManagerWrapper extends ServerCommandManager {

        private final ServerCommandManager delegate;
        private final BiConsumer<String, String> sendToDiscord;

        public ServerCommandManagerWrapper(ServerCommandManager delegate, BiConsumer<String, String> sendToDiscord) {
            this.delegate = delegate;
            this.sendToDiscord = sendToDiscord;
        }

        @Override
        public int executeCommand(ICommandSender commandSender, String command) {
            int result = this.delegate.executeCommand(commandSender, command);

            if (result == 1 && (command.startsWith("/say") || command.startsWith("say"))) {
                DBridge.LOG.debug("Intercepted /say command: {}", command);
                String sender = commandSender.getCommandSenderName();
                String message = command.substring(command.indexOf(' ') + 1);
                this.sendToDiscord.accept(sender, message);
            }
            return result;
        }
    }

    private boolean isRealPlayer(@Nullable Entity entity) {
        if (!(entity instanceof EntityPlayerMP)) return false;
        EntityPlayerMP player = (EntityPlayerMP) entity;
        if (player instanceof FakePlayer) return false;
        return player.playerNetServerHandler != null;
    }

    private String getName(Entity entity) {
        if (entity instanceof EntityPlayer) {
            return ((EntityPlayer) entity).getDisplayName();
        }
        if (entity instanceof EntityLiving) {
            EntityLiving living = (EntityLiving) entity;
            if (living.hasCustomNameTag()) return living.getCustomNameTag();
        }
        return entity.getCommandSenderName();
    }

    private String getAchievementDescription(Achievement achievement) {
        try {
            Field achievementDescriptionField = Achievement.class.getDeclaredField("achievementDescription");
            achievementDescriptionField.setAccessible(true);
            String translated = StatCollector.translateToLocal(
                achievementDescriptionField.get(achievement)
                    .toString());
            try {
                if (achievement.statId.equals("achievement.openInventory")) {
                    return String.format(translated, 'E');
                }
            } catch (IllegalFormatException ignored) {}
            return translated;
        } catch (NoSuchFieldException e) {
            return "Err: field not found";
        } catch (IllegalAccessException e) {
            return "Err: access denied";
        }
    }
}
