package br.com.patasunidas.data
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class InstitutionalApiTest {
 @Test fun somenteAdministradorGerenciaEquipe() { UserProfile.entries.forEach { assertEquals(it == UserProfile.ADMINISTRADOR, canManageEmployees(it)) } }
 @Test fun alteracaoEnviaBearerEVersaoSemReenvioAutomatico() = runBlocking {
  val server=MockWebServer()
  try {
   server.start(); server.enqueue(MockResponse().setResponseCode(409))
   val api=InstitutionalApi(server.url("/").toString())
   try { api.request("PUT","/api/ong/funcionarios/4","teste",JSONObject().put("nome","Nome").put("versao",3)); fail() }
   catch(e:InstitutionalHttpException) { assertEquals(409,e.status) }
   val request=server.takeRequest(); assertEquals("PUT",request.method);assertEquals("Bearer teste",request.getHeader("Authorization"));assertEquals(3,JSONObject(request.body.readUtf8()).getInt("versao"));assertEquals(1,server.requestCount)
  } finally { server.shutdown() }
 }
 @Test fun listagemMantemPaginacaoKotlin() = runBlocking {
  val server=MockWebServer()
  try { server.start();server.enqueue(MockResponse().setBody("""{"content":[],"totalPages":0,"totalElements":0}"""))
   val result=InstitutionalApi(server.url("/").toString()).request("GET","/api/plataforma/ongs?page=0&size=20","teste")
   assertEquals(0,result.getJSONArray("content").length());assertEquals("/api/plataforma/ongs?page=0&size=20",server.takeRequest().path)
  } finally { server.shutdown() }
 }
 @Test fun errosPrevistosPossuemMensagens() { listOf(400,401,403,404,409,429).forEach { assertTrue(institutionalMessage(it).isNotBlank()) } }
}
