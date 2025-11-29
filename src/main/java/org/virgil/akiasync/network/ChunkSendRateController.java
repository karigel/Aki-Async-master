package org.virgil.akiasync.network;

import org.bukkit.entity.Player;
import org.virgil.akiasync.AkiAsyncPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区块发送速率控制器 / Chunk Send Rate Controller
 * 根据网络状况动态调整区块发送速率
 * Dynamically adjusts chunk send rate based on network conditions
 */
public class ChunkSendRateController {

    private final AkiAsyncPlugin plugin;
    private final NetworkCongestionDetector congestionDetector;
    private final Map<UUID, PlayerChunkState> playerStates = new ConcurrentHashMap<>();

    private int baseChunkSendRate = 10;
    private int maxChunkSendRate = 20;
    private int minChunkSendRate = 2;

    public ChunkSendRateController(AkiAsyncPlugin plugin, NetworkCongestionDetector congestionDetector) {
        this.plugin = plugin;
        this.congestionDetector = congestionDetector;
    }

    public void updatePlayerLocation(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerChunkState state = playerStates.computeIfAbsent(playerId, 
            k -> new PlayerChunkState());
        
        state.updateLocation(player.getLocation());
        
        // 根据拥塞级别调整发送速率 / Adjust send rate based on congestion level
        NetworkCongestionDetector.CongestionLevel level = congestionDetector.getCongestionLevel(playerId);
        int rate = calculateRate(level);
        state.setCurrentRate(rate);
    }

    private int calculateRate(NetworkCongestionDetector.CongestionLevel level) {
        switch (level) {
            case CRITICAL:
                return minChunkSendRate;
            case HIGH:
                return (baseChunkSendRate + minChunkSendRate) / 2;
            default:
                return baseChunkSendRate;
        }
    }

    public int getPlayerChunkRate(UUID playerId) {
        PlayerChunkState state = playerStates.get(playerId);
        return state != null ? state.getCurrentRate() : baseChunkSendRate;
    }

    public String getPlayerStatistics(UUID playerId) {
        PlayerChunkState state = playerStates.get(playerId);
        if (state == null) {
            return "No data";
        }
        return String.format("Rate: %d chunks/s", state.getCurrentRate());
    }

    public void removePlayer(UUID playerId) {
        playerStates.remove(playerId);
    }

    public void clear() {
        playerStates.clear();
    }

    public void setBaseChunkSendRate(int rate) {
        this.baseChunkSendRate = rate;
    }

    public void setMaxChunkSendRate(int rate) {
        this.maxChunkSendRate = rate;
    }

    public void setMinChunkSendRate(int rate) {
        this.minChunkSendRate = rate;
    }

    public int getBaseChunkSendRate() {
        return baseChunkSendRate;
    }

    public int getMaxChunkSendRate() {
        return maxChunkSendRate;
    }

    public int getMinChunkSendRate() {
        return minChunkSendRate;
    }

    private static class PlayerChunkState {
        private org.bukkit.Location lastLocation;
        private int currentRate = 10;
        private long lastUpdateTime = System.currentTimeMillis();

        public void updateLocation(org.bukkit.Location location) {
            this.lastLocation = location;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public int getCurrentRate() {
            return currentRate;
        }

        public void setCurrentRate(int rate) {
            this.currentRate = rate;
        }
    }
}
