package br.com.patasunidas.prontuarios

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ClinicalType(val label:String) { CONSULTA("Consulta"),VACINA("Vacina"),VERMIFUGACAO("Vermifugação"),EXAME("Exame"),CIRURGIA("Cirurgia"),MEDICACAO("Medicação"),TRATAMENTO("Tratamento"),OBSERVACAO("Observação") }
internal fun codePoints(value:String)=value.codePointCount(0,value.length)
internal fun validClinicalDate(value:String):Boolean {
 if(!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)||value.take(4).toInt() !in 1000..9999)return false
 return try{val f=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply{isLenient=false};f.parse(value)!=null}catch(_:Exception){false}
}
data class ClinicalForm(val tipo:ClinicalType=ClinicalType.CONSULTA,val titulo:String="",val descricao:String="",val dataProcedimento:String="",val profissionalResponsavel:String="",val medicamentos:String="",val observacoes:String="") {
 fun normalized()=copy(titulo=titulo.trim(),descricao=descricao.trim(),dataProcedimento=dataProcedimento.trim(),profissionalResponsavel=profissionalResponsavel.trim(),medicamentos=medicamentos.trim(),observacoes=observacoes.trim())
 fun validate():String? {val f=normalized()
  if(f.titulo.isEmpty()||codePoints(f.titulo)>120)return "Informe o título (até 120 caracteres)."
  if(f.descricao.isEmpty()||codePoints(f.descricao)>10000)return "Informe a descrição (até 10.000 caracteres)."
  if(!validClinicalDate(f.dataProcedimento))return "Informe uma data válida (AAAA-MM-DD), entre 1000 e 9999."
  if(f.dataProcedimento>SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(Date()))return "A data do procedimento não pode ser futura."
  if(codePoints(f.profissionalResponsavel)>120)return "Profissional responsável: até 120 caracteres."
  if(codePoints(f.medicamentos)>5000||codePoints(f.observacoes)>5000)return "Medicamentos e observações: até 5.000 caracteres cada."
  return null
 }
 internal fun fields():Map<String,Any?> {val f=normalized();return linkedMapOf("tipo" to f.tipo.name,"titulo" to f.titulo,"descricao" to f.descricao,"dataProcedimento" to f.dataProcedimento,"profissionalResponsavel" to f.profissionalResponsavel.ifEmpty{null},"medicamentos" to f.medicamentos.ifEmpty{null},"observacoes" to f.observacoes.ifEmpty{null})}
}
data class PessoaClinica(val id:Int,val nome:String)
data class InstitutionalRecord(val id:Long,val animalId:Long,val animalName:String,val data:ClinicalForm,val registradoPor:PessoaClinica,val criadoEm:String,val atualizadoEm:String,val versao:Long)
data class PersonalRecord(val id:Long,val animalId:Long,val animalName:String,val data:ClinicalForm)
data class CorrectionRecord(val id:Long,val criadoEm:String,val justificativa:String,val corrigidoPor:PessoaClinica,val dadosAnteriores:ClinicalForm,val dadosNovos:ClinicalForm,val versaoAnterior:Long,val versaoPosterior:Long)
data class MedicalPage<T>(val items:List<T>,val page:Int,val size:Int,val total:Long,val pages:Int)
class ProntuarioHttpException(val status:Int,val code:String?=null):IOException()
/** Apply only the user's original changes to the latest record, for explicit review and confirmation. */
fun rebaseClinicalDraft(original:ClinicalForm,draft:ClinicalForm,remote:ClinicalForm):ClinicalForm {
 val o=original.normalized();val d=draft.normalized();val r=remote.normalized()
 return r.copy(tipo=if(d.tipo!=o.tipo)d.tipo else r.tipo,titulo=if(d.titulo!=o.titulo)d.titulo else r.titulo,descricao=if(d.descricao!=o.descricao)d.descricao else r.descricao,dataProcedimento=if(d.dataProcedimento!=o.dataProcedimento)d.dataProcedimento else r.dataProcedimento,profissionalResponsavel=if(d.profissionalResponsavel!=o.profissionalResponsavel)d.profissionalResponsavel else r.profissionalResponsavel,medicamentos=if(d.medicamentos!=o.medicamentos)d.medicamentos else r.medicamentos,observacoes=if(d.observacoes!=o.observacoes)d.observacoes else r.observacoes)
}
fun rebaseClinicalDraft(original:InstitutionalRecord,draft:ClinicalForm,remote:InstitutionalRecord)=rebaseClinicalDraft(original.data,draft,remote.data)

