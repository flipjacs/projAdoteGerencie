package br.com.sysanimais.api.financeiro.dto

import java.time.LocalDate

data class PeriodoRelatorioFinanceiro(val de: LocalDate?, val ate: LocalDate?)
data class RelatorioFinanceiroResponse(
    val periodo: PeriodoRelatorioFinanceiro,
    val semanticaPeriodo: String,
    val receitasRealizadas: String,
    val despesasRealizadas: String,
    val receitasPendentes: String,
    val despesasPendentes: String,
    val despesasAprovadas: String,
    val saldoRealizado: String,
    val quantidadeMovimentacoes: Long,
    val receitasPorCategoria: Map<String, String>,
    val despesasPorCategoria: Map<String, String>
)
