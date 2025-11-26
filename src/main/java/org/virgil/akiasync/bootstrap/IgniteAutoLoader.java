package org.virgil.akiasync.bootstrap;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.misc.Unsafe;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.virgil.akiasync.AkiAsyncPlugin;

/**
 * Bridges Ignite's mods-only workflow with Bukkit's plugin lifecycle by
 * manually creating and registering the plugin instance from the mods directory.
 */
public final class IgniteAutoLoader {

    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
    private static final String PLUGIN_NAME = "AkiAsync";

    private IgniteAutoLoader() {
    }

    public static void injectPlugin(final CraftServer server) {
        final Logger logger = server.getLogger();
        
        final PluginManager pluginManager = server.getPluginManager();
        final Plugin existing = pluginManager.getPlugin(PLUGIN_NAME);
        
        // 如果插件已存在但未启用，尝试启用它
        if (existing != null && !ENABLED.get()) {
            enablePlugin(server, pluginManager, existing, logger);
            return;
        }
        
        if (LOADED.get()) {
            logger.fine("[AkiAsync/Ignite] 插件已标记为已加载，跳过重复注入。");
            return;
        }

        if (existing != null) {
            logger.info("[AkiAsync/Ignite] 检测到插件已存在于 PluginManager，跳过注入。");
            LOADED.set(true);
            return;
        }

        logger.info("[AkiAsync/Ignite] ====== 开始自动注入流程 ======");
        
        if (!LOADED.compareAndSet(false, true)) {
            logger.warning("[AkiAsync/Ignite] 并发检测到注入请求，跳过本次。");
            return;
        }

        logger.info("[AkiAsync/Ignite] 检测到 mods-only 模式，正在尝试手动注入 Bukkit 插件...");

        final File jarFile = locateModJar(logger);
        if (jarFile == null) {
            LOADED.set(false);
            return;
        }

        logger.info("[AkiAsync/Ignite] 目标 JAR: " + jarFile.getAbsolutePath());

        final Plugin plugin = createAndRegisterPlugin(server, pluginManager, jarFile, logger);
        if (plugin == null) {
            LOADED.set(false);
            return;
        }
        logger.info("[AkiAsync/Ignite] 插件已成功注入 Bukkit 列表，稍后由 enablePlugins() 启用。");
    }

