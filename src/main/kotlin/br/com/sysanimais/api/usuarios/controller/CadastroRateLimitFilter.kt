package br.com.sysanimais.api.usuarios.controller

import br.com.sysanimais.api.auth.service.LoginRateLimiter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Proteção independente do login; contagem inclui requisições inválidas. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class CadastroRateLimitFilter : OncePerRequestFilter() {
    private val limiter = LoginRateLimiter(originLimit = 10, pairLimit = 10, globalLimit = 100,
        originWindowMs = 60_000, pairWindowMs = 60_000, globalWindowMs = 60_000)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "POST" || request.requestURI.removePrefix(request.contextPath) != "/api/usuarios/cadastro"

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val retry = limiter.acquire(request.remoteAddr ?: "unknown", "cadastro")
        if (retry == 0L) {
            chain.doFilter(request, response)
            return
        }
        response.status = 429
        response.characterEncoding = "UTF-8"
        response.contentType = "application/problem+json"
        response.setHeader("Retry-After", retry.toString())
        response.writer.write("""{"status":429,"code":"CADASTRO_RATE_LIMIT","detail":"Muitas tentativas. Tente novamente mais tarde."}""")
    }
}
