package org.kari.kariClaims.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.models.ChunkClaim;
import org.kari.kariClaims.models.PowerCell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 能量电池监听器 - 处理能量电池相关事件
 */
public class PowerCellListener implements Listener {
    private final KariClaims plugin;
    private final Map<UUID, String> awaitingMoneyTransaction = new HashMap<>();
    private final Map<UUID, ChunkClaim> playerClaimContext = new HashMap<>();
    private final Map<UUID, Long> initialEnergyCache = new HashMap<>(); // 记录打开GUI时的初始能源值
    private final Set<UUID> closingGuard = java.util.Collections.newSetFromMap(new HashMap<>()); // 防止重复处理关闭事件

    public PowerCellListener(KariClaims plugin) {
        this.plugin = plugin;
    }

    /**
     * 在主线程打开能量电池GUI，并记录打开时的初始能源值
     */
    private void openPowerCellGui(Player player, ChunkClaim claim, Location powerCellLoc) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            long initialItemEnergy = 0;
            if (powerCellLoc != null) {
                Block chestBlock = powerCellLoc.getBlock();
                if (chestBlock.getType() == Material.CHEST) {
                    org.bukkit.block.Chest chest = (org.bukkit.block.Chest) chestBlock.getState();
                    initialItemEnergy = calculateItemEnergy(chest.getInventory());
                    
                    // 将数据库中的物品能源与实际箱子保持一致，避免显示过期的数值
                    claim.setEnergyTime(initialItemEnergy);
                    plugin.getChunkClaimDAO().updateChunkClaimAsync(claim);
                } else {
                    plugin.getLogger().warning("能量电池位置不是箱子，无法计算初始能源");
                }
            }

            initialEnergyCache.put(player.getUniqueId(), initialItemEnergy);
            
            double pricePerHour = plugin.getConfig().getDouble("power-cell.economy-price-per-hour", 100.0);
            double pricePerSecond = pricePerHour / 3600.0;

            PowerCell powerCell = new PowerCell(
                claim.getId(),
                powerCellLoc,
                initialItemEnergy,
                claim.getEconomyBalance(),
                claim.getInitialTime(),
                pricePerSecond
            );

