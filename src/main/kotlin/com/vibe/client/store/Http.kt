package com.vibe.client.store

import com.vibe.client.VibeClient
import com.vibe.client.config.Env
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Tiny HTTP helper shared by the mod store.
 *
 * Uses the JDK's own HttpClient — zero extra libraries. Every call is
 * synchronous and MUST run off the render thread (the store screen wraps
 * calls in CompletableFuture).
 *
 * Rate-limit policy (Modrinth answers 429 when hammered):
 *  - catalog GETs go through [StoreCache] first (10 min TTL);
 *  - a 429/5xx is retried at most [MAX_RETRIES] times, honouring
 *    `Retry-After` when the server sends it, else exponential backoff;
 *  - downloads are capped at [MAX_CONCURRENT_DOWNLOADS] at a time.
 */
object Http {

    private const val MAX_RETRIES = 2
    private const val BACKOFF_BASE_MS = 750L
    private const val MAX_CONCURRENT_DOWNLOADS = 2

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val downloadSlots = Semaphore(MAX_CONCURRENT_DOWNLOADS, true)

    private const val USER_AGENT = "VibeClient/0.1.0 (Minecraft Fabric mod)"

    /**
     * GET returning the body as text, or null on any failure.
     *
     * When [cacheKey] is non-null the response is cached for 10 minutes and a
     * fresh cache hit skips the network entirely.
     */
    fun get(url: String, accept: String = "application/json", cacheKey: String? = null): String? {
        if (cacheKey != null) StoreCache.get(cacheKey)?.let { return it }

        var attempt = 0
        while (true) {
            val outcome = tryOnce(url, accept)
            when (outcome) {
                is Outcome.Ok -> {
                    if (cacheKey != null) StoreCache.put(cacheKey, outcome.body)
                    return outcome.body
                }
                is Outcome.Retry -> {
                    if (attempt >= MAX_RETRIES) return null
                    val waitMs = outcome.retryAfterMs ?: backoffMs(attempt)
                    VibeClient.logger.warn(
                        "HTTP ${outcome.status} for $url - retry ${attempt + 1}/$MAX_RETRIES in ${waitMs}ms"
                    )
                    sleep(waitMs)
                    attempt++
                }
                is Outcome.Fail -> return null
            }
        }
    }

    private sealed interface Outcome {
        class Ok(val body: String) : Outcome
        class Retry(val status: Int, val retryAfterMs: Long?) : Outcome
        class Fail(val reason: String) : Outcome
    }

    private fun tryOnce(url: String, accept: String): Outcome {
        return try {
            val builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .GET()

            // Optional Modrinth PAT raises the rate limit.
            val token = Env.get("MODRINTH_TOKEN")
            if (token.isNotBlank() && url.contains("modrinth.com")) {
                builder.header("Authorization", token)
            }

            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            val code = response.statusCode()
            when {
                code in 200..299 -> Outcome.Ok(response.body())
                code == 429 || code in 500..599 ->
                    Outcome.Retry(code, retryAfterMs(response))
                else -> {
                    VibeClient.logger.warn("HTTP $code for $url")
                    Outcome.Fail("HTTP $code")
                }
            }
        } catch (t: Throwable) {
            // Connection reset / timeout: worth one more try.
            Outcome.Retry(-1, null).also {
                VibeClient.logger.warn("HTTP failed for $url: ${t.message}")
            }
        }
    }

    /** Parses `Retry-After` (seconds) and caps it so the game never stalls long. */
    private fun retryAfterMs(response: HttpResponse<*>): Long? {
        val raw = response.headers().firstValue("Retry-After").orElse(null) ?: return null
        val seconds = raw.trim().toLongOrNull() ?: return null
        return (seconds * 1000L).coerceIn(0L, 8000L)
    }

    private fun backoffMs(attempt: Int): Long =
        BACKOFF_BASE_MS shl attempt.coerceAtMost(3)

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Downloads a binary file to [destination].
     * Returns true only when the file was written completely.
     * Writes to a `.part` file first, then renames — so an interrupted
     * download never leaves a corrupt jar that Fabric would try to load.
     *
     * At most [MAX_CONCURRENT_DOWNLOADS] downloads run at once; the rest wait.
     */
    fun download(url: String, destination: java.io.File): Boolean {
        val acquired = try {
            downloadSlots.tryAcquire(60, TimeUnit.SECONDS)
        } catch (t: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!acquired) {
            VibeClient.logger.warn("Download queue full, giving up on $url")
            return false
        }

        try {
            var attempt = 0
            while (true) {
                when (val outcome = downloadOnce(url, destination)) {
                    is Outcome.Ok -> return true
                    is Outcome.Retry -> {
                        if (attempt >= MAX_RETRIES) return false
                        val waitMs = outcome.retryAfterMs ?: backoffMs(attempt)
                        VibeClient.logger.warn(
                            "Download ${outcome.status} for $url - retry ${attempt + 1}/$MAX_RETRIES"
                        )
                        sleep(waitMs)
                        attempt++
                    }
                    is Outcome.Fail -> return false
                }
            }
        } finally {
            downloadSlots.release()
        }
    }

    private fun downloadOnce(url: String, destination: java.io.File): Outcome {
        return try {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build()

            destination.parentFile?.mkdirs()
            val part = java.io.File(destination.parentFile, destination.name + ".part")
            if (part.exists()) part.delete()

            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(part.toPath()))
            val code = response.statusCode()
            if (code !in 200..299) {
                part.delete()
                return if (code == 429 || code in 500..599) {
                    Outcome.Retry(code, retryAfterMs(response))
                } else {
                    VibeClient.logger.warn("Download HTTP $code for $url")
                    Outcome.Fail("HTTP $code")
                }
            }

            if (part.length() == 0L) {
                part.delete()
                VibeClient.logger.warn("Empty download for $url")
                return Outcome.Fail("empty body")
            }

            if (destination.exists()) destination.delete()
            if (!part.renameTo(destination)) {
                part.delete()
                VibeClient.logger.warn("Could not rename ${part.name} -> ${destination.name}")
                return Outcome.Fail("rename failed")
            }
            Outcome.Ok("")
        } catch (t: Throwable) {
            VibeClient.logger.warn("Download failed for $url: ${t.message}")
            Outcome.Retry(-1, null)
        }
    }
}
