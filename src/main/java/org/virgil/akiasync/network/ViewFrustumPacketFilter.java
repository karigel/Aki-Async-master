package org.virgil.akiasync.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视锥体数据包过滤器 / View Frustum Packet Filter
 * 过滤玩家视野范围外的数据包以减少带宽使用
 * Filters packets outside player's view frustum to reduce bandwidth usage
 */
public class ViewFrustumPacketFilter {

    private final Map<UUID, PlayerViewState> playerViews = new ConcurrentHashMap<>();
    
    private boolean enabled = false;
    private boolean filterEntities = true;
    private boolean filterBlocks = false;
    private boolean filterParticles = true;
    private boolean debugEnabled = false;

    // 视锥体参数 / View frustum parameters
    private static final double FOV_HORIZONTAL = 110.0; // 水平视角 / Horizontal FOV
    private static final double FOV_VERTICAL = 90.0;    // 垂直视角 / Vertical FOV
    private static final double MAX_RENDER_DISTANCE = 256.0; // 最大渲染距离 / Max render distance

    public ViewFrustumPacketFilter() {
    }

    /**
     * 更新玩家视角 / Update player view
     */
    public void updatePlayerView(ServerPlayer player) {
        if (!enabled || player == null) return;
        
        UUID playerId = player.getUUID();
        PlayerViewState viewState = playerViews.computeIfAbsent(playerId, 
            k -> new PlayerViewState());
        
        viewState.update(
            player.position(),
            player.getYRot(),
            player.getXRot()
        );
    }

    /**
     * 检查位置是否在玩家视锥体内 / Check if position is within player's view frustum
     */
    public boolean isInViewFrustum(UUID playerId, double x, double y, double z) {
        if (!enabled) return true;
        
        PlayerViewState viewState = playerViews.get(playerId);
        if (viewState == null) return true;
        
        return viewState.isInFrustum(x, y, z);
    }

    /**
     * 检查是否应该过滤此数据包 / Check if this packet should be filtered
     */
    public boolean shouldFilter(UUID playerId, double x, double y, double z, PacketType type) {
        if (!enabled) return false;
        
        // 检查是否启用了对应类型的过滤 / Check if filtering is enabled for this type
        switch (type) {
            case ENTITY:
                if (!filterEntities) return false;
                break;
            case BLOCK:
                if (!filterBlocks) return false;
                break;
            case PARTICLE:
                if (!filterParticles) return false;
                break;
            default:
                return false;
        }
        
        return !isInViewFrustum(playerId, x, y, z);
    }

    public void removePlayer(UUID playerId) {
        playerViews.remove(playerId);
    }

    public void clear() {
        playerViews.clear();
    }

    // Getters and Setters
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setFilterEntities(boolean filterEntities) {
        this.filterEntities = filterEntities;
    }

    public void setFilterBlocks(boolean filterBlocks) {
        this.filterBlocks = filterBlocks;
    }

    public void setFilterParticles(boolean filterParticles) {
        this.filterParticles = filterParticles;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public enum PacketType {
        ENTITY,
        BLOCK,
        PARTICLE,
        OTHER
    }

    private static class PlayerViewState {
        private Vec3 position = Vec3.ZERO;
        private float yaw = 0;
        private float pitch = 0;
        private long lastUpdate = 0;

        public void update(Vec3 pos, float yaw, float pitch) {
            this.position = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.lastUpdate = System.currentTimeMillis();
        }

        public boolean isInFrustum(double x, double y, double z) {
            // 计算到目标点的向量 / Calculate vector to target point
            double dx = x - position.x;
            double dy = y - position.y;
            double dz = z - position.z;
            
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            
            // 距离检查 / Distance check
            if (distance > MAX_RENDER_DISTANCE) {
                return false;
            }
            
            // 非常近的物体始终可见 / Very close objects are always visible
            if (distance < 5.0) {
                return true;
            }
            
            // 计算水平角度 / Calculate horizontal angle
            double horizontalAngle = Math.toDegrees(Math.atan2(dz, dx)) - yaw + 90;
            while (horizontalAngle > 180) horizontalAngle -= 360;
            while (horizontalAngle < -180) horizontalAngle += 360;
            
            // 计算垂直角度 / Calculate vertical angle
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            double verticalAngle = Math.toDegrees(Math.atan2(dy, horizontalDist)) + pitch;
            
            // 检查是否在视锥体内 / Check if within view frustum
            return Math.abs(horizontalAngle) <= FOV_HORIZONTAL / 2 &&
                   Math.abs(verticalAngle) <= FOV_VERTICAL / 2;
        }
    }
}
