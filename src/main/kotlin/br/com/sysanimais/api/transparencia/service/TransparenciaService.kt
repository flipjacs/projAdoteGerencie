package br.com.sysanimais.api.transparencia.service

import br.com.sysanimais.api.animais.dto.PaginaResponse
import br.com.sysanimais.api.financeiro.domain.CategoriaFinanceira
import br.com.sysanimais.api.financeiro.service.RelatorioFinanceiroService
import br.com.sysanimais.api.ongs.domain.Ong
import br.com.sysanimais.api.transparencia.domain.*
import br.com.sysanimais.api.transparencia.dto.*
import br.com.sysanimais.api.transparencia.repository.DemonstrativoRepository
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import com.fasterxml.jackson.databind.*
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service @Transactional
class TransparenciaService(
    private val reports: DemonstrativoRepository,
    private val finance: RelatorioFinanceiroService,
    private val access: TransparenciaAccess,
    private val json: ObjectMapper
) {
    private val allowed = setOf("titulo", "descricao", "periodoInicio", "periodoFim")
    private val moneyPattern = Regex("-?(?:0|[1-9][0-9]*)\\.[0-9]{2}")

    private fun body(input: JsonNode) {
        if (!input.isObject || input.fieldNames().asSequence().any { it !in allowed }) transparenciaBad()
    }
    private fun text(input: JsonNode, field: String, max: Int, nullable: Boolean = false): String? {
        val node = input[field] ?: transparenciaBad("Campo $field obrigatório")
        if (nullable && node.isNull) return null
        if (!node.isTextual) transparenciaBad("Campo $field deve ser texto")
        val value = node.textValue().trim()
        if ((!nullable && value.isBlank()) || value.codePointCount(0, value.length) > max) transparenciaBad("Campo $field inválido")
        return value
    }
    private fun date(input: JsonNode, field: String): LocalDate {
        val node = input[field] ?: transparenciaBad("Campo $field obrigatório")
        if (!node.isTextual) transparenciaBad("Campo $field deve ser data textual")
        return dataTransparencia(node.textValue())
    }
    private fun snapshot(ong: Int, start: LocalDate, end: LocalDate) = json.writeValueAsString(finance.snapshot(ong, PeriodoTransparencia(start, end)))

    /** Read exactly the permitted six fields, even if a stored JSON object contains private extras. */
    private fun publicData(report: DemonstrativoTransparencia): DadosPublicosTransparencia {
        val node = json.readTree(report.dadosConsolidados)
        fun money(value: JsonNode?): String {
            if (value == null || !value.isTextual || !moneyPattern.matches(value.textValue())) throw IllegalStateException("Snapshot financeiro inválido")
            return value.textValue()
        }
        fun categories(field: String): Map<String, String> {
            val values = node[field] ?: throw IllegalStateException("Snapshot financeiro inválido")
            val allowedCategories = CategoriaFinanceira.entries.map { it.name }.toSet()
            return values.fields().asSequence().filter { it.key in allowedCategories }.associate { it.key to money(it.value) }
        }
        return DadosPublicosTransparencia(money(node["receitasRealizadas"]), money(node["despesasRealizadas"]), money(node["resultadoLiquido"]), categories("receitasPorCategoria"), categories("despesasPorCategoria"), node["quantidadeMovimentacoes"].longValue())
    }
    private fun publicResponse(row: DemonstrativoTransparencia, ong: Ong) = DemonstrativoPublicoResponse(row.id!!, OngTransparencia(ong.id!!, ong.nome), row.titulo, row.descricao, row.periodoInicio, row.periodoFim, row.publicadoEm, publicData(row))
    private fun internalResponse(row: DemonstrativoTransparencia, ong: Ong) = DemonstrativoInternoResponse(row.id!!, OngTransparencia(ong.id!!, ong.nome), row.titulo, row.descricao, row.periodoInicio, row.periodoFim, row.publicadoEm, publicData(row), row.status, row.publicadoPor, row.despublicadoEm, row.criadoEm, row.atualizadoEm)

    fun create(u: UsuarioResponse, input: JsonNode): DemonstrativoInternoResponse {
        val ong = access.scope(u, admin = true); body(input)
        val title = text(input, "titulo", 120)!!
        val description = if (input.has("descricao")) text(input, "descricao", 2000, nullable = true) else null
        val start = date(input, "periodoInicio"); val end = date(input, "periodoFim")
        if (end < start) transparenciaBad("O fim deve ser igual ou posterior ao início")
        val row = reports.saveAndFlush(DemonstrativoTransparencia(ongId = ong.id!!, titulo = title, descricao = description, periodoInicio = start, periodoFim = end, dadosConsolidados = snapshot(ong.id!!, start, end)))
        return internalResponse(row, ong)
    }
    fun details(u: UsuarioResponse, id: Long): DemonstrativoInternoResponse {
        val ong = access.scope(u)
        return internalResponse(reports.findByIdAndOngId(id, ong.id!!) ?: transparenciaMissing(), ong)
    }
    fun edit(u: UsuarioResponse, id: Long, input: JsonNode): DemonstrativoInternoResponse {
        val ong = access.scope(u, admin = true); body(input)
        val row = reports.lockOng(id, ong.id!!) ?: transparenciaMissing()
        if (row.status != StatusDemonstrativo.RASCUNHO) transparenciaConflict()
        val start = if (input.has("periodoInicio")) date(input, "periodoInicio") else row.periodoInicio
        val end = if (input.has("periodoFim")) date(input, "periodoFim") else row.periodoFim
        if (end < start) transparenciaBad("O fim deve ser igual ou posterior ao início")
        if (input.has("titulo")) row.titulo = text(input, "titulo", 120)!!
        if (input.has("descricao")) row.descricao = text(input, "descricao", 2000, nullable = true)
        if (start != row.periodoInicio || end != row.periodoFim) row.dadosConsolidados = snapshot(ong.id!!, start, end)
        row.periodoInicio = start; row.periodoFim = end
        if (input.size() != 0) row.atualizadoEm = Instant.now()
        reports.flush()
        return internalResponse(row, ong)
    }
    fun publish(u: UsuarioResponse, id: Long, publish: Boolean, input: JsonNode?): DemonstrativoInternoResponse {
        val ong = access.scope(u, admin = true)
        if (input != null && (!input.isObject || input.size() != 0)) transparenciaBad("Esta operação não aceita campos")
        val row = reports.lockOng(id, ong.id!!) ?: transparenciaMissing()
        if ((row.status == StatusDemonstrativo.PUBLICADO) == publish) transparenciaConflict()
        val now = Instant.now()
        if (publish) {
            row.dadosConsolidados = snapshot(ong.id!!, row.periodoInicio, row.periodoFim)
            row.status = StatusDemonstrativo.PUBLICADO; row.publicadoPor = u.id; row.publicadoEm = now
        } else { row.status = StatusDemonstrativo.RASCUNHO; row.despublicadoEm = now }
        row.atualizadoEm = now; reports.flush()
        return internalResponse(row, ong)
    }
    private fun spec(ong: Int, status: StatusDemonstrativo?, period: PeriodoTransparencia): Specification<DemonstrativoTransparencia> = Specification { r, _, cb ->
        val conditions = mutableListOf(cb.equal(r.get<Int>("ongId"), ong))
        status?.let { conditions += cb.equal(r.get<StatusDemonstrativo>("status"), it) }
        period.de?.let { conditions += cb.greaterThanOrEqualTo(r.get("periodoFim"), it) }
        period.ate?.let { conditions += cb.lessThanOrEqualTo(r.get("periodoInicio"), it) }
        cb.and(*conditions.toTypedArray())
    }
    fun list(u: UsuarioResponse, page: Int, size: Int, status: StatusDemonstrativo?, de: String?, ate: String?): PaginaResponse<DemonstrativoInternoResponse> {
        val ong = access.scope(u)
        val rows = reports.findAll(spec(ong.id!!, status, periodoTransparencia(de, ate)), paginaTransparencia(page, size))
        return PaginaResponse(rows.content.map { internalResponse(it, ong) }, rows.number, rows.size, rows.totalElements, rows.totalPages)
    }
    fun publicList(ongId: Int, page: Int, size: Int): PaginaResponse<DemonstrativoPublicoResponse> {
        val ong = access.publicOng(ongId)
        val rows = reports.findAll(spec(ongId, StatusDemonstrativo.PUBLICADO, PeriodoTransparencia(null, null)), paginaTransparencia(page, size, publico = true))
        return PaginaResponse(rows.content.map { publicResponse(it, ong) }, rows.number, rows.size, rows.totalElements, rows.totalPages)
    }
    fun publicDetails(ongId: Int, id: Long): DemonstrativoPublicoResponse {
        val ong = access.publicOng(ongId)
        return publicResponse(reports.findByIdAndOngIdAndStatus(id, ongId, StatusDemonstrativo.PUBLICADO) ?: transparenciaMissing(), ong)
    }
}
