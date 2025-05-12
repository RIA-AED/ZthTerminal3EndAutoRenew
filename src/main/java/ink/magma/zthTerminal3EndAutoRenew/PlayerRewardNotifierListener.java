package ink.magma.zthTerminal3EndAutoRenew;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;
import java.util.UUID;

public class PlayerRewardNotifierListener implements Listener {

    private final ZthTerminal3EndAutoRenew plugin;
    private final ConfigManager configManager;

    public PlayerRewardNotifierListener(ZthTerminal3EndAutoRenew plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon && event.getEntity().getWorld().getEnvironment() == World.Environment.THE_END) {
            World endWorld = event.getEntity().getWorld();
            if (noLivingEnderDragon(endWorld)) {
                Optional<ConfigManager.RefreshEntry> currentEntryOpt = configManager.getCurrentRefreshEntry();
                if (currentEntryOpt.isPresent()) {
                    ConfigManager.RefreshEntry currentEntry = currentEntryOpt.get();
                    if (currentEntry.getRewardItems() != null && !currentEntry.getRewardItems().isEmpty()) {
                        for (Player player : endWorld.getPlayers()) {
                            checkAndNotifyPlayer(player, currentEntry);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
            if (noLivingEnderDragon(player.getWorld())) {
                Optional<ConfigManager.RefreshEntry> currentEntryOpt = configManager.getCurrentRefreshEntry();
                if (currentEntryOpt.isPresent()) {
                    ConfigManager.RefreshEntry currentEntry = currentEntryOpt.get();
                    if (currentEntry.getRewardItems() != null && !currentEntry.getRewardItems().isEmpty()) {
                        checkAndNotifyPlayer(player, currentEntry);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
            if (noLivingEnderDragon(player.getWorld())) {
                Optional<ConfigManager.RefreshEntry> currentEntryOpt = configManager.getCurrentRefreshEntry();
                if (currentEntryOpt.isPresent()) {
                    ConfigManager.RefreshEntry currentEntry = currentEntryOpt.get();
                    if (currentEntry.getRewardItems() != null && !currentEntry.getRewardItems().isEmpty()) {
                        checkAndNotifyPlayer(player, currentEntry);
                    }
                }
            }
        }
    }

    private void checkAndNotifyPlayer(Player player, ConfigManager.RefreshEntry refreshEntry) {
        UUID playerUUID = player.getUniqueId();
        if (!refreshEntry.getRewardClaimedPlayers().contains(playerUUID.toString())) {
            Component message = Component.text("本轮末地远征奖励待领取！", NamedTextColor.GOLD)
                    .append(Component.text(" [点击此处领取]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.runCommand("/zth-end-renew claimreward"))
                            .hoverEvent(HoverEvent.showText(Component.text("执行 /zth-end-renew claimreward"))));
            player.sendMessage(message);
        }
    }

    private boolean noLivingEnderDragon(World endWorld) {
        if (endWorld.getEnvironment() != World.Environment.THE_END) {
            return true; // 不是末地，直接返回false或根据需要抛出异常
        }
        return endWorld.getEntitiesByClass(EnderDragon.class).isEmpty();
    }
}