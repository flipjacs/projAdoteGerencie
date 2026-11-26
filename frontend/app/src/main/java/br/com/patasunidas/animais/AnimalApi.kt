package br.com.patasunidas.animais

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AnimalHttpException(val status: Int, val versionConflict: Boolean = false): IOException()
private const val VERSION_CONFLICT = "ANIMAL_VERSION_CONFLICT"
class ImageValidationException: IOException()

class AnimalApi(baseUrl: String) {
 private val base = baseUrl.trimEnd('/')
 private val client = OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
 private val jsonType="application/json; charset=utf-8".toMediaType()
 private fun request(path:String, token:String) = Request.Builder().url(base+path).header("Authorization","Bearer $token")
 private suspend fun bytes(request:Request):ByteArray = suspendCancellableCoroutine { continuation ->
  val call=client.newCall(request); continuation.invokeOnCancellation { call.cancel() }
  call.enqueue(object:Callback {
   override fun onFailure(call:Call,e:IOException) { if(continuation.isActive) continuation.resumeWithException(e) }
   override fun onResponse(call:Call,response:Response) {
    try {
     val data=response.use { r ->
      if(!r.isSuccessful) {
       val code=if(r.code==409) try { JSONObject(r.peekBody(4096).string()).optString("code") } catch(_:Exception) { "" } else ""
       throw AnimalHttpException(r.code, code == VERSION_CONFLICT)
      }
      r.body?.byteStream()?.use { input -> val output=java.io.ByteArrayOutputStream(); val buf=ByteArray(8192); var total=0; while(true) { val n=input.read(buf); if(n<0) break; total+=n; if(total>5*1024*1024) throw IOException("Resposta muito grande"); output.write(buf,0,n) }; output.toByteArray() } ?: throw IOException("Resposta vazia")
     }
     if(continuation.isActive) continuation.resume(data)
    } catch(e:Exception) { if(continuation.isActive) continuation.resumeWithException(e) }
   }
  })
 }
 private suspend fun response(request:Request):String = bytes(request).toString(Charsets.UTF_8)
 private fun optional(j:JSONObject,key:String)=if(j.isNull(key)) "" else j.getString(key)
 private fun parse(j:JSONObject):Animal {
  val photos=j.getJSONArray("fotos"); val form=AnimalForm(j.getString("nome"),j.getString("especie"),j.getString("sexo"),j.getString("porte"),j.getString("dataEntrada"),optional(j,"nascimentoEstimado"),optional(j,"descricao"),optional(j,"historia"),optional(j,"dataResgate"),optional(j,"origemResgate"),optional(j,"observacoesResgate"))
  val versao=when(val v=j.get("versao")) { is Int -> v.toLong(); is Long -> v; else -> throw IOException("Versão ausente ou inválida") }; if(versao<0) throw IOException("Versão inválida")
  return Animal(j.getLong("id"),j.getInt("ongId"),form,AnimalStatus.valueOf(j.getString("situacao")),j.getBoolean("ativo"),(0 until photos.length()).map { val p=photos.getJSONObject(it); AnimalPhoto(p.getLong("id"),p.getBoolean("principal"),p.getString("url")) },versao,j.optBoolean("publicado",false))
 }
 private fun parsePublic(j: JSONObject): PublicAnimal {
  val photos=j.getJSONArray("fotos"); val ong=j.optJSONObject("ong")
  fun nullable(o: JSONObject?, key: String): String? = o?.optString(key)?.takeUnless { it.isBlank() || it=="null" }
  return PublicAnimal(j.getLong("id"),j.getString("nome"),j.getString("especie"),j.getString("sexo"),j.getString("porte"),nullable(j,"descricao"),(0 until photos.length()).map { val p=photos.getJSONObject(it); AnimalPhoto(p.getLong("id"),p.getBoolean("principal"),p.getString("url")) },nullable(j,"nascimentoEstimado"),nullable(ong,"nome"),nullable(ong,"cidade"),nullable(ong,"estado"),ong?.optInt("id")?.takeIf { it>0 })
 }
 suspend fun publicList(page: Int=0, especie: String?=null, sexo: String?=null, porte: String?=null): PublicAnimalPage {
  require(page>=0 && (especie==null || especie in especies) && (sexo==null || sexo in sexos) && (porte==null || porte in portes))
  val url=(base+"/api/animais").toHttpUrl().newBuilder().addQueryParameter("page",page.toString()).addQueryParameter("size","20")
  listOf("especie" to especie,"sexo" to sexo,"porte" to porte).forEach { (key,value) -> if(value!=null) url.addQueryParameter(key,value) }
  val j=JSONObject(response(Request.Builder().url(url.build()).get().build()));val items=j.getJSONArray("content")
  return PublicAnimalPage((0 until items.length()).map { parsePublic(items.getJSONObject(it)) },j.getInt("number"),j.getInt("totalPages"),j.getLong("totalElements"))
 }
 suspend fun publicGet(id: Long): PublicAnimal = parsePublic(JSONObject(response(Request.Builder().url(base+"/api/animais/$id").get().build())))
 suspend fun publicPhotoBytes(animalId: Long,photoId: Long): ByteArray = bytes(Request.Builder().url(base+"/api/animais/$animalId/fotos/$photoId").get().build())
 suspend fun publication(token:String,id:Long,published:Boolean):Animal = parse(JSONObject(response(request("/api/ong/animais/$id/"+if(published) "publicar" else "despublicar",token).patch(ByteArray(0).toRequestBody(null)).build())))
 private fun body(f:AnimalForm,versao:Long? = null):RequestBody {
  fun opt(v:String):Any = v.takeIf { it.isNotEmpty() } ?: JSONObject.NULL
  val json=JSONObject().put("nome",f.nome.trim()).put("especie",f.especie).put("sexo",f.sexo).put("porte",f.porte).put("dataEntrada",f.dataEntrada)
   .put("nascimentoEstimado",opt(f.nascimentoEstimado)).put("descricao",opt(f.descricao)).put("historia",opt(f.historia)).put("dataResgate",opt(f.dataResgate)).put("origemResgate",opt(f.origemResgate)).put("observacoesResgate",opt(f.observacoesResgate))
  if(versao!=null) json.put("versao",versao)
  return json.toString().toRequestBody(jsonType)
 }
 suspend fun list(token:String,page:Int,active:Boolean?):AnimalPage = withContext(Dispatchers.IO) {
  val filter=active?.let { "&ativo=$it" }.orEmpty(); val j=JSONObject(response(request("/api/ong/animais?page=$page&size=20$filter",token).get().build())); val array=j.getJSONArray("content")
  AnimalPage((0 until array.length()).map { parse(array.getJSONObject(it)) },j.getInt("number"),j.getInt("size"),j.getLong("totalElements"),j.getInt("totalPages"))
 }
 suspend fun get(token:String,id:Long):Animal = withContext(Dispatchers.IO) { parse(JSONObject(response(request("/api/ong/animais/$id",token).get().build()))) }
 suspend fun save(token:String,id:Long?,form:AnimalForm,versao:Long? = null):Animal = withContext(Dispatchers.IO) {
  val builder=request(if(id==null) "/api/animais" else "/api/ong/animais/$id",token)
  parse(JSONObject(response((if(id==null) builder.post(body(form)) else builder.put(body(form,requireNotNull(versao)))).build())))
 }
 suspend fun status(token:String,id:Long,status:AnimalStatus,versao:Long):Animal = withContext(Dispatchers.IO) { parse(JSONObject(response(request("/api/ong/animais/$id/situacao",token).patch(JSONObject().put("situacao",status.name).put("versao",versao).toString().toRequestBody(jsonType)).build()))) }
 suspend fun deactivate(token:String,id:Long):Animal = withContext(Dispatchers.IO) { parse(JSONObject(response(request("/api/ong/animais/$id/inativacao",token).patch(ByteArray(0).toRequestBody(null)).build()))) }
 suspend fun upload(token:String,id:Long,uri:Uri,resolver:ContentResolver) = withContext(Dispatchers.IO) {
  if(uri.scheme != "content") throw ImageValidationException()
  val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true }
  resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,opts) } ?: throw ImageValidationException()
  if(opts.outMimeType !in listOf("image/jpeg","image/png") || opts.outWidth<=0 || opts.outHeight<=0 || opts.outWidth.toLong()*opts.outHeight>16_000_000) throw ImageValidationException()
  val total=resolver.openInputStream(uri)?.use { input -> val buf=ByteArray(8192); var size=0L; while(true) { val n=input.read(buf); if(n<0) break; size+=n; if(size>5L*1024*1024) throw ImageValidationException() }; size } ?: throw ImageValidationException()
  if(total==0L) throw ImageValidationException()
  val file=object:RequestBody() {
   override fun contentType()=opts.outMimeType.toMediaType()
   override fun contentLength()=total
   override fun writeTo(sink:BufferedSink) { resolver.openInputStream(uri)?.use { input -> val buf=ByteArray(8192); var remaining=total; while(remaining>0) { val n=input.read(buf,0,minOf(buf.size.toLong(),remaining).toInt()); if(n<0) throw IOException("Imagem indisponível"); sink.write(buf,0,n); remaining-=n } } ?: throw IOException("Imagem indisponível") }
  }
  val multipart=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("arquivo","imagem",file).build()
  response(request("/api/ong/animais/$id/fotos",token).post(multipart).build()); Unit
 }
 suspend fun photo(token:String,animalId:Long,photoId:Long):Bitmap = withContext(Dispatchers.IO) {
  val bytes=bytes(request("/api/ong/animais/$animalId/fotos/$photoId",token).get().build())
  val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
  if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth.toLong()*bounds.outHeight>16_000_000) throw IOException("Imagem inválida")
  var sample=1; while(bounds.outWidth/sample>600 || bounds.outHeight/sample>600) sample*=2
  BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample }) ?: throw IOException("Imagem inválida")
 }
}
