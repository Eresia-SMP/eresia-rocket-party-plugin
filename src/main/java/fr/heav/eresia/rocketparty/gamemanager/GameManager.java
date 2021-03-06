package fr.heav.eresia.rocketparty.gamemanager;

import fr.heav.eresia.rocketparty.RocketParty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scoreboard.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GameManager implements Listener {
    GameSettings settings;

    private static class RespawningPlayer {
        public BossBar timerBossBar;
        public int bossBarRefreshTask;
        public int timerEndTask;
    }
    private static Map<Player, GameManager> playerGameManagerMap = new HashMap<>();

    public static @Nullable GameManager getPlayerGameManager(@NotNull Player player) {
        return playerGameManagerMap.get(player);
    }

    private RocketParty plugin;
    private final Map<Player, Participant> participants = new HashMap<>();
    private final Random random = new Random();
    private boolean isStarted = false;
    private boolean isEnded = false;
    private final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    private Objective killsObjs;
    private final ItemStack firework;
    private final ItemStack crossBow;
    private Map<Player, RespawningPlayer> respawningPlayers = new HashMap<>();

    public boolean getIsStarted() {
        return isStarted;
    }
    public boolean getIsEnded() {
        return isEnded;
    }

    public GameManager(RocketParty plugin, String name) {
        this.plugin = plugin;
        this.settings = new GameSettings(plugin, name);

        recreateKillsObjective();

        firework = new ItemStack(Material.FIREWORK_ROCKET, 10);
        FireworkMeta fireworkMeta = (FireworkMeta) firework.getItemMeta();
        for (int i = 0; i < 10; i++) {
            fireworkMeta.addEffect(FireworkEffect.builder()
                    .flicker(false)
                    .trail(false)
                    .withColor(Color.WHITE)
                    .withFade(Color.WHITE)
                    .with(FireworkEffect.Type.BALL)
                    .build());
        }
        fireworkMeta.setPower(2);
        firework.setItemMeta(fireworkMeta);

        crossBow = new ItemStack(Material.CROSSBOW, 1);
        CrossbowMeta crossbowMeta = (CrossbowMeta) crossBow.getItemMeta();
        crossbowMeta.addEnchant(Enchantment.QUICK_CHARGE, 3, true);
        crossbowMeta.setUnbreakable(true);
        crossbowMeta.addChargedProjectile(firework.clone());
        crossBow.setItemMeta(crossbowMeta);
    }
    private void recreateKillsObjective() {
        if (killsObjs != null)
            killsObjs.unregister();
        killsObjs = scoreboard.registerNewObjective("kills", "THISISPLAYERKILLS", Component.text("Kills"));
        killsObjs.setDisplaySlot(DisplaySlot.SIDEBAR);
    }
    private void broadcastMessage(Component message) {
        for (Player player : participants.keySet()) {
            player.sendMessage(message);
        }
    }
    private void resetRespawningPlayers() {
        for (Map.Entry<Player, RespawningPlayer> player : respawningPlayers.entrySet()) {
            player.getValue().timerBossBar.removeAll();
            Bukkit.getScheduler().cancelTask(player.getValue().bossBarRefreshTask);
            Bukkit.getScheduler().cancelTask(player.getValue().timerEndTask);
        }
        respawningPlayers.clear();
    }
    private void resetARespawningPlayers(Player player) {
        if (!respawningPlayers.containsKey(player))
            return;
        RespawningPlayer rp = respawningPlayers.remove(player);
        rp.timerBossBar.removeAll();
        Bukkit.getScheduler().cancelTask(rp.bossBarRefreshTask);
        Bukkit.getScheduler().cancelTask(rp.timerEndTask);
    }

    public @NotNull GameSettings getSettings() {
        return settings;
    }

    public @NotNull ParticipantJoinResult addParticipant(@NotNull Player player, boolean shouldJoinIfGameIsStarted) {
        if (isEnded)
            return ParticipantJoinResult.GameInEndScene;
        if (isStarted && !shouldJoinIfGameIsStarted)
            return ParticipantJoinResult.GameStarted;
        if (participants.containsKey(player))
            return ParticipantJoinResult.PlayerAlreadyInGame;
        if (playerGameManagerMap.containsKey(player)) {
            GameManager oldGameManager = playerGameManagerMap.remove(player);
            oldGameManager.removeParticipant(player);
        }
        playerGameManagerMap.put(player, this);

        Team participantTeam = scoreboard.registerNewTeam(player.getName().substring(0,Math.min(player.getName().length()-1, 15)));
        participantTeam.setAllowFriendlyFire(false);
        participantTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OTHER_TEAMS);
        participantTeam.addEntry(player.getName());
        player.setScoreboard(scoreboard);
        Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(1.0);
        Participant participant = new Participant(player, participantTeam);
        killsObjs.getScore(player.getName()).setScore(0);

        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();

        if (isStarted)
            makeAPlayerStartGame(player);
        else {
            Location spawnLocation = settings.getLobbyLocation();
            if (spawnLocation != null)
                player.teleport(spawnLocation);
        }

        broadcastMessage(
                Component.text()
                        .append(Component.text(player.getName()).decorate(TextDecoration.BOLD))
                        .append(Component.text(" landed in the game"))
                        .color(NamedTextColor.GREEN)
                        .build()
        );

        participants.put(player, participant);
        return ParticipantJoinResult.Joined;
    }
    public enum ParticipantJoinResult {
        Joined,
        PlayerAlreadyInGame,
        GameInEndScene,
        GameStarted,
    }

    public ParticipantLeaveResult removeParticipant(@NotNull Player player) {
        if (!participants.containsKey(player))
            return ParticipantLeaveResult.PlayerNotInGame;
        Participant participant = participants.remove(player);
        participant.playerTeam.unregister();
        playerGameManagerMap.remove(player);

        player.teleport(participant.originalLocation);
        player.setGameMode(participant.originalGameMode);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20.0);

        broadcastMessage(
                Component.text()
                .append(Component.text(player.getName()).decorate(TextDecoration.BOLD))
                .append(Component.text(" is flying to other mini games"))
                .color(NamedTextColor.RED)
                .build()
        );

        resetARespawningPlayers(player);

        return ParticipantLeaveResult.Left;
    }
    public enum ParticipantLeaveResult {
        Left,
        PlayerNotInGame,
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        if (participants.containsKey(event.getPlayer())) {
            removeParticipant(event.getPlayer());
            event.quitMessage(null);
        }
    }
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!participants.containsKey(event.getEntity()))
            return;
        Player player = event.getEntity();
        if (!isStarted) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "How did you die when the game hasn't even started yet ??");
            event.deathMessage(Component.text(player.getName()).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                    .append(Component.text(" is dead in a mysterious way").color(NamedTextColor.YELLOW)));
        }
        else {
            event.setCancelled(true);
            if (respawningPlayers.containsKey(player))
                return;
            player.setGameMode(GameMode.SPECTATOR);

            String killerName;
            if (player.getKiller() == null) {
                killerName = "xXx_Admin_Magic_xXx";
            }
            else {
                killerName = player.getKiller().getName();
                Score score = killsObjs.getScore(player.getKiller().getName());
                score.setScore(score.getScore()+1);
            }
            broadcastMessage(
                    Component.text()
                            .append(Component.text(player.getName()).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                            .append(Component.text(" has been killed by ").color(NamedTextColor.YELLOW))
                            .append(Component.text(killerName).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                            .build()
            );

            player.sendTitle(
                    ChatColor.RED + "You are dead",
                    ChatColor.GOLD + "You were killed by "+killerName,
                    10,60,10
            );

            long start = System.currentTimeMillis();

            RespawningPlayer rp = new RespawningPlayer();
            respawningPlayers.put(player, rp);
            rp.timerBossBar = Bukkit.getServer().createBossBar(ChatColor.RED + "Respawn in 3s", BarColor.RED, BarStyle.SOLID);
            rp.timerBossBar.setProgress(0.);
            rp.timerBossBar.addPlayer(player);

            rp.bossBarRefreshTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                long currentTime = System.currentTimeMillis() - start;
                double progress = Math.min(1.0, ((double)currentTime) / ((double)settings.getRespawnDuration()));
                rp.timerBossBar.setProgress(progress);
            }, 0, 5);

            rp.timerEndTask = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                rp.timerBossBar.removeAll();
                Bukkit.getScheduler().cancelTask(rp.bossBarRefreshTask);

                Location spawnLocation = settings.getRandomRespawn(random);
                if (spawnLocation != null)
                    player.teleport(spawnLocation);
                player.setGameMode(GameMode.ADVENTURE);
                player.getInventory().setItem(4, crossBow.clone());

                respawningPlayers.remove(player);
            }, settings.getRespawnDuration() / 50); // Ms / 50 = Tick
        }
    }
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!participants.containsKey(event.getPlayer()))
            return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (respawningPlayers.containsKey(event.getPlayer()) && (
                from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()
                )) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onPlayerLoseHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        event.setCancelled(true);
    }
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!participants.containsKey(event.getPlayer()))
            return;
        event.setCancelled(true);
    }
    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!participants.containsKey(event.getEntity()))
            return;
        Player player = (Player)event.getEntity();
        if (event.getBow().getType() == Material.CROSSBOW) {
            player.getInventory().setItemInOffHand(firework.clone());
        }
    }
    @EventHandler
    public void onInventoryInteract(InventoryClickEvent event) {
        if (!participants.containsKey(event.getWhoClicked()))
            return;
        event.setCancelled(true);
    }
    @EventHandler
    public void onPlayerSwapHands(PlayerSwapHandItemsEvent event) {
        if (!participants.containsKey(event.getPlayer()) || !isStarted)
            return;
        event.setCancelled(true);
    }
    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!participants.containsKey(event.getPlayer()))
            return;
        event.setCancelled(true);
    }
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!participants.containsKey(event.getEntity()))
            return;
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            if (!isStarted) {
                event.setCancelled(true);
            }
            else {
                event.setDamage(10);
            }
        }
    }
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!participants.containsKey(event.getPlayer()) || event.getClickedBlock() == null)
            return;
        String blockName = event.getClickedBlock().getType().name();
        if (blockName.contains("TRAPDOOR") || blockName.contains("DOOR"))
            event.setCancelled(true);
    }

    public StartGameResult startGame() {
        if (isStarted)
            return StartGameResult.AlreadyStarted;
        System.out.println("START A GAME");

        for (Player player : participants.keySet()) {
            player.sendTitle("The game has started!", "Good luck", 10, 70, 10);
            makeAPlayerStartGame(player);
        }
        isStarted = true;
        return StartGameResult.Started;
    }
    private void makeAPlayerStartGame(Player player) {
        player.teleport(Objects.requireNonNull(settings.getRandomRespawn(random)));

        PlayerInventory playerInventory = player.getInventory();

        playerInventory.clear();
        playerInventory.setItemInOffHand(firework.clone());
        playerInventory.setHeldItemSlot(4);
        playerInventory.setItemInMainHand(crossBow.clone());
    }
    public enum StartGameResult {
        Started,
        AlreadyStarted,
    }

    private BossBar endGameTimingBossBar = null;
    private int endGameTimerTaskId = 0;
    public EndGameResult endGame() {
        if (isEnded || !isStarted)
            return EndGameResult.AlreadyEnded;
        isEnded = true;

        resetRespawningPlayers();

        endGameTimingBossBar = Bukkit.getServer().createBossBar("firematchendgameremaning", BarColor.WHITE, BarStyle.SOLID);
        endGameTimingBossBar.setProgress(0.0);
        endGameTimingBossBar.setTitle("Congratulations !");

        for (Player player : participants.keySet()) {
            endGameTimingBossBar.addPlayer(player);
            player.sendTitle(
                    ChatColor.GREEN + "End !",
                    ChatColor.UNDERLINE + "" + ChatColor.DARK_GRAY + "The game is finished",
                    10, 40, 10
            );
            player.setGameMode(GameMode.SPECTATOR);
        }
        long startTime = System.currentTimeMillis();
        endGameTimerTaskId = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long elapsedTime = System.currentTimeMillis()-startTime;
            double progress = Math.min(1., ((double)elapsedTime) / ((double)settings.getEndGameDuration()));
            endGameTimingBossBar.setProgress(progress);
        }, 0, 10);
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                plugin,
                this::stopGame,
                settings.getEndGameDuration() / 50
        );

        return EndGameResult.Ended;
    }
    public enum EndGameResult {
        Ended,
        AlreadyEnded,
    }

    public StopGameResult stopGame() {
        if (!isStarted)
            return StopGameResult.AlreadyStopped;

        resetRespawningPlayers();
        recreateKillsObjective();

        if (isEnded || endGameTimingBossBar != null || endGameTimerTaskId != 0) {
            Bukkit.getServer().getScheduler().cancelTask(endGameTimerTaskId);
            endGameTimingBossBar.removeAll();
            endGameTimerTaskId = 0;
            endGameTimingBossBar = null;
        }

        Location lobby = settings.getLobbyLocation();
        for (Player player : participants.keySet()) {
            if (lobby != null)
                player.teleport(lobby);
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
        }

        isStarted = false;
        isEnded = false;
        return StopGameResult.Stopped;
    }
    public enum StopGameResult {
        Stopped,
        AlreadyStopped,
    }
}
