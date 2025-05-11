package ink.magma.zthTerminal3EndAutoRenew;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossBarManager {
    private final JavaPlugin plugin;
    // 存储玩家特定的 BossBar: Player UUID -> (Bar ID -> BossBar)
    private final Map<UUID, Map<String, BossBar>> playerBossBars = new ConcurrentHashMap<>();
    // 存储全局 BossBar: Bar ID -> BossBar
    private final Map<String, BossBar> globalBossBars = new ConcurrentHashMap<>();
    // 存储全局倒计时 BossBar 的任务: Bar ID -> BukkitTask
    private final Map<String, BukkitTask> globalCountdownTasks = new ConcurrentHashMap<>();

    public BossBarManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 为指定玩家显示或更新一个 BossBar。
     * 如果具有相同 barId 的 BossBar 已存在于该玩家，则更新其内容。
     *
     * @param player   目标玩家
     * @param barId    BossBar 的唯一标识符
     * @param title    BossBar 的标题 (MiniMessage 格式)
     * @param color    BossBar 的颜色
     * @param style    BossBar 的样式
     * @param progress BossBar 的进度 (0.0 到 1.0)
     */
    public void showPlayerBossBar(Player player, String barId, String title, BarColor color, BarStyle style, double progress) {
        Map<String, BossBar> barsForPlayer = playerBossBars.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        BossBar bar = barsForPlayer.get(barId);

        String formattedTitle = LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(title));
        double clampedProgress = Math.max(0.0, Math.min(1.0, progress));

        if (bar != null) {
            // 更新现有 BossBar
            bar.setTitle(formattedTitle);
            bar.setColor(color);
            bar.setStyle(style);
            bar.setProgress(clampedProgress);
            if (!bar.getPlayers().contains(player)) { // 确保玩家在列表中
                bar.addPlayer(player);
            }
            bar.setVisible(true);
        } else {
            // 创建新的 BossBar
            bar = Bukkit.createBossBar(formattedTitle, color, style);
            bar.setProgress(clampedProgress);
            bar.addPlayer(player);
            bar.setVisible(true);
            barsForPlayer.put(barId, bar);
        }
    }

    /**
     * 更新指定玩家 BossBar 的标题和进度。
     *
     * @param player   目标玩家
     * @param barId    BossBar 的唯一标识符
     * @param title    新的标题 (MiniMessage 格式)
     * @param progress 新的进度 (0.0 到 1.0)
     */
    public void updatePlayerBossBar(Player player, String barId, String title, double progress) {
        BossBar bar = getPlayerBossBar(player.getUniqueId(), barId);
        if (bar != null && bar.isVisible()) {
            bar.setTitle(LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(title)));
            bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        }
    }

    /**
     * 更新指定玩家 BossBar 的标题。
     *
     * @param player 目标玩家
     * @param barId  BossBar 的唯一标识符
     * @param title  新的标题 (MiniMessage 格式)
     */
    public void updatePlayerBossBarTitle(Player player, String barId, String title) {
        BossBar bar = getPlayerBossBar(player.getUniqueId(), barId);
        if (bar != null && bar.isVisible()) {
            bar.setTitle(LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(title)));
        }
    }

    /**
     * 更新指定玩家 BossBar 的进度。
     *
     * @param player   目标玩家
     * @param barId    BossBar 的唯一标识符
     * @param progress 新的进度 (0.0 到 1.0)
     */
    public void updatePlayerBossBarProgress(Player player, String barId, double progress) {
        BossBar bar = getPlayerBossBar(player.getUniqueId(), barId);
        if (bar != null && bar.isVisible()) {
            bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        }
    }

    /**
     * 隐藏并移除指定玩家的特定 BossBar。
     *
     * @param player 目标玩家
     * @param barId  要隐藏的 BossBar 的唯一标识符
     */
    public void hidePlayerBossBar(Player player, String barId) {
        hidePlayerBossBar(player.getUniqueId(), barId);
    }

    /**
     * 隐藏并移除指定玩家 UUID 的特定 BossBar。
     *
     * @param playerUuid 目标玩家的 UUID
     * @param barId      要隐藏的 BossBar 的唯一标识符
     */
    public void hidePlayerBossBar(UUID playerUuid, String barId) {
        Map<String, BossBar> barsForPlayer = playerBossBars.get(playerUuid);
        if (barsForPlayer != null) {
            BossBar bar = barsForPlayer.remove(barId);
            if (bar != null) {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null) {
                    bar.removePlayer(player); // 从 BossBar 实例中移除玩家
                }
                // 即使玩家离线，也确保 BossBar 被清理
                bar.setVisible(false);
                bar.removeAll(); // 清理 BossBar 资源
            }
            if (barsForPlayer.isEmpty()) {
                playerBossBars.remove(playerUuid);
            }
        }
    }

    /**
     * 隐藏并移除指定玩家的所有 BossBar。
     *
     * @param player 目标玩家
     */
    public void hideAllPlayerBossBars(Player player) {
        hideAllPlayerBossBars(player.getUniqueId());
    }

    /**
     * 隐藏并移除指定玩家 UUID 的所有 BossBar。
     *
     * @param playerUuid 目标玩家的 UUID
     */
    public void hideAllPlayerBossBars(UUID playerUuid) {
        Map<String, BossBar> barsForPlayer = playerBossBars.remove(playerUuid);
        if (barsForPlayer != null) {
            for (BossBar bar : barsForPlayer.values()) {
                bar.setVisible(false);
                bar.removeAll();
            }
            barsForPlayer.clear();
        }
    }

    /**
     * 检查指定的 BossBar 是否对特定玩家可见。
     *
     * @param player 目标玩家
     * @param barId  BossBar 的唯一标识符
     * @return 如果 BossBar 对玩家可见，则为 true；否则为 false
     */
    public boolean isPlayerBossBarVisible(Player player, String barId) {
        BossBar bar = getPlayerBossBar(player.getUniqueId(), barId);
        return bar != null && bar.isVisible() && bar.getPlayers().contains(player);
    }

    private BossBar getPlayerBossBar(UUID playerUuid, String barId) {
        Map<String, BossBar> barsForPlayer = playerBossBars.get(playerUuid);
        if (barsForPlayer != null) {
            return barsForPlayer.get(barId);
        }
        return null;
    }

    /**
     * 显示一个全局的、临时的倒计时 BossBar。
     * 如果已存在具有相同 ID 的 BossBar，旧的将被移除并替换。
     *
     * @param barId           此 BossBar 的唯一ID。
     * @param titleTemplate   BossBar 标题的 MiniMessage 格式模板 (例如, "事件将在 <gold>{time}</gold> 后开始")。
     *                        其中的 "{time}" 会被替换为剩余秒数。
     * @param color           BossBar 的颜色。
     * @param style           BossBar 的样式。
     * @param durationSeconds 倒计时的总时长（秒）。
     * @param onFinish        倒计时结束时要执行的操作 (一个 {@link Runnable} 实例)。可为 null。
     * @param playersToShow   要向哪些玩家显示此 BossBar。如果为 null 或空，则向所有在线玩家显示。
     */
    public void showGlobalCountdownBossBar(String barId, String titleTemplate, BarColor color, BarStyle style, int durationSeconds, Runnable onFinish, Collection<Player> playersToShow) {
        // 首先，隐藏并清理任何具有相同 ID 的现有 BossBar 及其任务
        hideGlobalBossBar(barId);

        BossBar bossBar = Bukkit.createBossBar(
                formatBossBarTitle(titleTemplate, durationSeconds),
                color,
                style
        );
        bossBar.setProgress(1.0); // 初始进度设置为满

        Collection<? extends Player> targetPlayers = (playersToShow == null || playersToShow.isEmpty()) ? Bukkit.getOnlinePlayers() : playersToShow;

        if (targetPlayers.isEmpty() && Bukkit.getOnlinePlayers().isEmpty()) {
             // 如果没有指定玩家，并且服务器上也没有在线玩家，则记录警告并且不创建 BossBar
            plugin.getLogger().info("尝试显示全局倒计时 BossBar '" + barId + "' 但没有目标玩家且无在线玩家。");
            return;
        }
        if (targetPlayers.isEmpty() && !Bukkit.getOnlinePlayers().isEmpty()){
            // 如果没有指定玩家，但服务器上有在线玩家，则默认给所有在线玩家显示
            targetPlayers = Bukkit.getOnlinePlayers();
        }


        for (Player p : targetPlayers) {
            if (p != null && p.isOnline()) { // 确保玩家有效
                bossBar.addPlayer(p);
            }
        }

        if (bossBar.getPlayers().isEmpty()){
            plugin.getLogger().info("全局倒计时 BossBar '" + barId + "' 创建后没有有效的玩家，将不会显示。");
            return; // 如果添加后仍然没有玩家（例如目标玩家都离线了），则不继续
        }

        bossBar.setVisible(true);
        globalBossBars.put(barId, bossBar);

        BukkitTask task = new BukkitRunnable() {
            private int timeLeft = durationSeconds;

            @Override
            public void run() {
                BossBar currentBar = globalBossBars.get(barId);
                // 如果 BossBar 在外部被移除或隐藏，则任务应自行取消
                if (currentBar == null || !currentBar.isVisible()) {
                    globalCountdownTasks.remove(barId); // 清理任务映射
                    this.cancel();
                    return;
                }

                if (timeLeft <= 0) {
                    // 倒计时结束，隐藏 BossBar（这将通过 hideGlobalBossBar 自动取消此任务）
                    hideGlobalBossBar(barId);
                    if (onFinish != null) {
                        try {
                            onFinish.run();
                        } catch (Exception e) {
                            plugin.getLogger().severe("执行 BossBar '" + barId + "' 的 onFinish 回调时出错: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    return; // 任务将由 hideGlobalBossBar 取消
                }

                currentBar.setTitle(formatBossBarTitle(titleTemplate, timeLeft));
                currentBar.setProgress(Math.max(0.0, Math.min(1.0, (double) timeLeft / durationSeconds)));
                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        globalCountdownTasks.put(barId, task);
    }

    /**
     * 显示一个全局的、非倒计时的 BossBar。
     * 如果已存在具有相同 ID 的 BossBar，旧的将被移除并替换。
     *
     * @param barId         此 BossBar 的唯一ID。
     * @param title         BossBar 的标题 (MiniMessage 格式)。
     * @param color         BossBar 的颜色。
     * @param style         BossBar 的样式。
     * @param progress      BossBar 的进度 (0.0 到 1.0)。
     * @param playersToShow 要向哪些玩家显示此 BossBar。如果为 null 或空，则向所有在线玩家显示。
     */
    public void showGlobalBossBar(String barId, String title, BarColor color, BarStyle style, double progress, Collection<Player> playersToShow) {
        hideGlobalBossBar(barId); // 清理任何同 ID 的旧 BossBar 或倒计时任务

        String formattedTitle = LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(title));
        double clampedProgress = Math.max(0.0, Math.min(1.0, progress));

        BossBar bossBar = Bukkit.createBossBar(formattedTitle, color, style);
        bossBar.setProgress(clampedProgress);

        Collection<? extends Player> targetPlayers = (playersToShow == null || playersToShow.isEmpty()) ? Bukkit.getOnlinePlayers() : playersToShow;

        if (targetPlayers.isEmpty() && Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().info("尝试显示全局 BossBar '" + barId + "' 但没有目标玩家且无在线玩家。");
            return;
        }
        if (targetPlayers.isEmpty() && !Bukkit.getOnlinePlayers().isEmpty()){
            targetPlayers = Bukkit.getOnlinePlayers();
        }

        for (Player p : targetPlayers) {
            if (p != null && p.isOnline()) {
                bossBar.addPlayer(p);
            }
        }

        if (bossBar.getPlayers().isEmpty()){
            plugin.getLogger().info("全局 BossBar '" + barId + "' 创建后没有有效的玩家，将不会显示。");
            return;
        }

        bossBar.setVisible(true);
        globalBossBars.put(barId, bossBar);
    }


    /**
     * 更新指定 ID 的全局 BossBar 的标题和进度。
     *
     * @param barId    要更新的 BossBar 的唯一ID。
     * @param title    新的标题 (MiniMessage 格式)。
     * @param progress 新的进度 (0.0 到 1.0)。
     */
    public void updateGlobalBossBar(String barId, String title, double progress) {
        BossBar bar = globalBossBars.get(barId);
        if (bar != null && bar.isVisible()) {
            bar.setTitle(LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(title)));
            bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        }
    }

    /**
     * 将一个玩家添加到一个已存在的全局 BossBar。
     *
     * @param barId  全局 BossBar 的唯一ID。
     * @param player 要添加的玩家。
     */
    public void addPlayerToGlobalBossBar(String barId, Player player) {
        BossBar bar = globalBossBars.get(barId);
        if (bar != null && bar.isVisible() && player != null && player.isOnline()) {
            bar.addPlayer(player);
        }
    }

    /**
     * 从一个全局 BossBar 移除一个玩家。
     * 如果 BossBar 变为空，它不会自动隐藏，除非显式调用 hideGlobalBossBar。
     *
     * @param barId  全局 BossBar 的唯一ID。
     * @param player 要移除的玩家。
     */
    public void removePlayerFromGlobalBossBar(String barId, Player player) {
        BossBar bar = globalBossBars.get(barId);
        if (bar != null && player != null) { // 即使不可见也尝试移除玩家
            bar.removePlayer(player);
        }
    }

    /**
     * 检查指定 ID 的全局 BossBar 当前是否可见且有玩家。
     *
     * @param barId BossBar 的唯一ID。
     * @return 如果 BossBar 可见且至少有一个玩家，则为 true。
     */
    public boolean isGlobalBossBarVisible(String barId) {
        BossBar bar = globalBossBars.get(barId);
        return bar != null && bar.isVisible() && !bar.getPlayers().isEmpty();
    }


    /**
     * 隐藏并移除指定 ID 的全局 BossBar。
     * 如果有关联的倒计时任务，该任务也会被取消。
     *
     * @param barId 要隐藏的 BossBar 的唯一ID。
     */
    public void hideGlobalBossBar(String barId) {
        // 取消并移除相关的倒计时任务
        BukkitTask existingTask = globalCountdownTasks.remove(barId);
        if (existingTask != null) {
            try {
                if (!existingTask.isCancelled()) {
                    existingTask.cancel();
                }
            } catch (IllegalStateException e) {
                // 任务可能已经完成或被取消，忽略。
            }
        }

        BossBar bar = globalBossBars.remove(barId);
        if (bar != null) {
            bar.setVisible(false);
            bar.removeAll(); // 清空 BossBar 的所有玩家并使其不再显示
        }
    }

    /**
     * 格式化 BossBar 的标题。
     * 将模板中的 "{time}" 替换为指定的秒数，并使用 MiniMessage 解析。
     *
     * @param miniMessageTemplate MiniMessage 格式的标题模板。
     * @param time                要替换到模板中的时间值（通常是秒数）。
     * @return 格式化后的、带有旧式颜色代码的字符串标题。
     */
    private String formatBossBarTitle(String miniMessageTemplate, int time) {
        String replacedTemplate = miniMessageTemplate.replace("{time}", String.valueOf(time));
        return LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(replacedTemplate));
    }
}