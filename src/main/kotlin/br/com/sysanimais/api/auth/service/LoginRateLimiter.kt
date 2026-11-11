package br.com.sysanimais.api.auth.service

import java.security.MessageDigest
import java.util.HexFormat

/** Limites por instância; as reservas incluem tentativas válidas e inválidas. */
class LoginRateLimiter(
    private val originLimit: Int = 30,
    private val pairLimit: Int = 8,
    private val globalLimit: Int = 120,
    private val originWindowMs: Long = 60_000,
    private val pairWindowMs: Long = 300_000,
    private val globalWindowMs: Long = 60_000,
    private val maxOrigins: Int = 4096,
    private val maxPairs: Int = 4096,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private data class Window(var count: Int, val expiresAt: Long)
    private val origins = HashMap<String, Window>()
    private val pairs = HashMap<Pair<String, String>, Window>()
    private var global: Window? = null
    private var lastCleanup = Long.MIN_VALUE

    init {
        require(originLimit > 0 && pairLimit > 0 && globalLimit > 0 && maxOrigins > 0 && maxPairs > 0)
        require(originWindowMs > 0 && pairWindowMs > 0 && globalWindowMs > 0)
    }

    /** Retorna zero se a reserva foi aceita ou os segundos até a próxima tentativa. */
    @Synchronized
    fun acquire(origin: String, identifier: String): Long {
        val now = nowMs()
        if (lastCleanup == Long.MIN_VALUE || now - lastCleanup >= 60_000 || origins.size >= maxOrigins || pairs.size >= maxPairs) {
            origins.entries.removeIf { it.value.expiresAt <= now }
            pairs.entries.removeIf { it.value.expiresAt <= now }
            lastCleanup = now
        }
        val originKey = origin.take(64)
        val normalized = identifier.trim().lowercase().take(256)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        val pairKey = originKey to HexFormat.of().formatHex(digest)
        val originWindow = origins[originKey]?.takeIf { it.expiresAt > now }
        val pairWindow = pairs[pairKey]?.takeIf { it.expiresAt > now }
        val globalWindow = global?.takeIf { it.expiresAt > now }
        var wait = 0L
        if (originWindow != null && originWindow.count >= originLimit) wait = maxOf(wait, originWindow.expiresAt - now)
        if (pairWindow != null && pairWindow.count >= pairLimit) wait = maxOf(wait, pairWindow.expiresAt - now)
        if (globalWindow != null && globalWindow.count >= globalLimit) wait = maxOf(wait, globalWindow.expiresAt - now)
        if (originWindow == null && origins.size >= maxOrigins) wait = maxOf(wait, origins.values.minOf { it.expiresAt } - now)
        if (pairWindow == null && pairs.size >= maxPairs) wait = maxOf(wait, pairs.values.minOf { it.expiresAt } - now)
        if (wait > 0) return ((wait + 999) / 1000).coerceAtLeast(1)

        val originBucket = originWindow ?: Window(0, now + originWindowMs).also { origins[originKey] = it }
        val pairBucket = pairWindow ?: Window(0, now + pairWindowMs).also { pairs[pairKey] = it }
        val globalBucket = globalWindow ?: Window(0, now + globalWindowMs).also { global = it }
        originBucket.count++
        pairBucket.count++
        globalBucket.count++
        return 0
    }
}
