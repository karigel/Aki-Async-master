package org.kari.kariClaims.models;

import java.util.UUID;

/**
 * 权限数据模型
 */
public class Permission {
    private final int claimId;
    private final UUID playerId;
    private final PermissionType type;
    private final boolean allowed;

    public Permission(int claimId, UUID playerId, PermissionType type, boolean allowed) {
        this.claimId = claimId;
        this.playerId = playerId;
        this.type = type;
        this.allowed = allowed;
    }

    public int getClaimId() {
        return claimId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public PermissionType getType() {
        return type;
    }

    public boolean isAllowed() {
        return allowed;
    }

    /**
     * 权限类型枚举
     */
    public enum PermissionType {
        BUILD("建造"),
        BREAK("破坏"),
        INTERACT("交互"),
        PVP("PVP"),
        MOB_DAMAGE("生物伤害"),
        ANIMAL_DAMAGE("动物伤害"),
        EXPLOSION("爆炸"),
        FIRE("火焰"),
        MOB_SPAWN("生物生成"),
        REDSTONE("红石"),
        DOOR("门"),
        CHEST("箱子"),
        FURNACE("熔炉"),
        ANVIL("铁砧"),
        ENCHANT("附魔"),
        BREW("酿造"),
        BEACON("信标"),
        HOPPER("漏斗"),
        DROPPER("投掷器"),
        DISPENSER("发射器");

        private final String displayName;

        PermissionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}

