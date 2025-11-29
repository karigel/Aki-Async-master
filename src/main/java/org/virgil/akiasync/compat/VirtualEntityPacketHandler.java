package org.virgil.akiasync.compat;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.virgil.akiasync.config.ConfigManager;
import org.virgil.akiasync.network.PacketPriority;

import java.lang.reflect.Field;
import java.util.logging.Logger;

/**
 * 虚拟实体数据包处理器 / Virtual Entity Packet Handler
 * 处理与虚拟实体相关的数据包
 */
public class VirtualEntityPacketHandler {

    private final PluginDetectorRegistry registry;
    private final ConfigManager configManager;
    private final Logger logger;
    private final boolean debugEnabled;

    private static Field addEntityPacketIdField;
    private static Field setEntityDataPacketIdField;
    private static Field teleportEntityPacketIdField;
    private static Field moveEntityPacketIdField;
    private static Field rotateHeadPacketIdField;
    private static Field setEntityMotionPacketIdField;

    static {
        try {
            addEntityPacketIdField = ClientboundAddEntityPacket.class.getDeclaredField("id");
            addEntityPacketIdField.setAccessible(true);

            setEntityDataPacketIdField = ClientboundSetEntityDataPacket.class.getDeclaredField("id");
            setEntityDataPacketIdField.setAccessible(true);

            teleportEntityPacketIdField = ClientboundTeleportEntityPacket.class.getDeclaredField("id");
            teleportEntityPacketIdField.setAccessible(true);

            moveEntityPacketIdField = ClientboundMoveEntityPacket.class.getDeclaredField("entityId");
            moveEntityPacketIdField.setAccessible(true);

            rotateHeadPacketIdField = ClientboundRotateHeadPacket.class.getDeclaredField("entityId");
            rotateHeadPacketIdField.setAccessible(true);

            setEntityMotionPacketIdField = ClientboundSetEntityMotionPacket.class.getDeclaredField("id");
            setEntityMotionPacketIdField.setAccessible(true);
        } catch (Exception e) {
            // Ignore
        }
    }

    public VirtualEntityPacketHandler(PluginDetectorRegistry registry, ConfigManager configManager, Logger logger) {
        this.registry = registry;
        this.configManager = configManager;
        this.logger = logger;

        boolean debug = false;
        try {
            debug = configManager.isVirtualEntityDebugEnabled();
        } catch (NoSuchMethodError e) {
            debug = org.virgil.akiasync.util.DebugLogger.isDebugEnabled();
        }
        this.debugEnabled = debug;
    }

    public boolean isVirtualEntityRelatedPacket(Packet<?> packet, ServerPlayer player) {
        if (packet == null || player == null) {
            return false;
        }

        try {
            Integer entityId = extractEntityId(packet);
            if (entityId == null) {
                return false;
            }

            ServerLevel level = (ServerLevel) player.level();
            if (level == null) {
                return false;
            }

            Entity entity = level.getEntity(entityId);
            if (entity == null) {
                return false;
            }

            org.bukkit.entity.Entity bukkitEntity = entity.getBukkitEntity();
            if (bukkitEntity == null) {
                return false;
            }

            return registry.isVirtualEntity(bukkitEntity);

        } catch (Exception e) {
            if (debugEnabled) {
                logger.warning("[VirtualEntity] Error checking packet: " + e.getMessage());
            }
            return false;
        }
    }

    public boolean shouldBypassQueue(Packet<?> packet, ServerPlayer player) {
        boolean bypassEnabled = true;
        try {
            bypassEnabled = configManager.isVirtualEntityBypassPacketQueue();
        } catch (NoSuchMethodError e) {
            // Use default
        }

        if (!bypassEnabled) {
            return false;
        }

        return isVirtualEntityRelatedPacket(packet, player);
    }

    public PacketPriority getVirtualEntityPacketPriority(Packet<?> packet) {
        return PacketPriority.CRITICAL;
    }

    public String getPacketSource(Packet<?> packet, ServerPlayer player) {
        if (packet == null || player == null) {
            return null;
        }

        try {
            Integer entityId = extractEntityId(packet);
            if (entityId == null) {
                return null;
            }

            ServerLevel level = (ServerLevel) player.level();
            if (level == null) {
                return null;
            }

            Entity entity = level.getEntity(entityId);
            if (entity == null) {
                return null;
            }

            org.bukkit.entity.Entity bukkitEntity = entity.getBukkitEntity();
            if (bukkitEntity == null) {
                return null;
            }

            return registry.getEntitySource(bukkitEntity);

        } catch (Exception e) {
            return null;
        }
    }

    private Integer extractEntityId(Packet<?> packet) {
        try {
            if (packet instanceof ClientboundAddEntityPacket && addEntityPacketIdField != null) {
                return (Integer) addEntityPacketIdField.get(packet);
            }

            if (packet instanceof ClientboundSetEntityDataPacket && setEntityDataPacketIdField != null) {
                return (Integer) setEntityDataPacketIdField.get(packet);
            }

            if (packet instanceof ClientboundTeleportEntityPacket && teleportEntityPacketIdField != null) {
                return (Integer) teleportEntityPacketIdField.get(packet);
            }

            if (packet instanceof ClientboundMoveEntityPacket && moveEntityPacketIdField != null) {
                return (Integer) moveEntityPacketIdField.get(packet);
            }

            if (packet instanceof ClientboundRotateHeadPacket && rotateHeadPacketIdField != null) {
                return (Integer) rotateHeadPacketIdField.get(packet);
            }

            if (packet instanceof ClientboundSetEntityMotionPacket && setEntityMotionPacketIdField != null) {
                return (Integer) setEntityMotionPacketIdField.get(packet);
            }

        } catch (Exception e) {
            // Ignore
        }

        return null;
    }
}
