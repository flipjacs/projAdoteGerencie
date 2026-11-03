package br.com.sysanimais.api

import org.springframework.data.jpa.repository.JpaRepository

interface UsuarioRepository : JpaRepository<Usuario, Int> {
    fun findByEmail(email: String): Usuario?
    fun existsByEmail(email: String): Boolean
}
