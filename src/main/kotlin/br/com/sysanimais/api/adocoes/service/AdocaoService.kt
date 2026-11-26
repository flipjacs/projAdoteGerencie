package br.com.sysanimais.api.adocoes.service

import br.com.sysanimais.api.adocoes.domain.*
import br.com.sysanimais.api.adocoes.dto.*
import br.com.sysanimais.api.adocoes.repository.*
import br.com.sysanimais.api.animais.domain.*
import br.com.sysanimais.api.animais.repository.AnimalRepository
import br.com.sysanimais.api.ongs.domain.EstadoOng
import br.com.sysanimais.api.ongs.repository.OngRepository
import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class AdocaoService(
 private val requests:SolicitacaoAdocaoRepository,
 private val completions:AdocaoRepository,
 private val history:HistoricoAdocaoRepository,
 private val animals:AnimalRepository,
 private val ongs:OngRepository,
 private val users:UsuarioRepository,
 private val encerramentoApadrinhamento:br.com.sysanimais.api.apadrinhamentos.service.EncerramentoApadrinhamentoService
) {
 private val staff=setOf(Perfil.ADMINISTRADOR,Perfil.VOLUNTARIO)
 private fun denied():Nothing=throw ResponseStatusException(HttpStatus.FORBIDDEN)
 private fun missing():Nothing=throw ResponseStatusException(HttpStatus.NOT_FOUND)
 private fun conflict():Nothing=throw ResponseStatusException(HttpStatus.CONFLICT,"Solicitação ou animal incompatível com esta operação")
 private fun actor(u:UsuarioResponse,internal:Boolean) {
  val current=users.lockByIdRead(u.id) ?: denied()
  if(!current.ativo || current.credencialPendente || current.tokenVersao!=u.tokenVersao || current.perfil!=u.perfil || current.ongId!=u.ongId) denied()
  if(internal) { if(current.perfil !in staff || current.ongId==null) denied() }
  else if(current.perfil!=Perfil.USUARIO) denied()
 }
 private fun ong(id:Int,active:Boolean) {
  val o=ongs.lockRead(id) ?: missing()
  if(active && o.situacao!=EstadoOng.ATIVA) denied()
 }
 private fun internalScope(u:UsuarioResponse):Int {
  if(u.perfil !in staff) denied()
  val id=u.ongId ?: denied();ong(id,true);actor(u,true);return id
 }
 private fun pageable(page:Int,size:Int):PageRequest {
  if(page<0 || size !in 1..100) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
  return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))
 }
 private fun response(s:SolicitacaoAdocao,internal:Boolean):SolicitacaoResponse {
  val a=animals.findById(s.animalId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
  val o=ongs.findById(s.ongId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
  fun person(id:Int?,email:Boolean=false):PessoaResumo?=id?.let { users.findById(it).orElse(null) }?.let { PessoaResumo(it.id!!,it.nome,if(email) it.email else null) }
  val completed=completions.findBySolicitacaoId(s.id!!)?.let { EntregaResponse(it.id!!,it.dataEntrega) }
  val events=history.findAllBySolicitacaoIdOrderByIdAsc(s.id!!).map { HistoricoResponse(it.operacao,it.anterior,it.posterior,it.justificativa,it.ocorridoEm) }
  return SolicitacaoResponse(s.id!!,AnimalResumo(a.id!!,a.nome),OngResumo(o.id!!,o.nome),s.status,s.mensagem,s.justificativaRejeicao,s.justificativaCancelamento,s.analisadoEm,s.canceladoEm,completed,s.criadoEm,s.atualizadoEm,s.versao,if(internal) person(s.usuarioId,true) else null,if(internal) person(s.analisadoPor) else null,if(internal) person(s.canceladoPor) else null,events)
 }
 private fun event(s:SolicitacaoAdocao,actor:Int,operation:String,before:String?,reason:String?=null) {
  history.save(HistoricoAdocao(solicitacaoId=s.id!!,atorId=actor,operacao=operation,anterior=before,posterior=s.status.name,justificativa=reason))
 }
 private fun change(s:SolicitacaoAdocao,u:UsuarioResponse,next:StatusSolicitacao,operation:String,reason:String?=null) {
  val before=s.status.name;s.status=next;s.atualizadoEm=Instant.now();requests.flush();event(s,u.id,operation,before,reason)
 }
 private fun animalState(a:Animal,u:UsuarioResponse,status:SituacaoAnimal) {
  val now=Instant.now();a.situacao=status;a.situacaoAlteradaPor=u.id;a.situacaoAtualizadaEm=now;a.atualizadoEm=now;animals.flush()
 }
 private fun eligible(a:Animal)=a.ativo && a.publicado && a.situacao==SituacaoAnimal.DISPONIVEL && !completions.existsByAnimalId(a.id!!)
 // Same lock order as institutional animal writes: ONG, actor, animal, requests.
 // Scalar lookups avoid caching stale entities before the pessimistic locks.
 private fun transition(u:UsuarioResponse,id:Long,internal:Boolean):Triple<Animal,SolicitacaoAdocao,List<SolicitacaoAdocao>> {
  val d=requests.destino(id) ?: missing()
  if(internal) { if(u.perfil !in staff) denied();if(d.ongId!=u.ongId) missing() }
  else if(d.usuarioId!=u.id) missing()
  ong(d.ongId,internal);actor(u,internal)
  val a=animals.lockByIdAndOngId(d.animalId,d.ongId) ?: missing()
  val all=requests.lockAllByAnimalId(a.id!!)
  val s=all.firstOrNull { it.id==id } ?: missing()
  if(s.ongId!=a.ongId || (internal && s.ongId!=u.ongId) || (!internal && s.usuarioId!=u.id)) missing()
  return Triple(a,s,all)
 }
 private fun approved(a:Animal,s:SolicitacaoAdocao,all:List<SolicitacaoAdocao>):Boolean =
  s.status==StatusSolicitacao.APROVADA && a.situacao==SituacaoAnimal.EM_PROCESSO_ADOCAO && all.count { it.status==StatusSolicitacao.APROVADA }==1 && !completions.existsByAnimalId(a.id!!)

 @Transactional fun create(u:UsuarioResponse,r:SolicitarAdocaoRequest):SolicitacaoResponse {
  if(u.perfil!=Perfil.USUARIO) denied()
  val animalId=r.animalId ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST)
  val ongId=animals.ongId(animalId) ?: missing()
  ong(ongId,false);actor(u,false)
  val a=animals.lockByIdAndOngId(animalId,ongId) ?: missing()
  if(ongs.findById(ongId).orElseThrow().situacao!=EstadoOng.ATIVA || !eligible(a)) conflict()
  val all=requests.lockAllByAnimalId(animalId)
  if(all.any { it.usuarioId==u.id && it.status==StatusSolicitacao.PENDENTE }) conflict()
  val s=requests.saveAndFlush(SolicitacaoAdocao(animalId=animalId,usuarioId=u.id,ongId=ongId,mensagem=r.mensagem))
  event(s,u.id,"SOLICITADA",null);return response(s,false)
 }
 @Transactional(readOnly=true) fun mine(u:UsuarioResponse,page:Int,size:Int):org.springframework.data.domain.Page<SolicitacaoResponse> {
  actor(u,false);return requests.findAllByUsuarioId(u.id,pageable(page,size)).map { response(it,false) }
 }
 @Transactional(readOnly=true) fun ownDetails(u:UsuarioResponse,id:Long):SolicitacaoResponse {
  actor(u,false);val s=requests.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) };if(s.usuarioId!=u.id) missing();return response(s,false)
 }
 @Transactional(readOnly=true) fun internalList(u:UsuarioResponse,page:Int,size:Int,status:StatusSolicitacao?,animalId:Long?):org.springframework.data.domain.Page<SolicitacaoResponse> {
  val ongId=internalScope(u);if(animalId!=null && animalId<1) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
  return requests.listarOng(ongId,status,animalId,pageable(page,size)).map { response(it,true) }
 }
 @Transactional(readOnly=true) fun internalDetails(u:UsuarioResponse,id:Long):SolicitacaoResponse {
  val ongId=internalScope(u);val s=requests.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) };if(s.ongId!=ongId) missing();return response(s,true)
 }
 @Transactional fun approve(u:UsuarioResponse,id:Long):SolicitacaoResponse {
  val (a,s,all)=transition(u,id,true)
  if(s.status!=StatusSolicitacao.PENDENTE || !eligible(a) || all.any { it.status==StatusSolicitacao.APROVADA }) conflict()
  s.analisadoPor=u.id;s.analisadoEm=Instant.now();change(s,u,StatusSolicitacao.APROVADA,"APROVADA");animalState(a,u,SituacaoAnimal.EM_PROCESSO_ADOCAO)
  return response(s,true)
 }
 @Transactional fun reject(u:UsuarioResponse,id:Long,r:JustificativaRequest):SolicitacaoResponse {
  val (_,s,_)=transition(u,id,true);if(s.status!=StatusSolicitacao.PENDENTE) conflict()
  s.justificativaRejeicao=r.justificativa.trim();s.analisadoPor=u.id;s.analisadoEm=Instant.now();change(s,u,StatusSolicitacao.REJEITADA,"REJEITADA",s.justificativaRejeicao)
  return response(s,true)
 }
 @Transactional fun cancel(u:UsuarioResponse,id:Long):SolicitacaoResponse {
  val (a,s,all)=transition(u,id,false)
  if(s.status==StatusSolicitacao.APROVADA && approved(a,s,all)) animalState(a,u,SituacaoAnimal.DISPONIVEL)
  else if(s.status!=StatusSolicitacao.PENDENTE) conflict()
  s.canceladoPor=u.id;s.canceladoEm=Instant.now();change(s,u,StatusSolicitacao.CANCELADA,"CANCELADA_PELO_USUARIO")
  return response(s,false)
 }
 @Transactional fun cancelApproval(u:UsuarioResponse,id:Long,r:JustificativaRequest):SolicitacaoResponse {
  val (a,s,all)=transition(u,id,true);if(!approved(a,s,all)) conflict()
  s.justificativaCancelamento=r.justificativa.trim();s.canceladoPor=u.id;s.canceladoEm=Instant.now();change(s,u,StatusSolicitacao.CANCELADA,"APROVACAO_CANCELADA",s.justificativaCancelamento);animalState(a,u,SituacaoAnimal.DISPONIVEL)
  return response(s,true)
 }
 @Transactional fun deliver(u:UsuarioResponse,id:Long):SolicitacaoResponse {
  val (a,s,all)=transition(u,id,true);if(!a.ativo || !approved(a,s,all)) conflict()
  completions.saveAndFlush(Adocao(solicitacaoId=s.id!!,animalId=a.id!!,usuarioId=s.usuarioId,ongId=s.ongId,confirmadoPor=u.id))
  change(s,u,StatusSolicitacao.CONCLUIDA,"ENTREGA_CONFIRMADA");animalState(a,u,SituacaoAnimal.ADOTADO)
  encerramentoApadrinhamento.encerrarAtivos(a,br.com.sysanimais.api.apadrinhamentos.domain.MotivoEncerramento.ADOCAO_CONCLUIDA,completions.findBySolicitacaoId(s.id!!)!!.dataEntrega)
  for(other in all.filter { it.status==StatusSolicitacao.PENDENTE }) {
   other.justificativaRejeicao="Animal adotado por outro solicitante.";other.analisadoPor=u.id;other.analisadoEm=Instant.now();change(other,u,StatusSolicitacao.REJEITADA,"REJEITADA_POR_ADOCAO",other.justificativaRejeicao)
  }
  return response(s,true)
 }
}
