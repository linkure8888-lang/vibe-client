package com.vibe.client.plugin

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vibe.client.VibeClient
import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile

/**
 * Discovers, validates, loads and unloads plugins from `plugins/`.
 *
 * Safety rules that this class enforces:
 *  - discovery never executes plugin code, only reads manifests;
 *  - a broken plugin becomes a [State.BROKEN] entry with a reason, never an
 *    exception that reaches the game loop;
 *  - an exception from any plugin callback disables that plugin and is logged;
 *  - nothing here blocks on the network or on the render thread.
 *
 * All state is mutated on the game thread (screen callbacks / init).
 */
object PluginManager {

    enum class State { ENABLED, DISABLED, BROKEN, UNSUPPORTED }

    /** One discovered plugin and everything we know about it. */
    class LoadedPlugin(
        val manifest: PluginManifest?,
        val sourceDir: File?,
        val jarFile: File?,
        var state: State,
        var reason: String? = null
    ) {
        val id: String get() = manifest?.id ?: (sourceDir?.name ?: jarFile?.nameWithoutExtension ?: "?")
        val name: String get() = manifest?.name ?: id

        internal var loader: URLClassLoader? = null
        internal var instance: Plugin? = null
        internal var host: PluginHostImpl? = null
    }

    private const val DISABLED_MARKER = "disabled.marker"
    private const val MAX_PLUGINS = 64

    private val gson = Gson()

    private var root: File? = null
    private val plugins = ArrayList<LoadedPlugin>(16)

    val all: List<LoadedPlugin> get() = plugins

    /**
     * Scans the plugins folder and loads everything that is enabled.
     * Call once from the client initializer.
     */
    fun init(pluginsDir: File) {
        root = pluginsDir
        if (!pluginsDir.exists()) pluginsDir.mkdirs()
        plugins.clear()

        val entries = try {
            pluginsDir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
        } catch (t: Throwable) {
            VibeClient.logger.warn("Could not list plugins folder: ${t.message}")
            emptyList()
        }

        for (entry in entries.take(MAX_PLUGINS)) {
            try {
                when {
                    entry.isDirectory -> discoverFolder(entry)
                    entry.isFile && entry.name.endsWith(".jar", ignoreCase = true) -> discoverJar(entry)
                }
            } catch (t: Throwable) {
                VibeClient.logger.warn("Plugin discovery failed for ${entry.name}: ${t.message}")
            }
        }

        // Load the ones that are enabled; failures degrade to BROKEN.
        for (plugin in plugins) {
            if (plugin.state == State.ENABLED) load(plugin)
        }

        VibeClient.logger.info(
            "Plugins: ${plugins.size} found, " +
                "${plugins.count { it.state == State.ENABLED }} enabled"
        )
    }

    // -- Discovery ---------------------------------------------------------

    private fun discoverFolder(dir: File) {
        val manifestFile = File(dir, PluginManifest.FILE_NAME)
        if (!manifestFile.isFile) return  // not a plugin folder, ignore silently

        val manifest = readManifest(manifestFile.readText())
        val jar = File(dir, "plugin.jar").takeIf { it.isFile }
        add(LoadedPlugin(manifest, dir, jar, initialState(dir, manifest), validationReason(manifest)))
    }

