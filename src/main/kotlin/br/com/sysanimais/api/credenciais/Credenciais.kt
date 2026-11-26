package br.com.sysanimais.api.credenciais

import br.com.sysanimais.api.auth.controller.LoginRateLimitException
import br.com.sysanimais.api.auth.service.LoginRateLimiter
import br.com.sysanimais.api.equipes.dto.VersaoFuncionarioDeserializer
import br.com.sysanimais.api.equipes.service.EquipeException
import br.com.sysanimais.api.ongs.domain.EstadoOng
import br.com.sysanimais.api.ongs.repository.OngRepository
import br.com.sysanimais.api.security.UsuarioAuthenticationToken
import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.domain.Usuario
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

class EmissaoRequest(
 @field:NotNull @field:PositiveOrZero @field:JsonDeserialize(using=VersaoFuncionarioDeserializer::class) val versao: Long?,
 @field:NotBlank @field:Size(max=500) val motivo: String
)
class TrocaRequest(
 @field:NotBlank @field:Size(max=120) val email: String,
 @field:NotBlank @field:Size(max=72) val senhaTemporaria: String,
 @field:NotBlank @field:Size(min=12,max=72) val novaSenha: String
)
class CredencialResponse(val senhaTemporaria: String, val expiraEm: Instant, val versao: Long, val proximoPasso: String = "TROCAR_SENHA")
class InicialResponse(val id: Int, val versao: Long, val credencialPendente: Boolean, val expiraEm: Instant?)
private fun proibido(): Nothing = throw EquipeException(HttpStatus.FORBIDDEN,"OPERACAO_NAO_AUTORIZADA","Operação não autorizada")
private fun invalido(): Nothing = throw EquipeException(HttpStatus.UNAUTHORIZED,"CREDENCIAL_INVALIDA","Credencial inválida ou expirada")
private fun conflito(): Nothing = throw EquipeException(HttpStatus.CONFLICT,"FUNCIONARIO_VERSAO_CONFLITANTE","Funcionário alterado. Recarregue os dados")
private val internos = setOf(Perfil.ADMINISTRADOR,Perfil.VOLUNTARIO,Perfil.VETERINARIO)

@RestController
class CredenciaisController(private val service: CredenciaisService) {
 private val primeiroLimite=LoginRateLimiter()
 private val emissaoLimite=LoginRateLimiter(originLimit=30,pairLimit=8,globalLimit=120)
 private fun limitar(l: LoginRateLimiter, request: HttpServletRequest, identidade: String) {
  val espera=l.acquire(request.remoteAddr,identidade); if(espera>0) throw LoginRateLimitException(espera)
 }
 @PostMapping("/api/ong/funcionarios/{id}/credencial-temporaria")
 fun emitir(@PathVariable id: Int,@Valid @RequestBody r: EmissaoRequest, auth: UsuarioAuthenticationToken, http: HttpServletRequest): ResponseEntity<CredencialResponse> {
  limitar(emissaoLimite,http,auth.usuario.id.toString())
  return ResponseEntity.ok().header("Cache-Control","no-store").header("Pragma","no-cache").body(service.emitir(auth,null,id,r))
 }
 @GetMapping("/api/plataforma/ongs/{id}/administrador-inicial")
 fun inicial(@PathVariable id: Int,auth: UsuarioAuthenticationToken): InicialResponse = service.inicial(auth,id)
 @PostMapping("/api/plataforma/ongs/{id}/administrador-inicial/credencial-temporaria")
 fun emitirInicial(@PathVariable id: Int,@Valid @RequestBody r: EmissaoRequest,auth: UsuarioAuthenticationToken,http: HttpServletRequest): ResponseEntity<CredencialResponse> {
  limitar(emissaoLimite,http,auth.usuario.id.toString())
  return ResponseEntity.ok().header("Cache-Control","no-store").header("Pragma","no-cache").body(service.emitir(auth,id,null,r))
 }
 @PostMapping("/api/auth/primeiro-acesso/trocar-senha")
 fun trocar(@Valid @RequestBody r: TrocaRequest,http: HttpServletRequest): ResponseEntity<Void> {
  limitar(primeiroLimite,http,r.email)
  service.trocar(r)
  return ResponseEntity.noContent().header("Cache-Control","no-store").build()
 }
}

