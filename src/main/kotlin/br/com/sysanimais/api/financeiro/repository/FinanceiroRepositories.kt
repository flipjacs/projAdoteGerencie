package br.com.sysanimais.api.financeiro.repository

import br.com.sysanimais.api.financeiro.domain.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant

interface DoacaoRepository:JpaRepository<Doacao,Long>,JpaSpecificationExecutor<Doacao> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from Doacao d where d.id=:id and d.ongId=:ongId")
 fun lockOng(@Param("id") id:Long,@Param("ongId") ongId:Int):Doacao?
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from Doacao d where d.id=:id and d.usuarioId=:usuarioId")
 fun lockOwn(@Param("id") id:Long,@Param("usuarioId") usuarioId:Int):Doacao?
 fun findByIdAndOngId(id:Long,ongId:Int):Doacao?
 fun findByIdAndUsuarioId(id:Long,usuarioId:Int):Doacao?
}
interface TotaisFinanceiros {
 val receitasRealizadas:BigDecimal;val despesasRealizadas:BigDecimal;val receitasPendentes:BigDecimal;val despesasPendentes:BigDecimal;val despesasAprovadas:BigDecimal
}
interface MovimentacaoFinanceiraRepository:JpaRepository<MovimentacaoFinanceira,Long>,JpaSpecificationExecutor<MovimentacaoFinanceira> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select m from MovimentacaoFinanceira m where m.id=:id and m.ongId=:ongId")
 fun lockOng(@Param("id") id:Long,@Param("ongId") ongId:Int):MovimentacaoFinanceira?
 fun findByIdAndOngId(id:Long,ongId:Int):MovimentacaoFinanceira?
 fun existsByDoacaoId(doacaoId:Long):Boolean
 fun existsByParcelaApadrinhamentoId(parcelaApadrinhamentoId:Long):Boolean
 @Query(value="""SELECT
 COALESCE(SUM(CASE WHEN tipo='RECEITA' AND status='REALIZADA' AND (:de IS NULL OR confirmado_em>=:de) AND (:ate IS NULL OR confirmado_em<:ate) THEN valor ELSE 0 END),0) AS receitasRealizadas,
 COALESCE(SUM(CASE WHEN tipo='DESPESA' AND status='REALIZADA' AND (:de IS NULL OR confirmado_em>=:de) AND (:ate IS NULL OR confirmado_em<:ate) THEN valor ELSE 0 END),0) AS despesasRealizadas,
 COALESCE(SUM(CASE WHEN tipo='RECEITA' AND status='PENDENTE' AND (:de IS NULL OR criado_em>=:de) AND (:ate IS NULL OR criado_em<:ate) THEN valor ELSE 0 END),0) AS receitasPendentes,
 COALESCE(SUM(CASE WHEN tipo='DESPESA' AND status='PENDENTE' AND (:de IS NULL OR criado_em>=:de) AND (:ate IS NULL OR criado_em<:ate) THEN valor ELSE 0 END),0) AS despesasPendentes,
 COALESCE(SUM(CASE WHEN tipo='DESPESA' AND status='APROVADA' AND (:de IS NULL OR aprovado_em>=:de) AND (:ate IS NULL OR aprovado_em<:ate) THEN valor ELSE 0 END),0) AS despesasAprovadas
 FROM movimentacao_financeira WHERE ong_id=:ongId""",nativeQuery=true)
 fun resumo(@Param("ongId") ongId:Int,@Param("de") de:Instant?,@Param("ate") ate:Instant?):TotaisFinanceiros
}
interface HistoricoFinanceiroRepository:JpaRepository<HistoricoFinanceiro,Long> {
 fun findAllByDoacaoIdInOrderByIdAsc(ids:Collection<Long>):List<HistoricoFinanceiro>
 fun findAllByMovimentacaoIdInOrderByIdAsc(ids:Collection<Long>):List<HistoricoFinanceiro>
}
