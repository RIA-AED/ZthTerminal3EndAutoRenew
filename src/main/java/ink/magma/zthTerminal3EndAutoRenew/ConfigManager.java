package ink.magma.zthTerminal3EndAutoRenew;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置管理器类，负责加载、解析和管理插件的配置。
 */
public class ConfigManager {
    // 日期时间格式化器
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 内部类，表示龙蛋的获得者信息。
     */
    public static class DragonEggOwner {
        public UUID uuid; // 玩家的UUID
        public String name; // 玩家的名称
        public LocalDateTime pickupTime; // 拾取龙蛋的时间

        /**
         * 构造函数。
         *
         * @param uuid       玩家UUID。
         * @param name       玩家名称。
         * @param pickupTime 拾取时间。
         */
        public DragonEggOwner(UUID uuid, String name, LocalDateTime pickupTime) {
            this.uuid = uuid;
            this.name = name;
            this.pickupTime = pickupTime;
        }

        /**
         * 用于从配置加载的默认构造函数。
         */
        public DragonEggOwner() {
        }

        // Getter 和 Setter 方法
        public UUID getUuid() {
            return uuid;
        }

        public void setUuid(UUID uuid) {
            this.uuid = uuid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LocalDateTime getPickupTime() {
            return pickupTime;
        }

        public void setPickupTime(LocalDateTime pickupTime) {
            this.pickupTime = pickupTime;
        }

        /**
         * 将 DragonEggOwner 对象转换为 Map，用于保存到配置文件。
         *
         * @return 包含此对象数据的 Map。
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("uuid", uuid != null ? uuid.toString() : "");
            map.put("name", name != null ? name : "");
            map.put("pickup-time", pickupTime != null ? pickupTime.format(DATE_TIME_FORMATTER) : "");
            return map;
        }
    }

    /**
     * 内部类，表示一次末地刷新的条目。
     */
    public static class RefreshEntry {
        public LocalDateTime time; // 刷新时间
        public DragonEggOwner dragonEggOwner; // 龙蛋获得者信息
        public List<String> rewardClaimedPlayers; // 已领取本次刷新奖励的玩家UUID列表
        public List<ItemStack> rewardItems; // 新增：奖励物品列表

        /**
         * 构造函数。
         *
         * @param time                 刷新时间。
         * @param dragonEggOwner       龙蛋获得者信息。
         * @param rewardClaimedPlayers 已领取奖励的玩家列表。
         * @param rewardItems          奖励物品列表。
         */
        public RefreshEntry(LocalDateTime time, DragonEggOwner dragonEggOwner, List<String> rewardClaimedPlayers, List<ItemStack> rewardItems) {
            this.time = time;
            this.dragonEggOwner = dragonEggOwner;
            this.rewardClaimedPlayers = rewardClaimedPlayers != null ? new ArrayList<>(rewardClaimedPlayers) : new ArrayList<>();
            this.rewardItems = rewardItems != null ? new ArrayList<>(rewardItems) : new ArrayList<>(); // 初始化 rewardItems
        }

        /**
         * 用于从配置加载的默认构造函数。
         */
        public RefreshEntry() {
            this.dragonEggOwner = new DragonEggOwner(); // 初始化龙蛋获得者
            this.rewardClaimedPlayers = new ArrayList<>(); // 初始化奖励领取列表
            this.rewardItems = new ArrayList<>(); // 初始化 rewardItems 为空列表
        }

        // Getter 和 Setter 方法
        public LocalDateTime getTime() {
            return time;
        }

        public void setTime(LocalDateTime time) {
            this.time = time;
        }

        public DragonEggOwner getDragonEggOwner() {
            return dragonEggOwner;
        }

        public void setDragonEggOwner(DragonEggOwner dragonEggOwner) {
            this.dragonEggOwner = dragonEggOwner;
        }

        public List<String> getRewardClaimedPlayers() {
            return rewardClaimedPlayers;
        }

        public void setRewardClaimedPlayers(List<String> rewardClaimedPlayers) {
            this.rewardClaimedPlayers = rewardClaimedPlayers;
        }

        public List<ItemStack> getRewardItems() {
            return rewardItems;
        } // Getter for rewardItems

        public void setRewardItems(List<ItemStack> rewardItems) {
            this.rewardItems = rewardItems;
        } // Setter for rewardItems

        /**
         * 将 RefreshEntry 对象转换为 Map，用于保存到配置文件。
         *
         * @return 包含此对象数据的 Map。
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("time", time.format(DATE_TIME_FORMATTER));
            map.put("dragon-egg-owner", dragonEggOwner.toMap());
            map.put("reward-claimed-players", rewardClaimedPlayers);

            // 序列化 rewardItems
            if (rewardItems != null) {
                List<Map<String, Object>> serializedItems = new ArrayList<>();
                for (ItemStack item : rewardItems) {
                    if (item != null) {
                        serializedItems.add(item.serialize());
                    }
                }
                map.put("reward-items", serializedItems);
            } else {
                map.put("reward-items", new ArrayList<>()); // 如果为null，则存入空列表
            }
            return map;
        }
    }

    private final JavaPlugin plugin; // 插件实例
    private FileConfiguration config; // 插件配置文件对象
    private ZoneId zoneId; // 时区ID
    private List<RefreshEntry> refreshEntries = new ArrayList<>(); // 末地刷新时间条目列表

    // 其他配置项
    public String bossBarMessage; // BossBar 显示的消息
    public List<String> dragonEggLore; // 龙蛋的Lore信息
    public String announceWinner; // 宣布获胜者的消息
    public String announceEggReset; // 宣布龙蛋重置的消息
    public String eggResetBossBarTitle; // 龙蛋重置倒计时BossBar的标题
    public String endResetBroadcastMessage; // 末地重置完成时的广播消息
    public boolean broadcastEndResetEnabled; // 是否启用末地重置完成的广播

    /**
     * ConfigManager 的构造函数。
     *
     * @param plugin 插件主类的实例。
     */
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 加载并处理配置文件中的所有末地刷新条目。
     * <p>
     * 此方法会执行以下操作：
     * 1. 保存默认配置（如果 {@code config.yml} 不存在）。
     * 2. 加载插件配置。
     * 3. 解析配置中定义的时区。
     * 4. 读取并解析 {@code refresh-times} 列表中的每个条目，无论其时间是过去还是未来。
     * 5. 将所有解析出的刷新条目按时间先后排序。
     * 6. 将内存中完整的、排序后的刷新条目列表保存回配置文件，确保数据的完整性和一致性。
     * 7. 加载其他相关的配置项。
     * </p>
     * <p>
     * 注意：此方法会加载所有历史记录和未来计划的刷新，并将其全部写回配置文件，
     * 从而实现对刷新记录（包括龙蛋得主、奖励领取情况等）的持久化存储。
     * </p>
     */
    public void loadRefreshEntries() {
        plugin.saveDefaultConfig(); // 保存默认配置（如果 config.yml 不存在）
        this.config = plugin.getConfig(); // 加载配置

        // 解析时区配置，默认为 "Asia/Shanghai"
        String timezone = config.getString("timezone", "Asia/Shanghai");
        this.zoneId = ZoneId.of(timezone);

        LocalDateTime now = LocalDateTime.now(zoneId); // 获取当前时区的当前时间, parseRefreshEntryFromMap 可能仍需此参数用于其他逻辑

        // 读取 refresh-times 配置节，它是一个 Map 对象的列表
        List<Map<?, ?>> timesMapList = config.getMapList("refresh-times");
        List<RefreshEntry> parsedEntries = new ArrayList<>(); // 用于存储解析后的 RefreshEntry 对象

        // 遍历从配置中读取的原始 Map 列表
        for (Map<?, ?> entryMap : timesMapList) {
            // parseRefreshEntryFromMap 不再基于 'now' 过滤过去的条目，但 'now' 可能用于其他校验
            RefreshEntry refreshEntry = parseRefreshEntryFromMap(entryMap, now);
            if (refreshEntry != null) {
                parsedEntries.add(refreshEntry); // 如果解析成功，则添加到列表
            }
        }

        // 按刷新时间对所有条目进行升序排序
        parsedEntries.sort(Comparator.comparing(RefreshEntry::getTime));
        this.refreshEntries = parsedEntries; // 更新内存中的刷新时间列表（包含所有记录）

        // 将内存中所有（包括过去和未来）的刷新条目列表转换回 Map 格式并保存到配置文件
        // 这确保了即便是过去的记录，如果其内容（如龙蛋得主）在程序运行中被更新，也会被持久化
        List<Map<String, Object>> toSave = this.refreshEntries.stream()
                .map(RefreshEntry::toMap) // 调用 RefreshEntry 的 toMap 方法进行转换
                .collect(Collectors.toList());
        config.set("refresh-times", toSave); // 设置到配置对象
        plugin.saveConfig(); // 保存配置文件到磁盘

        // 加载其他配置项
        this.bossBarMessage = config.getString("bossbar-message");
        this.dragonEggLore = config.getStringList("dragon-egg-lore");
        this.announceWinner = config.getString("announce-winner");
        this.announceEggReset = config.getString("announce-egg-reset");
        this.eggResetBossBarTitle = config.getString("egg-reset-bossbar-title", "<!color:#FF69B4>龙蛋将在 <gold>{time}</gold> 秒后重置");
        this.endResetBroadcastMessage = config.getString("end-reset-broadcast-message", "<#FF00FF>[末地刷新] <#FFC0CB>末地已重置，新的冒险开始了！");
        this.broadcastEndResetEnabled = config.getBoolean("broadcast-end-reset", true);
    }

    /**
     * 从 Map 对象解析单个 {@link RefreshEntry}。
     * <p>
     * 此方法会尝试从给定的 Map 中提取数据来构建一个 {@code RefreshEntry} 对象。
     * 它会解析时间、龙蛋获得者信息和奖励领取者列表。
     * 注意：此方法不再根据 {@code now} 参数过滤掉过去的条目；它会尝试解析所有有效的条目。
     * {@code now} 参数保留，以备将来可能的其他基于当前时间的校验逻辑（例如，某个字段只对未来条目有效）。
     * </p>
     *
     * @param entryMap 包含 {@code RefreshEntry} 数据的 Map 对象。
     * @param now      当前时间。虽然不再用于过滤过去条目，但保留以备将来使用。
     * @return 解析后的 {@code RefreshEntry} 对象。如果条目在结构上无效（例如缺少必要的时间字段）或解析过程中发生严重错误，则返回 {@code null}。
     */
    private RefreshEntry parseRefreshEntryFromMap(Map<?, ?> entryMap, LocalDateTime now) {
        try {
            String timeStr = (String) entryMap.get("time");
            if (timeStr == null || timeStr.isEmpty()) {
                plugin.getLogger().warning("Refresh entry is missing 'time' field: " + entryMap);
                return null;
            }
            LocalDateTime ldt = LocalDateTime.parse(timeStr, DATE_TIME_FORMATTER);

            RefreshEntry refreshEntry = new RefreshEntry();
            refreshEntry.setTime(ldt);

            // 解析 dragon-egg-owner
            Map<?, ?> ownerMap = (Map<?, ?>) entryMap.get("dragon-egg-owner");
            if (ownerMap != null) {
                DragonEggOwner owner = new DragonEggOwner();
                String uuidStr = (String) ownerMap.get("uuid");
                if (uuidStr != null && !uuidStr.isEmpty()) {
                    try {
                        owner.setUuid(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID format in dragon-egg-owner: " + uuidStr + " for entry: " + timeStr);
                        // 可以选择不设置UUID，或者记录错误后继续
                    }
                }
                owner.setName((String) ownerMap.get("name")); // 名称可以是null或空

                String pickupTimeStr = (String) ownerMap.get("pickup-time");
                if (pickupTimeStr != null && !pickupTimeStr.isEmpty()) {
                    try {
                        owner.setPickupTime(LocalDateTime.parse(pickupTimeStr, DATE_TIME_FORMATTER));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Invalid pickup-time format in dragon-egg-owner: " + pickupTimeStr + " for entry: " + timeStr);
                        // 可以选择不设置拾取时间，或者记录错误后继续
                    }
                }
                refreshEntry.setDragonEggOwner(owner);
            } else {
                // 如果配置文件中没有 dragon-egg-owner，则使用默认的空对象
                refreshEntry.setDragonEggOwner(new DragonEggOwner());
            }


            // 解析 reward-claimed-players
            Object claimedPlayersObj = entryMap.get("reward-claimed-players");
            if (claimedPlayersObj instanceof List<?> rawList) {
                // 进行类型安全的转换
                List<String> claimedPlayers = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof String) {
                        claimedPlayers.add((String) item);
                    } else {
                        plugin.getLogger().warning("Non-string value found in reward-claimed-players for entry: " + timeStr);
                    }
                }
                refreshEntry.setRewardClaimedPlayers(claimedPlayers);
            } else if (claimedPlayersObj != null) {
                plugin.getLogger().warning("reward-claimed-players is not a list for entry: " + timeStr);
                refreshEntry.setRewardClaimedPlayers(new ArrayList<>()); // 使用空列表作为默认值
            } else {
                refreshEntry.setRewardClaimedPlayers(new ArrayList<>()); // 如果字段不存在，也使用空列表
            }

            // 解析 reward-items
            Object rewardItemsObj = entryMap.get("reward-items");
            List<ItemStack> parsedRewardItems = new ArrayList<>();
            if (rewardItemsObj instanceof List<?> rawItemList) {
                for (Object itemMapObj : rawItemList) {
                    if (itemMapObj instanceof Map) {
                        try {
                            // 需要确保 Map 的键是 String 类型，值是 Object 类型
                            @SuppressWarnings("unchecked") // Bukkit API 需要这个转换
                            Map<String, Object> itemMap = (Map<String, Object>) itemMapObj;
                            ItemStack itemStack = ItemStack.deserialize(itemMap);
                            parsedRewardItems.add(itemStack);
                        } catch (ClassCastException cce) {
                            plugin.getLogger().warning("Error deserializing reward item due to invalid map structure for entry: " + timeStr + ". Item map: " + itemMapObj + " - " + cce.getMessage());
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error deserializing reward item for entry: " + timeStr + ". Item map: " + itemMapObj + " - " + e.getMessage());
                        }
                    } else {
                        plugin.getLogger().warning("Non-map element found in reward-items list for entry: " + timeStr + ". Element: " + itemMapObj.toString());
                    }
                }
                refreshEntry.setRewardItems(parsedRewardItems);
            } else if (rewardItemsObj != null) {
                plugin.getLogger().warning("'reward-items' is not a list for entry: " + timeStr + ". Found: " + rewardItemsObj.getClass().getName() + ". Defaulting to empty list.");
                refreshEntry.setRewardItems(new ArrayList<>()); // 默认为空列表
            } else {
                // plugin.getLogger().info("'reward-items' field not found for entry: " + timeStr + ". Defaulting to empty list."); // 可以选择不记录，因为这是可选字段
                refreshEntry.setRewardItems(new ArrayList<>()); // 如果字段不存在，默认为空列表
            }

            return refreshEntry;

        } catch (Exception e) {
            plugin.getLogger().severe("Error parsing refresh entry: " + entryMap.toString() + " - " + e.getMessage());
            e.printStackTrace(); // 打印堆栈跟踪以便调试
            return null; // 解析失败，返回null
        }
    }

    /**
     * 获取配置中定义的时区ID。
     *
     * @return 时区ID。
     */
    public ZoneId getZoneId() {
        return zoneId;
    }

    /**
     * 获取所有未来的末地刷新条目列表。
     *
     * @return 一个不可修改的、仅包含未来 {@link RefreshEntry} 的列表。
     */
    public List<RefreshEntry> getFutureRefreshEntries() {
        LocalDateTime now = LocalDateTime.now(zoneId);
        return refreshEntries.stream()
                .filter(entry -> entry.getTime().isAfter(now))
                .toList();
    }

    /**
     * 以字符串列表的形式列出所有未来的刷新时间。
     * 主要用于命令反馈或展示。
     *
     * @return 格式化后的未来刷新时间字符串列表 (yyyy-MM-dd HH:mm:ss)。
     */
    public List<String> listRefreshEntries() {
        LocalDateTime now = LocalDateTime.now(zoneId); // 获取当前时间进行比较
        return refreshEntries.stream()
                // .filter(entry -> entry.getTime().isAfter(now)) // 不再只选择未来的时间
                .map(entry -> {
                    String timeStr = DATE_TIME_FORMATTER.format(entry.getTime());
                    if (entry.getTime().isBefore(now)) {
                        return timeStr + " (已过期)";
                    }
                    return timeStr;
                }) // 格式化时间并标记已过期
                .collect(Collectors.toList());
    }

    /**
     * 添加一个新的末地刷新时间。
     * 如果该时间已存在，则不会添加。
     * 添加后会自动保存到配置文件。
     *
     * @param timeToAdd 要添加的 {@link LocalDateTime}。
     * @return 如果成功添加则返回 true，如果时间已存在则返回 false。
     */
    public boolean addRefreshEntry(LocalDateTime timeToAdd) {
        // 检查是否已存在具有相同时间的条目
        if (refreshEntries.stream().anyMatch(entry -> entry.getTime().equals(timeToAdd))) {
            plugin.getLogger().info("尝试添加已存在的刷新时间: " + DATE_TIME_FORMATTER.format(timeToAdd));
            return false; // 时间已存在
        }
        // 创建一个新的 RefreshEntry，龙蛋信息、奖励领取者列表和奖励物品列表为空
        RefreshEntry newEntry = new RefreshEntry(timeToAdd, new DragonEggOwner(), new ArrayList<>(), new ArrayList<>());
        refreshEntries.add(newEntry);
        refreshEntries.sort(Comparator.comparing(RefreshEntry::getTime)); // 按时间排序
        saveRefreshEntriesToConfig(); // 保存到配置文件
        plugin.getLogger().info("成功添加新的刷新时间: " + DATE_TIME_FORMATTER.format(timeToAdd));
        return true;
    }

    /**
     * 添加一个新的末地刷新条目。
     * 如果已存在具有相同时间的条目，则不会添加。
     * 添加后会自动保存到配置文件。
     *
     * @param entryToAdd 要添加的 {@link RefreshEntry}。
     * @return 如果成功添加则返回 true，如果时间已存在则返回 false。
     */
    public boolean addRefreshEntry(RefreshEntry entryToAdd) {
        if (entryToAdd == null || entryToAdd.getTime() == null) {
            plugin.getLogger().warning("尝试添加空的 RefreshEntry 或时间为空的条目。");
            return false;
        }
        // 检查是否已存在具有相同时间的条目
        if (refreshEntries.stream().anyMatch(entry -> entry.getTime().equals(entryToAdd.getTime()))) {
            plugin.getLogger().info("尝试添加已存在的刷新时间的条目: " + DATE_TIME_FORMATTER.format(entryToAdd.getTime()));
            return false; // 时间已存在
        }
        refreshEntries.add(entryToAdd);
        refreshEntries.sort(Comparator.comparing(RefreshEntry::getTime)); // 按时间排序
        saveRefreshEntriesToConfig(); // 保存到配置文件
        plugin.getLogger().info("成功添加新的刷新条目: " + DATE_TIME_FORMATTER.format(entryToAdd.getTime()));
        return true;
    }

    /**
     * 移除一个指定的末地刷新时间。
     * 如果成功移除，会自动保存到配置文件。
     *
     * @param timeToRemove 要移除的 {@link LocalDateTime}。
     * @return 如果成功移除则返回 true，否则返回 false。
     */
    public boolean removeRefreshEntry(LocalDateTime timeToRemove) {
        boolean removed = refreshEntries.removeIf(entry -> entry.getTime().equals(timeToRemove)); // 根据时间移除条目
        if (removed) {
            saveRefreshEntriesToConfig(); // 如果有变动，则保存到配置文件
            plugin.getLogger().info("成功移除刷新时间: " + DATE_TIME_FORMATTER.format(timeToRemove));
        } else {
            plugin.getLogger().info("尝试移除不存在的刷新时间: " + DATE_TIME_FORMATTER.format(timeToRemove));
        }
        return removed;
    }

    /**
     * 将当前的刷新时间列表保存回配置文件。
     * 此方法是私有的，由其他修改刷新时间列表的方法调用。
     */
    public void saveRefreshEntriesToConfig() { // -> 改为 public
        List<Map<String, Object>> toSave = refreshEntries.stream()
                .map(RefreshEntry::toMap) // 将每个 RefreshEntry 转换为 Map
                .collect(Collectors.toList());
        config.set("refresh-times", toSave); // 设置到配置对象
        plugin.saveConfig(); // 保存到磁盘
    }

    /**
     * 将 {@link Duration} 对象格式化为易读的字符串，如 "X天Y小时Z分钟" 或 "N秒"。
     *
     * @param dur 要格式化的 {@link Duration} 对象。
     * @return 格式化后的时间字符串。如果持续时间为负，则返回 "已过期"。
     */
    public String formatDuration(Duration dur) {
        long totalSeconds = dur.getSeconds(); // 获取总秒数

        if (totalSeconds < 0) { // 处理已过期的持续时间
            return "已过期";
        }

        if (totalSeconds == 0) { // 处理刚好是0秒的情况
            return "0秒";
        }

        if (totalSeconds < 60) { // 如果总时间小于1分钟，仅显示秒
            return totalSeconds + "秒";
        } else { // 否则，组合显示天、小时、分钟
            long days = dur.toDaysPart(); // 获取天数部分
            long hours = dur.toHoursPart(); // 获取小时部分
            long minutes = dur.toMinutesPart(); // 获取分钟部分
            long seconds = dur.toSecondsPart(); // 获取秒数部分 (用于决定是否显示分钟后的秒)


            StringBuilder sb = new StringBuilder();
            if (days > 0) {
                sb.append(days).append("天");
            }
            if (hours > 0) {
                sb.append(hours).append("小时");
            }
            if (minutes > 0) {
                sb.append(minutes).append("分钟");
            }
            // 如果天、小时、分钟都为0，或者有剩余秒数且总时间大于等于60秒，则显示秒
            if (days == 0 && hours == 0 && minutes == 0 || seconds > 0 && !sb.isEmpty()) {
                // 仅当没有天/小时/分钟，或者有天/小时/分钟且仍有秒数时，才追加秒
                if (sb.isEmpty() || seconds > 0) { // 避免在 "X分钟" 后再加 "0秒"
                    // 如果没有天/时/分，则显示秒
                    sb.append(seconds).append("秒"); // 如果已有天/时/分，且有秒，则追加
                }
            }
            // 如果构建器为空（例如，持续时间是0到59秒之间，但上面已处理），确保返回 "0秒" 或实际秒数

            return sb.toString();
        }
    }

    /**
     * 根据时间查找特定的 RefreshEntry。
     *
     * @param time 要查找的 LocalDateTime。
     * @return 包含 RefreshEntry 的 Optional，如果未找到则为空。
     */
    public Optional<RefreshEntry> getRefreshEntryByTime(LocalDateTime time) {
        return refreshEntries.stream()
                .filter(entry -> entry.getTime().equals(time))
                .findFirst();
    }

    /**
     * 获取所有已配置的刷新条目（包括过去和未来的）。
     *
     * @return 一个包含所有 RefreshEntry 的列表的不可修改副本。
     */
    public List<RefreshEntry> getAllRefreshEntries() {
        return List.copyOf(refreshEntries); // 返回副本以防外部修改
    }

    /**
     * 获取“当前期”的 RefreshEntry。
     * “当前期”定义为：从所有 RefreshEntry 中，筛选出 time 小于或等于当前时间的条目，
     * 按 time 降序排序，取第一个。
     *
     * @return Optional 包装的当前 RefreshEntry，如果找不到则为空。
     */
    public Optional<RefreshEntry> getCurrentRefreshEntry() {
        LocalDateTime now = LocalDateTime.now(getZoneId());
        // entry.time <= now
        // 按 time 降序
        return refreshEntries.stream()
                .filter(entry -> !entry.getTime().isAfter(now))
                .max(Comparator.comparing(RefreshEntry::getTime));
    }
}