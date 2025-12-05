package org.kari.kariClaims.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.kari.kariClaims.KariClaims;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * 数据库管理器 - 使用 HikariCP 连接池优化性能
 */
public class DatabaseManager {
    private final KariClaims plugin;
    private HikariDataSource dataSource;
    private DatabaseType databaseType;

    public DatabaseManager(KariClaims plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化数据库连接
     */
    public void initialize() {
        String dbType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        config.setPoolName("KariClaims-Pool");

        if (dbType.equals("mysql")) {
            databaseType = DatabaseType.MYSQL;
            String host = plugin.getConfig().getString("database.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("database.mysql.port", 3306);
            String database = plugin.getConfig().getString("database.mysql.database", "kariClaims");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
            config.setUsername(plugin.getConfig().getString("database.mysql.username", "root"));
            config.setPassword(plugin.getConfig().getString("database.mysql.password", ""));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
        } else if (dbType.equals("mariadb")) {
            databaseType = DatabaseType.MARIADB;
            // 优先使用 mariadb 配置，如果没有则使用 mysql 配置
            String host = plugin.getConfig().getString("database.mariadb.host", 
                plugin.getConfig().getString("database.mysql.host", "localhost"));
            int port = plugin.getConfig().getInt("database.mariadb.port", 
                plugin.getConfig().getInt("database.mysql.port", 3306));
            String database = plugin.getConfig().getString("database.mariadb.database", 
                plugin.getConfig().getString("database.mysql.database", "kariClaims"));
            String username = plugin.getConfig().getString("database.mariadb.username", 
                plugin.getConfig().getString("database.mysql.username", "root"));
            String password = plugin.getConfig().getString("database.mariadb.password", 
                plugin.getConfig().getString("database.mysql.password", ""));
            
            config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("org.mariadb.jdbc.Driver");
            
            // MariaDB 优化配置
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            // MariaDB 特定配置
            config.addDataSourceProperty("useMysqlMetadata", "true");
        } else {
            databaseType = DatabaseType.SQLITE;
            File dbFile = new File(plugin.getDataFolder(), "claims.db");
            
            // 确保数据文件夹存在
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            // SQLite JDBC 会自动从 JAR 中提取原生库
            // 设置系统属性以帮助原生库加载
            String tempPath = System.getProperty("java.io.tmpdir");
            System.setProperty("org.sqlite.tmpdir", tempPath);
            
            // 尝试设置库路径（如果原生库在特定位置）
            // 如果原生库在 JAR 中，SQLite JDBC 会自动提取
            
            String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            config.setJdbcUrl(jdbcUrl);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setConnectionTestQuery("SELECT 1");
            
            plugin.getLogger().info("SQLite 数据库路径: " + dbFile.getAbsolutePath());
        }

        try {
            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("数据库连接已建立: " + databaseType);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "无法初始化数据库连接", e);
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("数据源未初始化");
        }
        return dataSource.getConnection();
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("数据库连接已关闭");
        }
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public enum DatabaseType {
        MYSQL,
        MARIADB,
        SQLITE
    }
}

