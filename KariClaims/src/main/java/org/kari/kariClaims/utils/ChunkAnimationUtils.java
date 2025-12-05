package org.kari.kariClaims.utils;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.kari.kariClaims.KariClaims;

import java.util.*;

/**
 * 区块动画工具类 - 用于claim/unclaim时的视觉效果
 */
public class ChunkAnimationUtils {
    
    /**
     * 播放区块认领动画（绿宝石块滚过地面）
     * @param plugin 插件实例
     * @param chunk 区块
     * @param player 玩家（用于发送粒子效果）
     */
    public static void playClaimAnimation(KariClaims plugin, Chunk chunk, Player player) {
        playRollingBlockAnimation(plugin, chunk, player, Material.EMERALD_BLOCK, true);
    }
    
    /**
     * 播放区块取消认领动画（红石块滚过地面）
     * @param plugin 插件实例
     * @param chunk 区块
     * @param player 玩家（用于发送粒子效果）
     */
    public static void playUnclaimAnimation(KariClaims plugin, Chunk chunk, Player player) {
        playRollingBlockAnimation(plugin, chunk, player, Material.REDSTONE_BLOCK, false);
    }
    
    /**
     * 播放能量电池破坏动画
     * @param plugin 插件实例
     * @param location 能量电池位置
     */
    public static void playPowerCellDestroyAnimation(KariClaims plugin, Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        Location center = location.clone().add(0.5, 0.5, 0.5);
        
        // 播放爆炸粒子效果
        world.spawnParticle(Particle.EXPLOSION, center, 3, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticle(Particle.SMOKE, center, 20, 0.5, 0.5, 0.5, 0.05);
        world.spawnParticle(Particle.FLAME, center, 15, 0.4, 0.4, 0.4, 0.05);
        
        // 播放爆炸音效
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        world.playSound(location, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
        
        // 延迟播放余韵效果
        new BukkitRunnable() {
            @Override
            public void run() {
                world.spawnParticle(Particle.CLOUD, location.clone().add(0.5, 1, 0.5), 10, 0.3, 0.3, 0.3, 0.02);
            }
        }.runTaskLater(plugin, 5L);
    }
    
    /**
     * 播放波浪方块动画 - 从边缘向中心扩散
     */
    private static void playRollingBlockAnimation(KariClaims plugin, Chunk chunk, Player player, 
                                                   Material blockType, boolean isGreen) {
        World world = chunk.getWorld();
        if (world == null) return;
        
        int baseX = chunk.getX() * 16;
        int baseZ = chunk.getZ() * 16;
        BlockData fakeBlockData = blockType.createBlockData();
        
        // 收集所有需要显示的位置（按层分组）
        List<List<Location>> layers = new ArrayList<>();
        
        // 从外向内，共8层（0-7对应边缘到中心）
        for (int layer = 0; layer < 8; layer++) {
            List<Location> layerLocations = new ArrayList<>();
            
            // 计算这一层的边界
            int minOffset = layer;
            int maxOffset = 15 - layer;
            
            if (minOffset > maxOffset) break;
            
            // 收集这一层的所有边缘点
            for (int i = minOffset; i <= maxOffset; i++) {
                // 上边和下边
                addSurfaceLocation(world, baseX + i, baseZ + minOffset, layerLocations);
                if (minOffset != maxOffset) {
                    addSurfaceLocation(world, baseX + i, baseZ + maxOffset, layerLocations);
                }
                // 左边和右边（排除角落避免重复）
                if (i > minOffset && i < maxOffset) {
                    addSurfaceLocation(world, baseX + minOffset, baseZ + i, layerLocations);
                    addSurfaceLocation(world, baseX + maxOffset, baseZ + i, layerLocations);
                }
            }
            
            if (!layerLocations.isEmpty()) {
                layers.add(layerLocations);
            }
        }
        
        // 存储所有显示的假方块位置
        List<Location> allFakeBlocks = new ArrayList<>();
        
        // 逐层显示动画
        new BukkitRunnable() {
            int currentLayer = 0;
            
            @Override
            public void run() {
                if (!player.isOnline() || currentLayer >= layers.size()) {
                    this.cancel();
                    // 延迟后恢复所有方块
                    scheduleRestore(plugin, player, world, allFakeBlocks);
                    return;
                }
                
                List<Location> layerLocs = layers.get(currentLayer);
                for (Location loc : layerLocs) {
                    player.sendBlockChange(loc, fakeBlockData);
                    allFakeBlocks.add(loc);
                }
                
                // 播放一次音效
                if (!layerLocs.isEmpty()) {
                    Location soundLoc = layerLocs.get(0);
                    world.playSound(soundLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.0f + currentLayer * 0.1f);
                }
                
                currentLayer++;
            }
        }.runTaskTimer(plugin, 0L, 2L); // 每2tick一层
    }
    
    /**
     * 添加地面位置到列表
     */
    private static void addSurfaceLocation(World world, int x, int z, List<Location> list) {
        int y = getGroundY(world, x, z);
        if (y != Integer.MIN_VALUE) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() && !isSpecialBlock(block.getType())) {
                list.add(block.getLocation());
            }
        }
    }
    
