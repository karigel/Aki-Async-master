package org.kari.kariClaims.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.models.ChunkClaim;
import org.kari.kariClaims.models.PowerCell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * 能量电池 GUI - 类似 UltimateClaims 的界面
 */
public class PowerCellGUI {
    private final KariClaims plugin;

    public PowerCellGUI(KariClaims plugin) {
        this.plugin = plugin;
    }

    /**
     * 自定义 InventoryHolder 用于标识能量电池 GUI
     */
    public static class PowerCellHolder implements org.bukkit.inventory.InventoryHolder {
        private final ChunkClaim claim;
        
        public PowerCellHolder(ChunkClaim claim) {
            this.claim = claim;
        }
        
        public ChunkClaim getClaim() {
            return claim;
        }
        
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /**
     * 打开能量电池主界面
     */
    public void openPowerCellGUI(Player player, ChunkClaim claim, PowerCell powerCell) {
        // 使用领地名称作为GUI标题
        String claimName = getClaimDisplayName(claim);
        Inventory gui = Bukkit.createInventory(new PowerCellHolder(claim), 54, LegacyComponentSerializer.legacySection().deserialize(claimName));

        // 第一行：功能按钮
        setupFirstRow(gui, claim, powerCell);

        // 中间部分（第2-5行）：物品能源存储区域
        // 填充左右两列的玻璃板
        ItemStack glassPane = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glassPane.setItemMeta(glassMeta);
        
        // 填充左列（槽位9, 18, 27, 36）和右列（槽位17, 26, 35, 44）
        gui.setItem(9, glassPane);   // 第二行左
        gui.setItem(17, glassPane);  // 第二行右
        gui.setItem(18, glassPane);  // 第三行左
        gui.setItem(26, glassPane);  // 第三行右
        gui.setItem(27, glassPane);  // 第四行左
        gui.setItem(35, glassPane);   // 第四行右
        gui.setItem(36, glassPane);  // 第五行左
        gui.setItem(44, glassPane);  // 第五行右
        
        // 从能量电池箱子加载物品到中间区域
        // 可用区域：10-16, 19-25, 28-34, 37-43（共28个槽位）
        // 映射到箱子的0-26槽位（27个槽位）
        if (powerCell.getLocation() != null && powerCell.getLocation().getBlock().getType() == Material.CHEST) {
            // 重新获取箱子状态，确保是最新的
            org.bukkit.block.Block chestBlock = powerCell.getLocation().getBlock();
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) chestBlock.getState();
            Inventory chestInv = chest.getInventory();
            
            // GUI可用槽位数组：10-16, 19-25, 28-34, 37-43
            int[] guiSlots = {10, 11, 12, 13, 14, 15, 16,  // 第二行中间
                              19, 20, 21, 22, 23, 24, 25,  // 第三行中间
                              28, 29, 30, 31, 32, 33, 34,  // 第四行中间
                              37, 38, 39, 40, 41, 42, 43}; // 第五行中间
            
            // 只加载前27个槽位（即使箱子是双箱）
            int maxSlots = Math.min(27, chestInv.getSize());
            for (int i = 0; i < Math.min(maxSlots, guiSlots.length); i++) {
                ItemStack item = chestInv.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    gui.setItem(guiSlots[i], item.clone());
                }
            }
        } else {
            plugin.getLogger().warning("无法加载物品：能量电池位置无效或不是箱子");
        }

        // 最后一行：设置按钮
        setupLastRow(gui, claim);

