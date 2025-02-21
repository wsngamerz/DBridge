package com.williamneild.dbridge;

import java.util.function.BiConsumer;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.ServerCommandManager;

/**
 * Wraps the command manager so we can intercept and listen for certain commands
 */
public class ServerCommandManagerWrapper extends ServerCommandManager {

    private final ServerCommandManager delegate;
    private final BiConsumer<String, String> sendToDiscord;

    public ServerCommandManagerWrapper(ServerCommandManager delegate, BiConsumer<String, String> sendToDiscord) {
        this.delegate = delegate;
        this.sendToDiscord = sendToDiscord;
    }

    @Override
    public int executeCommand(ICommandSender commandSender, String command) {
        int result = this.delegate.executeCommand(commandSender, command);

        if (result == 1 && (command.startsWith("/say") || command.startsWith("say"))) {
            DBridge.LOG.debug("Intercepted /say command: {}", command);
            String sender = commandSender.getCommandSenderName();
            String message = command.substring(command.indexOf(' ') + 1);
            this.sendToDiscord.accept(sender, message);
        }
        return result;
    }
}
