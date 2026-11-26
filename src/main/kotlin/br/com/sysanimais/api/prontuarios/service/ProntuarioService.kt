package br.com.sysanimais.api.prontuarios.service

import br.com.sysanimais.api.animais.domain.*
import br.com.sysanimais.api.animais.repository.AnimalRepository
import br.com.sysanimais.api.ongs.domain.EstadoOng
import br.com.sysanimais.api.ongs.repository.OngRepository
import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import br.com.sysanimais.api.prontuarios.domain.*
import br.com.sysanimais.api.prontuarios.dto.*
import br.com.sysanimais.api.prontuarios.repository.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.*
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.*

class ProntuarioException(val status:HttpStatus,val code:String,message:String):RuntimeException(message)
private fun bad(message:String="Confira os campos clínicos e os filtros informados"):Nothing=throw ProntuarioException(HttpStatus.BAD_REQUEST,"PRONTUARIO_DADOS_INVALIDOS",message)
private fun denied():Nothing=throw ProntuarioException(HttpStatus.FORBIDDEN,"PRONTUARIO_ACESSO_NEGADO","Consulta ou operação não autorizada")
private fun missing():Nothing=throw ProntuarioException(HttpStatus.NOT_FOUND,"PRONTUARIO_NAO_ENCONTRADO","Animal ou registro não encontrado para esta conta")
private fun conflict(message:String):Nothing=throw ProntuarioException(HttpStatus.CONFLICT,"PRONTUARIO_CONFLITO",message)

