package ink.magma.zthTerminal3EndAutoRenew;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class EndResetScheduler implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final BossBarManager bossBarManager;
    private BukkitTask resetCheckTask;
    private BukkitTask bossBarUpdateTask;

    public static final String END_RESET_BAR_ID = "zth_end_reset_countdown";
    private static final String END_WORLD_NAME = "world_the_end";
    private static final long SEVEN_DAYS_IN_SECONDS = 7 * 24 * 60 * 60;

    public EndResetScheduler(JavaPlugin plugin, ConfigManager configManager, BossBarManager bossBarManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bossBarManager = bossBarManager;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        stop(); // 确保在启动新任务前停止所有现有任务

        // 检查重置时间的任务 (例如每分钟运行一次)
        resetCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkAndResetEnd();
            }
        }.runTaskTimer(plugin, 20L * 10, 20L * 60); // 10秒后开始，然后每分钟一次

        // 更新 BossBar 的任务 (每秒运行一次)
        bossBarUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateEndCountdownBars();
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1秒后开始，然后每秒一次
    }

    public void stop() {
        if (resetCheckTask != null && !resetCheckTask.isCancelled()) {
            resetCheckTask.cancel();
            resetCheckTask = null;
        }
        if (bossBarUpdateTask != null && !bossBarUpdateTask.isCancelled()) {
            bossBarUpdateTask.cancel();
            bossBarUpdateTask = null;
        }
        // 当调度器停止时，隐藏所有相关的 BossBar
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && player.isOnline()) {
                bossBarManager.hidePlayerBossBar(player, END_RESET_BAR_ID);
            }
        }
    }

    public void reloadSchedule() {
        // ConfigManager 被假定已在外部重载。
        // 重启任务以应用任何潜在的调度更改，并确保任务正在运行。
        stop();
        start();
        plugin.getLogger().info("EndResetScheduler tasks reloaded.");
        // 强制立即更新所有末地玩家的 BossBar。
        Bukkit.getOnlinePlayers().forEach(this::updatePlayerEndBarStatus);
    }

    private void updateEndCountdownBars() {
        List<LocalDateTime> futureRefreshTimes = configManager.getFutureRefreshTimes();
        LocalDateTime nextRefreshTime = futureRefreshTimes.isEmpty() ? null : futureRefreshTimes.get(0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerEndBar(player, nextRefreshTime);
        }
    }

    private void updatePlayerEndBarStatus(Player player) {
        if (player == null || !player.isOnline()) return;
        List<LocalDateTime> futureRefreshTimes = configManager.getFutureRefreshTimes();
        LocalDateTime nextRefreshTime = futureRefreshTimes.isEmpty() ? null : futureRefreshTimes.get(0);
        updatePlayerEndBar(player, nextRefreshTime);
    }

    private void updatePlayerEndBar(Player player, LocalDateTime nextRefreshTime) {
        if (player == null || !player.isOnline()) {
            // 确保离线玩家的 BossBar 被隐藏 (尽管 BossBarManager 内部可能已处理)
            bossBarManager.hidePlayerBossBar(player, END_RESET_BAR_ID);
            return;
        }

        String playerWorldName = player.getWorld().getName();

        if (nextRefreshTime == null) {
            bossBarManager.hidePlayerBossBar(player, END_RESET_BAR_ID);
            return;
        }

        Duration durationToNext = Duration.between(LocalDateTime.now(configManager.getZoneId()), nextRefreshTime);

        if (END_WORLD_NAME.equals(playerWorldName)) {
            if (durationToNext.isNegative() || durationToNext.isZero()) {
                // 时间已到或已过，但尚未被 checkAndResetEnd 重置。
                // 根据偏好显示“刷新中”或隐藏。目前，如果时间已过较久则隐藏。
                if (durationToNext.getSeconds() > -10) { // 在重置前后短暂显示“即将刷新”
                    String title = configManager.bossBarMessage.replace("{time}", "<bold><red>即将刷新</red></bold>");
                    bossBarManager.showPlayerBossBar(player, END_RESET_BAR_ID, title, BarColor.RED, BarStyle.SOLID, 0.0);
                } else {
                    bossBarManager.hidePlayerBossBar(player, END_RESET_BAR_ID);
                }
            } else if (durationToNext.toSeconds() > 0 && durationToNext.toSeconds() <= SEVEN_DAYS_IN_SECONDS) {
                String timeStr = configManager.formatDuration(durationToNext);
                String title = configManager.bossBarMessage.replace("{time}", timeStr);
                // 进度条从7天开始倒数，1.0表示剩余7天或更多，0.0表示时间已到
                double progress = Math.max(0.0, Math.min(1.0, (double) durationToNext.getSeconds() / SEVEN_DAYS_IN_SECONDS));
                bossBarManager.showPlayerBossBar(player, END_RESET_BAR_ID, title, BarColor.PURPLE, BarStyle.SEGMENTED_10, progress);
            } else {
                // 超过7天或处于其他状态
                bossBarManager.hidePlayerBossBar(player, END_RESET_BAR_ID);
            }
        } else {
            // 玩家不在末地世界
            bossBarManager.hidePlayerBossBar(player, END_RESET_BAR_ID);
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // 根据新世界更新 BossBar 状态
        updatePlayerEndBarStatus(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 为加入的玩家更新 BossBar 状态
        updatePlayerEndBarStatus(player);
    }

    private void checkAndResetEnd() {
        List<LocalDateTime> times = configManager.getFutureRefreshTimes();
        if (times.isEmpty()) {
            return; // 没有计划的重置
        }

        ZoneId zoneId = configManager.getZoneId();
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime nextResetTime = times.get(0);

        if (!now.isBefore(nextResetTime)) {
            // 到达重置时间
            plugin.getLogger().info("Scheduled end reset time reached. Resetting the End...");
            resetEndWorld();
            // 加载并修剪刷新时间，这也将更新下一次检查的列表
            configManager.loadAndPruneRefreshTimes();

            // 重置和配置更新后，强制更新（新）末地中玩家的 BossBar。
            // updateEndCountdownBars 任务将获取新的 nextRefreshTime。
            // 或者我们可以立即为所有末地玩家调用 updatePlayerEndBarStatus。
            Bukkit.getScheduler().runTask(plugin, () -> { // 在下一个 tick 运行以确保世界可用
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player != null && player.isOnline()) {
                        updatePlayerEndBarStatus(player); // 这会根据玩家所在世界决定是否显示
                    }
                }
            });
        }
    }

    private void resetEndWorld() {
        World endWorld = Bukkit.getWorld(END_WORLD_NAME);
        if (endWorld != null) {
            // 在卸载前将所有玩家踢出末地
            for (Player player : endWorld.getPlayers()) {
                // 传送到主世界的出生点或配置的回退位置
                World mainWorld = Bukkit.getWorlds().get(0); // 假设第一个世界是主世界
                if (mainWorld != null) {
                    player.teleport(mainWorld.getSpawnLocation());
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>末地正在重置，您已被传送回主世界。</gold>"));
                } else {
                    player.kick(MiniMessage.miniMessage().deserialize("末地正在重置，请稍后重新加入。"));
                }
            }

            plugin.getLogger().info("Unloading End world: " + END_WORLD_NAME);
            if (!Bukkit.unloadWorld(endWorld, false)) { // false 表示不保存区块
                plugin.getLogger().severe("Failed to unload End world: " + END_WORLD_NAME + ". Reset might be incomplete.");
                return; // 如果卸载失败则停止
            } else {
                plugin.getLogger().info("End world unloaded successfully.");
            }
        } else {
            plugin.getLogger().info("End world '" + END_WORLD_NAME + "' not found or already unloaded. Proceeding to create a new one.");
        }

        plugin.getLogger().info("Attempting to create a new End world: " + END_WORLD_NAME);
        World newEnd = Bukkit.createWorld(new org.bukkit.WorldCreator(END_WORLD_NAME).environment(World.Environment.THE_END));
        if (newEnd != null) {
            plugin.getLogger().info("New End world created successfully.");
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize(configManager.endResetBroadcastMessage));
        } else {
            plugin.getLogger().severe("Failed to create new End world: " + END_WORLD_NAME);
        }
    }
}