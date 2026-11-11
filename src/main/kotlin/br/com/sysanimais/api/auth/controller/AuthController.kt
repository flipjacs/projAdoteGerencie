package br.com.sysanimais.api.auth.controller

import br.com.sysanimais.api.auth.dto.LoginRequest
import br.com.sysanimais.api.auth.dto.LoginResponse
import br.com.sysanimais.api.auth.service.AuthService
import br.com.sysanimais.api.security.UsuarioAuthenticationToken
import br.com.sysanimais.api.auth.service.LoginRateLimiter
import jakarta.servlet.http.HttpServletRequest

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

class LoginRateLimitException(val retryAfterSeconds: Long) : RuntimeException()

@RestController
@RequestMapping("/api/auth")
class AuthController(private val service: AuthService, private val limiter: LoginRateLimiter) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, servletRequest: HttpServletRequest): LoginResponse {
        val retry = limiter.acquire(servletRequest.remoteAddr, request.email)
        if (retry > 0) throw LoginRateLimitException(retry)
        return service.login(request)
    }

    @GetMapping("/me")
    fun me(authentication: UsuarioAuthenticationToken) = authentication.usuario
}
