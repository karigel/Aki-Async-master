package org.virgil.akiasync.network;

import org.bukkit.entity.Player;
import org.virgil.akiasync.AkiAsyncPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网络拥塞检测器 / Network Congestion Detector
 * 监控玩家ping和带宽使用情况
 * Monitors player ping and bandwidth usage
 */
public class NetworkCongestionDetector {

    private final AkiAsyncPlugin plugin;
    private final Map<UUID, PlayerNetworkState> playerStates = new ConcurrentHashMap<>();

    private int highPingThreshold = 150;
    private int criticalPingThreshold = 300;
    private long highBandwidthThreshold = 1000000L; // 1MB/s

    public NetworkCongestionDetector(AkiAsyncPlugin plugin) {
        this.plugin = plugin;
    }

    public void updatePlayerPing(Player player) {
        UUID playerId = player.getUniqueId();
        int ping = player.getPing();

        PlayerNetworkState state = playerStates.computeIfAbsent(playerId, 
            k -> new PlayerNetworkState());
        state.updatePing(ping);
    }

    public CongestionLevel getCongestionLevel(UUID playerId) {
        PlayerNetworkState state = playerStates.get(playerId);
        if (state == null) {
            return CongestionLevel.NORMAL;
        }

        int avgPing = state.getAveragePing();
        if (avgPing >= criticalPingThreshold) {
            return CongestionLevel.CRITICAL;
        } else if (avgPing >= highPingThreshold) {
            return CongestionLevel.HIGH;
        }
        return CongestionLevel.NORMAL;
    }

    public String getPlayerStatistics(UUID playerId) {
        PlayerNetworkState state = playerStates.get(playerId);
        if (state == null) {
            return "No data";
        }
        return String.format("Ping: %dms (avg: %dms), Level: %s",
            state.getCurrentPing(),
            state.getAveragePing(),
            getCongestionLevel(playerId));
    }

    public void removePlayer(UUID playerId) {
        playerStates.remove(playerId);
    }

    public void clear() {
        playerStates.clear();
    }

    public void setHighPingThreshold(int threshold) {
        this.highPingThreshold = threshold;
    }

    public void setCriticalPingThreshold(int threshold) {
        this.criticalPingThreshold = threshold;
    }

    public void setHighBandwidthThreshold(long threshold) {
        this.highBandwidthThreshold = threshold;
    }

    public int getHighPingThreshold() {
        return highPingThreshold;
    }

    public int getCriticalPingThreshold() {
        return criticalPingThreshold;
    }

    public long getHighBandwidthThreshold() {
        return highBandwidthThreshold;
    }

    public enum CongestionLevel {
        NORMAL,
        HIGH,
        CRITICAL
    }

    private static class PlayerNetworkState {
        private static final int SAMPLE_SIZE = 10;
        private final int[] pingSamples = new int[SAMPLE_SIZE];
        private int sampleIndex = 0;
        private int sampleCount = 0;
        private int currentPing = 0;

        public void updatePing(int ping) {
            this.currentPing = ping;
            pingSamples[sampleIndex] = ping;
            sampleIndex = (sampleIndex + 1) % SAMPLE_SIZE;
            if (sampleCount < SAMPLE_SIZE) {
                sampleCount++;
            }
        }

        public int getCurrentPing() {
            return currentPing;
        }

        public int getAveragePing() {
            if (sampleCount == 0) return 0;
            int sum = 0;
            for (int i = 0; i < sampleCount; i++) {
                sum += pingSamples[i];
            }
            return sum / sampleCount;
        }
    }
}
