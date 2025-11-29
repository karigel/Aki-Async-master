package org.virgil.akiasync.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;

/**
 * 数据包分类器 / Packet Classifier
 * 根据数据包类型确定其优先级
 * Determines packet priority based on packet type
 */
public class PacketClassifier {

    /**
     * 分类数据包优先级 / Classify packet priority
     * @param packet 数据包 / The packet
     * @return 优先级 / Priority
     */
    public static PacketPriority classify(Packet<?> packet) {
        if (packet == null) {
            return PacketPriority.NORMAL;
        }

        // 关键数据包 - 玩家状态和实体位置 / Critical packets - player state and entity position
        if (packet instanceof ClientboundContainerSetContentPacket ||
            packet instanceof ClientboundContainerSetSlotPacket ||
            packet instanceof ClientboundSetEntityDataPacket ||
            packet instanceof ClientboundSetEquipmentPacket ||
            packet instanceof ClientboundSetHealthPacket ||
            packet instanceof ClientboundSetExperiencePacket ||
            packet instanceof ClientboundPlayerAbilitiesPacket ||
            packet instanceof ClientboundPlayerPositionPacket ||
            packet instanceof ClientboundTeleportEntityPacket ||
            packet instanceof ClientboundMoveEntityPacket ||
            packet instanceof ClientboundRotateHeadPacket) {
            return PacketPriority.CRITICAL;
        }

        // 高优先级 - 实体创建/移除和方块更新 / High priority - entity create/remove and block updates
        if (packet instanceof ClientboundAddEntityPacket ||
            packet instanceof ClientboundRemoveEntitiesPacket ||
            packet instanceof ClientboundSetEntityMotionPacket ||
            packet instanceof ClientboundBlockUpdatePacket ||
            packet instanceof ClientboundSectionBlocksUpdatePacket ||
            packet instanceof ClientboundBlockEntityDataPacket ||
            packet instanceof ClientboundExplodePacket ||
            packet instanceof ClientboundDamageEventPacket ||
            packet instanceof ClientboundHurtAnimationPacket) {
            return PacketPriority.HIGH;
        }

        // 普通优先级 - 区块和光照数据 / Normal priority - chunk and light data
        if (packet instanceof ClientboundLevelChunkWithLightPacket ||
            packet instanceof ClientboundLightUpdatePacket ||
            packet instanceof ClientboundForgetLevelChunkPacket ||
            packet instanceof ClientboundMapItemDataPacket ||
            packet instanceof ClientboundSetChunkCacheRadiusPacket ||
            packet instanceof ClientboundSetChunkCacheCenterPacket) {
            return PacketPriority.NORMAL;
        }

        // 低优先级 - 粒子、声音和UI / Low priority - particles, sounds and UI
        if (packet instanceof ClientboundLevelParticlesPacket ||
            packet instanceof ClientboundSoundPacket ||
            packet instanceof ClientboundSoundEntityPacket ||
            packet instanceof ClientboundSetSubtitleTextPacket ||
            packet instanceof ClientboundSetTitleTextPacket ||
            packet instanceof ClientboundSetActionBarTextPacket ||
            packet instanceof ClientboundBossEventPacket ||
            packet instanceof ClientboundSetScorePacket ||
            packet instanceof ClientboundSetDisplayObjectivePacket ||
            packet instanceof ClientboundSetPlayerTeamPacket ||
            packet instanceof ClientboundTabListPacket) {
            return PacketPriority.LOW;
        }

        return PacketPriority.NORMAL;
    }

    /**
     * 判断是否为区块数据包 / Check if packet is a chunk packet
     */
    public static boolean isChunkPacket(Packet<?> packet) {
        return packet instanceof ClientboundLevelChunkWithLightPacket ||
               packet instanceof ClientboundForgetLevelChunkPacket ||
               packet instanceof ClientboundLightUpdatePacket;
    }

    /**
     * 判断是否为实体数据包 / Check if packet is an entity packet
     */
    public static boolean isEntityPacket(Packet<?> packet) {
        return packet instanceof ClientboundAddEntityPacket ||
               packet instanceof ClientboundRemoveEntitiesPacket ||
               packet instanceof ClientboundSetEntityDataPacket ||
               packet instanceof ClientboundSetEntityMotionPacket ||
               packet instanceof ClientboundTeleportEntityPacket ||
               packet instanceof ClientboundMoveEntityPacket ||
               packet instanceof ClientboundRotateHeadPacket;
    }

    /**
     * 判断是否为玩家动作数据包 / Check if packet is a player action packet
     */
    public static boolean isPlayerActionPacket(Packet<?> packet) {
        return packet instanceof ClientboundContainerSetContentPacket ||
               packet instanceof ClientboundContainerSetSlotPacket ||
               packet instanceof ClientboundSetHealthPacket ||
               packet instanceof ClientboundSetExperiencePacket ||
               packet instanceof ClientboundPlayerAbilitiesPacket ||
               packet instanceof ClientboundPlayerPositionPacket;
    }

    /**
     * 判断是否为视觉效果数据包 / Check if packet is a visual effect packet
     */
    public static boolean isVisualEffectPacket(Packet<?> packet) {
        return packet instanceof ClientboundLevelParticlesPacket ||
               packet instanceof ClientboundSoundPacket ||
               packet instanceof ClientboundSoundEntityPacket;
    }
}
