package com.williamneild.dbridge.events;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.eventhandler.Event;

public class ServerUtilitiesPlayerAfkEvent extends Event {

    public final EntityPlayerMP player;
    public final boolean isAfk;

    public ServerUtilitiesPlayerAfkEvent(EntityPlayerMP player, boolean isAfk) {
        super();
        this.player = player;
        this.isAfk = isAfk;
    }
}
