package br.com.sysanimais.api.usuarios.domain

import jakarta.persistence.*
import java.time.Instant

enum class Perfil { USUARIO, ADMINISTRADOR, VOLUNTARIO, VETERINARIO, ADMIN_PLATAFORMA }

@Converter
class PerfilConverter : AttributeConverter<Perfil, String> {
    override fun convertToDatabaseColumn(attribute: Perfil?): String? = attribute?.name?.lowercase()
    override fun convertToEntityAttribute(dbData: String?): Perfil? = dbData?.let { Perfil.valueOf(it.uppercase()) }
}

@Entity
@Table(name = "usuario")
class Usuario(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,
    @Column(name = "ong_id")
    var ongId: Int? = null,
    @Column(nullable = false, length = 100)
    var nome: String = "",
    @Column(nullable = false, unique = true, length = 120)
    var email: String = "",
    @Column(name = "senha_hash", nullable = false)
    var senhaHash: String = "",
    @Convert(converter = PerfilConverter::class)
    @Column(nullable = false, length = 20)
    var perfil: Perfil = Perfil.USUARIO,
    @Column(nullable = false)
    var ativo: Boolean = true,
    @Column(name = "credencial_pendente", nullable = false)
    var credencialPendente: Boolean = false,
    @Column(name = "credencial_temporaria_expira_em")
    var credencialTemporariaExpiraEm: Instant? = null,
    @Column(name = "provisionamento_virgem", nullable = false)
    var provisionamentoVirgem: Boolean = false,
    @Column(name = "primeiro_acesso_concluido", nullable = false)
    var primeiroAcessoConcluido: Boolean = true,
    @Version @Column(nullable = false)
    var versao: Long = 0,
    @Column(name = "token_versao", nullable = false)
    var tokenVersao: Long = 0
) {
    fun identidadeGlobalValida(): Boolean = perfil != Perfil.ADMIN_PLATAFORMA || ongId == null

    @PrePersist
    @PreUpdate
    fun validarVinculo() {
        require(identidadeGlobalValida()) { "Administrador da plataforma não pode pertencer a uma ONG" }
        require(perfil !in setOf(Perfil.ADMINISTRADOR, Perfil.VOLUNTARIO, Perfil.VETERINARIO) || ongId != null) {
            "Funcionário precisa estar vinculado a uma ONG"
        }
    }
}
