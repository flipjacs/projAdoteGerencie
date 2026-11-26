package br.com.sysanimais.api.animais.repository

import br.com.sysanimais.api.animais.domain.Animal
import br.com.sysanimais.api.animais.domain.AnimalFoto
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AnimalRepository : JpaRepository<Animal,Long> {
 @Query("select a.ongId from Animal a where a.id=:id")
 fun ongId(@Param("id") id:Long):Int?
 @Query("select a from Animal a where a.publicado=true and a.ativo=true and a.situacao=br.com.sysanimais.api.animais.domain.SituacaoAnimal.DISPONIVEL and (:especie is null or a.especie=:especie) and (:sexo is null or a.sexo=:sexo) and (:porte is null or a.porte=:porte) and exists (select o.id from Ong o where o.id=a.ongId and o.situacao=br.com.sysanimais.api.ongs.domain.EstadoOng.ATIVA)")
 fun catalog(@Param("especie") especie: br.com.sysanimais.api.animais.domain.EspecieAnimal?, @Param("sexo") sexo: br.com.sysanimais.api.animais.domain.SexoAnimal?, @Param("porte") porte: br.com.sysanimais.api.animais.domain.PorteAnimal?, pageable: Pageable): Page<Animal>
 @Query("select a from Animal a where a.publicado=true and a.ativo=true and a.situacao=:situacao and exists (select o.id from Ong o where o.id=a.ongId and o.situacao=br.com.sysanimais.api.ongs.domain.EstadoOng.ATIVA)")
 fun findAllByPublicadoTrueAndAtivoTrueAndSituacao(@Param("situacao") situacao: br.com.sysanimais.api.animais.domain.SituacaoAnimal, pageable: Pageable): Page<Animal>
 @Query("select a from Animal a where a.id=:id and a.publicado=true and a.ativo=true and a.situacao=:situacao and exists (select o.id from Ong o where o.id=a.ongId and o.situacao=br.com.sysanimais.api.ongs.domain.EstadoOng.ATIVA)")
 fun findByIdAndPublicadoTrueAndAtivoTrueAndSituacao(@Param("id") id: Long, @Param("situacao") situacao: br.com.sysanimais.api.animais.domain.SituacaoAnimal): Animal?
 fun findByIdAndOngId(id: Long, ongId: Int): Animal?
 fun findAllByOngId(ongId: Int, pageable: Pageable): Page<Animal>
 fun findAllByOngIdAndAtivo(ongId: Int, ativo: Boolean, pageable: Pageable): Page<Animal>
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select a from Animal a where a.id = :id and a.ongId = :ongId")
 fun lockByIdAndOngId(@Param("id") id: Long, @Param("ongId") ongId: Int): Animal?
}
interface AnimalFotoRepository : JpaRepository<AnimalFoto,Long> {
 fun findAllByAnimalIdOrderByIdAsc(animalId: Long): List<AnimalFoto>
 fun findAllByAnimalIdInOrderByIdAsc(animalIds: Collection<Long>): List<AnimalFoto>
 fun findByIdAndAnimalId(id: Long, animalId: Long): AnimalFoto?
 fun countByAnimalId(animalId: Long): Long
}
