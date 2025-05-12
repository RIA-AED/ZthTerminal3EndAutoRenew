package ink.magma.zthTerminal3EndAutoRenew;

import org.bukkit.plugin.java.JavaPlugin;

public final class ZthTerminal3EndAutoRenew extends JavaPlugin {

    private ConfigManager configManager;
    private EndResetScheduler endResetScheduler;
    private BossBarManager bossBarManager;

    @Override
    public void onEnable() {
        // Plugin startup logic

        // 初始化配置管理器
        configManager = new ConfigManager(this);
        // 初始化 BossBar 管理器
        bossBarManager = new BossBarManager(this);
        // 启动末地刷新调度器 (现在需要 BossBarManager)
        endResetScheduler = new EndResetScheduler(this, configManager, bossBarManager);
        // EndResetScheduler 内部会注册自己为监听器

        // 注册龙蛋监听器
        DragonEggListener dragonEggListener = new DragonEggListener(this, configManager, bossBarManager); // Pass BossBarManager
        getServer().getPluginManager().registerEvents(dragonEggListener, this);

        // 注册玩家奖励提醒监听器
        PlayerRewardNotifierListener playerRewardNotifierListener = new PlayerRewardNotifierListener(this, configManager);
        getServer().getPluginManager().registerEvents(playerRewardNotifierListener, this);

        endResetScheduler.start();
        // configManager.loadAndPruneRefreshTimes(); // 这行似乎在 ConfigManager 的 loadRefreshEntries 中重复了，或者逻辑有调整，暂时注释掉，以 ConfigManager 内部逻辑为准
        configManager.loadRefreshEntries(); // 确保调用的是加载所有条目的方法

        // 注册新的统一命令处理器
        PluginCommands pluginCommands = new PluginCommands(this);
        getCommand("zth-end-renew").setExecutor(pluginCommands);
        getCommand("zth-end-renew").setTabCompleter(pluginCommands);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (endResetScheduler != null) {
            endResetScheduler.stop();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EndResetScheduler getEndResetScheduler() {
        return endResetScheduler;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }
}
