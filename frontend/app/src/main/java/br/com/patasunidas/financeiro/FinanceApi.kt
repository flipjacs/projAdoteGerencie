package br.com.patasunidas.financeiro

import br.com.patasunidas.data.UserProfile
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class DonationStatus(val label:String) { PENDENTE("Pendente"), CONFIRMADA("Confirmada"), CANCELADA("Cancelada") }
enum class MovementStatus(val label:String) { PENDENTE("Pendente"), APROVADA("Aprovada"), REALIZADA("Realizada"), CANCELADA("Cancelada") }
enum class MovementType(val label:String) { RECEITA("Receita"), DESPESA("Despesa") }
enum class FinanceCategory(val label:String) {
 DOACAO("Doação"), APADRINHAMENTO("Apadrinhamento"), EVENTO("Evento"), OUTRA_RECEITA("Outra receita"), ALIMENTACAO("Alimentação"), MEDICAMENTOS("Medicamentos"), VETERINARIO("Veterinário"), TRANSPORTE("Transporte"), MANUTENCAO("Manutenção"), OUTRA_DESPESA("Outra despesa");
 companion object { fun forType(type:MovementType)=if(type==MovementType.RECEITA) listOf(EVENTO,OUTRA_RECEITA) else listOf(ALIMENTACAO,MEDICAMENTOS,VETERINARIO,TRANSPORTE,MANUTENCAO,OUTRA_DESPESA) }
}
enum class FinanceAction(val label:String,val path:String) { CONFIRM_DONATION("Confirmar recebimento","confirmar"), APPROVE("Aprovar despesa","aprovar"), RECEIVE("Confirmar recebimento","confirmar-recebimento"), PAY("Confirmar pagamento","confirmar-pagamento"), CANCEL("Cancelar","cancelar") }
fun canManageFinance(profile:UserProfile)=profile in listOf(UserProfile.ADMINISTRADOR,UserProfile.VOLUNTARIO)
fun donationActions(record:DonationRecord,profile:UserProfile)=if(record.status==DonationStatus.PENDENTE) when(profile){UserProfile.USUARIO->listOf(FinanceAction.CANCEL);UserProfile.ADMINISTRADOR->listOf(FinanceAction.CONFIRM_DONATION,FinanceAction.CANCEL);else->emptyList()} else emptyList()
fun movementActions(record:MovementRecord,profile:UserProfile):List<FinanceAction> = if(profile!=UserProfile.ADMINISTRADOR||record.origin!="MANUAL") emptyList() else when {
 record.status==MovementStatus.PENDENTE && record.type==MovementType.RECEITA->listOf(FinanceAction.RECEIVE,FinanceAction.CANCEL)
 record.status==MovementStatus.PENDENTE && record.type==MovementType.DESPESA->listOf(FinanceAction.APPROVE,FinanceAction.CANCEL)
 record.status==MovementStatus.APROVADA && record.type==MovementType.DESPESA->listOf(FinanceAction.PAY,FinanceAction.CANCEL)
 else->emptyList()
}
/** Accept a decimal separator, never grouping, exponent syntax or silent rounding. */
fun normalizeMoney(input:String):String {
 val value=input.trim().replace(',','.')
 require(Regex("(?:0|[1-9][0-9]{0,9})(?:\\.[0-9]{1,2})?").matches(value)){"Informe um valor positivo, até 9999999999,99 e duas casas decimais."}
 val decimal=value.toBigDecimal();require(decimal.signum()>0 && decimal<=BigDecimal("9999999999.99")){"Informe um valor positivo."}
 return decimal.setScale(2,RoundingMode.UNNECESSARY).toPlainString()
}
fun formatMoney(value:String):String {
 val decimal=value.toBigDecimal().setScale(2,RoundingMode.UNNECESSARY)
 return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).apply { minimumFractionDigits=2;maximumFractionDigits=2;roundingMode=RoundingMode.UNNECESSARY }.format(decimal).replace('\u00a0',' ')
}
data class FinancePerson(val id:Int,val name:String)
data class FinanceHistory(val operation:String,val previous:String?,val next:String,val reason:String?,val at:String,val actor:FinancePerson?)
data class DonationRecord(val id:Long,val ongId:Int,val ongName:String,val value:String,val status:DonationStatus,val message:String?,val confirmedAt:String?,val cancelledAt:String?,val cancellation:String?,val created:String,val updated:String,val version:Long,val history:List<FinanceHistory>,val donor:FinancePerson?,val confirmedBy:FinancePerson?,val cancelledBy:FinancePerson?)
data class MovementRecord(val id:Long,val ongId:Int,val type:MovementType,val category:FinanceCategory,val description:String,val value:String,val status:MovementStatus,val origin:String,val donationId:Long?,val registeredBy:FinancePerson?,val approvedBy:FinancePerson?,val confirmedBy:FinancePerson?,val cancelledBy:FinancePerson?,val approvedAt:String?,val confirmedAt:String?,val cancelledAt:String?,val cancellation:String?,val created:String,val updated:String,val version:Long,val history:List<FinanceHistory>)
data class FinanceSummary(val receitasRealizadas:String,val despesasRealizadas:String,val receitasPendentes:String,val despesasPendentes:String,val despesasAprovadas:String,val saldoRealizado:String)
data class FinancePage<T>(val items:List<T>,val page:Int,val pages:Int,val total:Long,val size:Int)
class FinanceHttpException(val status:Int,val code:String?=null):IOException()
class FinanceApi(baseUrl:String) {
 private val base=baseUrl.trimEnd('/')
 private val client=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
 private fun body(j:JSONObject)=j.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
 private fun request(path:String,token:String)=Request.Builder().url(base+path).header("Authorization","Bearer $token")
 private suspend fun json(request:Request):JSONObject=suspendCancellableCoroutine { c->
  val call=client.newCall(request);c.invokeOnCancellation {call.cancel()};call.enqueue(object:Callback {
   override fun onFailure(call:Call,e:IOException){if(c.isActive)c.resumeWithException(e)}
   override fun onResponse(call:Call,response:Response){try{val value=response.use { r->
    val bytes=r.peekBody(1024*1024+1).bytes();if(bytes.size>1024*1024)throw IOException("Resposta muito grande")
    val text=bytes.toString(Charsets.UTF_8)
    if(!r.isSuccessful)throw FinanceHttpException(r.code,runCatching { JSONObject(text).optString("code").takeIf {it.isNotEmpty()} }.getOrNull())
    JSONObject(text)
   };if(c.isActive)c.resume(value)}catch(e:Exception){if(c.isActive)c.resumeWithException(e)}}
  })
 }
 private fun optional(j:JSONObject,key:String)=if(j.isNull(key))null else j.getString(key)
 private fun person(j:JSONObject,key:String)=j.optJSONObject(key)?.let { FinancePerson(it.getInt("id"),it.getString("nome")) }
 private fun history(j:JSONObject):List<FinanceHistory> {val a=j.getJSONArray("historico");return (0 until a.length()).map {val h=a.getJSONObject(it);FinanceHistory(h.getString("operacao"),optional(h,"anterior"),h.getString("posterior"),optional(h,"justificativa"),h.getString("ocorridoEm"),person(h,"ator"))}}
 private fun donation(j:JSONObject):DonationRecord {val o=j.getJSONObject("ong");return DonationRecord(j.getLong("id"),o.getInt("id"),o.getString("nome"),j.getString("valor"),DonationStatus.valueOf(j.getString("status")),optional(j,"mensagem"),optional(j,"confirmadoEm"),optional(j,"canceladoEm"),optional(j,"justificativaCancelamento"),j.getString("createdAt"),j.getString("updatedAt"),j.getLong("versao"),history(j),person(j,"doador"),person(j,"confirmadoPor"),person(j,"canceladoPor"))}
 private fun movement(j:JSONObject):MovementRecord {val o=j.getJSONObject("origem");return MovementRecord(j.getLong("id"),j.getInt("ongId"),MovementType.valueOf(j.getString("tipo")),FinanceCategory.valueOf(j.getString("categoria")),j.getString("descricao"),j.getString("valor"),MovementStatus.valueOf(j.getString("status")),o.getString("tipo"),if(o.isNull("id"))null else o.getLong("id"),person(j,"registradoPor"),person(j,"aprovadoPor"),person(j,"confirmadoPor"),person(j,"canceladoPor"),optional(j,"aprovadoEm"),optional(j,"confirmadoEm"),optional(j,"canceladoEm"),optional(j,"justificativaCancelamento"),j.getString("createdAt"),j.getString("updatedAt"),j.getLong("versao"),history(j))}
 private fun <T> page(j:JSONObject,parse:(JSONObject)->T):FinancePage<T> {val a=j.getJSONArray("content");return FinancePage((0 until a.length()).map {parse(a.getJSONObject(it))},j.getInt("number"),j.getInt("totalPages"),j.getLong("totalElements"),j.getInt("size"))}
 private fun prefix(internal:Boolean)="/api/${if(internal) "ong" else "me"}/donations"
 private suspend fun get(token:String,path:String,params:Map<String,String?> = emptyMap()):JSONObject {val url=(base+path).toHttpUrl().newBuilder();params.forEach {(k,v)->if(!v.isNullOrBlank())url.addQueryParameter(k,v)};return json(Request.Builder().url(url.build()).header("Authorization","Bearer $token").get().build())}
 private fun pagination(page:Int,size:Int):Map<String,String?> {require(page>=0 && size in 1..100);return linkedMapOf("page" to page.toString(),"size" to size.toString())}
 suspend fun createDonation(token:String,ongId:Int,value:String,message:String):DonationRecord {require(ongId>0 && message.length<=2000);return donation(json(request("/api/donations",token).post(body(JSONObject().put("ongId",ongId).put("valor",normalizeMoney(value)).put("mensagem",message.trim().takeIf {it.isNotEmpty()}?:JSONObject.NULL))).build()))}
 suspend fun donations(token:String,internal:Boolean,page:Int=0,status:DonationStatus?=null,de:String="",ate:String="",size:Int=20):FinancePage<DonationRecord> {val params=pagination(page,size)+(if(internal)mapOf("status" to status?.name,"de" to de,"ate" to ate) else emptyMap());return page(get(token,prefix(internal),params),::donation)}
 suspend fun donationDetails(token:String,internal:Boolean,id:Long):DonationRecord {require(id>0);return donation(get(token,"${prefix(internal)}/$id"))}
 private fun reasonBody(reason:String):RequestBody {require(reason.length<=1000);return if(reason.isBlank()) body(JSONObject()) else body(JSONObject().put("justificativa",reason.trim()))}
 suspend fun actDonation(token:String,internal:Boolean,id:Long,action:FinanceAction,reason:String=""):DonationRecord {require(id>0 && (action==FinanceAction.CANCEL || internal && action==FinanceAction.CONFIRM_DONATION));return donation(json(request("${prefix(internal)}/$id/${action.path}",token).patch(if(action==FinanceAction.CANCEL)reasonBody(reason) else body(JSONObject())).build()))}
 suspend fun createMovement(token:String,type:MovementType,category:FinanceCategory,description:String,value:String):MovementRecord {require(category in FinanceCategory.forType(type) && description.trim().isNotEmpty() && description.trim().length<=255);return movement(json(request("/api/ong/finance/movimentacoes",token).post(body(JSONObject().put("tipo",type.name).put("categoria",category.name).put("descricao",description.trim()).put("valor",normalizeMoney(value)))).build()))}
 suspend fun movements(token:String,page:Int=0,type:MovementType?=null,status:MovementStatus?=null,category:FinanceCategory?=null,de:String="",ate:String="",size:Int=20)=page(get(token,"/api/ong/finance/movimentacoes",pagination(page,size)+mapOf("tipo" to type?.name,"status" to status?.name,"categoria" to category?.name,"de" to de,"ate" to ate)),::movement)
 suspend fun movementDetails(token:String,id:Long):MovementRecord {require(id>0);return movement(get(token,"/api/ong/finance/movimentacoes/$id"))}
 suspend fun actMovement(token:String,id:Long,action:FinanceAction,reason:String=""):MovementRecord {require(id>0 && action!=FinanceAction.CONFIRM_DONATION);return movement(json(request("/api/ong/finance/movimentacoes/$id/${action.path}",token).patch(if(action==FinanceAction.CANCEL)reasonBody(reason) else body(JSONObject())).build()))}
 suspend fun summary(token:String,de:String="",ate:String=""):FinanceSummary {val j=get(token,"/api/ong/finance/resumo",mapOf("de" to de,"ate" to ate));return FinanceSummary(j.getString("receitasRealizadas"),j.getString("despesasRealizadas"),j.getString("receitasPendentes"),j.getString("despesasPendentes"),j.getString("despesasAprovadas"),j.getString("saldoRealizado"))}
}
