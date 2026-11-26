package br.com.sysanimais.api.financeiro.service

import br.com.sysanimais.api.financeiro.dto.*
import br.com.sysanimais.api.financeiro.repository.RelatorioFinanceiroRepository
import br.com.sysanimais.api.transparencia.dto.DadosPublicosTransparencia
import br.com.sysanimais.api.transparencia.service.*
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service @Transactional
class RelatorioFinanceiroService(
    private val finance: FinanceiroService,
    private val repository: RelatorioFinanceiroRepository,
    private val access: TransparenciaAccess
) {
    private fun money(value: BigDecimal) = value.setScale(2).toPlainString()

    fun snapshot(ongId: Int, period: PeriodoTransparencia): DadosPublicosTransparencia {
        val rows = repository.categorias(ongId, period.inicio, period.fimExclusivo)
        val receitas = linkedMapOf<String, String>(); val despesas = linkedMapOf<String, String>()
        var receita = BigDecimal.ZERO; var despesa = BigDecimal.ZERO; var count = 0L
        for (row in rows) {
            count += row.quantidade
            if (row.tipo == "RECEITA") { receitas[row.categoria] = money(row.valor); receita += row.valor }
            else { despesas[row.categoria] = money(row.valor); despesa += row.valor }
        }
        return DadosPublicosTransparencia(money(receita), money(despesa), money(receita - despesa), receitas, despesas, count)
    }

    fun report(u: UsuarioResponse, de: String?, ate: String?): RelatorioFinanceiroResponse {
        val ong = access.scope(u, admin = true)
        val period = periodoTransparencia(de, ate)
        val totals = finance.summary(u, de, ate)
        val categories = snapshot(ong.id!!, period)
        return RelatorioFinanceiroResponse(
            PeriodoRelatorioFinanceiro(period.de, period.ate),
            "Valores realizados usam a data de confirmação; pendentes usam o cadastro; despesas aprovadas usam a aprovação; quantidade de movimentações usa o cadastro.",
            totals.receitasRealizadas, totals.despesasRealizadas, totals.receitasPendentes,
            totals.despesasPendentes, totals.despesasAprovadas, totals.saldoRealizado,
            repository.quantidadeCadastro(ong.id!!, period.inicio, period.fimExclusivo),
            categories.receitasPorCategoria, categories.despesasPorCategoria
        )
    }
}
