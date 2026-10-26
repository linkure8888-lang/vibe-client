package com.vibe.client.store

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vibe.client.VibeClient
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Modrinth public API client (no key required).
 *
 * Endpoints used:
 *   GET /v2/search?query=&facets=...   — catalog page
 *   GET /v2/project/{slug}/version     — downloadable files for a version
 *
 * Everything here is blocking; the UI calls it from a worker thread.
 */
object ModrinthApi {

    private const val BASE = "https://api.modrinth.com/v2"

    private val gson = Gson()

    /** One row in the store list. */
    data class Project(
        val slug: String,
        val title: String,
        val description: String,
        val downloads: Long,
        val iconUrl: String?
    )

    /** One downloadable file of a project. */
    data class VersionFile(
        val fileName: String,
        val downloadUrl: String,
        val versionName: String,
        val gameVersions: List<String>
    )

    /**
     * Searches Fabric mods for [mcVersion], sorted by downloads (trending).
     * Returns an empty list on any failure so the UI can show "no mods".
     */
    fun search(query: String, mcVersion: String, limit: Int = 25, offset: Int = 0): List<Project> {
        // facets is a JSON array of arrays: [["categories:fabric"],["versions:1.21.1"]]
        val facets = "[[\"categories:fabric\"],[\"versions:$mcVersion\"]]"
        val encodedFacets = URLEncoder.encode(facets, StandardCharsets.UTF_8)
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val url = "$BASE/search?query=$encodedQuery&facets=$encodedFacets" +
            "&limit=$limit&offset=$offset&index=downloads"

        // One cache slot per page: revisiting a page costs nothing.
        val cacheKey = "search:$mcVersion:$limit:$offset:$query"
        val body = Http.get(url, cacheKey = cacheKey) ?: return emptyList()
        return try {
            val root = gson.fromJson(body, JsonObject::class.java)
            val hits = root.getAsJsonArray("hits") ?: return emptyList()
            hits.mapNotNull { element ->
                val obj = element.asJsonObject
                val slug = obj.get("slug")?.asString ?: return@mapNotNull null
                val icon = obj.get("icon_url")?.takeIf { !it.isJsonNull }?.asString
                Project(
                    slug = slug,
                    title = obj.get("title")?.asString ?: slug,
                    description = obj.get("description")?.asString ?: "",
                    downloads = obj.get("downloads")?.asLong ?: 0L,
                    iconUrl = icon
                )
            }
        } catch (t: Throwable) {
            VibeClient.logger.warn("Modrinth search parse failed: ${t.message}")
            emptyList()
        }
    }

    /**
     * Finds the newest Fabric file of [slug] that targets [mcVersion].
     * Returns null when the project has no build for that version.
     */
    fun findFile(slug: String, mcVersion: String): VersionFile? {
        val url = "$BASE/project/$slug/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22$mcVersion%22%5D"
        val body = Http.get(url, cacheKey = "version:$mcVersion:$slug") ?: return null
        return try {
            val array = gson.fromJson(body, com.google.gson.JsonArray::class.java)
            val first = array.firstOrNull()?.asJsonObject ?: return null

            val files = first.getAsJsonArray("files") ?: return null
            // Prefer the primary file flag, else the first entry.
            val fileObj = files.map { it.asJsonObject }
                .firstOrNull { it.get("primary")?.asBoolean == true }
                ?: files.firstOrNull()?.asJsonObject
                ?: return null

            val downloadUrl = fileObj.get("url")?.asString ?: return null
            val fileName = fileObj.get("filename")?.asString ?: "$slug.jar"

            VersionFile(
                fileName = fileName,
                downloadUrl = downloadUrl,
                versionName = first.get("version_number")?.asString ?: "",
                gameVersions = first.getAsJsonArray("game_versions")
                    ?.map { it.asString } ?: emptyList()
            )
        } catch (t: Throwable) {
            VibeClient.logger.warn("Modrinth version parse failed for $slug: ${t.message}")
            null
        }
    }

    /** Downloads [file] into [modsDir]. Returns the written file, or null. */
    fun download(file: VersionFile, modsDir: File): File? {
        val destination = File(modsDir, file.fileName)
        if (destination.exists()) return destination
        return if (Http.download(file.downloadUrl, destination)) destination else null
    }
}
