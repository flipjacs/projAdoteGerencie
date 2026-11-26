package br.com.patasunidas.prontuarios

import br.com.patasunidas.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ProntuarioStoreTest {
 private class Gateway(var identity:UserIdentity):AuthGateway{var checks=0;override suspend fun login(email:String,senha:String)=AuthSession("token",System.currentTimeMillis()+60000);override suspend fun me(token:String):UserIdentity{checks++;return identity}}
 private suspend fun await(c:()->Boolean){withTimeout(4000){while(!c())delay(10)}}
 private fun check(profile:UserProfile=UserProfile.VETERINARIO,block:suspend(MockWebServer,SessionController,ProntuarioStore,Gateway)->Unit)=runBlocking{
  Dispatchers.setMain(UnconfinedTestDispatcher());val s=MockWebServer();s.start();var store:ProntuarioStore?=null
  try{val g=Gateway(UserIdentity(1,"Pessoa","test@example.test",profile,if(profile==UserProfile.USUARIO)null else 1));val sessions=SessionController(g);sessions.login("x","y");val a=sessions.state as SessionState.Authenticated;store=ProntuarioStore(ProntuarioApi(s.url("/").toString()),sessions,a.session,a.identity,7,"Lua");block(s,sessions,store,g)}finally{store?.close();s.shutdown();Dispatchers.resetMain()}
 }
 @Test fun cincoPerfisEProjecaoPessoal(){for(p in UserProfile.entries)check(p){s,_,store,_->assertEquals(p in listOf(UserProfile.ADMINISTRADOR,UserProfile.VETERINARIO),store.canWrite);if(p!=UserProfile.ADMIN_PLATAFORMA)s.enqueue(MockResponse().setBody(medicalPage(recordJson)));store.load();if(p!=UserProfile.ADMIN_PLATAFORMA){await{!store.busy};assertEquals(if(p==UserProfile.USUARIO)"/api/me/animais-adotados/7/prontuarios?page=0&size=15" else "/api/ong/animais/7/prontuarios?page=0&size=15",s.takeRequest().path)};if(!store.canWrite){store.create(form()){fail()};if(p!=UserProfile.VOLUNTARIO)store.loadHistory(3);assertEquals(if(p==UserProfile.ADMIN_PLATAFORMA)0 else 1,s.requestCount)}}}
 @Test fun conflitoConsultaRemotoPreservaDraftENaoRepetePatch()=check{s,_,store,_->
  s.enqueue(MockResponse().setBody(medicalPage(recordJson)));store.load();await{!store.busy};s.enqueue(MockResponse().setBody(recordJson));store.details(3);await{!store.busy};val old=store.selectedInstitutional!!
  s.enqueue(MockResponse().setResponseCode(409));s.enqueue(MockResponse().setBody(recordJson.replace("\"versao\":0","\"versao\":1").replace("Clínica","Remota")));s.enqueue(MockResponse().setBody(medicalPage(recordJson)));val draft=old.data.copy(titulo="Local");store.correct(old,draft,"Revisão"){fail()};await{!store.busy};assertEquals(1L,store.conflictRemote!!.versao);assertEquals("Local",draft.titulo);assertEquals(5,s.requestCount);val methods=(1..5).map{s.takeRequest().method};assertEquals(listOf("GET","GET","PATCH","GET","GET"),methods)
 }
 @Test fun conflitoComFalhaDeLeituraBloqueiaEscritaAteReconsultaERevisaoExplicita()=check{s,_,store,_->
  s.enqueue(MockResponse().setBody(recordJson));store.details(3);await{!store.busy};val old=store.selectedInstitutional!!;val draft=old.data.copy(titulo="Local")
  s.enqueue(MockResponse().setResponseCode(409));s.enqueue(MockResponse().setBody(recordJson).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));store.correct(old,draft,"Revisão"){fail()};await{!store.busy}
  assertTrue(store.conflictPending);assertNull(store.conflictRemote);assertEquals(old,store.selectedInstitutional);assertEquals("Local",draft.titulo);assertEquals(3,s.requestCount)
  store.correct(old,draft,"Revisão"){fail()};await{!store.busy};assertEquals(3,s.requestCount)
  s.enqueue(MockResponse().setBody(recordJson.replace("\"versao\":0","\"versao\":1").replace("Clínica","Remota")));store.retryConflict(3);await{!store.busy}
  val remote=store.conflictRemote!!;assertTrue(store.conflictPending);assertEquals(1L,remote.versao);assertEquals("Local",draft.titulo);assertEquals(4,s.requestCount)
  store.correct(remote,rebaseClinicalDraft(old,draft,remote),"Revisão"){fail()};await{!store.busy};assertEquals(4,s.requestCount)
  val reviewed=rebaseClinicalDraft(old,draft,remote);store.clearConflict();assertFalse(store.conflictPending);s.enqueue(MockResponse().setBody(recordJson.replace("Avaliação","Local").replace("\"versao\":0","\"versao\":2")));var done=false;store.correct(remote,reviewed,"Revisão"){done=true};await{!store.busy};assertTrue(done)
  assertEquals(listOf("GET","PATCH","GET","GET","PATCH"),(1..5).map{s.takeRequest().method});store.close();assertFalse(store.conflictPending)
 }
 @Test fun criacaoImpedeDuplicacaoEAtualizaLista()=check{s,_,store,_->s.enqueue(MockResponse().setBody(medicalPage(recordJson)));store.load();await{!store.busy};s.enqueue(MockResponse().setBody(recordJson).setBodyDelay(100,TimeUnit.MILLISECONDS));s.enqueue(MockResponse().setBody(medicalPage(recordJson)));var done=0;store.create(form()){done++};store.create(form()){done++};await{!store.busy};assertEquals(1,done);assertNull(store.message);assertNotNull(store.selectedInstitutional);assertEquals(3,s.requestCount)}
 @Test fun correcaoConcluidaMantemDetalheSemErro()=check{s,_,store,_->s.enqueue(MockResponse().setBody(recordJson));store.details(3);await{!store.busy};val old=store.selectedInstitutional!!;s.enqueue(MockResponse().setBody(recordJson.replace("Avaliação","Novo")));var done=false;store.correct(old,old.data.copy(titulo="Novo"),"Revisão"){done=true};await{!store.busy};assertTrue(done);assertNull(store.message);assertEquals("Novo",store.selectedInstitutional!!.data.titulo)}
 @Test fun filtrosInvalidosNaoConsultamERespeitamPagina()=check{s,_,store,_->store.filters(null,"2020-02-30","");assertEquals(0,s.requestCount);assertNotNull(store.message);s.enqueue(MockResponse().setBody(medicalPage(recordJson)));store.filters(ClinicalType.VACINA,"2020-01-01","2020-12-31");await{!store.busy};assertTrue(s.takeRequest().path!!.contains("tipo=VACINA&de=2020-01-01&ate=2020-12-31"));assertEquals(ClinicalType.VACINA,store.typeFilter)}
 @Test fun sessaoExpiradaFazLogout()=check{s,sessions,store,_->s.enqueue(MockResponse().setResponseCode(401));store.load();await{sessions.state is SessionState.SignedOut};assertNull(store.institutional)}
 @Test fun forbiddenRevalidaENotFoundLimpaDetalhe()=check{s,_,store,g->s.enqueue(MockResponse().setBody(recordJson));store.details(3);await{!store.busy};s.enqueue(MockResponse().setResponseCode(404));store.details(3);await{!store.busy};assertNull(store.selectedInstitutional);s.enqueue(MockResponse().setResponseCode(403));store.load();await{!store.busy};assertEquals(2,g.checks)}
 @Test fun trocaOngOuSessaoIgnoraRespostaTardia()=check{s,sessions,store,g->s.enqueue(MockResponse().setBody(medicalPage(recordJson)).setBodyDelay(200,TimeUnit.MILLISECONDS));store.load();s.takeRequest();g.identity=g.identity.copy(ongId=2);sessions.operationForbidden();delay(350);assertNull(store.institutional)}
 @Test fun logoutMesmoUsuarioNovaSessaoIgnoraRespostaTardia()=check{s,sessions,store,_->s.enqueue(MockResponse().setBody(medicalPage(recordJson)).setBodyDelay(200,TimeUnit.MILLISECONDS));store.load();s.takeRequest();sessions.logout();sessions.login("x","y");delay(350);assertNull(store.institutional)}
 @Test fun closeCancelaLimpaDados()=check{s,_,store,_->s.enqueue(MockResponse().setBody(medicalPage(recordJson)).setBodyDelay(200,TimeUnit.MILLISECONDS));store.load();s.takeRequest();store.close();delay(350);assertNull(store.institutional);assertFalse(store.busy)}
}
