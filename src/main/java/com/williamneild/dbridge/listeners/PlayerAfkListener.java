package com.williamneild.dbridge.listeners;

import com.williamneild.dbridge.DBridge;
import com.williamneild.dbridge.events.ServerUtilitiesPlayerAfkEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerAfkListener {

    private final DBridge dbridge;

    public PlayerAfkListener(DBridge dbridge) {
        this.dbridge = dbridge;
    }

    @SubscribeEvent
    public void onPlayerAfkStatusChange(ServerUtilitiesPlayerAfkEvent event) {
        String playerName = event.player.getDisplayName();
        this.dbridge.sendEmbedToDiscord(
            playerName,
            event.isAfk ? String.format("%s is AFK", playerName) : String.format("%s is no longer AFK", playerName),
            DBridge.GREY);
    }
}
