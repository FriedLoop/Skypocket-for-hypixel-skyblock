pluginManagement {
    repositories {
        // Deftu's multiversion / bloom plugins live outside the portal, and their toolkit pulls
        // architectury-loom from Essential's and pack200 from Architectury's maven.
        maven("https://maven.deftu.dev/releases/")
        maven("https://maven.deftu.dev/snapshots/")
        maven("https://repo.essential.gg/repository/maven-public")
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "skypocket"
// The shared build script is applied by the version subprojects, so the root needs its own.
rootProject.buildFileName = "root.gradle.kts"

// One subproject per Minecraft version. Adding a version = one line here + a versions/<name>/src.
listOf("26.2-fabric", "26.3-fabric").forEach { name ->
    include(":$name")
    project(":$name").apply {
        projectDir = file("versions/$name")
        buildFileName = "../../build.gradle.kts"
    }
}
