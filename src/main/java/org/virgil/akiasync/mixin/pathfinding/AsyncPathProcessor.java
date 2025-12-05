package org.virgil.akiasync.mixin.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.virgil.akiasync.mixin.bridge.Bridge;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步路径查找处理器
 * Async pathfinding processor
 * 
 * 来源 / Source: Pufferfish
 * 版本 / Version: 8.0
 */
public class AsyncPathProcessor {
    
    private static AsyncPathProcessor instance;
    private ExecutorService executor;
    private final ConcurrentHashMap<Long, AsyncPath> pendingPaths = new ConcurrentHashMap<>();
    
    private volatile boolean enabled = false;
    private volatile int maxThreads = 4;
    private volatile long keepAliveSeconds = 60;
    private volatile int maxQueueSize = 1000;
    private volatile long timeoutMs = 50;
    
    private AsyncPathProcessor() {
    }
    
    public static synchronized AsyncPathProcessor getInstance() {
        if (instance == null) {
            instance = new AsyncPathProcessor();
        }
        return instance;
    }
    
    public void initialize(Bridge bridge) {
        if (bridge == null || !bridge.isAsyncPathfindingEnabled()) {
            enabled = false;
            shutdown();
            return;
        }
        
        enabled = true;
        maxThreads = bridge.getAsyncPathfindingMaxThreads();
        keepAliveSeconds = bridge.getAsyncPathfindingKeepAliveSeconds();
        maxQueueSize = bridge.getAsyncPathfindingMaxQueueSize();
        timeoutMs = bridge.getAsyncPathfindingTimeoutMs();
        
        if (executor == null || executor.isShutdown()) {
            executor = createExecutor();
        }
    }
    
    private ExecutorService createExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "AkiAsync-Pathfinding-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        };
        
        return new java.util.concurrent.ThreadPoolExecutor(
            maxThreads,
            maxThreads,
            keepAliveSeconds,
            TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(maxQueueSize),
            factory,
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    public AsyncPath submitPathfinding(
            PathFinder finder,
            Object region,
            Mob mob,
            Set<BlockPos> targets,
            float maxRange,
            int accuracy,
            float depth) {
        
        if (!enabled || executor == null || executor.isShutdown()) {
            return null;
        }
        
        // 检查队列是否已满
        if (((java.util.concurrent.ThreadPoolExecutor) executor).getQueue().size() >= maxQueueSize) {
            return null; // 队列满，返回 null 以使用同步路径查找
        }
        
        CompletableFuture<Path> future = CompletableFuture.supplyAsync(() -> {
            try {
                return invokeFindPath(finder, region, mob, targets, maxRange, accuracy, depth);
            } catch (Exception e) {
                return null;
            }
        }, executor);
        
        AsyncPath asyncPath = new AsyncPath(future, timeoutMs);
        long entityId = mob.getId();
        pendingPaths.put(entityId, asyncPath);
        
        // 清理过期的路径
        future.whenComplete((path, throwable) -> {
            pendingPaths.remove(entityId);
        });
        
        return asyncPath;
    }
    
    private Path invokeFindPath(
            PathFinder finder,
            Object region,
            Mob mob,
            Set<BlockPos> targets,
            float maxRange,
            int accuracy,
            float depth) {
        
        try {
            java.lang.reflect.Method findPathMethod = PathFinder.class.getMethod("findPath",
                Class.forName("net.minecraft.world.level.pathfinder.PathNavigationRegion"),
                Mob.class, Set.class, float.class, int.class, float.class);
            findPathMethod.setAccessible(true);
            return (Path) findPathMethod.invoke(finder, region, mob, targets, maxRange, accuracy, depth);
        } catch (Exception e) {
            return null;
        }
    }
    
    public Path getPath(AsyncPath asyncPath) {
        if (asyncPath == null) {
            return null;
        }
        
        if (asyncPath.isExpired()) {
            return null;
        }
        
        try {
            return asyncPath.getFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return null;
        }
    }
    
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        pendingPaths.clear();
    }
    
    public void updateConfiguration(Bridge bridge) {
        if (bridge == null) {
            return;
        }
        
        boolean wasEnabled = enabled;
        initialize(bridge);
        
        // 如果配置改变，重新创建执行器
        if (wasEnabled != enabled || 
            maxThreads != bridge.getAsyncPathfindingMaxThreads() ||
            keepAliveSeconds != bridge.getAsyncPathfindingKeepAliveSeconds() ||
            maxQueueSize != bridge.getAsyncPathfindingMaxQueueSize()) {
            shutdown();
            if (enabled) {
                executor = createExecutor();
            }
        }
    }
    
    /**
     * 刷新待处理的路径 - 每tick调用
     * Flush pending paths - called every tick
     */
    public static void flushPendingPaths() {
        if (instance == null) {
            return;
        }
        
        // 清理过期的待处理路径
        long currentTime = System.currentTimeMillis();
        instance.pendingPaths.entrySet().removeIf(entry -> {
            AsyncPath path = entry.getValue();
            return path == null || path.isExpired() || 
                   (path.getFuture() != null && path.getFuture().isDone());
        });
    }
}

