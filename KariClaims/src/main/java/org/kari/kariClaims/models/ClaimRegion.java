package org.kari.kariClaims.models;

import org.bukkit.Location;

import java.util.*;

/**
 * 领地区域（由相邻区块组成）
 */
public class ClaimRegion {
    private final int id;
    private final UUID owner;
    private final String world;
    private final Set<ChunkClaim> chunks;
    private final Map<Integer, String> chunkNames; // chunkId -> name
    private final Map<Integer, Location> homes; // chunkId -> home location
    private final Map<Integer, Boolean> homePublic; // chunkId -> is public
    private String regionName;
    private Location defaultHome;
    private boolean locked;
    private long energyTime;
    private double economyBalance;
    private PowerCell powerCell;

    public ClaimRegion(int id, UUID owner, String world) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.chunks = new HashSet<>();
        this.chunkNames = new HashMap<>();
        this.homes = new HashMap<>();
        this.homePublic = new HashMap<>();
        this.regionName = "区域 #" + id;
        this.locked = false;
        this.energyTime = 0;
        this.economyBalance = 0;
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

    public Set<ChunkClaim> getChunks() {
        return Collections.unmodifiableSet(chunks);
    }

    public void addChunk(ChunkClaim chunk) {
        chunks.add(chunk);
    }

    public void removeChunk(ChunkClaim chunk) {
        chunks.remove(chunk);
        chunkNames.remove(getChunkId(chunk));
        homes.remove(getChunkId(chunk));
        homePublic.remove(getChunkId(chunk));
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getChunkName(ChunkClaim chunk) {
        return chunkNames.getOrDefault(getChunkId(chunk), chunk.getChunkName());
    }

    public void setChunkName(ChunkClaim chunk, String name) {
        chunkNames.put(getChunkId(chunk), name);
    }

    public Location getHome(ChunkClaim chunk) {
        return homes.getOrDefault(getChunkId(chunk), defaultHome);
    }

    public void setHome(ChunkClaim chunk, Location location) {
        homes.put(getChunkId(chunk), location);
        if (defaultHome == null) {
            defaultHome = location;
        }
    }

    public boolean isHomePublic(ChunkClaim chunk) {
        return homePublic.getOrDefault(getChunkId(chunk), false);
    }

    public void setHomePublic(ChunkClaim chunk, boolean isPublic) {
        homePublic.put(getChunkId(chunk), isPublic);
    }

    public Location getDefaultHome() {
        return defaultHome;
    }

    public void setDefaultHome(Location defaultHome) {
        this.defaultHome = defaultHome;
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

    public PowerCell getPowerCell() {
        return powerCell;
    }

    public void setPowerCell(PowerCell powerCell) {
        this.powerCell = powerCell;
    }

    /**
     * 检查位置是否在此区域内
     */
    public boolean contains(Location location) {
        if (!location.getWorld().getName().equals(world)) {
            return false;
        }
        for (ChunkClaim chunk : chunks) {
            if (chunk.contains(location)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取区块ID（用于Map键）
     */
    private int getChunkId(ChunkClaim chunk) {
        return chunk.getChunkX() * 10000 + chunk.getChunkZ();
    }

    /**
     * 查找位置所在的区块
     */
    public Optional<ChunkClaim> findChunk(Location location) {
        for (ChunkClaim chunk : chunks) {
            if (chunk.contains(location)) {
                return Optional.of(chunk);
            }
        }
        return Optional.empty();
    }
}

