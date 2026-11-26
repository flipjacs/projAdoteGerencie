package br.com.sysanimais.api.transparencia.domain

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

enum class StatusDemonstrativo { RASCUNHO, PUBLICADO }

@Entity
@Table(name = "demonstrativo_transparencia")
class DemonstrativoTransparencia(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false) var ongId: Int = 0,
    @Column(nullable = false) var periodoInicio: LocalDate = LocalDate.now(),
    @Column(nullable = false) var periodoFim: LocalDate = LocalDate.now(),
    @Column(nullable = false, length = 120) var titulo: String = "",
    @Column(length = 2000) var descricao: String? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) var status: StatusDemonstrativo = StatusDemonstrativo.RASCUNHO,
    @Column(nullable = false, columnDefinition = "TEXT") var dadosConsolidados: String = "{}",
    var publicadoPor: Int? = null,
    var publicadoEm: Instant? = null,
    var despublicadoEm: Instant? = null,
    @Column(nullable = false) var criadoEm: Instant = Instant.now(),
    @Column(nullable = false) var atualizadoEm: Instant = Instant.now(),
    @Version @Column(nullable = false) var versao: Long = 0
)
