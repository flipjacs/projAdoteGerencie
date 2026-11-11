package br.com.sysanimais.api

import br.com.sysanimais.api.auth.service.LoginRateLimiter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class LoginRateLimiterTests {
    @Test
    fun `limites por par e origem expiram sem bloquear outras contas`() {
        var now = 0L
        val limiter = LoginRateLimiter(originLimit = 3, pairLimit = 2, globalLimit = 20, originWindowMs = 1000, pairWindowMs = 2000, globalWindowMs = 1000, maxOrigins = 5, maxPairs = 5, nowMs = { now })
        assertEquals(0, limiter.acquire("ip1", " Pessoa@Exemplo.com "))
        assertEquals(0, limiter.acquire("ip1", "pessoa@exemplo.com"))
        assertEquals(2, limiter.acquire("ip1", "pessoa@exemplo.com"))
        assertEquals(0, limiter.acquire("ip1", "outra@exemplo.com"))
        assertEquals(1, limiter.acquire("ip1", "terceira@exemplo.com"))
        assertEquals(0, limiter.acquire("ip2", "pessoa@exemplo.com"))
        now = 2001
        assertEquals(0, limiter.acquire("ip1", "pessoa@exemplo.com"))
    }

    @Test
    fun `reservas concorrentes nao ultrapassam limite`() {
        val limiter = LoginRateLimiter(originLimit = 100, pairLimit = 5, globalLimit = 100, nowMs = { 0 })
        val pool = Executors.newFixedThreadPool(12)
        try {
            val results = pool.invokeAll((1..40).map { Callable { limiter.acquire("ip", "conta@example.com") } }).map { it.get() }
            assertEquals(5, results.count { it == 0L })
            assertEquals(35, results.count { it > 0L })
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `capacidade limitada recusa novas chaves e libera depois da janela`() {
        var now = 0L
        val limiter = LoginRateLimiter(originLimit = 50, pairLimit = 50, globalLimit = 100, originWindowMs = 1000, pairWindowMs = 1000, globalWindowMs = 1000, maxOrigins = 2, maxPairs = 2, nowMs = { now })
        assertEquals(0, limiter.acquire("ip1", "a"))
        assertEquals(0, limiter.acquire("ip2", "b"))
        assertTrue(limiter.acquire("ip3", "c") > 0)
        now = 1001
        assertEquals(0, limiter.acquire("ip3", "c"))
    }

    @Test
    fun `teto global limita origens diferentes e expira`() {
        var now = 0L
        val limiter = LoginRateLimiter(originLimit = 20, pairLimit = 20, globalLimit = 2, globalWindowMs = 1000, nowMs = { now })
        assertEquals(0, limiter.acquire("ip1", "a"))
        assertEquals(0, limiter.acquire("ip2", "b"))
        assertEquals(1, limiter.acquire("ip3", "c"))
        now = 1001
        assertEquals(0, limiter.acquire("ip3", "c"))
    }
}
