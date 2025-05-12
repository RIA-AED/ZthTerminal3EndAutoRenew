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
import java.util.Collections; // 新增导入
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

        List<String> times = configManager.listRefreshTimes();
        if (times.isEmpty()) {
            sender.sendMessage(Component.text("当前没有配置末地刷新时间。", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("已配置的末地刷新时间:", NamedTextColor.GREEN));
            for (String timeStr : times) {
                Component timeComponent = Component.text("- " + timeStr, NamedTextColor.AQUA);
                Component removeButton = Component.text(" [删除]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/" + baseCommand + " remove " + timeStr))
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

            if (configManager.addRefreshTime(timeToAdd)) {
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
            if (configManager.removeRefreshTime(timeToRemove)) {
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
                        .hoverEvent(HoverEvent.showText(Component.text("编辑指定刷新事件的奖励物品\n" +
                                "时间戳格式: yyyy-MM-dd HH:mm:ss\n" +
                                "add: 添加手持物品\n" +
                                "remove <索引>: 移除指定索引的物品\n" +
                                "list: 列出奖励物品"))))
               .append(Component.text(" - 编辑奖励物品", NamedTextColor.GRAY))
               .append(Component.newline())
               .append(Component.text("/" + baseCommand + " claimreward", NamedTextColor.AQUA)
                       .hoverEvent(HoverEvent.showText(Component.text("领取当前末地远征的奖励"))))
               .append(Component.text(" - 领取本轮奖励", NamedTextColor.GRAY));
       sender.sendMessage(helpMessage);
   }

   private boolean handleClaimReward(CommandSender sender) {
       if (!(sender instanceof Player)) {
           sender.sendMessage(Component.text("此命令只能由玩家执行。", NamedTextColor.RED));
           return true;
       }
       if (!sender.hasPermission("zthendrenew.player.claimreward")) {
           sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
           return true;
       }

       Player player = (Player) sender;
       UUID playerUuid = player.getUniqueId();
       LocalDateTime now = LocalDateTime.now(configManager.getZoneId());

       // 1. 确定“当前期”的 RefreshEntry
       List<ConfigManager.RefreshEntry> allEntries = new ArrayList<>(configManager.getAllRefreshEntries());
       Collections.sort(allEntries, Comparator.comparing(ConfigManager.RefreshEntry::getTime).reversed()); // 按时间降序

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
               player.sendMessage(Component.text("- " + dropItem.getType().toString() + " x" + dropItem.getAmount(), NamedTextColor.GRAY));
           }
       }

       // 4. 更新领取记录并保存配置
       currentEntry.getRewardClaimedPlayers().add(playerUuid.toString());
       configManager.saveRefreshTimesToConfig();

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
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.text("此命令只能由玩家执行。", NamedTextColor.RED));
                    return true;
                }
                Player player = (Player) sender;
                ItemStack itemInHand = player.getInventory().getItemInMainHand();

                if (itemInHand.getType() == Material.AIR || itemInHand.getAmount() == 0) {
                    sender.sendMessage(Component.text("你手上没有物品可添加。", NamedTextColor.RED));
                    return true;
                }

                entry.getRewardItems().add(itemInHand.clone()); // 添加克隆以防意外修改
                configManager.saveRefreshTimesToConfig(); // 保存配置
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
                configManager.saveRefreshTimesToConfig(); // 保存配置
                // 修改反馈消息，不再使用 getItemDisplayName
                sender.sendMessage(Component.text("已从 " + timestampStr + " 的奖励列表中移除物品: " + removedItem.getType().toString() + " x" + removedItem.getAmount(), NamedTextColor.GREEN));
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
                    Component itemText = Component.text("- [" + i + "] " + item.getType().toString() + " x" + item.getAmount(), NamedTextColor.AQUA)
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
        } else if (args.length >= 2) {
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