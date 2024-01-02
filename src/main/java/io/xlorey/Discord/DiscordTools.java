package io.xlorey.Discord;

import com.google.gson.JsonObject;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;
import zombie.network.GameServer;
import zombie.network.ServerOptions;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

import static io.xlorey.Discord.Main.*;

/**
 * Tools for sending messages to Discord
 */
@SuppressWarnings("deprecation")
public class DiscordTools {
    public static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static Map<String, ScheduledFuture<?>> disconnectTimers = new ConcurrentHashMap<>();
    public static long serverStartTime;

    private static Random random = new Random();


    /**
     * Send a chat message as a bot
     * @param content Message text
     * @return Mono<Void> method is void, must call block to send
     */
    public static Mono<Void> sendMessage(String content) {
        Snowflake channelId = Snowflake.of(Main.getDefaultConfig().getString("chatID"));
        return gateway.getChannelById(channelId)
                .ofType(MessageChannel.class)
                .flatMap(channel -> channel.createMessage(content))
                .then();
    }

    /**
     * Retrieves random message text from a list by key
     */
    public static String getMessageText(String key) {
        List<Object> messageList = Main.getDefaultConfig().getList(key);
        if (messageList == null || messageList.isEmpty()) {
            return "";
        }

        return (String) messageList.get(random.nextInt(messageList.size()));
    }

    /**
     * Removes surrogate characters from the given text.
     * @param text The input text containing surrogate characters.
     * @return A new string with surrogate characters removed.
     */
    public static String removeSmilesAndImages(String text) {
        StringBuilder builder = new StringBuilder();
        char[] chars = text.toCharArray();
        int length = chars.length;

        for(int index = 0; index < length; ++index) {
            Character character = chars[index];
            if (!Character.isLowSurrogate(character) && !Character.isHighSurrogate(character)) {
                builder.append(character);
            }
        }

        return builder.toString();
    }

    /**
     * Send embed message to chat
     * @param embedBuilder Message structure
     * @return Mono<Void> method is void, must call block to send
     */
    public static Mono<Void> sendEmbed(EmbedCreateSpec.Builder embedBuilder) {
        if (!isBotReady) {
            return Mono.empty();
        }

        EmbedCreateSpec embed = embedBuilder
                .title(getDefaultConfig().getString("translation.embedTitle"))
                .url(getDefaultConfig().getString("botSettings.titleURL"))
                .timestamp(Instant.now())
                .build();

        Snowflake channelId = Snowflake.of(Main.getDefaultConfig().getString("chatID"));
        return gateway.getChannelById(channelId)
                .ofType(MessageChannel.class)
                .flatMap(channel -> channel.createMessage(MessageCreateSpec.builder()
                        .addEmbed(embed)
                        .build()))
                .then();
    }

    /**
     * Update chat header information
     */
    public static void updateTopicTitle() {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String formattedDate = dateTime.format(formatter);

        long currentTime = System.nanoTime();
        long timeDelta = currentTime - serverStartTime;
        long timeDeltaInMinutes = TimeUnit.NANOSECONDS.toMinutes(timeDelta);

        String topicText = Main.getDefaultConfig().getString("translation.topicText");
        topicText = topicText.replace("<CURRENT_PLAYER>", String.valueOf(GameServer.getPlayerCount()));
        topicText = topicText.replace("<MAX_PLAYER>", String.valueOf(ServerOptions.getInstance().getMaxPlayers()));
        topicText = topicText.replace("<SERVER_WORK_TIME>", String.valueOf(timeDeltaInMinutes));
        topicText = topicText.replace("<UPDATE_TIME>", formattedDate);

        Snowflake channelId = Snowflake.of(Main.getDefaultConfig().getString("chatID"));

        String finalTopicText = topicText;

        gateway.getChannelById(channelId)
                .ofType(TextChannel.class)
                .flatMap(channel -> channel.edit(spec -> spec.setTopic(finalTopicText)))
                .subscribe();
    }

    /**
     * Clear chat header
     */
    public static void clearTopicTitle() {
        Snowflake channelId = Snowflake.of(Main.getDefaultConfig().getString("chatID"));
        gateway.getChannelById(channelId)
                .ofType(TextChannel.class)
                .flatMap(channel -> channel.edit(spec -> spec.setTopic("")))
                .subscribe();
    }

    /**
     * Send a chat message via webhook
     * @param content Message text
     * @param username sender name
     */
    public static void sendWebhookMessage(String content, String username) {
        int id = Math.abs(username.hashCode()) % 100 + 1;
        String imageUrl = "https://picsum.photos/id/" + id + "/200/300";

        JsonObject json = new JsonObject();
        json.addProperty("content", content);
        json.addProperty("username", username);
        json.addProperty("avatar_url", imageUrl);

        String jsonString = json.toString();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Main.getDefaultConfig().getString("webHookToken")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonString))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(System.out::println)
                .join();
    }
}
