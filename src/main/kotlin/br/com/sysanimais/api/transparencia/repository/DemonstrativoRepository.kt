package br.com.sysanimais.api.transparencia.repository

import br.com.sysanimais.api.transparencia.domain.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param

interface DemonstrativoRepository : JpaRepository<DemonstrativoTransparencia, Long>, JpaSpecificationExecutor<DemonstrativoTransparencia> {
    fun findByIdAndOngId(id: Long, ongId: Int): DemonstrativoTransparencia?
    fun findByIdAndOngIdAndStatus(id: Long, ongId: Int, status: StatusDemonstrativo): DemonstrativoTransparencia?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DemonstrativoTransparencia d where d.id = :id and d.ongId = :ongId")
    fun lockOng(@Param("id") id: Long, @Param("ongId") ongId: Int): DemonstrativoTransparencia?
}
