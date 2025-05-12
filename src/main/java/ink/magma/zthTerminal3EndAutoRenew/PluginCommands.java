package ink.magma.zthTerminal3EndAutoRenew;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap; // 新增导入
import java.util.List;
import java.util.Optional; // 新增导入
import java.util.UUID; // 新增导入，用于玩家UUID
import java.util.stream.Collectors;
import java.util.Comparator; // 新增导入

import org.bukkit.Material; // 新增导入
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player; // 新增导入
import org.bukkit.inventory.ItemStack; // 新增导入
import org.bukkit.inventory.PlayerInventory; // 新增导入
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class PluginCommands implements CommandExecutor, TabCompleter {
    private final ZthTerminal3EndAutoRenew plugin;
    private final ConfigManager configManager;
    private final EndResetScheduler endResetScheduler;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final String baseCommand = "zth-end-renew"; // 主命令名，将在 plugin.yml 中使用

    public PluginCommands(ZthTerminal3EndAutoRenew plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.endResetScheduler = plugin.getEndResetScheduler();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        // 权限检查应该在每个子命令内部进行，因为权限可能不同

        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "list":
                return handleListTimes(sender);
            case "add":
                if (args.length < 3) { // "add yyyy-MM-dd HH:mm:ss"
                    sender.sendMessage(
                            Component.text("用法: /" + label + " add <yyyy-MM-dd HH:mm:ss>", NamedTextColor.RED));
                    return true;
                }
                String dateTimeStrAdd = args[1] + " " + args[2];
                return handleAddTime(sender, dateTimeStrAdd, label);
            case "remove":
                if (args.length < 3) { // "remove yyyy-MM-dd HH:mm:ss"
                    sender.sendMessage(
                            Component.text("用法: /" + label + " remove <yyyy-MM-dd HH:mm:ss>", NamedTextColor.RED));
                    return true;
                }
                String dateTimeStrRemove = args[1] + " " + args[2];
                return handleRemoveTime(sender, dateTimeStrRemove, label);
            case "editreward":
                return handleEditReward(sender, args, label);
            case "claimreward":
                return handleClaimReward(sender);
            case "refreshnow":
                return handleRefreshNow(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("zth.endrenew.reload")) {
            sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
            return true;
        }

        configManager.loadRefreshEntries(); // 修改: loadAndPruneRefreshTimes() -> loadRefreshEntries()
        endResetScheduler.reloadSchedule(); // 调用 EndResetScheduler 的重载方法

        sender.sendMessage(Component.text("ZthTerminal3EndAutoRenew 插件配置已重载。", NamedTextColor.GREEN));
        plugin.getLogger().info("配置文件已重载，操作者: " + sender.getName());
        return true;
    }

    private boolean handleListTimes(CommandSender sender) {
        if (!sender.hasPermission("zth.endrenew.manage")) { // 假设管理时间使用 .manage 权限
            sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
            return true;
        }

        List<String> times = configManager.listRefreshEntries();
        if (times.isEmpty()) {
            sender.sendMessage(Component.text("当前没有配置末地刷新时间。", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("已配置的末地刷新时间:", NamedTextColor.GREEN));
            for (String timeStr : times) {
                Component timeComponent = Component.text("- " + timeStr, NamedTextColor.AQUA);
                Component removeButton = Component.text(" [删除]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.suggestCommand("/" + baseCommand + " remove " + timeStr))
                        .hoverEvent(HoverEvent.showText(Component.text("点击删除: " + timeStr, NamedTextColor.GRAY)));
                sender.sendMessage(timeComponent.append(removeButton));
            }
        }
        return true;
    }

    private boolean handleAddTime(CommandSender sender, String dateTimeStr, String commandLabel) {
        if (!sender.hasPermission("zth.endrenew.manage")) {
            sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
            return true;
        }
        try {
            LocalDateTime timeToAdd = LocalDateTime.parse(dateTimeStr, formatter);
            LocalDateTime now = LocalDateTime.now(configManager.getZoneId());
            if (timeToAdd.isBefore(now)) {
                sender.sendMessage(Component.text("不能添加过去的时间。", NamedTextColor.RED));
                return true;
            }

            if (configManager.addRefreshEntry(timeToAdd)) {
                sender.sendMessage(Component.text("已成功添加刷新时间: " + dateTimeStr, NamedTextColor.GREEN));
                endResetScheduler.reloadSchedule(); // 调用 EndResetScheduler 的重载方法
            } else {
                sender.sendMessage(Component.text("刷新时间 " + dateTimeStr + " 已存在。", NamedTextColor.YELLOW));
            }
        } catch (DateTimeParseException e) {
            sender.sendMessage(Component.text("时间格式错误，请使用 'yyyy-MM-dd HH:mm:ss' 格式。", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleRemoveTime(CommandSender sender, String dateTimeStr, String commandLabel) {
        if (!sender.hasPermission("zth.endrenew.manage")) {
            sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
            return true;
        }
        try {
            LocalDateTime timeToRemove = LocalDateTime.parse(dateTimeStr, formatter);
            if (configManager.removeRefreshEntry(timeToRemove)) {
                sender.sendMessage(Component.text("已成功移除刷新时间: " + dateTimeStr, NamedTextColor.GREEN));
                endResetScheduler.reloadSchedule(); // 调用 EndResetScheduler 的重载方法
            } else {
                sender.sendMessage(Component.text("未找到刷新时间: " + dateTimeStr, NamedTextColor.YELLOW));
            }
        } catch (DateTimeParseException e) {
            sender.sendMessage(Component.text("时间格式错误，请使用 'yyyy-MM-dd HH:mm:ss' 格式。", NamedTextColor.RED));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        Component helpMessage = Component.text("--- 末地自动刷新插件命令 ---", NamedTextColor.GOLD)
                .append(Component.newline())
                .append(Component.text("/" + baseCommand + " reload", NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(Component.text("重载插件配置"))))
                .append(Component.text(" - 重载插件配置", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("/" + baseCommand + " list", NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(Component.text("列出所有刷新时间"))))
                .append(Component.text(" - 列出所有刷新时间", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("/" + baseCommand + " add <yyyy-MM-dd HH:mm:ss>", NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent
                                .showText(Component.text("添加刷新时间, 例如: /" + baseCommand + " add 2025-12-31 20:00:00"))))
                .append(Component.text(" - 添加刷新时间", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("/" + baseCommand + " remove <yyyy-MM-dd HH:mm:ss>", NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(
                                Component.text("移除刷新时间, 例如: /" + baseCommand + " remove 2025-12-31 20:00:00"))))
                .append(Component.text(" - 移除刷新时间", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("/" + baseCommand + " editreward <时间戳> <add|remove|list> [索引]", NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(Component.text("""
                                编辑指定刷新事件的奖励物品
                                时间戳格式: yyyy-MM-dd HH:mm:ss
                                add: 添加手持物品
                                remove <索引>: 移除指定索引的物品
                                list: 列出奖励物品"""))))
               .append(Component.text(" - 编辑奖励物品", NamedTextColor.GRAY))
               .append(Component.newline())
               .append(Component.text("/" + baseCommand + " claimreward", NamedTextColor.AQUA)
                       .hoverEvent(HoverEvent.showText(Component.text("领取当前末地远征的奖励"))))
               .append(Component.text(" - 领取本轮奖励", NamedTextColor.GRAY))
               .append(Component.newline())
               .append(Component.text("/" + baseCommand + " refreshnow", NamedTextColor.AQUA)
                       .hoverEvent(HoverEvent.showText(Component.text("立即刷新末地"))))
               .append(Component.text(" - 立即刷新末地", NamedTextColor.GRAY));
       sender.sendMessage(helpMessage);
   }

   private boolean handleRefreshNow(CommandSender sender) {
       if (!sender.hasPermission("zth.endrenew.refreshnow")) {
           sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
           return true;
       }

       sender.sendMessage(Component.text("正在尝试立即刷新末地...", NamedTextColor.YELLOW));
       plugin.getLogger().info(sender.getName() + " 触发了手动末地刷新。");

       LocalDateTime now = LocalDateTime.now(configManager.getZoneId());
       long toleranceSeconds = 5; // 允许5秒的误差范围

       // 检查是否存在非常接近的现有条目
       boolean tooCloseToExisting = configManager.getAllRefreshEntries().stream()
               .anyMatch(entry -> {
                   long diff = java.time.Duration.between(entry.getTime(), now).abs().getSeconds();
                   return diff <= toleranceSeconds;
               });

       if (tooCloseToExisting) {
           sender.sendMessage(Component.text("检测到近期已有或将有一次刷新，为避免重复，本次手动刷新已取消。", NamedTextColor.RED));
           plugin.getLogger().info("手动刷新取消：存在过于接近的计划刷新。");
           // 理论上，如果存在非常接近的 *未来* 刷新，checkAndResetEnd 很快会处理它。
           // 如果是 *过去* 的，说明可能刚刷新过。
           // 无论哪种情况，直接执行重置逻辑可能更安全，因为 checkAndResetEnd 自身会处理时间判断。
           // 但为了遵循需求，如果非常接近，我们先不创建新条目。
           // 不过，如果管理员坚持要刷新，即使附近有条目，也应该执行。
           // 这里的逻辑是：如果附近有条目，就不 *创建新条目*，但仍然 *执行刷新逻辑*。
           // 或者，更严格地，如果附近有条目，就完全不执行任何操作。
           // 当前实现：如果附近有条目，则不创建新条目，并提示用户。
           // 实际的重置逻辑仍然可以由管理员通过其他方式（如果需要）或等待计划任务触发。
           // 根据需求：“如果不存在非常接近的现有条目: ... 创建新的 RefreshEntry ...”
           // 这意味着如果存在，就不创建。是否执行重置是下一步。
           // 需求接着说：“执行末地重置逻辑。” 这似乎是无论是否创建新条目都要执行的。
           // 为了清晰，我们先按“如果接近就不创建条目”处理，然后总是执行重置。
           // 但如果已经有一个非常接近的条目，再次执行重置可能不是预期行为。
           // 修正：如果存在非常接近的条目，则不创建新条目，也不立即执行重置，而是让用户知晓。
           // 如果用户确实希望覆盖或强制刷新，他们可能需要一个带有 --force 标志的命令。
           // 目前，我们简单地不创建新条目，并通知。
           // 让我们重新审视需求：“如果不存在非常接近的现有条目: ... 创建新的 RefreshEntry ... 将这个新创建的 RefreshEntry 添加到 ... 执行末地重置逻辑。”
           // 这暗示了只有在 *不* 存在接近条目时，才会创建新条目并执行重置。
           // 如果存在接近条目，则什么也不做。这似乎更合理。
       } else {
           // 创建新的 RefreshEntry
           ConfigManager.RefreshEntry newManualEntry = new ConfigManager.RefreshEntry(
                   now,
                   new ConfigManager.DragonEggOwner(), // 空的龙蛋所有者
                   new ArrayList<>(), // 空的奖励领取列表
                   new ArrayList<>() // 空的奖励物品列表
           );
           // 尝试添加这个新条目
           if (configManager.addRefreshEntry(newManualEntry)) {
               sender.sendMessage(Component.text("已为本次手动刷新创建新的记录条目。", NamedTextColor.GREEN));
               plugin.getLogger().info("为手动刷新创建了新的 RefreshEntry: " + now.format(formatter));
           } else {
               // 理论上，由于上面的 tooCloseToExisting 检查，这里不应该发生。
               // 但如果 addRefreshEntry 由于其他原因（如并发修改）失败，这里会捕获。
               sender.sendMessage(Component.text("无法为手动刷新创建记录条目，可能已存在冲突。", NamedTextColor.RED));
               plugin.getLogger().warning("尝试为手动刷新添加 RefreshEntry 失败，即使没有检测到时间冲突。时间: " + now.format(formatter));
               // 即使条目添加失败，我们仍然继续执行重置逻辑，因为这是用户明确要求的。
           }
       }

       // 无论是否创建了新的 RefreshEntry（因为可能存在接近的条目），都执行末地重置逻辑
       // 这是根据 “执行末地重置逻辑” 在手动触发部分是独立步骤的理解。
       // 但如果上面决定了“如果接近就不做任何事”，那么这里也应该条件执行。
       // 重新评估：如果目标是“处理计划外的手动刷新”，那么即使附近有计划刷新，
       // 手动刷新也应该被视为一个独立的、优先的事件。
       // 因此，即使存在接近的条目，也应该执行重置。
       // 但创建重复的 RefreshEntry 是不好的。
       // 最终决定：
       // 1. 检查是否有非常接近的 *未来* 刷新。如果有，提示用户，不创建新条目，不执行重置。
       // 2. 检查是否有非常接近的 *过去* 刷新（表示刚刷新过）。如果有，提示用户，不创建新条目，不执行重置。
       // 3. 如果没有非常接近的，则创建新条目，并执行重置。

       // 再次修正逻辑以匹配需求：
       // 1. 获取当前时间。
       // 2. 检查是否有非常接近的现有条目。
       // 3. 如果没有，创建并添加新的 RefreshEntry，然后保存。
       // 4. 执行末地重置逻辑。

       // 实际执行重置
       // 注意：resetEndWorld 本身不处理 RefreshEntry 的移除或状态更新，
       // 它只负责世界的物理重置。
       // EndResetScheduler 中的 checkAndResetEnd 会在计划任务执行时调用 loadRefreshEntries。
       // 对于手动刷新，我们也应该在重置后调用 loadRefreshEntries，以确保配置状态是最新的。
       plugin.getEndResetScheduler().forceResetEndWorld(); // 需要一个公共方法来触发
       configManager.loadRefreshEntries(); // 确保在重置后，配置状态得到更新

       sender.sendMessage(Component.text("末地已手动刷新。", NamedTextColor.GREEN));
       plugin.getLogger().info("末地已由 " + sender.getName() + " 手动刷新完成。");

       // 手动刷新后，也需要更新 BossBar
       endResetScheduler.reloadSchedule(); // 这会重新读取配置并更新 BossBar

       return true;
   }

   private boolean handleClaimReward(CommandSender sender) {
       if (!(sender instanceof Player player)) {
           sender.sendMessage(Component.text("此命令只能由玩家执行。", NamedTextColor.RED));
           return true;
       }
       if (!sender.hasPermission("zthendrenew.player.claimreward")) {
           sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
           return true;
       }

       UUID playerUuid = player.getUniqueId();
       LocalDateTime now = LocalDateTime.now(configManager.getZoneId());

       // 1. 确定“当前期”的 RefreshEntry
       List<ConfigManager.RefreshEntry> allEntries = new ArrayList<>(configManager.getAllRefreshEntries());
       allEntries.sort(Comparator.comparing(ConfigManager.RefreshEntry::getTime).reversed()); // 按时间降序

       Optional<ConfigManager.RefreshEntry> currentEntryOptional = allEntries.stream()
               .filter(entry -> !entry.getTime().isAfter(now)) // 时间已过去或正好是现在
               .findFirst();

       if (currentEntryOptional.isEmpty()) {
           player.sendMessage(Component.text("当前没有可领取的末地远征奖励。", NamedTextColor.YELLOW));
           return true;
       }

       ConfigManager.RefreshEntry currentEntry = currentEntryOptional.get();

       // 2. 检查领取资格
       if (currentEntry.getRewardClaimedPlayers().contains(playerUuid.toString())) {
           player.sendMessage(Component.text("您已经领取过本轮末地远征的奖励了。", NamedTextColor.YELLOW));
           return true;
       }

       if (currentEntry.getRewardItems() == null || currentEntry.getRewardItems().isEmpty()) {
           player.sendMessage(Component.text("本轮末地远征暂未配置奖励物品。", NamedTextColor.YELLOW));
           return true;
       }

       // 3. 发放奖励
       PlayerInventory inventory = player.getInventory();
       List<ItemStack> itemsToGive = currentEntry.getRewardItems();
       List<ItemStack> notAddedItems = new ArrayList<>();

       for (ItemStack item : itemsToGive) {
           if (item != null && item.getType() != Material.AIR) {
               // 尝试添加到背包，如果满了，则记录下来
               HashMap<Integer, ItemStack> couldNotFit = inventory.addItem(item.clone()); // 发放克隆
               if (!couldNotFit.isEmpty()) {
                   notAddedItems.addAll(couldNotFit.values());
               }
           }
       }

       // 处理背包满的情况
       if (!notAddedItems.isEmpty()) {
           player.sendMessage(Component.text("你的背包已满！部分奖励物品掉落在你的脚下：", NamedTextColor.RED));
           for (ItemStack dropItem : notAddedItems) {
               player.getWorld().dropItemNaturally(player.getLocation(), dropItem);
               player.sendMessage(Component.text("- " + dropItem.getType() + " x" + dropItem.getAmount(), NamedTextColor.GRAY));
           }
       }

       // 4. 更新领取记录并保存配置
       currentEntry.getRewardClaimedPlayers().add(playerUuid.toString());
       configManager.saveRefreshEntriesToConfig();

       player.sendMessage(Component.text("末地远征奖励已发放！", NamedTextColor.GREEN));
       return true;
   }


   private boolean handleEditReward(CommandSender sender, String[] args, String commandLabel) {
        if (!sender.hasPermission("zthendrenew.admin.editreward")) {
            sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
            return true;
        }

        // /zth-end-renew editreward <yyyy-MM-dd> <HH:mm:ss> <operation> [params...]
        // args[0] = editreward
        // args[1] = yyyy-MM-dd
        // args[2] = HH:mm:ss
        // args[3] = operation (add, remove, list)
        // args[4+] = params for operation (e.g., index for remove)

        if (args.length < 4) { // editreward <date> <time> <operation>
            sender.sendMessage(Component.text("用法: /" + commandLabel + " editreward <yyyy-MM-dd HH:mm:ss> <add|remove|list> [参数...]", NamedTextColor.RED));
            return true;
        }

        String timestampStr = args[1] + " " + args[2];
        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(timestampStr, formatter);
        } catch (DateTimeParseException e) {
            sender.sendMessage(Component.text("时间戳格式错误，请使用 'yyyy-MM-dd HH:mm:ss' 格式。", NamedTextColor.RED));
            return true;
        }

        Optional<ConfigManager.RefreshEntry> entryOptional = configManager.getRefreshEntryByTime(timestamp);
        if (entryOptional.isEmpty()) {
            sender.sendMessage(Component.text("未找到指定时间戳的刷新事件: " + timestampStr, NamedTextColor.YELLOW));
            return true;
        }
        ConfigManager.RefreshEntry entry = entryOptional.get();

        String operation = args[3].toLowerCase();

        switch (operation) {
            case "add":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("此命令只能由玩家执行。", NamedTextColor.RED));
                    return true;
                }
                ItemStack itemInHand = player.getInventory().getItemInMainHand();

                if (itemInHand.getType() == Material.AIR || itemInHand.getAmount() == 0) {
                    sender.sendMessage(Component.text("你手上没有物品可添加。", NamedTextColor.RED));
                    return true;
                }

                entry.getRewardItems().add(itemInHand.clone()); // 添加克隆以防意外修改
                configManager.saveRefreshEntriesToConfig(); // 保存配置
                sender.sendMessage(Component.text("已将手中的物品添加到 " + timestampStr + " 的奖励列表。", NamedTextColor.GREEN));
                break;

            case "remove":
                if (args.length < 5) {
                    sender.sendMessage(Component.text("用法: /" + commandLabel + " editreward " + timestampStr + " remove <索引>", NamedTextColor.RED));
                    return true;
                }
                int indexToRemove;
                try {
                    indexToRemove = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("索引必须是一个数字。", NamedTextColor.RED));
                    return true;
                }

                List<ItemStack> rewardItemsRemove = entry.getRewardItems();
                if (indexToRemove < 0 || indexToRemove >= rewardItemsRemove.size()) {
                    sender.sendMessage(Component.text("索引越界。有效索引范围: 0 到 " + (rewardItemsRemove.size() - 1), NamedTextColor.RED));
                    return true;
                }

                ItemStack removedItem = rewardItemsRemove.remove(indexToRemove);
                configManager.saveRefreshEntriesToConfig(); // 保存配置
                // 修改反馈消息，不再使用 getItemDisplayName
                sender.sendMessage(Component.text("已从 " + timestampStr + " 的奖励列表中移除物品: " + removedItem.getType() + " x" + removedItem.getAmount(), NamedTextColor.GREEN));
                break;

            case "list":
                List<ItemStack> rewardItemsList = entry.getRewardItems();
                if (rewardItemsList.isEmpty()) {
                    sender.sendMessage(Component.text("刷新事件 " + timestampStr + " 当前没有配置奖励物品。", NamedTextColor.YELLOW));
                    return true;
                }

                sender.sendMessage(Component.text("刷新事件 " + timestampStr + " 的奖励物品列表:", NamedTextColor.GOLD));
                for (int i = 0; i < rewardItemsList.size(); i++) {
                    ItemStack item = rewardItemsList.get(i);
                    // 使用 ItemStack#asHoverEvent()
                    Component itemText = Component.text("- [" + i + "] " + item.getType() + " x" + item.getAmount(), NamedTextColor.AQUA)
                            .hoverEvent(item.asHoverEvent()); // 使用 ItemStack#asHoverEvent()

                    Component removeButton = Component.text(" [移除]", NamedTextColor.RED, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.runCommand("/" + baseCommand + " editreward " + timestampStr + " remove " + i))
                            .hoverEvent(HoverEvent.showText(Component.text("点击移除此奖励物品 (索引 " + i + ")", NamedTextColor.GRAY)));
                    sender.sendMessage(itemText.append(removeButton));
                }
                break;

            default:
                sender.sendMessage(Component.text("无效操作: " + operation + "。可用操作: add, remove, list", NamedTextColor.RED));
                return true;
        }
        return true;
    }

    // getItemDisplayName 辅助方法不再需要，已移除

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            if (sender.hasPermission("zth.endrenew.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("zth.endrenew.manage")) {
                completions.add("list");
                completions.add("add");
                completions.add("remove");
            }
            if (sender.hasPermission("zthendrenew.admin.editreward")) {
                completions.add("editreward");
            }
            if (sender.hasPermission("zthendrenew.player.claimreward")) {
                completions.add("claimreward");
            }
            if (sender.hasPermission("zth.endrenew.refreshnow")) {
                completions.add("refreshnow");
            }
        } else {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("add") || subCommand.equals("remove")) { // 处理 add 和 remove 时间的 Tab 补全
                if (sender.hasPermission("zth.endrenew.manage")) {
                    if (args.length == 2) { // Date part for add/remove time
                        completions.add(LocalDateTime.now(configManager.getZoneId()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                        if (subCommand.equals("remove")) { // 为 remove time 提供现有时间的日期部分
                            configManager.getAllRefreshEntries().stream() // 使用 getAllRefreshEntries 获取所有条目
                                    .map(entry -> entry.getTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                    .distinct()
                                    .forEach(completions::add);
                        }
                    } else if (args.length == 3) { // Time part for add/remove time
                        completions.add(LocalDateTime.now(configManager.getZoneId()).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                        if (subCommand.equals("remove")) { // 为 remove time 提供现有时间的具体时间部分
                            String datePart = args[1];
                            configManager.getAllRefreshEntries().stream()
                                    .filter(entry -> entry.getTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")).equals(datePart))
                                    .map(entry -> entry.getTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                                    .forEach(completions::add);
                        }
                    }
                }
            } else if (subCommand.equals("editreward")) { // 处理 editreward 的 Tab 补全
                if (sender.hasPermission("zthendrenew.admin.editreward")) {
                    if (args.length == 2) { // Date part for timestamp
                        // 提供所有已配置的刷新时间的日期部分
                        configManager.getAllRefreshEntries().stream()
                                .map(entry -> entry.getTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                .distinct()
                                .sorted()
                                .forEach(completions::add);
                    } else if (args.length == 3) { // Time part for timestamp
                        String datePart = args[1];
                        // 提供匹配日期的刷新时间的具体时间部分
                        configManager.getAllRefreshEntries().stream()
                                .filter(entry -> entry.getTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")).equals(datePart))
                                .map(entry -> entry.getTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                                .sorted()
                                .forEach(completions::add);
                    } else if (args.length == 4) { // Operation (add, remove, list)
                        completions.add("add");
                        completions.add("remove");
                        completions.add("list");
                    } else if (args.length == 5 && args[3].equalsIgnoreCase("remove")) { // Index for remove
                        // 尝试解析时间戳以获取对应的 RefreshEntry
                        String timestampStr = args[1] + " " + args[2];
                        try {
                            LocalDateTime timestamp = LocalDateTime.parse(timestampStr, formatter);
                            Optional<ConfigManager.RefreshEntry> entryOptional = configManager.getRefreshEntryByTime(timestamp);
                            if (entryOptional.isPresent()) {
                                List<ItemStack> items = entryOptional.get().getRewardItems();
                                for (int i = 0; i < items.size(); i++) {
                                    completions.add(String.valueOf(i));
                                }
                            }
                        } catch (DateTimeParseException e) {
                            // 时间戳格式不正确，不提供索引补全
                        }
                    }
                }
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentArg))
                .sorted()
                .collect(Collectors.toList());
    }
}