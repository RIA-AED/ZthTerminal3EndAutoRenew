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
        // 启动末地刷新调度器
        endResetScheduler = new EndResetScheduler(this, configManager);
        // 注册 BossBar 管理器监听器
        bossBarManager = new BossBarManager(this, configManager);
        getServer().getPluginManager().registerEvents(bossBarManager, this); // Register BossBarManager events

        // 注册龙蛋监听器
        DragonEggListener dragonEggListener = new DragonEggListener(this, configManager, bossBarManager); // Pass BossBarManager
        getServer().getPluginManager().registerEvents(dragonEggListener, this);

        endResetScheduler.start();
        configManager.loadAndPruneRefreshTimes();

        // 注册新的统一命令处理器
        PluginCommands pluginCommands = new PluginCommands(this);
        getCommand("zthautorenew").setExecutor(pluginCommands);
        getCommand("zthautorenew").setTabCompleter(pluginCommands);
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
