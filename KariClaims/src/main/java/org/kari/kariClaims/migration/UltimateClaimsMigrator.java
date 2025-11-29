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
    }
    
    /**
     * 设置 UltimateClaims 表前缀
     */
    public void setTablePrefix(String prefix) {
        this.ucTablePrefix = prefix;
    }
    
    /**
     * 执行迁移（从 SQLite 文件）
     */
    public CompletableFuture<MigrationResult> migrateFromSQLite(File sqliteFile, CommandSender sender) {
        return CompletableFuture.supplyAsync(() -> {
            MigrationResult result = new MigrationResult();
            
            if (!sqliteFile.exists()) {
                result.setSuccess(false);
                result.setError("找不到 UltimateClaims 数据库文件: " + sqliteFile.getAbsolutePath());
                return result;
            }
            
            String jdbcUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
            
            try (Connection ucConn = DriverManager.getConnection(jdbcUrl);
                 Connection kcConn = databaseManager.getConnection()) {
                
                sendMessage(sender, "§a开始迁移 UltimateClaims 数据...");
                
                // 禁用自动提交以提高性能
                kcConn.setAutoCommit(false);
                
                try {
                    // 1. 迁移领地数据
                    Map<Integer, Integer> claimIdMapping = migrateClaimsAndChunks(ucConn, kcConn, result, sender);
                    
                    // 2. 迁移成员数据
                    migrateMembers(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 3. 迁移封禁数据
                    migrateBans(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 4. 迁移设置数据
                    migrateSettings(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 5. 迁移权限数据
                    migratePermissions(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 6. 迁移能量电池位置
                    migratePowerCells(ucConn, kcConn, claimIdMapping, result, sender);
                    
                    // 提交事务
                    kcConn.commit();
                    result.setSuccess(true);
                    
                    sendMessage(sender, "§a迁移完成！");
                    sendMessage(sender, "§7- 领地: §e" + result.getClaimsMigrated());
                    sendMessage(sender, "§7- 区块: §e" + result.getChunksMigrated());
                    sendMessage(sender, "§7- 成员: §e" + result.getMembersMigrated());
                    sendMessage(sender, "§7- 封禁: §e" + result.getBansMigrated());
                    sendMessage(sender, "§7- 能量电池: §e" + result.getPowerCellsMigrated());
                    
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
    private Map<Integer, Integer> migrateClaimsAndChunks(Connection ucConn, Connection kcConn, 
            MigrationResult result, CommandSender sender) throws SQLException {
        
        Map<Integer, Integer> claimIdMapping = new HashMap<>(); // UC claim_id -> KC claim_id
        Map<Integer, Integer> regionIdMapping = new HashMap<>(); // UC claim_id -> KC region_id
        Map<Integer, UUID> claimOwners = new HashMap<>(); // UC claim_id -> owner UUID
        Map<Integer, ClaimData> claimDataMap = new HashMap<>();
        
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
            
            // 获取第一个区块的世界作为区域世界
            String world = chunks.get(0).world;
            
            // 创建 KC region
            String insertRegion = "INSERT INTO claim_regions (owner, world, region_name, locked, energy_time, economy_balance, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            int regionId;
            try (PreparedStatement pstmt = kcConn.prepareStatement(insertRegion, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, owner.toString());
                pstmt.setString(2, world);
                pstmt.setString(3, data.name != null ? data.name : "迁移领地 #" + ucClaimId);
                pstmt.setBoolean(4, data.locked);
                pstmt.setLong(5, data.power * 60L); // power 转换为秒（假设 power 是分钟）
                pstmt.setDouble(6, data.ecoBal);
                pstmt.setLong(7, System.currentTimeMillis());
                pstmt.executeUpdate();
                
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        regionId = keys.getInt(1);
                    } else {
                        // SQLite fallback
                        try (Statement queryStmt = kcConn.createStatement();
                             ResultSet lastId = queryStmt.executeQuery("SELECT last_insert_rowid()")) {
                            regionId = lastId.next() ? lastId.getInt(1) : -1;
                        }
                    }
                }
            }
            
            regionIdMapping.put(ucClaimId, regionId);
            result.incrementClaimsMigrated();
            
            // 创建 KC chunk_claims
            String insertChunk = "INSERT INTO chunk_claims (owner, world, chunk_x, chunk_z, region_id, chunk_name, " +
                "home_x, home_y, home_z, home_yaw, home_pitch, locked, energy_time, economy_balance, claimed_at, initial_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            boolean firstChunk = true;
            for (ChunkData chunk : chunks) {
                try (PreparedStatement pstmt = kcConn.prepareStatement(insertChunk, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, owner.toString());
                    pstmt.setString(2, chunk.world);
                    pstmt.setInt(3, chunk.x);
                    pstmt.setInt(4, chunk.z);
                    pstmt.setInt(5, regionId);
                    pstmt.setString(6, data.name);
                    
                    // 只有第一个区块设置 home
                    if (firstChunk && data.homeWorld != null) {
                        pstmt.setDouble(7, data.homeX);
                        pstmt.setDouble(8, data.homeY);
                        pstmt.setDouble(9, data.homeZ);
                        pstmt.setFloat(10, data.homeYaw);
                        pstmt.setFloat(11, data.homePitch);
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
                    pstmt.setLong(16, 0);
                    
                    pstmt.executeUpdate();
                    
                    if (firstChunk) {
                        try (ResultSet keys = pstmt.getGeneratedKeys()) {
                            if (keys.next()) {
                                claimIdMapping.put(ucClaimId, keys.getInt(1));
                            } else {
                                try (Statement queryStmt = kcConn.createStatement();
                                     ResultSet lastId = queryStmt.executeQuery("SELECT last_insert_rowid()")) {
                                    if (lastId.next()) {
                                        claimIdMapping.put(ucClaimId, lastId.getInt(1));
                                    }
                                }
                            }
                        }
                        firstChunk = false;
                    }
                    
                    result.incrementChunksMigrated();
                }
            }
        }
        
        return claimIdMapping;
    }
    
    /**
     * 迁移成员数据
     */
    private void migrateMembers(Connection ucConn, Connection kcConn, 
            Map<Integer, Integer> claimIdMapping, MigrationResult result, CommandSender sender) throws SQLException {
        
        sendMessage(sender, "§7正在迁移成员数据...");
        
        String selectMembers = "SELECT * FROM " + ucTablePrefix + "member WHERE role != 3"; // 非所有者
        String insertMember = "INSERT INTO chunk_claim_members (claim_id, player_id, role, joined_at) VALUES (?, ?, ?, ?)";
        
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectMembers);
             PreparedStatement pstmt = kcConn.prepareStatement(insertMember)) {
            
            while (rs.next()) {
                int ucClaimId = rs.getInt("claim_id");
                Integer kcClaimId = claimIdMapping.get(ucClaimId);
                
                if (kcClaimId == null) {
                    continue; // 跳过未迁移的领地
                }
                
                String playerUUID = rs.getString("player_uuid");
                long memberSince = rs.getLong("member_since");
                
                // KC role: 1=MEMBER (所有非所有者都是成员)
                int kcRole = 1;
                
                try {
                    pstmt.setInt(1, kcClaimId);
                    pstmt.setString(2, playerUUID);
                    pstmt.setInt(3, kcRole);
                    pstmt.setLong(4, memberSince > 0 ? memberSince : System.currentTimeMillis());
                    pstmt.executeUpdate();
                    
                    result.incrementMembersMigrated();
                } catch (SQLException e) {
                    // 可能是重复键，忽略
                    result.addWarning("成员插入失败: " + playerUUID + " -> claim " + kcClaimId);
                }
            }
        }
    }
    
    /**
     * 迁移封禁数据
     */
    private void migrateBans(Connection ucConn, Connection kcConn, 
            Map<Integer, Integer> claimIdMapping, MigrationResult result, CommandSender sender) throws SQLException {
        
        sendMessage(sender, "§7正在迁移封禁数据...");
        
        String selectBans = "SELECT * FROM " + ucTablePrefix + "ban";
        String insertBan = "INSERT INTO banned_players (claim_id, player_id, banned_at) VALUES (?, ?, ?)";
        
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectBans);
             PreparedStatement pstmt = kcConn.prepareStatement(insertBan)) {
            
            while (rs.next()) {
                int ucClaimId = rs.getInt("claim_id");
                Integer kcClaimId = claimIdMapping.get(ucClaimId);
                
                if (kcClaimId == null) {
                    continue;
                }
                
                String playerUUID = rs.getString("player_uuid");
                
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
    
    /**
     * 迁移设置数据
     */
    private void migrateSettings(Connection ucConn, Connection kcConn, 
            Map<Integer, Integer> claimIdMapping, MigrationResult result, CommandSender sender) throws SQLException {
        
        sendMessage(sender, "§7正在迁移领地设置...");
        
        String selectSettings = "SELECT * FROM " + ucTablePrefix + "settings";
        String updateSettings = "UPDATE chunk_claims SET mob_spawning = ?, fire_spread = ?, mob_griefing = ?, " +
            "leaf_decay = ?, pvp_enabled = ?, tnt = ?, fly = ? WHERE region_id = (SELECT region_id FROM chunk_claims WHERE id = ?)";
        
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSettings);
             PreparedStatement pstmt = kcConn.prepareStatement(updateSettings)) {
            
            while (rs.next()) {
                int ucClaimId = rs.getInt("claim_id");
                Integer kcClaimId = claimIdMapping.get(ucClaimId);
                
                if (kcClaimId == null) {
                    continue;
                }
                
                boolean hostileMobSpawning = rs.getInt("hostile_mob_spawning") == 1;
                boolean fireSpread = rs.getInt("fire_spread") == 1;
                boolean mobGriefing = rs.getInt("mob_griefing") == 1;
                boolean leafDecay = rs.getInt("leaf_decay") == 1;
                boolean pvp = rs.getInt("pvp") == 1;
                boolean tnt = rs.getInt("tnt") == 1;
                boolean fly = rs.getInt("fly") == 1;
                
                // 更新同一区域的所有区块
                String updateAllInRegion = "UPDATE chunk_claims SET mob_spawning = ?, fire_spread = ?, mob_griefing = ?, " +
                    "leaf_decay = ?, pvp_enabled = ?, tnt = ?, fly = ? WHERE region_id = " +
                    "(SELECT region_id FROM chunk_claims WHERE id = " + kcClaimId + ")";
                
                try (PreparedStatement updatePstmt = kcConn.prepareStatement(updateAllInRegion)) {
                    updatePstmt.setBoolean(1, hostileMobSpawning);
                    updatePstmt.setBoolean(2, fireSpread);
                    updatePstmt.setBoolean(3, mobGriefing);
                    updatePstmt.setBoolean(4, leafDecay);
                    updatePstmt.setBoolean(5, pvp);
                    updatePstmt.setBoolean(6, tnt);
                    updatePstmt.setBoolean(7, fly);
                    updatePstmt.executeUpdate();
                }
            }
        }
    }
    
    /**
     * 迁移权限数据
     */
    private void migratePermissions(Connection ucConn, Connection kcConn, 
            Map<Integer, Integer> claimIdMapping, MigrationResult result, CommandSender sender) throws SQLException {
        
        sendMessage(sender, "§7正在迁移权限设置...");
        
        String selectPermissions = "SELECT * FROM " + ucTablePrefix + "permissions";
        
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectPermissions)) {
            
            while (rs.next()) {
                int ucClaimId = rs.getInt("claim_id");
                Integer kcClaimId = claimIdMapping.get(ucClaimId);
                
                if (kcClaimId == null) {
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
                
                // 将权限编码为整数（位掩码）
                // interact=1, break=2, place=4, mob_kill=8, redstone=16, doors=32, trading=64
                int permissions = 0;
                if (interact) permissions |= 1;
                if (breakBlock) permissions |= 2;
                if (place) permissions |= 4;
                if (mobKill) permissions |= 8;
                if (redstone) permissions |= 16;
                if (doors) permissions |= 32;
                if (trading) permissions |= 64;
                
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
    
    /**
     * 迁移能量电池位置
     */
    private void migratePowerCells(Connection ucConn, Connection kcConn, 
            Map<Integer, Integer> claimIdMapping, MigrationResult result, CommandSender sender) throws SQLException {
        
        sendMessage(sender, "§7正在迁移能量电池数据...");
        
        String selectClaims = "SELECT id, powercell_world, powercell_x, powercell_y, powercell_z, power, eco_bal " +
            "FROM " + ucTablePrefix + "claim WHERE powercell_world IS NOT NULL";
        String insertPowerCell = "INSERT INTO power_cells (claim_id, region_id, world, x, y, z, energy_time, economy_balance, last_update) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Statement stmt = ucConn.createStatement();
             ResultSet rs = stmt.executeQuery(selectClaims);
             PreparedStatement pstmt = kcConn.prepareStatement(insertPowerCell)) {
            
            while (rs.next()) {
                int ucClaimId = rs.getInt("id");
                Integer kcClaimId = claimIdMapping.get(ucClaimId);
                
                if (kcClaimId == null) {
                    continue;
                }
                
                String world = rs.getString("powercell_world");
                int x = rs.getInt("powercell_x");
                int y = rs.getInt("powercell_y");
                int z = rs.getInt("powercell_z");
                int power = rs.getInt("power");
                double ecoBal = rs.getDouble("eco_bal");
                
                // 获取 region_id
                int regionId = 0;
                String getRegionId = "SELECT region_id FROM chunk_claims WHERE id = ?";
                try (PreparedStatement regionStmt = kcConn.prepareStatement(getRegionId)) {
                    regionStmt.setInt(1, kcClaimId);
                    try (ResultSet regionRs = regionStmt.executeQuery()) {
                        if (regionRs.next()) {
                            regionId = regionRs.getInt("region_id");
                        }
                    }
                }
                
                try {
                    pstmt.setInt(1, kcClaimId);
                    pstmt.setInt(2, regionId);
                    pstmt.setString(3, world);
                    pstmt.setInt(4, x);
                    pstmt.setInt(5, y);
                    pstmt.setInt(6, z);
                    pstmt.setLong(7, power * 60L); // 转换为秒
                    pstmt.setDouble(8, ecoBal);
                    pstmt.setLong(9, System.currentTimeMillis());
                    pstmt.executeUpdate();
                    
                    result.incrementPowerCellsMigrated();
                } catch (SQLException e) {
                    result.addWarning("能量电池插入失败: claim " + kcClaimId + " - " + e.getMessage());
                }
            }
        }
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
    }
    
    private static class ChunkData {
        String world;
        int x, z;
        String regionId;
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
        private List<String> warnings = new ArrayList<>();
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public int getClaimsMigrated() { return claimsMigrated; }
        public void incrementClaimsMigrated() { claimsMigrated++; }
        public int getChunksMigrated() { return chunksMigrated; }
        public void incrementChunksMigrated() { chunksMigrated++; }
        public int getMembersMigrated() { return membersMigrated; }
        public void incrementMembersMigrated() { membersMigrated++; }
        public int getBansMigrated() { return bansMigrated; }
        public void incrementBansMigrated() { bansMigrated++; }
        public int getPowerCellsMigrated() { return powerCellsMigrated; }
        public void incrementPowerCellsMigrated() { powerCellsMigrated++; }
        public List<String> getWarnings() { return warnings; }
        public void addWarning(String warning) { warnings.add(warning); }
    }
}
