package com.williamneild.dbridge.listeners;

import net.minecraftforge.event.ServerChatEvent;

import com.williamneild.dbridge.DBridge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerChatListener {

    private final DBridge dbridge;

    public PlayerChatListener(DBridge dbridge) {
        this.dbridge = dbridge;
    }

    /**
     * Listens for chat messages and relays them to Discord
     *
     * @param event The chat event
     */
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        String sender = event.username;
        String message = event.message;
        FMLCommonHandler.instance()
            .getFMLLogger()
            .info("Chat event received: <{}> {}", sender, message);
        this.dbridge.sendToDiscord(sender, message);
    }
}