class ProntuarioApi(baseUrl:String) {
 private val base=baseUrl.trimEnd('/')
 private val client=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
 private fun request(token:String,path:String)=Request.Builder().url(base+path).header("Authorization","Bearer $token")
 private fun body(values:Map<String,Any?>)=JSONObject().apply{values.forEach{(k,v)->put(k,v?:JSONObject.NULL)}}.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
 private suspend fun json(request:Request):JSONObject=suspendCancellableCoroutine{c->val call=client.newCall(request);c.invokeOnCancellation{call.cancel()};call.enqueue(object:Callback {
  override fun onFailure(call:Call,e:IOException){if(c.isActive)c.resumeWithException(e)}
  override fun onResponse(call:Call,response:Response){try{val value=response.use{r->val bytes=r.peekBody(32L*1024*1024+1).bytes();if(bytes.size>32*1024*1024)throw IOException("Resposta muito grande");val text=bytes.toString(Charsets.UTF_8);if(!r.isSuccessful)throw ProntuarioHttpException(r.code,try{JSONObject(text).optString("code").takeIf{it.isNotBlank()}}catch(_:Exception){null});JSONObject(text)};if(c.isActive)c.resume(value)}catch(e:Exception){if(c.isActive)c.resumeWithException(e)}}
 })}
 private fun path(animalId:Long,personal:Boolean=false):String {require(animalId>0);return if(personal)"/api/me/animais-adotados/$animalId/prontuarios" else "/api/ong/animais/$animalId/prontuarios"}
 private fun id(id:Long):Long{require(id>0);return id}
 private suspend fun get(token:String,path:String,params:Map<String,String?> = emptyMap()):JSONObject{val url=(base+path).toHttpUrl().newBuilder();params.forEach{(k,v)->if(!v.isNullOrEmpty())url.addQueryParameter(k,v)};return json(Request.Builder().url(url.build()).header("Authorization","Bearer $token").get().build())}
 private fun pagination(page:Int,size:Int):Map<String,String?>{require(page>=0&&size in 1..100&&page.toLong()*size<=Int.MAX_VALUE);return linkedMapOf("page" to page.toString(),"size" to size.toString())}
 private fun filters(page:Int,size:Int,type:ClinicalType?,de:String,ate:String):Map<String,String?>{require((de.isEmpty()||validClinicalDate(de))&&(ate.isEmpty()||validClinicalDate(ate))&&(de.isEmpty()||ate.isEmpty()||ate>=de));return pagination(page,size)+linkedMapOf("tipo" to type?.name,"de" to de,"ate" to ate)}
 private fun clinical(j:JSONObject)=ClinicalForm(ClinicalType.valueOf(j.getString("tipo")),j.getString("titulo"),j.getString("descricao"),j.getString("dataProcedimento"),optional(j,"profissionalResponsavel"),optional(j,"medicamentos"),optional(j,"observacoes"))
 private fun optional(j:JSONObject,key:String)=if(j.isNull(key))"" else j.getString(key)
 private fun person(j:JSONObject)=PessoaClinica(j.getInt("id"),j.getString("nome"))
 private fun institutional(j:JSONObject):InstitutionalRecord{val a=j.getJSONObject("animal");return InstitutionalRecord(j.getLong("id"),a.getLong("id"),a.getString("nome"),clinical(j),person(j.getJSONObject("registradoPor")),j.getString("criadoEm"),j.getString("atualizadoEm"),j.getLong("versao"))}
 private fun personal(j:JSONObject):PersonalRecord{val a=j.getJSONObject("animal");return PersonalRecord(j.getLong("id"),a.getLong("id"),a.getString("nome"),clinical(j))}
 private fun correction(j:JSONObject)=CorrectionRecord(j.getLong("id"),j.getString("criadoEm"),j.getString("justificativa"),person(j.getJSONObject("corrigidoPor")),clinical(j.getJSONObject("dadosAnteriores")),clinical(j.getJSONObject("dadosNovos")),j.getLong("versaoAnterior"),j.getLong("versaoPosterior"))
 private fun <T> page(j:JSONObject,parse:(JSONObject)->T):MedicalPage<T>{val a=j.getJSONArray("content");return MedicalPage((0 until a.length()).map{parse(a.getJSONObject(it))},j.getInt("number"),j.getInt("size"),j.getLong("totalElements"),j.getInt("totalPages"))}
 suspend fun listInstitutional(token:String,animalId:Long,page:Int=0,type:ClinicalType?=null,de:String="",ate:String="",size:Int=15)=page(get(token,path(animalId),filters(page,size,type,de,ate)),::institutional)
 suspend fun institutionalDetails(token:String,animalId:Long,id:Long)=institutional(get(token,"${path(animalId)}/${id(id)}"))
 suspend fun listPersonal(token:String,animalId:Long,page:Int=0,type:ClinicalType?=null,de:String="",ate:String="",size:Int=15)=page(get(token,path(animalId,true),filters(page,size,type,de,ate)),::personal)
 suspend fun personalDetails(token:String,animalId:Long,id:Long)=personal(get(token,"${path(animalId,true)}/${id(id)}"))
 suspend fun create(token:String,animalId:Long,form:ClinicalForm):InstitutionalRecord{require(form.validate()==null){form.validate()?:"Dados inválidos"};return institutional(json(request(token,path(animalId)).post(body(form.fields())).build()))}
 suspend fun correct(token:String,animalId:Long,original:InstitutionalRecord,draft:ClinicalForm,reason:String):InstitutionalRecord {
  require(original.animalId==animalId&&original.versao>=0);require(draft.validate()==null){draft.validate()?:"Dados inválidos"};require(reason.trim().isNotEmpty()&&codePoints(reason.trim())<=2000){"Informe a justificativa (até 2.000 caracteres)."}
  val before=original.data.fields();val changes=draft.fields().filter{(k,v)->v!=before[k]};require(changes.isNotEmpty()){"Altere ao menos um campo clínico antes de salvar."}
  return institutional(json(request(token,"${path(animalId)}/${id(original.id)}/corrigir").patch(body(changes+mapOf("justificativa" to reason.trim(),"versao" to original.versao))).build()))
 }
 suspend fun corrections(token:String,animalId:Long,id:Long,page:Int=0,size:Int=15)=page(get(token,"${path(animalId)}/${id(id)}/correcoes",pagination(page,size)),::correction)
}
