package org.kari.kariClaims;

import org.bukkit.plugin.java.JavaPlugin;
import org.kari.kariClaims.commands.ChunkClaimCommand;
import org.kari.kariClaims.database.ChunkClaimDAO;
import org.kari.kariClaims.database.ClaimDAO;
import org.kari.kariClaims.database.DatabaseManager;
import org.kari.kariClaims.database.DatabaseMigration;
import org.kari.kariClaims.gui.ClaimGUI;
import org.kari.kariClaims.gui.GUIListener;
import org.kari.kariClaims.gui.PowerCellGUI;
import org.kari.kariClaims.listeners.ClaimListener;
import org.kari.kariClaims.listeners.PowerCellListener;
import org.kari.kariClaims.managers.ChunkClaimManager;
import org.kari.kariClaims.managers.ClaimManager;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.logging.Level;

/**
 * KariClaims - 高性能领地保护插件
 * 兼容 UltimateClaims 数据库
 */
public final class KariClaims extends JavaPlugin {
    private static KariClaims instance;
    
    private DatabaseManager databaseManager;
    private ClaimDAO claimDAO;
    private ChunkClaimDAO chunkClaimDAO;
    private ClaimManager claimManager;
    private ChunkClaimManager chunkClaimManager;
    private org.kari.kariClaims.managers.EconomyManager economyManager;
    private ClaimGUI claimGUI;
    private PowerCellGUI powerCellGUI;
    private BukkitTask energyDrainTask;
    private long lastEnergyUpdate;
    private org.kari.kariClaims.listeners.ClaimEnterListener claimEnterListener;
    private org.kari.kariClaims.tasks.PowerCellParticleTask powerCellParticleTask;
    private PowerCellListener powerCellListener;

