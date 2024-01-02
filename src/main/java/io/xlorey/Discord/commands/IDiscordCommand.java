package io.xlorey.Discord.commands;

/**
 * Chat command interface
 */
public interface IDiscordCommand {
    /**
     * Implementation of functionality when calling a command
     * @param args command arguments
     */
    void onInvoke(String[] args);
}