package ink.magma.zthTerminal3EndAutoRenew;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class EndResetScheduler {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private BukkitTask task;

    public EndResetScheduler(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void start() {
        // 如果已有任务在运行，先取消它
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        // 每分钟检查一次刷新时间
        task = new BukkitRunnable() {
            @Override
            public void run() {
                checkAndResetEnd();
            }
        }.runTaskTimer(plugin, 20L, 20L * 60); // 1分钟
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
    }

    public void reloadSchedule() {
        // ConfigManager 应该已经被外部重载过了
        // BossBarManager 应该也已经被外部通知更新了
        // EndResetScheduler 的下一次 checkAndResetEnd() 会自动使用新的配置
        // 但为了确保任务调度本身能应对可能的即时变化（比如修改了调度周期，虽然本插件没有这个功能）
        // 或者确保如果之前没有任务（例如首次加载失败），现在可以启动。
        // 最稳妥的方式是停止旧任务并启动新任务。
        stop();
        start();
        plugin.getLogger().info("EndResetScheduler schedule reloaded.");
    }

    /**
     * Re-evaluates and schedules the next end reset based on the current configuration.
     * This method should be called after any changes to the refresh times in the config.
     */
    public void scheduleNextReset() {
        // The reloadSchedule method already handles stopping the old task and starting a new one
        // based on the (potentially updated) configuration.
        reloadSchedule();
        plugin.getLogger().info("End reset schedule updated and next reset re-scheduled if applicable.");
    }

    private void checkAndResetEnd() {
        List<LocalDateTime> times = configManager.getFutureRefreshTimes();
        if (times.isEmpty())
            return;
        ZoneId zoneId = configManager.getZoneId();
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime next = times.get(0);

        if (!now.isBefore(next)) {
            // 到点，执行末地重置
            resetEndWorld();
            // 刷新配置，移除已过期时间
            configManager.loadAndPruneRefreshTimes();
        }
    }

    private void resetEndWorld() {
        // 末地世界名通常为 world_the_end
        String endName = "world_the_end";
        World end = Bukkit.getWorld(endName);
        if (end != null) {
            // 卸载并删除末地世界
            Bukkit.unloadWorld(end, false);
            // 物理删除 world_the_end 文件夹建议由管理员手动或用外部脚本完成
            // 这里只做简单卸载和重载
        }
        // 重新创建末地世界
        Bukkit.createWorld(new org.bukkit.WorldCreator(endName).environment(World.Environment.THE_END));
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(configManager.endResetBroadcastMessage));
    }
}