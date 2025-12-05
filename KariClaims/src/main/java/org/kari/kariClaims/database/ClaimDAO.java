package org.kari.kariClaims.database;

import org.kari.kariClaims.models.Claim;
import org.kari.kariClaims.models.ClaimMember;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 领地数据访问对象 - 使用异步操作优化性能
 */
public class ClaimDAO {
    private final DatabaseManager databaseManager;

    public ClaimDAO(DatabaseManager databaseManager) {
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
        String createClaimsTable = isMySQLOrMariaDB()
            ? "CREATE TABLE IF NOT EXISTS claims (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "owner VARCHAR(36) NOT NULL, " +
                "world VARCHAR(255) NOT NULL, " +
                "min_x INT NOT NULL, " +
                "min_z INT NOT NULL, " +
                "max_x INT NOT NULL, " +
                "max_z INT NOT NULL, " +
                "name VARCHAR(255) DEFAULT NULL, " +
                "description TEXT DEFAULT NULL, " +
                "pvp_enabled BOOLEAN DEFAULT FALSE, " +
                "mob_spawning BOOLEAN DEFAULT TRUE, " +
                "fire_spread BOOLEAN DEFAULT FALSE, " +
                "explosion BOOLEAN DEFAULT FALSE, " +
                "enter_message BOOLEAN DEFAULT FALSE, " +
                "exit_message BOOLEAN DEFAULT FALSE, " +
                "enter_message_text TEXT DEFAULT NULL, " +
                "exit_message_text TEXT DEFAULT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "INDEX idx_world_coords (world, min_x, min_z, max_x, max_z), " +
                "INDEX idx_owner (owner)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            : "CREATE TABLE IF NOT EXISTS claims (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "owner VARCHAR(36) NOT NULL, " +
                "world VARCHAR(255) NOT NULL, " +
                "min_x INTEGER NOT NULL, " +
                "min_z INTEGER NOT NULL, " +
                "max_x INTEGER NOT NULL, " +
                "max_z INTEGER NOT NULL, " +
                "name VARCHAR(255) DEFAULT NULL, " +
                "description TEXT DEFAULT NULL, " +
                "pvp_enabled BOOLEAN DEFAULT 0, " +
                "mob_spawning BOOLEAN DEFAULT 1, " +
                "fire_spread BOOLEAN DEFAULT 0, " +
                "explosion BOOLEAN DEFAULT 0, " +
                "enter_message BOOLEAN DEFAULT 0, " +
                "exit_message BOOLEAN DEFAULT 0, " +
                "enter_message_text TEXT DEFAULT NULL, " +
                "exit_message_text TEXT DEFAULT NULL, " +
                "created_at INTEGER NOT NULL" +
                ")";

        String createMembersTable = isMySQLOrMariaDB()
            ? "CREATE TABLE IF NOT EXISTS members (" +
                "claim_id INT NOT NULL, " +
                "player_id VARCHAR(36) NOT NULL, " +
                "role INT NOT NULL, " +
                "joined_at BIGINT NOT NULL, " +
                "PRIMARY KEY (claim_id, player_id), " +
                "FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE, " +
                "INDEX idx_player (player_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            : "CREATE TABLE IF NOT EXISTS members (" +
                "claim_id INTEGER NOT NULL, " +
                "player_id VARCHAR(36) NOT NULL, " +
                "role INTEGER NOT NULL, " +
                "joined_at INTEGER NOT NULL, " +
                "PRIMARY KEY (claim_id, player_id), " +
                "FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE" +
                ")";

        String createPermissionsTable = isMySQLOrMariaDB()
            ? "CREATE TABLE IF NOT EXISTS permissions (" +
                "claim_id INT NOT NULL, " +
                "player_id VARCHAR(36) NOT NULL, " +
                "permission_type VARCHAR(50) NOT NULL, " +
                "allowed BOOLEAN NOT NULL, " +
                "PRIMARY KEY (claim_id, player_id, permission_type), " +
                "FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE, " +
                "INDEX idx_player (player_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            : "CREATE TABLE IF NOT EXISTS permissions (" +
                "claim_id INTEGER NOT NULL, " +
                "player_id VARCHAR(36) NOT NULL, " +
                "permission_type VARCHAR(50) NOT NULL, " +
                "allowed BOOLEAN NOT NULL, " +
                "PRIMARY KEY (claim_id, player_id, permission_type), " +
                "FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE" +
                ")";

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createClaimsTable);
            stmt.execute(createMembersTable);
            stmt.execute(createPermissionsTable);
        }
    }

    /**
     * 异步创建领地
     */
    public CompletableFuture<Claim> createClaimAsync(Claim claim) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO claims (owner, world, min_x, min_z, max_x, max_z, name, description, " +
                "pvp_enabled, mob_spawning, fire_spread, explosion, enter_message, exit_message, " +
                "enter_message_text, exit_message_text, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = isMySQLOrMariaDB()
                     ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                     : conn.prepareStatement(sql)) {
                
                stmt.setString(1, claim.getOwner().toString());
                stmt.setString(2, claim.getWorld());
                stmt.setInt(3, claim.getMinX());
                stmt.setInt(4, claim.getMinZ());
                stmt.setInt(5, claim.getMaxX());
                stmt.setInt(6, claim.getMaxZ());
                stmt.setString(7, claim.getName());
                stmt.setString(8, claim.getDescription());
                stmt.setBoolean(9, claim.isPvpEnabled());
                stmt.setBoolean(10, claim.isMobSpawning());
                stmt.setBoolean(11, claim.isFireSpread());
                stmt.setBoolean(12, claim.isExplosion());
                stmt.setBoolean(13, claim.isEnterMessage());
                stmt.setBoolean(14, claim.isExitMessage());
                stmt.setString(15, claim.getEnterMessageText());
                stmt.setString(16, claim.getExitMessageText());
                stmt.setLong(17, claim.getCreatedAt());
                
                stmt.executeUpdate();
                
                if (isMySQLOrMariaDB()) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            return new Claim(rs.getInt(1), claim.getOwner(), claim.getWorld(),
                                claim.getMinX(), claim.getMinZ(), claim.getMaxX(), claim.getMaxZ(), claim.getCreatedAt());
                        }
                    }
                } else {
                    // SQLite 需要查询最后插入的 ID
                    try (Statement queryStmt = conn.createStatement();
                         ResultSet rs = queryStmt.executeQuery("SELECT last_insert_rowid()")) {
                        if (rs.next()) {
                            return new Claim(rs.getInt(1), claim.getOwner(), claim.getWorld(),
                                claim.getMinX(), claim.getMinZ(), claim.getMaxX(), claim.getMaxZ(), claim.getCreatedAt());
                        }
                    }
                }
                
                return claim;
            } catch (SQLException e) {
                throw new RuntimeException("创建领地失败", e);
            }
        });
    }

    /**
     * 异步查询位置所在的领地
     */
    public CompletableFuture<Optional<Claim>> findClaimAtAsync(String world, int x, int z) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM claims WHERE world = ? AND min_x <= ? AND max_x >= ? AND min_z <= ? AND max_z >= ? LIMIT 1";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, world);
                stmt.setInt(2, x);
                stmt.setInt(3, x);
                stmt.setInt(4, z);
                stmt.setInt(5, z);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToClaim(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("查询领地失败", e);
            }
        });
    }

    /**
     * 异步查询玩家的所有领地
     */
    public CompletableFuture<List<Claim>> getPlayerClaimsAsync(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM claims WHERE owner = ?";
            List<Claim> claims = new ArrayList<>();
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        claims.add(mapResultSetToClaim(rs));
                    }
                }
                return claims;
            } catch (SQLException e) {
                throw new RuntimeException("查询玩家领地失败", e);
            }
        });
    }

    /**
     * 异步删除领地
     */
    public CompletableFuture<Void> deleteClaimAsync(int claimId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM claims WHERE id = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, claimId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("删除领地失败", e);
            }
        });
    }

    /**
     * 异步更新领地
     */
    public CompletableFuture<Void> updateClaimAsync(Claim claim) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE claims SET name = ?, description = ?, pvp_enabled = ?, mob_spawning = ?, " +
                "fire_spread = ?, explosion = ?, enter_message = ?, exit_message = ?, " +
                "enter_message_text = ?, exit_message_text = ? WHERE id = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, claim.getName());
                stmt.setString(2, claim.getDescription());
                stmt.setBoolean(3, claim.isPvpEnabled());
                stmt.setBoolean(4, claim.isMobSpawning());
                stmt.setBoolean(5, claim.isFireSpread());
                stmt.setBoolean(6, claim.isExplosion());
                stmt.setBoolean(7, claim.isEnterMessage());
                stmt.setBoolean(8, claim.isExitMessage());
                stmt.setString(9, claim.getEnterMessageText());
                stmt.setString(10, claim.getExitMessageText());
                stmt.setInt(11, claim.getId());
                
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("更新领地失败", e);
            }
        });
    }

    /**
     * 异步添加成员
     */
    public CompletableFuture<Void> addMemberAsync(ClaimMember member) {
        return CompletableFuture.runAsync(() -> {
            String sql = isMySQLOrMariaDB()
                ? "INSERT INTO members (claim_id, player_id, role, joined_at) VALUES (?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE role = ?, joined_at = ?"
                : "INSERT OR REPLACE INTO members (claim_id, player_id, role, joined_at) VALUES (?, ?, ?, ?)";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, member.getClaimId());
                stmt.setString(2, member.getPlayerId().toString());
                stmt.setInt(3, member.getRole().getId());
                stmt.setLong(4, member.getJoinedAt());
                
                if (isMySQLOrMariaDB()) {
                    stmt.setInt(5, member.getRole().getId());
                    stmt.setLong(6, member.getJoinedAt());
                }
                
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("添加成员失败", e);
            }
        });
    }

    /**
     * 异步获取领地的所有成员
     */
    public CompletableFuture<List<ClaimMember>> getClaimMembersAsync(int claimId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM members WHERE claim_id = ?";
            List<ClaimMember> members = new ArrayList<>();
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, claimId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        members.add(new ClaimMember(
                            rs.getInt("claim_id"),
                            UUID.fromString(rs.getString("player_id")),
                            ClaimMember.MemberRole.fromId(rs.getInt("role")),
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
     * 将 ResultSet 映射为 Claim 对象
     */
    private Claim mapResultSetToClaim(ResultSet rs) throws SQLException {
        Claim claim = new Claim(
            rs.getInt("id"),
            UUID.fromString(rs.getString("owner")),
            rs.getString("world"),
            rs.getInt("min_x"),
            rs.getInt("min_z"),
            rs.getInt("max_x"),
            rs.getInt("max_z"),
            rs.getLong("created_at")
        );
        
        claim.setName(rs.getString("name"));
        claim.setDescription(rs.getString("description"));
        claim.setPvpEnabled(rs.getBoolean("pvp_enabled"));
        claim.setMobSpawning(rs.getBoolean("mob_spawning"));
        claim.setFireSpread(rs.getBoolean("fire_spread"));
        claim.setExplosion(rs.getBoolean("explosion"));
        claim.setEnterMessage(rs.getBoolean("enter_message"));
        claim.setExitMessage(rs.getBoolean("exit_message"));
        claim.setEnterMessageText(rs.getString("enter_message_text"));
        claim.setExitMessageText(rs.getString("exit_message_text"));
        
        return claim;
    }
}

