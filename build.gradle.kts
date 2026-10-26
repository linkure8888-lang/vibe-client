plugins {
    id("fabric-loom") version "1.7-SNAPSHOT"
    kotlin("jvm") version "2.0.20"
}

// IMPORTANT: Gradle does NOT camelCase property names.
// `mod_version` in gradle.properties is readable as property("mod_version"),
// NOT as `val modVersion by project` (that looks for a literal `modVersion`).
val minecraftVersion = property("minecraft_version") as String
val yarnMappings = property("yarn_mappings") as String
val loaderVersion = property("loader_version") as String
val modVersion = property("mod_version") as String
val mavenGroup = property("maven_group") as String
val archivesBaseName = property("archives_base_name") as String
val fabricKotlinVersion = property("fabric_kotlin_version") as String

version = modVersion
group = mavenGroup
base.archivesName.set(archivesBaseName)

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.102.0+$minecraftVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}
