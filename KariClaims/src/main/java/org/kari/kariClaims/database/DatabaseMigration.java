package org.kari.kariClaims.database;

import org.kari.kariClaims.KariClaims;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

/**
 * 数据库迁移工具 - 支持从 UltimateClaims 数据库迁移
 */
public class DatabaseMigration {
    private final KariClaims plugin;
    private final DatabaseManager databaseManager;

    public DatabaseMigration(KariClaims plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * 检查并迁移 UltimateClaims 数据库
     */
    public void migrateFromUltimateClaims() {
        try {
            // 检查是否存在 UltimateClaims 数据库
            File pluginsFolder = plugin.getDataFolder().getParentFile();
            File oldDbFile = new File(pluginsFolder, "UltimateClaims/claims.db");
            
            if (!oldDbFile.exists()) {
                plugin.getLogger().info("未找到 UltimateClaims 数据库，跳过迁移");
                return;
            }

            plugin.getLogger().info("检测到 UltimateClaims 数据库，开始迁移...");

            // 连接到旧数据库
            String oldDbUrl = "jdbc:sqlite:" + oldDbFile.getAbsolutePath();
            try (Connection oldConn = DriverManager.getConnection(oldDbUrl);
                 Connection newConn = databaseManager.getConnection()) {
                
                // 迁移 claims 表
                migrateClaimsTable(oldConn, newConn);
                
                // 迁移 members 表
                migrateMembersTable(oldConn, newConn);
                
                // 迁移 permissions 表
                migratePermissionsTable(oldConn, newConn);
                
                plugin.getLogger().info("数据库迁移完成！");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "迁移 UltimateClaims 数据库时出错", e);
        }
    }

    /**
     * 迁移 claims 表
     */
    private void migrateClaimsTable(Connection oldConn, Connection newConn) throws SQLException {
        String selectSql = "SELECT * FROM claims";
        String insertSql = "INSERT INTO claims (id, owner, world, min_x, min_z, max_x, max_z, name, description, " +
            "pvp_enabled, mob_spawning, fire_spread, explosion, enter_message, exit_message, " +
            "enter_message_text, exit_message_text, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement selectStmt = oldConn.prepareStatement(selectSql);
             ResultSet rs = selectStmt.executeQuery();
             PreparedStatement insertStmt = newConn.prepareStatement(insertSql)) {
            
            int count = 0;
            while (rs.next()) {
                insertStmt.setInt(1, rs.getInt("id"));
                insertStmt.setString(2, rs.getString("owner"));
                insertStmt.setString(3, rs.getString("world"));
                insertStmt.setInt(4, rs.getInt("min_x"));
                insertStmt.setInt(5, rs.getInt("min_z"));
                insertStmt.setInt(6, rs.getInt("max_x"));
                insertStmt.setInt(7, rs.getInt("max_z"));
                insertStmt.setString(8, rs.getString("name"));
                insertStmt.setString(9, rs.getString("description"));
                insertStmt.setBoolean(10, rs.getBoolean("pvp_enabled"));
                insertStmt.setBoolean(11, rs.getBoolean("mob_spawning"));
                insertStmt.setBoolean(12, rs.getBoolean("fire_spread"));
                insertStmt.setBoolean(13, rs.getBoolean("explosion"));
                insertStmt.setBoolean(14, rs.getBoolean("enter_message"));
                insertStmt.setBoolean(15, rs.getBoolean("exit_message"));
                insertStmt.setString(16, rs.getString("enter_message_text"));
                insertStmt.setString(17, rs.getString("exit_message_text"));
                insertStmt.setLong(18, rs.getLong("created_at"));
                
                try {
                    insertStmt.executeUpdate();
                    count++;
                } catch (SQLException e) {
                    // 如果记录已存在，跳过
                    plugin.getLogger().fine("跳过已存在的领地记录: " + rs.getInt("id"));
                }
            }
            plugin.getLogger().info("已迁移 " + count + " 个领地记录");
        }
    }

    /**
     * 迁移 members 表
     */
    private void migrateMembersTable(Connection oldConn, Connection newConn) throws SQLException {
        String selectSql = "SELECT * FROM members";
        String insertSql = "INSERT INTO members (claim_id, player_id, role, joined_at) VALUES (?, ?, ?, ?)";

        try (PreparedStatement selectStmt = oldConn.prepareStatement(selectSql);
             ResultSet rs = selectStmt.executeQuery();
             PreparedStatement insertStmt = newConn.prepareStatement(insertSql)) {
            
            int count = 0;
            while (rs.next()) {
                insertStmt.setInt(1, rs.getInt("claim_id"));
                insertStmt.setString(2, rs.getString("player_id"));
                insertStmt.setInt(3, rs.getInt("role"));
                insertStmt.setLong(4, rs.getLong("joined_at"));
                
                try {
                    insertStmt.executeUpdate();
                    count++;
                } catch (SQLException e) {
                    plugin.getLogger().fine("跳过已存在的成员记录");
                }
            }
            plugin.getLogger().info("已迁移 " + count + " 个成员记录");
        }
    }

    /**
     * 迁移 permissions 表
     */
    private void migratePermissionsTable(Connection oldConn, Connection newConn) throws SQLException {
        String selectSql = "SELECT * FROM permissions";
        String insertSql = "INSERT INTO permissions (claim_id, player_id, permission_type, allowed) VALUES (?, ?, ?, ?)";

        try (PreparedStatement selectStmt = oldConn.prepareStatement(selectSql);
             ResultSet rs = selectStmt.executeQuery();
             PreparedStatement insertStmt = newConn.prepareStatement(insertSql)) {
            
            int count = 0;
            while (rs.next()) {
                insertStmt.setInt(1, rs.getInt("claim_id"));
                insertStmt.setString(2, rs.getString("player_id"));
                insertStmt.setString(3, rs.getString("permission_type"));
                insertStmt.setBoolean(4, rs.getBoolean("allowed"));
                
                try {
                    insertStmt.executeUpdate();
                    count++;
                } catch (SQLException e) {
                    plugin.getLogger().fine("跳过已存在的权限记录");
                }
            }
            plugin.getLogger().info("已迁移 " + count + " 个权限记录");
        }
    }
}

