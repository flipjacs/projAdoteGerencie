package br.com.sysanimais.api.ongs.domain

import jakarta.persistence.*
import java.time.Instant

enum class EstadoOng { ATIVA, SUSPENSA }

@Entity
@Table(name="ong")
class Ong(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id: Int? = null,
 @Column(nullable=false, length=120) var nome: String = "",
 @Column(name="cnpj_cpf", length=20) var identificacao: String? = null,
 @Column(nullable=false, length=120) var email: String = "",
 @Column(length=20) var telefone: String? = null,
 @Column(length=80) var cidade: String? = null,
 @Column(length=2, columnDefinition="CHAR(2)") var estado: String? = null,
 @Enumerated(EnumType.STRING) @Column(name="situacao", nullable=false, length=16) var situacao: EstadoOng = EstadoOng.ATIVA,
 @Column(name="criado_em") var criadoEm: Instant? = Instant.now(),
 @Column(name="atualizado_em") var atualizadoEm: Instant? = Instant.now(),
 @Column(name="criado_por") var criadoPor: Int? = null,
 @Version @Column(nullable=false) var versao: Long = 0
)
