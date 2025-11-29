package org.kari.kariClaims.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.kari.kariClaims.KariClaims;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 能量电池配方监听器 - 检测箱子内的配方
 */
public class PowerCellRecipeListener implements Listener {
    private final KariClaims plugin;

    public PowerCellRecipeListener(KariClaims plugin) {
        this.plugin = plugin;
    }

    /**
     * 监听箱子关闭事件，检查配方
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();

        // 检查是否是箱子
        if (!(inv.getHolder() instanceof Chest)) {
            return;
        }

        Chest chest = (Chest) inv.getHolder();
        Block chestBlock = chest.getBlock();

        // 必须在主线程上检查配方，因为需要访问箱子物品
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                // 重新获取箱子库存（确保是最新的）
                Chest currentChest = (Chest) chestBlock.getState();
                Inventory chestInv = currentChest.getInventory();
                
                // 检查区块是否已被认领
                plugin.getChunkClaimManager().findChunkClaimAtAsync(chestBlock.getLocation())
                    .thenAccept(claimOpt -> {
                        if (claimOpt.isEmpty()) {
                            return;
                        }
                        
                        org.kari.kariClaims.models.ChunkClaim claim = claimOpt.get();
                        
                        // 检查该领地是否已有能量电池
                        plugin.getChunkClaimDAO().getPowerCellLocationAsync(claim.getId())
                            .thenAccept(existingLoc -> {
                                // 如果已有能量电池且就在当前箱子位置，直接返回，不再检测配方/替换
                                if (existingLoc != null &&
                                    existingLoc.getWorld().getName().equals(chestBlock.getWorld().getName()) &&
                                    existingLoc.getBlockX() == chestBlock.getX() &&
                                    existingLoc.getBlockY() == chestBlock.getY() &&
                                    existingLoc.getBlockZ() == chestBlock.getZ()) {
                                    return;
                                }
                                
                                // 如果已有能量电池但位置不同，允许替换（删除旧的）
                                if (existingLoc != null) {
                                    // 删除旧的能源箱记录
                                    plugin.getChunkClaimDAO().deletePowerCellAsync(claim.getId());
                                    plugin.getChunkClaimManager().unregisterPowerCellLocation(existingLoc);
                                    
                                    // 如果旧箱子还存在，掉落物品
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        org.bukkit.block.Block oldBlock = existingLoc.getBlock();
                                        if (oldBlock.getType() == org.bukkit.Material.CHEST) {
                                            org.bukkit.block.Chest oldChest = (org.bukkit.block.Chest) oldBlock.getState();
                                            for (org.bukkit.inventory.ItemStack item : oldChest.getInventory().getContents()) {
                                                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                                                    existingLoc.getWorld().dropItemNaturally(existingLoc, item);
                                                }
                                            }
                                            oldBlock.setType(org.bukkit.Material.AIR);
                                            existingLoc.getWorld().dropItemNaturally(existingLoc, new org.bukkit.inventory.ItemStack(org.bukkit.Material.CHEST));
                                        }
                                    });
                                }
                                
                                // 检查玩家是否是所有者
                                if (!claim.getOwner().equals(player.getUniqueId())) {
                                    return;
                                }

                                // 在主线程上检查配方
                                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                                    @Override
                                    public void run() {
                                        checkRecipe(player, chestBlock, chestInv);
                                    }
                                });
                            });
                    });
            }
        });
    }

    /**
     * 检查箱子内的配方
     */
    private void checkRecipe(Player player, Block chestBlock, Inventory chestInv) {
        // 获取配方配置
        var recipeSection = plugin.getConfig().getConfigurationSection("power-cell.recipe.slots");
        if (recipeSection == null) return;

        var keys = recipeSection.getKeys(false);
        if (keys.isEmpty()) return;
        
        // 检查每个槽位是否符合配方
        int checkedSlots = 0;
        boolean matches = true;
        
        for (String slotKey : keys) {
            try {
                int slot = Integer.parseInt(slotKey);
                if (slot < 0 || slot >= chestInv.getSize()) continue;
                
                String requiredMaterial = recipeSection.getString(slotKey);
                if (requiredMaterial == null || requiredMaterial.isEmpty()) continue;

                Material required = Material.matchMaterial(requiredMaterial);
                if (required == null) continue;

                ItemStack item = chestInv.getItem(slot);
                
                // 检查物品是否匹配
                if (item == null || item.getType() != required) {
                    matches = false;
                    break;
                }
                
                checkedSlots++;
            } catch (NumberFormatException e) {
                continue;
            }
        }

        // 配方匹配成功才创建能量电池（静默跳过不匹配的情况）
        if (checkedSlots > 0 && matches) {
            createPowerCell(player, chestBlock, chestInv);
        }
    }

