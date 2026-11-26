package br.com.sysanimais.api.equipes.service

import br.com.sysanimais.api.equipes.dto.*
import br.com.sysanimais.api.ongs.domain.EstadoOng
import br.com.sysanimais.api.ongs.repository.OngRepository
import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.domain.Usuario
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
import java.security.SecureRandom
import java.sql.SQLException
import java.util.Base64

class EquipeException(val status: HttpStatus,val code: String,message: String): RuntimeException(message)

@Service
class EquipeService(private val usuarios: UsuarioRepository, private val ongs: OngRepository,
 private val encoder: PasswordEncoder, private val db: JdbcTemplate) {
 private val internos=setOf(Perfil.ADMINISTRADOR,Perfil.VOLUNTARIO,Perfil.VETERINARIO)
 private fun proibido(): Nothing = throw EquipeException(HttpStatus.FORBIDDEN,"OPERACAO_NAO_AUTORIZADA","Operação não autorizada")
 private fun escopo(ator: UsuarioResponse,escrita: Boolean = true): Int {
  val ongId=ator.ongId ?: proibido()
  // A ONG é sempre o primeiro bloqueio; a leitura com lock do ator ocorre depois.
  val ong=if(escrita) ongs.lock(ongId) else ongs.lockRead(ongId)
  if(ong?.situacao!=EstadoOng.ATIVA) proibido()
  val atual=(if(escrita) usuarios.lockById(ator.id) else usuarios.lockByIdRead(ator.id)) ?: proibido()
  if(atual.ongId!=ongId || atual.perfil!=Perfil.ADMINISTRADOR || !atual.ativo || atual.credencialPendente || atual.tokenVersao!=ator.tokenVersao) proibido()
  return ongId
 }
 private fun perfil(p: Perfil) { if(p !in internos) throw EquipeException(HttpStatus.BAD_REQUEST,"PERFIL_INVALIDO","Perfil interno inválido") }
 private fun alvo(id: Int,ongId: Int): Usuario = usuarios.lockFuncionario(id,ongId,internos)
  ?: throw EquipeException(HttpStatus.NOT_FOUND,"FUNCIONARIO_NAO_ENCONTRADO","Funcionário não encontrado")
 private fun versao(u: Usuario, versao: Long?) {
  if(versao==null || versao<0) throw EquipeException(HttpStatus.BAD_REQUEST,"VERSAO_INVALIDA","Versão inválida")
  if(u.versao!=versao) throw EquipeException(HttpStatus.CONFLICT,"FUNCIONARIO_VERSAO_CONFLITANTE","Funcionário alterado. Recarregue os dados")
 }
 private fun impedirUltimo(ongId: Int,u: Usuario) {
  if(u.perfil==Perfil.ADMINISTRADOR && u.ativo && !u.credencialPendente && usuarios.countByOngIdAndPerfilAndAtivoTrueAndCredencialPendenteFalse(ongId,Perfil.ADMINISTRADOR)<=1)
   throw EquipeException(HttpStatus.CONFLICT,"ULTIMO_ADMINISTRADOR","A ONG precisa manter um administrador apto")
 }
 private fun audit(ongId: Int,u: Usuario,ator: Int,operacao: String,antes: String?,depois: String?,motivo: String?=null) {
  db.update("INSERT INTO auditoria_equipe(ong_id,funcionario_id,ator_id,operacao,anterior,posterior,motivo) VALUES (?,?,?,?,?,?,?)",ongId,u.id,ator,operacao,antes,depois,motivo?.trim())
 }
 private fun emailDuplicado(ex: DataIntegrityViolationException): Boolean = generateSequence(ex as Throwable?) { it.cause }
  .filterIsInstance<SQLException>().any { e ->
   (e.errorCode==1062 && Regex("(?i)for key ['`\\\"](?:[^'`\\\"]+\\.)?email['`\\\"]").containsMatchIn(e.message.orEmpty())) ||
    (e.sqlState=="23505" && Regex("(?i)USUARIO\\s*\\(\\s*EMAIL\\b").containsMatchIn(e.message.orEmpty()))
  }
 @Transactional fun criar(ator: UsuarioResponse,r: CriarFuncionarioRequest): FuncionarioResponse {
  val ongId=escopo(ator);perfil(r.perfil)
  val email=r.email.trim().lowercase()
  if(!email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) throw EquipeException(HttpStatus.BAD_REQUEST,"EMAIL_INVALIDO","E-mail inválido")
  if(usuarios.findByEmail(email)!=null) throw EquipeException(HttpStatus.CONFLICT,"EMAIL_ALREADY_REGISTERED","E-mail já cadastrado")
  val secret=ByteArray(32).also { SecureRandom().nextBytes(it) }
  val u=try { usuarios.saveAndFlush(Usuario(ongId=ongId,nome=r.nome.trim(),email=email,senhaHash=encoder.encode(Base64.getEncoder().encodeToString(secret)),perfil=r.perfil,ativo=false,credencialPendente=true,provisionamentoVirgem=true)) }
   catch(ex: DataIntegrityViolationException) { if(emailDuplicado(ex)) throw EquipeException(HttpStatus.CONFLICT,"EMAIL_ALREADY_REGISTERED","E-mail já cadastrado");throw ex }
  audit(ongId,u,ator.id,"FUNCIONARIO_CRIADO",null,"PENDENTE")
  return FuncionarioResponse.from(u)
 }
 @Transactional fun listar(ator: UsuarioResponse,page: Int,size: Int): Page<FuncionarioResponse> {
  val ongId=escopo(ator,false);if(page<0 || size !in 1..100) throw EquipeException(HttpStatus.BAD_REQUEST,"PAGINACAO_INVALIDA","Paginação inválida")
  return usuarios.findAllByOngIdAndPerfilIn(ongId,internos,PageRequest.of(page,size,Sort.by("id"))).map { FuncionarioResponse.from(it) }
 }
 @Transactional fun obter(ator: UsuarioResponse,id: Int): FuncionarioResponse {
  val ongId=escopo(ator,false); val u=usuarios.findByIdAndOngIdAndPerfilIn(id,ongId,internos) ?: throw EquipeException(HttpStatus.NOT_FOUND,"FUNCIONARIO_NAO_ENCONTRADO","Funcionário não encontrado")
  return FuncionarioResponse.from(u)
 }
 @Transactional fun atualizar(ator: UsuarioResponse,id: Int,r: AtualizarFuncionarioRequest): FuncionarioResponse {
  val ongId=escopo(ator);val u=alvo(id,ongId);versao(u,r.versao);val antigo=u.nome;val novo=r.nome.trim()
  if(antigo!=novo) { u.nome=novo;usuarios.flush();audit(ongId,u,ator.id,"FUNCIONARIO_ATUALIZADO",antigo,novo) }
  return FuncionarioResponse.from(u)
 }
 @Transactional fun alterarPerfil(ator: UsuarioResponse,id: Int,r: AlterarPerfilRequest): FuncionarioResponse {
  val ongId=escopo(ator);perfil(r.perfil)
  if(r.motivo.isBlank() || r.motivo.length>500) throw EquipeException(HttpStatus.BAD_REQUEST,"MOTIVO_OBRIGATORIO","Motivo obrigatório")
  val u=alvo(id,ongId);versao(u,r.versao)
  if(u.perfil!=r.perfil) { if(r.perfil!=Perfil.ADMINISTRADOR) impedirUltimo(ongId,u);val anterior=u.perfil.name;u.perfil=r.perfil;usuarios.flush();audit(ongId,u,ator.id,"PERFIL_ALTERADO",anterior,u.perfil.name,r.motivo) }
  return FuncionarioResponse.from(u)
 }
 @Transactional fun alterarAtivo(ator: UsuarioResponse,id: Int,ativo: Boolean,r: AlterarAtivoRequest): FuncionarioResponse {
  val ongId=escopo(ator);val u=alvo(id,ongId);versao(u,r.versao)
  if((!ativo && r.motivo.isNullOrBlank()) || (r.motivo?.length ?: 0)>500) throw EquipeException(HttpStatus.BAD_REQUEST,"MOTIVO_OBRIGATORIO","Motivo obrigatório")
  if(!ativo && !u.ativo && u.provisionamentoVirgem) { u.provisionamentoVirgem=false;usuarios.flush();audit(ongId,u,ator.id,"FUNCIONARIO_DESATIVADO","PENDENTE","DESATIVADO",r.motivo) }
  if(u.ativo!=ativo) { if(!ativo) { impedirUltimo(ongId,u);u.tokenVersao=Math.addExact(u.tokenVersao,1L) };val antigo=u.ativo;u.ativo=ativo;usuarios.flush();audit(ongId,u,ator.id,if(ativo) "FUNCIONARIO_REATIVADO" else "FUNCIONARIO_DESATIVADO",antigo.toString(),ativo.toString(),r.motivo) }
  return FuncionarioResponse.from(u)
 }
}
