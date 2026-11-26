package br.com.sysanimais.api.usuarios.service

import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.domain.Usuario
import br.com.sysanimais.api.usuarios.dto.CadastroPublicoRequest
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLException
import org.slf4j.LoggerFactory

class EmailEmUsoException : RuntimeException("E-mail já cadastrado")
class PerfilInvalidoException : RuntimeException("Perfil não permitido para este cadastro")
class EmailInvalidoException : RuntimeException("E-mail inválido")

@Service
class UsuarioService(private val repository: UsuarioRepository, private val encoder: PasswordEncoder) {
    private val logger = LoggerFactory.getLogger(UsuarioService::class.java)
    @Transactional
    fun cadastrarPublico(request: CadastroPublicoRequest): UsuarioResponse {
        return salvar(request.nome, request.email, request.senha, Perfil.USUARIO, null)
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
            val sqlError = generateSequence(ex as Throwable?) { it.cause }.filterIsInstance<SQLException>().firstOrNull()
            logger.warn("Falha de integridade ao cadastrar usuario: sqlState={}, codigo={}",
                sqlError?.sqlState?.takeIf { it.matches(Regex("[A-Za-z0-9]{5}")) } ?: "indisponivel",
                sqlError?.errorCode ?: 0)
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
