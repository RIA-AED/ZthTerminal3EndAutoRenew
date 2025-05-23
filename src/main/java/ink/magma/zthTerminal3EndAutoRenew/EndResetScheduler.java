package ink.magma.zthTerminal3EndAutoRenew;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
// 新增导入

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
    // 低频轮询任务A（拉起/管理高频B）
    private BukkitTask lowFreqCheckTask;
    // 高频轮询任务B（精准到点自动重置）
    private BukkitTask highFreqCheckTask;
    private BukkitTask bossBarUpdateTask;

    public static final String END_RESET_BAR_ID = "zth_end_reset_countdown";
    private static final String END_WORLD_NAME = "world_the_end";
    private static final long SEVEN_DAYS_IN_SECONDS = 7 * 24 * 60 * 60;

    // 新增常量
    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String BACKUP_FOLDER_SUFFIX = "_backup_";
    private static final String KICK_MESSAGE_END_RESET_MM = "<red><bold>末地正在重置</bold>，请稍后重新加入。</red>"; // MiniMessage 格式

    public EndResetScheduler(JavaPlugin plugin, ConfigManager configManager, BossBarManager bossBarManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bossBarManager = bossBarManager;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        stop(); // 确保在启动新任务前停止所有现有任务

        // 轮询A：每10秒检查一次是否进入高精度检测区间
        lowFreqCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                List<ConfigManager.RefreshEntry> future = configManager.getFutureRefreshEntries();
                if (future.isEmpty()) return;
                LocalDateTime nextResetTime = future.get(0).getTime();
                ZoneId zoneId = configManager.getZoneId();
                LocalDateTime now = LocalDateTime.now(zoneId);
                long secondsLeft = java.time.Duration.between(now, nextResetTime).getSeconds();

                if (secondsLeft <= 30 && secondsLeft >= 0) {
                    // 启动/维持高频轮询B
                    if (highFreqCheckTask == null || highFreqCheckTask.isCancelled()) {
                        plugin.getLogger().info("已进入末地重置30秒临界区，切换至高频检测...");
                        startHighFreqResetCheck(nextResetTime);
                    }
                } else {
                    // 如B存在但不在临界区，强制关闭B
                    if (highFreqCheckTask != null && !highFreqCheckTask.isCancelled()) {
                        highFreqCheckTask.cancel();
                        highFreqCheckTask = null;
                        plugin.getLogger().info("已离开高频重置检测区，高频检测已关闭。");
                    }
                }
            }
        }.runTaskTimer(plugin, 10 * 20L, 10 * 20L); // 10秒后开始，然后每10秒

        // BossBar刷新任务
        bossBarUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateEndCountdownBars();
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1秒后开始，然后每秒一次
    }

    /**
     * 启动高频检测B，tick级别认定是否到点并精确重置。
     * 到点即回收自身、关闭自身任务handle。
     * @param resetTime 目标重置时间
     */
    private void startHighFreqResetCheck(LocalDateTime resetTime) {
        if (highFreqCheckTask != null && !highFreqCheckTask.isCancelled()) {
            return; // 已经有B任务在运行
        }
        highFreqCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                LocalDateTime now = LocalDateTime.now(configManager.getZoneId());
                if (!now.isBefore(resetTime)) {
                    plugin.getLogger().info("高精度检测触发末地自动重置：" + resetTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    resetEndWorld();
                    configManager.loadRefreshEntries();
                    // BossBar等立即刷新
                    Bukkit.getOnlinePlayers().forEach(EndResetScheduler.this::updatePlayerEndBarStatus);
                    // 高频检测关闭
                    if (highFreqCheckTask != null && !highFreqCheckTask.isCancelled()) {
                        highFreqCheckTask.cancel();
                        highFreqCheckTask = null;
                        plugin.getLogger().info("重置达成，高频检测任务已关闭。");
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L); // 1tick后开始，每tick运行
    }

    public void stop() {
        if (lowFreqCheckTask != null && !lowFreqCheckTask.isCancelled()) {
            lowFreqCheckTask.cancel();
            lowFreqCheckTask = null;
        }
        if (highFreqCheckTask != null && !highFreqCheckTask.isCancelled()) {
            highFreqCheckTask.cancel();
            highFreqCheckTask = null;
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
        // 通常由外部热重载配置后调用
        stop();
        start();
        plugin.getLogger().info("末地重置调度器任务已重载。");
        // 强制立即更新所有末地玩家的 BossBar。
        Bukkit.getOnlinePlayers().forEach(this::updatePlayerEndBarStatus);
    }

    private void updateEndCountdownBars() {
        List<ConfigManager.RefreshEntry> futureRefreshEntries = configManager.getFutureRefreshEntries();
        LocalDateTime nextRefreshTime = null;
        if (!futureRefreshEntries.isEmpty()) {
            nextRefreshTime = futureRefreshEntries.get(0).getTime();
        }

        // 确保 nextRefreshTime 确实是未来的，或者为 null
        if (nextRefreshTime != null && !nextRefreshTime.isAfter(LocalDateTime.now(configManager.getZoneId()))) {
            nextRefreshTime = null; // 如果不是未来的（例如，由于极小的时间差，它变成了现在或过去），则不显示
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerEndBar(player, nextRefreshTime);
        }
    }

    private void updatePlayerEndBarStatus(Player player) {
        if (player == null || !player.isOnline())
            return;

        List<ConfigManager.RefreshEntry> futureRefreshEntries = configManager.getFutureRefreshEntries();
        LocalDateTime nextRefreshTime = null;
        if (!futureRefreshEntries.isEmpty()) {
            nextRefreshTime = futureRefreshEntries.get(0).getTime();
        }

        // 确保 nextRefreshTime 确实是未来的，或者为 null
        if (nextRefreshTime != null && !nextRefreshTime.isAfter(LocalDateTime.now(configManager.getZoneId()))) {
            nextRefreshTime = null; // 如果不是未来的，则不显示
        }
        updatePlayerEndBar(player, nextRefreshTime);
    }

    private void updatePlayerEndBar(Player player, LocalDateTime nextRefreshTime) {
        if (player == null || !player.isOnline()) {
            // 确保离线玩家的 BossBar 被隐藏 (尽管 BossBarManager 内部可能已处理)
            if (player != null) {
                bossBarManager.hidePlayerBossBar(player, END_RESET_BAR_ID);
            }
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
                    bossBarManager.showPlayerBossBar(player, END_RESET_BAR_ID, title, BarColor.RED, BarStyle.SOLID,
                            0.0);
                } else {
                    bossBarManager.hidePlayerBossBar(player, END_RESET_BAR_ID);
                }
            } else if (durationToNext.toSeconds() > 0 && durationToNext.toSeconds() <= SEVEN_DAYS_IN_SECONDS) {
                String timeStr = configManager.formatDuration(durationToNext);
                String title = configManager.bossBarMessage.replace("{time}", timeStr);
                // 进度条从7天开始倒数，1.0表示剩余7天或更多，0.0表示时间已到
                double progress = Math.max(0.0,
                        Math.min(1.0, (double) durationToNext.getSeconds() / SEVEN_DAYS_IN_SECONDS));
                bossBarManager.showPlayerBossBar(player, END_RESET_BAR_ID, title, BarColor.PURPLE,
                        BarStyle.SEGMENTED_10, progress);
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

    // scheduleNextReset方法已废除，高精度检测及定时调度由高低频轮询机制替代

    /**
     * 公共方法，用于从外部（例如命令）强制触发末地重置。
     * 这将直接调用私有的重置逻辑。
     */
    public void forceResetEndWorld() {
        plugin.getLogger().info("正在通过外部调用强制重置末地...");
        resetEndWorld();
        // 注意：调用此方法后，调用者可能需要负责更新配置状态（例如调用 configManager.loadRefreshEntries()）
        // 以及重新加载调度或更新 BossBar（例如调用 endResetScheduler.reloadSchedule()）。
        // 这是因为此方法仅执行物理重置。
    }

    private void resetEndWorld() {
        plugin.getLogger().info("开始末地世界重置流程...");
        World endWorld = Bukkit.getWorld(END_WORLD_NAME);
        Path worldFolderPathToArchive;
        boolean wasWorldLoaded = endWorld != null;

        if (wasWorldLoaded) {
            plugin.getLogger().info("末地世界 '" + END_WORLD_NAME + "' 当前已加载。");
            // 1. 踢出玩家
            kickPlayersFromWorld(endWorld, MiniMessage.miniMessage().deserialize(KICK_MESSAGE_END_RESET_MM));

            // 2. 获取世界文件夹路径 (卸载前)
            worldFolderPathToArchive = endWorld.getWorldFolder().toPath();

            // 3. 卸载世界
            plugin.getLogger().info("正在卸载末地世界: " + END_WORLD_NAME);
            if (!Bukkit.unloadWorld(endWorld, true)) { // true 表示保存区块
                plugin.getLogger().severe("卸载末地世界失败: " + END_WORLD_NAME + "。重置过程已中止。世界未归档或重新创建。");
                // 考虑是否需要通知管理员或采取其他恢复措施
                return; // 关键步骤失败，中止重置
            }
            plugin.getLogger().info("末地世界卸载成功。");
        } else {
            plugin.getLogger().info("末地世界 '" + END_WORLD_NAME + "' 当前未加载。正在尝试定位其文件夹以进行归档。");
            // 尝试定位未加载世界的文件夹
            File worldContainer = Bukkit.getWorldContainer();
            worldFolderPathToArchive = Paths.get(worldContainer.getAbsolutePath(), END_WORLD_NAME);
        }

        // 4. 归档旧的世界文件夹 (无论之前是否加载)
        // archiveWorldFolder 会检查路径是否存在
        boolean archiveSuccess = archiveWorldFolder(worldFolderPathToArchive, END_WORLD_NAME);
        if (archiveSuccess) {
            plugin.getLogger().info("旧的末地世界文件夹已成功归档或未找到（这没问题）。");
        } else {
            // 归档失败的日志已在 archiveWorldFolder 中记录
            plugin.getLogger().warning("旧的末地世界文件夹归档失败或已跳过。有关详细信息，请参阅先前的日志。正在继续创建新世界。");
            // 如果是从已加载的世界卸载后归档失败，这可能更值得关注
            if (wasWorldLoaded) {
                plugin.getLogger().warning("严重：最近卸载的世界文件夹归档失败：" + worldFolderPathToArchive + "。旧数据可能未正确备份。");
            }
        }

        // 5. 创建新的末地世界
        createNewEndWorld();
        plugin.getLogger().info("末地世界重置流程已完成。");
    }

    private void kickPlayersFromWorld(World world, net.kyori.adventure.text.Component kickMessage) {
        List<Player> playersInWorld = world.getPlayers();
        if (playersInWorld.isEmpty()) {
            plugin.getLogger().info("在世界 '" + world.getName() + "' 中未找到需要踢出的玩家。");
            return;
        }

        plugin.getLogger().info("正在从世界 '" + world.getName() + "' 踢出 " + playersInWorld.size() + " 名玩家...");
        for (Player player : playersInWorld) {
            player.kick(kickMessage); // Adventure API
        }
        plugin.getLogger().info("已从世界 '" + world.getName() + "' 踢出所有玩家。");
    }

    private boolean archiveWorldFolder(Path worldPath, String worldName) {
        // 检查文件夹是否存在且确实是文件夹
        if (!Files.isDirectory(worldPath)) { // Files.isDirectory 也隐式检查了 Files.exists
            plugin.getLogger().info("世界文件夹 '" + worldPath + "' 不存在或不是一个目录。将不执行归档操作。");
            return true; // 没有东西可归档，视为“成功”完成此步骤（因为目标是移除旧文件夹）
        }

        // 生成备份文件夹名称
        // 假设 configManager.getZoneId() 存在并返回正确的时区ID
        String timestamp = LocalDateTime.now(configManager.getZoneId()).format(BACKUP_TIMESTAMP_FORMAT);
        String backupFolderName = worldName + BACKUP_FOLDER_SUFFIX + timestamp;

        Path parentDir = worldPath.getParent();
        if (parentDir == null) {
            plugin.getLogger().severe("无法确定用于创建备份的 '" + worldPath + "' 的父目录。已跳过归档。");
            return false; // 无法确定备份路径
        }
        Path backupPath = parentDir.resolve(backupFolderName);

        plugin.getLogger().info("正在尝试将世界文件夹从 '" + worldPath + "' 归档到 '" + backupPath + "'...");
        try {
            Files.move(worldPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("世界文件夹已成功归档到 '" + backupPath + "'。");
            return true;
        } catch (java.io.IOException e) { // 更具体的异常类型
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "归档世界文件夹 '" + worldPath + "' 到 '" + backupPath + "' 失败。错误: " + e.getMessage(), e);
            return false;
        }
    }

    private void createNewEndWorld() {
        plugin.getLogger().info("正在尝试创建一个新的末地世界: '" + END_WORLD_NAME + "'...");
        org.bukkit.WorldCreator creator = new org.bukkit.WorldCreator(END_WORLD_NAME)
                .environment(World.Environment.THE_END);

        World newEnd = Bukkit.createWorld(creator);

        if (newEnd != null) {
            plugin.getLogger().info("新的末地世界 '" + END_WORLD_NAME + "' 创建成功。");
            // 广播消息
            if (configManager.broadcastEndResetEnabled) {
                String broadcastMessage = configManager.endResetBroadcastMessage;
                if (broadcastMessage != null && !broadcastMessage.isEmpty()) {
                    Bukkit.broadcast(MiniMessage.miniMessage().deserialize(broadcastMessage));
                } else {
                    plugin.getLogger().warning("末地重置广播消息已启用，但消息文本未配置或为空。");
                }
            } else {
                plugin.getLogger().info("末地重置广播在配置中已禁用。正在跳过广播。");
            }
        } else {
            plugin.getLogger().severe("创建新的末地世界失败: '" + END_WORLD_NAME + "'。这是一个严重问题。末地可能无法访问或已损坏。");
        }
    }
}