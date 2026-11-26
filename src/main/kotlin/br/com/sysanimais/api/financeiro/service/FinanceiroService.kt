package br.com.sysanimais.api.financeiro.service

import br.com.sysanimais.api.financeiro.domain.*
import br.com.sysanimais.api.financeiro.dto.*
import br.com.sysanimais.api.financeiro.repository.*
import br.com.sysanimais.api.ongs.domain.EstadoOng
import br.com.sysanimais.api.ongs.repository.OngRepository
import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import org.springframework.data.domain.*
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.*

class FinanceiroException(val status:HttpStatus,val code:String,message:String):RuntimeException(message)
private fun bad(message:String="Dados financeiros inválidos"):Nothing=throw FinanceiroException(HttpStatus.BAD_REQUEST,"FINANCEIRO_DADOS_INVALIDOS",message)
private fun denied():Nothing=throw FinanceiroException(HttpStatus.FORBIDDEN,"FINANCEIRO_ACESSO_NEGADO","Acesso financeiro não permitido")
private fun missing():Nothing=throw FinanceiroException(HttpStatus.NOT_FOUND,"FINANCEIRO_NAO_ENCONTRADO","Registro não encontrado para esta conta ou ONG")
private fun conflict():Nothing=throw FinanceiroException(HttpStatus.CONFLICT,"FINANCEIRO_ESTADO_INCOMPATIVEL","Operação incompatível com a situação atual; atualize os dados")

