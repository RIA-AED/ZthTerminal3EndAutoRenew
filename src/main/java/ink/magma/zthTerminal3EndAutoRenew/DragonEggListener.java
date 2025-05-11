package ink.magma.zthTerminal3EndAutoRenew;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
// BossBar related imports are no longer needed here directly for creation
import org.bukkit.boss.BarColor; // Keep for passing as parameter
import org.bukkit.boss.BarStyle; // Keep for passing as parameter
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class DragonEggListener implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final BossBarManager bossBarManager; // Add BossBarManager instance

    public DragonEggListener(JavaPlugin plugin, ConfigManager config, BossBarManager bossBarManager) {
        this.plugin = plugin;
        this.config = config;
        this.bossBarManager = bossBarManager; // Initialize BossBarManager
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EnderDragon && entity.getWorld().getEnvironment() == World.Environment.THE_END) {
            // 末影龙死亡，等待龙蛋生成
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World end = entity.getWorld();
                // 检查末地是否已有龙蛋
                boolean hasEgg = false;
                for (Entity e : end.getEntities()) {
                    if (e instanceof Item) {
                        ItemStack stack = ((Item) e).getItemStack();
                        if (stack.getType() == Material.DRAGON_EGG) {
                            hasEgg = true;
                            break;
                        }
                    }
                }
                if (!hasEgg) {
                    // 主动在传送门上方生成龙蛋
                    end.getBlockAt(0, 65, 0).setType(Material.DRAGON_EGG);
                }
            }, 60L); // 延迟3秒
        }
    }

    @EventHandler
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ItemStack itemStack = event.getItem().getItemStack();
        if (itemStack.getType() != Material.DRAGON_EGG) {
            return;
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !isOriginalDragonEgg(itemMeta)) {
            return;
        }

        addLoreToDragonEgg(itemMeta, player);
        itemStack.setItemMeta(itemMeta);
        announceDragonEggWinner(player);
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
        if (config.dragonEggLore != null && !config.dragonEggLore.isEmpty()) {
            Component configTemplateComponent = MiniMessage.miniMessage()
                    .deserialize(config.dragonEggLore.get(0).replace("{player}", "").replace("{date}", ""));
            configLoreTemplatePlain = PlainTextComponentSerializer.plainText().serialize(configTemplateComponent);
        }

        // 如果 Lore 模板为空，或者首行 Lore 不包含模板内容，则认为是原始龙蛋
        return configLoreTemplatePlain.isEmpty() || !firstLorePlain.contains(configLoreTemplatePlain);
    }

    private void addLoreToDragonEgg(ItemMeta meta, Player player) {
        List<Component> lore = new java.util.ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String date = fmt.format(LocalDateTime.now(config.getZoneId()));

        for (String line : config.dragonEggLore) {
            String replaced = line.replace("{player}", player.getName()).replace("{date}", date);
            lore.add(MiniMessage.miniMessage().deserialize(replaced));
        }
        meta.lore(lore);
    }

    private void announceDragonEggWinner(Player player) {
        String msg = config.announceWinner.replace("{player}", player.getName());
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
                String announceMsg = config.announceEggReset;
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(announceMsg));

                // 使用 BossBarManager 显示倒计时
                final String bossBarId = "dragon_egg_reset_countdown";
                bossBarManager.showGlobalCountdownBossBar(
                        bossBarId,
                        config.eggResetBossBarTitle, // Title template from config
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
                            Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<green>龙蛋已成功重置！</green>"));
                        },
                        null // Show to all online players
                );
            }
        }
    }
}