package io.xlorey.Discord.commands;


import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import io.xlorey.Discord.DiscordTools;
import io.xlorey.Discord.Main;
import io.xlorey.FluxLoader.plugin.Configuration;
import zombie.core.znet.SteamUtils;
import zombie.network.GameServer;
import zombie.network.ServerOptions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Displaying information about the server
 */
public class ServerInfoCommand implements IDiscordCommand{
    @Override
    public void onInvoke(String[] args) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String formattedDate = dateTime.format(formatter);

        DiscordTools.updateTopicTitle();

        long currentTime = System.nanoTime();
        long timeDelta = currentTime - DiscordTools.serverStartTime;
        long timeDeltaInMinutes = TimeUnit.NANOSECONDS.toMinutes(timeDelta);

        Configuration config = Main.getDefaultConfig();
        String serverName = config.getString("translation.fieldServerNameValue");
        String serverWorkTime = String.format("%s %s", timeDeltaInMinutes, config.getString("translation.commands.serverInfo.serverWorkTimeValueSuffix"));
        String maxPlayers = String.valueOf(ServerOptions.getInstance().getMaxPlayers());
        String serverIP = config.getString("translation.fieldIPValue");
        String serverPort = config.getString("translation.fieldPORTValue");
        String avatarURL = config.getString("botSettings.avatarURL");

        EmbedCreateSpec.Builder statEmbed = EmbedCreateSpec.builder()
                .color(Color.DEEP_SEA)
                .description(DiscordTools.getMessageText("translation.commands.serverInfo.title"))
                .addField(config.getString("translation.commands.serverInfo.dateField"), formattedDate, false)
                .addField(config.getString("translation.fieldServerName"), serverName, false)
                .addField(config.getString("translation.fieldCurrentPlayers"), String.valueOf(GameServer.getPlayerCount()), false)
                .addField(config.getString("translation.fieldMaxPlayers"), maxPlayers, false)
                .addField(config.getString("translation.fieldServerMode"), SteamUtils.isSteamModeEnabled() ? "Steam" : "No Steam", false)
                .addField(config.getString("translation.commands.serverInfo.serverWorkTimeField"), serverWorkTime, false)
                .addField(config.getString("translation.fieldIP"), serverIP, true)
                .addField(config.getString("translation.fieldPORT"), serverPort, true)
                .footer(DiscordTools.getMessageText("translation.commands.serverInfo.footer"), avatarURL);

        DiscordTools.sendEmbed(statEmbed).block();
    }
}