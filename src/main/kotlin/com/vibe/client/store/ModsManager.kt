package com.vibe.client.store

import com.vibe.client.VibeClient
import java.io.File

/**
 * Works with the real `mods/` folder of the running game.
 *
 * Enable/disable is done the standard Fabric way: a disabled mod is renamed
 * `foo.jar` -> `foo.jar.disabled`. No game restart is faked here; the UI tells
 * the user a restart is required.
 */
object ModsManager {

    const val DISABLED_SUFFIX = ".disabled"

    /** One installed mod file. */
    data class InstalledMod(
        val file: File,
        val displayName: String,
        val enabled: Boolean,
        val sizeBytes: Long
    )

    private var modsDir: File? = null

    fun init(dir: File) {
        modsDir = dir
        if (!dir.exists()) dir.mkdirs()
    }

    val directory: File?
        get() = modsDir

    /** Lists every jar in mods/ (enabled and disabled), sorted by name. */
    fun list(): List<InstalledMod> {
        val dir = modsDir ?: return emptyList()
        val files = dir.listFiles { f -> f.isFile } ?: return emptyList()

        return files
            .filter { it.name.endsWith(".jar") || it.name.endsWith(".jar" + DISABLED_SUFFIX) }
            .map { file ->
                val disabled = file.name.endsWith(DISABLED_SUFFIX)
                val cleanName = file.name
                    .removeSuffix(DISABLED_SUFFIX)
                    .removeSuffix(".jar")
                InstalledMod(
                    file = file,
                    displayName = cleanName,
                    enabled = !disabled,
                    sizeBytes = file.length()
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }

    /** Renames the file to flip its state. Returns true on success. */
    fun setEnabled(mod: InstalledMod, enabled: Boolean): Boolean {
        if (mod.enabled == enabled) return true

        val target = if (enabled) {
            File(mod.file.parentFile, mod.file.name.removeSuffix(DISABLED_SUFFIX))
        } else {
            File(mod.file.parentFile, mod.file.name + DISABLED_SUFFIX)
        }

        if (target.exists()) {
            VibeClient.logger.warn("Cannot toggle ${mod.file.name}: ${target.name} already exists")
            return false
        }

        val ok = mod.file.renameTo(target)
        if (!ok) VibeClient.logger.warn("Could not rename ${mod.file.name} -> ${target.name}")
        return ok
    }

    /** Deletes a mod file from disk. */
    fun delete(mod: InstalledMod): Boolean {
        val ok = mod.file.delete()
        if (!ok) VibeClient.logger.warn("Could not delete ${mod.file.name}")
        return ok
    }

    /** True when a file with this name already exists (enabled or disabled). */
    fun isInstalled(fileName: String): Boolean {
        val dir = modsDir ?: return false
        return File(dir, fileName).exists() || File(dir, fileName + DISABLED_SUFFIX).exists()
    }
}
