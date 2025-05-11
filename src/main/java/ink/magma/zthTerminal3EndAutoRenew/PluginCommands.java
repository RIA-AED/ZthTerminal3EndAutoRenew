package ink.magma.zthTerminal3EndAutoRenew;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
    private final String baseCommand = "zthautorenew"; // 主命令名，将在 plugin.yml 中使用

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
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("zthterminal3endautorenew.reload")) {
            sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
            return true;
        }

        configManager.loadAndPruneRefreshTimes();
        endResetScheduler.reloadSchedule(); // 调用 EndResetScheduler 的重载方法

        sender.sendMessage(Component.text("ZthTerminal3EndAutoRenew 插件配置已重载。", NamedTextColor.GREEN));
        plugin.getLogger().info("Configuration reloaded by " + sender.getName());
        return true;
    }

    private boolean handleListTimes(CommandSender sender) {
        if (!sender.hasPermission("zthterminal3endautorenew.manage")) { // 假设管理时间使用 .manage 权限
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
        if (!sender.hasPermission("zthterminal3endautorenew.manage")) {
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
        if (!sender.hasPermission("zthterminal3endautorenew.manage")) {
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
                .append(Component.text(" - 移除刷新时间", NamedTextColor.GRAY));
        sender.sendMessage(helpMessage);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            if (sender.hasPermission("zthterminal3endautorenew.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("zthterminal3endautorenew.manage")) {
                completions.add("list");
                completions.add("add");
                completions.add("remove");
            }
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("add") || subCommand.equals("remove")) {
                if (sender.hasPermission("zthterminal3endautorenew.manage")) {
                    if (args.length == 2) { // Date part
                        completions.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                        if (subCommand.equals("remove")) {
                            configManager.listRefreshTimes().stream()
                                    .map(timeStr -> timeStr.split(" ")[0])
                                    .distinct()
                                    .forEach(completions::add);
                        }
                    } else if (args.length == 3) { // Time part
                        completions.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                        if (subCommand.equals("remove")) {
                            String datePart = args[1];
                            configManager.listRefreshTimes().stream()
                                    .filter(timeStr -> timeStr.startsWith(datePart))
                                    .map(timeStr -> timeStr.split(" ")[1])
                                    .forEach(completions::add);
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