package com.williamneild.dbridge.commands;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

import com.williamneild.dbridge.Relay;

public class RelayReplyCommand extends CommandBase {

    private final Relay relay;

    public RelayReplyCommand(Relay relay) {
        this.relay = relay;
    }

    @Override
    public String getCommandName() {
        return "relayReply";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList(new String[] {"rr"});
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/relayReply <messageLink> <message>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return super.canCommandSenderUseCommand(sender)
            || (sender instanceof EntityPlayerMP && getRequiredPermissionLevel() <= 0);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) throw new CommandException("Invalid number of arguments");

        String message = Stream.of(args)
            .skip(1)
            .collect(Collectors.joining(" "));
        relay.reply(args[0], sender.getCommandSenderName(), message);
    }
}
