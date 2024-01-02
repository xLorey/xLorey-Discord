package io.xlorey.Discord;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import io.xlorey.FluxLoader.plugin.Configuration;
import io.xlorey.FluxLoader.plugin.Plugin;
import io.xlorey.FluxLoader.shared.EventManager;
import io.xlorey.FluxLoader.utils.Logger;
import lombok.Getter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Plugin entry point
 */
public class Main extends Plugin {
    /**
     * -- GETTER --
     *  Returns an instance of the current Main class.
     *  This method is used to access a single instance of the Main class,
     *  implementing the Singleton pattern.
     * @return An instance of the Main class.
     */
    @Getter
    private static Main instance;

    public static volatile GatewayDiscordClient gateway;
    public static volatile boolean isBotReady = false;
    public static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * Returns the default configuration for the plugin.
     * This method provides access to the configuration object that is used
     * in the plugin. It returns the configuration associated with the current Main instance.
     * @return A Configuration object representing the plugin configuration.
     */
    public static Configuration getDefaultConfig() {
        return getInstance().getConfig();
    }

    /**
     * Initializing the plugin
     */
    @Override
    public void onInitialize() {

        instance = this;

        EventManager.subscribe(new EventHandler());

        saveDefaultConfig();
    }

    /**
     * Enabling the plugin
     */
    @Override
    public void onExecute() {
        executorService.submit(this::loadDiscordBot);
    }

    /**
     * Disabling the plugin
     */
    @Override
    public void onTerminate() {
        EventHandler.onServerShutdownHandler();

        // Shut down the Discord bot if it is running
        if (gateway != null) {
            try {
                gateway.logout().block();
                Logger.printLog("Discord bot has been successfully terminated.");
            } catch (Exception e) {
                Logger.printLog("Error while terminating Discord bot: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Stopping ExecutorService
        try {
            Logger.printLog("Shutting down executor service...");
            executorService.shutdown();
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            Logger.printLog("Interrupted during shutting down the executor service.");
        }
    }

    /**
     * Loading a bot by creating a new thread
     */
    private void loadDiscordBot() {
        try {
            Logger.printLog("Initializing Discord bot...");

            CommandsTools.loadCommands();

            DiscordClient client = DiscordClient.create(getConfig().getString("botToken"));
            gateway = client.login().block();

            if (gateway != null) {
                Logger.printLog("Discord bot has been successfully initialized!");

                isBotReady = true;

                gateway.on(MessageCreateEvent.class).subscribe(event -> {
                    String content = event.getMessage().getContent();
                    if (content.equals("!ping")) {
                        event.getMessage().getChannel().subscribe(channel -> channel.createMessage("Pong!").subscribe());
                    }
                });

                gateway.onDisconnect().block();
            } else {
                Logger.printLog("Failed to login to Discord");
            }
        } catch (Exception e) {
            Logger.printLog("Error when trying to load Discord bot: " + e.getMessage());
            e.printStackTrace();
        }
    }
}