    private fun discoverJar(jar: File) {
        val manifestText = try {
            ZipFile(jar).use { zip ->
                val entry = zip.getEntry(PluginManifest.FILE_NAME) ?: return
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        } catch (t: Throwable) {
            VibeClient.logger.warn("Could not read ${jar.name}: ${t.message}")
            return
        }

        val manifest = readManifest(manifestText)
        add(LoadedPlugin(manifest, null, jar, initialState(jar, manifest), validationReason(manifest)))
    }

    private fun readManifest(text: String): PluginManifest? = try {
        PluginManifest.parse(gson.fromJson(text, JsonObject::class.java))
    } catch (t: Throwable) {
        VibeClient.logger.warn("Manifest parse failed: ${t.message}")
        null
    }

    /** Chooses ENABLED / DISABLED / BROKEN / UNSUPPORTED for a fresh entry. */
    private fun initialState(source: File, manifest: PluginManifest?): State {
        if (manifest == null) return State.BROKEN
        if (manifest.apiVersion != PluginManifest.SUPPORTED_API) return State.UNSUPPORTED
        val marker = File(source, DISABLED_MARKER)
        val markerForJar = File(source.parentFile, source.nameWithoutExtension + ".disabled")
        val disabled = marker.isFile || markerForJar.isFile
        return if (disabled) State.DISABLED else State.ENABLED
    }

    private fun validationReason(manifest: PluginManifest?): String? {
        if (manifest == null) return "invalid or missing ${PluginManifest.FILE_NAME}"
        if (manifest.apiVersion != PluginManifest.SUPPORTED_API) {
            return "requires apiVersion ${manifest.apiVersion}, this client supports ${PluginManifest.SUPPORTED_API}"
        }
        return manifest.problems.firstOrNull()
    }

    /** Rejects duplicate ids: the first one wins, the rest are BROKEN. */
    private fun add(plugin: LoadedPlugin) {
        val duplicate = plugins.any { it.id == plugin.id }
        if (duplicate) {
            plugin.state = State.BROKEN
            plugin.reason = "duplicate plugin id '${plugin.id}'"
        }
        plugins += plugin
    }

    // -- Lifecycle ---------------------------------------------------------

    private fun load(plugin: LoadedPlugin) {
        val manifest = plugin.manifest ?: return
        val host = createHost(manifest)
        plugin.host = host

        // Manifest-declared lines need no code: push them straight into the HUD.
        // Re-applied by enable(), dropped by disable().
        if (PluginPermission.HUD in manifest.permissions) {
            for (line in manifest.lines) {
                PluginHud.put(manifest.id, line.id, line.text, line.x, line.y, line.color)
            }
        }

        // Declarative plugin: nothing to instantiate.
        val mainClass = manifest.mainClass
        if (mainClass == null) {
            safeCall(plugin, "onLoad") { }
            return
        }

        val jar = plugin.jarFile
        if (jar == null) {
            plugin.state = State.BROKEN
            plugin.reason = "mainClass declared but plugin.jar is missing"
            return
        }

        try {
            val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), Plugin::class.java.classLoader)
            plugin.loader = loader
            val type = Class.forName(mainClass, true, loader)
            val instance = type.getDeclaredConstructor().newInstance()
            if (instance !is Plugin) {
                plugin.state = State.BROKEN
                plugin.reason = "$mainClass does not implement Plugin"
                return
            }
            plugin.instance = instance
            safeCall(plugin, "onLoad") { instance.onLoad(host) }
            safeCall(plugin, "onEnable") { instance.onEnable(host) }
        } catch (t: Throwable) {
            plugin.state = State.BROKEN
            plugin.reason = t.message ?: t.javaClass.simpleName
            VibeClient.logger.warn("Plugin '${plugin.id}' failed to load: ${plugin.reason}")
        }
    }

    /** Enables a plugin at runtime (from the Plugins tab). */
    fun enable(plugin: LoadedPlugin): Boolean {
        if (plugin.state == State.ENABLED) return true
        if (plugin.manifest == null) return false
        if (plugin.manifest.apiVersion != PluginManifest.SUPPORTED_API) return false

        clearDisabledMarker(plugin)
        plugin.state = State.ENABLED
        plugin.reason = null

        val instance = plugin.instance
        val host = plugin.host ?: createHost(plugin.manifest).also { plugin.host = it }
        if (instance == null) {
            load(plugin)           // first activation instantiates the class
        } else {
            safeCall(plugin, "onEnable") { instance.onEnable(host) }
        }
        return plugin.state == State.ENABLED
    }

    /** Disables a plugin; its HUD lines disappear immediately. */
    fun disable(plugin: LoadedPlugin) {
        if (plugin.state != State.ENABLED) return

        val instance = plugin.instance
        val host = plugin.host
        if (instance != null && host != null) {
            safeCall(plugin, "onDisable") { instance.onDisable(host) }
        }
        PluginHud.removeAll(plugin.id)
        writeDisabledMarker(plugin)
        plugin.state = State.DISABLED
    }

    /** Drops the classloader so the jar can be replaced on the next start. */
    fun unload(plugin: LoadedPlugin) {
        disable(plugin)
        val instance = plugin.instance
        val host = plugin.host
        if (instance != null && host != null) {
            safeCall(plugin, "onUnload") { instance.onUnload(host) }
        }
        plugin.instance = null
        try {
            plugin.loader?.close()
        } catch (t: Throwable) {
            VibeClient.logger.warn("Could not close loader for '${plugin.id}': ${t.message}")
        }
        plugin.loader = null
        PluginHud.removeAll(plugin.id)
    }

    /** Called on client shutdown: gives every enabled plugin a clean onUnload. */
    fun shutdown() {
        for (plugin in plugins) {
            val instance = plugin.instance ?: continue
            val host = plugin.host ?: continue
            safeCall(plugin, "onUnload") { instance.onUnload(host) }
        }
    }

    // -- Helpers -----------------------------------------------------------

    private fun createHost(manifest: PluginManifest): PluginHostImpl {
        val base = root ?: File(".")
        val sourceDir = File(base, manifest.id)
        val dataDir = File(sourceDir, "data")
        val configDir = File(base.parentFile ?: base, "config")
        val pluginConfigDir = File(configDir, "plugins")
        val usageFile = File(pluginConfigDir, "${manifest.id}.usage.json")
        return PluginHostImpl(
            pluginId = manifest.id,
            permissions = manifest.permissions,
            dataDir = dataDir,
            configFile = File(pluginConfigDir, "${manifest.id}.json"),
            budget = PluginBudget(usageFile, manifest.requestsPerDay)
        )
    }

    private fun safeCall(plugin: LoadedPlugin, name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            plugin.state = State.BROKEN
            plugin.reason = "$name threw: ${t.message ?: t.javaClass.simpleName}"
            VibeClient.logger.warn("Plugin '${plugin.id}' $name failed: ${t.message}")
            PluginHud.removeAll(plugin.id)
        }
    }

    private fun writeDisabledMarker(plugin: LoadedPlugin) {
        try {
            val source = plugin.sourceDir
            if (source != null) {
                File(source, DISABLED_MARKER).writeText("disabled\n")
            } else {
                val jar = plugin.jarFile ?: return
                File(jar.parentFile, jar.nameWithoutExtension + ".disabled").writeText("disabled\n")
            }
        } catch (t: Throwable) {
            VibeClient.logger.warn("Could not mark '${plugin.id}' disabled: ${t.message}")
        }
    }

    private fun clearDisabledMarker(plugin: LoadedPlugin) {
        try {
            val source = plugin.sourceDir
            if (source != null) {
                File(source, DISABLED_MARKER).delete()
            } else {
                val jar = plugin.jarFile ?: return
                File(jar.parentFile, jar.nameWithoutExtension + ".disabled").delete()
            }
        } catch (t: Throwable) {
            VibeClient.logger.warn("Could not unmark '${plugin.id}': ${t.message}")
        }
    }
}
