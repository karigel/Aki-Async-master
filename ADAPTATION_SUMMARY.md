# Aki-Async Paper 适配总结

## 项目概述

本项目是将原版 Aki-Async（设计用于 Bukkit/Spigot 插件系统）适配到 **Ignite Mod Loader**（用于 Paper 服务器）的移植版本。

## 核心区别

### 1. **初始化方式：独立模式 vs 插件模式**

#### 原版 Aki-Async
- 依赖标准的 Bukkit `JavaPlugin` 生命周期
- 通过 `onEnable()` 方法初始化
- 需要插件系统加载和启用

#### 本项目（Paper 适配）
- **独立初始化模式**：通过 `AkiAsyncInitializer` 在服务器启动时直接初始化
- **不依赖插件系统**：可以在 Ignite 的 mods-only 模式下工作
- **Mixin 注入**：通过 `CraftServerLoadPluginsMixin` 在 `CraftServer` 构造时自动初始化

### 2. **配置文件路径检测**

#### 原版 Aki-Async
- 固定使用 `plugins/AkiAsync/` 目录

#### 本项目
- **智能路径检测**：自动检测 JAR 文件位置
  - 如果在 `mods/` 文件夹中 → 使用 `mods/AkiAsync/`
  - 如果在 `plugins/` 文件夹中 → 使用 `plugins/AkiAsync/`
- **自动复制配置文件**：从 JAR 中提取 `config.yml`、`entities.yml`、`throttling.yml`

### 3. **Executor 创建方式**

#### 原版 Aki-Async
- 通过 `AsyncExecutorManager` 在 `AkiAsyncPlugin.onEnable()` 中创建
- 需要 `AkiAsyncPlugin` 实例

#### 本项目
- **独立创建 Executor**：在 `AkiAsyncInitializer.createExecutors()` 中创建
- **完全功能支持**：创建所有 6 种 Executor（General、Lighting、TNT、ChunkTick、VillagerBreed、Brain）
- **保持原版行为**：使用相同的线程池配置、策略和优先级

### 4. **Bridge 模式：双模式支持**

#### 原版 Aki-Async
- 只有一种模式：`AkiAsyncBridge(plugin, executors...)`

#### 本项目
- **双构造函数支持**：
  ```java
  // 插件模式（原版）
  AkiAsyncBridge(plugin, executors...)
  
  // 独立模式（新增）
  AkiAsyncBridge(config, executors...)
  ```
- **向后兼容**：仍然支持原版的插件模式

### 5. **命令注册方式**

#### 原版 Aki-Async
- 在 `AkiAsyncPlugin.onEnable()` 中通过 `getCommand()` 注册

#### 本项目
- **Mixin 注入注册**：通过 `CraftServerLoadPluginsMixin.akiasync$afterEnablePlugins()` 注册
- **独立命令类**：`AkiReloadCommand`、`AkiDebugCommand`、`AkiVersionCommand`（不依赖 plugin 实例）

### 6. **配置管理**

#### 原版 Aki-Async
- `ConfigManager` 需要 `JavaPlugin` 实例来获取 `dataFolder`

#### 本项目
- **独立模式支持**：
  - `ConfigManager` 支持 `plugin == null` 的情况
  - `backupAndRegenerateConfig()` 方法支持从 `AkiAsyncInitializer` 获取数据文件夹
  - 添加了 `copyDefaultConfigFromJar()` 方法用于从 JAR 复制默认配置

## 关键适配文件

### 1. `AkiAsyncInitializer.java`（新增）
- **作用**：独立的初始化器，不依赖 `JavaPlugin` 生命周期
- **功能**：
  - 检测配置文件路径（mods vs plugins）
  - 从 JAR 复制默认配置文件
  - 创建所有 Executor 线程池
  - 初始化 Bridge 和所有优化模块
  - 支持配置重载

