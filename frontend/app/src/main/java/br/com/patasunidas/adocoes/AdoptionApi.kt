package br.com.patasunidas.adocoes

import br.com.patasunidas.data.UserProfile
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

enum class AdoptionStatus(val label:String) { PENDENTE("Pendente"), APROVADA("Aprovada"), REJEITADA("Rejeitada"), CANCELADA("Cancelada"), CONCLUIDA("Concluída") }
enum class AdoptionAction(val title:String,val heading:String,val submit:String,val path:String) {
 APPROVE("Aprovar solicitação","Aprovar e reservar","Reservar animal","aprovar"),
 REJECT("Rejeitar solicitação","Rejeitar solicitação","Rejeitar pedido","rejeitar"),
 CANCEL_APPROVAL("Cancelar aprovação","Cancelar aprovação","Liberar reserva","cancelar-aprovacao"),
 CANCEL("Cancelar solicitação","Cancelar solicitação?","Cancelar solicitação","cancelar"),
 DELIVER("Confirmar entrega","Concluir adoção","Concluir adoção","confirmar-entrega")
}
fun canManageAdoptions(profile:UserProfile)=profile==UserProfile.ADMINISTRADOR || profile==UserProfile.VOLUNTARIO
fun actionsFor(status:AdoptionStatus,profile:UserProfile):List<AdoptionAction> = when {
 profile==UserProfile.USUARIO && status in listOf(AdoptionStatus.PENDENTE,AdoptionStatus.APROVADA) -> listOf(AdoptionAction.CANCEL)
 canManageAdoptions(profile) && status==AdoptionStatus.PENDENTE -> listOf(AdoptionAction.APPROVE,AdoptionAction.REJECT)
 canManageAdoptions(profile) && status==AdoptionStatus.APROVADA -> listOf(AdoptionAction.DELIVER,AdoptionAction.CANCEL_APPROVAL)
 else -> emptyList()
}
data class AdoptionHistory(val operation:String,val previous:String?,val next:String,val reason:String?,val at:String)
data class AdoptionRecord(val id:Long,val animalId:Long,val animalName:String,val ongName:String,val status:AdoptionStatus,val message:String?,val rejection:String?,val cancellation:String?,val created:String,val delivered:String?,val requester:String?,val email:String?,val history:List<AdoptionHistory>)
data class AdoptionPage(val items:List<AdoptionRecord>,val page:Int,val pages:Int,val total:Long)
class AdoptionHttpException(val status:Int):IOException()
class AdoptionApi(baseUrl:String) {
 private val base=baseUrl.trimEnd('/')
 private val client=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
 private fun body(j:JSONObject)=j.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
 private fun prefix(internal:Boolean)="/api/${if(internal) "ong" else "me"}/adoption-requests"
 private suspend fun json(request:Request):JSONObject = suspendCancellableCoroutine { c ->
  val call=client.newCall(request);c.invokeOnCancellation { call.cancel() }
  call.enqueue(object:Callback {
   override fun onFailure(call:Call,e:IOException){if(c.isActive)c.resumeWithException(e)}
   override fun onResponse(call:Call,response:Response){try{val value=response.use { if(!it.isSuccessful)throw AdoptionHttpException(it.code);val bytes=it.peekBody(1024*1024+1).bytes();if(bytes.size>1024*1024)throw IOException("Resposta muito grande");JSONObject(bytes.toString(Charsets.UTF_8)) };if(c.isActive)c.resume(value)}catch(e:Exception){if(c.isActive)c.resumeWithException(e)}}
  })
 }
 private fun request(path:String,token:String)=Request.Builder().url(base+path).header("Authorization","Bearer $token")
 private fun optional(j:JSONObject,k:String)=if(j.isNull(k))null else j.getString(k)
 private fun parse(j:JSONObject):AdoptionRecord {
  val a=j.getJSONObject("animal");val h=j.getJSONArray("historico");val p=j.optJSONObject("solicitante")
  return AdoptionRecord(j.getLong("id"),a.getLong("id"),a.getString("nome"),j.getJSONObject("ong").getString("nome"),AdoptionStatus.valueOf(j.getString("status")),optional(j,"mensagem"),optional(j,"justificativaRejeicao"),optional(j,"justificativaCancelamento"),j.getString("createdAt"),j.optJSONObject("adocao")?.getString("dataEntrega"),p?.getString("nome"),p?.let { optional(it,"email") },(0 until h.length()).map { val x=h.getJSONObject(it);AdoptionHistory(x.getString("operacao"),optional(x,"anterior"),x.getString("posterior"),optional(x,"justificativa"),x.getString("ocorridoEm")) })
 }
 suspend fun create(token:String,animalId:Long,message:String):AdoptionRecord { require(animalId>0 && message.length<=2000);return parse(json(request("/api/adoption-requests",token).post(body(JSONObject().put("animalId",animalId).put("mensagem",message.takeIf { it.isNotBlank() } ?: JSONObject.NULL))).build())) }
 suspend fun list(token:String,internal:Boolean,page:Int=0,status:AdoptionStatus?=null,animalId:Long?=null):AdoptionPage {
  require(page>=0 && (animalId==null || animalId>0));val url=(base+prefix(internal)).toHttpUrl().newBuilder().addQueryParameter("page",page.toString()).addQueryParameter("size","20")
  if(internal){status?.let{url.addQueryParameter("status",it.name)};animalId?.let{url.addQueryParameter("animalId",it.toString())}}
  val j=json(Request.Builder().url(url.build()).header("Authorization","Bearer $token").get().build());val a=j.getJSONArray("content")
  return AdoptionPage((0 until a.length()).map{parse(a.getJSONObject(it))},j.getInt("number"),j.getInt("totalPages"),j.getLong("totalElements"))
 }
 suspend fun details(token:String,internal:Boolean,id:Long)=parse(json(request("${prefix(internal)}/$id",token).get().build()))
 suspend fun act(token:String,internal:Boolean,id:Long,action:AdoptionAction,reason:String="",confirmed:Boolean=false):AdoptionRecord {
  require((action==AdoptionAction.CANCEL)==!internal)
  val payload=when(action){AdoptionAction.REJECT,AdoptionAction.CANCEL_APPROVAL -> {require(reason.isNotBlank() && reason.length<=1000);body(JSONObject().put("justificativa",reason.trim()))};AdoptionAction.DELIVER -> {require(confirmed);body(JSONObject().put("entregaConfirmada",true))};else -> ByteArray(0).toRequestBody(null)}
  val r=request("${prefix(internal)}/$id/${action.path}",token)
  return parse(json((if(action==AdoptionAction.DELIVER)r.post(payload)else r.patch(payload)).build()))
 }
}
