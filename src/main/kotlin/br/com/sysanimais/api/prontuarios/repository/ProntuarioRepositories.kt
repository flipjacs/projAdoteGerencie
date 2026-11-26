package br.com.sysanimais.api.prontuarios.repository

import br.com.sysanimais.api.prontuarios.domain.*
import jakarta.persistence.LockModeType
import org.springframework.data.domain.*
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param

interface ProntuarioRepository:JpaRepository<Prontuario,Long>,JpaSpecificationExecutor<Prontuario> {
 fun findByIdAndAnimalIdAndOngId(id:Long,animalId:Long,ongId:Int):Prontuario?
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from Prontuario p where p.id=:id and p.animalId=:animalId and p.ongId=:ongId")
 fun lockRecord(@Param("id") id:Long,@Param("animalId") animalId:Long,@Param("ongId") ongId:Int):Prontuario?
 @Query("""select count(a)>0 from Adocao a, SolicitacaoAdocao s where a.solicitacaoId=s.id
 and a.animalId=:animalId and a.usuarioId=:usuarioId and a.ongId=:ongId
 and s.animalId=a.animalId and s.usuarioId=a.usuarioId and s.ongId=a.ongId
 and s.status=br.com.sysanimais.api.adocoes.domain.StatusSolicitacao.CONCLUIDA""")
 fun adotadoPor(@Param("animalId") animalId:Long,@Param("usuarioId") usuarioId:Int,@Param("ongId") ongId:Int):Boolean
}
interface CorrecaoProntuarioRepository:JpaRepository<CorrecaoProntuario,Long> {
 fun findAllByProntuarioId(prontuarioId:Long,pageable:Pageable):Page<CorrecaoProntuario>
}
