package br.com.patasunidas.animais

import br.com.patasunidas.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class AnimalStoreTest {
 private class Gateway:AuthGateway {
  var revoked=false
  val identity=UserIdentity(1,"Pessoa","teste@example.test",UserProfile.ADMINISTRADOR,7)
  override suspend fun login(email:String,senha:String)=AuthSession("token",System.currentTimeMillis()+60000)
  override suspend fun me(token:String):UserIdentity { if(revoked) throw SessionForbiddenException(); return identity }
 }
 private suspend fun await(condition:()->Boolean) { withTimeout(3000) { while(!condition()) delay(10) } }
 @Test fun `resposta tardia apos logout nao atualiza lista`() = runBlocking {
  Dispatchers.setMain(UnconfinedTestDispatcher()); val server=MockWebServer();server.start()
  try {
   val gateway=Gateway();val sessions=SessionController(gateway);sessions.login("x","y");val session=(sessions.state as SessionState.Authenticated).session
   val store=AnimalStore(AnimalApi(server.url("/").toString()),sessions,session,7)
   server.enqueue(MockResponse().setBody("""{"content":[],"number":0,"size":20,"totalElements":99,"totalPages":5}""").setBodyDelay(400,TimeUnit.MILLISECONDS))
   store.load(); assertNotNull(server.takeRequest(3,TimeUnit.SECONDS));sessions.logout();delay(600)
   assertTrue(sessions.state is SessionState.SignedOut); assertEquals(0L,store.total);store.close()
  } finally { server.shutdown();Dispatchers.resetMain() }
 }
 @Test fun `proibicao revalida identidade e revogacao encerra sessao`() = runBlocking {
  Dispatchers.setMain(UnconfinedTestDispatcher());val server=MockWebServer();server.start()
  try {
   val gateway=Gateway();val sessions=SessionController(gateway);sessions.login("x","y");val session=(sessions.state as SessionState.Authenticated).session
   val store=AnimalStore(AnimalApi(server.url("/").toString()),sessions,session,7)
   server.enqueue(MockResponse().setResponseCode(403));store.load();await{!store.busy};assertTrue(sessions.state is SessionState.Authenticated)
   gateway.revoked=true;server.enqueue(MockResponse().setResponseCode(403));store.load();await{sessions.state is SessionState.SignedOut};store.close()
  } finally {server.shutdown();Dispatchers.resetMain()}
 }
 @Test fun `conflito nao repete escrita e consulta atual separadamente`() = runBlocking {
  Dispatchers.setMain(UnconfinedTestDispatcher());val server=MockWebServer();server.start()
  try {
   val gateway=Gateway();val sessions=SessionController(gateway);sessions.login("x","y");val session=(sessions.state as SessionState.Authenticated).session
   val store=AnimalStore(AnimalApi(server.url("/").toString()),sessions,session,7)
   fun animal(v:Int)="""{"versao":$v,"id":1,"ongId":7,"nome":"Luna","especie":"CAO","sexo":"FEMEA","porte":"MEDIO","dataEntrada":"2025-01-01","situacao":"EM_TRATAMENTO","ativo":true,"fotos":[]}"""
   server.enqueue(MockResponse().setBody(animal(1)));store.details(1);await{!store.busy};assertEquals(1L,store.selected!!.versao)
   server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"Este animal foi atualizado por outra pessoa. Recarregue os dados antes de salvar novamente."}"""))
   val draft=store.selected!!.form.copy(nome="Rascunho");store.save(1,draft,1) { fail("salvamento indevido") };await{!store.busy}
   assertTrue(store.conflict);assertEquals("Rascunho",draft.nome);assertEquals(2,server.requestCount);assertTrue(sessions.state is SessionState.Authenticated)
   server.enqueue(MockResponse().setBody(animal(2)));store.fetchCurrent(1);await{!store.busy};assertEquals(1L,store.selected!!.versao);assertEquals(2L,store.currentRemote!!.versao)
   assertEquals(2L,store.acceptCurrent(1)!!.versao);store.close()
  } finally {server.shutdown();Dispatchers.resetMain()}
 }
}
