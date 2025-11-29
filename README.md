# Aki-Async (Ignite Fork)

[![GitHub](https://img.shields.io/badge/Fork_of-Aki--Async-blue)](https://github.com/virgil698/Aki-Async)
[![Ignite](https://img.shields.io/badge/Powered_by-Ignite-orange)](https://github.com/vectrix-space/ignite)

这是 [Aki-Async](https://github.com/virgil698/Aki-Async) 的 **Ignite 专用 Fork**，将原本设计为 Bukkit 插件的 Aki-Async 完全适配到 **Ignite Mod Loader** 环境，实现 100% 功能支持。

## 📋 为什么需要这个 Fork？

原版 Aki-Async 是一个优秀的服务器异步优化项目，但它设计为 Bukkit 插件，依赖 `JavaPlugin` 生命周期（`onEnable()`）来初始化。然而，**Ignite Mod Loader 不支持传统的 plugins 文件夹**，它使用 Mixin 注入技术在服务器启动时修改代码。

本 Fork 通过以下方式解决这个问题：
- **完全移除 plugin.yml**：不再作为 Bukkit 插件加载
- **Mixin 自动初始化**：在服务器启动时自动注入和初始化
- **适配层桥接**：让依赖 Plugin 实例的组件在 Ignite 下正常工作
- **插件类加载器修复**：解决 Ignite 环境下的类加载器隔离问题

## ✨ 主要特性

### 核心优化（100% 工作）
- ✅ **Entity Tick Parallel** - 实体 Tick 并行处理
- ✅ **Mob Spawning Async** - 异步怪物生成
- ✅ **TNT Optimization** - TNT 爆炸优化
- ✅ **Brain Throttle** - AI 大脑节流
- ✅ **Async Lighting** - 异步光照计算
- ✅ **Block Entity Parallel** - 方块实体并行处理
- ✅ **Chunk Tick Async** - 区块 Tick 异步
- ✅ **Structure Location Async** - 异步结构定位
- ✅ **DataPack Optimization** - 数据包加载优化

### 插件兼容性（完全支持）
- ✅ **WorldGuard** - 区域保护
- ✅ **Residence** - 领地插件
- ✅ **Lands** - 土地插件
- ✅ **KariClaims** - 自定义领地插件
- ✅ **ViaVersion** - 跨版本协议
- ✅ **FancyNpcs** - NPC 插件
- ✅ **ZNPCsPlus** - NPC 插件

### 辅助功能（完全支持）
- ✅ **NetworkOptimization** - 网络优化
- ✅ **ChunkLoadScheduler** - 区块加载调度
- ✅ **VirtualEntityCompat** - 虚拟实体兼容

## 🚀 安装方法

1. 将编译好的 JAR 文件放入服务器的 `mods/` 文件夹
2. 启动服务器，Aki-Async 会自动初始化
3. 配置文件会自动创建在 `mods/AkiAsync/` 目录

## 📁 配置文件位置

- **主配置**：`mods/AkiAsync/config.yml`
- **实体配置**：`mods/AkiAsync/entities.yml`
- **节流配置**：`mods/AkiAsync/throttling.yml`

首次运行时会自动从 JAR 中提取默认配置文件。

## 🔧 核心适配工作

### 1. 架构设计

```
原版 Aki-Async (Bukkit Plugin)
├── AkiAsyncPlugin.java        ← onEnable() 初始化
├── 依赖 plugin.yml
└── 使用 plugins/ 文件夹

本 Fork (Ignite Mod)
├── AkiAsyncInitializer.java   ← 独立初始化系统
├── CraftServerLoadPluginsMixin.java ← Mixin 钩子
├── IgnitePluginAdapter.java   ← 适配层（让辅助组件工作）
├── 使用 ignite.mod.json
└── 使用 mods/ 文件夹
```

### 2. 初始化流程

```
服务器启动
    │
    ▼
Ignite 加载 AkiAsync mod (ignite.mod.json)
    │
    ▼
CraftServerLoadPluginsMixin.loadPlugins() [HEAD]
    │  └─ AkiAsyncInitializer.initialize()  ← 核心初始化
    │       ├─ ConfigManager
    │       ├─ Executors (TNT, Lighting, Brain, etc.)
    │       ├─ AkiAsyncBridge
    │       └─ Mixin 配置
    ▼
Bukkit 加载其他插件 (WorldGuard, KariClaims, etc.)
    │
    ▼
CraftServerLoadPluginsMixin.enablePlugins() [POSTWORLD]
    │  └─ 兼容层初始化
    │       ├─ ViaVersionCompat.initialize()
    │       ├─ LandProtectionIntegration
    │       ├─ FancyNpcsDetector / ZNPCsPlusDetector
    │       └─ IgnitePluginAdapter.initializeAuxiliaryFeatures()
    ▼
服务器运行
```

### 3. 插件类加载器修复

Ignite 使用独立的类加载器，导致 `Class.forName()` 无法找到其他插件的类。

**问题**：
```java
// 在 Ignite 环境下失败
Class.forName("com.sk89q.worldguard.WorldGuard");
```

**解决方案**：
```java
// 使用插件自己的类加载器
Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
ClassLoader pluginClassLoader = plugin.getClass().getClassLoader();
Class.forName("com.sk89q.worldguard.WorldGuard", true, pluginClassLoader);
```

### 4. 适配层设计（IgnitePluginAdapter）

原版的辅助组件（NetworkOptimizationManager 等）需要 `AkiAsyncPlugin` 实例。我们创建适配层来桥接：

```java
public class IgnitePluginAdapter {
    // 从 AkiAsyncInitializer 获取配置
    private final ConfigManager configManager;
    
    // 使用代理插件注册事件
    private Plugin findProxyPlugin() {
        // 找一个已启用的插件来注册事件监听器
    }
    
    // 初始化所有辅助功能
    public void initializeAuxiliaryFeatures() {
        initializeNetworkOptimization();
        initializeChunkLoadScheduler();
        initializeVirtualEntityCompat();
    }
}
```

### 5. 保持上游兼容

为了方便跟随原版 Aki-Async 更新，我们：
- **不修改原有文件**（AkiAsyncPlugin.java 等保持不变）
- **只新增适配文件**（AkiAsyncInitializer, IgnitePluginAdapter, Mixins）
- **删除 plugin.yml**（唯一的删除操作）

```bash
# 合并上游更新
git remote add upstream https://github.com/virgil698/Aki-Async.git
git fetch upstream
git merge upstream/main
# 冲突只会在我们新增的文件中
```

## 📊 功能对比

| 功能 | 原版 Aki-Async | 本 Fork |
|------|---------------|---------|
| 运行方式 | Bukkit 插件 | Ignite Mod |
| 初始化方式 | `onEnable()` | Mixin 自动注入 |
| 配置文件路径 | `plugins/AkiAsync/` | `mods/AkiAsync/` |
| 插件兼容检测 | 默认类加载器 | 插件类加载器 |
| 辅助功能 | 需要 Plugin 实例 | 适配层桥接 |
| Ignite 支持 | ❌ | ✅ |
| 上游更新 | - | 易于合并 |

## 📝 使用说明

### 安装

1. 确保服务器已安装 [Ignite Mod Loader](https://github.com/vectrix-space/ignite)
2. 将 JAR 放入 `mods/` 文件夹
3. 启动服务器

### 验证安装

查看日志确认初始化成功：
```
[AkiAsync/Ignite] 正在初始化 AkiAsync...
[AkiAsync] Bridge registered successfully with all executors
[AkiAsync] Land protection plugins detected:
  [✓] WorldGuard - Compatible
  [✓] KariClaims - Compatible
[AkiAsync/Ignite] 所有兼容层已初始化完成
```

### 命令

- `/aki-reload` - 重载配置文件
- `/aki-debug` - 查看调试信息
- `/aki-version` - 查看版本信息

## 🔄 新增/修改的文件

### 新增文件（我们的适配代码）

| 文件 | 说明 |
|------|-----|
| `AkiAsyncInitializer.java` | 独立初始化系统 |
| `CraftServerLoadPluginsMixin.java` | Mixin 钩子（命令注册、兼容层初始化）|
| `IgnitePluginAdapter.java` | 适配层（让辅助组件工作）|
| `AkiReloadCommand.java` | 独立命令类 |
| `AkiDebugCommand.java` | 独立命令类 |
| `AkiVersionCommand.java` | 独立命令类 |

### 修改文件（小改动，易于合并）

| 文件 | 修改内容 |
|------|---------|
| `AkiAsyncBridge.java` | 添加独立模式构造函数 |
| `ConfigManager.java` | 支持 `plugin == null` |
| `LandProtectionIntegration.java` | 使用插件类加载器 |
| `ViaVersionCompat.java` | 使用插件类加载器 |
| `FancyNpcsDetector.java` | 使用插件类加载器 |

### 删除文件

| 文件 | 原因 |
|------|-----|
| `plugin.yml` | Ignite 不使用 plugins 文件夹 |

## ⚠️ 注意事项

1. **必须使用 Ignite**：本 Fork 专为 Ignite Mod Loader 设计
2. **JAR 放在 mods/**：不是 plugins/ 文件夹
3. **配置在 mods/AkiAsync/**：首次运行自动创建

## 📄 许可证

与原版 Aki-Async 保持一致。

## 🙏 致谢

- **原版项目**：[Aki-Async](https://github.com/virgil698/Aki-Async) by virgil698
- **Ignite Mod Loader**：[Ignite](https://github.com/vectrix-space/ignite) by vectrix-space

## 📚 相关链接

- [原版 Aki-Async](https://github.com/virgil698/Aki-Async)
- [Ignite Mod Loader](https://github.com/vectrix-space/ignite)
- [Paper MC](https://papermc.io/)
