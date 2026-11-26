package br.com.sysanimais.api.apadrinhamentos.repository

import br.com.sysanimais.api.apadrinhamentos.domain.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface DestinoApadrinhamento {val animalId:Long;val ongId:Int;val usuarioId:Int}
interface ProximoVencimentoApadrinhamento {val apadrinhamentoId:Long;val vencimento:LocalDate}
interface ApadrinhamentoRepository:JpaRepository<Apadrinhamento,Long>,JpaSpecificationExecutor<Apadrinhamento> {
 @Query("select a.animalId as animalId,a.ongId as ongId,a.usuarioId as usuarioId from Apadrinhamento a where a.id=:id")
 fun destino(@Param("id") id:Long):DestinoApadrinhamento?
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from Apadrinhamento a where a.id=:id and a.ongId=:ongId")
 fun lockOng(@Param("id") id:Long,@Param("ongId") ongId:Int):Apadrinhamento?
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from Apadrinhamento a where a.animalId=:animalId and a.status=br.com.sysanimais.api.apadrinhamentos.domain.StatusApadrinhamento.ATIVO order by a.id")
 fun lockAtivos(@Param("animalId") animalId:Long):List<Apadrinhamento>
 fun existsByUsuarioIdAndAnimalIdAndModalidadeAndStatus(usuarioId:Int,animalId:Long,modalidade:ModalidadeApadrinhamento,status:StatusApadrinhamento):Boolean
}
interface ParcelaApadrinhamentoRepository:JpaRepository<ParcelaApadrinhamento,Long>,JpaSpecificationExecutor<ParcelaApadrinhamento> {
 // Current read also when called inside the existing K4/K5 REPEATABLE READ transaction.
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from ParcelaApadrinhamento p where p.apadrinhamentoId=:id order by p.numero desc")
 fun lockExistentes(@Param("id") id:Long):List<ParcelaApadrinhamento>
 @Query("select p.apadrinhamentoId as apadrinhamentoId,min(p.vencimento) as vencimento from ParcelaApadrinhamento p where p.apadrinhamentoId in :ids and p.status=br.com.sysanimais.api.apadrinhamentos.domain.StatusParcela.PENDENTE group by p.apadrinhamentoId")
 fun proximos(@Param("ids") ids:Collection<Long>):List<ProximoVencimentoApadrinhamento>
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from ParcelaApadrinhamento p where p.id=:id and p.apadrinhamentoId=:apadrinhamentoId and p.ongId=:ongId")
 fun lockParcela(@Param("id") id:Long,@Param("apadrinhamentoId") apadrinhamentoId:Long,@Param("ongId") ongId:Int):ParcelaApadrinhamento?
}
