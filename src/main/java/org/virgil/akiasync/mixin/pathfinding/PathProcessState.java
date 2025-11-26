package org.virgil.akiasync.mixin.pathfinding;

/**
 * 路径处理状态枚举
 * Path processing state enumeration
 * 
 * 来源 / Source: Pufferfish
 * 版本 / Version: 8.0
 */
public enum PathProcessState {
    /**
     * 等待处理 / Waiting for processing
     */
    PENDING,
    
    /**
     * 正在处理 / Processing
     */
    PROCESSING,
    
    /**
     * 已完成 / Completed
     */
    COMPLETED,
    
    /**
     * 已超时 / Timed out
     */
    TIMED_OUT,
    
    /**
     * 已取消 / Cancelled
     */
    CANCELLED,
    
    /**
     * 失败 / Failed
     */
    FAILED
}

