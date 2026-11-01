import java.lang.module.ModuleDescriptor
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    // Version of loom is pinned here because every version subproject shares this script.
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("dev.deftu.gradle.tools.bloom")
}

/** Subproject name carries the target version: "26.2-fabric" -> project "26.2-fabric", mc "26.2". */
val mcProject: String = project.name
val mcVersion: String = mcProject.removeSuffix("-fabric")

/** Per-version property, e.g. `fabric_version.26.2`; missing keys fail loudly instead of defaulting. */
fun versioned(name: String, fallback: String? = null): String =
    (project.findProperty("$name.$mcVersion")?.toString() ?: fallback)
        ?: throw AssertionError("gradle.properties needs '$name.$mcVersion' for $mcProject")

/** Numeric Minecraft version comparison, used to gate the Bloom rules. */
fun atLeast(version: String): Boolean =
    ModuleDescriptor.Version.parse(mcVersion) >= ModuleDescriptor.Version.parse(version)


/** "26.3" -> 260300, the same encoding the preprocessor uses for its version nodes. */
fun versionCode(v: String): Int {
    val parts = v.split(".").map { it.toIntOrNull() ?: 0 }
    return (parts.getOrElse(0) { 0 } * 10000) + (parts.getOrElse(1) { 0 } * 100)
            + parts.getOrElse(2) { 0 }
}

/**
 * Resolves `//#if MC>=260300` style directives for one target version.
 *
 * <p>The multiversion plugin in use does not generate one version's tree from another here, so this
 * does it explicitly: only `MC` and integer literals are understood, which is all the build needs.
 * Nested blocks are supported, `//#elseif` and `//#else` included.
 */
fun resolveDirectives(text: String, mc: Int): String {
    fun eval(expr: String): Boolean {
        val e = expr.trim()
        val m = Regex("([A-Za-z_][A-Za-z0-9_]*|\\d+)\\s*(>=|<=|==|!=|>|<)\\s*([A-Za-z_][A-Za-z0-9_]*|\\d+)")
                .find(e) ?: return e == "true"
        fun value(token: String): Long = if (token == "MC") mc.toLong() else token.toLongOrNull() ?: 0L
        val left = value(m.groupValues[1])
        val right = value(m.groupValues[3])
        return when (m.groupValues[2]) {
            ">=" -> left >= right
            "<=" -> left <= right
            "==" -> left == right
            "!=" -> left != right
            ">" -> left > right
            else -> left < right
        }
    }

    val out = StringBuilder(text.length)
    // Each frame: whether the enclosing block is active, whether this one is, whether else was seen.
    val active = ArrayDeque<Boolean>()
    val taken = ArrayDeque<Boolean>()
    val elseSeen = ArrayDeque<Boolean>()
    fun live(): Boolean = active.all { it }

    text.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("//#if ") -> {
                val ok = eval(trimmed.removePrefix("//#if "))
                active.addLast(live() && ok)
                taken.addLast(ok)
                elseSeen.addLast(false)
            }
            trimmed.startsWith("//#elseif ") -> {
                if (taken.isEmpty()) return@forEach
                val parentLive = active.dropLast(1).all { it }
                val already = taken.last()
                val ok = !already && eval(trimmed.removePrefix("//#elseif "))
                active.removeLast()
                active.addLast(parentLive && ok)
                taken.removeLast()
                taken.addLast(already || ok)
            }
            trimmed == "//#else" -> {
                if (taken.isEmpty()) return@forEach
                val parentLive = active.dropLast(1).all { it }
                val already = taken.last()
                active.removeLast()
                active.addLast(parentLive && !already)
                elseSeen.removeLast()
                elseSeen.addLast(true)
            }
            trimmed == "//#endif" -> {
                if (active.isNotEmpty()) active.removeLast()
                if (taken.isNotEmpty()) taken.removeLast()
                if (elseSeen.isNotEmpty()) elseSeen.removeLast()
            }
            else -> if (live()) out.appendLine(line)
        }
    }
    return out.toString()
}
version = project.property("mod_version").toString()
group = project.property("maven_group").toString()

configure<BasePluginExtension> {
    archivesName.set("${project.property("archives_base_name")}-$mcProject")
}

repositories {
    // ModMenu (only used for the optional config-screen entrypoint)
    maven { name = "TerraformersMC"; url = uri("https://maven.terraformersmc.com/releases/") }
    // JLayer (MP3 decoding for custom mention-alert sounds)
    mavenCentral()
}

