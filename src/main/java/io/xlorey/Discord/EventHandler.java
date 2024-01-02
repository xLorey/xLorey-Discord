package io.xlorey.Discord;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.*;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import io.xlorey.FluxLoader.annotations.SubscribeEvent;
import io.xlorey.FluxLoader.plugin.Configuration;
import io.xlorey.FluxLoader.utils.Logger;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.chat.ChatBase;
import zombie.chat.ChatMessage;
import zombie.core.logger.LoggerManager;
import zombie.core.logger.ZLogger;
import zombie.core.raknet.UdpConnection;
import zombie.core.znet.SteamUtils;
import zombie.network.GameServer;
import zombie.network.ServerOptions;
import zombie.network.chat.ChatServer;
import zombie.network.chat.ChatType;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static io.xlorey.Discord.Main.gateway;
import static io.xlorey.Discord.Main.isBotReady;

/**
 * Server Event Handler
 */
public class EventHandler {
    private final Set<String> punishedPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Handling a player death event
     * @param character deceased player character
     */
    @SubscribeEvent(eventName="OnCharacterDeath")
    public void onPlayerDeathHandler(IsoGameCharacter character) {
        if (!(character instanceof IsoPlayer player)) return;

        IsoPlayer attacker = (character.getAttackedBy() instanceof IsoPlayer) ? (IsoPlayer) character.getAttackedBy() : null;
        String key = (attacker == null) ? "translation.playerDeath" : "translation.playerDeathPVP";

        String deathText = DiscordTools.getMessageText(key)
                .replace("<USERNAME>", player.getUsername())
                .replace("<ATTACKER>", attacker != null ? attacker.getUsername() : "");

        if (deathText.isEmpty()) return;

        EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder()
                .color(Color.RUBY)
                .timestamp(Instant.now())
                .footer(DiscordTools.getMessageText("translation.deathFooter"), Main.getDefaultConfig().getString("botSettings.avatarURL"))
                .description(deathText);

        DiscordTools.sendEmbed(embedBuilder).block();
    }

    /**
     * Handling the player connection event
     * @param playerData player information
     * @param playerConnection connection information
     * @param username player nickname
     */
    @SubscribeEvent(eventName = "onPlayerConnect")
    public void onPlayerConnectHandler(ByteBuffer playerData, UdpConnection playerConnection, String username) {
        ScheduledFuture<?> disconnectTask = DiscordTools.disconnectTimers.get(playerConnection.username);
        if (disconnectTask != null && !disconnectTask.isDone()) {
            disconnectTask.cancel(false);
        } else {
            DiscordTools.updateTopicTitle();

            String connectText = DiscordTools.getMessageText("translation.playerConnect").replace("<USERNAME>", playerConnection.username);

            if (connectText.isEmpty()) return;

            EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder()
                    .color(Color.SEA_GREEN)
                    .description(connectText)
                    .footer(DiscordTools.getMessageText("translation.connectFooter"), Main.getDefaultConfig().getString("botSettings.avatarURL"));

            DiscordTools.sendEmbed(embed).block();
        }
    }

    /**
     * Handling a player exit event
     * @param playerInstance player instance
     * @param playerConnection connection information
     */
    @SubscribeEvent(eventName="onPlayerDisconnect")
    public void onPlayerDisconnectHandler(IsoPlayer playerInstance, UdpConnection playerConnection){
        if (isPunishedPlayer(playerConnection.username)) {
            executeDisconnectActions(playerConnection);

            ScheduledFuture<?> disconnectTask = DiscordTools.disconnectTimers.get(playerConnection.username);
            if (disconnectTask != null && !disconnectTask.isDone()) {
                disconnectTask.cancel(false);
            }
        } else {
            ScheduledFuture<?> disconnectTask = DiscordTools.scheduler.schedule(
                    () -> executeDisconnectActions(playerConnection),
                    Main.getDefaultConfig().getInt("disconnectWindowTime"), TimeUnit.SECONDS
            );

            DiscordTools.disconnectTimers.put(playerConnection.username, disconnectTask);
        }
    }

