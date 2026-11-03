package br.com.sysanimais.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class BootstrapAdmin {
    @Bean
    fun criarPrimeiroAdmin(
        repository: UsuarioRepository,
        db: JdbcTemplate,
        encoder: PasswordEncoder,
        @Value("\${app.bootstrap.ong-id}") ongId: Int,
        @Value("\${app.bootstrap.name}") nome: String,
        @Value("\${app.bootstrap.email}") email: String,
        @Value("\${app.bootstrap.password}") senha: String
    ) = ApplicationRunner {
        if (email.isBlank() && senha.isBlank()) return@ApplicationRunner
        require(ongId > 0 && nome.isNotBlank() && email.isNotBlank() && senha.isNotBlank() && senha.length >= 12) {
            "Informe BOOTSTRAP_ADMIN_ONG_ID, NAME, EMAIL e PASSWORD (mínimo 12 caracteres)"
        }
        require(db.queryForObject("SELECT COUNT(*) FROM ong WHERE id = ?", Int::class.java, ongId) == 1) {
            "ONG inicial não encontrada"
        }
        val existente = repository.findByEmail(email.trim().lowercase())
        require(existente == null || (existente.perfil == Perfil.ADMINISTRADOR && existente.ongId == ongId)) {
            "E-mail do administrador inicial já pertence a outra conta"
        }
        if (existente == null) {
            repository.save(Usuario(ongId = ongId, nome = nome.trim(), email = email.trim().lowercase(),
                senhaHash = encoder.encode(senha), perfil = Perfil.ADMINISTRADOR))
        }
    }
}
