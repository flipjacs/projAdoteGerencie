package br.com.patasunidas.animais

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AnimalApiTest {
 private val animal="""{"versao":9007199254740993,"id":1,"ongId":2,"nome":"Luna","especie":"CAO","sexo":"FEMEA","porte":"MEDIO","dataEntrada":"2025-01-01","nascimentoEstimado":null,"descricao":"resgatada","historia":"história","dataResgate":null,"origemResgate":null,"observacoesResgate":"observação","situacao":"EM_TRATAMENTO","ativo":true,"fotos":[{"id":3,"principal":true,"url":"/api/ong/animais/1/fotos/3"}]}"""
 @Test fun `lista interpreta pagina foto e token`() = runBlocking {
  val server=MockWebServer();server.start(); try {
   server.enqueue(MockResponse().setBody("""{"content":[$animal],"number":0,"size":20,"totalElements":1,"totalPages":1}"""))
   val page=AnimalApi(server.url("/").toString()).list("token",0,true);assertEquals("Bearer token",server.takeRequest().getHeader("Authorization"));assertEquals(1L,page.total);assertEquals(9007199254740993L,page.items[0].versao);assertEquals("Luna",page.items[0].form.nome);assertEquals(3L,page.items[0].fotos[0].id);assertEquals("observação",page.items[0].form.observacoesResgate)
  } finally {server.shutdown()}
 }
 @Test fun `edicao preserva campos e patch transmite situacao`() = runBlocking {
  val server=MockWebServer();server.start();try {
   server.enqueue(MockResponse().setBody(animal));server.enqueue(MockResponse().setBody(animal))
   val api=AnimalApi(server.url("/").toString());val f=AnimalForm(nome="Luna",dataEntrada="2025-01-01",historia="história",observacoesResgate="observação");api.save("token",1,f,9007199254740993L)
   val request=server.takeRequest();val sent=request.body.readUtf8();assertEquals("PUT",request.method);assertTrue(sent.contains("9007199254740993"));assertTrue(sent.contains("história"));assertTrue(sent.contains("observação"));assertFalse(sent.contains("ongId"))
   api.status("token",1,AnimalStatus.DISPONIVEL,9007199254740993L);val patch=server.takeRequest();assertEquals("PATCH",patch.method);val patchBody=patch.body.readUtf8();assertTrue(patchBody.contains("DISPONIVEL"));assertTrue(patchBody.contains("9007199254740993"))
  } finally {server.shutdown()}
 }
 @Test fun `status HTTP de erro preserva codigo`() = runBlocking {
  val server=MockWebServer();server.start();try {val api=AnimalApi(server.url("/").toString());for(code in listOf(400,401,403,404,409,413,500)){server.enqueue(MockResponse().setResponseCode(code));try{api.get("token",1);fail("HTTP $code")}catch(e:AnimalHttpException){assertEquals(code,e.status)}}}finally {server.shutdown()}
 }
 @Test fun `cadastro e inativacao usam rotas e metodos corretos`() = runBlocking {
  val server=MockWebServer();server.start();try {
   server.enqueue(MockResponse().setResponseCode(201).setBody(animal));server.enqueue(MockResponse().setBody(animal))
   val api=AnimalApi(server.url("/").toString());val f=AnimalForm(nome="Luna",dataEntrada="2025-01-01")
   api.save("token",null,f);val create=server.takeRequest();assertEquals("POST",create.method);assertEquals("/api/animais",create.path);assertFalse(create.body.readUtf8().contains("ongId"))
   api.deactivate("token",1);val deactivate=server.takeRequest();assertEquals("PATCH",deactivate.method);assertEquals("/api/ong/animais/1/inativacao",deactivate.path)
  } finally {server.shutdown()}
 }
 @Test fun `redirecionamento nao encaminha credencial a outro servidor`() = runBlocking {
  val first=MockWebServer();val second=MockWebServer();first.start();second.start();try {
   first.enqueue(MockResponse().setResponseCode(302).addHeader("Location",second.url("/")))
   try { AnimalApi(first.url("/").toString()).get("token",1);fail() } catch(e:AnimalHttpException) { assertEquals(302,e.status) }
   assertEquals(0,second.requestCount)
  } finally {first.shutdown();second.shutdown()}
 }
 @Test fun `resposta sem versao ou com tipo invalido e rejeitada`() = runBlocking {
  val server=MockWebServer();server.start();try {
   val api=AnimalApi(server.url("/").toString())
   for(value in listOf(null, "\"3\"", "1.5", "-1")) {
    val body=if(value==null) animal.replace("\"versao\":9007199254740993,","") else animal.replace("9007199254740993",value)
    server.enqueue(MockResponse().setBody(body)); try { api.get("token",1);fail("versão inválida aceita") } catch(_:Exception) { }
   }
  } finally {server.shutdown()}
 }
 @Test fun `conflito de versao e diferenciado de outros 409`() = runBlocking {
  val server=MockWebServer();server.start();try {
   val api=AnimalApi(server.url("/").toString())
   for((body,expected) in listOf(
    """{"code":"ANIMAL_VERSION_CONFLICT","detail":"mensagem alterada"}""" to true,
    """{"code":"OUTRO","detail":"Este animal foi atualizado por outra pessoa. Recarregue os dados antes de salvar novamente."}""" to false,
    """{"detail":"qualquer"}""" to false, "inválido" to false, "" to false)) {
    server.enqueue(MockResponse().setResponseCode(409).setBody(body))
    try {api.get("token",1);fail()} catch(e:AnimalHttpException) { assertEquals(expected,e.versionConflict) }
   }
  } finally {server.shutdown()}
 }
}
