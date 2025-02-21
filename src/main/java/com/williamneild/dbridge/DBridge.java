package com.williamneild.dbridge;

import java.lang.reflect.Field;

import net.minecraft.command.ICommandManager;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.williamneild.dbridge.listeners.PlayerAchievementListener;
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
    public void onServerStarting(FMLServerStartingEvent event) throws InterruptedException {
        if (!isEnabled) return;
        this.server = event.getServer();
        this.relay = new Relay(this::sendToMinecraft, this::getPlayerListCommandResponse);

        // add listeners
        MinecraftForge.EVENT_BUS.register(new PlayerChatListener(this));
        MinecraftForge.EVENT_BUS.register(new PlayerAchievementListener(this, this.server.getConfigurationManager()));
        MinecraftForge.EVENT_BUS.register(new PlayerDeathListener(this));
        FMLCommonHandler.instance()
            .bus()
            .register(new PlayerJoinLeaveListener(this));
        FMLCommonHandler.instance()
            .bus()
            .register(new PlayerQuestListener(this));
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerTickListener(this));

        this.wrapCommandManager();
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        if (!isEnabled) return;
        DBridge.LOG.info("Server started");
        this.relay.sendToDiscord("Server started");
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        if (!isEnabled) return;
        DBridge.LOG.info("Server stopping");
        this.relay.sendToDiscord("Server stopping");
    }

    @Mod.EventHandler
    public void onServerStopped(FMLServerStoppedEvent event) {
        if (!isEnabled) return;
        DBridge.LOG.info("Server stopped");
        this.relay.sendToDiscord("Server stopped");
    }

    public void initCommands() {
        this.relay.initCommands();
    }

    private void wrapCommandManager() {
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

    /**
     * Sends a chat message to minecraft
     *
     * @param content The message to send to Minecraft
     */
    public void sendToMinecraft(String content) {
        ChatComponentText chatMessage = new ChatComponentText(content);
        this.server.getConfigurationManager()
            .sendChatMsgImpl(chatMessage, false);
    }

    public void sendToDiscord(String playerName, String content) {
        this.relay.sendToDiscord(playerName, content);
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
        this.sendToDiscord(playerName, String.format("*%s joined the game*", playerName));

        Integer playerCount = this.server.getCurrentPlayerCount();
        Integer maxPlayers = this.server.getMaxPlayers();
        String message = String.format("Minecraft with %d/%d players", playerCount, maxPlayers);

        this.setActivity(message);
    }

    public void processPlayerLeave(String playerName) {
        this.sendToDiscord(playerName, String.format("*%s left the game*", playerName));

        Integer playerCount = this.server.getCurrentPlayerCount() - 1;
        Integer maxPlayers = this.server.getMaxPlayers();
        String message = String.format("Minecraft with %d/%d players", playerCount, maxPlayers);

        this.setActivity(message);
    }
}
