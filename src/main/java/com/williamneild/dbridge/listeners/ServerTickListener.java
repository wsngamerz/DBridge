package com.williamneild.dbridge.listeners;

import com.williamneild.dbridge.DBridge;

import cpw.mods.fml.common.FMLCommonHandler;
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
        if (this.delayTimer <= 0) return;
        if (event.phase != TickEvent.Phase.END) return;
        this.delayTimer--;

        if (this.delayTimer == 0) {
            this.dbridge.initCommands();
            FMLCommonHandler.instance()
                .bus()
                .unregister(this);
        }
    }
}
