package io.xlorey.Discord;

import io.xlorey.Discord.commands.IDiscordCommand;
import io.xlorey.Discord.commands.ServerInfoCommand;
import io.xlorey.FluxLoader.utils.Logger;

import java.util.HashMap;

/**
 * Handler of entered commands
 */
public class CommandsTools {
    public static char commandChar = '$';

    /**
     * List of all commands
     */
    public static HashMap<String, IDiscordCommand> commandsList = new HashMap<>();

    /**
     * Initializing commands
     */
    public static void loadCommands() {
        commandsList.put("serverinfo", new ServerInfoCommand());
    }

    /**
     * Adding a command to scope
     * @param commandName command name
     * @param command command implementation
     */
    public static void addCommand(String commandName, IDiscordCommand command) {
        if (commandsList.containsKey(commandName)) {
            Logger.printSystem(String.format("[DiscordCommands] Duplicate command '%s' detected. Skipping...", commandName));
            return;
        }
        commandsList.put(commandName, command);
    }
    /**
     * Calling a command
     * @param command command name
     * @param args argument
     */
    public static void invokeCommand(String command, String[] args) {
        if (!commandsList.containsKey(command))
            return;

        IDiscordCommand commandFunction = commandsList.get(command);

        commandFunction.onInvoke(args);
    }
}