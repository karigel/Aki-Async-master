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
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL ||
            event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            Optional<Claim> claim = plugin.getClaimManager().findClaimAt(event.getLocation());
            if (claim.isPresent() && !claim.get().isMobSpawning()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (to == null || (from.getBlockX() == to.getBlockX() && 
            from.getBlockZ() == to.getBlockZ() && from.getBlockY() == to.getBlockY())) {
            return;
        }

        Optional<Claim> fromClaim = plugin.getClaimManager().findClaimAt(from);
        Optional<Claim> toClaim = plugin.getClaimManager().findClaimAt(to);

        if (fromClaim.isEmpty() && toClaim.isPresent()) {
            // 进入领地
            Claim claim = toClaim.get();
            if (claim.isEnterMessage() && !claim.getEnterMessageText().isEmpty()) {
                player.sendMessage(claim.getEnterMessageText().replace("{claim}", claim.getName()));
            }
        } else if (fromClaim.isPresent() && toClaim.isEmpty()) {
            // 离开领地
            Claim claim = fromClaim.get();
            if (claim.isExitMessage() && !claim.getExitMessageText().isEmpty()) {
                player.sendMessage(claim.getExitMessageText().replace("{claim}", claim.getName()));
            }
        }
    }
}