@Service @Transactional
class FinanceiroService(private val donations:DoacaoRepository,private val movements:MovimentacaoFinanceiraRepository,private val history:HistoricoFinanceiroRepository,private val ongs:OngRepository,private val users:UsuarioRepository) {
 private val staff=setOf(Perfil.ADMINISTRADOR,Perfil.VOLUNTARIO)
 private fun actor(u:UsuarioResponse,internal:Boolean){val c=users.lockByIdRead(u.id)?:denied();if(!c.ativo||c.credencialPendente||c.tokenVersao!=u.tokenVersao||c.perfil!=u.perfil||c.ongId!=u.ongId)denied();if(if(internal)c.perfil !in staff else c.perfil!=Perfil.USUARIO)denied()}
 private fun scope(u:UsuarioResponse,admin:Boolean=false):Int {if(u.perfil !in staff || admin&&u.perfil!=Perfil.ADMINISTRADOR)denied();val id=u.ongId?:denied();val o=ongs.lockRead(id)?:missing();if(o.situacao!=EstadoOng.ATIVA)denied();actor(u,true);return id}
 private fun page(page:Int,size:Int):PageRequest {if(page<0||size !in 1..100)bad("Paginação inválida");return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"criadoEm","id"))}
 private fun money(v:BigDecimal)=v.setScale(2).toPlainString()
 private fun amount(v:BigDecimal?):BigDecimal {if(v==null||v.signum()<=0||v.scale()>2||v>BigDecimal("9999999999.99"))bad();return v.setScale(2)}
 private data class Period(val start:Instant?,val end:Instant?)
 private fun period(de:String?,ate:String?):Period {
  fun parse(v:String?):LocalDate? {if(v==null)return null;if(!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(v))bad("Período inválido");return try{LocalDate.parse(v)}catch(_:DateTimeException){bad("Período inválido")}}
  val a=parse(de);val b=parse(ate);if(a!=null&&b!=null&&a>b)bad("Período inválido")
  return Period(a?.atStartOfDay()?.toInstant(ZoneOffset.UTC),b?.takeUnless{it==LocalDate.of(9999,12,31)}?.plusDays(1)?.atStartOfDay()?.toInstant(ZoneOffset.UTC))
 }
 private fun <T> range(p:Period):Specification<T> = Specification { root,_,cb->val predicates=mutableListOf<jakarta.persistence.criteria.Predicate>();p.start?.let{predicates+=cb.greaterThanOrEqualTo(root.get("criadoEm"),it)};p.end?.let{predicates+=cb.lessThan(root.get("criadoEm"),it)};cb.and(*predicates.toTypedArray())}
 private fun reason(r:CancelamentoFinanceiroRequest?):String? {val value=r?.justificativa?:return null;if(value.isBlank()||value.length>1000)bad("Justificativa inválida");return value.trim()}
 private fun event(d:Doacao,u:UsuarioResponse,op:String,before:String?=null,why:String?=null){history.save(HistoricoFinanceiro(doacaoId=d.id!!,atorId=u.id,operacao=op,anterior=before,posterior=d.status.name,justificativa=why))}
 private fun event(m:MovimentacaoFinanceira,u:UsuarioResponse,op:String,before:String?=null,why:String?=null){history.save(HistoricoFinanceiro(movimentacaoId=m.id!!,atorId=u.id,operacao=op,anterior=before,posterior=m.status.name,justificativa=why))}
 private fun donationResponses(rows:List<Doacao>,internal:Boolean):List<DoacaoResponse>{
  if(rows.isEmpty())return emptyList()
  val events=history.findAllByDoacaoIdInOrderByIdAsc(rows.map{it.id!!})
  val people=if(internal)users.findAllById(rows.flatMap{listOfNotNull(it.usuarioId,it.confirmadoPor,it.canceladoPor)}+events.map{it.atorId}).associateBy{it.id!!}else emptyMap()
  val orgs=ongs.findAllById(rows.map{it.ongId}.distinct()).associateBy{it.id!!};val grouped=events.groupBy{it.doacaoId}
  fun person(id:Int?)=people[id]?.let{PessoaFinanceira(it.id!!,it.nome)}
  return rows.map{d->val o=orgs[d.ongId]?:missing();DoacaoResponse(d.id!!,OngFinanceira(o.id!!,o.nome),money(d.valor),d.status,d.mensagem,d.confirmadoEm,d.canceladoEm,d.justificativaCancelamento,d.criadoEm,d.atualizadoEm,d.versao,grouped[d.id].orEmpty().map{EventoFinanceiro(it.operacao,it.anterior,it.posterior,it.justificativa,it.ocorridoEm,person(it.atorId))},person(d.usuarioId),person(d.confirmadoPor),person(d.canceladoPor))}
 }
 private fun movementResponses(rows:List<MovimentacaoFinanceira>):List<MovimentoFinanceiroResponse>{
  if(rows.isEmpty())return emptyList()
  val events=history.findAllByMovimentacaoIdInOrderByIdAsc(rows.map{it.id!!});val people=users.findAllById(rows.flatMap{listOfNotNull(it.registradoPor,it.aprovadoPor,it.confirmadoPor,it.canceladoPor)}+events.map{it.atorId}).associateBy{it.id!!};val grouped=events.groupBy{it.movimentacaoId}
  fun person(id:Int?)=people[id]?.let{PessoaFinanceira(it.id!!,it.nome)}
  return rows.map{m->MovimentoFinanceiroResponse(m.id!!,m.ongId,m.tipo,m.categoria,m.descricao,money(m.valor),m.status,OrigemFinanceira(when{m.parcelaApadrinhamentoId!=null->"APADRINHAMENTO";m.doacaoId!=null->"DOACAO";else->"MANUAL"},m.parcelaApadrinhamentoId?:m.doacaoId),person(m.registradoPor),person(m.aprovadoPor),person(m.confirmadoPor),person(m.canceladoPor),m.aprovadoEm,m.confirmadoEm,m.canceladoEm,m.justificativaCancelamento,m.criadoEm,m.atualizadoEm,m.versao,grouped[m.id].orEmpty().map{EventoFinanceiro(it.operacao,it.anterior,it.posterior,it.justificativa,it.ocorridoEm,person(it.atorId))})}
 }
 private fun donationResponse(d:Doacao,internal:Boolean):DoacaoResponse {donations.flush();return donationResponses(listOf(d),internal).single()}
 private fun movementResponse(m:MovimentacaoFinanceira):MovimentoFinanceiroResponse {movements.flush();return movementResponses(listOf(m)).single()}
 fun donate(u:UsuarioResponse,r:RegistrarDoacaoRequest):DoacaoResponse {
  if(u.perfil!=Perfil.USUARIO)denied();val o=ongs.lockRead(r.ongId?:bad())?:missing();actor(u,false);if(o.situacao!=EstadoOng.ATIVA)conflict()
  val d=donations.saveAndFlush(Doacao(ongId=o.id!!,usuarioId=u.id,valor=amount(r.valor),mensagem=r.mensagem?.trim()?.takeIf{it.isNotEmpty()}));event(d,u,"REGISTRADA");return donationResponse(d,false)
 }
 fun mine(u:UsuarioResponse,number:Int,size:Int):Page<DoacaoResponse>{actor(u,false);val spec=Specification<Doacao>{r,_,cb->cb.equal(r.get<Int>("usuarioId"),u.id)};val p=donations.findAll(spec,page(number,size));return PageImpl(donationResponses(p.content,false),p.pageable,p.totalElements)}
 fun ownDonation(u:UsuarioResponse,id:Long):DoacaoResponse {actor(u,false);return donationResponse(donations.findByIdAndUsuarioId(id,u.id)?:missing(),false)}
 fun donationList(u:UsuarioResponse,number:Int,size:Int,status:StatusDoacao?,de:String?,ate:String?):Page<DoacaoResponse>{val org=scope(u);val p=period(de,ate);var spec=Specification<Doacao>{r,_,cb->cb.equal(r.get<Int>("ongId"),org)}.and(range<Doacao>(p));status?.let{s->spec=spec.and{r,_,cb->cb.equal(r.get<StatusDoacao>("status"),s)}};val rows=donations.findAll(spec,page(number,size));return PageImpl(donationResponses(rows.content,true),rows.pageable,rows.totalElements)}
 fun donationDetails(u:UsuarioResponse,id:Long):DoacaoResponse {val org=scope(u);return donationResponse(donations.findByIdAndOngId(id,org)?:missing(),true)}
 fun confirmDonation(u:UsuarioResponse,id:Long):DoacaoResponse {
  val org=scope(u,true);val d=donations.lockOng(id,org)?:missing();if(d.status!=StatusDoacao.PENDENTE||movements.existsByDoacaoId(id))conflict()
  val now=Instant.now();d.status=StatusDoacao.CONFIRMADA;d.confirmadoPor=u.id;d.confirmadoEm=now;d.atualizadoEm=now;event(d,u,"RECEBIMENTO_CONFIRMADO","PENDENTE")
  val m=movements.saveAndFlush(MovimentacaoFinanceira(ongId=org,tipo=TipoMovimento.RECEITA,categoria=CategoriaFinanceira.DOACAO,descricao="Doação recebida",valor=d.valor,status=StatusMovimento.REALIZADA,doacaoId=d.id,registradoPor=u.id,confirmadoPor=u.id,confirmadoEm=now,criadoEm=now,atualizadoEm=now));event(m,u,"RECEBIMENTO_DOACAO");return donationResponse(d,true)
 }
 fun cancelDonation(u:UsuarioResponse,id:Long,internal:Boolean,r:CancelamentoFinanceiroRequest?):DoacaoResponse {
  val d=if(internal){val org=scope(u,true);donations.lockOng(id,org)?:missing()}else{actor(u,false);donations.lockOwn(id,u.id)?:missing()}
  if(d.status!=StatusDoacao.PENDENTE||movements.existsByDoacaoId(id))conflict();val why=reason(r);d.status=StatusDoacao.CANCELADA;d.canceladoPor=u.id;d.canceladoEm=Instant.now();d.atualizadoEm=d.canceladoEm!!;d.justificativaCancelamento=why;event(d,u,"CANCELADA","PENDENTE",why);return donationResponse(d,internal)
 }
 fun create(u:UsuarioResponse,r:RegistrarMovimentoRequest):MovimentoFinanceiroResponse {
  val org=scope(u);val type=r.tipo?:bad();val category=r.categoria?:bad();if(!category.manualPara(type))bad("Categoria incompatível com o tipo ou reservada para origem automática")
  val m=movements.saveAndFlush(MovimentacaoFinanceira(ongId=org,tipo=type,categoria=category,descricao=r.descricao?.trim()?.takeIf{it.isNotBlank()&&it.length<=255}?:bad(),valor=amount(r.valor),registradoPor=u.id));event(m,u,"REGISTRADA");return movementResponse(m)
 }
 fun list(u:UsuarioResponse,number:Int,size:Int,tipo:TipoMovimento?,status:StatusMovimento?,categoria:CategoriaFinanceira?,de:String?,ate:String?):Page<MovimentoFinanceiroResponse>{val org=scope(u);val p=period(de,ate);var spec=Specification<MovimentacaoFinanceira>{r,_,cb->cb.equal(r.get<Int>("ongId"),org)}.and(range<MovimentacaoFinanceira>(p));tipo?.let{v->spec=spec.and{r,_,cb->cb.equal(r.get<TipoMovimento>("tipo"),v)}};status?.let{v->spec=spec.and{r,_,cb->cb.equal(r.get<StatusMovimento>("status"),v)}};categoria?.let{v->spec=spec.and{r,_,cb->cb.equal(r.get<CategoriaFinanceira>("categoria"),v)}};val rows=movements.findAll(spec,page(number,size));return PageImpl(movementResponses(rows.content),rows.pageable,rows.totalElements)}
 fun details(u:UsuarioResponse,id:Long):MovimentoFinanceiroResponse {val org=scope(u);return movementResponse(movements.findByIdAndOngId(id,org)?:missing())}
 fun change(u:UsuarioResponse,id:Long,action:String,r:CancelamentoFinanceiroRequest?=null):MovimentoFinanceiroResponse {
  val org=scope(u,true);val m=movements.lockOng(id,org)?:missing();if(m.doacaoId!=null||m.parcelaApadrinhamentoId!=null)conflict();val old=m.status;val now=Instant.now()
  when(action){
   "aprovar"->{if(m.tipo!=TipoMovimento.DESPESA||old!=StatusMovimento.PENDENTE)conflict();m.status=StatusMovimento.APROVADA;m.aprovadoPor=u.id;m.aprovadoEm=now}
   "confirmar-pagamento"->{if(m.tipo!=TipoMovimento.DESPESA||old!=StatusMovimento.APROVADA)conflict();m.status=StatusMovimento.REALIZADA;m.confirmadoPor=u.id;m.confirmadoEm=now}
   "confirmar-recebimento"->{if(m.tipo!=TipoMovimento.RECEITA||old!=StatusMovimento.PENDENTE)conflict();m.status=StatusMovimento.REALIZADA;m.confirmadoPor=u.id;m.confirmadoEm=now}
   "cancelar"->{if(old !in setOf(StatusMovimento.PENDENTE,StatusMovimento.APROVADA))conflict();m.status=StatusMovimento.CANCELADA;m.canceladoPor=u.id;m.canceladoEm=now;m.justificativaCancelamento=reason(r)}
   else->bad()
  }
  m.atualizadoEm=now;event(m,u,when(action){"aprovar"->"DESPESA_APROVADA";"confirmar-pagamento"->"PAGAMENTO_CONFIRMADO";"confirmar-recebimento"->"RECEBIMENTO_CONFIRMADO";else->"CANCELADA"},old.name,m.justificativaCancelamento)
  return movementResponse(m)
 }
 fun summary(u:UsuarioResponse,de:String?,ate:String?):ResumoFinanceiroResponse {val org=scope(u);val p=period(de,ate);val t=movements.resumo(org,p.start,p.end);return ResumoFinanceiroResponse(money(t.receitasRealizadas),money(t.despesasRealizadas),money(t.receitasPendentes),money(t.despesasPendentes),money(t.despesasAprovadas),money(t.receitasRealizadas.subtract(t.despesasRealizadas)))}
 // Reuses the single financial ledger and its existing event/receipt rules.
 fun receberParcelaApadrinhamento(u:UsuarioResponse,ongId:Int,parcelaId:Long,valor:BigDecimal,recebidoEm:Instant):Long {
  if(scope(u,true)!=ongId)missing()
  if(movements.existsByParcelaApadrinhamentoId(parcelaId))conflict()
  val m=movements.saveAndFlush(MovimentacaoFinanceira(ongId=ongId,tipo=TipoMovimento.RECEITA,categoria=CategoriaFinanceira.APADRINHAMENTO,descricao="Parcela de apadrinhamento recebida",valor=amount(valor),status=StatusMovimento.REALIZADA,parcelaApadrinhamentoId=parcelaId,registradoPor=u.id,confirmadoPor=u.id,confirmadoEm=recebidoEm,criadoEm=recebidoEm,atualizadoEm=recebidoEm))
  event(m,u,"RECEBIMENTO_APADRINHAMENTO");return m.id!!
 }

}
