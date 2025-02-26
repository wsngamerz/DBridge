package com.williamneild.dbridge.mixins;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.williamneild.dbridge.events.ServerUtilitiesPlayerAfkEvent;

import serverutils.handlers.ServerUtilitiesServerEventHandler;

@Mixin(ServerUtilitiesServerEventHandler.class)
public class MixinServerUtilitiesAfk {

    @Inject(
        method = "onServerTick",
        at = @At(
            value = "INVOKE",
            // new MessageUpdateTabName(Collections.singleton(forgePlayer)).sendToAll();
            target = "Lserverutils/net/MessageUpdateTabName;sendToAll()V",
            shift = At.Shift.AFTER,
            remap = false),
        remap = false)
    private void afterPlayerAfkSendAll(CallbackInfo ci, @Local(name = "player") EntityPlayerMP player,
        @Local(name = "isAFK") boolean isAfk) {
        MinecraftForge.EVENT_BUS.post(new ServerUtilitiesPlayerAfkEvent(player, isAfk));
    }
}
