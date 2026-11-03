package br.com.sysanimais.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CadastroPublicoRequest(
    @field:NotBlank @field:Size(max = 100) val nome: String,
    @field:NotBlank @field:Size(max = 120) val email: String,
    @field:NotBlank @field:Size(min = 12, max = 72) val senha: String,
    val tipo: Perfil = Perfil.INTERESSADO
)

data class CadastroInternoRequest(
    @field:NotBlank @field:Size(max = 100) val nome: String,
    @field:Email @field:NotBlank @field:Size(max = 120) val email: String,
    @field:NotBlank @field:Size(min = 12, max = 72) val senha: String,
    val perfil: Perfil
)

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val senha: String
)

data class UsuarioResponse(val id: Int, val nome: String, val email: String, val perfil: Perfil, val ongId: Int?) {
    companion object {
        fun from(usuario: Usuario) = UsuarioResponse(usuario.id!!, usuario.nome, usuario.email, usuario.perfil, usuario.ongId)
    }
}

data class LoginResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresIn: Long = 3600)
