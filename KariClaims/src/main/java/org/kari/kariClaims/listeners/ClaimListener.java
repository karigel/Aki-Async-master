package org.kari.kariClaims.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.models.Claim;

import java.util.Optional;

/**
 * 领地事件监听器 - 保护领地
 */
public class ClaimListener implements Listener {
    private final KariClaims plugin;
    // 玩家最后所在领地缓存，用于优化移动检查
    private final java.util.Map<java.util.UUID, Integer> lastClaimMap = new java.util.concurrent.ConcurrentHashMap<>();
    // 缓存领地对象以便显示离开消息
    private final java.util.Map<java.util.UUID, Claim> lastClaimObjectMap = new java.util.concurrent.ConcurrentHashMap<>();

    public ClaimListener(KariClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("kariClaims.admin.bypass")) {
            return;
        }

        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!plugin.getClaimManager().hasPermissionSync(player, claim.get(), "break")) {
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

        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!plugin.getClaimManager().hasPermissionSync(player, claim.get(), "build")) {
            event.setCancelled(true);
            player.sendMessage("§c你没有权限在这个领地放置方块！");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("kariClaims.admin.bypass")) {
            return;
        }

        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(event.getClickedBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        Material blockType = event.getClickedBlock().getType();
        String permission = "interact";
        
        // 特殊方块需要特定权限
        if (blockType == Material.CHEST || blockType == Material.TRAPPED_CHEST || 
            blockType == Material.BARREL || blockType == Material.SHULKER_BOX) {
            permission = "chest";
        } else if (blockType == Material.FURNACE || blockType == Material.BLAST_FURNACE || 
                   blockType == Material.SMOKER) {
            permission = "furnace";
        } else if (blockType == Material.ANVIL || blockType == Material.CHIPPED_ANVIL || 
                   blockType == Material.DAMAGED_ANVIL) {
            permission = "anvil";
        } else if (blockType == Material.ENCHANTING_TABLE) {
            permission = "enchant";
        } else if (blockType == Material.BREWING_STAND) {
            permission = "brew";
        } else if (blockType == Material.BEACON) {
            permission = "beacon";
        } else if (blockType == Material.HOPPER || blockType == Material.DROPPER || 
                   blockType == Material.DISPENSER) {
            permission = "hopper";
        }

        if (!plugin.getClaimManager().hasPermissionSync(player, claim.get(), permission)) {
            event.setCancelled(true);
            player.sendMessage("§c你没有权限在这个领地交互！");
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

        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(player.getLocation());
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

        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(event.getEntity().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!claim.get().isPvpEnabled()) {
            event.setCancelled(true);
            player.sendMessage("§c这个领地禁止 PVP！");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.SPREAD) {
            return;
        }

        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!claim.get().isFireSpread()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(event.getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!claim.get().isExplosion()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(event.getBlock().getLocation());
        if (claim.isEmpty()) {
            return;
        }

        if (!claim.get().isExplosion()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // 只检查自然生成和刷怪笼生成
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL &&
            event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) {
            return;
        }
        
        // 只检查敌对生物（优化：跳过被动生物、NPC等）
        if (!(event.getEntity() instanceof org.bukkit.entity.Monster)) {
            return;
        }
        
        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(event.getLocation());
        if (claim.isPresent() && !claim.get().isMobSpawning()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // 优化：只在玩家跨越方块边界时检查
        if (to == null || (from.getBlockX() == to.getBlockX() && 
            from.getBlockZ() == to.getBlockZ() && from.getBlockY() == to.getBlockY())) {
            return;
        }

        Optional<Claim> toClaim = plugin.getClaimManager().findClaimAt(to);
        Integer toClaimId = toClaim.map(Claim::getId).orElse(-1);
        
        // 使用缓存检查是否进入了不同的领地
        Integer lastClaimId = lastClaimMap.put(player.getUniqueId(), toClaimId);
        Claim lastClaim = lastClaimObjectMap.get(player.getUniqueId());
        
        // 如果领地没有变化，跳过
        if (toClaimId.equals(lastClaimId)) {
            return;
        }

        // 领地变化了，处理进入/离开消息
        if (lastClaimId == null || lastClaimId == -1) {
            // 从荒野进入领地
            if (toClaim.isPresent()) {
                Claim claim = toClaim.get();
                lastClaimObjectMap.put(player.getUniqueId(), claim);
                if (claim.isEnterMessage() && !claim.getEnterMessageText().isEmpty()) {
                    player.sendMessage(claim.getEnterMessageText().replace("{claim}", claim.getName()));
                }
            }
        } else if (toClaimId == -1) {
            // 从领地离开到荒野
            lastClaimObjectMap.remove(player.getUniqueId());
            if (lastClaim != null) {
                if (lastClaim.isExitMessage() && !lastClaim.getExitMessageText().isEmpty()) {
                    player.sendMessage(lastClaim.getExitMessageText().replace("{claim}", lastClaim.getName()));
                }
            }
        } else {
            // 从一个领地到另一个领地
            if (toClaim.isPresent()) {
                Claim claim = toClaim.get();
                lastClaimObjectMap.put(player.getUniqueId(), claim);
                // 显示离开上一个领地的消息
                if (lastClaim != null && lastClaim.isExitMessage() && !lastClaim.getExitMessageText().isEmpty()) {
                    player.sendMessage(lastClaim.getExitMessageText().replace("{claim}", lastClaim.getName()));
                }
                // 显示进入新领地的消息
                if (claim.isEnterMessage() && !claim.getEnterMessageText().isEmpty()) {
                    player.sendMessage(claim.getEnterMessageText().replace("{claim}", claim.getName()));
                }
            }
        }
    }
}