    /**
     * 创建能量电池
     */
    private void createPowerCell(Player player, Block chestBlock, Inventory chestInv) {
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    // 计算配方物品的能源值（在主线程上）
                    long energyFromRecipe = calculateRecipeEnergy(chestInv);
                    
                    // 完成能量电池创建（不传递能源值，因为我们要转换为物品）
                    plugin.getChunkClaimManager().createPowerCell(player, chestBlock.getLocation(), 0)
                        .thenAccept(claim -> {
                            // 所有对箱子的操作必须在主线程上执行
                            plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        // 重新获取箱子状态（确保在主线程上）
                                        Chest currentChest = (Chest) chestBlock.getState();
                                        Inventory currentChestInv = currentChest.getInventory();
                                        
                                        // 移除配方物品（每个槽位只移除1个）
                                        var recipeSection = plugin.getConfig().getConfigurationSection("power-cell.recipe.slots");
                                        if (recipeSection != null) {
                                            for (String slotKey : recipeSection.getKeys(false)) {
                                                try {
                                                    int slot = Integer.parseInt(slotKey);
                                                    ItemStack item = currentChestInv.getItem(slot);
                                                    if (item != null) {
                                                        if (item.getAmount() > 1) {
                                                            item.setAmount(item.getAmount() - 1);
                                                        } else {
                                                            currentChestInv.setItem(slot, null);
                                                        }
                                                    }
                                                } catch (NumberFormatException ignored) {}
                                            }
                                        }
                                        
                                        // 先更新箱子状态（确保移除配方物品的操作生效）
                                        currentChest.update(true);
                                        
                                        // 将配方物品的能源值转换为物品放入箱子
                                        if (energyFromRecipe > 0) {
                                            convertEnergyToItems(currentChestInv, energyFromRecipe);
                                            // 再次更新
                                            currentChest.update(true);
                                        }
                                        
                                        // 重新获取最新的箱子物品状态，然后整理
                                        Inventory freshInv = ((Chest) chestBlock.getState()).getInventory();
                                        organizeInventory(freshInv);
                                        
                                        // 最终更新箱子状态
                                        ((Chest) chestBlock.getState()).update(true);
                                        
                                        // 保存能量电池位置到数据库
                                        org.bukkit.Location powerCellLoc = chestBlock.getLocation();
                                        plugin.getChunkClaimManager().registerPowerCellLocation(claim.getId(), powerCellLoc);
                                        
                                        plugin.getChunkClaimDAO().savePowerCellAsync(
                                            claim.getId(),
                                            powerCellLoc,
                                            claim.getEnergyTime(),
                                            claim.getEconomyBalance()
                                        ).thenRun(() -> {
                                            // 播放成功音效（在主线程上）
                                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                powerCellLoc.getWorld().spawnParticle(org.bukkit.Particle.HEART, powerCellLoc.clone().add(0.5, 1.0, 0.5), 12, 0.3, 0.6, 0.3, 0.01);
                                                powerCellLoc.getWorld().playSound(powerCellLoc, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.2f);
                                                player.playSound(chestBlock.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
                                                player.sendMessage("§a能量电池创建成功！");
                                                player.sendMessage("§e领地ID: §7" + claim.getId());
                                                player.sendMessage("§7能量电池位置: §f" + chestBlock.getX() + ", " + chestBlock.getY() + ", " + chestBlock.getZ());
                                                player.sendMessage("§7配方物品已转换为 §e" + energyFromRecipe + "秒 §7能源物品");
                                                player.sendMessage("§7右键能量电池可打开管理界面");
                                            });
                                        });
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("处理能量电池创建时出错: " + e.getMessage());
                                        e.printStackTrace();
                                        player.sendMessage("§c创建能量电池时出错: " + e.getMessage());
                                    }
                                }
                            });
                        })
                        .exceptionally(throwable -> {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                player.sendMessage("§c创建能量电池失败: " + throwable.getCause().getMessage());
                            });
                            return null;
                        });
                } catch (Exception e) {
                    player.sendMessage("§c创建能量电池时出错: " + e.getMessage());
                    plugin.getLogger().warning("创建能量电池失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 计算配方物品的能源值
     */
    private long calculateRecipeEnergy(Inventory chestInv) {
        long totalEnergy = 0;
        var recipeSection = plugin.getConfig().getConfigurationSection("power-cell.recipe.slots");
        if (recipeSection == null) {
            return 0;
        }
        
        var itemsSection = plugin.getConfig().getConfigurationSection("power-cell.items");
        if (itemsSection == null) {
            return 0;
        }
        
        for (String slotKey : recipeSection.getKeys(false)) {
            int slot = Integer.parseInt(slotKey);
            ItemStack item = chestInv.getItem(slot);
            if (item == null) {
                continue;
            }
            
            // 查找物品的能源值
            for (String key : itemsSection.getKeys(false)) {
                String itemType = itemsSection.getString(key + ".item");
                if (itemType != null && itemType.equalsIgnoreCase(item.getType().name())) {
                    long energyValue = itemsSection.getLong(key + ".value", 0);
                    totalEnergy += energyValue; // 每个物品算1个
                    break;
                }
            }
        }
        
        return totalEnergy;
    }
    
    /**
     * 将能源值转换为物品并放入箱子
     */
    private void convertEnergyToItems(Inventory chestInv, long totalEnergy) {
        var itemsSection = plugin.getConfig().getConfigurationSection("power-cell.items");
        if (itemsSection == null || totalEnergy <= 0) {
            return;
        }
        
        // 按价值从高到低排序物品（优先使用高价值物品）
        List<Map.Entry<String, Long>> sortedItems = new ArrayList<>();
        for (String key : itemsSection.getKeys(false)) {
            long value = itemsSection.getLong(key + ".value", 0);
            if (value > 0) {
                sortedItems.add(new AbstractMap.SimpleEntry<>(key, value));
            }
        }
        sortedItems.sort((a, b) -> Long.compare(b.getValue(), a.getValue())); // 从高到低排序
        
        // 转换能源为物品
        long remainingEnergy = totalEnergy;
        for (Map.Entry<String, Long> entry : sortedItems) {
            if (remainingEnergy <= 0) {
                break;
            }
            
            String key = entry.getKey();
            long itemValue = entry.getValue();
            String itemType = itemsSection.getString(key + ".item");
            
            if (itemType != null) {
                try {
                    Material material = Material.valueOf(itemType);
                    int itemCount = (int) (remainingEnergy / itemValue);
                    if (itemCount > 0) {
                        ItemStack item = new ItemStack(material, itemCount);
                        chestInv.addItem(item);
                        remainingEnergy -= itemCount * itemValue;
                        plugin.getLogger().info("将 " + itemCount + " 个 " + itemType + " 放入箱子（价值: " + (itemCount * itemValue) + " 秒）");
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的物品类型: " + itemType);
                }
            }
        }
        
        // 更新箱子状态
        if (chestInv.getHolder() instanceof Chest) {
            Chest chest = (Chest) chestInv.getHolder();
            chest.update(true);
        }
    }

    /**
     * 整理箱子物品：合并同类物品，按类型排序，从左上角开始整齐排列
     */
    private void organizeInventory(Inventory inv) {
        plugin.getLogger().info("开始整理箱子物品...");
        
        // 收集所有物品并合并同类
        Map<Material, Integer> itemCounts = new java.util.LinkedHashMap<>();
        
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                int oldCount = itemCounts.getOrDefault(item.getType(), 0);
                itemCounts.put(item.getType(), oldCount + item.getAmount());
                plugin.getLogger().info("槽位 " + i + ": " + item.getType() + " x" + item.getAmount());
            }
        }
        
        plugin.getLogger().info("共发现 " + itemCounts.size() + " 种物品");
        
        // 清空箱子
        inv.clear();
        
        // 按物品类型名称排序（使相同类型的物品聚在一起）
        List<Map.Entry<Material, Integer>> sortedEntries = new ArrayList<>(itemCounts.entrySet());
        sortedEntries.sort((a, b) -> {
            // 优先按能源价值排序（高价值在前）
            long valueA = getItemEnergyValue(a.getKey());
            long valueB = getItemEnergyValue(b.getKey());
            if (valueA != valueB) {
                return Long.compare(valueB, valueA); // 高价值在前
            }
            // 其次按物品名称排序
            return a.getKey().name().compareTo(b.getKey().name());
        });
        
        // 从左上角开始逐个放置物品
        int slot = 0;
        for (Map.Entry<Material, Integer> entry : sortedEntries) {
            Material material = entry.getKey();
            int totalAmount = entry.getValue();
            int maxStackSize = material.getMaxStackSize();
            
            // 分成多个堆叠
            while (totalAmount > 0 && slot < inv.getSize()) {
                int stackAmount = Math.min(totalAmount, maxStackSize);
                inv.setItem(slot, new ItemStack(material, stackAmount));
                totalAmount -= stackAmount;
                slot++;
            }
        }
        
        plugin.getLogger().info("箱子物品已整理完成，共 " + slot + " 个槽位被使用");
    }
    
    /**
     * 获取物品的能源价值
     */
    private long getItemEnergyValue(Material material) {
        var itemsSection = plugin.getConfig().getConfigurationSection("power-cell.items");
        if (itemsSection == null) {
            return 0;
        }
        
        for (String key : itemsSection.getKeys(false)) {
            String itemType = itemsSection.getString(key + ".item");
            if (itemType != null && itemType.equalsIgnoreCase(material.name())) {
                return itemsSection.getLong(key + ".value", 0);
            }
        }
        return 0;
    }
}

