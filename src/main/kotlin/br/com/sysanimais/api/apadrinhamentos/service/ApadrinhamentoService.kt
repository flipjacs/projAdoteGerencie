package br.com.sysanimais.api.apadrinhamentos.service

import br.com.sysanimais.api.apadrinhamentos.domain.*
import br.com.sysanimais.api.apadrinhamentos.dto.*
import br.com.sysanimais.api.apadrinhamentos.repository.*
import br.com.sysanimais.api.animais.domain.*
import br.com.sysanimais.api.animais.repository.AnimalRepository
import br.com.sysanimais.api.ongs.domain.EstadoOng
import br.com.sysanimais.api.ongs.repository.OngRepository
import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import br.com.sysanimais.api.financeiro.service.FinanceiroService
import br.com.sysanimais.api.financeiro.repository.MovimentacaoFinanceiraRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import org.springframework.data.domain.*
import org.springframework.data.jpa.domain.Specification
import java.time.*
import java.math.BigDecimal

// Lock waiters must see the prior commit rather than MySQL REPEATABLE READ snapshots.
@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class ApadrinhamentoService(private val sponsors:ApadrinhamentoRepository,private val parcelas:ParcelaApadrinhamentoRepository,private val generator:ParcelasApadrinhamentoService,private val animals:AnimalRepository,private val ongs:OngRepository,private val users:UsuarioRepository,private val finance:FinanceiroService,private val movements:MovimentacaoFinanceiraRepository) {
 private val staff=setOf(Perfil.ADMINISTRADOR,Perfil.VOLUNTARIO)
 private fun actor(u:UsuarioResponse,internal:Boolean) {
  val c=users.lockByIdRead(u.id)?:apadDenied()
  if(!c.ativo||c.credencialPendente||c.tokenVersao!=u.tokenVersao||c.perfil!=u.perfil||c.ongId!=u.ongId)apadDenied()
  if(if(internal)c.perfil !in staff else c.perfil!=Perfil.USUARIO)apadDenied()
 }
 private fun org(id:Int,active:Boolean) {val o=ongs.lockRead(id)?:apadMissing();if(active&&o.situacao!=EstadoOng.ATIVA)apadDenied()}
 private fun scope(u:UsuarioResponse,admin:Boolean=false):Int {if(u.perfil !in staff||admin&&u.perfil!=Perfil.ADMINISTRADOR)apadDenied();val id=u.ongId?:apadDenied();org(id,true);actor(u,true);return id}
 private fun paging(page:Int,size:Int,parts:Boolean=false):PageRequest {if(page<0||size !in 1..100||page.toLong()*size>Int.MAX_VALUE)apadBad("Paginação inválida");return PageRequest.of(page,size,if(parts)Sort.by("vencimento","numero")else Sort.by(Sort.Direction.DESC,"criadoEm","id"))}
 private fun day(value:String?):LocalDate? {if(value==null)return null;if(!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))apadBad("Período inválido");return try{LocalDate.parse(value).also{if(it.year !in 1000..9999)apadBad()}}catch(_:DateTimeException){apadBad("Período inválido")}}
 // Same lock order as K4/K5: ONG, actor, animal, sponsorship, installment.
 private fun locked(u:UsuarioResponse,id:Long,internal:Boolean,admin:Boolean=false):Apadrinhamento {
  if(internal){if(u.perfil !in staff||admin&&u.perfil!=Perfil.ADMINISTRADOR)apadDenied()}else if(u.perfil!=Perfil.USUARIO)apadDenied()
  val d=sponsors.destino(id)?:apadMissing()
  if(if(internal)d.ongId!=u.ongId else d.usuarioId!=u.id)apadMissing()
  org(d.ongId,internal);actor(u,internal);animals.lockByIdAndOngId(d.animalId,d.ongId)?:apadMissing()
  val a=sponsors.lockOng(id,d.ongId)?:apadMissing();if(!internal&&a.usuarioId!=u.id)apadMissing();return a
 }
 private fun money(value:BigDecimal)=value.setScale(2).toPlainString()
 private fun responses(rows:List<Apadrinhamento>,internal:Boolean,first:ParcelaApadrinhamento?=null):List<ApadrinhamentoResponse> {
  if(rows.isEmpty())return emptyList()
  val aa=animals.findAllById(rows.map{it.animalId}.distinct()).associateBy{it.id!!};val oo=ongs.findAllById(rows.map{it.ongId}.distinct()).associateBy{it.id!!}
  val people=if(internal)users.findAllById(rows.map{it.usuarioId}.distinct()).associateBy{it.id!!}else emptyMap()
  val next=parcelas.proximos(rows.map{it.id!!}).associate{it.apadrinhamentoId to it.vencimento}
  return rows.map { a -> val animal=aa[a.animalId]?:apadMissing();val o=oo[a.ongId]?:apadMissing();val p=people[a.usuarioId]
   ApadrinhamentoResponse(a.id!!,AnimalApadrinhamento(animal.id!!,animal.nome),OngApadrinhamento(o.id!!,o.nome),a.modalidade,money(a.valor),a.status,a.dataInicio,a.dataEncerramento,a.motivoEncerramento,a.justificativaEncerramento,next[a.id],a.criadoEm,a.atualizadoEm,a.versao,first?.takeIf{it.apadrinhamentoId==a.id}?.let{parcelaResponse(it,null)},p?.let{PadrinhoResponse(it.id!!,it.nome,it.email)}) }
 }
 private fun response(a:Apadrinhamento,internal:Boolean,first:ParcelaApadrinhamento?=null)=responses(listOf(a),internal,first).single()
 private fun parcelaResponse(p:ParcelaApadrinhamento,person:ConfirmadorParcela?)=ParcelaResponse(p.id!!,p.numero,money(p.valor),p.vencimento,p.status,p.status==StatusParcela.PENDENTE&&p.vencimento<LocalDate.now(ZoneOffset.UTC),p.confirmadoEm,person)
 fun create(u:UsuarioResponse,r:RegistrarApadrinhamentoRequest):ApadrinhamentoResponse {
  if(u.perfil!=Perfil.USUARIO)apadDenied();val id=r.animalId?.takeIf{it>0}?:apadBad()
  val orgId=animals.ongId(id)?:apadMissing();org(orgId,false);actor(u,false)
  val a=animals.lockByIdAndOngId(id,orgId)?:apadMissing();val o=ongs.findById(orgId).orElse(null)?:apadMissing()
  if(!a.ativo||!a.publicado||a.situacao !in setOf(SituacaoAnimal.DISPONIVEL,SituacaoAnimal.EM_PROCESSO_ADOCAO)||o.situacao!=EstadoOng.ATIVA)apadConflict("Animal indisponível para apadrinhamento")
  val mode=try{ModalidadeApadrinhamento.valueOf(r.modalidade?.trim()?:apadBad())}catch(_:IllegalArgumentException){apadBad("Modalidade inválida")}
  val amount=r.valor?:apadBad();if(amount.signum()<=0||amount.scale()>2||amount>BigDecimal("9999999999.99"))apadBad()
  if(sponsors.existsByUsuarioIdAndAnimalIdAndModalidadeAndStatus(u.id,id,mode,StatusApadrinhamento.ATIVO))apadConflict("Apadrinhamento ativo já existente para este animal e modalidade")
  val now=Instant.now();val start=LocalDate.now(ZoneOffset.UTC)
  val s=sponsors.saveAndFlush(Apadrinhamento(animalId=id,ongId=orgId,usuarioId=u.id,modalidade=mode,valor=amount.setScale(2),dataInicio=start,criadoEm=now,atualizadoEm=now))
  val p=parcelas.saveAndFlush(ParcelaApadrinhamento(apadrinhamentoId=s.id!!,ongId=orgId,valor=s.valor,vencimento=start,criadoEm=now,atualizadoEm=now))
  return response(s,false,p)
 }
 fun list(u:UsuarioResponse,internal:Boolean,page:Int,size:Int,status:StatusApadrinhamento?,animalId:Long?,modalidade:ModalidadeApadrinhamento?):Page<ApadrinhamentoResponse> {
  val orgId=if(internal)scope(u)else{actor(u,false);null};if(animalId!=null&&animalId<=0)apadBad()
  var spec=Specification<Apadrinhamento>{r,_,cb->if(internal)cb.equal(r.get<Int>("ongId"),orgId)else cb.equal(r.get<Int>("usuarioId"),u.id)}
  status?.let{v->spec=spec.and{r,_,cb->cb.equal(r.get<StatusApadrinhamento>("status"),v)}}
  if(internal){animalId?.let{v->spec=spec.and{r,_,cb->cb.equal(r.get<Long>("animalId"),v)}};modalidade?.let{v->spec=spec.and{r,_,cb->cb.equal(r.get<ModalidadeApadrinhamento>("modalidade"),v)}}}
  val p=sponsors.findAll(spec,paging(page,size));return PageImpl(responses(p.content,internal),p.pageable,p.totalElements)
 }
 fun details(u:UsuarioResponse,id:Long,internal:Boolean):ApadrinhamentoResponse {val a=locked(u,id,internal);generator.ensureDue(a);return response(a,internal)}
 fun installments(u:UsuarioResponse,id:Long,internal:Boolean,page:Int,size:Int,status:StatusParcela?,vencidas:Boolean?,de:String?,ate:String?):Page<ParcelaResponse> {
  val a=locked(u,id,internal);val start=day(de);val end=day(ate);if(start!=null&&end!=null&&end<start)apadBad("Período inválido");generator.ensureDue(a)
  var spec=Specification<ParcelaApadrinhamento>{r,_,cb->cb.equal(r.get<Long>("apadrinhamentoId"),a.id)}
  status?.let{v->spec=spec.and{r,_,cb->cb.equal(r.get<StatusParcela>("status"),v)}}
  if(internal){vencidas?.let{v->spec=spec.and{r,_,cb->val pending=cb.equal(r.get<StatusParcela>("status"),StatusParcela.PENDENTE);val past=cb.lessThan(r.get<LocalDate>("vencimento"),LocalDate.now(ZoneOffset.UTC));if(v)cb.and(pending,past)else cb.not(cb.and(pending,past))}};start?.let{v->spec=spec.and{r,_,cb->cb.greaterThanOrEqualTo(r.get<LocalDate>("vencimento"),v)}};end?.let{v->spec=spec.and{r,_,cb->cb.lessThanOrEqualTo(r.get<LocalDate>("vencimento"),v)}}}
  val p=parcelas.findAll(spec,paging(page,size,true));val people=if(internal)users.findAllById(p.content.mapNotNull{it.confirmadoPor}.distinct()).associate{it.id!! to ConfirmadorParcela(it.id!!,it.nome)}else emptyMap()
  return p.map{parcelaResponse(it,people[it.confirmadoPor])}
 }
 fun close(u:UsuarioResponse,id:Long,internal:Boolean,r:EncerrarApadrinhamentoRequest?):ApadrinhamentoResponse {
  val a=locked(u,id,internal,true);if(a.status!=StatusApadrinhamento.ATIVO)apadConflict("Apadrinhamento já encerrado")
  val reason=if(internal)r?.justificativa?.trim()?.takeIf{it.isNotBlank()&&it.codePointCount(0,it.length)<=1000}?:apadBad("Informe a justificativa de encerramento, até 1000 caracteres")else null
  generator.ensureDue(a);a.status=StatusApadrinhamento.ENCERRADO;a.dataEncerramento=LocalDate.now(ZoneOffset.UTC);a.motivoEncerramento=if(internal)MotivoEncerramento.ENCERRAMENTO_ONG else MotivoEncerramento.CANCELAMENTO_USUARIO;a.justificativaEncerramento=reason;a.atualizadoEm=Instant.now();sponsors.flush();return response(a,internal)
 }
 fun confirm(u:UsuarioResponse,id:Long,partId:Long):ParcelaResponse {
  val a=locked(u,id,true,true);val p=parcelas.lockParcela(partId,a.id!!,a.ongId)?:apadMissing()
  if(p.status!=StatusParcela.PENDENTE||movements.existsByParcelaApadrinhamentoId(partId))apadConflict("Parcela indisponível para confirmação")
  if(a.status==StatusApadrinhamento.ENCERRADO&&a.dataEncerramento!=null&&p.criadoEm.atZone(ZoneOffset.UTC).toLocalDate()>a.dataEncerramento&&p.vencimento>a.dataEncerramento)apadConflict("Parcela posterior ao encerramento")
  val now=Instant.now();p.status=StatusParcela.CONFIRMADA;p.confirmadoPor=u.id;p.confirmadoEm=now;p.atualizadoEm=now;parcelas.flush()
  finance.receberParcelaApadrinhamento(u,a.ongId,p.id!!,p.valor,now)
  return parcelaResponse(p,ConfirmadorParcela(u.id,u.nome))
 }
}
