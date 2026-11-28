import groovy.json.JsonOutput
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

buildscript {
    repositories { mavenCentral() }
    // Reads home.yaml at build time only; the app itself gets JSON and has no YAML dependency.
    dependencies { classpath("org.yaml:snakeyaml:2.3") }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") // Glance is Compose-based; Kotlin itself is built into AGP 9
}

android {
    namespace = "dev.faisha.porchlight"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.faisha.porchlight"
        minSdk = 31          // rounded Glance corners + reconfigurable widgets
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }
}

// Bundle the checked home config as assets/home.json in every variant.
androidComponents {
    onVariants { variant ->
        val task = tasks.register<GenerateHomeConfig>(
            "generate${variant.name.replaceFirstChar { it.uppercase() }}HomeConfig",
        ) {
            userConfig.from(rootProject.file("home.yaml"))
            exampleConfig.from(rootProject.file("home.example.yaml"))
            iconNames.set(layout.projectDirectory.file("src/main/assets/mdi/codepoints.txt"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, GenerateHomeConfig::outputDir)
    }
}

dependencies {
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json is a stub in JVM unit tests; this is the real implementation.
    testImplementation("org.json:json:20240303")
}

/**
 * Checks home.yaml (or home.example.yaml when there is no home.yaml) and writes it out as
 * home.json, so the app reads plain JSON and a mistake fails the build rather than the widget.
 */
abstract class GenerateHomeConfig : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val userConfig: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val exampleConfig: ConfigurableFileCollection

    /** The bundled MDI name list. `icon: x` (or `icon: mdi:x`) must be one of its names. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val iconNames: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val icons = iconNames.get().asFile.readLines()
            .mapNotNull { line -> line.trim().split(' ').takeIf { it.size == 2 }?.first() }
            .toSet()
        fun check(file: File): HomeConfigChecker.Result {
            val root = file.reader().use { Yaml(SafeConstructor(LoaderOptions())).load<Any?>(it) }
            return HomeConfigChecker(icons).check(root)
        }

        // Both are checked on every build, so the committed example can't go stale unnoticed.
        val user = userConfig.files.firstOrNull { it.isFile }
        val example = exampleConfig.files.firstOrNull { it.isFile }
        val results = listOfNotNull(user, example).associateWith(::check)
        val problems = results.filterValues { it.errors.isNotEmpty() }
        if (problems.isNotEmpty()) {
            throw GradleException(
                problems.entries.joinToString("\n\n") { (file, result) ->
                    "${file.name} has ${result.errors.size} problem(s):\n" +
                        result.errors.joinToString("\n") { "  - $it" }
                },
            )
        }

        // The app gets home.yaml if there is one, otherwise the example.
        val source = user ?: example
            ?: throw GradleException("No home.yaml or home.example.yaml in the project root.")
        val out = outputDir.get().asFile
        out.mkdirs()
        out.resolve("home.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(results.getValue(source).json)))
        logger.lifecycle("Home config: ${source.name}")
    }
}

/** Validates the parsed YAML and turns it into the JSON shape the app's HomeConfig reads. */
class HomeConfigChecker(private val icons: Set<String>) {
    class Result(val json: Map<String, Any?>, val errors: List<String>)

    private val errors = mutableListOf<String>()

    fun check(root: Any?): Result {
        val top = obj(root, "the file", setOf("rooms", "vacuum", "tv", "all_off_script", "sensors"))
            ?: return Result(emptyMap(), errors)

        val rooms = list(top["rooms"], "rooms", required = true).mapIndexedNotNull { i, item ->
            val at = "rooms[$i]"
            val o = obj(item, at, setOf("area", "label", "icon", "clean_script")) ?: return@mapIndexedNotNull null
            linkedMapOf(
                "area" to text(o["area"], "$at.area", required = true, pattern = AREA, hint = "an area ID like living_room"),
                "label" to text(o["label"], "$at.label", required = true),
                "icon" to icon(o["icon"], "$at.icon", default = "lightbulb-outline"),
                "clean_script" to entityId(o["clean_script"], "$at.clean_script", "script"),
            )
        }
        if (top.containsKey("rooms") && rooms.isEmpty() && errors.isEmpty()) errors += "rooms: add at least one room"
        rooms.groupBy { it["area"] }.filter { (area, list) -> area != null && list.size > 1 }
            .keys.forEach { errors += "rooms: area '$it' is listed more than once" }

        val tv = top["tv"]?.let { item ->
            obj(item, "tv", setOf("entity", "label"))?.let { o ->
                linkedMapOf(
                    "entity" to entityId(o["entity"], "tv.entity", "media_player", required = true),
                    "label" to text(o["label"], "tv.label"),
                )
            }
        }

        val sensors = list(top["sensors"], "sensors").mapIndexedNotNull { i, item ->
            val at = "sensors[$i]"
            val o = obj(item, at, setOf("entity", "label", "icon", "unit")) ?: return@mapIndexedNotNull null
            linkedMapOf(
                "entity" to entityId(o["entity"], "$at.entity", null, required = true),
                "label" to text(o["label"], "$at.label", required = true),
                "icon" to icon(o["icon"], "$at.icon", default = "eye-outline"),
                "unit" to text(o["unit"], "$at.unit"),
            )
        }

        val json = linkedMapOf(
            "rooms" to rooms,
            "vacuum" to entityId(top["vacuum"], "vacuum", "vacuum"),
            "tv" to tv,
            "all_off_script" to entityId(top["all_off_script"], "all_off_script", "script"),
            "sensors" to sensors,
        )
        return Result(json, errors)
    }

    private fun obj(value: Any?, at: String, keys: Set<String>): Map<*, *>? {
        if (value !is Map<*, *>) {
            errors += "$at: expected a mapping (key: value lines)"
            return null
        }
        (value.keys.map { it.toString() } - keys).forEach {
            errors += "$at: unknown key '$it' (expected one of ${keys.joinToString()})"
        }
        return value
    }

    private fun list(value: Any?, at: String, required: Boolean = false): List<Any?> = when (value) {
        null -> {
            if (required) errors += "$at: required"
            emptyList()
        }
        is List<*> -> value
        else -> {
            errors += "$at: expected a list (lines starting with '- ')"
            emptyList()
        }
    }

    private fun text(
        value: Any?,
        at: String,
        required: Boolean = false,
        pattern: Regex? = null,
        hint: String? = null,
    ): String? {
        if (value == null) {
            if (required) errors += "$at: required"
            return null
        }
        val s = value.toString().trim()
        when {
            value is Map<*, *> || value is List<*> -> errors += "$at: expected text"
            s.isEmpty() && required -> errors += "$at: required"
            pattern != null && !pattern.matches(s) -> errors += "$at: '$s' should be ${hint ?: "like $pattern"}"
            else -> return s.ifEmpty { null }
        }
        return null
    }

    private fun entityId(value: Any?, at: String, domain: String?, required: Boolean = false): String? {
        val id = text(value, at, required, ENTITY, "an entity ID like ${domain ?: "sensor"}.my_thing") ?: return null
        if (domain != null && id.substringBefore('.') != domain) {
            errors += "$at: '$id' should be a $domain.* entity"
            return null
        }
        return id
    }

    private fun icon(value: Any?, at: String, default: String): String {
        // Accept HA's `mdi:sofa` spelling as well as `sofa`.
        val name = text(value, at)?.removePrefix("mdi:") ?: return default
        if (name !in icons) {
            val close = icons.filter { name in it }.sortedBy { it.length }.take(5)
            errors += "$at: no MDI icon '$name'. " +
                (if (close.isNotEmpty()) "Did you mean ${close.joinToString()}? " else "") +
                "Find names at https://pictogrammers.com/library/mdi/"
        }
        return name
    }

    private companion object {
        val AREA = Regex("[a-z0-9_]+")
        val ENTITY = Regex("[a-z0-9_]+\\.[a-z0-9_]+")
    }
}
