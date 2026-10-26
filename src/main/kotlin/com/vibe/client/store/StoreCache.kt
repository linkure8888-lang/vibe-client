package com.vibe.client.store

import com.vibe.client.VibeClient
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Small disk + memory cache for Modrinth catalog responses.
 *
 * Why: Modrinth rate-limits anonymous clients (429). Reopening the store or
 * paging back and forth must NOT hit the network every time, so a catalog page
 * is reused for [TTL_MS].
 *
 * Rules:
 *  - Never throws. A broken cache entry is deleted and treated as a miss.
 *  - Bounded: at most [MAX_FILES] files, oldest pruned on write.
 *  - Thread-safe: lookups happen on worker threads, writes too.
 */
object StoreCache {

    private const val TTL_MS = 10 * 60 * 1000L      // 10 minutes
    private const val MAX_FILES = 200

    private class Entry(val body: String, val storedAt: Long)

    private val memory = ConcurrentHashMap<String, Entry>()

    @Volatile private var dir: File? = null

    private fun directory(): File {
        dir?.let { return it }
        val gameDir = try {
            FabricLoader.getInstance().gameDir.toFile()
        } catch (t: Throwable) {
            File(".")
        }
        val d = File(File(gameDir, "config"), "vibe-store-cache")
        dir = d
        return d
    }

    /** Returns a cached body that is still fresh, or null. */
    fun get(key: String): String? {
        val now = System.currentTimeMillis()

        memory[key]?.let { entry ->
            if (now - entry.storedAt <= TTL_MS) return entry.body
            memory.remove(key)
        }

        return try {
            val file = fileFor(key)
            if (!file.isFile) return null
            if (now - file.lastModified() > TTL_MS) {
                file.delete()
                return null
            }
            val body = file.readText()
            memory[key] = Entry(body, file.lastModified())
            body
        } catch (t: Throwable) {
            VibeClient.logger.warn("Store cache read failed: ${t.message}")
            null
        }
    }

    /** Stores a fresh body. Failures are logged, never thrown. */
    fun put(key: String, body: String) {
        val now = System.currentTimeMillis()
        memory[key] = Entry(body, now)

        try {
            val d = directory()
            if (!d.isDirectory && !d.mkdirs()) return
            val target = fileFor(key)
            val tmp = File(d, target.name + ".tmp")
            tmp.writeText(body)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return
            }
            prune(d)
        } catch (t: Throwable) {
            VibeClient.logger.warn("Store cache write failed: ${t.message}")
        }
    }

    /** Drops expired entries and keeps the newest [MAX_FILES]. */
    private fun prune(d: File) {
        val files = d.listFiles()?.filter { it.isFile } ?: return
        val now = System.currentTimeMillis()
        files.filter { now - it.lastModified() > TTL_MS }.forEach { it.delete() }

        val remaining = d.listFiles()?.filter { it.isFile } ?: return
        if (remaining.size <= MAX_FILES) return
        remaining.sortedBy { it.lastModified() }
            .take(remaining.size - MAX_FILES)
            .forEach { it.delete() }
    }

    private fun fileFor(key: String): File {
        // Stable, filesystem-safe name: FNV-1a over the key.
        var hash = -0x340d631b7bdddcdbL
        for (ch in key) {
            hash = hash xor ch.code.toLong()
            hash *= 0x100000001b3L
        }
        return File(directory(), "c%016x.json".format(hash))
    }
}