    @Override
    public void onEnable() {
        instance = this;
        
        // 保存默认配置
        saveDefaultConfig();
        
        getLogger().info("正在初始化 KariClaims...");
        
        try {
            // 预加载 SQLite 驱动以提取原生库
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                // SQLite 驱动加载失败
            }
            
            // 初始化数据库
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            
            claimDAO = new ClaimDAO(databaseManager);
            claimDAO.createTables();
            
            // 初始化区块系统
            chunkClaimDAO = new ChunkClaimDAO(databaseManager);
            chunkClaimDAO.createTables();
            
            // 执行数据库迁移
            DatabaseMigration migration = new DatabaseMigration(this, databaseManager);
            migration.migrateFromUltimateClaims();
            
            // 初始化管理器
            claimManager = new ClaimManager(this, claimDAO);
            chunkClaimManager = new ChunkClaimManager(this, chunkClaimDAO);
            economyManager = new org.kari.kariClaims.managers.EconomyManager(this);
            claimGUI = new ClaimGUI(this);
            powerCellGUI = new PowerCellGUI(this);
            
            // 加载所有领地数据
            chunkClaimManager.loadAllClaims();
            
            // 注册命令（优先使用区块系统命令）
            ChunkClaimCommand chunkCommand = new ChunkClaimCommand(this);
            getCommand("claim").setExecutor(chunkCommand);
            getCommand("claim").setTabCompleter(chunkCommand);
            getCommand("c").setExecutor(chunkCommand);
            getCommand("c").setTabCompleter(chunkCommand);
            
            // 注册迁移命令
            org.kari.kariClaims.commands.MigrateCommand migrateCommand = new org.kari.kariClaims.commands.MigrateCommand(this);
            getCommand("kariclaims").setExecutor(migrateCommand);
            getCommand("kariclaims").setTabCompleter(migrateCommand);
            
            // 注册事件监听器
            getServer().getPluginManager().registerEvents(new ClaimListener(this), this);
            getServer().getPluginManager().registerEvents(new org.kari.kariClaims.listeners.ChunkClaimListener(this), this);
            getServer().getPluginManager().registerEvents(new GUIListener(this, claimGUI), this);
            powerCellListener = new PowerCellListener(this);
            getServer().getPluginManager().registerEvents(powerCellListener, this);
            getServer().getPluginManager().registerEvents(new org.kari.kariClaims.listeners.PowerCellRecipeListener(this), this);
            getServer().getPluginManager().registerEvents(new org.kari.kariClaims.listeners.PowerCellBreakListener(this), this);
            
            // 注册领地进入监听器（BossBar）
            claimEnterListener = new org.kari.kariClaims.listeners.ClaimEnterListener(this);
            getServer().getPluginManager().registerEvents(claimEnterListener, this);
            
            // 启动能量扣除任务
            startEnergyDrainTask();
            
            // 启动能量电池粒子效果任务
            if (getConfig().getBoolean("power-cell.particles.enabled", true)) {
                powerCellParticleTask = new org.kari.kariClaims.tasks.PowerCellParticleTask(this);
                int interval = getConfig().getInt("power-cell.particles.interval", 2);
                powerCellParticleTask.start(interval);
            }
            
            // 加载所有能源箱位置到缓存
            chunkClaimDAO.getAllPowerCellLocationsAsync()
                .thenAccept(locations -> {
                    for (Map.Entry<Integer, org.bukkit.Location> entry : locations.entrySet()) {
                        chunkClaimManager.registerPowerCellLocation(entry.getKey(), entry.getValue());
                    }
                })
                .exceptionally(throwable -> null);
            
            getLogger().info("KariClaims 已成功加载！");
            getLogger().info("版本: " + getPluginMeta().getVersion());
            getLogger().info("数据库类型: " + databaseManager.getDatabaseType());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "加载插件时发生错误", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("正在关闭 KariClaims...");
        
        // 【重要】首先保存所有打开的能源箱GUI中的物品
        if (powerCellListener != null) {
            try {
                powerCellListener.saveAllOpenGUIItems();
            } catch (Exception e) {
                getLogger().warning("保存能源箱GUI物品时出错: " + e.getMessage());
            }
        }
        
        // 同步保存所有领地数据
        if (chunkClaimManager != null) {
            try {
                chunkClaimManager.saveAllDataSync();
            } catch (Exception e) {
                getLogger().severe("保存领地数据时出错: " + e.getMessage());
            }
        }
        
        if (claimManager != null) {
            claimManager.invalidateCache();
        }
        
        if (databaseManager != null) {
            databaseManager.close();
        }

        if (energyDrainTask != null) {
            energyDrainTask.cancel();
        }
        
        if (powerCellParticleTask != null) {
            powerCellParticleTask.cancel();
        }
        
        if (claimEnterListener != null) {
            claimEnterListener.cleanup();
        }
        
        getLogger().info("KariClaims 已卸载");
    }

    public static KariClaims getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ClaimDAO getClaimDAO() {
        return claimDAO;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public ClaimGUI getClaimGUI() {
        return claimGUI;
    }

    public ChunkClaimDAO getChunkClaimDAO() {
        return chunkClaimDAO;
    }

    public ChunkClaimManager getChunkClaimManager() {
        return chunkClaimManager;
    }

    public PowerCellGUI getPowerCellGUI() {
        return powerCellGUI;
    }

    public org.kari.kariClaims.managers.EconomyManager getEconomyManager() {
        return economyManager;
    }
    
    public org.kari.kariClaims.listeners.ClaimEnterListener getClaimEnterListener() {
        return claimEnterListener;
    }

    /**
     * 定时扣除领地剩余时间与经济余额
     */
    private void startEnergyDrainTask() {
        lastEnergyUpdate = System.currentTimeMillis();
        long intervalTicks = 20L * 60; // 每分钟结算一次

        energyDrainTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            long now = System.currentTimeMillis();
            long elapsedSeconds = (now - lastEnergyUpdate) / 1000;
            if (elapsedSeconds <= 0) {
                return;
            }
            lastEnergyUpdate = now;

            double pricePerHour = getConfig().getDouble("power-cell.economy-price-per-hour", 100.0);
            double pricePerSecond = pricePerHour / 3600.0;

            // 先消耗物品并更新energy_time，然后扣除时间
            getServer().getScheduler().runTask(this, () -> {
                chunkClaimManager.consumeItemsFromPowerCells(elapsedSeconds, pricePerSecond, this);
            });
        }, intervalTicks, intervalTicks);
    }
}
