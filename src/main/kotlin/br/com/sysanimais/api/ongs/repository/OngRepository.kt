package br.com.sysanimais.api.ongs.repository

import br.com.sysanimais.api.ongs.domain.Ong
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OngRepository : JpaRepository<Ong, Int> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select o from Ong o where o.id = :id")
 fun lock(@Param("id") id: Int): Ong?

 @Lock(LockModeType.PESSIMISTIC_READ)
 @Query("select o from Ong o where o.id = :id")
 fun lockRead(@Param("id") id: Int): Ong?
}
