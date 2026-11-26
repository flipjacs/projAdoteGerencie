package br.com.sysanimais.api.transparencia.dto

import br.com.sysanimais.api.transparencia.domain.StatusDemonstrativo
import java.time.Instant
import java.time.LocalDate

/** Explicit whitelist shared by snapshot generation and public projection; never a ledger DTO. */
data class DadosPublicosTransparencia(
    val receitasRealizadas: String,
    val despesasRealizadas: String,
    val resultadoLiquido: String,
    val receitasPorCategoria: Map<String, String>,
    val despesasPorCategoria: Map<String, String>,
    val quantidadeMovimentacoes: Long
)
data class OngTransparencia(val id: Int, val nome: String)
data class DemonstrativoPublicoResponse(
    val id: Long, val ong: OngTransparencia, val titulo: String, val descricao: String?,
    val periodoInicio: LocalDate, val periodoFim: LocalDate, val publicadoEm: Instant?,
    val dados: DadosPublicosTransparencia
)
data class DemonstrativoInternoResponse(
    val id: Long, val ong: OngTransparencia, val titulo: String, val descricao: String?,
    val periodoInicio: LocalDate, val periodoFim: LocalDate, val publicadoEm: Instant?,
    val dados: DadosPublicosTransparencia, val status: StatusDemonstrativo, val publicadoPor: Int?,
    val despublicadoEm: Instant?, val createdAt: Instant, val updatedAt: Instant
)
