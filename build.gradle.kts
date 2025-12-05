plugins {
    java
    alias(libs.plugins.paperweightUserdev)
    alias(libs.plugins.shadowJar)
    alias(libs.plugins.runPaper)
}

group = "org.virgil"
version = "3.2.16-ignite-1.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.viaversion.com") // ViaVersion
    maven("https://repo.codemc.io/repository/maven-public/") // codemc
    
    exclusiveContent {
        forRepository {
            maven("https://jitpack.io") {
                name = "jitpack"
            }
        }
        filter {
            includeGroup("com.github.angeschossen") // LandsAPI
            includeGroup("com.github.Zrips") // Residence
            includeGroup("com.github.rutgerkok") // BlockLocker
        }
    }
    
    exclusiveContent {
        forRepository {
            maven("https://maven.enginehub.org/repo/") {
                name = "enginehub"
            }
        }
        filter {
            includeGroup("com.sk89q.worldguard") // WorldGuard
            includeGroup("com.sk89q.worldedit") // WorldEdit
        }
    }
}

sourceSets {
    // Mixin source set - 原项目的 Mixin 代码（需要先定义，因为 main 依赖它）
    create("mixin") {
        java.srcDirs("src/mixin/java")
        resources.srcDirs("src/mixin/resources")
    }
    
    main {
        java.srcDirs("src/main/java")
        resources.srcDirs("src/main/resources")
        // main 依赖 mixin（原项目的 AkiAsyncPlugin 引用了 mixin 中的类）
        compileClasspath += sourceSets["mixin"].output
        runtimeClasspath += sourceSets["mixin"].output
    }
    
    // Ignite source set - Ignite 适配专用代码
    create("ignite") {
        java.srcDirs("src/ignite/java")
        resources.srcDirs("src/ignite/resources")
        compileClasspath += sourceSets.main.get().output
        compileClasspath += sourceSets.main.get().compileClasspath
        compileClasspath += sourceSets["mixin"].output
    }
}

// 配置 mixin 的依赖（在 sourceSets 之后）
configurations {
    named("mixinCompileClasspath") {
        extendsFrom(configurations.compileClasspath.get())
    }
    named("mixinRuntimeClasspath") {
        extendsFrom(configurations.runtimeClasspath.get())
    }
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paperApi)

    compileOnly(libs.igniteApi)
    compileOnly(libs.spongeMixin)
    compileOnly(libs.mixinExtras)
    compileOnly(libs.fastutil)
    
    // Mixin source set dependencies
    "mixinCompileOnly"(libs.igniteApi)
    "mixinCompileOnly"(libs.spongeMixin)
    "mixinCompileOnly"(libs.mixinExtras)
    "mixinCompileOnly"(libs.fastutil)
    
    // Ignite source set dependencies
    "igniteCompileOnly"(libs.igniteApi)
    "igniteCompileOnly"(libs.spongeMixin)
    "igniteCompileOnly"(libs.mixinExtras)
    "igniteCompileOnly"(libs.fastutil)
    
    // Optional plugin dependencies for land protection integration
    // Note: These are optional dependencies, the plugin will work without them
    // They are only needed if you want to use land protection features
    // The plugin uses reflection at runtime to detect these plugins, so these dependencies
    // are only needed for compilation (to avoid warnings)
    
    compileOnly("com.github.Zrips:Residence:6.0.0.1") {
        isTransitive = false
    }
    
    // LandsAPI - try different version formats if this doesn't work
    // JitPack format: com.github.USER:REPO:VERSION
    // If this fails, you can comment it out - the plugin will still work at runtime
    compileOnly("com.github.angeschossen:LandsAPI:6.22.0") {
        isTransitive = false
    }
    
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.1.0-SNAPSHOT") {
        isTransitive = false
    }
    
    // BlockLocker Integration (v3.2.15)
    compileOnly("com.github.rutgerkok:BlockLocker:1.13") {
        isTransitive = false
    }
    
    // ViaVersion API
    compileOnly("com.viaversion:viaversion-api:5.1.1") {
        isTransitive = false
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    
    named<ProcessResources>("processMixinResources") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    
    named<ProcessResources>("processIgniteResources") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    shadowJar {
        archiveFileName.set("${project.name}-${project.version}.jar")
        // 包含所有 source sets
        from(sourceSets.main.get().output)
        from(sourceSets["mixin"].output)
        from(sourceSets["ignite"].output)
    }
    
    build {
        dependsOn(shadowJar)
    }
}

// Configure access wideners if needed for compilation
// If the code accesses private members via access wideners during compilation:
// paperweight.accessWideners.from(file("src/mixin/resources/aki-async.accesswidener"))
