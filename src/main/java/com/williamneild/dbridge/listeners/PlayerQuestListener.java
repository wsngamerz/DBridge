package com.williamneild.dbridge.listeners;

import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.williamneild.dbridge.DBridge;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.events.QuestEvent;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerQuestListener {

    private final DBridge dbridge;

    public PlayerQuestListener(DBridge dbridge) {
        this.dbridge = dbridge;
    }

    @SubscribeEvent
    public void onQuestEvent(QuestEvent event) {
        // only interested in quest completion events
        if (event.getType() != QuestEvent.Type.COMPLETED) return;

        UUID playerID = event.getPlayerID();
        EntityPlayerMP player = QuestingAPI.getPlayer(playerID);
        if (player == null) return;

        Set<UUID> questIDs = event.getQuestIDs();
        for (UUID questID : questIDs) {
            IQuest quest = QuestDatabase.INSTANCE.get(questID);
            if (quest == null) continue;

            // Skip silent quests
            if (quest.getProperty(NativeProps.SILENT)) continue;

            // get quest name and strip any formatting codes
            String questName = quest.getProperty(NativeProps.NAME);
            questName = questName.replaceAll("§[0-9a-fk-or]", "");
            if (questName.isEmpty()) continue;

            // send quest completion message to both Discord and Minecraft chats
            String message = String.format("%s has completed the quest [%s]", player.getDisplayName(), questName);
            this.dbridge.sendToDiscord(player.getDisplayName(), String.format("*%s*", message));
            this.dbridge.sendToMinecraft(message);
        }
    }
}
