package fr.heav.eresia.eresiafirematchevent.commands;

import fr.heav.eresia.eresiafirematchevent.EresiaFireMatchEvent;
import fr.heav.eresia.eresiafirematchevent.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StopCommand implements SubCommand {
    private EresiaFireMatchEvent plugin;
    public StopCommand(EresiaFireMatchEvent plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getDescription() {
        return "Stop the game";
    }
    @Override
    public String getHelp() {
        return "stop <game name>";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("firematch.stopGame")) {
            sender.sendMessage(ChatColor.RED + "You do not have the permission to stop the game");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "You must specify the game name");
            return true;
        }
        GameManager gameManager = plugin.loadGame(args[1]);
        if (gameManager == null) {
            sender.sendMessage(ChatColor.RED + "This game doesn't exist");
            return true;
        }

        switch (gameManager.stopGame()) {
            case Stopped:
                sender.sendMessage(ChatColor.WHITE + "The game has been stopped");
                break;
            case AlreadyStopped:
                sender.sendMessage(ChatColor.RED + "You cannot stop this game");
                break;
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 2) {
            return plugin.getGameNames();
        }
        return new ArrayList<>();
    }
}
