package org.kari.kariClaims.database;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.kari.kariClaims.models.ChunkClaim;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 区块领地数据访问对象
 */
public class ChunkClaimDAO {
    private final DatabaseManager databaseManager;

    public ChunkClaimDAO(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * 检查是否是 MySQL 或 MariaDB（它们使用相同的 SQL 语法）
     */
    private boolean isMySQLOrMariaDB() {
        DatabaseManager.DatabaseType type = databaseManager.getDatabaseType();
        return type == DatabaseManager.DatabaseType.MYSQL || type == DatabaseManager.DatabaseType.MARIADB;
    }

    /**
     * 创建数据库表
     */
    public void createTables() throws SQLException {
        // 区块领地表
        String createChunkClaimsTable = isMySQLOrMariaDB()
            ? "CREATE TABLE IF NOT EXISTS chunk_claims (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "owner VARCHAR(36) NOT NULL, " +
                "world VARCHAR(255) NOT NULL, " +
                "chunk_x INT NOT NULL, " +
                "chunk_z INT NOT NULL, " +
                "region_id INT DEFAULT 0, " +
                "chunk_name VARCHAR(255) DEFAULT NULL, " +
                "home_x DOUBLE DEFAULT NULL, " +
                "home_y DOUBLE DEFAULT NULL, " +
                "home_z DOUBLE DEFAULT NULL, " +
                "home_yaw FLOAT DEFAULT NULL, " +
                "home_pitch FLOAT DEFAULT NULL, " +
                "home_public BOOLEAN DEFAULT TRUE, " +
                "locked BOOLEAN DEFAULT FALSE, " +
                "energy_time BIGINT DEFAULT 0, " +
                "economy_balance DOUBLE DEFAULT 0, " +
                "pvp_enabled BOOLEAN DEFAULT FALSE, " +
                "mob_spawning BOOLEAN DEFAULT TRUE, " +
                "fire_spread BOOLEAN DEFAULT FALSE, " +
                "explosion BOOLEAN DEFAULT FALSE, " +
                "external_fluid_inflow BOOLEAN DEFAULT TRUE, " +
                "leaf_decay BOOLEAN DEFAULT TRUE, " +
                "entity_drop BOOLEAN DEFAULT TRUE, " +
                "water_flow BOOLEAN DEFAULT TRUE, " +
                "fly BOOLEAN DEFAULT FALSE, " +
                "mob_griefing BOOLEAN DEFAULT FALSE, " +
                "tnt BOOLEAN DEFAULT FALSE, " +
                "claimed_at BIGINT NOT NULL, " +
                "INDEX idx_world_chunk (world, chunk_x, chunk_z), " +
                "INDEX idx_owner (owner), " +
                "INDEX idx_region (region_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            : "CREATE TABLE IF NOT EXISTS chunk_claims (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "owner VARCHAR(36) NOT NULL, " +
                "world VARCHAR(255) NOT NULL, " +
                "chunk_x INTEGER NOT NULL, " +
                "chunk_z INTEGER NOT NULL, " +
                "region_id INTEGER DEFAULT 0, " +
                "chunk_name VARCHAR(255) DEFAULT NULL, " +
                "home_x REAL DEFAULT NULL, " +
                "home_y REAL DEFAULT NULL, " +
                "home_z REAL DEFAULT NULL, " +
                "home_yaw REAL DEFAULT NULL, " +
                "home_pitch REAL DEFAULT NULL, " +
                "home_public BOOLEAN DEFAULT 1, " +
                "locked BOOLEAN DEFAULT 0, " +
                "energy_time INTEGER DEFAULT 0, " +
                "economy_balance REAL DEFAULT 0, " +
                "pvp_enabled BOOLEAN DEFAULT 0, " +
                "mob_spawning BOOLEAN DEFAULT 1, " +
                "fire_spread BOOLEAN DEFAULT 0, " +
                "explosion BOOLEAN DEFAULT 0, " +
                "external_fluid_inflow BOOLEAN DEFAULT 1, " +
                "leaf_decay BOOLEAN DEFAULT 1, " +
                "entity_drop BOOLEAN DEFAULT 1, " +
                "water_flow BOOLEAN DEFAULT 1, " +
                "fly BOOLEAN DEFAULT 0, " +
                "mob_griefing BOOLEAN DEFAULT 0, " +
                "tnt BOOLEAN DEFAULT 0, " +
                "claimed_at INTEGER NOT NULL" +
                ")";

        // 领地区域表
        String createRegionsTable = isMySQLOrMariaDB()
            ? "CREATE TABLE IF NOT EXISTS claim_regions (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "owner VARCHAR(36) NOT NULL, " +
                "world VARCHAR(255) NOT NULL, " +
                "region_name VARCHAR(255) DEFAULT NULL, " +
                "default_home_x DOUBLE DEFAULT NULL, " +
                "default_home_y DOUBLE DEFAULT NULL, " +
                "default_home_z DOUBLE DEFAULT NULL, " +
                "default_home_yaw FLOAT DEFAULT NULL, " +
                "default_home_pitch FLOAT DEFAULT NULL, " +
                "locked BOOLEAN DEFAULT FALSE, " +
                "energy_time BIGINT DEFAULT 0, " +
                "economy_balance DOUBLE DEFAULT 0, " +
                "created_at BIGINT NOT NULL, " +
                "INDEX idx_owner (owner)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            : "CREATE TABLE IF NOT EXISTS claim_regions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "owner VARCHAR(36) NOT NULL, " +
                "world VARCHAR(255) NOT NULL, " +
                "region_name VARCHAR(255) DEFAULT NULL, " +
                "default_home_x REAL DEFAULT NULL, " +
                "default_home_y REAL DEFAULT NULL, " +
                "default_home_z REAL DEFAULT NULL, " +
                "default_home_yaw REAL DEFAULT NULL, " +
                "default_home_pitch REAL DEFAULT NULL, " +
                "locked BOOLEAN DEFAULT 0, " +
                "energy_time INTEGER DEFAULT 0, " +
                "economy_balance REAL DEFAULT 0, " +
                "created_at INTEGER NOT NULL" +
                ")";

        // 能量电池表
        String createPowerCellsTable = isMySQLOrMariaDB()
            ? "CREATE TABLE IF NOT EXISTS power_cells (" +
                "claim_id INT NOT NULL, " +
                "region_id INT DEFAULT NULL, " +
                "world VARCHAR(255) NOT NULL, " +
                "x INT NOT NULL, " +
                "y INT NOT NULL, " +
                "z INT NOT NULL, " +
                "energy_time BIGINT DEFAULT 0, " +
                "economy_balance DOUBLE DEFAULT 0, " +
                "last_update BIGINT NOT NULL, " +
                "PRIMARY KEY (claim_id), " +
                "INDEX idx_location (world, x, y, z)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            : "CREATE TABLE IF NOT EXISTS power_cells (" +
                "claim_id INTEGER NOT NULL, " +
                "region_id INTEGER DEFAULT NULL, " +
                "world VARCHAR(255) NOT NULL, " +
                "x INTEGER NOT NULL, " +
                "y INTEGER NOT NULL, " +
                "z INTEGER NOT NULL, " +
                "energy_time INTEGER DEFAULT 0, " +
                "economy_balance REAL DEFAULT 0, " +
                "last_update INTEGER NOT NULL, " +
                "PRIMARY KEY (claim_id)" +
                ")";

        // 封禁玩家表
        String createBannedTable = isMySQLOrMariaDB()
            ? "CREATE TABLE IF NOT EXISTS banned_players (" +
                "claim_id INT NOT NULL, " +
                "player_id VARCHAR(36) NOT NULL, " +
                "banned_at BIGINT NOT NULL, " +
                "PRIMARY KEY (claim_id, player_id), " +
                "INDEX idx_player (player_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            : "CREATE TABLE IF NOT EXISTS banned_players (" +
                "claim_id INTEGER NOT NULL, " +
                "player_id VARCHAR(36) NOT NULL, " +
                "banned_at INTEGER NOT NULL, " +
                "PRIMARY KEY (claim_id, player_id)" +
                ")";

        // 创建chunk_claim_members表
        String createMembersTable = isMySQLOrMariaDB()
            ? "CREATE TABLE IF NOT EXISTS chunk_claim_members (" +
                "claim_id INT NOT NULL, " +
                "player_id VARCHAR(36) NOT NULL, " +
                "role INT NOT NULL, " +
                "joined_at BIGINT NOT NULL, " +
                "PRIMARY KEY (claim_id, player_id), " +
                "FOREIGN KEY (claim_id) REFERENCES chunk_claims(id) ON DELETE CASCADE, " +
                "INDEX idx_player (player_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            : "CREATE TABLE IF NOT EXISTS chunk_claim_members (" +
                "claim_id INTEGER NOT NULL, " +
                "player_id VARCHAR(36) NOT NULL, " +
                "role INTEGER NOT NULL, " +
                "joined_at INTEGER NOT NULL, " +
                "PRIMARY KEY (claim_id, player_id), " +
                "FOREIGN KEY (claim_id) REFERENCES chunk_claims(id) ON DELETE CASCADE" +
                ")";

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createRegionsTable);
            stmt.execute(createChunkClaimsTable);
            stmt.execute(createPowerCellsTable);
            stmt.execute(createBannedTable);
            stmt.execute(createMembersTable);
            
            // 添加initial_time列（如果不存在）
            addInitialTimeColumnIfNotExists(conn);
            addExternalFluidColumnIfNotExists(conn);
            addRegionIdColumnIfNotExists(conn);
            addPermissionsColumnsIfNotExists(conn);
            addPowerCellHopperColumnIfNotExists(conn);
        }
    }

    /**
     * 异步创建区块领地
     */
    public CompletableFuture<ChunkClaim> createChunkClaimAsync(UUID owner, Chunk chunk) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO chunk_claims (owner, world, chunk_x, chunk_z, claimed_at, home_public) VALUES (?, ?, ?, ?, ?, ?)";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = isMySQLOrMariaDB()
                     ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                     : conn.prepareStatement(sql)) {
                
                stmt.setString(1, owner.toString());
                stmt.setString(2, chunk.getWorld().getName());
                stmt.setInt(3, chunk.getX());
                stmt.setInt(4, chunk.getZ());
                stmt.setLong(5, System.currentTimeMillis());
                stmt.setBoolean(6, true); // 默认开启公开传送
                
                stmt.executeUpdate();
                
                int id;
                if (isMySQLOrMariaDB()) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            id = rs.getInt(1);
                        } else {
                            throw new SQLException("无法获取生成的ID");
                        }
                    }
                } else {
                    try (Statement queryStmt = conn.createStatement();
                         ResultSet rs = queryStmt.executeQuery("SELECT last_insert_rowid()")) {
                        if (rs.next()) {
                            id = rs.getInt(1);
                        } else {
                            throw new SQLException("无法获取生成的ID");
                        }
                    }
                }
                
