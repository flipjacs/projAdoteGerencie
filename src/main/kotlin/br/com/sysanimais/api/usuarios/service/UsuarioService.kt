package br.com.sysanimais.api.usuarios.service

import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.domain.Usuario
import br.com.sysanimais.api.usuarios.dto.CadastroInternoRequest
import br.com.sysanimais.api.usuarios.dto.CadastroPublicoRequest
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.SQLException

class EmailEmUsoException : RuntimeException("E-mail já cadastrado")
class PerfilInvalidoException : RuntimeException("Perfil não permitido para este cadastro")
class EmailInvalidoException : RuntimeException("E-mail inválido")

@Service
class UsuarioService(private val repository: UsuarioRepository, private val encoder: PasswordEncoder) {
    @Transactional
    fun cadastrarPublico(request: CadastroPublicoRequest): UsuarioResponse {
        return salvar(request.nome, request.email, request.senha, Perfil.USUARIO, null)
    }

    @Transactional
    fun cadastrarInterno(request: CadastroInternoRequest, administradorId: Int): UsuarioResponse {
        if (request.perfil !in setOf(Perfil.ADMINISTRADOR, Perfil.VOLUNTARIO, Perfil.VETERINARIO)) throw PerfilInvalidoException()
        val administrador = repository.findById(administradorId).orElseThrow()
        val ongId = administrador.ongId ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        return salvar(request.nome, request.email, request.senha, request.perfil, ongId)
    }

    private fun salvar(nome: String, email: String, senha: String, perfil: Perfil, ongId: Int?): UsuarioResponse {
        val normalizado = email.trim().lowercase()
        if (!normalizado.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) throw EmailInvalidoException()
        return try {
            UsuarioResponse.from(repository.saveAndFlush(Usuario(
                ongId = ongId,
                nome = nome.trim(),
                email = normalizado,
                senhaHash = encoder.encode(senha),
                perfil = perfil
            )))
        } catch (ex: DataIntegrityViolationException) {
            val duplicateEmail = generateSequence(ex as Throwable?) { it.cause }
                .filterIsInstance<SQLException>()
                .any { sql ->
                    val message = sql.message.orEmpty()
                    (sql.errorCode == 1062 && Regex("(?i)for key ['`\\\"](?:[^'`\\\"]+\\.)?email['`\\\"]").containsMatchIn(message)) ||
                        (sql.sqlState == "23505" && Regex("(?i)USUARIO\\s*\\(\\s*EMAIL\\b").containsMatchIn(message))
                }
            if (duplicateEmail) throw EmailEmUsoException()
            throw ex
        }
    }
}
