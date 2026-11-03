package br.com.sysanimais.api

import jakarta.persistence.*

enum class Perfil { INTERESSADO, APADRINHADOR, ADMINISTRADOR, VOLUNTARIO, VETERINARIO }

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
    var perfil: Perfil = Perfil.INTERESSADO,
    @Column(nullable = false)
    var ativo: Boolean = true
)