                return new ChunkClaim(id, owner, chunk.getWorld().getName(), 
                    chunk.getX(), chunk.getZ(), System.currentTimeMillis());
            } catch (SQLException e) {
                throw new RuntimeException("创建区块领地失败", e);
            }
        });
    }

    /**
     * 异步查找区块领地
     */
    public CompletableFuture<Optional<ChunkClaim>> findChunkClaimAsync(String world, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM chunk_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ? LIMIT 1";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, world);
                stmt.setInt(2, chunkX);
                stmt.setInt(3, chunkZ);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToChunkClaim(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("查询区块领地失败", e);
            }
        });
    }

    /**
     * 根据ID异步查找区块领地
     */
    public CompletableFuture<Optional<ChunkClaim>> findChunkClaimByIdAsync(int claimId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM chunk_claims WHERE id = ? LIMIT 1";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, claimId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToChunkClaim(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("根据ID查询区块领地失败", e);
            }
        });
    }

    /**
     * 异步获取玩家的所有区块领地
     */
    public CompletableFuture<List<ChunkClaim>> getPlayerChunkClaimsAsync(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM chunk_claims WHERE owner = ?";
            List<ChunkClaim> claims = new ArrayList<>();
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        claims.add(mapResultSetToChunkClaim(rs));
                    }
                }
                return claims;
            } catch (SQLException e) {
                throw new RuntimeException("查询玩家区块领地失败", e);
            }
        });
    }

    /**
     * 异步获取作为成员加入的领地
     */
    public CompletableFuture<List<ChunkClaim>> getMemberChunkClaimsAsync(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT cc.* FROM chunk_claims cc JOIN chunk_claim_members m ON m.claim_id = cc.id WHERE m.player_id = ?";
            List<ChunkClaim> claims = new ArrayList<>();
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        claims.add(mapResultSetToChunkClaim(rs));
                    }
                }
                return claims;
            } catch (SQLException e) {
                throw new RuntimeException("查询成员领地失败", e);
            }
        });
    }
    
    /**
     * 检查玩家是否是领地成员（同步方法，用于快速检查）
     */
    public boolean isClaimMember(int claimId, UUID playerId) {
        String sql = "SELECT 1 FROM chunk_claim_members WHERE claim_id = ? AND player_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, claimId);
            stmt.setString(2, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 异步获取所有区块领地
     */
    public CompletableFuture<List<ChunkClaim>> getAllChunkClaimsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM chunk_claims";
            List<ChunkClaim> claims = new ArrayList<>();
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    claims.add(mapResultSetToChunkClaim(rs));
                }
                return claims;
            } catch (SQLException e) {
                throw new RuntimeException("查询所有区块领地失败", e);
            }
        });
    }
    
    /**
     * 异步获取所有领地区域
     */
    public CompletableFuture<List<org.kari.kariClaims.models.ClaimRegion>> getAllRegionsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM claim_regions";
            List<org.kari.kariClaims.models.ClaimRegion> regions = new ArrayList<>();
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    org.kari.kariClaims.models.ClaimRegion region = new org.kari.kariClaims.models.ClaimRegion(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("owner")),
                        rs.getString("world")
                    );
                    region.setRegionName(rs.getString("region_name"));
                    region.setLocked(rs.getBoolean("locked"));
                    region.setEnergyTime(rs.getLong("energy_time"));
                    region.setEconomyBalance(rs.getDouble("economy_balance"));
                    regions.add(region);
                }
                return regions;
            } catch (SQLException e) {
                throw new RuntimeException("查询所有领地区域失败", e);
            }
        });
    }

    /**
     * 异步创建领地区域
     */
    public CompletableFuture<org.kari.kariClaims.models.ClaimRegion> createRegionAsync(UUID owner, String world) {
        return createRegionAsync(owner, world, null);
    }
    
    /**
     * 异步创建领地区域（带玩家名）
     */
    public CompletableFuture<org.kari.kariClaims.models.ClaimRegion> createRegionAsync(UUID owner, String world, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            // 生成唯一的领地名称
            String regionName = generateUniqueRegionName(owner, playerName);
            
            String sql = "INSERT INTO claim_regions (owner, world, region_name, created_at) VALUES (?, ?, ?, ?)";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = isMySQLOrMariaDB()
                     ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                     : conn.prepareStatement(sql)) {
                
                stmt.setString(1, owner.toString());
                stmt.setString(2, world);
                stmt.setString(3, regionName);
                stmt.setLong(4, System.currentTimeMillis());
                
                stmt.executeUpdate();
                
                int id;
                if (isMySQLOrMariaDB()) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            id = rs.getInt(1);
                        } else {
                            throw new SQLException("无法获取生成的区域ID");
                        }
                    }
                } else {
                    try (Statement queryStmt = conn.createStatement();
                         ResultSet rs = queryStmt.executeQuery("SELECT last_insert_rowid()")) {
                        if (rs.next()) {
                            id = rs.getInt(1);
                        } else {
                            throw new SQLException("无法获取生成的区域ID");
                        }
                    }
                }
                
                org.kari.kariClaims.models.ClaimRegion region = new org.kari.kariClaims.models.ClaimRegion(id, owner, world);
                region.setRegionName(regionName);
                return region;
            } catch (SQLException e) {
                throw new RuntimeException("创建领地区域失败", e);
            }
        });
    }
    
    /**
     * 生成唯一的领地名称
     */
    private String generateUniqueRegionName(UUID owner, String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(owner);
            playerName = offlinePlayer.getName();
            if (playerName == null) {
                playerName = owner.toString().substring(0, 8);
            }
        }
        
        String baseName = playerName + "的领地";
        String regionName = baseName;
        int suffix = 1;
        
        while (isRegionNameExists(regionName)) {
            suffix++;
            regionName = baseName + suffix;
        }
        
        return regionName;
    }
    
    /**
     * 检查领地名称是否已存在
     */
    public boolean isRegionNameExists(String regionName) {
        String sql = "SELECT COUNT(*) FROM claim_regions WHERE region_name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, regionName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * 根据名称查找领地区域
     */
    public CompletableFuture<Optional<org.kari.kariClaims.models.ClaimRegion>> findRegionByNameAsync(String regionName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM claim_regions WHERE region_name = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, regionName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToRegion(rs));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return Optional.empty();
        });
    }
    
    private org.kari.kariClaims.models.ClaimRegion mapResultSetToRegion(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        UUID owner = UUID.fromString(rs.getString("owner"));
        String world = rs.getString("world");
        org.kari.kariClaims.models.ClaimRegion region = new org.kari.kariClaims.models.ClaimRegion(id, owner, world);
        
        String name = rs.getString("region_name");
        if (name != null) {
            region.setRegionName(name);
        }
        
        return region;
    }

    /**
     * 异步获取玩家的所有领地区域
     */
    public CompletableFuture<List<org.kari.kariClaims.models.ClaimRegion>> getPlayerRegionsAsync(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            List<org.kari.kariClaims.models.ClaimRegion> regions = new ArrayList<>();
            String sql = "SELECT * FROM claim_regions WHERE owner = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, owner.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        regions.add(mapResultSetToRegion(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("获取玩家领地区域失败", e);
            }
            return regions;
        });
    }

    /**
     * 异步更新区域名称
     */
    public CompletableFuture<Void> updateRegionNameAsync(int regionId, String newName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE claim_regions SET region_name = ? WHERE id = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newName);
                stmt.setInt(2, regionId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("更新区域名称失败", e);
            }
        });
    }

    /**
     * 异步删除领地区域
     */
    public CompletableFuture<Void> deleteRegionAsync(int regionId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM claim_regions WHERE id = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, regionId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("删除领地区域失败", e);
            }
        });
    }

    /**
     * 异步删除玩家的所有领地区域
     */
    public CompletableFuture<Void> deletePlayerRegionsAsync(UUID owner) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM claim_regions WHERE owner = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, owner.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("删除玩家领地区域失败", e);
            }
        });
    }

    /**
     * 异步删除区块领地
     */
    public CompletableFuture<Void> deleteChunkClaimAsync(int claimId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM chunk_claims WHERE id = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, claimId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("删除区块领地失败", e);
            }
        });
    }

    /**
     * 同步更新区块领地
     */
    public void updateChunkClaimSync(ChunkClaim claim) {
        String sql = "UPDATE chunk_claims SET chunk_name = ?, home_x = ?, home_y = ?, home_z = ?, " +
            "home_yaw = ?, home_pitch = ?, home_public = ?, locked = ?, energy_time = ?, economy_balance = ?, " +
            "initial_time = ?, pvp_enabled = ?, mob_spawning = ?, fire_spread = ?, explosion = ?, external_fluid_inflow = ?, leaf_decay = ?, " +
            "entity_drop = ?, water_flow = ?, mob_griefing = ?, tnt = ?, region_id = ?, " +
            "visitor_permissions = ?, member_permissions = ?, power_cell_hopper_interaction = ? WHERE id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, claim.getChunkName());
            
            Location home = claim.getHomeLocation();
            if (home != null) {
                stmt.setDouble(2, home.getX());
                stmt.setDouble(3, home.getY());
                stmt.setDouble(4, home.getZ());
                stmt.setFloat(5, home.getYaw());
                stmt.setFloat(6, home.getPitch());
            } else {
                stmt.setNull(2, Types.DOUBLE);
                stmt.setNull(3, Types.DOUBLE);
                stmt.setNull(4, Types.DOUBLE);
                stmt.setNull(5, Types.FLOAT);
                stmt.setNull(6, Types.FLOAT);
            }
            
            stmt.setBoolean(7, claim.isHomePublic());
            stmt.setBoolean(8, claim.isLocked());
            stmt.setLong(9, claim.getEnergyTime());
            stmt.setDouble(10, claim.getEconomyBalance());
            stmt.setLong(11, claim.getInitialTime());
            stmt.setBoolean(12, claim.isPvpEnabled());
            stmt.setBoolean(13, claim.isMobSpawning());
            stmt.setBoolean(14, claim.isFireSpread());
            stmt.setBoolean(15, claim.isExplosion());
            stmt.setBoolean(16, claim.isExternalFluidInflow());
            stmt.setBoolean(17, claim.isLeafDecay());
            stmt.setBoolean(18, claim.isEntityDrop());
            stmt.setBoolean(19, claim.isWaterFlow());
            stmt.setBoolean(20, claim.isMobGriefing());
            stmt.setBoolean(21, claim.isTnt());
            stmt.setInt(22, claim.getRegionId());
            stmt.setInt(23, claim.getVisitorPermissions());
            stmt.setInt(24, claim.getMemberPermissions());
            stmt.setBoolean(25, claim.isPowerCellHopperInteraction());
            stmt.setInt(26, claim.getId());
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("同步更新区块领地失败", e);
        }
    }

    /**
     * 异步更新区块领地
     */
    public CompletableFuture<Void> updateChunkClaimAsync(ChunkClaim claim) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE chunk_claims SET chunk_name = ?, home_x = ?, home_y = ?, home_z = ?, " +
                "home_yaw = ?, home_pitch = ?, home_public = ?, locked = ?, energy_time = ?, economy_balance = ?, " +
                "initial_time = ?, pvp_enabled = ?, mob_spawning = ?, fire_spread = ?, explosion = ?, external_fluid_inflow = ?, leaf_decay = ?, " +
                "entity_drop = ?, water_flow = ?, mob_griefing = ?, tnt = ?, region_id = ?, " +
                "visitor_permissions = ?, member_permissions = ?, power_cell_hopper_interaction = ? WHERE id = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, claim.getChunkName());
                
                Location home = claim.getHomeLocation();
                if (home != null) {
                    stmt.setDouble(2, home.getX());
                    stmt.setDouble(3, home.getY());
                    stmt.setDouble(4, home.getZ());
                    stmt.setFloat(5, home.getYaw());
                    stmt.setFloat(6, home.getPitch());
                } else {
                    stmt.setNull(2, Types.DOUBLE);
                    stmt.setNull(3, Types.DOUBLE);
                    stmt.setNull(4, Types.DOUBLE);
                    stmt.setNull(5, Types.FLOAT);
                    stmt.setNull(6, Types.FLOAT);
                }
                
                stmt.setBoolean(7, claim.isHomePublic());
                stmt.setBoolean(8, claim.isLocked());
                stmt.setLong(9, claim.getEnergyTime());
                stmt.setDouble(10, claim.getEconomyBalance());
                stmt.setLong(11, claim.getInitialTime());
                stmt.setBoolean(12, claim.isPvpEnabled());
                stmt.setBoolean(13, claim.isMobSpawning());
                stmt.setBoolean(14, claim.isFireSpread());
                stmt.setBoolean(15, claim.isExplosion());
                stmt.setBoolean(16, claim.isExternalFluidInflow());
                stmt.setBoolean(17, claim.isLeafDecay());
                stmt.setBoolean(18, claim.isEntityDrop());
                stmt.setBoolean(19, claim.isWaterFlow());
                stmt.setBoolean(20, claim.isMobGriefing());
                stmt.setBoolean(21, claim.isTnt());
                stmt.setInt(22, claim.getRegionId());
                stmt.setInt(23, claim.getVisitorPermissions());
                stmt.setInt(24, claim.getMemberPermissions());
                stmt.setBoolean(25, claim.isPowerCellHopperInteraction());
                stmt.setInt(26, claim.getId());
                
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("更新区块领地失败", e);
            }
        });
    }

    /**
     * 扣除所有领地的能源时间和经济余额（包括initial_time）
     */
    public CompletableFuture<Void> drainEnergyAsync(long seconds, double pricePerSecond) {
        if (seconds <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            // 更新逻辑：优先消耗energy_time，然后economy_balance，最后initial_time
            String sql = "UPDATE chunk_claims SET " +
                "energy_time = CASE WHEN energy_time > ? THEN energy_time - ? ELSE 0 END, " +
                "economy_balance = CASE " +
                    "WHEN energy_time > ? THEN economy_balance " +
                    "WHEN energy_time + (economy_balance / ?) > ? THEN economy_balance - (? - energy_time) * ? " +
                    "ELSE 0 END, " +
                "initial_time = CASE " +
                    "WHEN energy_time > ? THEN initial_time " +
                    "WHEN energy_time + (economy_balance / ?) > ? THEN initial_time " +
                    "WHEN energy_time + (economy_balance / ?) + initial_time > ? THEN initial_time - (? - energy_time - (economy_balance / ?)) " +
                    "ELSE 0 END " +
                "WHERE energy_time > 0 OR economy_balance > 0 OR initial_time > 0";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, seconds);
                stmt.setLong(2, seconds);
                stmt.setLong(3, seconds);
                stmt.setDouble(4, pricePerSecond);
                stmt.setLong(5, seconds);
                stmt.setLong(6, seconds);
                stmt.setDouble(7, pricePerSecond);
                stmt.setLong(8, seconds);
                stmt.setDouble(9, pricePerSecond);
                stmt.setLong(10, seconds);
                stmt.setDouble(11, pricePerSecond);
                stmt.setLong(12, seconds);
                stmt.setLong(13, seconds);
                stmt.setDouble(14, pricePerSecond);

                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("扣除能量失败", e);
            }
        });
    }

    /**
     * 异步获取公开home的领地
     */
    public CompletableFuture<List<ChunkClaim>> getPublicClaimsAsync() {
        return CompletableFuture.supplyAsync(this::getPublicClaimsSync);
    }

    /**
    * 获取公开home的领地（同步，用于tab补全）
    */
    public List<ChunkClaim> getPublicClaimsSync() {
        String sql = "SELECT * FROM chunk_claims WHERE home_public = 1 AND home_x IS NOT NULL";
        List<ChunkClaim> claims = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                claims.add(mapResultSetToChunkClaim(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询公开home失败", e);
        }
        return claims;
    }

    /**
     * 扣除没有能量电池的领地的能源时间和经济余额（包括initial_time）
     */
    public CompletableFuture<Void> drainEnergyWithoutPowerCellsAsync(long seconds, double pricePerSecond) {
        if (seconds <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE chunk_claims SET " +
                "energy_time = CASE WHEN energy_time > ? THEN energy_time - ? ELSE 0 END, " +
                "economy_balance = CASE " +
                    "WHEN energy_time > ? THEN economy_balance " +
                    "WHEN energy_time + (economy_balance / ?) > ? THEN economy_balance - (? - energy_time) * ? " +
                    "ELSE 0 END, " +
                "initial_time = CASE " +
                    "WHEN energy_time > ? THEN initial_time " +
                    "WHEN energy_time + (economy_balance / ?) > ? THEN initial_time " +
                    "WHEN energy_time + (economy_balance / ?) + initial_time > ? THEN initial_time - (? - energy_time - (economy_balance / ?)) " +
                    "ELSE 0 END " +
                "WHERE (energy_time > 0 OR economy_balance > 0 OR initial_time > 0) " +
                "AND NOT EXISTS (SELECT 1 FROM power_cells pc WHERE pc.claim_id = chunk_claims.id " +
                "OR (chunk_claims.region_id > 0 AND pc.region_id = chunk_claims.region_id))";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, seconds);
                stmt.setLong(2, seconds);
                stmt.setLong(3, seconds);
                stmt.setDouble(4, pricePerSecond);
                stmt.setLong(5, seconds);
                stmt.setLong(6, seconds);
                stmt.setDouble(7, pricePerSecond);
                stmt.setLong(8, seconds);
                stmt.setDouble(9, pricePerSecond);
                stmt.setLong(10, seconds);
                stmt.setDouble(11, pricePerSecond);
                stmt.setLong(12, seconds);
                stmt.setLong(13, seconds);
                stmt.setDouble(14, pricePerSecond);

                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("扣除无能量电池领地的能量失败", e);
            }
        });
    }

    /**
     * 获取所有没有能量电池的领地
     */
    public CompletableFuture<List<ChunkClaim>> getClaimsWithoutPowerCellAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT cc.* FROM chunk_claims cc " +
                "LEFT JOIN power_cells pc ON pc.claim_id = cc.id " +
                "WHERE pc.claim_id IS NULL " +
                "AND NOT EXISTS (SELECT 1 FROM power_cells pc2 WHERE pc2.region_id = cc.region_id AND cc.region_id > 0)";
            List<ChunkClaim> claims = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    claims.add(mapResultSetToChunkClaim(rs));
                }
                return claims;
            } catch (SQLException e) {
                throw new RuntimeException("查询没有能量电池的领地失败", e);
            }
        });
    }

    /**
     * 将 ResultSet 映射为 ChunkClaim 对象
     */
    private ChunkClaim mapResultSetToChunkClaim(ResultSet rs) throws SQLException {
        ChunkClaim claim = new ChunkClaim(
            rs.getInt("id"),
            UUID.fromString(rs.getString("owner")),
            rs.getString("world"),
            rs.getInt("chunk_x"),
            rs.getInt("chunk_z"),
            rs.getLong("claimed_at")
        );
        
        claim.setChunkName(rs.getString("chunk_name"));
        claim.setLocked(rs.getBoolean("locked"));
        claim.setEnergyTime(rs.getLong("energy_time"));
        claim.setEconomyBalance(rs.getDouble("economy_balance"));
        claim.setHomePublic(rs.getBoolean("home_public"));
        try {
            claim.setRegionId(rs.getInt("region_id"));
        } catch (SQLException ignored) {}
        
        // 加载initial_time（如果字段存在）
        try {
            claim.setInitialTime(rs.getLong("initial_time"));
        } catch (SQLException e) {
            // 如果字段不存在（旧数据库），默认为0
            claim.setInitialTime(0);
        }
        
        // 加载设置（如果字段存在）
        try {
            claim.setPvpEnabled(rs.getBoolean("pvp_enabled"));
            claim.setMobSpawning(rs.getBoolean("mob_spawning"));
            claim.setFireSpread(rs.getBoolean("fire_spread"));
            claim.setExplosion(rs.getBoolean("explosion"));
            claim.setLeafDecay(rs.getBoolean("leaf_decay"));
            claim.setEntityDrop(rs.getBoolean("entity_drop"));
            claim.setWaterFlow(rs.getBoolean("water_flow"));
            claim.setMobGriefing(rs.getBoolean("mob_griefing"));
            claim.setTnt(rs.getBoolean("tnt"));
            claim.setExternalFluidInflow(rs.getBoolean("external_fluid_inflow"));
            
            // Try to get power_cell_hopper_interaction column, if it exists
            try {
                claim.setPowerCellHopperInteraction(rs.getBoolean("power_cell_hopper_interaction"));
            } catch (SQLException ignored) {}
            
            // Try to get permission columns, if they exist
            try {
                claim.setVisitorPermissions(rs.getInt("visitor_permissions"));
            } catch (SQLException ignored) {}
            
            try {
                claim.setMemberPermissions(rs.getInt("member_permissions"));
            } catch (SQLException ignored) {
                claim.setMemberPermissions(127);
            }
        } catch (SQLException e) {
            // If fields don't exist (very old database), use defaults
        }
        
        // 设置home位置
        double homeX = rs.getDouble("home_x");
        if (!rs.wasNull()) {
            double homeY = rs.getDouble("home_y");
            double homeZ = rs.getDouble("home_z");
            float yaw = rs.getFloat("home_yaw");
            float pitch = rs.getFloat("home_pitch");
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(claim.getWorld());
            if (world != null) {
                claim.setHomeLocation(new org.bukkit.Location(world, homeX, homeY, homeZ, yaw, pitch));
            }
        }
        
        return claim;
    }
    
    /**
     * 添加initial_time列（如果不存在）
     */
    private void addInitialTimeColumnIfNotExists(Connection conn) {
        try {
            String checkColumnSql = isMySQLOrMariaDB()
                ? "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chunk_claims' AND COLUMN_NAME = 'initial_time'"
                : "SELECT COUNT(*) FROM pragma_table_info('chunk_claims') WHERE name = 'initial_time'";
            
            try (PreparedStatement checkStmt = conn.prepareStatement(checkColumnSql);
                 ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    // 列不存在，添加它
                    String alterSql = isMySQLOrMariaDB()
                        ? "ALTER TABLE chunk_claims ADD COLUMN initial_time BIGINT DEFAULT 0"
                        : "ALTER TABLE chunk_claims ADD COLUMN initial_time INTEGER DEFAULT 0";
                    
                    try (Statement alterStmt = conn.createStatement()) {
                        alterStmt.execute(alterSql);
                    }
                }
            }
        } catch (SQLException e) {
            // 数据库迁移错误
        }
    }

    /**
     * 添加external_fluid_inflow列（如果不存在）
     */
    private void addExternalFluidColumnIfNotExists(Connection conn) {
        try {
            String checkColumnSql = isMySQLOrMariaDB()
                ? "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chunk_claims' AND COLUMN_NAME = 'external_fluid_inflow'"
                : "SELECT COUNT(*) FROM pragma_table_info('chunk_claims') WHERE name = 'external_fluid_inflow'";
            
            try (PreparedStatement checkStmt = conn.prepareStatement(checkColumnSql);
                 ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    String alterSql = isMySQLOrMariaDB()
                        ? "ALTER TABLE chunk_claims ADD COLUMN external_fluid_inflow BOOLEAN DEFAULT TRUE"
                        : "ALTER TABLE chunk_claims ADD COLUMN external_fluid_inflow BOOLEAN DEFAULT 1";
                    
                    try (Statement alterStmt = conn.createStatement()) {
                        alterStmt.execute(alterSql);
                    }
                }
            }
        } catch (SQLException e) {
            // 数据库迁移错误
        }
    }

    /**
     * 添加region_id列（如果不存在）
     */
    private void addRegionIdColumnIfNotExists(Connection conn) {
        try {
            String checkColumnSql = isMySQLOrMariaDB()
                ? "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chunk_claims' AND COLUMN_NAME = 'region_id'"
                : "SELECT COUNT(*) FROM pragma_table_info('chunk_claims') WHERE name = 'region_id'";
            
            try (PreparedStatement checkStmt = conn.prepareStatement(checkColumnSql);
                 ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    String alterSql = isMySQLOrMariaDB()
                        ? "ALTER TABLE chunk_claims ADD COLUMN region_id INT DEFAULT 0"
                        : "ALTER TABLE chunk_claims ADD COLUMN region_id INTEGER DEFAULT 0";
                    
                    try (Statement alterStmt = conn.createStatement()) {
                        alterStmt.execute(alterSql);
                    }
                }
            }
        } catch (SQLException e) {
            // 数据库迁移错误
        }
    }

    /**
     * 添加权限设置列（如果不存在）
     */
    private void addPermissionsColumnsIfNotExists(Connection conn) {
        try {
            // 检查 visitor_permissions 列
            String checkVisitorSql = isMySQLOrMariaDB()
                ? "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chunk_claims' AND COLUMN_NAME = 'visitor_permissions'"
                : "SELECT COUNT(*) FROM pragma_table_info('chunk_claims') WHERE name = 'visitor_permissions'";
            
            try (PreparedStatement checkStmt = conn.prepareStatement(checkVisitorSql);
                 ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    String alterSql = isMySQLOrMariaDB()
                        ? "ALTER TABLE chunk_claims ADD COLUMN visitor_permissions INT DEFAULT 0"
                        : "ALTER TABLE chunk_claims ADD COLUMN visitor_permissions INTEGER DEFAULT 0";
                    try (Statement alterStmt = conn.createStatement()) {
                        alterStmt.execute(alterSql);
                    }
                }
            }
            
            // 检查 member_permissions 列
            String checkMemberSql = isMySQLOrMariaDB()
                ? "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chunk_claims' AND COLUMN_NAME = 'member_permissions'"
                : "SELECT COUNT(*) FROM pragma_table_info('chunk_claims') WHERE name = 'member_permissions'";
            
            try (PreparedStatement checkStmt = conn.prepareStatement(checkMemberSql);
                 ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    String alterSql = isMySQLOrMariaDB()
                        ? "ALTER TABLE chunk_claims ADD COLUMN member_permissions INT DEFAULT 127"
                        : "ALTER TABLE chunk_claims ADD COLUMN member_permissions INTEGER DEFAULT 127";
                    try (Statement alterStmt = conn.createStatement()) {
                        alterStmt.execute(alterSql);
                    }
                }
            }
        } catch (SQLException e) {
            // 数据库迁移错误
        }
    }

    /**
     * 添加power_cell_hopper_interaction列（如果不存在）
     */
    private void addPowerCellHopperColumnIfNotExists(Connection conn) {
        try {
            String checkColumnSql = isMySQLOrMariaDB()
                ? "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chunk_claims' AND COLUMN_NAME = 'power_cell_hopper_interaction'"
                : "SELECT COUNT(*) FROM pragma_table_info('chunk_claims') WHERE name = 'power_cell_hopper_interaction'";
            
            try (PreparedStatement checkStmt = conn.prepareStatement(checkColumnSql);
                 ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    String alterSql = isMySQLOrMariaDB()
                        ? "ALTER TABLE chunk_claims ADD COLUMN power_cell_hopper_interaction BOOLEAN DEFAULT FALSE"
                        : "ALTER TABLE chunk_claims ADD COLUMN power_cell_hopper_interaction BOOLEAN DEFAULT 0";
                    try (Statement alterStmt = conn.createStatement()) {
                        alterStmt.execute(alterSql);
                    }
                }
            }
        } catch (SQLException e) {
            // 数据库迁移错误
        }
    }

    /**
     * 异步保存能量电池
     */
    public CompletableFuture<Void> savePowerCellAsync(int claimId, org.bukkit.Location location, long energyTime, double economyBalance) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO power_cells (claim_id, world, x, y, z, energy_time, economy_balance, last_update) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            
            if (isMySQLOrMariaDB()) {
                sql = "INSERT INTO power_cells (claim_id, world, x, y, z, energy_time, economy_balance, last_update) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                      "ON DUPLICATE KEY UPDATE world = ?, x = ?, y = ?, z = ?, energy_time = ?, economy_balance = ?, last_update = ?";
            }
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, claimId);
                stmt.setString(2, location.getWorld().getName());
                stmt.setInt(3, location.getBlockX());
                stmt.setInt(4, location.getBlockY());
                stmt.setInt(5, location.getBlockZ());
                stmt.setLong(6, energyTime);
                stmt.setDouble(7, economyBalance);
                stmt.setLong(8, System.currentTimeMillis());
                
                if (isMySQLOrMariaDB()) {
                    stmt.setString(9, location.getWorld().getName());
                    stmt.setInt(10, location.getBlockX());
                    stmt.setInt(11, location.getBlockY());
                    stmt.setInt(12, location.getBlockZ());
                    stmt.setLong(13, energyTime);
                    stmt.setDouble(14, economyBalance);
                    stmt.setLong(15, System.currentTimeMillis());
                }
                
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("保存能量电池失败", e);
            }
        });
    }

    /**
     * 异步获取能量电池位置（先按claim_id查找，再按region_id查找）
     */
    public CompletableFuture<org.bukkit.Location> getPowerCellLocationAsync(int claimId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                // 先按 claim_id 查找
                String sql1 = "SELECT * FROM power_cells WHERE claim_id = ? LIMIT 1";
                try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                    stmt.setInt(1, claimId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return extractLocationFromResultSet(rs);
                        }
                    }
                }
                
                // 如果没找到，获取该 claim 的 region_id，再按 region_id 查找
                String getRegionSql = "SELECT region_id FROM chunk_claims WHERE id = ?";
                int regionId = 0;
                try (PreparedStatement stmt = conn.prepareStatement(getRegionSql)) {
                    stmt.setInt(1, claimId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            regionId = rs.getInt("region_id");
                        }
                    }
                }
                
                if (regionId > 0) {
                    String sql2 = "SELECT * FROM power_cells WHERE region_id = ? LIMIT 1";
                    try (PreparedStatement stmt = conn.prepareStatement(sql2)) {
                        stmt.setInt(1, regionId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                return extractLocationFromResultSet(rs);
                            }
                        }
                    }
                }
                
                return null;
            } catch (SQLException e) {
                throw new RuntimeException("获取能量电池位置失败", e);
            }
        });
    }
    
    /**
     * 从ResultSet提取Location
     */
    private org.bukkit.Location extractLocationFromResultSet(ResultSet rs) throws SQLException {
        String worldName = rs.getString("world");
        int x = rs.getInt("x");
        int y = rs.getInt("y");
        int z = rs.getInt("z");
        
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world != null) {
            return new org.bukkit.Location(world, x, y, z);
        }
        return null;
    }

    /**
     * 异步删除能量电池（按claim_id或region_id删除）
     */
    public CompletableFuture<Void> deletePowerCellAsync(int claimId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                // 先尝试按 claim_id 删除
                String sql1 = "DELETE FROM power_cells WHERE claim_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                    stmt.setInt(1, claimId);
                    int deleted = stmt.executeUpdate();
                    if (deleted > 0) {
                        return;
                    }
                }
                
                // 如果没有删除任何记录，获取 region_id 再尝试按 region_id 删除
                String getRegionSql = "SELECT region_id FROM chunk_claims WHERE id = ?";
                int regionId = 0;
                try (PreparedStatement stmt = conn.prepareStatement(getRegionSql)) {
                    stmt.setInt(1, claimId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            regionId = rs.getInt("region_id");
                        }
                    }
                }
                
                if (regionId > 0) {
                    String sql2 = "DELETE FROM power_cells WHERE region_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql2)) {
                        stmt.setInt(1, regionId);
                        stmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("删除能量电池失败", e);
            }
        });
    }

    /**
     * 异步获取所有能量电池位置（用于启动时加载缓存）
     */
    public CompletableFuture<Map<Integer, org.bukkit.Location>> getAllPowerCellLocationsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT claim_id, world, x, y, z FROM power_cells";
            Map<Integer, org.bukkit.Location> locations = new HashMap<>();
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    int claimId = rs.getInt("claim_id");
                    String worldName = rs.getString("world");
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    
                    org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                    if (world != null) {
                        locations.put(claimId, new org.bukkit.Location(world, x, y, z));
                    }
                }
                
                return locations;
            } catch (SQLException e) {
                throw new RuntimeException("获取所有能量电池位置失败", e);
            }
        });
    }
    
    /**
     * 异步添加成员到区块领地
     */
    public CompletableFuture<Void> addChunkClaimMemberAsync(int claimId, UUID playerId, int role) {
        return CompletableFuture.runAsync(() -> {
            String sql = isMySQLOrMariaDB()
                ? "INSERT INTO chunk_claim_members (claim_id, player_id, role, joined_at) VALUES (?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE role = ?, joined_at = ?"
                : "INSERT OR REPLACE INTO chunk_claim_members (claim_id, player_id, role, joined_at) VALUES (?, ?, ?, ?)";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, claimId);
                stmt.setString(2, playerId.toString());
                stmt.setInt(3, role);
                stmt.setLong(4, System.currentTimeMillis());
                
                if (isMySQLOrMariaDB()) {
                    stmt.setInt(5, role);
                    stmt.setLong(6, System.currentTimeMillis());
                }
                
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("添加成员失败", e);
            }
        });
    }
    
    /**
     * 异步获取所有领地成员（用于缓存）
     */
    public CompletableFuture<List<org.kari.kariClaims.models.ClaimMember>> getAllClaimMembersAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM chunk_claim_members";
            List<org.kari.kariClaims.models.ClaimMember> members = new ArrayList<>();
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    members.add(new org.kari.kariClaims.models.ClaimMember(
                        rs.getInt("claim_id"),
                        UUID.fromString(rs.getString("player_id")),
                        org.kari.kariClaims.models.ClaimMember.MemberRole.fromId(rs.getInt("role")),
                        rs.getLong("joined_at")
                    ));
                }
                return members;
            } catch (SQLException e) {
                throw new RuntimeException("获取所有成员失败", e);
            }
        });
    }

    /**
     * 异步移除成员
     */
    public CompletableFuture<Void> removeChunkClaimMemberAsync(int claimId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM chunk_claim_members WHERE claim_id = ? AND player_id = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, claimId);
                stmt.setString(2, playerId.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("移除成员失败", e);
            }
        });
    }
    
    /**
     * 异步获取领地的所有成员
     */
    public CompletableFuture<List<org.kari.kariClaims.models.ClaimMember>> getChunkClaimMembersAsync(int claimId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM chunk_claim_members WHERE claim_id = ?";
            List<org.kari.kariClaims.models.ClaimMember> members = new ArrayList<>();
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, claimId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        members.add(new org.kari.kariClaims.models.ClaimMember(
                            rs.getInt("claim_id"),
                            UUID.fromString(rs.getString("player_id")),
                            org.kari.kariClaims.models.ClaimMember.MemberRole.fromId(rs.getInt("role")),
                            rs.getLong("joined_at")
                        ));
                    }
                }
                return members;
            } catch (SQLException e) {
                throw new RuntimeException("查询成员失败", e);
            }
        });
    }
    
    /**
     * 异步检查玩家是否是成员
     */
    public CompletableFuture<Optional<org.kari.kariClaims.models.ClaimMember>> getChunkClaimMemberAsync(int claimId, UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM chunk_claim_members WHERE claim_id = ? AND player_id = ? LIMIT 1";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, claimId);
                stmt.setString(2, playerId.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new org.kari.kariClaims.models.ClaimMember(
                            rs.getInt("claim_id"),
                            UUID.fromString(rs.getString("player_id")),
                            org.kari.kariClaims.models.ClaimMember.MemberRole.fromId(rs.getInt("role")),
                            rs.getLong("joined_at")
                        ));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("查询成员失败", e);
            }
        });
    }
    
    /**
     * 异步封禁玩家
     */
    public CompletableFuture<Void> banPlayerAsync(int claimId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = isMySQLOrMariaDB()
                ? "INSERT INTO banned_players (claim_id, player_id, banned_at) VALUES (?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE banned_at = ?"
                : "INSERT OR REPLACE INTO banned_players (claim_id, player_id, banned_at) VALUES (?, ?, ?)";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                long bannedAt = System.currentTimeMillis();
                stmt.setInt(1, claimId);
                stmt.setString(2, playerId.toString());
                stmt.setLong(3, bannedAt);
                
                if (isMySQLOrMariaDB()) {
                    stmt.setLong(4, bannedAt);
                }
                
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("封禁玩家失败", e);
            }
        });
    }
    
    /**
     * 异步解封玩家
     */
    public CompletableFuture<Void> unbanPlayerAsync(int claimId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM banned_players WHERE claim_id = ? AND player_id = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, claimId);
                stmt.setString(2, playerId.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("解封玩家失败", e);
            }
        });
    }
    
    /**
     * 异步检查玩家是否被封禁
     */
    public CompletableFuture<Boolean> isBannedAsync(int claimId, UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM banned_players WHERE claim_id = ? AND player_id = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, claimId);
                stmt.setString(2, playerId.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
                return false;
            } catch (SQLException e) {
                throw new RuntimeException("检查封禁状态失败", e);
            }
        });
    }
    
    /**
     * 异步获取领地的所有封禁玩家
     */
    public CompletableFuture<Set<UUID>> getBannedPlayersAsync(int claimId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_id FROM banned_players WHERE claim_id = ?";
            Set<UUID> banned = new HashSet<>();
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, claimId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        banned.add(UUID.fromString(rs.getString("player_id")));
                    }
                }
                return banned;
            } catch (SQLException e) {
                throw new RuntimeException("查询封禁列表失败", e);
            }
        });
    }
    
    /**
     * 异步更新领地所有者
     */
    public CompletableFuture<Void> updateChunkClaimOwnerAsync(int claimId, UUID newOwner) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE chunk_claims SET owner = ? WHERE id = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newOwner.toString());
                stmt.setInt(2, claimId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("更新所有者失败", e);
            }
        });
    }
    
    /**
     * 异步更新区域所有者
     */
    public CompletableFuture<Void> updateRegionOwnerAsync(int regionId, UUID newOwner) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE claim_regions SET owner = ? WHERE id = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newOwner.toString());
                stmt.setInt(2, regionId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("更新区域所有者失败", e);
            }
        });
    }
}

