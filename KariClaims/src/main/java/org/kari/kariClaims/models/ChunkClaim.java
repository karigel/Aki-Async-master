package org.kari.kariClaims.models;

import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.UUID;

/**
 * 区块领地数据模型
 */
public class ChunkClaim {
    private final int id;
    private final UUID owner;
    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final long claimedAt;
    private int regionId;
    private String chunkName;
    private Location homeLocation;
    private boolean homePublic;
    private boolean locked;
    private long energyTime; // 物品能源时间（从箱子中的物品计算）
    private double economyBalance; // 金钱余额
    private long initialTime; // 初始保护时间（10分钟）
    
    // 领地设置
    private boolean pvpEnabled = false;
    private boolean mobSpawning = true;
    private boolean fireSpread = false;
    private boolean explosion = false;
    private boolean leafDecay = true;
    private boolean entityDrop = true;
    private boolean waterFlow = true;
    private boolean externalFluidInflow = true;
    private boolean fly = false;
    private boolean mobGriefing = false;
    private boolean tnt = false;
    
    // 权限设置 (位掩码)
    // 1: 破坏方块, 2: 放置方块, 4: 交互, 8: 交易, 16: 使用门, 32: 攻击生物, 64: 使用红石
    private int visitorPermissions = 0; // 默认访客无权限
    private int memberPermissions = 127; // 默认成员拥有所有权限
    
    public static final int PERM_BLOCK_BREAK = 1;
    public static final int PERM_BLOCK_PLACE = 2;
    public static final int PERM_INTERACT = 4;
    public static final int PERM_TRADE = 8;
    public static final int PERM_USE_DOORS = 16;
    public static final int PERM_ATTACK_MOBS = 32;
    public static final int PERM_USE_REDSTONE = 64;

    public ChunkClaim(int id, UUID owner, String world, int chunkX, int chunkZ, long claimedAt) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.claimedAt = claimedAt;
        this.regionId = 0;
        this.chunkName = "区块 " + chunkX + "," + chunkZ;
        this.homePublic = false;
        this.locked = false;
        this.energyTime = 0;
        this.economyBalance = 0;
        this.initialTime = 0;
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

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public int getRegionId() {
        return regionId;
    }

    public void setRegionId(int regionId) {
        this.regionId = regionId;
    }

    public long getClaimedAt() {
        return claimedAt;
    }

    public String getChunkName() {
        return chunkName;
    }

    public void setChunkName(String chunkName) {
        this.chunkName = chunkName;
    }

    public Location getHomeLocation() {
        return homeLocation;
    }

    public void setHomeLocation(Location homeLocation) {
        this.homeLocation = homeLocation;
    }

    public boolean isHomePublic() {
        return homePublic;
    }

    public void setHomePublic(boolean homePublic) {
        this.homePublic = homePublic;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public long getEnergyTime() {
        return energyTime;
    }

    public void setEnergyTime(long energyTime) {
        this.energyTime = energyTime;
    }

    public double getEconomyBalance() {
        return economyBalance;
    }

    public void setEconomyBalance(double economyBalance) {
        this.economyBalance = economyBalance;
    }

    public long getInitialTime() {
        return initialTime;
    }

    public void setInitialTime(long initialTime) {
        this.initialTime = initialTime;
    }
    
    /**
     * 获取总剩余时间（秒）
     * 总时间 = 物品时间 + 金钱时间 + 初始时间
     */
    public long getTotalRemainingTime(double pricePerSecond) {
        long itemTime = energyTime;
        long economyTime = (long) (economyBalance / pricePerSecond);
        return itemTime + economyTime + initialTime;
    }

    /**
     * 检查位置是否在此区块内
     */
    public boolean contains(Location location) {
        if (!location.getWorld().getName().equals(world)) {
            return false;
        }
        Chunk chunk = location.getChunk();
        return chunk.getX() == chunkX && chunk.getZ() == chunkZ;
    }

    /**
     * 检查是否与另一个区块相邻
     */
    public boolean isAdjacent(ChunkClaim other) {
        if (!world.equals(other.world)) {
            return false;
        }
        int dx = Math.abs(chunkX - other.chunkX);
        int dz = Math.abs(chunkZ - other.chunkZ);
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }

    // Getter 和 Setter 方法
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

    public boolean isLeafDecay() {
        return leafDecay;
    }

    public void setLeafDecay(boolean leafDecay) {
        this.leafDecay = leafDecay;
    }

    public boolean isEntityDrop() {
        return entityDrop;
    }

    public void setEntityDrop(boolean entityDrop) {
        this.entityDrop = entityDrop;
    }

    public boolean isWaterFlow() {
        return waterFlow;
    }

    public void setWaterFlow(boolean waterFlow) {
        this.waterFlow = waterFlow;
    }

    public boolean isExternalFluidInflow() {
        return externalFluidInflow;
    }

    public void setExternalFluidInflow(boolean externalFluidInflow) {
        this.externalFluidInflow = externalFluidInflow;
    }

    public boolean isFly() {
        return fly;
    }

    public void setFly(boolean fly) {
        this.fly = fly;
    }

    public boolean isMobGriefing() {
        return mobGriefing;
    }

    public void setMobGriefing(boolean mobGriefing) {
        this.mobGriefing = mobGriefing;
    }

    public boolean isTnt() {
        return tnt;
    }

    public void setTnt(boolean tnt) {
        this.tnt = tnt;
    }

    public int getVisitorPermissions() {
        return visitorPermissions;
    }

    public void setVisitorPermissions(int visitorPermissions) {
        this.visitorPermissions = visitorPermissions;
    }

    public int getMemberPermissions() {
        return memberPermissions;
    }

    public void setMemberPermissions(int memberPermissions) {
        this.memberPermissions = memberPermissions;
    }
    
    public boolean hasVisitorPermission(int permission) {
        return (visitorPermissions & permission) != 0;
    }
    
    public boolean hasMemberPermission(int permission) {
        return (memberPermissions & permission) != 0;
    }
}

