package com.williamneild.dbridge.listeners;

import com.williamneild.dbridge.DBridge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;

public class PlayerJoinLeaveListener {

    private final DBridge dbridge;

    public PlayerJoinLeaveListener(DBridge dbridge) {
        this.dbridge = dbridge;
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerLoggedInEvent event) {
        String name = event.player.getDisplayName();
        this.dbridge.processPlayerJoin(name);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerLoggedOutEvent event) {
        String name = event.player.getDisplayName();
        this.dbridge.processPlayerLeave(name);
    }
}
