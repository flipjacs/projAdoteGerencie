package br.com.sysanimais.api.adocoes.repository

import br.com.sysanimais.api.adocoes.domain.*
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.*
import jakarta.persistence.LockModeType

interface DestinoAdocao { val animalId:Long; val ongId:Int; val usuarioId:Int }

interface SolicitacaoAdocaoRepository:JpaRepository<SolicitacaoAdocao,Long> {
 @Query("select s.animalId as animalId,s.ongId as ongId,s.usuarioId as usuarioId from SolicitacaoAdocao s where s.id=:id")
 fun destino(@Param("id") id:Long):DestinoAdocao?
 fun findAllByUsuarioId(usuarioId:Int,pageable:Pageable):Page<SolicitacaoAdocao>
 @Query("select s from SolicitacaoAdocao s where s.ongId=:ongId and (:status is null or s.status=:status) and (:animalId is null or s.animalId=:animalId)")
 fun listarOng(@Param("ongId") ongId:Int,@Param("status") status:StatusSolicitacao?,@Param("animalId") animalId:Long?,pageable:Pageable):Page<SolicitacaoAdocao>
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select s from SolicitacaoAdocao s where s.animalId=:animalId order by s.id")
 fun lockAllByAnimalId(@Param("animalId") animalId:Long):List<SolicitacaoAdocao>
 fun existsByAnimalIdAndStatus(animalId:Long,status:StatusSolicitacao):Boolean
}
interface AdocaoRepository:JpaRepository<Adocao,Long> {
 fun findBySolicitacaoId(solicitacaoId:Long):Adocao?
 fun existsByAnimalId(animalId:Long):Boolean
}
interface HistoricoAdocaoRepository:JpaRepository<HistoricoAdocao,Long> {
 fun findAllBySolicitacaoIdOrderByIdAsc(solicitacaoId:Long):List<HistoricoAdocao>
}