dependencies {
    "minecraft"("com.mojang:minecraft:$mcVersion")

    implementation("net.fabricmc:fabric-loader:${versioned("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${versioned("fabric_version")}")

    // ModMenu is optional: compile against it, but do not require it at runtime.
    compileOnly("com.terraformersmc:modmenu:${versioned("modmenu_version")}")

    // JLayer decodes MP3 for custom mention-alert sounds (bundled into the mod jar).
    implementation("javazoom:jlayer:1.0.1")
    "include"("javazoom:jlayer:1.0.1")
}

/**
 * Source rewrites applied per target version, on top of what the preprocessor copies over.
 *
 * <p>Rules are plain substring replacements and are applied in order, so a more specific rule must come
 * before a shorter one that would match its prefix.
 */
bloom {
    if (atLeast("26.3")) {
        // 26.3 renumbered the mouse buttons (left is 1 there, 0 before), so every literal comparison
        // is routed through one helper that knows the difference.
        replacement("event.button() == 0",
                "dev.skypocket.gui.widget.MouseButtons.isLeft(event.button())")
        replacement("event.button() != 0",
                "!dev.skypocket.gui.widget.MouseButtons.isLeft(event.button())")
        replacement("event.button() == 1",
                "dev.skypocket.gui.widget.MouseButtons.isRight(event.button())")

        // Renames that are pure spelling / package moves.
        // Only part of the package moved: Std140Builder / Std140SizeCalculator stayed behind.
        replacement("com.mojang.blaze3d.buffers.GpuBuffer",
                "com.mojang.renderpearl.api.buffers.GpuBuffer")
        replacement("com.mojang.blaze3d.buffers.TransientMemory",
                "com.mojang.renderpearl.api.buffers.TransientMemory")
        replacement("net.minecraft.world.entity.monster.EnderMan",
                "net.minecraft.world.entity.monster.Enderman")
        // The import was qualified but the usages are bare, so both forms need the rule.
        replacement("EnderMan", "Enderman")
        replacement("import net.minecraft.client.renderer.ItemInHandRenderer;",
                "import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;")
        replacement("@Mixin(ItemInHandRenderer.class)",
                "@Mixin(FirstPersonHandsAndItemsRenderer.class)")
        // The GPU abstraction moved out of blaze3d into the RenderPearl API.
        replacement("com.mojang.blaze3d.textures.", "com.mojang.renderpearl.api.textures.")
        replacement("com.mojang.blaze3d.pipeline.RenderPipeline",
                "com.mojang.renderpearl.api.pipeline.RenderPipeline")
        replacement("com.mojang.blaze3d.pipeline.DepthStencilState",
                "com.mojang.renderpearl.api.pipeline.DepthStencilState")
        replacement("com.mojang.blaze3d.platform.CompareOp",
                "com.mojang.renderpearl.api.pipeline.CompareOp")

        // 26.3 replaced GLFW with an SDL-backed window layer; keys come from InputConstants now.
        replacement("import org.lwjgl.glfw.GLFW;", "")
        replacement("import org.lwjgl.glfw.GLFW", "")
        replacement("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
        // Both the imported and the fully-qualified form collapse to the same InputConstants constant.
        replacement("org.lwjgl.glfw.GLFW.GLFW_KEY_",
                "com.mojang.blaze3d.platform.InputConstants.KEY_")
        replacement("GLFW.GLFW_KEY_", "com.mojang.blaze3d.platform.InputConstants.KEY_")
        // 26.3 renamed the modifier and keypad keys (there is no KEY_ENTER / KEY_KP_ENTER at all).
        replacement("InputConstants.KEY_KP_ENTER", "InputConstants.KEY_RETURN")
        replacement("InputConstants.KEY_ENTER", "InputConstants.KEY_RETURN")
        replacement("InputConstants.KEY_LEFT_SHIFT", "InputConstants.KEY_LSHIFT")
        replacement("InputConstants.KEY_RIGHT_SHIFT", "InputConstants.KEY_RSHIFT")
        replacement("InputConstants.KEY_LEFT_CONTROL", "InputConstants.KEY_LCONTROL")
        replacement("InputConstants.KEY_RIGHT_CONTROL", "InputConstants.KEY_RCONTROL")
        replacement("org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL",
                "com.mojang.blaze3d.platform.InputConstants.CURSOR_NORMAL")
    }
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

// Sources are generated. The hand-edited tree lives in versions/<mainProject>/src with //#if
// directives; resolveSources writes the resolved copy for this version into build/preprocessed, and
// the source set points there. That way one file can carry both branches without the unused one
// breaking the compile.
val mainProjectName: String = rootProject.file("versions/mainProject").readText().trim()
val preprocessedDir = layout.buildDirectory.dir("preprocessed").get().asFile

// Generated at configuration time: a task here would need every consumer (javac, Bloom, jars,
// resources) to declare a dependency on it, and missing one is a hard failure in Gradle 9. The tree is
// small, so resolving it eagerly is cheaper than that bookkeeping.
run {
    if (preprocessedDir.exists()) {
        preprocessedDir.deleteRecursively()
    }
    rootProject.file("versions/$mainProjectName/src").copyRecursively(preprocessedDir, overwrite = true)
    val target = versionCode(mcVersion)
    preprocessedDir.walkTopDown()
        .filter { it.isFile && it.extension in setOf("java", "kt", "json", "mcmeta", "fsh", "vsh") }
        .forEach { file ->
            val text = file.readText()
            if (text.contains("//#if")) {
                file.writeText(resolveDirectives(text, target))
            }
        }
}
configure<org.gradle.api.tasks.SourceSetContainer> {
    named("main") {
        java.setSrcDirs(listOf(File(preprocessedDir, "main/java")))
        resources.setSrcDirs(listOf(File(preprocessedDir, "main/resources")))
    }
}

configure<JavaPluginExtension> {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(versioned("java_version", "25").toInt()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.register("printSources") {
    doLast {
        val ss = project.extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().getByName("main")
        println("SOURCESET java=" + ss.java.srcDirs)
        println("SOURCESET resources=" + ss.resources.srcDirs)
        ss.java.srcDirs.forEach { d -> println("  $d exists=${d.exists()}") }
        println("PROJECTDIR=" + project.projectDir)
    }
}