    /**
     * Performs actions related to disconnecting a player.
     * Sends a notification to Discord and updates the topic title.
     * @param playerConnection Player connection information.
     */
    private void executeDisconnectActions(UdpConnection playerConnection) {
        DiscordTools.updateTopicTitle();

        String disconnectText = DiscordTools.getMessageText("translation.playerDisconnect")
                .replace("<USERNAME>", playerConnection.username);

        if (disconnectText.isEmpty()) return;

        EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder()
                .color(Color.RUBY)
                .description(disconnectText)
                .footer(DiscordTools.getMessageText("translation.disconnectFooter"),
                        Main.getDefaultConfig().getString("botSettings.avatarURL"));

        DiscordTools.sendEmbed(embed).block();
    }

    /**
     * Checks if the player has been kicked or banned.
     * Depending on your implementation, this may include checking
     * global status or list of kicked/banned players.
     * @param username Player username.
     * @return Returns true if the player was kicked or banned.
     */
    private boolean isPunishedPlayer(String username) {
        boolean isPunishment = punishedPlayers.contains(username);

        punishedPlayers.remove(username);

        return isPunishment;
    }

    /**
     * Handling a game chat message event
     * @param chatBase game chat
     * @param chatMessage message
     */
    @SubscribeEvent(eventName="onChatServerMessage")
    public void onChatServerMessageHandler(ChatBase chatBase, ChatMessage chatMessage){
        if (chatBase.getType() != ChatType.general)
            return;

        String text = chatMessage.getText().replace("@", "");

        DiscordTools.sendWebhookMessage(text, chatMessage.getAuthor());
    }

    /**
     * Handling a player kick event
     */
    @SubscribeEvent(eventName="onPlayerKick")
    public void onPlayerKickHandler(IsoPlayer player, String reason) {
        if (reason.isEmpty()) reason = "-";

        punishedPlayers.add(player.username);

        String kickText = DiscordTools.getMessageText("translation.playerKick")
                .replace("<USERNAME>", player.username)
                .replace("<REASON>", reason);

        if (kickText.isEmpty()) return;

        EmbedCreateSpec.Builder kickEmbed = EmbedCreateSpec.builder()
                .color(Color.RED)
                .description(kickText)
                .footer(DiscordTools.getMessageText("translation.punishmentFooter"), Main.getDefaultConfig().getString("botSettings.avatarURL"));
        DiscordTools.sendEmbed(kickEmbed).block();
    }

    /**
     * Handling a player ban event
     */
    @SubscribeEvent(eventName="onPlayerBan")
    public void onPlayerBanHandler(IsoPlayer player, String reason) {
        if (reason.isEmpty()) reason = "-";

        punishedPlayers.add(player.username);

        String banText = DiscordTools.getMessageText("translation.playerBan")
                .replace("<USERNAME>", player.username)
                .replace("<REASON>", reason);

        if (banText.isEmpty()) return;

        EmbedCreateSpec.Builder banEmbed = EmbedCreateSpec.builder()
                .color(Color.RED)
                .description(banText)
                .footer(DiscordTools.getMessageText("translation.punishmentFooter"), Main.getDefaultConfig().getString("botSettings.avatarURL"));
        DiscordTools.sendEmbed(banEmbed).block();
    }

