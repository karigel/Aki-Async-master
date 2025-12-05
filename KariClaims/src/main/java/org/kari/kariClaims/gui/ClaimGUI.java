package org.kari.kariClaims.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.models.Claim;
import org.kari.kariClaims.models.ClaimMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * 领地 GUI 管理界面
 */
public class ClaimGUI {
    private final KariClaims plugin;

    public ClaimGUI(KariClaims plugin) {
        this.plugin = plugin;
    }

    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, LegacyComponentSerializer.legacySection().deserialize("§6领地管理"));

        // 获取玩家的领地列表
        plugin.getClaimManager().getPlayerClaimsAsync(player.getUniqueId())
            .thenAccept(claims -> {
                int slot = 0;
                for (Claim claim : claims) {
                    if (slot >= 45) break;
                    
                    ItemStack item = createClaimItem(claim);
                    gui.setItem(slot++, item);
                }

                // 添加功能按钮
                ItemStack createItem = new ItemStack(Material.GRASS_BLOCK);
                ItemMeta createMeta = createItem.getItemMeta();
                createMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§a创建新领地"));
                createMeta.lore(List.of(LegacyComponentSerializer.legacySection().deserialize("§7点击创建新领地")));
                createItem.setItemMeta(createMeta);
                gui.setItem(45, createItem);

                ItemStack closeItem = new ItemStack(Material.BARRIER);
                ItemMeta closeMeta = closeItem.getItemMeta();
                closeMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c关闭"));
                closeItem.setItemMeta(closeMeta);
                gui.setItem(53, closeItem);

                player.openInventory(gui);
            });
    }

    /**
     * 打开领地详情界面
     */
    public void openClaimDetails(Player player, Claim claim) {
        Inventory gui = Bukkit.createInventory(null, 54, LegacyComponentSerializer.legacySection().deserialize("§6" + claim.getName()));

        // 基本信息
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e领地信息"));
        List<Component> lore = new ArrayList<>();
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7ID: §f" + claim.getId()));
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7所有者: §f" + Bukkit.getOfflinePlayer(claim.getOwner()).getName()));
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7世界: §f" + claim.getWorld()));
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7面积: §f" + claim.getArea()));
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7范围: §f" + claim.getMinX() + ", " + claim.getMinZ() + " ~ " + 
            claim.getMaxX() + ", " + claim.getMaxZ()));
        if (!claim.getDescription().isEmpty()) {
            lore.add(LegacyComponentSerializer.legacySection().deserialize("§7描述: §f" + claim.getDescription()));
        }
        infoMeta.lore(lore);
        infoItem.setItemMeta(infoMeta);
        gui.setItem(4, infoItem);

        // 设置按钮
        ItemStack settingsItem = new ItemStack(Material.REDSTONE);
        ItemMeta settingsMeta = settingsItem.getItemMeta();
        settingsMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e领地设置"));
        List<Component> settingsLore = new ArrayList<>();
        settingsLore.add(LegacyComponentSerializer.legacySection().deserialize("§7PVP: " + (claim.isPvpEnabled() ? "§a启用" : "§c禁用")));
        settingsLore.add(LegacyComponentSerializer.legacySection().deserialize("§7生物生成: " + (claim.isMobSpawning() ? "§a启用" : "§c禁用")));
        settingsLore.add(LegacyComponentSerializer.legacySection().deserialize("§7火焰蔓延: " + (claim.isFireSpread() ? "§a启用" : "§c禁用")));
        settingsLore.add(LegacyComponentSerializer.legacySection().deserialize("§7爆炸: " + (claim.isExplosion() ? "§a启用" : "§c禁用")));
        settingsMeta.lore(settingsLore);
        settingsItem.setItemMeta(settingsMeta);
        gui.setItem(20, settingsItem);

        // 成员列表
        ItemStack membersItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta membersMeta = membersItem.getItemMeta();
        membersMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e成员管理"));
        membersMeta.lore(List.of(LegacyComponentSerializer.legacySection().deserialize("§7点击查看和管理成员")));
        membersItem.setItemMeta(membersMeta);
        gui.setItem(22, membersItem);

        // 权限管理
        ItemStack permissionsItem = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta permissionsMeta = permissionsItem.getItemMeta();
        permissionsMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e权限管理"));
        permissionsMeta.lore(List.of(LegacyComponentSerializer.legacySection().deserialize("§7点击管理权限")));
        permissionsItem.setItemMeta(permissionsMeta);
        gui.setItem(24, membersItem);

        // 删除按钮
        ItemStack deleteItem = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta deleteMeta = deleteItem.getItemMeta();
        deleteMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c删除领地"));
        deleteMeta.lore(List.of(LegacyComponentSerializer.legacySection().deserialize("§7警告: 此操作不可撤销！")));
        deleteItem.setItemMeta(deleteMeta);
        gui.setItem(40, deleteItem);

        // 返回按钮
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§7返回"));
        backItem.setItemMeta(backMeta);
        gui.setItem(49, backItem);

        player.openInventory(gui);
    }

    /**
     * 打开成员管理界面
     */
    public void openMembersMenu(Player player, Claim claim) {
        Inventory gui = Bukkit.createInventory(null, 54, LegacyComponentSerializer.legacySection().deserialize("§6成员管理 - " + claim.getName()));

        plugin.getClaimManager().getClaimMembersAsync(claim.getId())
            .thenAccept(members -> {
                int slot = 0;
                for (ClaimMember member : members) {
                    if (slot >= 45) break;

                    ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                    ItemMeta meta = item.getItemMeta();
                    meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e" + Bukkit.getOfflinePlayer(member.getPlayerId()).getName()));
                    List<Component> lore = new ArrayList<>();
                    lore.add(LegacyComponentSerializer.legacySection().deserialize("§7角色: §f" + member.getRole().getDisplayName()));
                    lore.add(LegacyComponentSerializer.legacySection().deserialize("§7加入时间: §f" + new java.util.Date(member.getJoinedAt()).toString()));
                    meta.lore(lore);
                    item.setItemMeta(meta);
                    gui.setItem(slot++, item);
                }

                // 添加成员按钮
                ItemStack addItem = new ItemStack(Material.EMERALD);
                ItemMeta addMeta = addItem.getItemMeta();
                addMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§a添加成员"));
                addItem.setItemMeta(addMeta);
                gui.setItem(45, addItem);

                // 返回按钮
                ItemStack backItem = new ItemStack(Material.ARROW);
                ItemMeta backMeta = backItem.getItemMeta();
                backMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§7返回"));
                backItem.setItemMeta(backMeta);
                gui.setItem(49, backItem);

                player.openInventory(gui);
            });
    }

    /**
     * 创建领地物品
     */
    private ItemStack createClaimItem(Claim claim) {
        ItemStack item = new ItemStack(Material.GOLDEN_SHOVEL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e" + claim.getName()));
        List<Component> lore = new ArrayList<>();
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7ID: §f" + claim.getId()));
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7世界: §f" + claim.getWorld()));
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7面积: §f" + claim.getArea()));
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7点击查看详情"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}

