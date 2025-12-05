package org.kari.kariClaims.models;

import org.bukkit.Location;

/**
 * 能量电池数据模型
 */
public class PowerCell {
    private final int claimId;
    private final Location location;
    private long energyTime; // 剩余时间（秒）
    private double economyBalance; // 经济余额
    private final long initialTime; // 初始保护时间（秒）
    private final double pricePerSecond; // 经济燃料每秒价格
    private long lastUpdate;

    public PowerCell(int claimId, Location location, long energyTime, double economyBalance, long initialTime, double pricePerSecond) {
        this.claimId = claimId;
        this.location = location;
        this.energyTime = energyTime;
        this.economyBalance = economyBalance;
        this.initialTime = initialTime;
        this.pricePerSecond = pricePerSecond > 0 ? pricePerSecond : 100.0 / 3600.0;
        this.lastUpdate = System.currentTimeMillis();
    }

    public int getClaimId() {
        return claimId;
    }

    public Location getLocation() {
        return location;
    }

    public long getEnergyTime() {
        return energyTime;
    }

    public void setEnergyTime(long energyTime) {
        this.energyTime = energyTime;
        this.lastUpdate = System.currentTimeMillis();
    }

    public double getEconomyBalance() {
        return economyBalance;
    }

    public void setEconomyBalance(double economyBalance) {
        this.economyBalance = economyBalance;
        this.lastUpdate = System.currentTimeMillis();
    }

    public long getInitialTime() {
        return initialTime;
    }

    public double getPricePerSecond() {
        return pricePerSecond;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    /**
     * 添加物品能源
     */
    public void addItemEnergy(long seconds) {
        this.energyTime += seconds;
        this.lastUpdate = System.currentTimeMillis();
    }

    /**
     * 添加经济能源
     */
    public void addEconomyEnergy(double amount) {
        this.economyBalance += amount;
        this.lastUpdate = System.currentTimeMillis();
    }

    /**
     * 消耗时间
     */
    public void consumeTime(long seconds) {
        long remaining = seconds;
        
        if (this.energyTime > 0) {
            long used = Math.min(this.energyTime, remaining);
            this.energyTime -= used;
            remaining -= used;
        }

        if (remaining > 0 && this.economyBalance > 0) {
            double cost = remaining * pricePerSecond;
            this.economyBalance = Math.max(0, this.economyBalance - cost);
        }

        this.lastUpdate = System.currentTimeMillis();
    }

    /**
     * 检查是否有足够的能源
     */
    public boolean hasEnergy() {
        return energyTime > 0 || economyBalance > 0;
    }

    /**
     * 获取总剩余时间（秒）
     */
    public long getTotalRemainingTime() {
        long itemTime = energyTime;
        long economyTime = pricePerSecond > 0 ? (long) (economyBalance / pricePerSecond) : 0;
        return itemTime + economyTime + initialTime;
    }
}
