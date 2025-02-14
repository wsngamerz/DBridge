package com.williamneild.dbridge;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static String botToken;
    public static String guildId;
    public static String channelId;
    public static String webhookUrl;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        botToken = configuration.getString("botToken", Configuration.CATEGORY_GENERAL, "", "The discord bot's token");
        guildId = configuration.getString("guildId", Configuration.CATEGORY_GENERAL, "", "The id of the guild to connect to");
        channelId = configuration.getString("channelId", Configuration.CATEGORY_GENERAL, "", "The id of the channel to bridge between");
        webhookUrl = configuration.getString("webhookUrl", Configuration.CATEGORY_GENERAL, "", "The webhook url to send messages to. If this is blank, the bot will be used instead");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