### 2. `CraftServerLoadPluginsMixin.java`（新增）
- **作用**：通过 Mixin 在服务器启动时自动初始化
- **功能**：
  - 在 `CraftServer` 构造时调用 `AkiAsyncInitializer.initialize()`
  - 在 `enablePlugins()` 后注册命令

### 3. `AkiAsyncBridge.java`（修改）
- **新增**：独立模式的构造函数
- **修改**：所有方法都支持 `plugin == null` 的情况

### 4. `ConfigManager.java`（修改）
- **修改**：`backupAndRegenerateConfig()` 支持独立模式
- **新增**：`copyDefaultConfigFromJar()` 方法

### 5. `IgniteAutoLoader.java`（可选，用于完整插件模式）
- **作用**：将 mods 中的 JAR 手动注入到 Bukkit 插件系统
- **功能**：通过反射创建和注册 `AkiAsyncPlugin` 实例

## 技术实现细节

### 1. **Mixin 注入时机**
```java
@Inject(method = "<init>", at = @At("RETURN"))
private void akiasync$onConstruction(final CallbackInfo ci) {
    AkiAsyncInitializer.initialize(getLogger());
}
```
- 在 `CraftServer` 构造完成后立即初始化
- 不依赖插件系统的加载顺序

### 2. **Executor 创建策略**
- 使用 `ThreadPoolExecutor.CallerRunsPolicy`：**不破坏原版特性**
  - 当队列满时，在调用线程中执行任务
  - 确保不会丢失任务或阻塞
- 所有线程设置为 daemon：不会阻止 JVM 关闭
- 预启动核心线程：提高响应速度

### 3. **配置文件处理**
```java
private void copyResourceIfNotExists(String resourceName) {
    // 从 JAR 中提取资源文件
    // 如果文件已存在，跳过（保留用户配置）
}
```
- 首次运行时自动创建配置文件
- 保留用户修改的配置

### 4. **Bridge 模式切换**
```java
// 检查 plugin 是否为 null
if (plugin != null) {
    // 插件模式：使用 plugin.getDataFolder()
} else {
    // 独立模式：使用 AkiAsyncInitializer.getInstance().getDataFolder()
}
```

## 兼容性保证

### 1. **向后兼容**
- 仍然支持原版的插件模式（如果通过 `IgniteAutoLoader` 注入）
- 所有 Mixin 代码保持不变
- 所有优化功能完全兼容

### 2. **功能完整性**
- ✅ 所有 Executor 都已创建
- ✅ 所有优化功能都能正常工作
- ✅ 配置系统完全支持
- ✅ 命令系统正常工作

### 3. **不破坏原版特性**
- 使用 `CallerRunsPolicy` 确保任务不会丢失
- 所有线程都是 daemon 线程
- 保持与原版相同的线程优先级和配置

## 使用场景

### 场景 1：纯 Ignite 模式（推荐）
- JAR 放在 `mods/` 文件夹
- 通过 Mixin 自动初始化
- 配置文件在 `mods/AkiAsync/`

### 场景 2：混合模式
- JAR 放在 `plugins/` 文件夹
- 通过 `IgniteAutoLoader` 注入到插件系统
- 配置文件在 `plugins/AkiAsync/`

### 场景 3：标准插件模式（兼容）
- 如果服务器支持标准插件加载
- 可以像普通插件一样使用

## 总结

本项目通过以下关键适配实现了对 Paper 服务器的完整支持：

1. **独立初始化系统**：不依赖插件生命周期
2. **智能路径检测**：自动适配 mods/plugins 文件夹
3. **完整 Executor 支持**：所有功能都能正常工作
4. **双模式 Bridge**：支持插件模式和独立模式
5. **Mixin 自动注入**：无缝集成到服务器启动流程

**核心优势**：
- ✅ 完全兼容 Paper 服务器
- ✅ 保持所有原版功能
- ✅ 不破坏原版特性
- ✅ 向后兼容原版插件模式

