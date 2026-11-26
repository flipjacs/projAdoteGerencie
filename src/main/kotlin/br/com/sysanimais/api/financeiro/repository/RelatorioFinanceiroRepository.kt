package br.com.sysanimais.api.financeiro.repository

import br.com.sysanimais.api.financeiro.domain.MovimentacaoFinanceira
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant

interface CategoriaRealizada {
    val tipo: String
    val categoria: String
    val valor: BigDecimal
    val quantidade: Long
}

/** Aggregates only the existing K6 ledger, including automatic donation/sponsorship entries once. */
interface RelatorioFinanceiroRepository : Repository<MovimentacaoFinanceira, Long> {
    @Query(value = """SELECT tipo, categoria, SUM(valor) AS valor, COUNT(*) AS quantidade
        FROM movimentacao_financeira WHERE ong_id=:ongId AND status='REALIZADA'
        AND (:de IS NULL OR confirmado_em>=:de) AND (:ate IS NULL OR confirmado_em<:ate)
        GROUP BY tipo,categoria ORDER BY tipo,categoria""", nativeQuery = true)
    fun categorias(@Param("ongId") ongId: Int, @Param("de") de: Instant?, @Param("ate") ate: Instant?): List<CategoriaRealizada>

    @Query(value = """SELECT COUNT(*) FROM movimentacao_financeira WHERE ong_id=:ongId
        AND (:de IS NULL OR criado_em>=:de) AND (:ate IS NULL OR criado_em<:ate)""", nativeQuery = true)
    fun quantidadeCadastro(@Param("ongId") ongId: Int, @Param("de") de: Instant?, @Param("ate") ate: Instant?): Long
}
