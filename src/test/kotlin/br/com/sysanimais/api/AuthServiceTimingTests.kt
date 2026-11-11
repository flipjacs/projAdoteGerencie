package br.com.sysanimais.api

import br.com.sysanimais.api.auth.dto.LoginRequest
import br.com.sysanimais.api.auth.service.AuthService
import br.com.sysanimais.api.usuarios.domain.Usuario
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.web.server.ResponseStatusException

class AuthServiceTimingTests {
    @Test
    fun `login inexistente e inativo verificam senha antes de negar`() {
        val repository = mock(UsuarioRepository::class.java)
        val encoder = spy(BCryptPasswordEncoder())
        val service = AuthService(repository, encoder, mock(JwtEncoder::class.java))
        `when`(repository.findByEmail("inativo@example.test")).thenReturn(Usuario(email = "inativo@example.test", ativo = false))
        assertThrows(ResponseStatusException::class.java) { service.login(LoginRequest("ausente@example.test", "senha-invalida")) }
        assertThrows(ResponseStatusException::class.java) { service.login(LoginRequest("inativo@example.test", "senha-invalida")) }
        verify(encoder, times(2)).matches(anyString(), anyString())
    }
}
