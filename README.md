# Aki-Async (Paper/Ignite Fork)

这是 [Aki-Async](https://github.com/virgil698/Aki-Async) 的 Fork 版本，专门适配 **Paper 服务器** 和 **Ignite Mod Loader**。

## 📋 项目说明

原版 Aki-Async 设计用于标准的 Bukkit/Spigot 插件系统，需要插件生命周期（`onEnable()`）来初始化。本项目通过 **Mixin 注入** 和 **独立初始化系统**，使其能够在 Ignite Mod Loader 环境下作为 Mod 运行，完全支持 Paper 服务器。

## ✨ 主要特性

- ✅ **完全兼容 Paper 服务器**：通过 Ignite Mod Loader 运行
- ✅ **100% 功能支持**：所有原版优化功能完全可用
- ✅ **不破坏原版特性**：使用 `CallerRunsPolicy` 确保任务不丢失
- ✅ **自动初始化**：通过 Mixin 在服务器启动时自动加载
- ✅ **智能配置管理**：自动检测并创建配置文件

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

### 1. 独立初始化系统

**原版方式**：依赖 Bukkit `JavaPlugin` 生命周期
```java
// 原版：在 onEnable() 中初始化
public void onEnable() {
    // 初始化逻辑
}
```

**本 Fork**：通过 `AkiAsyncInitializer` 独立初始化
```java
// 通过 Mixin 在 CraftServer 构造时自动初始化
@Inject(method = "<init>", at = @At("RETURN"))
private void akiasync$onConstruction(final CallbackInfo ci) {
    AkiAsyncInitializer.initialize(getLogger());
}
```

### 2. Mixin 自动注入

创建了 `CraftServerLoadPluginsMixin`，在服务器启动时自动：
- 初始化 Aki-Async 系统
- 注册命令（`/aki-reload`, `/aki-debug`, `/aki-version`）
- 设置 Bridge 和所有 Executor

### 3. Executor 独立创建

**原版方式**：在 `AkiAsyncPlugin.onEnable()` 中通过 `AsyncExecutorManager` 创建

**本 Fork**：在 `AkiAsyncInitializer.createExecutors()` 中独立创建所有 Executor：
- General Executor（通用线程池）
- Lighting Executor（光照线程池）
- TNT Executor（TNT 爆炸线程池）
- ChunkTick Executor（区块 Tick 线程池）
- VillagerBreed Executor（村民繁殖线程池）
- Brain Executor（AI 大脑线程池）

所有 Executor 使用与原版相同的配置：
- `ThreadPoolExecutor.CallerRunsPolicy`：不破坏原版特性
- Daemon 线程：不会阻止 JVM 关闭
- 适当的线程优先级和队列大小

### 4. Bridge 双模式支持

**原版方式**：只有插件模式
```java
AkiAsyncBridge(plugin, executors...)
```

**本 Fork**：支持独立模式
```java
// 独立模式（新增）
AkiAsyncBridge(config, executors...)

// 插件模式（兼容）
AkiAsyncBridge(plugin, executors...)
```

### 5. 配置管理适配

- `ConfigManager` 支持 `plugin == null` 的情况
- `backupAndRegenerateConfig()` 支持从 `AkiAsyncInitializer` 获取数据文件夹
- 自动从 JAR 复制默认配置文件

### 6. 命令系统适配

创建了独立的命令类（不依赖 plugin 实例）：
- `AkiReloadCommand`：重载配置
- `AkiDebugCommand`：调试信息
- `AkiVersionCommand`：版本信息

通过 Mixin 在 `enablePlugins()` 后自动注册。

## 📊 功能对比

| 功能 | 原版 Aki-Async | 本 Fork |
|------|---------------|---------|
| 初始化方式 | 插件生命周期 | Mixin 自动注入 |
| 配置文件路径 | `plugins/AkiAsync/` | `mods/AkiAsync/` |
| Executor 创建 | 需要 plugin 实例 | 独立创建 |
| Bridge 模式 | 仅插件模式 | 双模式支持 |
| 命令注册 | 插件系统 | Mixin 注入 |
| Paper 支持 | ❌ | ✅ |
| Ignite 支持 | ❌ | ✅ |

## 🎯 技术实现

### Mixin 注入时机

```java
@Mixin(value = CraftServer.class)
public abstract class CraftServerLoadPluginsMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void akiasync$onConstruction(final CallbackInfo ci) {
        AkiAsyncInitializer.initialize(getLogger());
    }
}
```

在 `CraftServer` 构造完成后立即初始化，不依赖插件系统的加载顺序。

### Executor 创建策略

```java
new ThreadPoolExecutor(
    threadPoolSize, threadPoolSize,
    60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(maxQueueSize),
    threadFactory,
    new ThreadPoolExecutor.CallerRunsPolicy() // 不破坏原版特性
);
```

使用 `CallerRunsPolicy` 确保：
- 队列满时在调用线程执行任务
- 不会丢失任务或阻塞
- 保持原版游戏特性

## 📝 使用说明

### 基本使用

1. 将 JAR 放入 `mods/` 文件夹
2. 启动服务器
3. 查看日志确认初始化成功：
   ```
   [AkiAsync/Ignite] CraftServer 构造完成，Mixin 已生效！
   [AkiAsync] Bridge registered successfully with all executors
   ```

### 配置调整

编辑 `mods/AkiAsync/config.yml` 来调整优化设置。

### 命令使用

- `/aki-reload`：重载配置文件
- `/aki-debug`：查看调试信息
- `/aki-version`：查看版本信息

## 🔄 与原版的区别

### 主要修改文件

1. **新增**：`AkiAsyncInitializer.java` - 独立初始化系统
2. **新增**：`CraftServerLoadPluginsMixin.java` - Mixin 自动注入
3. **修改**：`AkiAsyncBridge.java` - 添加独立模式构造函数
4. **修改**：`ConfigManager.java` - 支持独立模式配置管理
5. **新增**：独立命令类（`AkiReloadCommand`, `AkiDebugCommand`, `AkiVersionCommand`）

### 保持兼容

- ✅ 所有 Mixin 代码保持不变
- ✅ 所有优化功能完全兼容
- ✅ 配置格式完全兼容
- ✅ 行为与原版一致

## ⚠️ 注意事项

1. **仅支持 mods 文件夹**：JAR 必须放在 `mods/` 文件夹中
2. **需要 Ignite Mod Loader**：必须在支持 Ignite 的 Paper 服务器上运行
3. **配置文件位置**：配置文件在 `mods/AkiAsync/`，不是 `plugins/AkiAsync/`

## 📄 许可证

与原版 Aki-Async 保持一致。

## 🙏 致谢

- 原版项目：[Aki-Async](https://github.com/virgil698/Aki-Async)
- Ignite Mod Loader：[Ignite](https://github.com/vectrix-space/ignite)

## 📚 相关链接

- [原版 Aki-Async](https://github.com/virgil698/Aki-Async)
- [Ignite Mod Loader](https://github.com/vectrix-space/ignite)
- [Paper](https://papermc.io/)
