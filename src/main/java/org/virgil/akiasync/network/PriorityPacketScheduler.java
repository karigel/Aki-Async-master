package org.virgil.akiasync.network;

import net.minecraft.network.protocol.Packet;
import org.virgil.akiasync.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 优先级数据包调度器 / Priority Packet Scheduler
 * 管理所有玩家的数据包队列
 * Manages packet queues for all players
 */
public class PriorityPacketScheduler {

    private final ConfigManager configManager;
    private final Map<UUID, PriorityPacketQueue> playerQueues = new ConcurrentHashMap<>();
    private Logger logger;
    private boolean debugEnabled;

    public PriorityPacketScheduler(ConfigManager configManager) {
        this.configManager = configManager;
        this.debugEnabled = configManager.isDebugLoggingEnabled();
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * 为玩家创建队列 / Create queue for player
     */
    public PriorityPacketQueue createQueue(UUID playerId, String playerName) {
        PriorityPacketQueue queue = new PriorityPacketQueue(
            playerName, 
            logger != null ? logger : Logger.getLogger("AkiAsync"), 
            debugEnabled
        );
        playerQueues.put(playerId, queue);
        return queue;
    }

    /**
     * 获取玩家队列 / Get player queue
     */
    public PriorityPacketQueue getQueue(UUID playerId) {
        return playerQueues.get(playerId);
    }

    /**
     * 向玩家队列添加数据包 / Add packet to player queue
     */
    public boolean schedulePacket(UUID playerId, Packet<?> packet) {
        PriorityPacketQueue queue = playerQueues.get(playerId);
        if (queue == null) {
            return false;
        }
        
        PacketPriority priority = PacketClassifier.classify(packet);
        return queue.offer(packet, priority);
    }

    /**
     * 向玩家队列添加带优先级的数据包 / Add packet with priority to player queue
     */
    public boolean schedulePacket(UUID playerId, Packet<?> packet, PacketPriority priority) {
        PriorityPacketQueue queue = playerQueues.get(playerId);
        if (queue == null) {
            return false;
        }
        return queue.offer(packet, priority);
    }

    /**
     * 清除玩家队列 / Clear player queue
     */
    public void clearQueue(UUID playerId) {
        PriorityPacketQueue queue = playerQueues.remove(playerId);
        if (queue != null) {
            queue.clear();
        }
    }

    /**
     * 获取所有玩家队列 / Get all player queues
     */
    public Map<UUID, PriorityPacketQueue> getAllQueues() {
        return playerQueues;
    }

    /**
     * 获取总队列大小 / Get total queue size
     */
    public int getTotalQueueSize() {
        return playerQueues.values().stream()
            .mapToInt(PriorityPacketQueue::size)
            .sum();
    }

    /**
     * 获取统计信息 / Get statistics
     */
    public String getStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("PriorityPacketScheduler Statistics:\n");
        sb.append("  Total Players: ").append(playerQueues.size()).append("\n");
        sb.append("  Total Queued Packets: ").append(getTotalQueueSize()).append("\n");
        
        for (Map.Entry<UUID, PriorityPacketQueue> entry : playerQueues.entrySet()) {
            sb.append("  ").append(entry.getValue().getStatistics()).append("\n");
        }
        
        return sb.toString();
    }

    /**
     * 清除所有队列 / Clear all queues
     */
    public void clear() {
        playerQueues.values().forEach(PriorityPacketQueue::clear);
        playerQueues.clear();
    }
}
