package org.virgil.akiasync.network;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场景检测器 / Scenario Detector
 * 检测玩家当前的游戏场景以优化网络传输
 * Detects player's current game scenario to optimize network transmission
 */
public class ScenarioDetector {

    private final Map<UUID, PlayerScenario> playerScenarios = new ConcurrentHashMap<>();

    public enum Scenario {
        IDLE,           // 空闲状态 / Idle state
        WALKING,        // 行走中 / Walking
        SPRINTING,      // 疾跑中 / Sprinting
        FLYING,         // 飞行中 / Flying
        ELYTRA,         // 鞘翅滑翔 / Elytra gliding
        COMBAT,         // 战斗中 / In combat
        BUILDING,       // 建造中 / Building
        MINING,         // 挖掘中 / Mining
        TELEPORTING,    // 传送中 / Teleporting
        CHUNK_LOADING   // 区块加载中 / Chunk loading
    }

    /**
     * 更新玩家场景 / Update player scenario
     */
    public void updateScenario(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerScenario scenario = playerScenarios.computeIfAbsent(playerId, 
            k -> new PlayerScenario());
        
        scenario.update(player);
    }

    /**
     * 获取玩家当前场景 / Get player's current scenario
     */
    public Scenario getScenario(UUID playerId) {
        PlayerScenario scenario = playerScenarios.get(playerId);
        return scenario != null ? scenario.getCurrentScenario() : Scenario.IDLE;
    }

    /**
     * 判断是否需要高频数据包 / Check if high frequency packets are needed
     */
    public boolean needsHighFrequencyPackets(UUID playerId) {
        Scenario scenario = getScenario(playerId);
        return scenario == Scenario.COMBAT || 
               scenario == Scenario.ELYTRA || 
               scenario == Scenario.TELEPORTING;
    }

    /**
     * 判断是否可以降低数据包频率 / Check if packet frequency can be reduced
     */
    public boolean canReducePacketFrequency(UUID playerId) {
        Scenario scenario = getScenario(playerId);
        return scenario == Scenario.IDLE || scenario == Scenario.WALKING;
    }

    /**
     * 移除玩家 / Remove player
     */
    public void removePlayer(UUID playerId) {
        playerScenarios.remove(playerId);
    }

    /**
     * 清除所有数据 / Clear all data
     */
    public void clear() {
        playerScenarios.clear();
    }

    private static class PlayerScenario {
        private Scenario currentScenario = Scenario.IDLE;
        private long lastUpdateTime = System.currentTimeMillis();
        private double lastX, lastY, lastZ;
        private boolean wasFlying = false;
        private boolean wasGliding = false;

        public void update(Player player) {
            long now = System.currentTimeMillis();
            double x = player.getLocation().getX();
            double y = player.getLocation().getY();
            double z = player.getLocation().getZ();
            
            double deltaX = x - lastX;
            double deltaY = y - lastY;
            double deltaZ = z - lastZ;
            double speed = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
            
            // 检测场景 / Detect scenario
            if (player.isGliding()) {
                currentScenario = Scenario.ELYTRA;
            } else if (player.isFlying()) {
                currentScenario = Scenario.FLYING;
            } else if (player.isSprinting() && speed > 0.2) {
                currentScenario = Scenario.SPRINTING;
            } else if (speed > 0.1) {
                currentScenario = Scenario.WALKING;
            } else {
                currentScenario = Scenario.IDLE;
            }
            
            // 更新状态 / Update state
            lastX = x;
            lastY = y;
            lastZ = z;
            lastUpdateTime = now;
            wasFlying = player.isFlying();
            wasGliding = player.isGliding();
        }

        public Scenario getCurrentScenario() {
            return currentScenario;
        }
    }
}