@Service @Transactional
class ProntuarioService(
 private val records:ProntuarioRepository,private val corrections:CorrecaoProntuarioRepository,
 private val animals:AnimalRepository,private val ongs:OngRepository,private val users:UsuarioRepository,
 private val json:ObjectMapper
) {
 private val readers=setOf(Perfil.ADMINISTRADOR,Perfil.VETERINARIO,Perfil.VOLUNTARIO)
 private val writers=setOf(Perfil.ADMINISTRADOR,Perfil.VETERINARIO)
 private val clinical=setOf("tipo","titulo","descricao","dataProcedimento","profissionalResponsavel","medicamentos","observacoes")
 private fun actor(u:UsuarioResponse) {
  val c=users.lockByIdRead(u.id)?:denied()
  if(!c.ativo||c.credencialPendente||c.tokenVersao!=u.tokenVersao||c.perfil!=u.perfil||c.ongId!=u.ongId)denied()
 }
 private fun scope(u:UsuarioResponse,write:Boolean=false):Int {
  if(u.perfil !in (if(write)writers else readers))denied()
  val id=u.ongId?:denied();val o=ongs.lockRead(id)?:missing()
  if(o.situacao!=EstadoOng.ATIVA)denied()
  actor(u);return id
 }
 private fun animal(u:UsuarioResponse,id:Long,write:Boolean=false):Animal {
  val org=scope(u,write)
  // Same lock order as animal/adoption writes: ONG, actor, animal, clinical record.
  return (if(write)animals.lockByIdAndOngId(id,org) else animals.findByIdAndOngId(id,org))?:missing()
 }
 private fun adoptedAnimal(u:UsuarioResponse,id:Long):Animal {
  if(u.perfil!=Perfil.USUARIO)denied();actor(u)
  val a=animals.findById(id).orElse(null)?:missing()
  if(!records.adotadoPor(id,u.id,a.ongId))missing()
  return a // Past adoption remains readable even if the ONG is later suspended.
 }
 private fun page(number:Int,size:Int,history:Boolean=false):PageRequest {
  if(number<0||size !in 1..100||number.toLong()*size>Int.MAX_VALUE)bad("Paginação inválida")
  return PageRequest.of(number,size,if(history)Sort.by("criadoEm","id")else Sort.by(Sort.Direction.DESC,"dataProcedimento","id"))
 }
 private fun date(s:String):LocalDate = try {
  if(!s.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))bad()
  LocalDate.parse(s).also {if(it.year !in 1000..9999)bad()}
 }catch(_:java.time.format.DateTimeParseException){bad()}
 private fun text(n:JsonNode?,max:Int,required:Boolean=false):String? {
  if(n==null||n.isNull){if(required)bad();return null}
  if(!n.isTextual)bad()
  val value=n.textValue().trim()
  if(value.codePointCount(0,value.length)>max||required&&value.isEmpty())bad()
  return value.takeIf{it.isNotEmpty()}
 }
 internal fun validateClinicalData(fields:Map<String,JsonNode>):DadosClinicos {
  val type=try{TipoProntuario.valueOf(text(fields["tipo"],20,true)!!)}catch(_:IllegalArgumentException){bad()}
  val day=date(text(fields["dataProcedimento"],10,true)!!)
  if(day.isAfter(LocalDate.now()))bad("A data do procedimento não pode estar no futuro")
  return DadosClinicos(type,text(fields["titulo"],120,true)!!,text(fields["descricao"],10000,true)!!,day,
   text(fields["profissionalResponsavel"],120),text(fields["medicamentos"],5000),text(fields["observacoes"],5000))
 }
 private fun snapshot(p:Prontuario)=DadosClinicos(p.tipo,p.titulo,p.descricao,p.dataProcedimento,p.profissionalResponsavel,p.medicamentos,p.observacoes)
 private fun apply(p:Prontuario,d:DadosClinicos) {
  p.tipo=d.tipo;p.titulo=d.titulo;p.descricao=d.descricao;p.dataProcedimento=d.dataProcedimento
  p.profissionalResponsavel=d.profissionalResponsavel;p.medicamentos=d.medicamentos;p.observacoes=d.observacoes
 }
 private fun record(a:Animal,id:Long,lock:Boolean=false)=
  (if(lock)records.lockRecord(id,a.id!!,a.ongId)else records.findByIdAndAnimalIdAndOngId(id,a.id!!,a.ongId))?:missing()
 private fun authors(rows:List<Prontuario>)=users.findAllById(rows.map{it.registradoPor}.distinct()).associate{it.id!! to PessoaProntuario(it.id!!,it.nome)}
 private fun response(p:Prontuario,a:Animal,people:Map<Int,PessoaProntuario>)=ProntuarioResponse(p.id!!,AnimalProntuario(a.id!!,a.nome),p.tipo,p.titulo,p.descricao,p.dataProcedimento,p.profissionalResponsavel,p.medicamentos,p.observacoes,people[p.registradoPor]?:missing(),p.criadoEm,p.atualizadoEm,p.versao)
 private fun response(p:Prontuario,a:Animal)=response(p,a,authors(listOf(p)))
 private fun adopterResponse(p:Prontuario,a:Animal)=ProntuarioAdotanteResponse(p.id!!,AnimalProntuario(a.id!!,a.nome),p.tipo,p.titulo,p.descricao,p.dataProcedimento,p.profissionalResponsavel,p.medicamentos,p.observacoes)
 private fun rows(a:Animal,number:Int,size:Int,tipo:TipoProntuario?,de:String?,ate:String?):Page<Prontuario> {
  val start=de?.let(::date);val end=ate?.let(::date)
  if(start!=null&&end!=null&&end<start)bad("O fim do período deve ser posterior ou igual ao início")
  var spec=Specification<Prontuario>{r,_,cb->cb.and(cb.equal(r.get<Long>("animalId"),a.id),cb.equal(r.get<Int>("ongId"),a.ongId))}
  tipo?.let{v->spec=spec.and{r,_,cb->cb.equal(r.get<TipoProntuario>("tipo"),v)}}
  start?.let{v->spec=spec.and{r,_,cb->cb.greaterThanOrEqualTo(r.get<LocalDate>("dataProcedimento"),v)}}
  end?.let{v->spec=spec.and{r,_,cb->cb.lessThanOrEqualTo(r.get<LocalDate>("dataProcedimento"),v)}}
  return records.findAll(spec,page(number,size))
 }
 fun create(u:UsuarioResponse,animalId:Long,r:ProntuarioRequest):ProntuarioResponse {
  val a=animal(u,animalId,true)
  if(a.situacao in setOf(SituacaoAnimal.ADOTADO,SituacaoAnimal.FALECIDO))conflict("Animal não pode receber novos procedimentos clínicos")
  if(r.campos.keys.any{it !in clinical})bad("Campo não permitido no registro clínico")
  val d=validateClinicalData(r.campos);val now=Instant.now()
  val p=Prontuario(animalId=a.id!!,ongId=a.ongId,registradoPor=u.id,criadoEm=now,atualizadoEm=now)
  apply(p,d);return response(records.saveAndFlush(p),a)
 }
 fun list(u:UsuarioResponse,animalId:Long,number:Int,size:Int,tipo:TipoProntuario?,de:String?,ate:String?):Page<ProntuarioResponse> {
  val a=animal(u,animalId);val p=rows(a,number,size,tipo,de,ate);val people=authors(p.content)
  return p.map{response(it,a,people)}
 }
 fun details(u:UsuarioResponse,animalId:Long,id:Long):ProntuarioResponse {val a=animal(u,animalId);return response(record(a,id),a)}
 fun correct(u:UsuarioResponse,animalId:Long,id:Long,r:ProntuarioRequest):ProntuarioResponse {
  val a=animal(u,animalId,true);val p=record(a,id,true)
  if(r.campos.keys.any{it !in clinical&&it !in setOf("justificativa","versao")})bad("Campo não permitido na correção")
  val reason=text(r.campos["justificativa"],2000,true)!!
  val version=r.campos["versao"]?:bad("Informe a versão atual do registro")
  if(!version.isIntegralNumber||!version.canConvertToLong()||version.longValue()<0)bad("Versão inválida")
  if(version.longValue()!=p.versao)conflict("O registro mudou; consulte a versão atual antes de corrigir")
  val changes=r.campos.filterKeys{it in clinical};if(changes.isEmpty())bad("Informe ao menos um campo clínico")
  val before=snapshot(p)
  val merged=json.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(before)
  changes.forEach{(key,value)->merged.set<JsonNode>(key,value)}
  val after=validateClinicalData(merged.properties().associate{it.key to it.value})
  if(before==after)bad("Nenhuma informação clínica foi alterada")
  val oldVersion=p.versao;val now=Instant.now();apply(p,after);p.atualizadoEm=now
  records.flush()
  corrections.saveAndFlush(CorrecaoProntuario(prontuarioId=p.id!!,corrigidoPor=u.id,justificativa=reason,
   dadosAnteriores=json.writeValueAsString(before),dadosNovos=json.writeValueAsString(after),versaoAnterior=oldVersion,versaoPosterior=p.versao,criadoEm=now))
  return response(p,a)
 }
 fun history(u:UsuarioResponse,animalId:Long,id:Long,number:Int,size:Int):Page<CorrecaoProntuarioResponse> {
  val a=animal(u,animalId);record(a,id)
  val p=corrections.findAllByProntuarioId(id,page(number,size,true))
  val people=users.findAllById(p.content.map{it.corrigidoPor}.distinct()).associate{it.id!! to PessoaProntuario(it.id!!,it.nome)}
  return p.map{CorrecaoProntuarioResponse(it.id!!,it.criadoEm,it.justificativa,people[it.corrigidoPor]?:missing(),json.readValue(it.dadosAnteriores,DadosClinicos::class.java),json.readValue(it.dadosNovos,DadosClinicos::class.java),it.versaoAnterior,it.versaoPosterior)}
 }
 fun adoptedList(u:UsuarioResponse,animalId:Long,number:Int,size:Int,tipo:TipoProntuario?,de:String?,ate:String?):Page<ProntuarioAdotanteResponse> {
  val a=adoptedAnimal(u,animalId);return rows(a,number,size,tipo,de,ate).map{adopterResponse(it,a)}
 }
 fun adoptedDetails(u:UsuarioResponse,animalId:Long,id:Long):ProntuarioAdotanteResponse {val a=adoptedAnimal(u,animalId);return adopterResponse(record(a,id),a)}
}