@Service
class CredenciaisService(private val usuarios: UsuarioRepository,private val encoder: PasswordEncoder,private val transacoes: CredenciaisTransacoes,
 @Value("\${app.credenciais.validade-horas:24}") private val validade: Long) {
 private val random=SecureRandom()
 private val dummyHash=encoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes)))
 init { require(validade in 1..72) { "Validade de credencial temporária deve estar entre 1 e 72 horas" } }
 fun inicial(auth: UsuarioAuthenticationToken,id: Int)=transacoes.inicial(auth,id)
 fun emitir(auth: UsuarioAuthenticationToken,ongId: Int?,id: Int?,r: EmissaoRequest): CredencialResponse {
  val segredo=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
  val hash=encoder.encode(segredo)
  val (expira,versao)=transacoes.emitir(auth,ongId,id,r,hash,validade)
  return CredencialResponse(segredo,expira,versao)
 }
 fun trocar(r: TrocaRequest) {
  if(r.novaSenha.toByteArray(Charsets.UTF_8).size>72 || r.senhaTemporaria.toByteArray(Charsets.UTF_8).size>72 || r.novaSenha.isBlank()) throw EquipeException(HttpStatus.BAD_REQUEST,"SENHA_INVALIDA","Senha inválida")
  val u=usuarios.findByEmail(r.email.trim().lowercase())
  val elegivel=u != null && u.ativo && u.credencialPendente && u.credencialTemporariaExpiraEm != null && u.ongId != null && u.perfil in internos
  val corresponde=encoder.matches(r.senhaTemporaria,if(elegivel) u!!.senhaHash else dummyHash)
  if(!corresponde || !elegivel) invalido()
  if(MessageDigest.isEqual(r.novaSenha.toByteArray(),r.senhaTemporaria.toByteArray())) throw EquipeException(HttpStatus.BAD_REQUEST,"SENHA_INVALIDA","Escolha uma senha diferente da credencial temporária")
  val senhaAtual=u!!.senhaHash
  val novoHash=encoder.encode(r.novaSenha)
  transacoes.trocar(u.id!!,u.ongId!!,senhaAtual,u.tokenVersao,novoHash)
 }
}

