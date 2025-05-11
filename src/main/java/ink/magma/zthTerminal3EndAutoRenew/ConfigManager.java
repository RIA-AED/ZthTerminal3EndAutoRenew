package ink.magma.zthTerminal3EndAutoRenew;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private ZoneId zoneId;
    private List<LocalDateTime> refreshTimes = new ArrayList<>();

    // 配置项
    public String bossBarMessage;
    public List<String> dragonEggLore;
    public String announceWinner;
    public String announceEggReset;
    public String eggResetBossBarTitle; // 新增 BossBar 标题配置
    public String endResetBroadcastMessage; // 末地重置时的广播消息

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAndPruneRefreshTimes() {
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        // 解析时区
        String timezone = config.getString("timezone", "Asia/Shanghai");
        this.zoneId = ZoneId.of(timezone);

        // 读取并解析 refresh-times
        List<String> times = config.getStringList("refresh-times");
        List<LocalDateTime> parsedTimes = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now(zoneId);

        for (String timeStr : times) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(timeStr, fmt);
                if (ldt.isAfter(now)) {
                    parsedTimes.add(ldt);
                }
            } catch (Exception ignored) {
            }
        }
        parsedTimes.sort(Comparator.naturalOrder());
        this.refreshTimes = parsedTimes;

        // 只保留未来时间并保存回配置
        List<String> toSave = parsedTimes.stream()
                .map(ldt -> fmt.format(ldt))
                .collect(Collectors.toList());
        config.set("refresh-times", toSave);
        plugin.saveConfig();

        // 其他配置项
        this.bossBarMessage = config.getString("bossbar-message");
        this.dragonEggLore = config.getStringList("dragon-egg-lore");
        this.announceWinner = config.getString("announce-winner");
        this.announceEggReset = config.getString("announce-egg-reset");
        this.eggResetBossBarTitle = config.getString("egg-reset-bossbar-title", "<!color:#FF69B4>龙蛋将在 <gold>{time}</gold> 秒后重置"); // 添加默认值
        this.endResetBroadcastMessage = config.getString("end-reset-broadcast-message", "<#FF00FF>[末地刷新] <#FFC0CB>末地已重置，新的冒险开始了！");
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public List<LocalDateTime> getFutureRefreshTimes() {
        return Collections.unmodifiableList(refreshTimes);
    }

    public List<String> listRefreshTimes() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return refreshTimes.stream()
                .map(ldt -> fmt.format(ldt))
                .collect(Collectors.toList());
    }

    public boolean addRefreshTime(LocalDateTime timeToAdd) {
        if (refreshTimes.contains(timeToAdd)) {
            return false; // Time already exists
        }
        refreshTimes.add(timeToAdd);
        refreshTimes.sort(Comparator.naturalOrder());
        saveRefreshTimesToConfig();
        return true;
    }

    public boolean removeRefreshTime(LocalDateTime timeToRemove) {
        boolean removed = refreshTimes.remove(timeToRemove);
        if (removed) {
            saveRefreshTimesToConfig();
        }
        return removed;
    }

    private void saveRefreshTimesToConfig() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<String> toSave = refreshTimes.stream()
                .map(ldt -> fmt.format(ldt))
                .collect(Collectors.toList());
        config.set("refresh-times", toSave);
        plugin.saveConfig();
    }

    public String formatDuration(Duration dur) {
        long totalSeconds = dur.getSeconds(); // 获取总秒数

        if (totalSeconds < 0) { // 处理已过期的持续时间
            return "已过期"; // 或者其他你希望显示的文本
        }

        if (totalSeconds < 60) { // 如果总时间小于1分钟，显示秒
            return totalSeconds + "秒";
        } else { // 否则，显示天、小时、分钟
            long days = dur.toDays();
            long hours = dur.toHours() % 24;
            long minutes = dur.toMinutes() % 60;

            StringBuilder sb = new StringBuilder();
            if (days > 0) {
                sb.append(days).append("天");
            }
            if (hours > 0) {
                sb.append(hours).append("小时");
            }
            // 即使天和小时都为0，也显示分钟（因为我们已经处理了小于60秒的情况）
            sb.append(minutes).append("分钟");
            return sb.toString();
        }
    }
}