package br.com.sysanimais.api.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val senha: String
)

data class LoginResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresIn: Long = 3600)
