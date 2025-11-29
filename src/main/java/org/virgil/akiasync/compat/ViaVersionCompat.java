package org.virgil.akiasync.compat;

import org.bukkit.entity.Player;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * ViaVersion 兼容层 / ViaVersion Compatibility Layer
 * 提供协议版本检测和连接状态检查
 * Provides protocol version detection and connection state checking
 */
public class ViaVersionCompat {

    private static final Logger LOGGER = Logger.getLogger("AkiAsync-ViaCompat");
    private static boolean viaVersionAvailable = false;
    private static boolean initialized = false;
    private static ClassLoader viaClassLoader = null;

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            Plugin viaPlugin = Bukkit.getPluginManager().getPlugin("ViaVersion");
            if (viaPlugin != null && viaPlugin.isEnabled()) {
                viaClassLoader = viaPlugin.getClass().getClassLoader();
                Class.forName("com.viaversion.viaversion.api.Via", true, viaClassLoader);
                viaVersionAvailable = true;
                LOGGER.info("[ViaCompat] ViaVersion detected, compatibility layer enabled");
            } else {
                viaVersionAvailable = false;
                LOGGER.info("[ViaCompat] ViaVersion not found, compatibility layer disabled");
            }
        } catch (ClassNotFoundException e) {
            viaVersionAvailable = false;
            LOGGER.info("[ViaCompat] ViaVersion not found, compatibility layer disabled");
        }
    }

    public static boolean isViaVersionAvailable() {
        return viaVersionAvailable;
    }

    public static int getPlayerProtocolVersion(Player player) {
        if (!viaVersionAvailable) {
            return -1;
        }

        try {
            // 使用反射调用ViaVersion API / Use reflection to call ViaVersion API
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via", true, viaClassLoader);
            Object api = viaClass.getMethod("getAPI").invoke(null);
            Object connection = api.getClass().getMethod("getConnection", UUID.class)
                .invoke(api, player.getUniqueId());
            if (connection != null) {
                Object protocolInfo = connection.getClass().getMethod("getProtocolInfo").invoke(connection);
                Object protocolVersion = protocolInfo.getClass().getMethod("protocolVersion").invoke(protocolInfo);
                return (int) protocolVersion.getClass().getMethod("getVersion").invoke(protocolVersion);
            }
        } catch (Exception e) {
            LOGGER.warning("[ViaCompat] Failed to get protocol version for player " + player.getName() + ": " + e.getMessage());
        }

        return -1;
    }

    public static boolean isPlayerUsingVia(UUID playerId) {
        if (!viaVersionAvailable) {
            return false;
        }

        try {
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via", true, viaClassLoader);
            Object api = viaClass.getMethod("getAPI").invoke(null);
            Object connection = api.getClass().getMethod("getConnection", UUID.class)
                .invoke(api, playerId);
            return connection != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPlayerUsingVia(Player player) {
        return isPlayerUsingVia(player.getUniqueId());
    }

    public static boolean isConnectionInPlayState(UUID playerId) {
        if (!viaVersionAvailable) {
            return true;
        }

        try {
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via", true, viaClassLoader);
            Object api = viaClass.getMethod("getAPI").invoke(null);
            Object connection = api.getClass().getMethod("getConnection", UUID.class)
                .invoke(api, playerId);
            if (connection != null) {
                Object protocolInfo = connection.getClass().getMethod("getProtocolInfo").invoke(connection);
                Class<?> directionClass = Class.forName("com.viaversion.viaversion.api.protocol.packet.Direction", true, viaClassLoader);
                Object clientbound = Enum.valueOf((Class<Enum>) directionClass, "CLIENTBOUND");
                Object state = protocolInfo.getClass().getMethod("getState", directionClass)
                    .invoke(protocolInfo, clientbound);
                Class<?> stateClass = Class.forName("com.viaversion.viaversion.api.protocol.packet.State", true, viaClassLoader);
                Object playState = Enum.valueOf((Class<Enum>) stateClass, "PLAY");
                return state == playState;
            }
        } catch (Exception e) {
            LOGGER.warning("[ViaCompat] Failed to check connection state for player " + playerId + ": " + e.getMessage());
        }

        return true;
    }

    public static boolean isConnectionInPlayState(Player player) {
        return isConnectionInPlayState(player.getUniqueId());
    }

    public static int getServerProtocolVersion() {
        if (!viaVersionAvailable) {
            return -1;
        }

        try {
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via", true, viaClassLoader);
            Object api = viaClass.getMethod("getAPI").invoke(null);
            Object serverVersion = api.getClass().getMethod("getServerVersion").invoke(api);
            Object lowestVersion = serverVersion.getClass().getMethod("lowestSupportedProtocolVersion").invoke(serverVersion);
            return (int) lowestVersion.getClass().getMethod("getVersion").invoke(lowestVersion);
        } catch (Exception e) {
            LOGGER.warning("[ViaCompat] Failed to get server protocol version: " + e.getMessage());
            return -1;
        }
    }

    public static boolean isProtocolVersionDifferent(Player player) {
        if (!viaVersionAvailable) {
            return false;
        }

        int playerVersion = getPlayerProtocolVersion(player);
        int serverVersion = getServerProtocolVersion();

        if (playerVersion == -1 || serverVersion == -1) {
            return false;
        }

        return playerVersion != serverVersion;
    }

    public static String getProtocolVersionName(int protocolVersion) {
        return switch (protocolVersion) {
            case 769 -> "1.21.4";
            case 770 -> "1.21.5";
            case 771 -> "1.21.6";
            case 767 -> "1.21.1";
            case 766 -> "1.21";
            default -> "Unknown (" + protocolVersion + ")";
        };
    }

    public static void logPlayerProtocolInfo(Player player) {
        if (!viaVersionAvailable) {
            return;
        }

        int playerVersion = getPlayerProtocolVersion(player);
        int serverVersion = getServerProtocolVersion();
        boolean usingVia = isPlayerUsingVia(player);
        boolean inPlayState = isConnectionInPlayState(player);

        LOGGER.info(String.format(
            "[ViaCompat] Player: %s, Protocol: %s, Server: %s, UsingVia: %s, PlayState: %s",
            player.getName(),
            getProtocolVersionName(playerVersion),
            getProtocolVersionName(serverVersion),
            usingVia,
            inPlayState
        ));
    }
}
