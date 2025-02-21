package com.williamneild.dbridge.listeners;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.CombatTracker;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.williamneild.dbridge.DBridge;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerDeathListener {

    private final DBridge dbridge;

    public PlayerDeathListener(DBridge dbridge) {
        this.dbridge = dbridge;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        final EntityLivingBase living = event.entityLiving;
        if (!isRealPlayer(living)) return;

        CombatTracker tracker = living.func_110142_aN();
        if (tracker == null) return;

        IChatComponent deathMessageComponent = tracker.func_151521_b();
        if (deathMessageComponent == null) return;

        String deathMessage = deathMessageComponent.getUnformattedText();
        String target = getName(living);
        this.dbridge.sendToDiscord(target, String.format("*%s*", deathMessage));
    }

    private boolean isRealPlayer(@Nullable Entity entity) {
        if (!(entity instanceof EntityPlayerMP player)) return false;
        if (player instanceof FakePlayer) return false;
        return player.playerNetServerHandler != null;
    }

    private String getName(Entity entity) {
        if (entity instanceof EntityPlayer) {
            return ((EntityPlayer) entity).getDisplayName();
        }
        if (entity instanceof EntityLiving living) {
            if (living.hasCustomNameTag()) return living.getCustomNameTag();
        }
        return entity.getCommandSenderName();
    }
}