    /**
     * 计划恢复假方块显示
     */
    private static void scheduleRestore(KariClaims plugin, Player player, World world, List<Location> locations) {
        new BukkitRunnable() {
            int layer = 0;
            final int layerSize = Math.max(1, locations.size() / 8);
            
            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    return;
                }
                
                int start = layer * layerSize;
                int end = Math.min(start + layerSize, locations.size());
                
                if (start >= locations.size()) {
                    this.cancel();
                    return;
                }
                
                for (int i = start; i < end; i++) {
                    Location loc = locations.get(i);
                    player.sendBlockChange(loc, loc.getBlock().getBlockData());
                }
                
                layer++;
            }
        }.runTaskTimer(plugin, 20L, 2L); // 1秒后开始，每2tick恢复一批
    }
    
    /**
     * 检查是否是特殊方块（不应该被替换）
     */
    private static boolean isSpecialBlock(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST ||
               type == Material.ENDER_CHEST || type == Material.BARREL ||
               type == Material.FURNACE || type == Material.BLAST_FURNACE ||
               type == Material.SMOKER || type == Material.HOPPER ||
               type == Material.DROPPER || type == Material.DISPENSER ||
               type == Material.BEACON || type == Material.ENCHANTING_TABLE ||
               type == Material.ANVIL || type == Material.CHIPPED_ANVIL ||
               type == Material.DAMAGED_ANVIL || type == Material.CRAFTING_TABLE ||
               type == Material.BREWING_STAND || type == Material.LECTERN ||
               type.name().contains("SIGN") || type.name().contains("BED") ||
               type.name().contains("DOOR") || type.name().contains("SHULKER");
    }
    
    /**
     * 获取指定位置的地面Y坐标
     * @return 地面Y坐标，如果找不到则返回 Integer.MIN_VALUE
     */
    private static int getGroundY(World world, int x, int z) {
        // 从较高位置向下搜索（支持1.18+的扩展世界高度）
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        
        for (int y = maxY - 1; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            
            // 找到一个固体方块，且上方是空气或可通过的方块
            if (block.getType().isSolid() && !above.getType().isSolid()) {
                return y;
            }
        }
        return Integer.MIN_VALUE; // 使用明确的"未找到"标记
    }
    
    /**
     * 播放简单的边界高亮动画
     * @param plugin 插件实例
     * @param chunk 区块
     * @param player 玩家
     * @param color 颜色
     */
    public static void playBorderHighlight(KariClaims plugin, Chunk chunk, Player player, Color color) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX() * 16;
        int chunkZ = chunk.getZ() * 16;
        
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.5f);
        
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 20) { // 1秒
                    this.cancel();
                    return;
                }
                
                // 绘制区块边界
                for (int i = 0; i < 16; i += 2) {
                    int y = getGroundY(world, chunkX + i, chunkZ);
                    if (y != Integer.MIN_VALUE) {
                        world.spawnParticle(Particle.DUST, chunkX + i + 0.5, y + 1.5, chunkZ + 0.5, 1, dustOptions);
                        world.spawnParticle(Particle.DUST, chunkX + i + 0.5, y + 1.5, chunkZ + 15.5, 1, dustOptions);
                    }
                    
                    y = getGroundY(world, chunkX, chunkZ + i);
                    if (y != Integer.MIN_VALUE) {
                        world.spawnParticle(Particle.DUST, chunkX + 0.5, y + 1.5, chunkZ + i + 0.5, 1, dustOptions);
                        world.spawnParticle(Particle.DUST, chunkX + 15.5, y + 1.5, chunkZ + i + 0.5, 1, dustOptions);
                    }
                }
                
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