        player.openInventory(gui);
    }

    /**
     * 设置第一行按钮
     */
    private void setupFirstRow(Inventory gui, ChunkClaim claim, PowerCell powerCell) {
        // 玻璃板填充
        ItemStack glassPane = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glassPane.setItemMeta(glassMeta);
        
        // 计算箱子中物品的总价值
        long chestItemValue = 0;
        if (powerCell.getLocation() != null && powerCell.getLocation().getBlock().getType() == Material.CHEST) {
             chestItemValue = calculateInventoryEnergy(((org.bukkit.block.Chest)powerCell.getLocation().getBlock().getState()).getInventory());
        }
        
        // 总物品时间 = 缓冲区(energyTime) + 箱子物品价值
        long buffer = powerCell.getEnergyTime();
        long totalItemTime = buffer + chestItemValue;
        
        // 第一行左边：存钱按钮（金币）
        ItemStack moneyButton = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta moneyMeta = moneyButton.getItemMeta();
        moneyMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§6资金管理"));
        List<Component> moneyLore = new ArrayList<>();
        moneyLore.add(LegacyComponentSerializer.legacySection().deserialize("§7左键点击: §a存入金钱"));
        moneyLore.add(LegacyComponentSerializer.legacySection().deserialize("§7右键点击: §c取出金钱"));
        moneyLore.add(LegacyComponentSerializer.legacySection().deserialize("§7当前余额: §e" + String.format("%.2f", powerCell.getEconomyBalance())));
        moneyMeta.lore(moneyLore);
        moneyButton.setItemMeta(moneyMeta);
        gui.setItem(2, moneyButton);

        // 第一行中间：总能量显示（时钟）
        ItemStack clockItem = new ItemStack(Material.CLOCK);
        ItemMeta clockMeta = clockItem.getItemMeta();
        clockMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e总能量"));
        List<Component> clockLore = new ArrayList<>();
        
        double pricePerSecond = powerCell.getPricePerSecond();
        long economyTime = pricePerSecond > 0 ? (long)(powerCell.getEconomyBalance() / pricePerSecond) : 0;
        long totalTime = totalItemTime + economyTime + powerCell.getInitialTime();
        
        String timeStr = formatTime(totalTime);
        clockLore.add(LegacyComponentSerializer.legacySection().deserialize("§6总能量: §e" + timeStr));
        clockLore.add(LegacyComponentSerializer.legacySection().deserialize("§7物品燃料: §a" + formatTime(totalItemTime)));
        clockLore.add(LegacyComponentSerializer.legacySection().deserialize("§7金钱燃料: §e" + formatTime(economyTime)));
        clockLore.add(LegacyComponentSerializer.legacySection().deserialize("§7初始保护: §e" + formatTime(powerCell.getInitialTime())));
        clockMeta.lore(clockLore);
        clockItem.setItemMeta(clockMeta);
        gui.setItem(4, clockItem);

        // 第一行右边：物品剩余时间显示（钻石）
        ItemStack itemTimeButton = new ItemStack(Material.DIAMOND);
        ItemMeta itemTimeMeta = itemTimeButton.getItemMeta();
        itemTimeMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§b物品剩余时间"));
        List<Component> itemTimeLore = new ArrayList<>();
        itemTimeLore.add(LegacyComponentSerializer.legacySection().deserialize("§7物品燃料剩余: §a" + formatTime(totalItemTime)));
        itemTimeMeta.lore(itemTimeLore);
        itemTimeButton.setItemMeta(itemTimeMeta);
        gui.setItem(6, itemTimeButton);
        
        // 填充第一行空白位置
        gui.setItem(0, glassPane);
        gui.setItem(1, glassPane);
        gui.setItem(3, glassPane);
        gui.setItem(5, glassPane);
        gui.setItem(7, glassPane);
        gui.setItem(8, glassPane);
    }

    /**
     * 计算Inventory中物品的能量总值
     */
    private long calculateInventoryEnergy(Inventory inv) {
        long total = 0;
        var itemsSection = plugin.getConfig().getConfigurationSection("power-cell.items");
        if (itemsSection == null) return 0;
        
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType().isAir()) continue;
            
            // 查找配置
            for (String key : itemsSection.getKeys(false)) {
                 String itemType = itemsSection.getString(key + ".item");
                 if (itemType != null && itemType.equalsIgnoreCase(item.getType().name())) {
                     long val = itemsSection.getLong(key + ".value", 0);
                     total += val * item.getAmount();
                     break;
                 }
            }
        }
        return total * 60; // 配置单位是分钟，转换为秒
    }

    /**
     * 设置最后一行按钮
     * 布局: [玻璃][设置][玻璃][成员][玻璃][信息][玻璃][封禁][玻璃]
     * 槽位:  45    46    47    48    49    50    51    52    53
     * 功能按钮均匀分布，中间用玻璃板隔开
     */
    private void setupLastRow(Inventory gui, ChunkClaim claim) {
        // 玻璃板填充
        ItemStack glassPane = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glassPane.setItemMeta(glassMeta);
        
        // 领地设置按钮（红石）- 槽位 46
        ItemStack settingsButton = new ItemStack(Material.REDSTONE);
        ItemMeta settingsMeta = settingsButton.getItemMeta();
        settingsMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c领地设置"));
        List<Component> settingsLore = new ArrayList<>();
        settingsLore.add(LegacyComponentSerializer.legacySection().deserialize("§7更改你领地的设置。"));
        settingsMeta.lore(settingsLore);
        settingsButton.setItemMeta(settingsMeta);
        gui.setItem(46, settingsButton);

        // 成员列表按钮（画）- 槽位 48
        ItemStack membersButton = new ItemStack(Material.PAINTING);
        ItemMeta membersMeta = membersButton.getItemMeta();
        membersMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e成员列表"));
        List<Component> membersLore = new ArrayList<>();
        membersLore.add(LegacyComponentSerializer.legacySection().deserialize("§7查看你领地的成员。"));
        membersMeta.lore(membersLore);
        membersButton.setItemMeta(membersMeta);
        gui.setItem(48, membersButton);

        // 领地信息按钮（书）- 槽位 50
        ItemStack infoButton = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoButton.getItemMeta();
        infoMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e领地信息"));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(LegacyComponentSerializer.legacySection().deserialize("§7领地名称: §f" + claim.getChunkName()));
        infoLore.add(LegacyComponentSerializer.legacySection().deserialize("§7区块坐标: §f" + claim.getChunkX() + ", " + claim.getChunkZ()));
        infoLore.add(LegacyComponentSerializer.legacySection().deserialize("§7区域ID: §f" + claim.getRegionId()));
        infoMeta.lore(infoLore);
        infoButton.setItemMeta(infoMeta);
        gui.setItem(50, infoButton);

        // 封禁列表按钮（屏障）- 槽位 52
        ItemStack banListButton = new ItemStack(Material.BARRIER);
        ItemMeta banListMeta = banListButton.getItemMeta();
        banListMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c封禁列表"));
        List<Component> banListLore = new ArrayList<>();
        banListLore.add(LegacyComponentSerializer.legacySection().deserialize("§7管理被封禁的玩家。"));
        banListMeta.lore(banListLore);
        banListButton.setItemMeta(banListMeta);
        gui.setItem(52, banListButton);
        
        // 填充空白位置（按钮之间用玻璃隔开）
        gui.setItem(45, glassPane);
        gui.setItem(47, glassPane);
        gui.setItem(49, glassPane);
        gui.setItem(51, glassPane);
        gui.setItem(53, glassPane);
    }

    /**
     * 格式化时间显示
     */
    private String formatTime(long seconds) {
        if (seconds <= 0) {
            return "0m";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || sb.length() == 0) {
            sb.append(minutes).append("m");
        }

        return sb.toString().trim();
    }

    /**
     * 打开领地设置GUI
     */
    public void openSettingsGUI(Player player, ChunkClaim claim) {
        Inventory gui = Bukkit.createInventory(null, 27, LegacyComponentSerializer.legacySection().deserialize("领地设置"));

        // 玻璃板填充
        ItemStack glassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glassPane.setItemMeta(glassMeta);
        
        // 填充所有槽位
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glassPane);
        }

        // 第一行：各种设置开关（居中排列）
        // 树叶腐烂
        gui.setItem(1, createToggleItem(Material.OAK_LEAVES, "树叶腐烂", claim.isLeafDecay(), "leaf-decay"));
        // 火焰蔓延
        gui.setItem(2, createToggleItem(Material.FLINT_AND_STEEL, "火焰蔓延", claim.isFireSpread(), "fire-spread"));
        // PVP
        gui.setItem(3, createToggleItem(Material.DIAMOND_SWORD, "PVP", claim.isPvpEnabled(), "pvp"));
        // 敌对生物生成
        gui.setItem(4, createToggleItem(Material.SPAWNER, "敌对生物生成", claim.isMobSpawning(), "mob-spawning"));
        // TNT爆炸破坏
        gui.setItem(5, createToggleItem(Material.TNT, "TNT 爆炸破坏", claim.isTnt(), "tnt"));
        // 爆炸
        gui.setItem(6, createToggleItem(Material.FIRE_CHARGE, "爆炸", claim.isExplosion(), "explosion"));
        // 怪物破坏
        gui.setItem(7, createToggleItem(Material.GUNPOWDER, "怪物破坏", claim.isMobGriefing(), "mob-griefing"));

        // 第二行
        // Home 公共传送
        gui.setItem(10, createToggleItem(Material.ENDER_PEARL, "公开Home传送", claim.isHomePublic(), "home-public"));
        // 实体掉落
        gui.setItem(11, createToggleItem(Material.ITEM_FRAME, "实体掉落", claim.isEntityDrop(), "entity-drop"));
        // 水流蔓延
        gui.setItem(12, createToggleItem(Material.WATER_BUCKET, "水流蔓延", claim.isWaterFlow(), "water-flow"));
        // 外部流体流入
        gui.setItem(13, createToggleItem(Material.LAVA_BUCKET, "外部流体流入", claim.isExternalFluidInflow(), "external-fluid-inflow"));
        // 锁定
        gui.setItem(14, createToggleItem(Material.IRON_DOOR, "领地锁定", claim.isLocked(), "locked"));
        // 能源箱漏斗互动
        gui.setItem(15, createToggleItem(Material.HOPPER, "能源箱漏斗互动", claim.isPowerCellHopperInteraction(), "power-cell-hopper"));

        // 第三行：权限按钮和返回
        // 访客权限
        ItemStack visitorPermButton = new ItemStack(Material.SKELETON_SKULL);
        ItemMeta visitorPermMeta = visitorPermButton.getItemMeta();
        visitorPermMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e访客权限设置"));
        visitorPermMeta.lore(List.of(LegacyComponentSerializer.legacySection().deserialize("§7设置访客的详细权限")));
        visitorPermButton.setItemMeta(visitorPermMeta);
        gui.setItem(20, visitorPermButton);

        // 返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§7返回"));
        backButton.setItemMeta(backMeta);
        gui.setItem(22, backButton);
        
        // 成员权限
        ItemStack memberPermButton = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta memberPermMeta = memberPermButton.getItemMeta();
        memberPermMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§a成员权限设置"));
        memberPermMeta.lore(List.of(LegacyComponentSerializer.legacySection().deserialize("§7设置成员的详细权限")));
        memberPermButton.setItemMeta(memberPermMeta);
        gui.setItem(24, memberPermButton);

        player.openInventory(gui);
    }

    /**
     * 创建开关物品
     */
    private ItemStack createToggleItem(Material material, String name, boolean enabled, String settingKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e" + name));
        List<Component> lore = new ArrayList<>();
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7当前: " + (enabled ? "§a是" : "§c否")));
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7点击切换"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 打开成员列表GUI
     */
    public void openMembersGUI(Player player, ChunkClaim claim) {
        Inventory gui = Bukkit.createInventory(null, 54, LegacyComponentSerializer.legacySection().deserialize("成员列表"));

        // 玻璃板边框
        ItemStack glassPane = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glassPane.setItemMeta(glassMeta);
        
        int[] borderSlots = getBorderSlots();
        for (int slot : borderSlots) {
            gui.setItem(slot, glassPane);
        }

        // 第一行中间：添加成员按钮
        ItemStack addButton = new ItemStack(Material.EMERALD);
        ItemMeta addMeta = addButton.getItemMeta();
        addMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§a添加成员"));
        addMeta.lore(List.of(LegacyComponentSerializer.legacySection().deserialize("§7点击邀请新成员")));
        addButton.setItemMeta(addMeta);
        gui.setItem(4, addButton);

        // 加载成员列表
        plugin.getChunkClaimManager().getMembers(claim.getId())
            .thenAccept(members -> {
                // 在主线程更新GUI
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    List<Integer> centerSlots = getCenterSlots();
                    int index = 0;
                    
                    // 1. 添加所有者 (始终显示在第一个)
                    java.util.UUID ownerId = claim.getOwner();
                    org.bukkit.OfflinePlayer ownerPlayer = plugin.getServer().getOfflinePlayer(ownerId);
                    ItemStack ownerItem = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
                    org.bukkit.inventory.meta.SkullMeta ownerMeta = (org.bukkit.inventory.meta.SkullMeta) ownerItem.getItemMeta();
                    ownerMeta.setOwningPlayer(ownerPlayer);
                    ownerMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§6" + 
                        (ownerPlayer.getName() != null ? ownerPlayer.getName() : "未知玩家")));
                    List<Component> ownerLore = new ArrayList<>();
                    ownerLore.add(LegacyComponentSerializer.legacySection().deserialize("§7角色: §c§l所有者"));
                    ownerLore.add(LegacyComponentSerializer.legacySection().deserialize("§e这是领地的拥有者"));
                    ownerMeta.lore(ownerLore);
                    ownerItem.setItemMeta(ownerMeta);
                    
                    if (index < centerSlots.size()) {
                        gui.setItem(centerSlots.get(index++), ownerItem);
                    }
                    
                    // 2. 添加其他成员
                    for (org.kari.kariClaims.models.ClaimMember member : members) {
                        if (index >= centerSlots.size()) break;
                        
                        org.bukkit.OfflinePlayer memberPlayer = plugin.getServer().getOfflinePlayer(member.getPlayerId());
                        ItemStack memberItem = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
                        org.bukkit.inventory.meta.SkullMeta memberMeta = (org.bukkit.inventory.meta.SkullMeta) memberItem.getItemMeta();
                        memberMeta.setOwningPlayer(memberPlayer);
                        memberMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e" + 
                            (memberPlayer.getName() != null ? memberPlayer.getName() : "未知玩家")));
                        List<Component> memberLore = new ArrayList<>();
                        memberLore.add(LegacyComponentSerializer.legacySection().deserialize("§7角色: §f" + member.getRole().getDisplayName()));
                        memberLore.add(LegacyComponentSerializer.legacySection().deserialize("§7加入时间: §f" + 
                            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(member.getJoinedAt()))));
                        memberLore.add(LegacyComponentSerializer.legacySection().deserialize("§c右键点击移除/管理"));
                        memberMeta.lore(memberLore);
                        memberItem.setItemMeta(memberMeta);
                        gui.setItem(centerSlots.get(index++), memberItem);
                    }
                });
            });

        // 返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§7返回"));
        backButton.setItemMeta(backMeta);
        gui.setItem(49, backButton);

        player.openInventory(gui);
    }
    
    /**
     * 打开封禁列表GUI
     */
    public void openBanListGUI(Player player, ChunkClaim claim) {
        Inventory gui = Bukkit.createInventory(null, 54, LegacyComponentSerializer.legacySection().deserialize("封禁列表"));
        
        // 玻璃板边框
        ItemStack glassPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glassPane.setItemMeta(glassMeta);
        
        int[] borderSlots = getBorderSlots();
        for (int slot : borderSlots) {
            gui.setItem(slot, glassPane);
        }
        
        // 加载封禁列表
        plugin.getChunkClaimManager().getBannedPlayersAsync(claim.getId())
            .thenAccept(bannedUUIDs -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // 第一行中间：信息物品
                    ItemStack infoItem = new ItemStack(Material.PAPER);
                    ItemMeta infoMeta = infoItem.getItemMeta();
                    infoMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c封禁列表信息"));
                    infoMeta.lore(List.of(LegacyComponentSerializer.legacySection().deserialize("§7当前封禁人数: §c" + bannedUUIDs.size())));
                    infoItem.setItemMeta(infoMeta);
                    gui.setItem(4, infoItem);
                    
                    List<Integer> centerSlots = getCenterSlots();
                    int index = 0;
                    for (java.util.UUID uuid : bannedUUIDs) {
                        if (index >= centerSlots.size()) break;
                        
                        org.bukkit.OfflinePlayer bannedPlayer = plugin.getServer().getOfflinePlayer(uuid);
                        ItemStack headItem = new ItemStack(Material.PLAYER_HEAD);
                        org.bukkit.inventory.meta.SkullMeta headMeta = (org.bukkit.inventory.meta.SkullMeta) headItem.getItemMeta();
                        headMeta.setOwningPlayer(bannedPlayer);
                        headMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c" + 
                            (bannedPlayer.getName() != null ? bannedPlayer.getName() : "未知玩家")));
                        headMeta.lore(List.of(LegacyComponentSerializer.legacySection().deserialize("§e右键点击解除封禁")));
                        headItem.setItemMeta(headMeta);
                        
                        gui.setItem(centerSlots.get(index++), headItem);
                    }
                });
            });
            
        // 返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§7返回"));
        backButton.setItemMeta(backMeta);
        gui.setItem(49, backButton);
        
        player.openInventory(gui);
    }
    
    /**
     * 打开权限设置GUI
     */
    public void openPermissionGUI(Player player, ChunkClaim claim, boolean isVisitor) {
        String title = isVisitor ? "访客权限设置" : "成员权限设置";
        Inventory gui = Bukkit.createInventory(null, 27, LegacyComponentSerializer.legacySection().deserialize(title));
        
        ItemStack glassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glassPane.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glassPane);
        }
        
        // 获取当前权限
        int currentPerms = isVisitor ? claim.getVisitorPermissions() : claim.getMemberPermissions();
        
        // 破坏方块
        gui.setItem(10, createPermToggleItem(Material.IRON_PICKAXE, "破坏方块", 
            (currentPerms & ChunkClaim.PERM_BLOCK_BREAK) != 0));
            
        // 放置方块
        gui.setItem(11, createPermToggleItem(Material.GRASS_BLOCK, "放置方块", 
            (currentPerms & ChunkClaim.PERM_BLOCK_PLACE) != 0));
            
        // 交互
        gui.setItem(12, createPermToggleItem(Material.CHEST, "交互", 
            (currentPerms & ChunkClaim.PERM_INTERACT) != 0));
            
        // 交易
        gui.setItem(13, createPermToggleItem(Material.EMERALD, "交易", 
            (currentPerms & ChunkClaim.PERM_TRADE) != 0));
            
        // 使用门
        gui.setItem(14, createPermToggleItem(Material.OAK_DOOR, "使用门/活板门", 
            (currentPerms & ChunkClaim.PERM_USE_DOORS) != 0));
            
        // 攻击生物
        gui.setItem(15, createPermToggleItem(Material.IRON_SWORD, "攻击生物", 
            (currentPerms & ChunkClaim.PERM_ATTACK_MOBS) != 0));
            
        // 使用红石
        gui.setItem(16, createPermToggleItem(Material.REDSTONE, "使用红石", 
            (currentPerms & ChunkClaim.PERM_USE_REDSTONE) != 0));
            
        // 返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§7返回"));
        backButton.setItemMeta(backMeta);
        gui.setItem(22, backButton);
        
        player.openInventory(gui);
    }
    
    private ItemStack createPermToggleItem(Material mat, String name, boolean enabled) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e" + name));
        List<Component> lore = new ArrayList<>();
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7当前: " + (enabled ? "§a允许" : "§c禁止")));
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7点击切换"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    private int[] getBorderSlots() {
        return new int[]{
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            36, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
        };
    }
    
    private List<Integer> getCenterSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < 5; row++) {
            for (int col = 1; col < 8; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }
    
    /**
     * 获取领地显示名称（优先使用区域名称）
     */
    private String getClaimDisplayName(ChunkClaim claim) {
        // 如果属于区域，使用区域名称
        if (claim.getRegionId() > 0) {
            org.kari.kariClaims.models.ClaimRegion region = plugin.getChunkClaimManager().getRegion(claim.getRegionId());
            if (region != null && region.getRegionName() != null && !region.getRegionName().isEmpty()) {
                return region.getRegionName();
            }
        }
        // 使用区块名称
        if (claim.getChunkName() != null && !claim.getChunkName().isEmpty()) {
            return claim.getChunkName();
        }
        // 默认使用"未命名领地"
        return "未命名领地";
    }
}

