package br.com.sysanimais.api

import br.com.sysanimais.api.config.BootstrapGlobalService
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder

class BootstrapGlobalTests {
    @Test fun `destino incorreto e senha curta nao criam conta`() {
        val db=mock(JdbcTemplate::class.java)
        val users=mock(UsuarioRepository::class.java)
        val encoder=mock(PasswordEncoder::class.java)
        `when`(db.queryForObject("SELECT DATABASE()",String::class.java)).thenReturn("isolado")
        val service=BootstrapGlobalService(db,users,encoder)
        assertThrows(IllegalArgumentException::class.java) { service.criar("Pessoa", "pessoa@teste.invalid", "SenhaValida123!", "outro") }
        assertThrows(IllegalArgumentException::class.java) { service.criar("Pessoa", "pessoa@teste.invalid", "curta", "isolado") }
        verifyNoInteractions(users,encoder)
    }
}
