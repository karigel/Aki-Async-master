package org.kari.kariClaims.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.models.Claim;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * GUI 事件监听器
 */
public class GUIListener implements Listener {
    private final KariClaims plugin;
    private final ClaimGUI claimGUI;

    public GUIListener(KariClaims plugin, ClaimGUI claimGUI) {
        this.plugin = plugin;
        this.claimGUI = claimGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());

        if (title.equals("§6领地管理")) {
            event.setCancelled(true);
            handleMainMenuClick(player, event);
        } else if (title.startsWith("§6") && title.contains("领地")) {
            event.setCancelled(true);
            handleClaimDetailsClick(player, event, title);
        } else if (title.startsWith("§6成员管理")) {
            event.setCancelled(true);
            handleMembersMenuClick(player, event);
        }
    }

    private void handleMainMenuClick(Player player, InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null) return;

        int slot = event.getSlot();

        if (slot == 45) {
            // 创建新领地
            player.closeInventory();
            player.sendMessage("§7请使用 /claim pos1 和 /claim pos2 设置位置，然后使用 /claim create 创建领地");
        } else if (slot == 53) {
            // 关闭
            player.closeInventory();
        } else if (slot < 45) {
            // 点击了领地物品
            // 这里需要从物品中解析出领地ID，简化处理：重新查询
            plugin.getClaimManager().getPlayerClaimsAsync(player.getUniqueId())
                .thenAccept(claims -> {
                    if (slot < claims.size()) {
                        Claim claim = claims.get(slot);
                        claimGUI.openClaimDetails(player, claim);
                    }
                });
        }
    }

    private void handleClaimDetailsClick(Player player, InventoryClickEvent event, String title) {
        ItemStack item = event.getCurrentItem();
        if (item == null) return;

        int slot = event.getSlot();
        String claimName = title.replace("§6", "");

        // 查找对应的领地
        plugin.getClaimManager().getPlayerClaimsAsync(player.getUniqueId())
            .thenAccept(claims -> {
                Claim claim = claims.stream()
                    .filter(c -> c.getName().equals(claimName))
                    .findFirst()
                    .orElse(null);

                if (claim == null) return;

                if (slot == 22) {
                    // 成员管理
                    claimGUI.openMembersMenu(player, claim);
                } else if (slot == 40) {
                    // 删除领地
                    player.closeInventory();
                    plugin.getClaimManager().deleteClaim(claim.getId())
                        .thenRun(() -> player.sendMessage("§a领地已删除！"));
                } else if (slot == 49) {
                    // 返回
                    claimGUI.openMainMenu(player);
                }
            });
    }

    private void handleMembersMenuClick(Player player, InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null) return;

        int slot = event.getSlot();

        if (slot == 45) {
            // 添加成员
            player.closeInventory();
            player.sendMessage("§7请使用 /claim add <玩家> 添加成员");
        } else if (slot == 49) {
            // 返回
            claimGUI.openMainMenu(player);
        }
    }
}

