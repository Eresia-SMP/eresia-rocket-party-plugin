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

public class AddSpawnCommand implements SubCommand {
    private RocketParty plugin;
    public AddSpawnCommand(RocketParty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getDescription() {
        return "Ajoute un spawner";
    }
    @Override
    public String getHelp() {
        return "addspawn <game name>";
    }
    @Override
    public String getPermission() {
        return "firematch.addSpawn";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command");
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

        Player player = (Player)sender;
        gameManager.getSettings().addRespawnLocation(player.getLocation());
        sender.sendMessage(ChatColor.WHITE + "The spawnpoint has been added");

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
