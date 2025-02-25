package com.williamneild.dbridge.listeners;

import com.williamneild.dbridge.DBridge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ServerTickListener {

    private final DBridge dbridge;
    private int delayTimer = 20 * 5;

    public ServerTickListener(DBridge dbridge) {
        this.dbridge = dbridge;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) return;

        this.handleDelayedCommands();
    }

    /**
     * Initializes commands after a certain delay.
     */
    private void handleDelayedCommands() {
        if (this.delayTimer <= 0) return;
        this.delayTimer--;

        if (this.delayTimer == 0) this.dbridge.initCommands();
    }
}
