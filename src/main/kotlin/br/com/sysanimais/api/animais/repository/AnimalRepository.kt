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
 fun findAllByPublicadoTrueAndAtivoTrueAndSituacao(situacao: br.com.sysanimais.api.animais.domain.SituacaoAnimal, pageable: Pageable): Page<Animal>
 fun findByIdAndPublicadoTrueAndAtivoTrueAndSituacao(id: Long, situacao: br.com.sysanimais.api.animais.domain.SituacaoAnimal): Animal?
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