            plugin.getPowerCellGUI().openPowerCellGUI(player, claim, powerCell);
            playerClaimContext.put(player.getUniqueId(), claim);
        });
    }

    /**
     * 右键能量核心（箱子）打开GUI
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickChest(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) {
            return;
        }

        Player player = event.getPlayer();
        org.bukkit.Location blockLoc = block.getLocation();
        
        // 先使用缓存快速检查是否是能源箱（同步检查）
        Integer claimId = plugin.getChunkClaimManager().getPowerCellClaimIdAt(blockLoc);
        if (claimId != null) {
            // 可能是能源箱，需要进一步验证
            plugin.getChunkClaimManager().findChunkClaimAtAsync(blockLoc)
                .thenAccept(claimOpt -> {
                    if (claimOpt.isEmpty()) {
                        return;
                    }

                    ChunkClaim claim = claimOpt.get();
                    
                    // 验证claimId是否匹配
                    if (claim.getId() != claimId) {
                        return;
                    }
                    
                    // 检查玩家是否有权限
                    if (!claim.getOwner().equals(player.getUniqueId()) && 
                        !plugin.getChunkClaimManager().hasPermissionSync(player, claim, "interact")) {
                        return;
                    }

                    // 从数据库加载能量电池位置（验证缓存）
                    plugin.getChunkClaimDAO().getPowerCellLocationAsync(claim.getId())
                        .thenAccept(powerCellLoc -> {
                            // 检查点击的箱子是否是能量电池
                            if (powerCellLoc == null) {
                                // 数据库中没有能量电池位置，不是能量电池
                                plugin.getLogger().fine("点击的箱子不是能量电池（数据库中没有记录）");
                                return;
                            }
                            
                            // 检查位置是否匹配
                            if (!blockLoc.getWorld().getName().equals(powerCellLoc.getWorld().getName()) ||
                                blockLoc.getBlockX() != powerCellLoc.getBlockX() ||
                                blockLoc.getBlockY() != powerCellLoc.getBlockY() ||
                                blockLoc.getBlockZ() != powerCellLoc.getBlockZ()) {
                                // 点击的不是能量电池箱子，不打开GUI
                                plugin.getLogger().fine("点击的箱子不是能量电池（位置不匹配）");
                                return;
                            }
                            
                            // 这是能量电池，打开GUI（在主线程）
                            event.setCancelled(true);
                            openPowerCellGui(player, claim, powerCellLoc);
                        });
                });
            // 先取消事件，防止箱子打开
            event.setCancelled(true);
            return;
        }
        
        // 如果缓存中没有，可能是普通箱子，但也要异步检查一下（以防缓存未更新）
        plugin.getChunkClaimManager().findChunkClaimAtAsync(blockLoc)
            .thenAccept(claimOpt -> {
                if (claimOpt.isEmpty()) {
                    return;
                }

                ChunkClaim claim = claimOpt.get();
                
                // 检查玩家是否有权限
                if (!claim.getOwner().equals(player.getUniqueId()) && 
                    !plugin.getChunkClaimManager().hasPermissionSync(player, claim, "interact")) {
                    return;
                }

                // 从数据库加载能量电池位置
                plugin.getChunkClaimDAO().getPowerCellLocationAsync(claim.getId())
                    .thenAccept(powerCellLoc -> {
                        // 检查点击的箱子是否是能量电池
                        if (powerCellLoc == null) {
                            return;
                        }
                        
                        // 检查位置是否匹配
                        if (!blockLoc.getWorld().getName().equals(powerCellLoc.getWorld().getName()) ||
                            blockLoc.getBlockX() != powerCellLoc.getBlockX() ||
                            blockLoc.getBlockY() != powerCellLoc.getBlockY() ||
                            blockLoc.getBlockZ() != powerCellLoc.getBlockZ()) {
                            return;
                        }
                        
                        // 更新缓存
                        plugin.getChunkClaimManager().registerPowerCellLocation(claim.getId(), powerCellLoc);
                            
                        // 这是能量电池，打开GUI（在主线程）
                        event.setCancelled(true);
                        openPowerCellGui(player, claim, powerCellLoc);
                    });
            });
    }

    /**
     * 处理GUI点击事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();
        String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());

        // 能量电池主界面
        if (title.endsWith("的领地")) {
            handlePowerCellGUIClick(player, event, inv, title);
            return; // 处理完后直接返回，避免其他监听器干扰
        }
        // 领地设置界面
        else if (title.equals("领地设置")) {
            // 确保所有操作都被取消，防止按钮被移动
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                event.setCancelled(true);
            }
            handleSettingsGUIClick(player, event, inv);
        }
        // 成员列表界面
        else if (title.equals("成员列表")) {
            handleMembersGUIClick(player, event, inv);
        }
        // 封禁列表界面
        else if (title.equals("封禁列表")) {
            handleBanListGUIClick(player, event, inv);
        }
        // 权限设置界面
        else if (title.equals("访客权限设置") || title.equals("成员权限设置")) {
            handlePermissionGUIClick(player, event, inv, title.equals("访客权限设置"));
        }
    }

    /**
     * 处理能量电池GUI点击
     */
    private void handlePowerCellGUIClick(Player player, InventoryClickEvent event, Inventory inv, String title) {
        int slot = event.getSlot();
        
        // 检查点击的是哪个库存
        Inventory clickedInv = event.getClickedInventory();
        Inventory topInv = event.getView().getTopInventory();
        Inventory bottomInv = event.getView().getBottomInventory();
        
        // 中间区域可用槽位：10-16, 19-25, 28-34, 37-43
        boolean isAllowedSlot = (slot >= 10 && slot <= 16) || 
                               (slot >= 19 && slot <= 25) || 
                               (slot >= 28 && slot <= 34) || 
                               (slot >= 37 && slot <= 43);
        
        // 如果点击的是玩家背包，检查是否要放置到允许的槽位
        if (clickedInv == bottomInv) {
            // 检查是否是拖拽操作到GUI中间区域
            if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                // Shift点击，移动到其他库存 - 允许（会在目标槽位检查）
                return;
            }
            // 检查是否是拖拽到GUI
            if (event.getAction() == org.bukkit.event.inventory.InventoryAction.PLACE_ALL ||
                event.getAction() == org.bukkit.event.inventory.InventoryAction.PLACE_ONE ||
                event.getAction() == org.bukkit.event.inventory.InventoryAction.PLACE_SOME ||
                event.getAction() == org.bukkit.event.inventory.InventoryAction.SWAP_WITH_CURSOR) {
                // 检查目标槽位是否在允许区域
                if (isAllowedSlot) {
                    return; // 允许操作
                }
            }
            // 其他从背包的操作，允许（玩家背包内部操作）
            return;
        }
        
        // 如果点击的是GUI
        if (clickedInv == topInv) {
            // 中间区域可用槽位：允许所有操作
            if (isAllowedSlot) {
                // 允许所有操作：放置、取出、移动等
                // 不取消事件，让玩家正常操作物品
                return;
            }
            
            // 第一行按钮（更新后的位置）
            if (slot == 2) {
                // 资金管理
                event.setCancelled(true);
                player.closeInventory();
                
                if (event.isLeftClick()) {
                    player.sendMessage("§7请在聊天框输入要存入的金额（输入 'cancel' 取消）");
                    awaitingMoneyTransaction.put(player.getUniqueId(), "deposit");
                } else if (event.isRightClick()) {
                    player.sendMessage("§7请在聊天框输入要取出的金额（输入 'cancel' 取消）");
                    awaitingMoneyTransaction.put(player.getUniqueId(), "withdraw");
                }
            } else if (slot == 4) {
                // 时钟（总能量显示）- 不可点击
                event.setCancelled(true);
            } else if (slot == 6) {
                // 物品剩余时间显示 - 不可点击
                event.setCancelled(true);
            } else if (slot == 0 || slot == 1 || slot == 3 || slot == 5 || slot == 7 || slot == 8) {
                // 第一行玻璃板 - 不可点击
                event.setCancelled(true);
            }
            // 最后一行按钮（更新后的位置）
            // 布局: [玻璃45][设置46][玻璃47][成员48][玻璃49][信息50][玻璃51][封禁52][玻璃53]
            else if (slot == 46) {
                // 领地设置
                event.setCancelled(true);
                ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
                if (claim != null) {
                    plugin.getPowerCellGUI().openSettingsGUI(player, claim);
                }
            } else if (slot == 48) {
                // 成员列表
                event.setCancelled(true);
                ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
                if (claim != null) {
                    plugin.getPowerCellGUI().openMembersGUI(player, claim);
                }
            } else if (slot == 50) {
                // 领地信息 - 不可点击
                event.setCancelled(true);
            } else if (slot == 52) {
                // 封禁列表
                event.setCancelled(true);
                ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
                if (claim != null) {
                    plugin.getPowerCellGUI().openBanListGUI(player, claim);
                }
            } else if (slot == 45 || slot == 47 || slot == 49 || slot == 51 || slot == 53) {
                // 最后一行玻璃板 - 不可点击
                event.setCancelled(true);
            } else if (slot == 9 || slot == 17 || slot == 18 || slot == 26 || 
                       slot == 27 || slot == 35 || slot == 36 || slot == 44) {
                // 左右两列的玻璃板 - 不可点击
                event.setCancelled(true);
            } else {
                // 其他槽位不允许操作
                event.setCancelled(true);
            }
        }
        
        // 处理拖拽操作（从GUI拖到背包或从背包拖到GUI）
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR ||
            event.getAction() == org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP) {
            // 如果涉及中间区域，允许操作
            if (isAllowedSlot) {
                return; // 允许操作
            }
        }
    }

    /**
     * 关闭能量电池GUI时保存物品回箱子
     * 使用HIGH优先级，确保在GUI关闭前保存物品
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPowerCellGUIClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        if (!closingGuard.add(player.getUniqueId())) {
            return; // 已处理过本次关闭
        }
        String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());

        // 检查是否是能量电池GUI
        if (!title.endsWith("的领地")) {
            closingGuard.remove(player.getUniqueId());
            return;
        }

        ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
        if (claim == null) {
            closingGuard.remove(player.getUniqueId());
            return;
        }

        // 立即获取GUI中的物品内容（在GUI关闭前）
        Inventory guiInv = event.getView().getTopInventory();
        if (guiInv == null) {
            plugin.getLogger().warning("无法获取GUI库存，物品可能已丢失");
            return;
        }
        
        // 立即复制GUI中间区域的物品
        // 可用区域：10-16, 19-25, 28-34, 37-43（共28个槽位）
        // 映射到箱子的0-26槽位（27个槽位）
        int[] guiSlots = {10, 11, 12, 13, 14, 15, 16,  // 第二行中间
                          19, 20, 21, 22, 23, 24, 25,  // 第三行中间
                          28, 29, 30, 31, 32, 33, 34,  // 第四行中间
                          37, 38, 39, 40, 41, 42, 43}; // 第五行中间
        
        ItemStack[] itemsToSave = new ItemStack[guiSlots.length];
        int itemsFound = 0;
        for (int i = 0; i < guiSlots.length; i++) {
            ItemStack item = guiInv.getItem(guiSlots[i]);
            if (item != null && item.getType() != Material.AIR) {
                itemsToSave[i] = item.clone();
                itemsFound++;
                plugin.getLogger().info("从GUI槽位 " + guiSlots[i] + " 复制物品到数组索引 " + i + ": " + item.getType() + " x" + item.getAmount());
            } else {
                itemsToSave[i] = null;
            }
        }
        plugin.getLogger().info("总共从GUI复制了 " + itemsFound + " 个物品到保存数组");
        
        // 计算GUI中物品的能源值（用于日志记录）
        long currentItemEnergy = calculateItemEnergy(guiInv);
        long initialItemEnergy = initialEnergyCache.getOrDefault(player.getUniqueId(), currentItemEnergy);

        // 从数据库获取能量电池位置并保存物品
        plugin.getChunkClaimDAO().getPowerCellLocationAsync(claim.getId())
            .thenAccept(powerCellLoc -> {
                if (powerCellLoc == null) {
                    plugin.getLogger().warning("未找到能量电池位置，无法保存物品");
                    closingGuard.remove(player.getUniqueId());
                    return;
                }
                
                // 在主线程上操作箱子
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Block chestBlock = powerCellLoc.getBlock();
                    if (chestBlock.getType() != Material.CHEST) {
                        plugin.getLogger().warning("能量电池位置不是箱子！");
                        closingGuard.remove(player.getUniqueId());
                        return;
                    }
                    
                    // 重新获取箱子状态，确保是最新的
                    org.bukkit.block.Chest chest = (org.bukkit.block.Chest) chestBlock.getState();
                    Inventory chestInv = chest.getInventory();
                    
                    // 保存GUI中间区域的物品回箱子
                    // GUI可用槽位映射到箱子槽位0-26（只使用前27个槽位，即使箱子是双箱）
                    int maxSlots = Math.min(27, chestInv.getSize()); // 只使用前27个槽位
                    final int[] savedCountRef = {0}; // 使用数组来避免final问题
                    
                    // 先清空前27个槽位
                    for (int i = 0; i < maxSlots; i++) {
                        chestInv.setItem(i, null);
                    }
                    
                    // 保存物品到前27个槽位
                    for (int i = 0; i < Math.min(itemsToSave.length, maxSlots); i++) {
                        if (itemsToSave[i] != null) {
                            ItemStack itemToSet = itemsToSave[i].clone();
                            chestInv.setItem(i, itemToSet);
                            savedCountRef[0]++;
                        }
                    }
                    
                    final int finalSavedCount = savedCountRef[0];
                    
                    // 关键修复：不在设置物品后立即调用update()
                    // 而是等待一小段时间，让Inventory的修改先完成，然后再更新BlockState
                    // 这样可以避免update()覆盖Inventory的修改
                    
                    // 等待2 tick后再更新BlockState
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        try {
                            // 重新获取箱子状态（确保是最新的）
                            org.bukkit.block.Chest updatedChest = (org.bukkit.block.Chest) chestBlock.getState();
                            Inventory updatedInv = updatedChest.getInventory();
                            
                            // 验证物品是否还在
                            int verifyCount = 0;
                            for (int i = 0; i < Math.min(27, updatedInv.getSize()); i++) {
                                ItemStack item = updatedInv.getItem(i);
                                if (item != null && item.getType() != Material.AIR) {
                                    verifyCount++;
                                }
                            }
                            
                            // 如果物品丢失，重新设置
                            if (verifyCount == 0 && finalSavedCount > 0) {
                                plugin.getLogger().warning("物品丢失，重新设置到箱子");
                                for (int i = 0; i < Math.min(itemsToSave.length, 27); i++) {
                                    if (itemsToSave[i] != null) {
                                        updatedInv.setItem(i, itemsToSave[i].clone());
                                    }
                                }
                                verifyCount = finalSavedCount; // 更新计数
                            }
                            
                            // 现在更新BlockState（此时Inventory应该已经有物品了）
                            updatedChest.update(true);
                            plugin.getLogger().info("箱子状态已更新");
                        } catch (Exception e) {
                            plugin.getLogger().warning("更新箱子状态时出错: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }, 2L);
                    
                    // 等待一小段时间后重新获取箱子状态并计算能源（确保状态已保存）
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        // 重新获取箱子状态，确保是最新的
                        org.bukkit.block.Chest updatedChest = (org.bukkit.block.Chest) chestBlock.getState();
                        Inventory updatedChestInv = updatedChest.getInventory();
                        
                        // 计算当前箱子中的物品能源
                        long newItemEnergy = calculateItemEnergy(updatedChestInv);
                        
                        // 计算玩家手动操作导致的能源变化（GUI打开时的初始值 vs 关闭时的值）
                        long energyChange = newItemEnergy - initialItemEnergy;
                        
                        // 只更新玩家手动添加/移除的物品变化，而不是直接覆盖整个值
                        // 这样可以避免覆盖定时任务已经消耗的物品
                        if (Math.abs(energyChange) > 0) {
                            // 从数据库重新加载最新的claim数据（可能已经被定时任务更新）
                            plugin.getChunkClaimDAO().findChunkClaimByIdAsync(claim.getId())
                                .thenAccept(claimOpt -> {
                                    if (claimOpt.isEmpty()) {
                                        return;
                                    }
                                    
                                    ChunkClaim latestClaim = claimOpt.get();
                                    // 只更新玩家手动操作的变化
                                    long updatedEnergy = latestClaim.getEnergyTime() + energyChange;
                                    latestClaim.setEnergyTime(Math.max(0, updatedEnergy));
                                    
                                    plugin.getChunkClaimDAO().updateChunkClaimAsync(latestClaim)
                                        .thenRun(() -> {
                                            plugin.getLogger().info("能源更新: 变化 " + energyChange + " 秒，新值 " + updatedEnergy + " 秒，领地 " + latestClaim.getId());
                                            
                                            // 更新缓存（通过invalidateCache然后重新加载，或者直接更新）
                                            plugin.getChunkClaimManager().invalidateCache();
                                        });
                                });
                        } else {
                        }
                    }, 2L); // 延迟2 tick，确保箱子状态已保存
                    
                    // 清理初始能源缓存
                    initialEnergyCache.remove(player.getUniqueId());
                    closingGuard.remove(player.getUniqueId());
                });
            });

        // 不要立即清理上下文，等待存钱操作完成
        // playerClaimContext.remove(player.getUniqueId());
    }

    /**
     * 处理设置GUI点击
     */
    private void handleSettingsGUIClick(Player player, InventoryClickEvent event, Inventory inv) {
        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();

        // 确保所有操作都被取消，防止按钮被移动
        event.setCancelled(true);
        
        // 如果点击的是玩家背包，直接返回
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        if (clicked == null) {
            return;
        }

        if (slot == 22) {
            // 返回按钮
            ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
            if (claim != null) {
                // 从数据库加载能量电池位置
                plugin.getChunkClaimDAO().getPowerCellLocationAsync(claim.getId())
                    .thenAccept(powerCellLoc -> {
                        openPowerCellGui(player, claim, powerCellLoc);
                    });
            }
        } else if (slot == 20) {
            // 访客权限设置
            ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
            if (claim != null) {
                plugin.getPowerCellGUI().openPermissionGUI(player, claim, true);
            }
        } else if (slot == 24) {
            // 成员权限设置
            ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
            if (claim != null) {
                plugin.getPowerCellGUI().openPermissionGUI(player, claim, false);
            }
        } else if ((slot >= 1 && slot <= 7) || (slot >= 10 && slot <= 15)) {
            // 切换设置
            ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
            if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
                player.sendMessage("§c只有所有者可以修改设置！");
                return;
            }

            Material itemType = clicked.getType();
            
            // 获取当前值并取反
            boolean currentValue = getCurrentValue(claim, itemType);
            boolean newValue = !currentValue;
            String settingName = getSettingName(itemType);
            
            if (settingName == null) {
                return;
            }

            // 更新当前领地设置
            applySetting(claim, itemType, newValue);

            // 立即更新GUI中的按钮显示
            updateSettingsButton(inv, slot, newValue, itemType);
            
            // 准备数据库更新任务列表
            List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();
            futures.add(plugin.getChunkClaimDAO().updateChunkClaimAsync(claim));
            
            // 如果属于某个区域，同步设置到区域内所有区块
            if (claim.getRegionId() > 0) {
                org.kari.kariClaims.models.ClaimRegion region = plugin.getChunkClaimManager().getRegion(claim.getRegionId());
                if (region != null) {
                    for (ChunkClaim regionChunk : region.getChunks()) {
                        // 跳过当前已经处理的claim
                        if (regionChunk.getId() == claim.getId()) {
                            continue;
                        }
                        
                        // 应用设置到区域内的其他区块
                        applySetting(regionChunk, itemType, newValue);
                        futures.add(plugin.getChunkClaimDAO().updateChunkClaimAsync(regionChunk));
                    }
                }
            }
            
            // 等待所有更新完成
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .thenRun(() -> {
                    player.sendMessage("§a" + settingName + "已" + (newValue ? "启用" : "禁用"));
                    // 如果涉及区域更新，可能需要提示
                    if (claim.getRegionId() > 0) {
                        // player.sendMessage("§7(已应用到整个区域)");
                    }
                });
        } else {
            // 其他槽位（玻璃板等）也取消操作
            event.setCancelled(true);
        }
    }
    
    private boolean getCurrentValue(ChunkClaim claim, Material itemType) {
        switch (itemType) {
            case OAK_LEAVES: return claim.isLeafDecay();
            case SPAWNER: return claim.isMobSpawning();
            case FLINT_AND_STEEL: return claim.isFireSpread();
            case DIAMOND_SWORD: return claim.isPvpEnabled();
            case TNT: return claim.isTnt();
            case GUNPOWDER: return claim.isMobGriefing();
            case ENDER_PEARL: return claim.isHomePublic();
            case ITEM_FRAME: return claim.isEntityDrop();
            case WATER_BUCKET: return claim.isWaterFlow();
            case LAVA_BUCKET: return claim.isExternalFluidInflow();
            case FEATHER: return claim.isFly();
            case IRON_DOOR: return claim.isLocked();
            case FIRE_CHARGE: return claim.isExplosion();
            default: return false;
        }
    }

    private String getSettingName(Material itemType) {
        switch (itemType) {
            case OAK_LEAVES: return "树叶腐烂";
            case SPAWNER: return "敌对生物生成";
            case FLINT_AND_STEEL: return "火焰蔓延";
            case DIAMOND_SWORD: return "PVP";
            case TNT: return "TNT爆炸破坏";
            case GUNPOWDER: return "怪物破坏";
            case ENDER_PEARL: return "Home传送公开";
            case ITEM_FRAME: return "实体掉落";
            case WATER_BUCKET: return "水流蔓延";
            case LAVA_BUCKET: return "外部流体流入";
            case FEATHER: return "飞行";
            case IRON_DOOR: return "领地锁定";
            case FIRE_CHARGE: return "爆炸";
            default: return null;
        }
    }

    private void applySetting(ChunkClaim claim, Material itemType, boolean value) {
        switch (itemType) {
            case OAK_LEAVES: claim.setLeafDecay(value); break;
            case SPAWNER: claim.setMobSpawning(value); break;
            case FLINT_AND_STEEL: claim.setFireSpread(value); break;
            case DIAMOND_SWORD: claim.setPvpEnabled(value); break;
            case TNT: claim.setTnt(value); break;
            case GUNPOWDER: claim.setMobGriefing(value); break;
            case ENDER_PEARL: claim.setHomePublic(value); break;
            case ITEM_FRAME: claim.setEntityDrop(value); break;
            case WATER_BUCKET: claim.setWaterFlow(value); break;
            case LAVA_BUCKET: claim.setExternalFluidInflow(value); break;
            case FEATHER: claim.setFly(value); break;
            case IRON_DOOR: claim.setLocked(value); break;
            case FIRE_CHARGE: claim.setExplosion(value); break;
            default: break;
        }
    }
    
    /**
     * 更新设置按钮的显示文本
     */
    private void updateSettingsButton(Inventory inv, int slot, boolean newValue, Material material) {
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getType() != material) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        
        // 更新lore
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7当前: " + (newValue ? "§a是" : "§c否")));
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7点击切换"));
        meta.lore(lore);
        
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    /**
     * 计算物品能源值
     * @param inv 可以是GUI（可用槽位10-16, 19-25, 28-34, 37-43）或箱子（槽位0-26）
     */
    private long calculateItemEnergy(Inventory inv) {
        long totalEnergy = 0;
        var itemsSection = plugin.getConfig().getConfigurationSection("power-cell.items");
        if (itemsSection == null) {
            return 0;
        }
        
        // 判断是GUI还是箱子：GUI无holder
        int[] slotsToCheck;
        if (inv.getHolder() == null) {
            // GUI：检查中间可用区域（10-16, 19-25, 28-34, 37-43）
            slotsToCheck = new int[]{10, 11, 12, 13, 14, 15, 16,
                                     19, 20, 21, 22, 23, 24, 25,
                                     28, 29, 30, 31, 32, 33, 34,
                                     37, 38, 39, 40, 41, 42, 43};
        } else {
            // 箱子：检查所有槽位（0-26）
            slotsToCheck = new int[inv.getSize()];
            for (int i = 0; i < inv.getSize(); i++) {
                slotsToCheck[i] = i;
            }
        }
        
        for (int slot : slotsToCheck) {
            if (slot >= inv.getSize()) {
                continue;
            }
            
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            
            // 查找物品的能源值
            for (String key : itemsSection.getKeys(false)) {
                String itemType = itemsSection.getString(key + ".item");
                if (itemType != null && itemType.equalsIgnoreCase(item.getType().name())) {
                    long energyValue = itemsSection.getLong(key + ".value", 0);
                    totalEnergy += energyValue * item.getAmount(); // 乘以数量
                    break;
                }
            }
        }
        
        return totalEnergy;
    }

    /**
     * 处理成员列表GUI点击
     */
    private void handleMembersGUIClick(Player player, InventoryClickEvent event, Inventory inv) {
        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null) {
            return;
        }

        event.setCancelled(true);

        if (slot == 46) {
            // 添加成员
            player.closeInventory();
            player.sendMessage("§7请使用 /claim invite <玩家> 添加成员");
        } else if (slot == 49) {
            // 返回按钮
            ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
            if (claim != null) {
                plugin.getChunkClaimDAO().getPowerCellLocationAsync(claim.getId())
                    .thenAccept(powerCellLoc -> {
                        openPowerCellGui(player, claim, powerCellLoc);
                    });
            }
        } else if (clicked.getType() == Material.PLAYER_HEAD) {
            // 点击成员头像 - 移除成员
            if (event.isRightClick()) {
                ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
                if (claim == null) return;
                
                if (!claim.getOwner().equals(player.getUniqueId())) {
                    player.sendMessage("§c只有所有者可以管理成员！");
                    return;
                }
                
                org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) clicked.getItemMeta();
                org.bukkit.OfflinePlayer target = meta.getOwningPlayer();
                
                if (target != null) {
                    plugin.getChunkClaimManager().removeMember(claim.getId(), target.getUniqueId())
                        .thenRun(() -> {
                            player.sendMessage("§a已移除成员: " + target.getName());
                            // 刷新界面
                            plugin.getServer().getScheduler().runTask(plugin, () -> 
                                plugin.getPowerCellGUI().openMembersGUI(player, claim));
                        });
                }
            }
        }
    }
    
    /**
     * 处理封禁列表GUI点击
     */
    private void handleBanListGUIClick(Player player, InventoryClickEvent event, Inventory inv) {
        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        
        if (clicked == null) return;
        event.setCancelled(true);
        
        if (slot == 49) {
            // 返回按钮
            ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
            if (claim != null) {
                plugin.getChunkClaimDAO().getPowerCellLocationAsync(claim.getId())
                    .thenAccept(powerCellLoc -> {
                        openPowerCellGui(player, claim, powerCellLoc);
                    });
            }
        } else if (clicked.getType() == Material.PLAYER_HEAD) {
            // 右键解封
            if (event.isRightClick()) {
                ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
                if (claim == null) return;
                
                if (!claim.getOwner().equals(player.getUniqueId())) {
                    player.sendMessage("§c只有所有者可以管理封禁列表！");
                    return;
                }
                
                org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) clicked.getItemMeta();
                org.bukkit.OfflinePlayer target = meta.getOwningPlayer();
                
                if (target != null) {
                    plugin.getChunkClaimManager().unbanPlayer(claim.getId(), target.getUniqueId())
                        .thenRun(() -> {
                            player.sendMessage("§a已解封玩家: " + target.getName());
                            // 刷新界面
                            plugin.getServer().getScheduler().runTask(plugin, () -> 
                                plugin.getPowerCellGUI().openBanListGUI(player, claim));
                        });
                }
            }
        }
    }
    
    /**
     * 处理权限设置GUI点击
     */
    private void handlePermissionGUIClick(Player player, InventoryClickEvent event, Inventory inv, boolean isVisitor) {
        int slot = event.getSlot();
        event.setCancelled(true);
        
        if (slot == 22) {
            // 返回
            ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
            if (claim != null) {
                plugin.getPowerCellGUI().openSettingsGUI(player, claim);
            }
            return;
        }
        
        if (slot >= 10 && slot <= 16) {
            ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
            if (claim == null) return;
            
            if (!claim.getOwner().equals(player.getUniqueId())) {
                player.sendMessage("§c只有所有者可以修改设置！");
                return;
            }
            
            int permFlag = 0;
            Material mat = event.getCurrentItem().getType();
            switch (mat) {
                case IRON_PICKAXE: permFlag = ChunkClaim.PERM_BLOCK_BREAK; break;
                case GRASS_BLOCK: permFlag = ChunkClaim.PERM_BLOCK_PLACE; break;
                case CHEST: permFlag = ChunkClaim.PERM_INTERACT; break;
                case EMERALD: permFlag = ChunkClaim.PERM_TRADE; break;
                case OAK_DOOR: permFlag = ChunkClaim.PERM_USE_DOORS; break;
                case IRON_SWORD: permFlag = ChunkClaim.PERM_ATTACK_MOBS; break;
                case REDSTONE: permFlag = ChunkClaim.PERM_USE_REDSTONE; break;
                default: return;
            }
            
            int currentPerms = isVisitor ? claim.getVisitorPermissions() : claim.getMemberPermissions();
            boolean hasPerm = (currentPerms & permFlag) != 0;
            int newPerms = hasPerm ? (currentPerms & ~permFlag) : (currentPerms | permFlag);
            
            if (isVisitor) {
                claim.setVisitorPermissions(newPerms);
            } else {
                claim.setMemberPermissions(newPerms);
            }
            
            // 更新GUI
            plugin.getPowerCellGUI().openPermissionGUI(player, claim, isVisitor);
            
            // 保存并同步
            List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();
            futures.add(plugin.getChunkClaimDAO().updateChunkClaimAsync(claim));
            
            if (claim.getRegionId() > 0) {
                org.kari.kariClaims.models.ClaimRegion region = plugin.getChunkClaimManager().getRegion(claim.getRegionId());
                if (region != null) {
                    for (ChunkClaim regionChunk : region.getChunks()) {
                        if (regionChunk.getId() == claim.getId()) continue;
                        
                        if (isVisitor) {
                            regionChunk.setVisitorPermissions(newPerms);
                        } else {
                            regionChunk.setMemberPermissions(newPerms);
                        }
                        futures.add(plugin.getChunkClaimDAO().updateChunkClaimAsync(regionChunk));
                    }
                }
            }
            
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .thenRun(() -> {
                    // 更新完成
                });
        }
    }

    /**
     * 处理金钱输入
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        
        String transactionType = awaitingMoneyTransaction.get(player.getUniqueId());
        if (transactionType == null) {
            return;
        }

        event.setCancelled(true);

        String message = LegacyComponentSerializer.legacySection().serialize(event.message()).trim();
        if (message.equalsIgnoreCase("cancel")) {
            awaitingMoneyTransaction.remove(player.getUniqueId());
            player.sendMessage("§c已取消操作");
            return;
        }

        try {
            double amount = Double.parseDouble(message);
            if (amount <= 0) {
                player.sendMessage("§c金额必须大于0！");
                return;
            }

            ChunkClaim claim = playerClaimContext.get(player.getUniqueId());
            if (claim == null) {
                awaitingMoneyTransaction.remove(player.getUniqueId());
                player.sendMessage("§c错误：无法找到领地信息");
                return;
            }

            // 检查经济系统是否启用
            if (!plugin.getEconomyManager().isEnabled()) {
                awaitingMoneyTransaction.remove(player.getUniqueId());
                player.sendMessage("§c经济系统未启用，无法操作");
                return;
            }

            if (transactionType.equals("deposit")) {
                // 存入金钱逻辑
                // 检查玩家是否有足够的钱
                if (!plugin.getEconomyManager().hasEnough(player, amount)) {
                    String balance = plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player));
                    String required = plugin.getEconomyManager().format(amount);
                    player.sendMessage("§c余额不足！");
                    player.sendMessage("§7当前余额: §e" + balance);
                    player.sendMessage("§7需要金额: §e" + required);
                    return;
                }

                // 从玩家账户扣除
                if (!plugin.getEconomyManager().withdraw(player, amount)) {
                    player.sendMessage("§c扣款失败，请重试");
                    return;
                }

                // 添加到能量电池
                claim.setEconomyBalance(claim.getEconomyBalance() + amount);
                plugin.getChunkClaimDAO().updateChunkClaimAsync(claim)
                    .thenRun(() -> {
                        awaitingMoneyTransaction.remove(player.getUniqueId());
                        String formattedAmount = plugin.getEconomyManager().format(amount);
                        player.sendMessage("§a已存入 " + formattedAmount + " 到能量电池！");
                        
                        // 计算可续费时间
                        double pricePerHour = plugin.getConfig().getDouble("power-cell.economy-price-per-hour", 100.0);
                        long hours = (long)(amount / pricePerHour);
                        long minutes = (long)((amount % pricePerHour) / (pricePerHour / 60.0));
                        
                        // 存钱完成后，如果玩家不在GUI中，清理上下文
                        if (!player.isOnline() || player.getOpenInventory().title() == null || 
                            !LegacyComponentSerializer.legacySection().serialize(player.getOpenInventory().title()).endsWith("的领地")) {
                            playerClaimContext.remove(player.getUniqueId());
                        }
                        player.sendMessage("§7可续费约 §e" + hours + "小时" + (minutes > 0 ? minutes + "分钟" : ""));
                    });
            } else if (transactionType.equals("withdraw")) {
                // 取出金钱逻辑
                // 检查领地余额是否足够
                if (claim.getEconomyBalance() < amount) {
                    player.sendMessage("§c领地余额不足！");
                    player.sendMessage("§7当前余额: §e" + String.format("%.2f", claim.getEconomyBalance()));
                    return;
                }
                
                // 从能量电池扣除
                claim.setEconomyBalance(claim.getEconomyBalance() - amount);
                
                // 存入玩家账户
                plugin.getEconomyManager().deposit(player, amount);
                
                plugin.getChunkClaimDAO().updateChunkClaimAsync(claim)
                    .thenRun(() -> {
                        awaitingMoneyTransaction.remove(player.getUniqueId());
                        String formattedAmount = plugin.getEconomyManager().format(amount);
                        player.sendMessage("§a已从能量电池取出 " + formattedAmount + "！");
                        
                        if (!player.isOnline() || player.getOpenInventory().title() == null || 
                            !LegacyComponentSerializer.legacySection().serialize(player.getOpenInventory().title()).endsWith("的领地")) {
                            playerClaimContext.remove(player.getUniqueId());
                        }
                    });
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的金额！请输入数字或 'cancel' 取消");
        }
    }
}

