package br.com.sysanimais.api.usuarios.dto

import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.domain.Usuario

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CadastroPublicoRequest(
    @field:NotBlank @field:Size(max = 100) val nome: String,
    @field:NotBlank @field:Size(max = 120) val email: String,
    @field:NotBlank @field:Size(min = 8, max = 72) val senha: String
)

data class UsuarioResponse(val id: Int, val nome: String, val email: String, val perfil: Perfil, val ongId: Int?, @get:com.fasterxml.jackson.annotation.JsonIgnore val tokenVersao: Long = 0) {
    companion object {
        fun from(usuario: Usuario) = UsuarioResponse(usuario.id!!, usuario.nome, usuario.email, usuario.perfil, usuario.ongId, usuario.tokenVersao)
    }
}
