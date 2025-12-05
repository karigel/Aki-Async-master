package org.kari.kariClaims.models;

import java.util.UUID;

/**
 * 领地成员数据模型
 */
public class ClaimMember {
    private final int claimId;
    private final UUID playerId;
    private final MemberRole role;
    private long joinedAt;

    public ClaimMember(int claimId, UUID playerId, MemberRole role, long joinedAt) {
        this.claimId = claimId;
        this.playerId = playerId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public int getClaimId() {
        return claimId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public MemberRole getRole() {
        return role;
    }

    public long getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(long joinedAt) {
        this.joinedAt = joinedAt;
    }

    /**
     * 成员角色枚举
     */
    public enum MemberRole {
        OWNER(0, "所有者"),
        TRUSTED(1, "信任成员"),
        MEMBER(2, "成员"),
        VISITOR(3, "访客");

        private final int id;
        private final String displayName;

        MemberRole(int id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public int getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static MemberRole fromId(int id) {
            for (MemberRole role : values()) {
                if (role.id == id) {
                    return role;
                }
            }
            return VISITOR;
        }
    }
}

