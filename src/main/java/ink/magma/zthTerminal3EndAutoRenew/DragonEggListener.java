package ink.magma.zthTerminal3EndAutoRenew;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class DragonEggListener implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final BossBarManager bossBarManager; // Add BossBarManager instance

    public DragonEggListener(JavaPlugin plugin, ConfigManager configManager, BossBarManager bossBarManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bossBarManager = bossBarManager; // Initialize BossBarManager
    }


    @EventHandler
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // 确保事件发生在末地
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }

        ItemStack itemStack = event.getItem().getItemStack();
        if (itemStack.getType() != Material.DRAGON_EGG) {
            return;
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        // 确保是原始龙蛋（没有被修改过 Lore 的）
        if (itemMeta == null || !isOriginalDragonEgg(itemMeta)) {
            return;
        }

        // 检查末影龙是否存活
        if (!player.getWorld().getEntitiesByClass(EnderDragon.class).isEmpty()) {
            plugin.getLogger().info("玩家 " + player.getName() + " 尝试拾取龙蛋，但末影龙仍存活");
            return;
        }

        // 确定“当前期”的 RefreshEntry
        Optional<ConfigManager.RefreshEntry> currentEntryOptional = configManager.getCurrentRefreshEntry();

        if (currentEntryOptional.isEmpty()) {
            plugin.getLogger().info("龙蛋被拾取，但未找到当前刷新期条目，不记录拾取者。玩家: " + player.getName());
            return; // 明确：如果找不到当前期，则不记录，也不执行后续操作
        }

        ConfigManager.RefreshEntry currentEntry = currentEntryOptional.get();

        // 检查并记录拾取者
        if (currentEntry.getDragonEggOwner().getUuid() == null) {
            // 记录拾取者信息
            currentEntry.getDragonEggOwner().setUuid(player.getUniqueId());
            currentEntry.getDragonEggOwner().setName(player.getName());
            currentEntry.getDragonEggOwner().setPickupTime(LocalDateTime.now(configManager.getZoneId()));

            configManager.saveRefreshEntriesToConfig(); // 保存更改到配置文件

            plugin.getLogger().info("玩家 " + player.getName() + " 拾取了龙蛋，已记录。刷新期: " + currentEntry.getTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // 执行原有后续操作
            addLoreToDragonEgg(itemMeta, player);
            itemStack.setItemMeta(itemMeta);
            announceDragonEggWinner(player);
        } else {
            // 本期龙蛋得主已被记录
            plugin.getLogger().info("玩家 " + player.getName() + " 尝试拾取龙蛋，但本期得主 (" + currentEntry.getDragonEggOwner().getName() + ") 已被记录。刷新期: " + currentEntry.getTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            // 不执行任何操作，不覆盖记录，也不重复执行后续操作
            // 注意：这里需要阻止物品被拾取，或者在拾取后移除 Lore（如果已错误添加）
            // 为简单起见，我们假设如果已被记录，玩家拾取到的龙蛋不应该再被添加新的 Lore
            // 如果玩家拾取的是一个已经有 Lore 的龙蛋（非原始），isOriginalDragonEgg 会处理
            // 如果玩家拾取的是原始龙蛋，但记录已存在，则不应添加 Lore 和广播
            // 因此，如果记录已存在，我们直接返回，不执行 addLore 和 announce
        }
    }

    private boolean isOriginalDragonEgg(ItemMeta meta) {
        if (!meta.hasLore() || meta.lore() == null || meta.lore().isEmpty()) {
            return true; // 没有 Lore，是原始龙蛋
        }

        // 兼容 Adventure API，判断首行 plainText 是否为归属标记
        Component firstLoreComponent = meta.lore().get(0);
        String firstLorePlain = PlainTextComponentSerializer.plainText().serialize(firstLoreComponent);

        // 获取配置中的 Lore 模板（移除变量部分）
        String configLoreTemplatePlain = "";
        if (configManager.dragonEggLore != null && !configManager.dragonEggLore.isEmpty()) {
            Component configTemplateComponent = MiniMessage.miniMessage()
                    .deserialize(configManager.dragonEggLore.get(0).replace("{player}", "").replace("{date}", ""));
            configLoreTemplatePlain = PlainTextComponentSerializer.plainText().serialize(configTemplateComponent);
        }

        // 如果 Lore 模板为空，或者首行 Lore 不包含模板内容，则认为是原始龙蛋
        return configLoreTemplatePlain.isEmpty() || !firstLorePlain.contains(configLoreTemplatePlain);
    }

    private void addLoreToDragonEgg(ItemMeta meta, Player player) {
        List<Component> lore = new java.util.ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String date = fmt.format(LocalDateTime.now(configManager.getZoneId()));

        for (String line : configManager.dragonEggLore) {
            String replaced = line.replace("{player}", player.getName()).replace("{date}", date);
            lore.add(MiniMessage.miniMessage().deserialize(replaced));
        }
        meta.lore(lore);
    }

    private void announceDragonEggWinner(Player player) {
        String msg = configManager.announceWinner.replace("{player}", player.getName());
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(msg));
    }

    // 监听龙蛋掉入虚空并复原
    @EventHandler
    public void onDragonEggVoid(org.bukkit.event.entity.ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack stack = item.getItemStack();
        if (stack.getType() == Material.DRAGON_EGG) {
            // 若龙蛋生成在末地且 Y<0，判定为掉入虚空
            World world = item.getWorld();
            if (world.getEnvironment() == World.Environment.THE_END && item.getLocation().getY() < 0) {
                // 立即移除掉落的龙蛋实体
                item.remove();

                // 公告龙蛋即将重置
                String announceMsg = configManager.announceEggReset;
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(announceMsg));

                // 使用 BossBarManager 显示倒计时
                final String bossBarId = "dragon_egg_reset_countdown";
                bossBarManager.showGlobalCountdownBossBar(
                        bossBarId,
                        configManager.eggResetBossBarTitle, // Title template from config
                        BarColor.PINK, // Example color
                        BarStyle.SOLID, // Example style
                        60, // 60 seconds countdown
                        () -> { // Runnable to execute on finish
                            // 寻找末地传送门中心的最高基岩方块
                            org.bukkit.Location bedrockLocation = null;
                            for (int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
                                org.bukkit.block.Block block = world.getBlockAt(0, y, 0);
                                if (block.getType() == Material.BEDROCK) {
                                    bedrockLocation = block.getLocation();
                                    break;
                                }
                            }

                            if (bedrockLocation != null) {
                                // 在基岩上方放置龙蛋
                                world.getBlockAt(bedrockLocation.getBlockX(), bedrockLocation.getBlockY() + 1,
                                        bedrockLocation.getBlockZ()).setType(Material.DRAGON_EGG);
                            } else {
                                // 如果没有找到基岩（理论上不应该发生），则在默认位置生成
                                world.getBlockAt(0, 65, 0).setType(Material.DRAGON_EGG);
                                plugin.getLogger().warning(
                                        "在末地的 (0,0) 处找不到基岩。正在将龙蛋重置到默认位置。");
                            }
                            // 可以在这里再发一个公告，说明龙蛋已重置
                            Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<white>龙蛋已成功重置！</white>"));
                        },
                        null // Show to all online players
                );
            }
        }
    }
}