    public static void enablePlugin(final CraftServer server, final PluginManager pluginManager, 
                                     final Plugin plugin, final Logger logger) {
        if (plugin == null) {
            logger.warning("[AkiAsync/Ignite] enablePlugin 被调用但插件为 null");
            return;
        }

        if (ENABLED.get()) {
            logger.info("[AkiAsync/Ignite] 插件已启用，跳过重复启用。");
            return;
        }

        if (!ENABLED.compareAndSet(false, true)) {
            logger.warning("[AkiAsync/Ignite] 并发启用检测，跳过本次。");
            return;
        }

        try {
            logger.info("[AkiAsync/Ignite] ====== 开始启用插件 ======");
            logger.info("[AkiAsync/Ignite] 插件实例: " + plugin.getClass().getName());
            
            // 首先尝试使用 PluginManager 的 enablePlugin 方法
            try {
                final Method enablePluginMethod = pluginManager.getClass().getMethod("enablePlugin", Plugin.class);
                enablePluginMethod.setAccessible(true);
                logger.info("[AkiAsync/Ignite] 使用 PluginManager.enablePlugin() 方法...");
                enablePluginMethod.invoke(pluginManager, plugin);
                logger.info("[AkiAsync/Ignite] 插件已通过 PluginManager 成功启用！");
                return;
            } catch (NoSuchMethodException ex) {
                logger.info("[AkiAsync/Ignite] PluginManager 没有 enablePlugin 方法，尝试直接调用...");
            }
            
            // 如果没有 enablePlugin 方法，直接调用 onEnable
            logger.info("[AkiAsync/Ignite] 直接调用 plugin.onEnable()...");
            plugin.onEnable();
            logger.info("[AkiAsync/Ignite] ====== 插件 onEnable() 调用成功！======");
            
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "[AkiAsync/Ignite] 启用插件失败", ex);
            ex.printStackTrace();
            ENABLED.set(false);
        }
    }

    private static Plugin createAndRegisterPlugin(final CraftServer server, final PluginManager pluginManager, 
                                                   final File jarFile, final Logger logger) {
        try {
            // 1. 读取 plugin.yml
            final PluginDescriptionFile description = loadPluginDescription(jarFile, logger);
            if (description == null) {
                return null;
            }

            // 2. 使用反射实例化插件（绕过 ClassLoader 检查）
            final AkiAsyncPlugin plugin = createPluginInstance(logger);
            
            // 3. 使用反射设置必要的字段
            setupPluginInstance(plugin, description, jarFile, server, logger);
            
            // 4. 手动注册到 PluginManager
            registerPlugin(pluginManager, plugin, logger);
            
            // 5. 调用 onLoad
            logger.info("[AkiAsync/Ignite] 调用插件 onLoad()...");
            plugin.onLoad();
            
            return plugin;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "[AkiAsync/Ignite] 创建插件实例失败", ex);
            return null;
        }
    }

    private static AkiAsyncPlugin createPluginInstance(final Logger logger) {
        try {
            // 使用 Unsafe 直接分配对象，绕过构造函数的 ClassLoader 检查
            final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            final Unsafe unsafe = (Unsafe) unsafeField.get(null);
            
            final AkiAsyncPlugin plugin = (AkiAsyncPlugin) unsafe.allocateInstance(AkiAsyncPlugin.class);
            logger.info("[AkiAsync/Ignite] 使用 Unsafe 成功创建插件实例（绕过构造函数）");
            return plugin;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "[AkiAsync/Ignite] 无法创建插件实例，尝试使用反射...", ex);
            // 回退到反射方式
            try {
                final Constructor<AkiAsyncPlugin> constructor = AkiAsyncPlugin.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (Exception ex2) {
                logger.log(Level.SEVERE, "[AkiAsync/Ignite] 反射方式也失败", ex2);
                throw new RuntimeException(ex2);
            }
        }
    }

    private static PluginDescriptionFile loadPluginDescription(final File jarFile, final Logger logger) {
        try (JarFile jar = new JarFile(jarFile)) {
            final JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null) {
                logger.severe("[AkiAsync/Ignite] JAR 中未找到 plugin.yml");
                return null;
            }
            try (InputStream stream = jar.getInputStream(entry)) {
                // 使用 PluginDescriptionFile 的构造函数直接读取流
                return new PluginDescriptionFile(stream);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "[AkiAsync/Ignite] 读取 plugin.yml 失败", ex);
            return null;
        }
    }

    private static void setupPluginInstance(final AkiAsyncPlugin plugin, final PluginDescriptionFile description,
                                             final File jarFile, final CraftServer server, final Logger logger) {
        try {
            // 使用反射设置 JavaPlugin 的私有字段
            final Class<?> pluginClass = org.bukkit.plugin.java.JavaPlugin.class;
            
            // 设置 description
            final Field descriptionField = pluginClass.getDeclaredField("description");
            descriptionField.setAccessible(true);
            descriptionField.set(plugin, description);
            
            // 设置 dataFolder
            final File dataFolder = new File(server.getPluginsFolder(), description.getName());
            final Field dataFolderField = pluginClass.getDeclaredField("dataFolder");
            dataFolderField.setAccessible(true);
            dataFolderField.set(plugin, dataFolder);
            
            // 设置 file
            final Field fileField = pluginClass.getDeclaredField("file");
            fileField.setAccessible(true);
            fileField.set(plugin, jarFile);
            
            // 设置 logger
            final Field loggerField = pluginClass.getDeclaredField("logger");
            loggerField.setAccessible(true);
            loggerField.set(plugin, server.getLogger());
            
            // 设置 server
            final Field serverField = pluginClass.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(plugin, server);
            
            logger.info("[AkiAsync/Ignite] 插件实例字段设置完成");
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "[AkiAsync/Ignite] 设置插件实例字段失败", ex);
            throw new RuntimeException(ex);
        }
    }

    private static void registerPlugin(final PluginManager pluginManager, final Plugin plugin, final Logger logger) {
        try {
            // 使用反射调用 PluginManager 的 addPlugin 方法（如果存在）
            final Method addPluginMethod = pluginManager.getClass().getMethod("addPlugin", Plugin.class);
            addPluginMethod.setAccessible(true);
            addPluginMethod.invoke(pluginManager, plugin);
            logger.info("[AkiAsync/Ignite] 插件已通过 addPlugin() 注册到 PluginManager");
            return;
        } catch (NoSuchMethodException ex) {
            logger.info("[AkiAsync/Ignite] PluginManager 没有 addPlugin 方法，尝试手动注册...");
        } catch (Exception ex) {
            logger.warning("[AkiAsync/Ignite] addPlugin() 调用失败: " + ex.getMessage());
        }
        
        // 手动注册插件到 PluginManager
        try {
            final Class<?> pmClass = pluginManager.getClass();
            
            // 1. 添加到 plugins 列表
            try {
                final Field pluginsField = pmClass.getDeclaredField("plugins");
                pluginsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                final java.util.List<Plugin> plugins = (java.util.List<Plugin>) pluginsField.get(pluginManager);
                if (!plugins.contains(plugin)) {
                    plugins.add(plugin);
                    logger.info("[AkiAsync/Ignite] 插件已添加到 plugins 列表");
                }
            } catch (Exception ex) {
                logger.warning("[AkiAsync/Ignite] 无法添加到 plugins 列表: " + ex.getMessage());
            }
            
            // 2. 添加到 lookupNames 映射（Paper 使用的）
            try {
                final Field lookupNamesField = pmClass.getDeclaredField("lookupNames");
                lookupNamesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                final java.util.Map<String, Plugin> lookupNames = (java.util.Map<String, Plugin>) lookupNamesField.get(pluginManager);
                lookupNames.put(plugin.getName().toLowerCase(java.util.Locale.ENGLISH), plugin);
                logger.info("[AkiAsync/Ignite] 插件已添加到 lookupNames 映射");
            } catch (Exception ex) {
                logger.warning("[AkiAsync/Ignite] 无法添加到 lookupNames 映射: " + ex.getMessage());
                // 尝试其他可能的字段名
                try {
                    final Field namesField = pmClass.getDeclaredField("names");
                    namesField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    final java.util.Map<String, Plugin> names = (java.util.Map<String, Plugin>) namesField.get(pluginManager);
                    names.put(plugin.getName().toLowerCase(java.util.Locale.ENGLISH), plugin);
                    logger.info("[AkiAsync/Ignite] 插件已添加到 names 映射");
                } catch (Exception ex2) {
                    logger.warning("[AkiAsync/Ignite] 无法添加到 names 映射: " + ex2.getMessage());
                }
            }
            
            logger.info("[AkiAsync/Ignite] 插件注册完成，验证中...");
            final Plugin verify = pluginManager.getPlugin(plugin.getName());
            if (verify != null) {
                logger.info("[AkiAsync/Ignite] ✓ 插件已成功注册，可通过 getPlugin() 找到");
            } else {
                logger.warning("[AkiAsync/Ignite] ✗ 插件注册后仍无法通过 getPlugin() 找到");
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "[AkiAsync/Ignite] 手动注册插件失败", ex);
            throw new RuntimeException(ex);
        }
    }

    private static File locateModJar(final Logger logger) {
        logger.info("[AkiAsync/Ignite] 开始定位 mods JAR...");
        final URL location = IgniteAutoLoader.class.getProtectionDomain().getCodeSource().getLocation();
        if (location == null) {
            logger.severe("[AkiAsync/Ignite] 无法定位 mods JAR：getLocation() 返回 null，跳过自动注册。");
            return null;
        }
        logger.info("[AkiAsync/Ignite] 检测到 CodeSource URL: " + location);
        try {
            final Path path = Paths.get(location.toURI());
            logger.info("[AkiAsync/Ignite] 解析后的路径: " + path);
            if (Files.isRegularFile(path)) {
                logger.info("[AkiAsync/Ignite] 确认是有效的 JAR 文件: " + path.toFile().getAbsolutePath());
                return path.toFile();
            }
            logger.warning("[AkiAsync/Ignite] " + path + " 不是有效的 JAR 文件（可能是目录），无法注册插件。");
        } catch (final Exception ex) {
            logger.log(Level.SEVERE, "[AkiAsync/Ignite] 解析 mods JAR 路径失败", ex);
        }
        return null;
    }
}

