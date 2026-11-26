package br.com.patasunidas.adocoes
import br.com.patasunidas.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class AdoptionStoreTest {
 private val record="""{"id":1,"animal":{"id":8,"nome":"Tito"},"ong":{"id":1,"nome":"ONG"},"status":"PENDENTE","createdAt":"2026-11-22T12:00:00Z","historico":[]}"""
 private fun page(r:String=record)="""{"content":[$r],"number":0,"totalPages":1,"totalElements":1}"""
 private class Gateway:AuthGateway {var checks=0;override suspend fun login(email:String,senha:String)=AuthSession("token",System.currentTimeMillis()+60000);override suspend fun me(token:String):UserIdentity {checks++;return UserIdentity(1,"Pessoa","teste@example.test",UserProfile.USUARIO,null)}}
 private suspend fun await(c:()->Boolean){withTimeout(4000){while(!c())delay(10)}}
 private fun check(block:suspend (MockWebServer,SessionController,AdoptionStore,Gateway)->Unit)=runBlocking {Dispatchers.setMain(UnconfinedTestDispatcher());val server=MockWebServer();server.start();var store:AdoptionStore?=null;try{val g=Gateway();val s=SessionController(g);s.login("x","y");val a=s.state as SessionState.Authenticated;store=AdoptionStore(AdoptionApi(server.url("/").toString()),s,a.session,a.identity);block(server,s,store,g)}finally{store?.close();server.shutdown();Dispatchers.resetMain()}}
 @Test fun logoutIgnoraRespostasTardias()=check {server,s,store,_->server.enqueue(MockResponse().setBody(page()).setBodyDelay(300,TimeUnit.MILLISECONDS));store.load();assertNotNull(server.takeRequest(2,TimeUnit.SECONDS));s.logout();delay(500);assertNull(store.result)}
 @Test fun unauthorizedEncerraSessao()=check {server,s,store,_->server.enqueue(MockResponse().setResponseCode(401));store.load();await{s.state is SessionState.SignedOut}}
 @Test fun forbiddenRevalidaIdentidade()=check {server,s,store,g->server.enqueue(MockResponse().setResponseCode(403));store.load();await{!store.busy};assertEquals(2,g.checks);assertTrue(s.state is SessionState.Authenticated);assertNotNull(store.message)}
 @Test fun notFoundLimpaDetalheAnterior()=check {server,_,store,_->server.enqueue(MockResponse().setBody(record));store.details(1);await{!store.busy};server.enqueue(MockResponse().setResponseCode(404));store.details(1);await{!store.busy};assertNull(store.selected);assertTrue(store.message!!.contains("não encontrada"))}
 @Test fun conflitoAtualizaSemRepetirEscrita()=check {server,_,store,_->server.enqueue(MockResponse().setBody(record));store.details(1);await{!store.busy};server.enqueue(MockResponse().setResponseCode(409));val updated=record.replace("PENDENTE","CONCLUIDA");server.enqueue(MockResponse().setBody(updated));server.enqueue(MockResponse().setBody(page(updated)));store.act(AdoptionAction.CANCEL,"",false){fail()};await{!store.busy};assertEquals(AdoptionStatus.CONCLUIDA,store.selected!!.status);assertEquals(4,server.requestCount);assertTrue(store.message!!.contains("situação mudou"));assertEquals(AdoptionStatus.CONCLUIDA,store.result!!.items[0].status)}
 @Test fun operacaoAtualizaListaEImpedeDuplicacao()=check {server,_,store,_->server.enqueue(MockResponse().setBody(record));store.details(1);await{!store.busy};val updated=record.replace("PENDENTE","CANCELADA");server.enqueue(MockResponse().setBody(updated).setBodyDelay(200,TimeUnit.MILLISECONDS));server.enqueue(MockResponse().setBody(page(updated)));var done=0;store.act(AdoptionAction.CANCEL,"",false){done++};store.act(AdoptionAction.CANCEL,"",false){done++};await{!store.busy};assertEquals(1,done);assertEquals(3,server.requestCount);assertEquals(AdoptionStatus.CANCELADA,store.result!!.items[0].status)}
}
