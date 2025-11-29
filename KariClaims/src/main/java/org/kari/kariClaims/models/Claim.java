package org.kari.kariClaims.models;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * 领地数据模型
 */
public class Claim {
    private final int id;
    private final UUID owner;
    private final String world;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final long createdAt;
    private String name;
    private String description;
    private boolean pvpEnabled;
    private boolean mobSpawning;
    private boolean fireSpread;
    private boolean explosion;
    private boolean enterMessage;
    private boolean exitMessage;
    private String enterMessageText;
    private String exitMessageText;

    public Claim(int id, UUID owner, String world, int minX, int minZ, int maxX, int maxZ, long createdAt) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.createdAt = createdAt;
        this.name = "领地 #" + id;
        this.description = "";
        this.pvpEnabled = false;
        this.mobSpawning = true;
        this.fireSpread = false;
        this.explosion = false;
        this.enterMessage = false;
        this.exitMessage = false;
        this.enterMessageText = "";
        this.exitMessageText = "";
    }

    public int getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getWorld() {
        return world;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public boolean isMobSpawning() {
        return mobSpawning;
    }

    public void setMobSpawning(boolean mobSpawning) {
        this.mobSpawning = mobSpawning;
    }

    public boolean isFireSpread() {
        return fireSpread;
    }

    public void setFireSpread(boolean fireSpread) {
        this.fireSpread = fireSpread;
    }

    public boolean isExplosion() {
        return explosion;
    }

    public void setExplosion(boolean explosion) {
        this.explosion = explosion;
    }

    public boolean isEnterMessage() {
        return enterMessage;
    }

    public void setEnterMessage(boolean enterMessage) {
        this.enterMessage = enterMessage;
    }

    public boolean isExitMessage() {
        return exitMessage;
    }

    public void setExitMessage(boolean exitMessage) {
        this.exitMessage = exitMessage;
    }

    public String getEnterMessageText() {
        return enterMessageText;
    }

    public void setEnterMessageText(String enterMessageText) {
        this.enterMessageText = enterMessageText;
    }

    public String getExitMessageText() {
        return exitMessageText;
    }

    public void setExitMessageText(String exitMessageText) {
        this.exitMessageText = exitMessageText;
    }

    /**
     * 检查位置是否在领地内
     */
    public boolean contains(Location location) {
        if (!location.getWorld().getName().equals(world)) {
            return false;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * 检查位置是否在领地内（通过世界名称和坐标）
     */
    public boolean contains(World world, int x, int z) {
        if (!world.getName().equals(this.world)) {
            return false;
        }
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * 获取领地面积
     */
    public int getArea() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    /**
     * 检查两个领地是否重叠
     */
    public boolean overlaps(Claim other) {
        if (!world.equals(other.world)) {
            return false;
        }
        return !(maxX < other.minX || minX > other.maxX || maxZ < other.minZ || minZ > other.maxZ);
    }
}

