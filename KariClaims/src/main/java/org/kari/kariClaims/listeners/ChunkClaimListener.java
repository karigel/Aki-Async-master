package org.kari.kariClaims.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.models.ChunkClaim;

import java.util.Optional;

/**
 * 区块领地事件监听器 - 保护领地和实现设置
 */
public class ChunkClaimListener implements Listener {
    private final KariClaims plugin;

    public ChunkClaimListener(KariClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("kariClaims.admin.bypass")) {
            return;
        }

        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!plugin.getChunkClaimManager().hasPermissionSync(player, claim.get(), "break")) {
            event.setCancelled(true);
            player.sendMessage("§c你没有权限在这个领地破坏方块！");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("kariClaims.admin.bypass")) {
            return;
        }

        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!plugin.getChunkClaimManager().hasPermissionSync(player, claim.get(), "build")) {
            event.setCancelled(true);
            player.sendMessage("§c你没有权限在这个领地放置方块！");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (player.hasPermission("kariClaims.admin.bypass")) {
            return;
        }

        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!claim.get().isPvpEnabled()) {
            if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();
        if (player.hasPermission("kariClaims.admin.bypass")) {
            return;
        }

        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getEntity().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (event.getEntity() instanceof Player) {
            if (!claim.get().isPvpEnabled()) {
                event.setCancelled(true);
                player.sendMessage("§c这个领地禁止 PVP！");
            }
        } else if (event.getEntity() instanceof org.bukkit.entity.LivingEntity) {
            if (!plugin.getChunkClaimManager().hasPermissionSync(player, claim.get(), "attack")) {
                event.setCancelled(true);
                player.sendMessage("§c你没有权限在此领地攻击生物！");
            }
        } else {
            // 非生物实体（展示框、画等）视为破坏
            if (!plugin.getChunkClaimManager().hasPermissionSync(player, claim.get(), "break")) {
                event.setCancelled(true);
                player.sendMessage("§c你没有权限在此领地破坏实体！");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) {
            return;
        }
        
        org.bukkit.block.Block block = event.getClickedBlock();
        if (block == null) return;
        
        Player player = event.getPlayer();
        if (player.hasPermission("kariClaims.admin.bypass")) return;
        
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(block.getLocation());
        if (claim.isEmpty()) return;
        
        Material type = block.getType();
        String perm = "interact";
        
        if (isDoor(type)) {
            perm = "door";
        } else if (isRedstone(type)) {
            perm = "redstone";
        } else if (isContainer(type)) {
            perm = "interact";
        } else if (event.getAction() == Action.PHYSICAL) {
             // 压力板等物理触发
             perm = "redstone";
        } else {
            // 其他方块交互，默认为interact
            perm = "interact";
        }
        
        if (!plugin.getChunkClaimManager().hasPermissionSync(player, claim.get(), perm)) {
            event.setCancelled(true);
            if (event.getAction() != Action.PHYSICAL) {
                player.sendMessage("§c你没有权限在此领地进行此操作！");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("kariClaims.admin.bypass")) return;
        
        org.bukkit.entity.Entity entity = event.getRightClicked();
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(entity.getLocation());
        if (claim.isEmpty()) return;
        
        String perm = "interact";
        if (entity instanceof org.bukkit.entity.Villager || entity instanceof org.bukkit.entity.WanderingTrader) {
            perm = "trade";
        } else if (entity instanceof org.bukkit.entity.ItemFrame || entity instanceof org.bukkit.entity.ArmorStand) {
            // 旋转展示框物品或调整盔甲架
            perm = "interact";
        }
        
        if (!plugin.getChunkClaimManager().hasPermissionSync(player, claim.get(), perm)) {
            event.setCancelled(true);
            player.sendMessage("§c你没有权限在此领地进行此操作！");
        }
    }
    
    private boolean isDoor(Material type) {
        return type.name().contains("DOOR") || type.name().contains("GATE") || type.name().contains("TRAPDOOR");
    }
    
    private boolean isRedstone(Material type) {
        return type.name().contains("BUTTON") || type.name().contains("LEVER") || 
               type.name().contains("PLATE") || type == Material.NOTE_BLOCK ||
               type == Material.REPEATER || type == Material.COMPARATOR ||
               type == Material.DAYLIGHT_DETECTOR;
    }
    
    private boolean isContainer(Material type) {
        return type.name().contains("CHEST") || type.name().contains("SHULKER") || 
               type.name().contains("BARREL") || type == Material.HOPPER ||
               type == Material.DISPENSER || type == Material.DROPPER ||
               type == Material.FURNACE || type == Material.BLAST_FURNACE ||
               type == Material.SMOKER || type == Material.BREWING_STAND;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.SPREAD) {
            return;
        }

        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!claim.get().isFireSpread()) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (event.getNewState().getType() != org.bukkit.Material.FIRE) {
            return;
        }
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getBlock().getLocation());
        if (claim.isPresent() && !claim.get().isFireSpread()) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireBurn(BlockBurnEvent event) {
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getBlock().getLocation());
        if (claim.isPresent() && !claim.get().isFireSpread()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!claim.get().isExplosion()) {
            event.setCancelled(true);
            return;
        }

        if (!claim.get().isTnt()) {
            event.blockList().clear();
            event.setYield(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!claim.get().isExplosion()) {
            event.setCancelled(true);
            return;
        }

        if (!claim.get().isTnt()) {
            event.blockList().clear();
            event.setYield(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL ||
            event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getLocation());
            if (claim.isPresent() && !claim.get().isMobSpawning()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // 怪物破坏（如末影人搬方块、苦力怕爆炸等）
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!claim.get().isMobGriefing()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!claim.get().isLeafDecay()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        // 水流蔓延
        Optional<ChunkClaim> toClaimOpt = plugin.getChunkClaimManager().findChunkClaimAt(event.getToBlock().getLocation());
        if (toClaimOpt.isEmpty()) {
            return;
        }

        ChunkClaim toClaim = toClaimOpt.get();
        if (!toClaim.isWaterFlow()) {
            event.setCancelled(true);
            return;
        }
        
        Optional<ChunkClaim> fromClaimOpt = plugin.getChunkClaimManager().findChunkClaimAt(event.getBlock().getLocation());
        boolean isExternal = true;
        
        if (fromClaimOpt.isPresent()) {
            ChunkClaim fromClaim = fromClaimOpt.get();
            if (fromClaim.getId() == toClaim.getId()) {
                // 同一个区块
                isExternal = false;
            } else if (fromClaim.getRegionId() > 0 && fromClaim.getRegionId() == toClaim.getRegionId()) {
                // 同一个区域的不同区块
                isExternal = false;
            }
        }
        
        if (isExternal && !toClaim.isExternalFluidInflow()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("kariClaims.admin.bypass") || 
            player.hasPermission("kariClaims.bypass.fly")) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(to);
        if (claim.isEmpty()) {
            return;
        }

        // 检查飞行设置
        if (!claim.get().isFly() && player.isFlying() && !player.hasPermission("kariClaims.bypass.fly")) {
            player.setFlying(false);
            player.setAllowFlight(false);
            player.sendMessage("§c此领地禁止飞行！");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDropItem(org.bukkit.event.entity.EntityDropItemEvent event) {
        // 实体掉落物品
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(event.getEntity().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!claim.get().isEntityDrop()) {
            event.setCancelled(true);
        }
    }
}

