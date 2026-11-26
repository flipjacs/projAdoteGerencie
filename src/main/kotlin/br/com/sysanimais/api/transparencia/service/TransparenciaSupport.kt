package br.com.sysanimais.api.transparencia.service

import br.com.sysanimais.api.financeiro.service.FinanceiroException
import br.com.sysanimais.api.ongs.domain.*
import br.com.sysanimais.api.ongs.repository.OngRepository
import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import org.springframework.data.domain.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.*

fun transparenciaBad(message: String = "Campos ou filtros de transparência inválidos"): Nothing =
    throw FinanceiroException(HttpStatus.BAD_REQUEST, "TRANSPARENCIA_DADOS_INVALIDOS", message)
fun transparenciaDenied(): Nothing = throw FinanceiroException(HttpStatus.FORBIDDEN, "TRANSPARENCIA_ACESSO_NEGADO", "Acesso não permitido")
fun transparenciaMissing(): Nothing = throw FinanceiroException(HttpStatus.NOT_FOUND, "TRANSPARENCIA_NAO_ENCONTRADA", "ONG ou demonstrativo indisponível")
fun transparenciaConflict(): Nothing = throw FinanceiroException(HttpStatus.CONFLICT, "TRANSPARENCIA_ESTADO_INCOMPATIVEL", "Operação incompatível com a situação atual do demonstrativo")

data class PeriodoTransparencia(val de: LocalDate?, val ate: LocalDate?) {
    val inicio: Instant? get() = de?.atStartOfDay()?.toInstant(ZoneOffset.UTC)
    val fimExclusivo: Instant? get() = ate?.takeUnless { it == LocalDate.of(9999, 12, 31) }?.plusDays(1)?.atStartOfDay()?.toInstant(ZoneOffset.UTC)
}
fun dataTransparencia(value: String): LocalDate {
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) transparenciaBad("Data deve usar YYYY-MM-DD")
    return try { LocalDate.parse(value).also { if (it.year !in 1..9999) transparenciaBad("Data inválida") } }
    catch (_: DateTimeException) { transparenciaBad("Data inválida") }
}
fun periodoTransparencia(de: String?, ate: String?): PeriodoTransparencia {
    val a = de?.let(::dataTransparencia); val b = ate?.let(::dataTransparencia)
    if (a != null && b != null && a > b) transparenciaBad("O fim deve ser igual ou posterior ao início")
    return PeriodoTransparencia(a, b)
}
fun paginaTransparencia(page: Int, size: Int, publico: Boolean = false): PageRequest {
    if (page < 0 || size !in 1..100 || page.toLong() * size > Int.MAX_VALUE) transparenciaBad("Paginação inválida")
    return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, if (publico) "publicadoEm" else "criadoEm", "id"))
}

/** Revalidate persisted actor and active institution in the same transaction as the operation. */
@Component
class TransparenciaAccess(private val ongs: OngRepository, private val users: UsuarioRepository) {
    fun scope(u: UsuarioResponse, admin: Boolean = false): Ong {
        if (u.perfil !in setOf(Perfil.ADMINISTRADOR, Perfil.VOLUNTARIO) || admin && u.perfil != Perfil.ADMINISTRADOR) transparenciaDenied()
        val ong = ongs.lockRead(u.ongId ?: transparenciaDenied()) ?: transparenciaMissing()
        if (ong.situacao != EstadoOng.ATIVA) transparenciaDenied()
        val current = users.lockByIdRead(u.id) ?: transparenciaDenied()
        if (!current.ativo || current.credencialPendente || current.tokenVersao != u.tokenVersao || current.perfil != u.perfil || current.ongId != u.ongId) transparenciaDenied()
        return ong
    }
    fun publicOng(id: Int): Ong = ongs.findById(id).orElse(null)?.takeIf { it.situacao == EstadoOng.ATIVA } ?: transparenciaMissing()
}
