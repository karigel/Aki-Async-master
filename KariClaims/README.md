# KariClaims

一个高性能的领地保护插件，专为 Paper 1.21.8 设计，完全兼容 UltimateClaims 数据库。

## 特性

### 核心功能
- ✅ **领地创建与管理** - 轻松创建和管理你的领地
- ✅ **权限系统** - 细粒度的权限控制（建造、破坏、交互等）
- ✅ **成员管理** - 支持添加成员、设置角色（所有者、信任成员、成员、访客）
- ✅ **GUI 界面** - 友好的图形化管理界面
- ✅ **事件保护** - 全面保护领地免受破坏、PVP、爆炸等
- ✅ **数据库兼容** - 自动迁移 UltimateClaims 数据库

### 性能优化
- 🚀 **HikariCP 连接池** - 高效的数据库连接管理
- 🚀 **智能缓存系统** - 减少数据库查询，提升响应速度
- 🚀 **异步操作** - 所有数据库操作异步执行，不阻塞主线程
- 🚀 **索引优化** - 数据库表结构优化，查询速度更快

### 数据库支持
- 📊 **SQLite** - 默认数据库，轻量级，适合小型服务器
- 📊 **MySQL** - 支持 MySQL，适合大型服务器和集群

## 安装

1. 下载插件 JAR 文件
2. 将文件放入服务器的 `plugins` 文件夹
3. 重启服务器
4. 插件会自动创建配置文件和数据文件夹

## 配置

配置文件位于 `plugins/KariClaims/config.yml`

### 数据库配置示例

**SQLite（默认）:**
```yaml
database:
  type: sqlite
```

**MySQL:**
```yaml
database:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: kariClaims
    username: root
    password: your_password
```

## 命令

- `/claim pos1` - 设置第一个位置
- `/claim pos2` - 设置第二个位置
- `/claim create` - 创建领地
- `/claim delete <id>` - 删除领地
- `/claim list` - 查看你的领地列表
- `/claim info` - 查看当前位置的领地信息
- `/claim gui` - 打开管理界面
- `/claim add <玩家>` - 添加成员
- `/claim remove <玩家>` - 移除成员
- `/claim setname <名称>` - 设置领地名称
- `/claim setdesc <描述>` - 设置领地描述

## 权限

- `kariClaims.use` - 使用领地功能（默认所有玩家）
- `kariClaims.create` - 创建领地（默认所有玩家）
- `kariClaims.delete` - 删除自己的领地（默认所有玩家）
- `kariClaims.admin.bypass` - 绕过所有领地保护（默认 OP）
- `kariClaims.admin.delete` - 删除任何领地（默认 OP）

## 数据库迁移

插件启动时会自动检测 UltimateClaims 数据库并尝试迁移。迁移过程包括：

1. 检测 `plugins/UltimateClaims/claims.db` 文件
2. 自动迁移所有领地数据
3. 迁移成员数据
4. 迁移权限数据

迁移完成后，原数据库不会被删除，你可以手动备份或删除。

## 开发

### 构建

```bash
mvn clean package
```

构建产物位于 `target/KariClaims-1.0-SNAPSHOT.jar`

### 项目结构

```
src/main/java/org/kari/kariClaims/
├── KariClaims.java              # 主类
├── commands/
│   └── ClaimCommand.java        # 命令处理器
├── database/
│   ├── DatabaseManager.java     # 数据库管理器
│   ├── DatabaseMigration.java   # 数据库迁移工具
│   └── ClaimDAO.java           # 数据访问对象
├── gui/
│   ├── ClaimGUI.java           # GUI 界面
│   └── GUIListener.java        # GUI 事件监听器
├── listeners/
│   └── ClaimListener.java      # 领地保护事件监听器
├── managers/
│   └── ClaimManager.java       # 领地管理器
└── models/
    ├── Claim.java              # 领地数据模型
    ├── ClaimMember.java       # 成员数据模型
    └── Permission.java        # 权限数据模型
```

## 技术栈

- **Java 21** - 编程语言
- **Paper API 1.21.8** - Minecraft 服务器 API
- **HikariCP 5.1.0** - 数据库连接池
- **MySQL Connector 9.1.0** - MySQL 驱动
- **SQLite JDBC 3.47.1.0** - SQLite 驱动

## 性能特点

1. **连接池优化** - 使用 HikariCP 连接池，支持连接复用和自动管理
2. **缓存机制** - 智能缓存系统，减少数据库查询次数
3. **异步处理** - 所有数据库操作异步执行，不阻塞主线程
4. **索引优化** - 数据库表结构包含必要的索引，提升查询速度
5. **批量操作** - 支持批量数据库操作，提高效率

## 兼容性

- ✅ Paper 1.21.8
- ✅ UltimateClaims 数据库自动迁移
- ✅ 支持 SQLite 和 MySQL

## 许可证

本项目为开源项目，可自由使用和修改。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 更新日志

### v1.0-SNAPSHOT
- 初始版本
- 实现所有核心功能
- 支持 UltimateClaims 数据库迁移
- 性能优化和缓存系统

