package fr.heav.eresia.rocketparty.commands;

import fr.heav.eresia.rocketparty.RocketParty;
import fr.heav.eresia.rocketparty.gamemanager.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LeaveGameCommand implements SubCommand {
    private RocketParty plugin;
    public LeaveGameCommand(RocketParty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getDescription() {
        return "Leave une game";
    }
    @Override
    public String getHelp() {
        return "leave [user that should leave the game]";
    }
    @Override
    public String getPermission() {
        return "firematch.leaveMatch";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player target;
        String targetName;
        if (!(sender instanceof Player) && args.length < 2) {
            sender.sendMessage(ChatColor.RED + "You must specify who should leave the game");
            return true;
        }
        if (args.length >= 2) {
            if (!sender.hasPermission("firematch.leaveMatch.someoneElse")) {
                sender.sendMessage(ChatColor.RED + "You do not have the permission to make someone leave a game");
                return true;
            }
            target = sender.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Could not find player "+args[1]);
                return true;
            }
            targetName = target.getName();
        }
        else {
            target = (Player)sender;
            targetName = "You";
        }
        GameManager gameManager = GameManager.getPlayerGameManager(target);
        if (gameManager == null) {
            sender.sendMessage(ChatColor.RED + targetName + " is not in any game");
            return true;
        }

        switch (gameManager.removeParticipant(target)) {
            case Left:
                sender.sendMessage(ChatColor.WHITE + "" + ChatColor.BOLD + targetName + ChatColor.RESET + ChatColor.WHITE + " has successfully left the game");
                break;
            case PlayerNotInGame:
                sender.sendMessage(ChatColor.RED + targetName + " is not in the game");
                break;
        }

        return true;
    }
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 2) {
            return null;
        }

        return new ArrayList<>();
    }
}
