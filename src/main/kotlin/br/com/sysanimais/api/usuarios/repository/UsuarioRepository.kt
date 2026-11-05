package br.com.sysanimais.api.usuarios.repository

import br.com.sysanimais.api.usuarios.domain.Usuario

import org.springframework.data.jpa.repository.JpaRepository

interface UsuarioRepository : JpaRepository<Usuario, Int> {
    fun findByEmail(email: String): Usuario?
}
