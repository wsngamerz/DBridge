package com.williamneild.dbridge.listeners;

import java.util.IllegalFormatException;

import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.AchievementEvent;

import com.williamneild.dbridge.DBridge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerAchievementListener {

    private final DBridge dbridge;
    private final ServerConfigurationManager serverConfigurationManager;

    public PlayerAchievementListener(DBridge dbridge, ServerConfigurationManager serverConfigurationManager) {
        this.dbridge = dbridge;
        this.serverConfigurationManager = serverConfigurationManager;
    }

    @SubscribeEvent
    public void onAchievement(AchievementEvent event) {
        Achievement achievement = event.achievement;
        StatisticsFile statisticsFile = serverConfigurationManager.func_152602_a(event.entityPlayer);

        boolean hasRequirements = statisticsFile.canUnlockAchievement(achievement);
        boolean alreadyObtained = statisticsFile.hasAchievementUnlocked(achievement);
        if (!hasRequirements || alreadyObtained) return;

        String name = event.entityPlayer.getDisplayName();
        String title = achievement.func_150951_e()
            .getUnformattedText();
        String description = getAchievementDescription(achievement);

        this.dbridge.sendEmbedToDiscord(
            name,
            String.format("*%s has earned the achievement [%s]: %s*", name, title, description),
            DBridge.PURPLE);
    }

    private String getAchievementDescription(Achievement achievement) {
        String translated = StatCollector.translateToLocal(achievement.achievementDescription);
        try {
            if (achievement.statId.equals("achievement.openInventory")) {
                return String.format(translated, 'E');
            }
        } catch (IllegalFormatException ignored) {}
        return translated;
    }
}
