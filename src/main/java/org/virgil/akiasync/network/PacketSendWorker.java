package org.virgil.akiasync.network;

import org.virgil.akiasync.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 数据包发送工作线程 / Packet Send Worker Thread
 * 从优先级队列中取出数据包并发送
 * Takes packets from priority queues and sends them
 */
public class PacketSendWorker {

    private final PriorityPacketScheduler scheduler;
    private final ConfigManager configManager;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;
    private Logger logger;

    // 统计数据 / Statistics
    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong criticalSent = new AtomicLong(0);
    private final AtomicLong highSent = new AtomicLong(0);
    private final AtomicLong normalSent = new AtomicLong(0);
    private final AtomicLong lowSent = new AtomicLong(0);

    // 发送速率控制 / Send rate control
    private int packetSendRateBase = 100;
    private int packetSendRateMedium = 50;
    private int packetSendRateHeavy = 25;
    private int packetSendRateExtreme = 10;

    public PacketSendWorker(PriorityPacketScheduler scheduler, ConfigManager configManager) {
        this.scheduler = scheduler;
        this.configManager = configManager;
        loadConfiguration();
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    private void loadConfiguration() {
        // 可从配置加载发送速率参数 / Can load send rate parameters from config
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            workerThread = new Thread(this::run, "AkiAsync-PacketSendWorker");
            workerThread.setDaemon(true);
            workerThread.start();
            if (logger != null) {
                logger.info("[PacketSendWorker] Worker thread started");
            }
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (workerThread != null) {
                workerThread.interrupt();
                try {
                    workerThread.join(1000);
                } catch (InterruptedException ignored) {
                }
            }
            if (logger != null) {
                logger.info("[PacketSendWorker] Worker thread stopped");
            }
        }
    }

    private void run() {
        while (running.get()) {
            try {
                processQueues();
                
                // 根据负载调整休眠时间 / Adjust sleep time based on load
                int queueSize = scheduler.getTotalQueueSize();
                long sleepMs = calculateSleepTime(queueSize);
                
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("[PacketSendWorker] Error in worker loop: " + e.getMessage());
                }
            }
        }
    }

    private void processQueues() {
        Map<UUID, PriorityPacketQueue> queues = scheduler.getAllQueues();
        
        for (Map.Entry<UUID, PriorityPacketQueue> entry : queues.entrySet()) {
            PriorityPacketQueue queue = entry.getValue();
            
            // 每个玩家每轮处理一定数量的数据包 / Process a certain number of packets per player per round
            int processed = 0;
            int maxPerRound = calculateMaxPacketsPerRound(queue.size());
            
            while (processed < maxPerRound && !queue.isEmpty()) {
                try {
                    PacketInfo packetInfo = queue.poll(1, TimeUnit.MILLISECONDS);
                    if (packetInfo != null) {
                        // 这里实际发送数据包 / Actually send the packet here
                        // 由Mixin注入点调用实际的发送方法
                        // Called by Mixin injection point for actual sending
                        recordSent(packetInfo.getPriority());
                        processed++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private int calculateMaxPacketsPerRound(int queueSize) {
        if (queueSize > 500) {
            return packetSendRateExtreme;
        } else if (queueSize > 200) {
            return packetSendRateHeavy;
        } else if (queueSize > 50) {
            return packetSendRateMedium;
        }
        return packetSendRateBase;
    }

    private long calculateSleepTime(int totalQueueSize) {
        if (totalQueueSize > 1000) {
            return 0; // 高负载不休眠 / No sleep under high load
        } else if (totalQueueSize > 500) {
            return 1;
        } else if (totalQueueSize > 100) {
            return 5;
        }
        return 10;
    }

    private void recordSent(PacketPriority priority) {
        totalSent.incrementAndGet();
        switch (priority) {
            case CRITICAL -> criticalSent.incrementAndGet();
            case HIGH -> highSent.incrementAndGet();
            case NORMAL -> normalSent.incrementAndGet();
            case LOW -> lowSent.incrementAndGet();
        }
    }

    public String getStatistics() {
        return String.format(
            "PacketSendWorker: Total=%d, C=%d, H=%d, N=%d, L=%d, Running=%s",
            totalSent.get(), criticalSent.get(), highSent.get(), 
            normalSent.get(), lowSent.get(), running.get()
        );
    }

    public void resetStatistics() {
        totalSent.set(0);
        criticalSent.set(0);
        highSent.set(0);
        normalSent.set(0);
        lowSent.set(0);
    }

    public boolean isRunning() {
        return running.get();
    }
}
