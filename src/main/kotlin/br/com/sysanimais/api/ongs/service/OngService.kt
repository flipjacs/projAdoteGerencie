package br.com.sysanimais.api.ongs.service

import br.com.sysanimais.api.ongs.domain.*
import br.com.sysanimais.api.ongs.dto.*
import br.com.sysanimais.api.ongs.repository.OngRepository
import br.com.sysanimais.api.usuarios.domain.*
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

@Service
class OngService(private val ongs: OngRepository, private val usuarios: UsuarioRepository,
 private val encoder: PasswordEncoder, private val db: JdbcTemplate) {
 private fun autorizar(ator: UsuarioResponse, escrita: Boolean = true) {
  val u=if(escrita) usuarios.lockById(ator.id) else usuarios.lockByIdRead(ator.id)
  if(u==null || !u.ativo || u.credencialPendente || u.perfil!=Perfil.ADMIN_PLATAFORMA || u.ongId!=null || u.tokenVersao!=ator.tokenVersao) throw ResponseStatusException(HttpStatus.FORBIDDEN)
 }
 private fun audit(ong: Int, ator: Int, operacao: String, anterior: String?, posterior: String?, motivo: String? = null, alvo: Int? = null) {
  db.update("INSERT INTO auditoria_institucional(ong_id,ator_id,operacao,estado_anterior,estado_posterior,motivo,usuario_alvo_id) VALUES (?,?,?,?,?,?,?)",ong,ator,operacao,anterior,posterior,motivo,alvo)
 }
 private fun identificacao(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
 @Transactional fun criar(ator: UsuarioResponse, r: CriarOngRequest): OngResponse {
  autorizar(ator)
  try {
   val o=ongs.saveAndFlush(Ong(nome=r.nome.trim(), email=r.email.trim().lowercase(), identificacao=identificacao(r.identificacao), telefone=r.telefone?.trim(), cidade=r.cidade?.trim(), estado=r.estado?.uppercase(), criadoPor=ator.id))
   val random=ByteArray(32).also { SecureRandom().nextBytes(it) }
   val unusable=Base64.getEncoder().encodeToString(random)
   val admin=usuarios.saveAndFlush(Usuario(ongId=o.id, nome=r.administrador.nome.trim(), email=r.administrador.email.trim().lowercase(), senhaHash=encoder.encode(unusable), perfil=Perfil.ADMINISTRADOR, ativo=false, credencialPendente=true, provisionamentoVirgem=true, primeiroAcessoConcluido=false))
   audit(o.id!!,ator.id,"ONG_CRIADA",null,"ATIVA")
   audit(o.id!!,ator.id,"ADMIN_INICIAL_PROVISIONADO",null,"PENDENTE",alvo=admin.id)
   return OngResponse.from(o)
  } catch (_: DataIntegrityViolationException) { throw ResponseStatusException(HttpStatus.CONFLICT,"Conflito de cadastro institucional") }
 }
 @Transactional(readOnly=true) fun listar(ator: UsuarioResponse,page: Int,size: Int): Page<OngResponse> {
  autorizar(ator,false); if(page<0 || size !in 1..100) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
  return ongs.findAll(PageRequest.of(page,size,Sort.by("id"))).map { OngResponse.from(it) }
 }
 @Transactional(readOnly=true) fun obter(ator: UsuarioResponse,id: Int): OngResponse {
  autorizar(ator,false); return OngResponse.from(ongs.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) })
 }
 @Transactional fun atualizar(ator: UsuarioResponse,id: Int,r: AtualizarOngRequest): OngResponse {
  val o=ongs.lock(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND); autorizar(ator)
  if(r.versao==null) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
  if(o.versao!=r.versao) throw ResponseStatusException(HttpStatus.CONFLICT,"Dados institucionais desatualizados")
  val anterior=o.versao
  o.nome=r.nome.trim();o.email=r.email.trim().lowercase();o.identificacao=identificacao(r.identificacao);o.telefone=r.telefone?.trim();o.cidade=r.cidade?.trim();o.estado=r.estado?.uppercase();o.atualizadoEm=Instant.now()
  try { ongs.flush() } catch (_: DataIntegrityViolationException) { throw ResponseStatusException(HttpStatus.CONFLICT,"Conflito de cadastro institucional") }
  audit(id,ator.id,"ONG_ATUALIZADA","v$anterior","v${o.versao}")
  return OngResponse.from(o)
 }
 @Transactional fun situacao(ator: UsuarioResponse,id: Int,nova: EstadoOng,motivo: String?): OngResponse {
  val o=ongs.lock(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND); autorizar(ator)
  if(nova==EstadoOng.SUSPENSA && motivo.isNullOrBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
  if(nova==EstadoOng.ATIVA && o.situacao!=nova && usuarios.countByOngIdAndPerfilAndAtivoTrue(id,Perfil.ADMINISTRADOR)==0L)
   throw ResponseStatusException(HttpStatus.CONFLICT,"A ONG precisa de um administrador ativo para ser reativada")
  if(o.situacao!=nova) { val anterior=o.situacao.name;o.situacao=nova;o.atualizadoEm=Instant.now();ongs.flush();audit(id,ator.id,if(nova==EstadoOng.SUSPENSA) "ONG_SUSPENSA" else "ONG_REATIVADA",anterior,nova.name,motivo?.trim()) }
  return OngResponse.from(o)
 }
}
