package org.kari.kariClaims.migration;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.database.DatabaseManager;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * UltimateClaims 数据迁移工具
 * 将 UltimateClaims 插件的数据库迁移到 KariClaims
 */
public class UltimateClaimsMigrator {
    private final KariClaims plugin;
    private final DatabaseManager databaseManager;
    
    // UltimateClaims 表前缀（默认为 ultimateclaims_）
    private String ucTablePrefix = "ultimateclaims_";
    
    public UltimateClaimsMigrator(KariClaims plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        // 从配置读取表前缀
        this.ucTablePrefix = plugin.getConfig().getString("migration.ultimateclaims-table-prefix", "ultimateclaims_");
    }
    
    /**
     * 设置 UltimateClaims 表前缀
     */
    public void setTablePrefix(String prefix) {
        this.ucTablePrefix = prefix;
    }
    
    /**
     * 执行迁移（支持 SQLite 和 H2 数据库文件）
     */
    public CompletableFuture<MigrationResult> migrateFromSQLite(File dbFile, CommandSender sender) {
        return CompletableFuture.supplyAsync(() -> {
            MigrationResult result = new MigrationResult();
            
            // 检测数据库类型
            String fileName = dbFile.getName().toLowerCase();
            String jdbcUrl;
            boolean isH2 = fileName.endsWith(".mv.db") || fileName.endsWith(".h2.db");
            
            if (isH2) {
                // H2 数据库 - 移除 .mv.db 后缀
                String basePath = dbFile.getAbsolutePath();
                if (basePath.endsWith(".mv.db")) {
                    basePath = basePath.substring(0, basePath.length() - 6);
                } else if (basePath.endsWith(".h2.db")) {
                    basePath = basePath.substring(0, basePath.length() - 6);
                }
                jdbcUrl = "jdbc:h2:" + basePath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
                sendMessage(sender, "§7检测到 H2 数据库格式...");
            } else {
                // SQLite 数据库
                if (!dbFile.exists()) {
                    result.setSuccess(false);
                    result.setError("找不到 UltimateClaims 数据库文件: " + dbFile.getAbsolutePath());
                    return result;
                }
                jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                sendMessage(sender, "§7检测到 SQLite 数据库格式...");
            }
            
            try (Connection ucConn = DriverManager.getConnection(jdbcUrl);
                 Connection kcConn = databaseManager.getConnection()) {
                
                sendMessage(sender, "§a开始迁移 UltimateClaims 数据...");
                
                // 禁用自动提交以提高性能
                kcConn.setAutoCommit(false);
                
                try {
                    // 1. 迁移领地数据
                    PreMigrationData preData = migrateClaimsAndChunks(ucConn, kcConn, result, sender);
                    Map<Integer, List<Integer>> claimIdMapping = preData.claimIdMapping;
                    
                    // 2. 迁移成员数据
                    migrateMembers(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 3. 迁移封禁数据
                    migrateBans(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 4. 迁移设置数据
                    migrateSettings(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 5. 迁移权限数据
                    migratePermissions(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 6. 迁移能量电池位置
                    migratePowerCells(kcConn, preData.powerCellPlan, result, sender);
                    
                    // 提交事务
                    kcConn.commit();
                    result.setSuccess(true);
                    
                    sendMessage(sender, "§a迁移完成！");
                    sendMessage(sender, "§7- 领地: §e" + result.getClaimsMigrated());
                    sendMessage(sender, "§7- 区块: §e" + result.getChunksMigrated());
                    sendMessage(sender, "§7- 成员: §e" + result.getMembersMigrated());
                    sendMessage(sender, "§7- 封禁: §e" + result.getBansMigrated());
                    sendMessage(sender, "§7- 能量电池: §e" + result.getPowerCellsMigrated());
                    
                    if (result.getClaimsWithDefaultProtection() > 0) {
                        int defaultMinutes = plugin.getConfig().getInt("migration.default-protection-minutes", 10080);
                        int days = defaultMinutes / 60 / 24;
                        sendMessage(sender, "§e- 无能量电池领地: §c" + result.getClaimsWithDefaultProtection() + 
                            " §7(已设置 " + days + " 天保护时间)");
                    }
                    
                    if (result.getWarnings().size() > 0) {
                        sendMessage(sender, "§e警告: " + result.getWarnings().size() + " 条");
                    }
                    
                } catch (Exception e) {
                    kcConn.rollback();
                    throw e;
                }
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "迁移失败", e);
                result.setSuccess(false);
                result.setError("迁移失败: " + e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * 执行迁移（从 MariaDB/MySQL 服务器）
     */
    public CompletableFuture<MigrationResult> migrateFromMariaDB(String host, int port, String database, 
            String user, String password, CommandSender sender) {
        return CompletableFuture.supplyAsync(() -> {
            MigrationResult result = new MigrationResult();
            
            String jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + database;
            
            try (Connection ucConn = DriverManager.getConnection(jdbcUrl, user, password);
                 Connection kcConn = databaseManager.getConnection()) {
                
                sendMessage(sender, "§a已连接到 UltimateClaims 数据库，开始迁移...");
                
                // 禁用自动提交以提高性能
                kcConn.setAutoCommit(false);
                
                try {
                    // 1. 迁移领地数据
                    PreMigrationData preData = migrateClaimsAndChunks(ucConn, kcConn, result, sender);
                    Map<Integer, List<Integer>> claimIdMapping = preData.claimIdMapping;
                    
                    // 2. 迁移成员数据
                    migrateMembers(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 3. 迁移封禁数据
                    migrateBans(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 4. 迁移设置数据
                    migrateSettings(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 5. 迁移权限数据
                    migratePermissions(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 6. 迁移能量电池位置
                    migratePowerCells(kcConn, preData.powerCellPlan, result, sender);
                    
                    // 提交事务
                    kcConn.commit();
                    result.setSuccess(true);
                    
                    sendMessage(sender, "§a迁移完成！");
                    sendMessage(sender, "§7- 领地: §e" + result.getClaimsMigrated());
                    sendMessage(sender, "§7- 区块: §e" + result.getChunksMigrated());
                    sendMessage(sender, "§7- 成员: §e" + result.getMembersMigrated());
                    sendMessage(sender, "§7- 封禁: §e" + result.getBansMigrated());
                    sendMessage(sender, "§7- 能量电池: §e" + result.getPowerCellsMigrated());
                    
                    if (result.getClaimsWithDefaultProtection() > 0) {
                        int defaultMinutes = plugin.getConfig().getInt("migration.default-protection-minutes", 10080);
                        int days = defaultMinutes / 60 / 24;
                        sendMessage(sender, "§e- 无能量电池领地: §c" + result.getClaimsWithDefaultProtection() + 
                            " §7(已设置 " + days + " 天保护时间)");
                    }
                    
                    if (result.getWarnings().size() > 0) {
                        sendMessage(sender, "§e警告: " + result.getWarnings().size() + " 条");
                    }
                    
                } catch (Exception e) {
                    kcConn.rollback();
                    throw e;
                }
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "迁移失败", e);
                result.setSuccess(false);
                result.setError("迁移失败: " + e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * 迁移领地和区块数据
     */
    private PreMigrationData migrateClaimsAndChunks(Connection ucConn, Connection kcConn, 
            MigrationResult result, CommandSender sender) throws SQLException {
        
        Map<Integer, List<Integer>> claimIdMapping = new HashMap<>(); // UC claim_id -> List<KC claim_id>
        Map<Integer, UUID> claimOwners = new HashMap<>(); // UC claim_id -> owner UUID
        Map<Integer, ClaimData> claimDataMap = new HashMap<>();
        PreMigrationData preData = new PreMigrationData(claimIdMapping);
        
        // 追踪已使用的名称和能量箱位置，避免冲突
        Set<String> usedNames = new HashSet<>();
        Set<String> usedPowerCellLocs = new HashSet<>();
        
        sendMessage(sender, "§7正在读取 UltimateClaims 领地数据...");
        
        // 读取 UltimateClaims 的 claim 表
        String selectClaims = "SELECT * FROM " + ucTablePrefix + "claim";
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectClaims)) {
            
            while (rs.next()) {
                ClaimData data = new ClaimData();
                data.id = rs.getInt("id");
                data.name = rs.getString("name");
                data.power = rs.getInt("power");
                data.ecoBal = rs.getDouble("eco_bal");
                data.locked = rs.getInt("locked") == 1;
                data.homeWorld = rs.getString("home_world");
                data.homeX = rs.getDouble("home_x");
                data.homeY = rs.getDouble("home_y");
                data.homeZ = rs.getDouble("home_z");
                data.homePitch = rs.getFloat("home_pitch");
                data.homeYaw = rs.getFloat("home_yaw");
                data.powercellWorld = rs.getString("powercell_world");
                data.powercellX = rs.getInt("powercell_x");
                data.powercellY = rs.getInt("powercell_y");
                data.powercellZ = rs.getInt("powercell_z");
                
                try {
                    // 尝试读取物品数据（如果有）
                    data.powerCellInventory = rs.getString("powercell_inventory");
                } catch (SQLException e) {
                    // ignore
                }
                
                claimDataMap.put(data.id, data);
            }
        }
        
        // 读取成员表获取所有者
        String selectMembers = "SELECT * FROM " + ucTablePrefix + "member WHERE role = 3"; // OWNER role
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectMembers)) {
            
            while (rs.next()) {
                int claimId = rs.getInt("claim_id");
                String playerUUID = rs.getString("player_uuid");
                try {
                    claimOwners.put(claimId, UUID.fromString(playerUUID));
                } catch (Exception e) {
                    result.addWarning("无效的玩家UUID: " + playerUUID + " (claim_id=" + claimId + ")");
                }
            }
        }
        
        // 读取区块数据
        Map<Integer, List<ChunkData>> claimChunks = new HashMap<>();
        String selectChunks = "SELECT * FROM " + ucTablePrefix + "chunk";
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectChunks)) {
            
            while (rs.next()) {
                int claimId = rs.getInt("claim_id");
                ChunkData chunk = new ChunkData();
                chunk.world = rs.getString("world");
                chunk.x = rs.getInt("x");
                chunk.z = rs.getInt("z");
                chunk.regionId = rs.getString("region_id");
                
                claimChunks.computeIfAbsent(claimId, k -> new ArrayList<>()).add(chunk);
            }
        }
        
        sendMessage(sender, "§7正在写入 KariClaims 数据库...");
        
        // 为每个 UC claim 创建 KC region 和 chunk_claims
        for (Map.Entry<Integer, ClaimData> entry : claimDataMap.entrySet()) {
            int ucClaimId = entry.getKey();
            ClaimData data = entry.getValue();
            UUID owner = claimOwners.get(ucClaimId);
            
            if (owner == null) {
                result.addWarning("领地 " + ucClaimId + " 没有所有者，跳过");
                continue;
            }
            
            List<ChunkData> chunks = claimChunks.get(ucClaimId);
            if (chunks == null || chunks.isEmpty()) {
                result.addWarning("领地 " + ucClaimId + " 没有区块，跳过");
                continue;
            }
            
            // 对区块进行聚类拆分
            List<List<ChunkData>> clusters = clusterChunks(chunks);
            List<Integer> createdKcClaimIds = new ArrayList<>();
            
            int clusterIndex = 0;
            for (List<ChunkData> cluster : clusters) {
                clusterIndex++;
                
                // 1. 确定区域名称
                String baseName = data.name != null ? data.name : "Claim";
                String clusterName = clusters.size() > 1 ? baseName + "_" + clusterIndex : baseName;
                
                // 名称去重
                String finalName = clusterName;
                int nameCount = 1;
                while (usedNames.contains(finalName)) {
                    finalName = clusterName + "_" + nameCount++;
                }
                usedNames.add(finalName);
                
                // 2. 确定 Home 位置
                String homeWorld = null;
                double homeX = 0, homeY = 0, homeZ = 0;
                float homeYaw = 0, homePitch = 0;
                boolean hasHome = false;
                
                // 检查原 Home 是否在这个 Cluster 内
                if (data.homeWorld != null) {
                    for (ChunkData c : cluster) {
                        if (c.world.equals(data.homeWorld) && 
                            (int)data.homeX >> 4 == c.x && 
                            (int)data.homeZ >> 4 == c.z) {
                            homeWorld = data.homeWorld;
                            homeX = data.homeX;
                            homeY = data.homeY;
                            homeZ = data.homeZ;
                            homeYaw = data.homeYaw;
                            homePitch = data.homePitch;
                            hasHome = true;
                            break;
                        }
                    }
                }
                
                // 如果原 Home 不在，计算新 Home (Cluster 第一个区块的中心)
                if (!hasHome) {
                    ChunkData c = cluster.get(0);
                    homeWorld = c.world;
                    homeX = (c.x << 4) + 8;
                    homeY = 64; // 默认高度
                    homeZ = (c.z << 4) + 8;
                    hasHome = true;
                }
                
                // 3. 确定 PowerCell 位置
                String pcWorld = null;
                int pcX = 0, pcY = 0, pcZ = 0;
                boolean hasPowerCell = false;
                
                // 检查原 PowerCell 是否在这个 Cluster 内且未冲突
                if (data.powercellWorld != null) {
                    String key = data.powercellWorld + ":" + data.powercellX + "," + data.powercellY + "," + data.powercellZ;
                    if (!usedPowerCellLocs.contains(key)) {
                         for (ChunkData c : cluster) {
                            if (c.world.equals(data.powercellWorld) && 
                                data.powercellX >> 4 == c.x && 
                                data.powercellZ >> 4 == c.z) {
                                pcWorld = data.powercellWorld;
                                pcX = data.powercellX;
                                pcY = data.powercellY;
                                pcZ = data.powercellZ;
                                hasPowerCell = true;
                                break;
                            }
                        }
                    }
                }
                
                // 如果没有继承 PowerCell，使用 Home 位置或中心
                if (!hasPowerCell) {
                    pcWorld = homeWorld;
                    pcX = (int) homeX;
                    pcY = (int) homeY;
                    pcZ = (int) homeZ;
                    
                    // 简单防冲突
                    String key = pcWorld + ":" + pcX + "," + pcY + "," + pcZ;
                    if (usedPowerCellLocs.contains(key)) {
                        pcY++; // 尝试向上偏移
                    }
                }
                
                usedPowerCellLocs.add(pcWorld + ":" + pcX + "," + pcY + "," + pcZ);
                
                // 4. 插入 KC Region
                String insertRegion = "INSERT INTO claim_regions (owner, world, region_name, locked, energy_time, economy_balance, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
                int regionId = -1;
                
                try (PreparedStatement pstmt = kcConn.prepareStatement(insertRegion, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, owner.toString());
                    pstmt.setString(2, cluster.get(0).world);
                    pstmt.setString(3, finalName);
                    pstmt.setBoolean(4, data.locked);
                    pstmt.setLong(5, data.power * 60L); 
                    pstmt.setDouble(6, data.ecoBal);
                    pstmt.setLong(7, System.currentTimeMillis());
                    pstmt.executeUpdate();
                    
                    try (ResultSet keys = pstmt.getGeneratedKeys()) {
                        if (keys.next()) regionId = keys.getInt(1);
                        else {
                            try (Statement q = kcConn.createStatement(); ResultSet rs = q.executeQuery("SELECT last_insert_rowid()")) {
                                if (rs.next()) regionId = rs.getInt(1);
                            }
                        }
                    }
                }
                
                // 5. 插入 Chunks
                String insertChunk = "INSERT INTO chunk_claims (owner, world, chunk_x, chunk_z, region_id, chunk_name, " +
                    "home_x, home_y, home_z, home_yaw, home_pitch, locked, energy_time, economy_balance, claimed_at, initial_time, home_public) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
                int powerCellClaimId = -1;
                boolean firstChunkInCluster = true;
                
                for (ChunkData chunk : cluster) {
                    try (PreparedStatement pstmt = kcConn.prepareStatement(insertChunk, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setString(1, owner.toString());
                        pstmt.setString(2, chunk.world);
                        pstmt.setInt(3, chunk.x);
                        pstmt.setInt(4, chunk.z);
                        pstmt.setInt(5, regionId);
                        pstmt.setString(6, finalName);
                        
                        // Home 只在第一个 Chunk 设置（或逻辑上所属的）
                        // 为简化，我们在第一个 Chunk 设置 Home 字段，虽然物理位置可能在别处
                        // KariClaims 可能要求 Home 在该 Chunk 内？不，Region 共享 Home
                        if (firstChunkInCluster) {
                            pstmt.setDouble(7, homeX);
                            pstmt.setDouble(8, homeY);
                            pstmt.setDouble(9, homeZ);
                            pstmt.setFloat(10, homeYaw);
                            pstmt.setFloat(11, homePitch);
                        } else {
                            pstmt.setNull(7, Types.DOUBLE);
                            pstmt.setNull(8, Types.DOUBLE);
                            pstmt.setNull(9, Types.DOUBLE);
                            pstmt.setNull(10, Types.FLOAT);
                            pstmt.setNull(11, Types.FLOAT);
                        }
                        
                        pstmt.setBoolean(12, data.locked);
                        pstmt.setLong(13, data.power * 60L);
                        pstmt.setDouble(14, data.ecoBal);
                        pstmt.setLong(15, System.currentTimeMillis());
                        
                        long initialTime = data.power > 0 ? data.power * 60L : plugin.getConfig().getInt("migration.default-protection-minutes", 10080) * 60L;
                        if (data.power <= 0 && firstChunkInCluster) result.incrementClaimsWithDefaultProtection();
                        
                        pstmt.setLong(16, initialTime);
                        pstmt.setBoolean(17, true);
                        
                        pstmt.executeUpdate();
                        
                        int generatedId = -1;
                        try (ResultSet keys = pstmt.getGeneratedKeys()) {
                            if (keys.next()) generatedId = keys.getInt(1);
                            else {
                                try (Statement q = kcConn.createStatement(); ResultSet rs = q.executeQuery("SELECT last_insert_rowid()")) {
                                    if (rs.next()) generatedId = rs.getInt(1);
                                }
                            }
                        }
                        
                        if (generatedId != -1) {
                            if (firstChunkInCluster) {
                                createdKcClaimIds.add(generatedId);
                                firstChunkInCluster = false;
                            }
                            
                            // 检查 PowerCell 是否在此 Chunk
                            if (chunk.world.equals(pcWorld) && 
                                chunk.x == (pcX >> 4) && 
                                chunk.z == (pcZ >> 4)) {
                                powerCellClaimId = generatedId;
                            }
                        }
                        
                        result.incrementChunksMigrated();
                    }
                }
                
                // 如果没找到 PowerCell 对应的 Chunk（比如回退到了 Chunk 0），则关联到第一个生成的 ID
                if (powerCellClaimId == -1 && !createdKcClaimIds.isEmpty()) {
                    powerCellClaimId = createdKcClaimIds.get(createdKcClaimIds.size() - 1); // 当前 Cluster 的第一个
                }
                
                if (powerCellClaimId != -1) {
                    preData.powerCellPlan.put(powerCellClaimId, new PowerCellInfo(pcWorld, pcX, pcY, pcZ, data.power, data.ecoBal, data.powerCellInventory));
                }
            }
            
            claimIdMapping.put(ucClaimId, createdKcClaimIds);
            result.incrementClaimsMigrated();
        }
        
        return preData;
    }
    
    /**
     * 迁移成员数据
     */
    private void migrateMembers(Connection ucConn, Connection kcConn, 
            Map<Integer, List<Integer>> claimIdMapping, MigrationResult result, CommandSender sender) throws SQLException {
        
        sendMessage(sender, "§7正在迁移成员数据...");
        
        String selectMembers = "SELECT * FROM " + ucTablePrefix + "member WHERE role != 3"; // 非所有者
        String insertMember = "INSERT INTO chunk_claim_members (claim_id, player_id, role, joined_at) VALUES (?, ?, ?, ?)";
        
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectMembers);
             PreparedStatement pstmt = kcConn.prepareStatement(insertMember)) {
            
            while (rs.next()) {
                int ucClaimId = rs.getInt("claim_id");
                List<Integer> kcClaimIds = claimIdMapping.get(ucClaimId);
                
                if (kcClaimIds == null || kcClaimIds.isEmpty()) {
                    continue; // 跳过未迁移的领地
                }
                
                String playerUUID = rs.getString("player_uuid");
                long memberSince = rs.getLong("member_since");
                int kcRole = 1; // MEMBER
                
                for (Integer kcClaimId : kcClaimIds) {
                    try {
                        pstmt.setInt(1, kcClaimId);
                        pstmt.setString(2, playerUUID);
                        pstmt.setInt(3, kcRole);
                        pstmt.setLong(4, memberSince > 0 ? memberSince : System.currentTimeMillis());
                        pstmt.executeUpdate();
                        
                        result.incrementMembersMigrated();
                    } catch (SQLException e) {
                        result.addWarning("成员插入失败: " + playerUUID + " -> claim " + kcClaimId);
                    }
                }
            }
        }
    }
    
    /**
     * 迁移封禁数据
     */
    private void migrateBans(Connection ucConn, Connection kcConn, 
            Map<Integer, List<Integer>> claimIdMapping, MigrationResult result, CommandSender sender) throws SQLException {
        
        sendMessage(sender, "§7正在迁移封禁数据...");
        
        String selectBans = "SELECT * FROM " + ucTablePrefix + "ban";
        String insertBan = "INSERT INTO banned_players (claim_id, player_id, banned_at) VALUES (?, ?, ?)";
        
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectBans);
             PreparedStatement pstmt = kcConn.prepareStatement(insertBan)) {
            
            while (rs.next()) {
                int ucClaimId = rs.getInt("claim_id");
                List<Integer> kcClaimIds = claimIdMapping.get(ucClaimId);
                
                if (kcClaimIds == null || kcClaimIds.isEmpty()) {
                    continue;
                }
                
                String playerUUID = rs.getString("player_uuid");
                
                for (Integer kcClaimId : kcClaimIds) {
                    try {
                        pstmt.setInt(1, kcClaimId);
                        pstmt.setString(2, playerUUID);
                        pstmt.setLong(3, System.currentTimeMillis());
                        pstmt.executeUpdate();
                        
                        result.incrementBansMigrated();
                    } catch (SQLException e) {
                        result.addWarning("封禁插入失败: " + playerUUID + " -> claim " + kcClaimId);
                    }
                }
            }
        }
    }
    
    /**
     * 迁移设置数据
     */
    private void migrateSettings(Connection ucConn, Connection kcConn, 
            Map<Integer, List<Integer>> claimIdMapping, MigrationResult result, CommandSender sender) throws SQLException {
        
        sendMessage(sender, "§7正在迁移领地设置...");
        
        String selectSettings = "SELECT * FROM " + ucTablePrefix + "settings";
        
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSettings)) {
            
            while (rs.next()) {
                int ucClaimId = rs.getInt("claim_id");
                List<Integer> kcClaimIds = claimIdMapping.get(ucClaimId);
                
                if (kcClaimIds == null || kcClaimIds.isEmpty()) {
                    continue;
                }
                
                boolean hostileMobSpawning = rs.getInt("hostile_mob_spawning") == 1;
                boolean fireSpread = rs.getInt("fire_spread") == 1;
                boolean mobGriefing = rs.getInt("mob_griefing") == 1;
                boolean leafDecay = rs.getInt("leaf_decay") == 1;
                boolean pvp = rs.getInt("pvp") == 1;
                boolean tnt = rs.getInt("tnt") == 1;
                
                for (Integer kcClaimId : kcClaimIds) {
                    // 更新同一区域的所有区块
                    String updateAllInRegion = "UPDATE chunk_claims SET mob_spawning = ?, fire_spread = ?, mob_griefing = ?, " +
                        "leaf_decay = ?, pvp_enabled = ?, tnt = ? WHERE region_id = " +
                        "(SELECT region_id FROM chunk_claims WHERE id = " + kcClaimId + ")";
                    
                    try (PreparedStatement updatePstmt = kcConn.prepareStatement(updateAllInRegion)) {
                        updatePstmt.setBoolean(1, hostileMobSpawning);
                        updatePstmt.setBoolean(2, fireSpread);
                        updatePstmt.setBoolean(3, mobGriefing);
                        updatePstmt.setBoolean(4, leafDecay);
                        updatePstmt.setBoolean(5, pvp);
                        updatePstmt.setBoolean(6, tnt);
                        updatePstmt.executeUpdate();
                    }
                }
            }
        }
    }
    
    /**
     * 迁移权限数据
     */
    private void migratePermissions(Connection ucConn, Connection kcConn, 
            Map<Integer, List<Integer>> claimIdMapping, MigrationResult result, CommandSender sender) throws SQLException {
        
        sendMessage(sender, "§7正在迁移权限设置...");
        
        String selectPermissions = "SELECT * FROM " + ucTablePrefix + "permissions";
        
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectPermissions)) {
            
            while (rs.next()) {
                int ucClaimId = rs.getInt("claim_id");
                List<Integer> kcClaimIds = claimIdMapping.get(ucClaimId);
                
                if (kcClaimIds == null || kcClaimIds.isEmpty()) {
                    continue;
                }
                
                String type = rs.getString("type"); // "member" or "visitor"
                boolean interact = rs.getInt("interact") == 1;
                boolean breakBlock = rs.getInt("break") == 1;
                boolean place = rs.getInt("place") == 1;
                boolean mobKill = rs.getInt("mob_kill") == 1;
                boolean redstone = rs.getInt("redstone") == 1;
                boolean doors = rs.getInt("doors") == 1;
                boolean trading = rs.getInt("trading") == 1;
                
                int permissions = 0;
                if (interact) permissions |= 1;
                if (breakBlock) permissions |= 2;
                if (place) permissions |= 4;
                if (mobKill) permissions |= 8;
                if (redstone) permissions |= 16;
                if (doors) permissions |= 32;
                if (trading) permissions |= 64;
                
                for (Integer kcClaimId : kcClaimIds) {
                    String column = type.equals("visitor") ? "visitor_permissions" : "member_permissions";
                    String updatePermissions = "UPDATE chunk_claims SET " + column + " = ? WHERE region_id = " +
                        "(SELECT region_id FROM chunk_claims WHERE id = " + kcClaimId + ")";
                    
                    try (PreparedStatement pstmt = kcConn.prepareStatement(updatePermissions)) {
                        pstmt.setInt(1, permissions);
                        pstmt.executeUpdate();
                    }
                }
            }
        }
    }
    
    /**
     * 迁移能量电池位置
     */
    private void migratePowerCells(Connection kcConn, Map<Integer, PowerCellInfo> powerCellPlan, 
            MigrationResult result, CommandSender sender) throws SQLException {
        
        sendMessage(sender, "§7正在迁移能量电池数据...");
        
        String insertPowerCell = "INSERT INTO power_cells (claim_id, region_id, world, x, y, z, energy_time, economy_balance, last_update) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        // 获取所有 region_id
        Map<Integer, Integer> claimRegionMap = new HashMap<>();
        String getAllRegions = "SELECT id, region_id FROM chunk_claims";
        try (Statement stmt = kcConn.createStatement();
             ResultSet rs = stmt.executeQuery(getAllRegions)) {
            while (rs.next()) {
                claimRegionMap.put(rs.getInt("id"), rs.getInt("region_id"));
            }
        }
        
        try (PreparedStatement pstmt = kcConn.prepareStatement(insertPowerCell)) {
            
            for (Map.Entry<Integer, PowerCellInfo> entry : powerCellPlan.entrySet()) {
                int kcClaimId = entry.getKey();
                PowerCellInfo info = entry.getValue();
                
                int regionId = claimRegionMap.getOrDefault(kcClaimId, 0);
                
                try {
                    pstmt.setInt(1, kcClaimId);
                    pstmt.setInt(2, regionId);
                    pstmt.setString(3, info.world);
                    pstmt.setInt(4, info.x);
                    pstmt.setInt(5, info.y);
                    pstmt.setInt(6, info.z);
                    pstmt.setLong(7, info.power * 60L); // 转换为秒
                    pstmt.setDouble(8, info.ecoBal);
                    pstmt.setLong(9, System.currentTimeMillis());
                    pstmt.executeUpdate();
                    
                    result.incrementPowerCellsMigrated();
                    // 添加到物理生成列表
                    result.addGeneratedPowerCell(new PowerCellData(info.world, info.x, info.y, info.z, info.inventory, kcClaimId));
                    
                    sendMessage(sender, "§a能量电池迁移: " + info.world + " (" + info.x + ", " + info.y + ", " + info.z + ") -> claim_id=" + kcClaimId);
                } catch (SQLException e) {
                    result.addWarning("能量电池插入失败: claim " + kcClaimId + " - " + e.getMessage());
                    sendMessage(sender, "§c能量电池插入失败: " + e.getMessage());
                }
            }
        }
        
        sendMessage(sender, "§7能量电池统计: 成功 " + result.getPowerCellsMigrated() + " 个");
    }
    
    private void sendMessage(CommandSender sender, String message) {
        if (sender != null) {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
        }
        plugin.getLogger().info(message.replaceAll("§.", ""));
    }
    
    // 数据类
    private static class ClaimData {
        int id;
        String name;
        int power;
        double ecoBal;
        boolean locked;
        String homeWorld;
        double homeX, homeY, homeZ;
        float homePitch, homeYaw;
        String powercellWorld;
        int powercellX, powercellY, powercellZ;
        String powerCellInventory; // Base64 inventory string
    }
    
    private static class ChunkData {
        String world;
        int x, z;
        String regionId;
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkData chunkData = (ChunkData) o;
            return x == chunkData.x && z == chunkData.z && Objects.equals(world, chunkData.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, x, z);
        }
    }
    
    /**
     * 将区块按连通性聚类
     */
    private List<List<ChunkData>> clusterChunks(List<ChunkData> chunks) {
        List<List<ChunkData>> clusters = new ArrayList<>();
        Set<ChunkData> visited = new HashSet<>();
        
        for (ChunkData chunk : chunks) {
            if (visited.contains(chunk)) continue;
            
            List<ChunkData> cluster = new ArrayList<>();
            Queue<ChunkData> queue = new LinkedList<>();
            queue.add(chunk);
            visited.add(chunk);
            
            while (!queue.isEmpty()) {
                ChunkData current = queue.poll();
                cluster.add(current);
                
                // 找邻居
                for (ChunkData other : chunks) {
                    if (!visited.contains(other)) {
                        if (isAdjacent(current, other)) {
                            visited.add(other);
                            queue.add(other);
                        }
                    }
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }
    
    /**
     * 判断两个区块是否相邻
     */
    private boolean isAdjacent(ChunkData c1, ChunkData c2) {
        return c1.world.equals(c2.world) && 
               (Math.abs(c1.x - c2.x) + Math.abs(c1.z - c2.z) == 1);
    }
    
    /**
     * 能量电池数据（用于生成物理箱子）
     */
    public static class PowerCellData {
        public final String world;
        public final int x, y, z;
        public final String inventoryBase64; // 物品数据
        public final int claimId; // KariClaims ID
        
        public PowerCellData(String world, int x, int y, int z, String inventoryBase64, int claimId) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.inventoryBase64 = inventoryBase64;
            this.claimId = claimId;
        }
        
        public PowerCellData(String world, int x, int y, int z, String inventoryBase64) {
            this(world, x, y, z, inventoryBase64, -1);
        }
    }
    
    private static class PowerCellInfo {
        String world;
        int x, y, z;
        int power;
        double ecoBal;
        String inventory;
        
        public PowerCellInfo(String world, int x, int y, int z, int power, double ecoBal, String inventory) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.power = power;
            this.ecoBal = ecoBal;
            this.inventory = inventory;
        }
    }
    
    /**
     * 预迁移数据
     */
    private static class PreMigrationData {
        Map<Integer, List<Integer>> claimIdMapping;
        Map<Integer, PowerCellInfo> powerCellPlan = new HashMap<>();
        
        public PreMigrationData(Map<Integer, List<Integer>> claimIdMapping) {
            this.claimIdMapping = claimIdMapping;
        }
    }

    /**
     * 迁移结果
     */
    public static class MigrationResult {
        private boolean success;
        private String error;
        private int claimsMigrated;
        private int chunksMigrated;
        private int membersMigrated;
        private int bansMigrated;
        private int powerCellsMigrated;
        private int claimsWithDefaultProtection;
        private List<String> warnings = new ArrayList<>();
        private List<PowerCellData> generatedPowerCells = new ArrayList<>();
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public int getClaimsMigrated() { return claimsMigrated; }
        public void incrementClaimsMigrated() { this.claimsMigrated++; }
        
        public int getChunksMigrated() { return chunksMigrated; }
        public void incrementChunksMigrated() { this.chunksMigrated++; }
        
        public int getMembersMigrated() { return membersMigrated; }
        public void incrementMembersMigrated() { this.membersMigrated++; }
        
        public int getBansMigrated() { return bansMigrated; }
        public void incrementBansMigrated() { this.bansMigrated++; }
        
        public int getPowerCellsMigrated() { return powerCellsMigrated; }
        public void incrementPowerCellsMigrated() { this.powerCellsMigrated++; }
        
        public int getClaimsWithDefaultProtection() { return claimsWithDefaultProtection; }
        public void incrementClaimsWithDefaultProtection() { this.claimsWithDefaultProtection++; }
        
        public List<String> getWarnings() { return warnings; }
        public void addWarning(String warning) { this.warnings.add(warning); }
        
        public List<PowerCellData> getGeneratedPowerCells() { return generatedPowerCells; }
        public void addGeneratedPowerCell(PowerCellData data) { this.generatedPowerCells.add(data); }
    }
}