@Service
class CredenciaisTransacoes(private val usuarios: UsuarioRepository,private val ongs: OngRepository,private val db: JdbcTemplate) {
 private fun ator(auth: UsuarioAuthenticationToken,perfil: Perfil,ongId: Int?): Usuario {
  val u=usuarios.lockById(auth.usuario.id) ?: proibido()
  val versao=when(val v=auth.token.claims["token_version"]) { null -> 0L; is Int -> v.toLong(); is Long -> v; else -> null }
  if(!u.ativo || u.credencialPendente || u.perfil!=perfil || u.ongId!=ongId || u.tokenVersao!=versao) proibido()
  return u
 }
 private fun ong(id: Int) { if(ongs.lock(id)?.situacao != EstadoOng.ATIVA) proibido() }
 private fun ongExistente(id: Int) { if(ongs.lock(id)==null) proibido() }
 private fun inicialId(ongId: Int): Int {
  val ids=db.queryForList("SELECT usuario_alvo_id FROM auditoria_institucional WHERE ong_id=? AND operacao='ADMIN_INICIAL_PROVISIONADO' AND usuario_alvo_id IS NOT NULL",Int::class.java,ongId)
  if(ids.size!=1) throw EquipeException(HttpStatus.CONFLICT,"ADMIN_INICIAL_INDEFINIDO","Administrador inicial indisponível")
  return ids.single()
 }
 private fun inicialAlvo(ongId: Int): Usuario {
  val id=inicialId(ongId)
  val u=usuarios.lockFuncionario(id,ongId,internos) ?: throw EquipeException(HttpStatus.NOT_FOUND,"FUNCIONARIO_NAO_ENCONTRADO","Funcionário não encontrado")
  if(u.perfil!=Perfil.ADMINISTRADOR || u.primeiroAcessoConcluido || !u.credencialPendente) proibido()
  return u
 }
 @Transactional fun inicial(auth: UsuarioAuthenticationToken,ongId: Int): InicialResponse {
  ongExistente(ongId);ator(auth,Perfil.ADMIN_PLATAFORMA,null);val u=inicialAlvo(ongId)
  return InicialResponse(u.id!!,u.versao,u.credencialPendente,u.credencialTemporariaExpiraEm)
 }
 @Transactional fun emitir(auth: UsuarioAuthenticationToken,ongIdGlobal: Int?,id: Int?,r: EmissaoRequest,hash: String,validade: Long): Pair<Instant,Long> {
  val global=ongIdGlobal!=null
  val ongId=ongIdGlobal ?: auth.usuario.ongId ?: proibido()
  if(global) ongExistente(ongId) else ong(ongId)
  val a=ator(auth,if(global) Perfil.ADMIN_PLATAFORMA else Perfil.ADMINISTRADOR,if(global) null else ongId)
  val u=if(global) inicialAlvo(ongId) else usuarios.lockFuncionario(id!!,ongId,internos) ?: throw EquipeException(HttpStatus.NOT_FOUND,"FUNCIONARIO_NAO_ENCONTRADO","Funcionário não encontrado")
  if(u.versao!=r.versao) conflito()
  if(!u.ativo && !u.provisionamentoVirgem) throw EquipeException(HttpStatus.CONFLICT,"FUNCIONARIO_INATIVO","Funcionário inativo")
  if(u.perfil==Perfil.ADMINISTRADOR && u.ativo && !u.credencialPendente && usuarios.countByOngIdAndPerfilAndAtivoTrueAndCredencialPendenteFalse(ongId,Perfil.ADMINISTRADOR)<=1)
   throw EquipeException(HttpStatus.CONFLICT,"ULTIMO_ADMINISTRADOR","A ONG precisa manter um administrador apto")
  val antes=if(u.credencialPendente) "PENDENTE" else "DEFINITIVA"
  val expira=Instant.now().plus(validade,ChronoUnit.HOURS)
  u.senhaHash=hash;u.ativo=true;u.credencialPendente=true;u.provisionamentoVirgem=false;u.credencialTemporariaExpiraEm=expira;u.tokenVersao=Math.addExact(u.tokenVersao,1)
  usuarios.flush()
  if(global) db.update("INSERT INTO auditoria_institucional(ong_id,ator_id,usuario_alvo_id,operacao,estado_anterior,estado_posterior,motivo) VALUES (?,?,?,?,?,?,?)",ongId,a.id,u.id,"CREDENCIAL_INICIAL_EMITIDA",antes,"PENDENTE",r.motivo.trim())
  else db.update("INSERT INTO auditoria_equipe(ong_id,funcionario_id,ator_id,operacao,anterior,posterior,motivo) VALUES (?,?,?,?,?,?,?)",ongId,u.id,a.id,"CREDENCIAL_TEMPORARIA_EMITIDA",antes,"PENDENTE",r.motivo.trim())
  return expira to u.versao
 }
 @Transactional fun trocar(id: Int,ongId: Int,hashAnterior: String,tokenVersao: Long,novoHash: String) {
  ong(ongId)
  val u=usuarios.lockFuncionario(id,ongId,internos) ?: invalido()
  if(!u.ativo || !u.credencialPendente || u.tokenVersao!=tokenVersao || u.credencialTemporariaExpiraEm?.isAfter(Instant.now())!=true || !MessageDigest.isEqual(u.senhaHash.toByteArray(),hashAnterior.toByteArray())) invalido()
  u.senhaHash=novoHash;u.credencialPendente=false;u.credencialTemporariaExpiraEm=null;u.primeiroAcessoConcluido=true;u.tokenVersao=Math.addExact(u.tokenVersao,1)
  usuarios.flush()
  db.update("INSERT INTO auditoria_equipe(ong_id,funcionario_id,ator_id,operacao,anterior,posterior) VALUES (?,?,?,?,?,?)",ongId,u.id,u.id,"PRIMEIRO_ACESSO_CONCLUIDO","PENDENTE","DEFINITIVA")
 }
}
