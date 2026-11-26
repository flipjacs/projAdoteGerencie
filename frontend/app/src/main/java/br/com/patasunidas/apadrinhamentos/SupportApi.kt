package br.com.patasunidas.apadrinhamentos

import br.com.patasunidas.financeiro.normalizeMoney
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SupportHttpException(val status:Int,val code:String?=null):IOException()
class SupportApi(baseUrl:String){
 private val base=baseUrl.trimEnd('/')
 private val client=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
 // Only reads may recover a stale pooled connection; uncertain writes are never retried.
 private val readClient=client.newBuilder().retryOnConnectionFailure(true).build()
 private fun body(j:JSONObject)=j.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
 private fun request(path:String,token:String?=null)=Request.Builder().url(base+path).apply{if(token!=null)header("Authorization","Bearer $token")}
 private suspend fun json(request:Request):JSONObject=suspendCancellableCoroutine{c->val call=(if(request.method=="GET")readClient else client).newCall(request);c.invokeOnCancellation{call.cancel()};call.enqueue(object:Callback{
  override fun onFailure(call:Call,e:IOException){if(c.isActive)c.resumeWithException(e)}
  override fun onResponse(call:Call,response:Response){try{val result=response.use{r->val bytes=r.peekBody(1024L*1024+1).bytes();if(bytes.size>1024*1024)throw IOException("Resposta muito grande");val text=bytes.toString(Charsets.UTF_8);if(!r.isSuccessful)throw SupportHttpException(r.code,runCatching{JSONObject(text).optString("code").takeIf{it.isNotBlank()}}.getOrNull());JSONObject(text)};if(c.isActive)c.resume(result)}catch(e:Exception){if(c.isActive)c.resumeWithException(e)}}
 })}
 private suspend fun get(path:String,token:String?=null,params:Map<String,String?> = emptyMap()):JSONObject{val url=(base+path).toHttpUrl().newBuilder();params.forEach{(k,v)->if(!v.isNullOrEmpty())url.addQueryParameter(k,v)};return json(request(path,token).url(url.build()).get().build())}
 private suspend fun patch(path:String,token:String,j:JSONObject=JSONObject())=json(request(path,token).patch(body(j)).build())
 private fun id(id:Long):Long{require(id>0);return id}
 private fun paging(page:Int,size:Int):Map<String,String?>{require(page>=0&&size in 1..100&&page.toLong()*size<=Int.MAX_VALUE);return linkedMapOf("page" to page.toString(),"size" to size.toString())}
 private fun period(de:String,ate:String):Map<String,String?>{validateSupportPeriod(de,ate);return linkedMapOf("de" to de,"ate" to ate)}
 private fun optional(j:JSONObject,k:String)=if(j.isNull(k))null else j.getString(k)
 private fun entity(j:JSONObject)=SupportEntity(j.getLong("id"),j.getString("nome"))
 private fun person(j:JSONObject)=SupportPerson(j.getInt("id"),j.getString("nome"),optional(j,"email"))
 private fun money(j:JSONObject,k:String):String{val v=j.get(k);require(v is String&&Regex("-?(?:0|[1-9][0-9]*)\\.[0-9]{2}").matches(v)){"Valor monetário inválido na resposta"};v.toBigDecimal();return v}
 private fun categories(j:JSONObject,key:String):Map<String,String>{val c=j.getJSONObject(key);return c.keys().asSequence().associateWith{money(c,it)}}
 private fun installment(j:JSONObject)=InstallmentRecord(j.getLong("id"),j.getInt("numero"),money(j,"valor"),j.getString("vencimento"),InstallmentStatus.valueOf(j.getString("status")),j.getBoolean("vencida"),optional(j,"confirmadoEm"),j.optJSONObject("confirmadoPor")?.let(::person))
 private fun sponsor(j:JSONObject)=SponsorRecord(j.getLong("id"),entity(j.getJSONObject("animal")),entity(j.getJSONObject("ong")),SponsorMode.valueOf(j.getString("modalidade")),money(j,"valor"),SponsorStatus.valueOf(j.getString("status")),j.getString("dataInicio"),optional(j,"dataEncerramento"),optional(j,"motivoEncerramento")?.let(SponsorClosureReason::valueOf),optional(j,"justificativaEncerramento"),optional(j,"proximoVencimento"),j.getString("criadoEm"),j.getString("atualizadoEm"),j.getLong("versao"),j.optJSONObject("primeiraParcela")?.let(::installment),j.optJSONObject("padrinho")?.let(::person))
 private fun data(j:JSONObject)=PublicTransparencyData(money(j,"receitasRealizadas"),money(j,"despesasRealizadas"),money(j,"resultadoLiquido"),categories(j,"receitasPorCategoria"),categories(j,"despesasPorCategoria"),j.getLong("quantidadeMovimentacoes"))
 private fun public(j:JSONObject)=PublicDemonstrativeRecord(j.getLong("id"),entity(j.getJSONObject("ong")),j.getString("titulo"),optional(j,"descricao"),j.getString("periodoInicio"),j.getString("periodoFim"),optional(j,"publicadoEm"),data(j.getJSONObject("dados")))
 private fun demonstrative(j:JSONObject):DemonstrativeRecord{val p=public(j);return DemonstrativeRecord(p.id,p.ong,p.title,p.description,p.startDate,p.endDate,p.publishedAt,p.data,DemonstrativeStatus.valueOf(j.getString("status")),if(j.isNull("publicadoPor"))null else j.getInt("publicadoPor"),optional(j,"despublicadoEm"),j.getString("createdAt"),j.getString("updatedAt"))}
 private fun <T> page(j:JSONObject,parse:(JSONObject)->T):SupportPage<T>{val a=j.getJSONArray("content");return SupportPage((0 until a.length()).map{parse(a.getJSONObject(it))},j.getInt("number"),j.getInt("size"),j.getLong("totalElements"),j.getInt("totalPages"))}
 private fun prefix(internal:Boolean)="/api/${if(internal)"ong" else "me"}/sponsorships"
 suspend fun createSponsor(token:String,animalId:Long,mode:SponsorMode,value:String):SponsorRecord{require(animalId>0);return sponsor(json(request("/api/sponsorships",token).post(body(JSONObject().put("animalId",animalId).put("modalidade",mode.name).put("valor",normalizeMoney(value)))).build()))}
 suspend fun sponsors(token:String,internal:Boolean,page:Int=0,status:SponsorStatus?=null,animalId:Long?=null,mode:SponsorMode?=null,size:Int=15):SupportPage<SponsorRecord>{require(animalId==null||animalId>0);return page(get(prefix(internal),token,paging(page,size)+mapOf("status" to status?.name)+(if(internal)linkedMapOf("animalId" to animalId?.toString(),"modalidade" to mode?.name)else emptyMap())),::sponsor)}
 suspend fun sponsorDetails(token:String,internal:Boolean,id:Long)=sponsor(get("${prefix(internal)}/${id(id)}",token))
 suspend fun installments(token:String,internal:Boolean,id:Long,page:Int=0,status:InstallmentStatus?=null,overdue:Boolean?=null,de:String="",ate:String="",size:Int=15):SupportPage<InstallmentRecord>{val filters=if(internal)mapOf("vencidas" to overdue?.toString())+period(de,ate)else emptyMap();return page(get("${prefix(internal)}/${id(id)}/parcelas",token,paging(page,size)+mapOf("status" to status?.name)+filters),::installment)}
 suspend fun cancelSponsor(token:String,id:Long)=sponsor(patch("/api/me/sponsorships/${id(id)}/cancelar",token))
 suspend fun closeSponsor(token:String,id:Long,reason:String):SponsorRecord{val r=reason.trim();require(r.isNotEmpty()&&r.codePointCount(0,r.length)<=1000){"Informe a justificativa (até 1.000 caracteres)."};return sponsor(patch("/api/ong/sponsorships/${id(id)}/encerrar",token,JSONObject().put("justificativa",r)))}
 suspend fun confirmInstallment(token:String,id:Long,installmentId:Long)=installment(patch("/api/ong/sponsorships/${id(id)}/parcelas/${id(installmentId)}/confirmar",token))
 suspend fun report(token:String,de:String="",ate:String=""):FinancialReport{val j=get("/api/ong/relatorios/financeiro",token,period(de,ate));val p=j.getJSONObject("periodo");return FinancialReport(optional(p,"de"),optional(p,"ate"),j.getString("semanticaPeriodo"),money(j,"receitasRealizadas"),money(j,"despesasRealizadas"),money(j,"receitasPendentes"),money(j,"despesasPendentes"),money(j,"despesasAprovadas"),money(j,"saldoRealizado"),j.getLong("quantidadeMovimentacoes"),categories(j,"receitasPorCategoria"),categories(j,"despesasPorCategoria"))}
 private fun form(f:DemonstrativeForm):JSONObject{require(f.validate()==null){f.validate()?:"Confira os campos."};return JSONObject().put("titulo",f.title.trim()).put("descricao",f.description.trim().ifEmpty{null}?:JSONObject.NULL).put("periodoInicio",f.startDate).put("periodoFim",f.endDate)}
 suspend fun createDemonstrative(token:String,form:DemonstrativeForm)=demonstrative(json(request("/api/ong/transparencia",token).post(body(form(form))).build()))
 suspend fun demonstratives(token:String,page:Int=0,status:DemonstrativeStatus?=null,de:String="",ate:String="",size:Int=15)=page(get("/api/ong/transparencia",token,paging(page,size)+mapOf("status" to status?.name)+period(de,ate)),::demonstrative)
 suspend fun demonstrativeDetails(token:String,id:Long)=demonstrative(get("/api/ong/transparencia/${id(id)}",token))
 suspend fun editDemonstrative(token:String,id:Long,form:DemonstrativeForm)=demonstrative(patch("/api/ong/transparencia/${id(id)}",token,form(form)))
 suspend fun setPublished(token:String,id:Long,published:Boolean)=demonstrative(patch("/api/ong/transparencia/${id(id)}/${if(published)"publicar" else "despublicar"}",token))
 suspend fun publicDemonstratives(ongId:Int,page:Int=0,size:Int=15):SupportPage<PublicDemonstrativeRecord>{require(ongId>0);return page(get("/api/public/ongs/$ongId/transparencia",params=paging(page,size)),::public)}
 suspend fun publicDetails(ongId:Int,id:Long):PublicDemonstrativeRecord{require(ongId>0);return public(get("/api/public/ongs/$ongId/transparencia/${id(id)}"))}
}
