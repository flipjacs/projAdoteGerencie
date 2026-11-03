package br.com.sysanimais.api

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class EmailEmUsoException : RuntimeException("E-mail já cadastrado")
class PerfilInvalidoException : RuntimeException("Perfil não permitido para este cadastro")
class EmailInvalidoException : RuntimeException("E-mail inválido")

@Service
class UsuarioService(private val repository: UsuarioRepository, private val encoder: PasswordEncoder) {
    @Transactional
    fun cadastrarPublico(request: CadastroPublicoRequest): UsuarioResponse {
        if (request.tipo !in setOf(Perfil.INTERESSADO, Perfil.APADRINHADOR)) throw PerfilInvalidoException()
        return salvar(request.nome, request.email, request.senha, request.tipo, null)
    }

    @Transactional
    fun cadastrarInterno(request: CadastroInternoRequest, ongId: Int): UsuarioResponse {
        if (request.perfil !in setOf(Perfil.ADMINISTRADOR, Perfil.VOLUNTARIO, Perfil.VETERINARIO)) throw PerfilInvalidoException()
        return salvar(request.nome, request.email, request.senha, request.perfil, ongId)
    }

    private fun salvar(nome: String, email: String, senha: String, perfil: Perfil, ongId: Int?): UsuarioResponse {
        val normalizado = email.trim().lowercase()
        if (!normalizado.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) throw EmailInvalidoException()
        if (repository.existsByEmail(normalizado)) throw EmailEmUsoException()
        return try {
            UsuarioResponse.from(repository.saveAndFlush(Usuario(
                ongId = ongId,
                nome = nome.trim(),
                email = normalizado,
                senhaHash = encoder.encode(senha),
                perfil = perfil
            )))
        } catch (_: DataIntegrityViolationException) {
            throw EmailEmUsoException()
        }
    }
}
