plugins {
    java
    alias(libs.plugins.paperweightUserdev)
    alias(libs.plugins.shadowJar)
    alias(libs.plugins.runPaper)
}

group = "org.virgil"
version = "3.2.16-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    
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
    main {
        java.srcDirs("src/main/java")
        resources.srcDirs("src/main/resources")
    }
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paperApi)

    compileOnly(libs.igniteApi)
    compileOnly(libs.spongeMixin)
    compileOnly(libs.mixinExtras)
    compileOnly(libs.fastutil)
    
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
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    shadowJar {
        archiveFileName.set("${project.name}-${project.version}.jar")
    }
    
    build {
        dependsOn(shadowJar)
    }
}

// Configure access wideners if needed for compilation
// If the code accesses private members via access wideners during compilation:
// paperweight.accessWideners.from(file("src/mixin/resources/aki-async.accesswidener"))
