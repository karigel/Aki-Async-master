package org.virgil.akiasync.mixin.pathfinding;

import net.minecraft.world.level.pathfinder.Path;
import java.util.concurrent.CompletableFuture;

/**
 * 异步路径查找结果包装类
 * Wrapper class for async pathfinding results
 * 
 * 来源 / Source: Pufferfish
 * 版本 / Version: 8.0
 */
public class AsyncPath {
    
    private final CompletableFuture<Path> future;
    private final long submitTime;
    private final long timeoutMs;
    
    public AsyncPath(CompletableFuture<Path> future, long timeoutMs) {
        this.future = future;
        this.submitTime = System.currentTimeMillis();
        this.timeoutMs = timeoutMs;
    }
    
    public CompletableFuture<Path> getFuture() {
        return future;
    }
    
    public long getSubmitTime() {
        return submitTime;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() - submitTime > timeoutMs;
    }
    
    public Path getNow() {
        if (future.isDone()) {
            try {
                return future.get();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}

