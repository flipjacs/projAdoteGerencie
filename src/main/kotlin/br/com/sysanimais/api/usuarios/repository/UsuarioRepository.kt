package br.com.sysanimais.api.usuarios.repository

import br.com.sysanimais.api.usuarios.domain.Usuario

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import br.com.sysanimais.api.usuarios.domain.Perfil

interface UsuarioRepository : JpaRepository<Usuario, Int> {
    fun findByEmail(email: String): Usuario?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id=:id")
    fun lockById(@Param("id") id: Int): Usuario?
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select u from Usuario u where u.id=:id")
    fun lockByIdRead(@Param("id") id: Int): Usuario?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id=:id and u.ongId=:ongId and u.perfil in :perfis")
    fun lockFuncionario(@Param("id") id: Int,@Param("ongId") ongId: Int,@Param("perfis") perfis: Collection<Perfil>): Usuario?
    fun findByIdAndOngIdAndPerfilIn(id: Int,ongId: Int,perfis: Collection<Perfil>): Usuario?
    fun findAllByOngIdAndPerfilIn(ongId: Int,perfis: Collection<Perfil>,page: Pageable): Page<Usuario>
    fun countByOngIdAndPerfilAndAtivoTrueAndCredencialPendenteFalse(ongId: Int,perfil: Perfil): Long
    fun countByOngIdAndPerfilAndAtivoTrue(ongId: Int, perfil: Perfil): Long
}
