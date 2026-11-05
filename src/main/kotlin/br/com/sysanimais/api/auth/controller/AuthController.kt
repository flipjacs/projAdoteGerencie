package br.com.sysanimais.api.auth.controller

import br.com.sysanimais.api.auth.dto.LoginRequest
import br.com.sysanimais.api.auth.dto.LoginResponse
import br.com.sysanimais.api.auth.service.AuthService

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val service: AuthService) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse = service.login(request)
}
