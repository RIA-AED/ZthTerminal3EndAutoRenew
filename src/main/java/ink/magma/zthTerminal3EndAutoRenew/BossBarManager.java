package ink.magma.zthTerminal3EndAutoRenew;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class BossBarManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, BossBar> playerBars = new HashMap<>(); // 用于存储每个玩家持久化的 BossBar 实例
    private final Map<String, BossBar> globalTemporaryBars = new HashMap<>(); // 用于存储全局的、临时的 BossBar 实例，键为 BossBar 的唯一ID
    private LocalDateTime nextRefresh; // 下一次末地重置的时间点

    public BossBarManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        updateNextRefresh();
        // 启动一个周期性任务，用于更新所有 BossBar 的内容
        new BukkitRunnable() {
            public void run() {
                updateBars(); // 调用更新方法
            }
        }.runTaskTimer(plugin, 20L, 20L); // 立即开始，每 1 秒执行一次 (20 ticks = 1 second)
    }

    /**
     * 更新下一次末地重置的时间。
     * 会从配置中获取未来的刷新时间列表，并取第一个作为下一次刷新的时间。
     */
    public void updateNextRefresh() {
        LocalDateTime oldNextRefresh = this.nextRefresh;
        List<LocalDateTime> times = config.getFutureRefreshTimes();
        this.nextRefresh = times.isEmpty() ? null : times.get(0); // 如果列表为空，则 nextRefresh 为 null

        // 如果刷新时间发生变化，则需要更新所有相关的 BossBar
        if (!Objects.equals(oldNextRefresh, this.nextRefresh)) {
            // 强制更新所有当前在末地的玩家的 BossBar
            for (UUID uuid : new HashSet<>(playerBars.keySet())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && "world_the_end".equals(player.getWorld().getName())) {
                    // 先移除旧的bar，然后尝试显示新的bar
                    // 这可以确保bar的属性（如颜色、样式，如果将来会变的话）得到更新
                    // 并且能处理 nextRefresh 变为 null 或超过7天的情况
                    removeBar(player); // 移除旧的
                    showBar(player);   // 根据新的 nextRefresh 决定是否显示以及如何显示
                }
            }
            // 也需要更新全局 BossBar 的逻辑（如果它依赖 nextRefresh）
            // 当前的全局临时 BossBar 不直接依赖 nextRefresh
            updateBars(); // 立即更新所有 BossBar 的内容
        }
    }

    /**
     * 监听玩家切换世界的事件。
     *
     * @param event 玩家切换世界事件对象
     */
    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        // 检查玩家是否进入了末地 ("world_the_end")
        if ("world_the_end".equals(worldName)) {
            showBar(player); // 如果进入末地，则为该玩家显示 BossBar
        } else {
            removeBar(player); // 如果离开末地（或进入其他世界），则移除该玩家的持久化 BossBar
            // 注意：此处的逻辑是，全局临时 BossBar 是真正全局的，不受玩家所在世界影响。
            // 如果需要让全局临时 BossBar 也具有世界特定性，则需要在此处添加逻辑，
            // 将玩家从特定世界的全局临时 BossBar 中移除。
        }
    }

    /**
     * 监听玩家加入游戏的事件。
     *
     * @param event 玩家加入事件对象
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 检查玩家加入时是否在末地
        if ("world_the_end".equals(player.getWorld().getName())) {
            showBar(player); // 如果在末地，则为该玩家显示 BossBar
        }
        // 将玩家添加到所有当前活动的全局临时 BossBar
        // 这样新加入的玩家也能看到正在显示的全局倒计时等信息
        globalTemporaryBars.values().forEach(bar -> {
            if (bar.isVisible()) { // 只添加到可见的 BossBar
                bar.addPlayer(player);
            }
        });
    }

    /**
     * 为指定玩家显示末地重置倒计时 BossBar。
     * 仅当距离下次重置时间小于7天时显示。
     *
     * @param player 要显示 BossBar 的玩家
     */
    private void showBar(Player player) {
        if (nextRefresh == null) return; // 如果没有下一次刷新时间，则不执行任何操作
        // 计算当前时间到下一次刷新时间的间隔
        Duration toNext = Duration.between(LocalDateTime.now(config.getZoneId()), nextRefresh);
        // 如果距离下次刷新时间大于等于7天，则不显示 BossBar
        if (toNext.toDays() >= 7) return;

        // 创建一个新的 BossBar 实例
        BossBar bar = Bukkit.createBossBar("loading...", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bar.addPlayer(player); // 将玩家添加到 BossBar
        playerBars.put(player.getUniqueId(), bar); // 将 BossBar 存储到玩家映射中
        updateBarContent(player, bar, toNext); // 更新 BossBar 的初始内容
    }

    /**
     * 定期更新所有当前显示的末地重置 BossBar 的内容。
     * 同时会清理无效的 BossBar（例如玩家已离线或离开末地）。
     */
    private void updateBars() {
        if (nextRefresh == null) { // 如果没有下一次刷新时间
            // 移除所有现有的玩家 BossBar，因为没有有效的刷新时间了
            for (UUID uuid : new HashSet<>(playerBars.keySet())) {
                removeBar(uuid);
            }
            return;
        }

        Duration toNext = Duration.between(LocalDateTime.now(config.getZoneId()), nextRefresh);

        // 如果刷新时间已过，则移除所有 BossBar 并返回
        if (toNext.isNegative() || toNext.isZero()) {
            for (UUID uuid : new HashSet<>(playerBars.keySet())) {
                removeBar(uuid);
            }
            // 理论上，当 nextRefresh 更新为一个未来的时间时，showBar 会重新创建。
            // 或者，如果 EndResetScheduler 在重置后立即更新了 nextRefresh，
            // 这里的逻辑可以确保旧的 "已到刷新时间" 的 bar 被清除。
            return;
        }

        // 遍历所有持久化 BossBar 的玩家 UUID 集合（使用 HashSet 避免并发修改异常）
        for (UUID uuid : new HashSet<>(playerBars.keySet())) {
            Player player = Bukkit.getPlayer(uuid); // 根据 UUID 获取玩家对象
            // 如果玩家不存在（可能已离线）或者玩家当前不在末地
            if (player == null || !player.getWorld().getName().equals("world_the_end")) {
                removeBar(uuid); // 则移除该玩家的 BossBar
            } else if (toNext.toDays() < 7) { // 如果玩家有效且在末地，并且距离刷新小于7天
                updateBarContent(player, playerBars.get(uuid), toNext); // 更新其 BossBar 内容
            } else { // 如果距离刷新大于等于7天
                removeBar(uuid); // 也移除 BossBar，因为 showBar 时有此判断
            }
        }
    }

    /**
     * 更新指定 BossBar 的标题和进度。
     * 标题使用 MiniMessage 格式化。
     *
     * @param player BossBar 所属的玩家（当前未使用，但保留以备将来扩展）
     * @param bar    要更新的 BossBar 实例
     * @param left   距离下次重置的剩余时间
     */
    private void updateBarContent(Player player, BossBar bar, Duration left) {
        String msg;
        double progress;

        if (left.isNegative() || left.isZero()) {
            // 时间已到或已过
            msg = config.bossBarMessage.replace("{time}", "<bold><red>已到刷新时间</red></bold>");
            progress = 0.0;
            // 当时间到期时，我们希望移除这个 BossBar，而不是仅仅更新它的文本。
            // removeBar(player.getUniqueId()); // 直接移除会导致在 updateBars 迭代时出现问题
            // 标记此 bar 为待移除，或让 updateBars 的逻辑来处理
            // 实际上，updateBars 中已经有了 nextRefresh isNegative/Zero 的判断，会移除所有 bar
            // 所以这里不需要额外操作，updateBars 会处理。
        } else {
            String timeStr = config.formatDuration(left); // 将剩余时间格式化为字符串
            msg = config.bossBarMessage.replace("{time}", timeStr); // 替换消息模板中的时间占位符

            // 计算并设置 BossBar 的进度条
            // 进度条表示从7天开始到0的倒计时，1.0 表示剩余7天或更多，0.0 表示时间已到或已过
            // 总秒数为 7天 * 24小时/天 * 3600秒/小时
            double totalSecondsInWeek = 7.0 * 24.0 * 3600.0;
            // 确保 left.getSeconds() 不会因为时间已过而变成负数导致 progress > 1
            long remainingSeconds = Math.max(0, left.getSeconds());
            progress = 1.0 - (double) remainingSeconds / totalSecondsInWeek;
        }

        bar.setTitle(LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(msg))); // 使用 MiniMessage 解析并设置标题
        bar.setProgress(Math.max(0, Math.min(1, progress))); // 确保进度在 0.0 到 1.0 之间
    }

    /**
     * 移除指定玩家的持久化 BossBar。
     *
     * @param player 要移除 BossBar 的玩家
     */
    public void removeBar(Player player) {
        removeBar(player.getUniqueId()); // 通过 UUID 移除
    }

    /**
     * 根据玩家 UUID 移除持久化 BossBar。
     *
     * @param uuid 玩家的 UUID
     */
    private void removeBar(UUID uuid) {
        BossBar bar = playerBars.remove(uuid); // 从映射中移除 BossBar
        if (bar != null) {
            bar.removeAll(); // 清空 BossBar 的所有玩家并使其不再显示
        }
    }

    /**
     * 显示一个全局的、临时的倒计时 BossBar。
     * 这个 BossBar 会向所有在线玩家显示，并在倒计时结束后执行一个回调。
     *
     * @param barId           此临时 BossBar 的唯一ID (例如, "dragon_egg_reset")。用于管理和区分不同的临时 BossBar。
     * @param titleTemplate   BossBar 标题的 MiniMessage 格式模板 (例如, "龙蛋将在 <gold>{time}</gold> 秒后重置")。
     *                        其中的 "{time}" 会被替换为剩余秒数。
     * @param color           BossBar 的颜色。
     * @param style           BossBar 的样式。
     * @param durationSeconds 倒计时的总时长（秒）。
     * @param onFinish        倒计时结束时要执行的操作 (一个 {@link Runnable} 实例)。如果不需要结束回调，可以传入 null。
     */
    public void showGlobalCountdownBossBar(String barId, String titleTemplate, BarColor color, BarStyle style, int durationSeconds, Runnable onFinish) {
        // 检查是否已存在具有相同 ID 且当前可见的 BossBar
        if (globalTemporaryBars.containsKey(barId) && globalTemporaryBars.get(barId).isVisible()) {
            plugin.getLogger().info("ID 为 " + barId + " 的全局临时 BossBar 已在运行中，本次请求被忽略。");
            return; // 如果已存在，则不创建新的，直接返回
        }
        // 清理任何可能由于异常情况未被正确移除的、具有相同 ID 的旧 BossBar 实例
        hideGlobalTemporaryBossBar(barId);

        // 创建新的 BossBar 实例
        BossBar bossBar = Bukkit.createBossBar(
                formatBossBarTitle(titleTemplate, durationSeconds), // 格式化初始标题
                color,
                style
        );
        bossBar.setProgress(1.0); // 初始进度设置为满
        // 将当前所有在线玩家添加到这个 BossBar
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(onlinePlayer);
        }
        bossBar.setVisible(true); // 设置 BossBar 可见
        globalTemporaryBars.put(barId, bossBar); // 将 BossBar 存入全局临时 BossBar 映射

        // 创建并启动一个 BukkitRunnable 任务来处理倒计时逻辑
        new BukkitRunnable() {
            private int timeLeft = durationSeconds; // 剩余时间，初始化为总时长

            @Override
            public void run() {
                // 检查 BossBar 是否仍然存在于映射中并且可见
                // 如果 BossBar 被外部代码（例如 hideGlobalTemporaryBossBar 方法）移除了，则任务应自行取消
                if (!globalTemporaryBars.containsKey(barId) || !globalTemporaryBars.get(barId).isVisible()) {
                    this.cancel(); // BossBar 已被移除或隐藏，取消此任务
                    return;
                }

                // 检查倒计时是否结束
                if (timeLeft <= 0) {
                    hideGlobalTemporaryBossBar(barId); // 倒计时结束，隐藏并移除 BossBar
                    if (onFinish != null) {
                        onFinish.run(); // 如果定义了结束回调，则执行它
                    }
                    this.cancel(); // 取消此任务
                    return;
                }

                // 更新 BossBar 的标题和进度
                bossBar.setTitle(formatBossBarTitle(titleTemplate, timeLeft));
                bossBar.setProgress((double) timeLeft / durationSeconds); // 进度按剩余时间比例计算
                timeLeft--; // 剩余时间减一秒
            }
        }.runTaskTimer(plugin, 0L, 20L); // 立即开始，每秒执行一次 (20 ticks = 1 second)
    }

    /**
     * 隐藏并移除指定 ID 的全局临时 BossBar。
     *
     * @param barId 要隐藏的 BossBar 的唯一ID。
     */
    public void hideGlobalTemporaryBossBar(String barId) {
        BossBar bar = globalTemporaryBars.remove(barId); // 从映射中移除 BossBar
        if (bar != null) {
            bar.setVisible(false); // 设置为不可见
            bar.removeAll(); // 清空 BossBar 的所有玩家
        }
    }

    /**
     * 格式化 BossBar 的标题。
     * 将模板中的 "{time}" 替换为指定的秒数，并使用 MiniMessage 解析。
     * 注意：这里返回的是纯文本字符串，因为 BossBar 的 setTitle 方法接受的是 String。
     * 如果 BossBar API 支持直接设置 Component，则可以返回 Component 对象。
     *
     * @param miniMessageTemplate MiniMessage 格式的标题模板。
     * @param time                要替换到模板中的时间值（通常是秒数）。
     * @return 格式化后的纯文本标题。
     */
    private String formatBossBarTitle(String miniMessageTemplate, int time) {
        String replacedTemplate = miniMessageTemplate.replace("{time}", String.valueOf(time));
        // 使用 MiniMessage 解析，然后序列化为带有旧式颜色代码的字符串
        return LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(replacedTemplate));
    }
}