    /**
     * Handling the server initialization event
     */
    @SubscribeEvent(eventName="onServerInitialize")
    public void onServerInitializeHandler() {
        DiscordTools.serverStartTime = System.nanoTime();
        if (!isBotReady) return;

        DiscordTools.updateTopicTitle();

        Configuration config = Main.getDefaultConfig();
        String serverName = config.getString("translation.fieldServerNameValue");
        String maxPlayers = String.valueOf(ServerOptions.getInstance().getMaxPlayers());
        String serverIP = config.getString("translation.fieldIPValue");
        String serverPort = config.getString("translation.fieldPORTValue");
        String avatarURL = config.getString("botSettings.avatarURL");
        String chatID = config.getString("chatID");

        EmbedCreateSpec.Builder initEmbed = EmbedCreateSpec.builder()
                .color(Color.SEA_GREEN)
                .description(DiscordTools.getMessageText("translation.initServerTitle"))
                .addField(config.getString("translation.fieldServerName"), serverName, false)
                .addField(config.getString("translation.fieldCurrentPlayers"), String.valueOf(GameServer.getPlayerCount()), false)
                .addField(config.getString("translation.fieldMaxPlayers"), maxPlayers, false)
                .addField(config.getString("translation.fieldServerMode"), SteamUtils.isSteamModeEnabled() ? "Steam" : "No Steam", false)
                .addField(config.getString("translation.fieldIP"), serverIP, true)
                .addField(config.getString("translation.fieldPORT"), serverPort, true)
                .footer(DiscordTools.getMessageText("translation.initServerFooter"), avatarURL);

        DiscordTools.sendEmbed(initEmbed).block();

        gateway.on(MessageCreateEvent.class).subscribe(event -> {
            Message message = event.getMessage();

            if (!event.getMessage().getChannelId().asString().equals(chatID)) {
                return;
            }
            event.getMessage().getAuthorAsMember().subscribe(member -> {
                if (message.getContent().startsWith(Main.getDefaultConfig().getString("botSettings.commandsChar"))) {
                    if (Objects.requireNonNull(member.getBasePermissions().block()).contains(Permission.ADMINISTRATOR) || Main.getDefaultConfig().getBoolean("botSettings.commandAllowForUser")) {
                        String chatCommand = message.getContent();
                        String[] commandArgs = chatCommand.split("\\s+");
                        if (commandArgs.length == 0 || commandArgs[0].isEmpty()) {
                            return;
                        }

                        String commandName = commandArgs[0];
                        commandName = commandName.substring(1);

                        if(commandName.isEmpty()) {
                            return;
                        }

                        commandArgs[0] = commandName.toLowerCase();

                        CommandsTools.invokeCommand(commandName, commandArgs);
                    }
                    return;
                }

                if (event.getMessage().getAuthor().map(User::isBot).orElse(false) || event.getMessage().getWebhookId().isPresent()) {
                    return;
                }
                String username = event.getMessage().getAuthor().get().getUsername();
                String messageContent = event.getMessage().getContent();

                messageContent = DiscordTools.removeSmilesAndImages(messageContent)
                        .replace("<", "")
                        .replace(">", "");

                if (messageContent.isEmpty()) messageContent = "*** pointedly silent ***";

                ZLogger discordLogger = LoggerManager.getLogger("DiscordChat");
                Logger.printLog(discordLogger, String.format("%s: %s", username, messageContent));
                String chatText = config.getString("translation.chatFromDiscord")
                        .replace("<SPACE_SYMBOL>", "\u200B")
                        .replace("<USERNAME>", username)
                        .replace("<TEXT>", messageContent);

                ChatServer.getInstance().sendMessageToServerChat(chatText);
            });
        });
    }

    /**
     * Handling a server shutdown event
     */
    public static void onServerShutdownHandler() {
        if (isBotReady && gateway != null) {
            DiscordTools.clearTopicTitle();
            EmbedCreateSpec.Builder shutdownEmbed = EmbedCreateSpec.builder()
                    .color(Color.RED)
                    .description(DiscordTools.getMessageText("translation.shutdownServerTitle"))
                    .footer(DiscordTools.getMessageText("translation.shutdownServerFooter"), Main.getDefaultConfig().getString("botSettings.avatarURL"));
            DiscordTools.sendEmbed(shutdownEmbed).block();
        }
    }
}
