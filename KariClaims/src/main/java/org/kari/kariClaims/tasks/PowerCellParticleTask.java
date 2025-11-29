package org.kari.kariClaims.tasks;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.kari.kariClaims.KariClaims;

import java.util.Collection;

/**
 * 能量电池粒子效果任务
 * 低性能消耗：只对附近玩家显示，且间隔较长
 */
public class PowerCellParticleTask extends BukkitRunnable {
    private final KariClaims plugin;
    private int tick = 0;

    public PowerCellParticleTask(KariClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // 检查配置是否启用
        if (!plugin.getConfig().getBoolean("power-cell.particles.enabled", true)) {
            return;
        }
        
        tick++;
        
        // 获取所有注册的能量电池位置
        Collection<Location> powerCellLocations = plugin.getChunkClaimManager().getAllPowerCellLocations();
        
        for (Location loc : powerCellLocations) {
            World world = loc.getWorld();
            if (world == null) continue;
            
            // 检查是否有玩家在附近（32格范围内）
            boolean hasNearbyPlayer = world.getPlayers().stream()
                .anyMatch(p -> p.getLocation().distanceSquared(loc) < 1024); // 32^2
            
            if (!hasNearbyPlayer) continue;
            
            // 显示粒子效果 - 柔和的心跳光环效果
            Location center = loc.clone().add(0.5, 0.8, 0.5);
            
            // 呼吸灯效果：缓慢起伏的光点
            double phase = (tick % 60) * Math.PI / 30; // 3秒一个周期
            double heightOffset = Math.sin(phase) * 0.15;
            
            // 在箱子上方显示少量柔和粒子
            world.spawnParticle(Particle.HAPPY_VILLAGER, 
                center.clone().add(0, heightOffset, 0), 
                1, 0.15, 0.1, 0.15, 0);
            
            // 每3秒显示一次淡淡的光环
            if (tick % 60 == 0) {
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    double px = center.getX() + Math.cos(angle) * 0.4;
                    double pz = center.getZ() + Math.sin(angle) * 0.4;
                    world.spawnParticle(Particle.END_ROD, px, center.getY(), pz, 1, 0, 0, 0, 0);
                }
            }
        }
    }
    
    /**
     * 启动任务
     * @param intervalTicks 粒子更新间隔（tick）
     */
    public void start(int intervalTicks) {
        this.runTaskTimer(plugin, 20L, intervalTicks); // 1秒后开始
    }
}
