package br.com.patasunidas.financeiro

import br.com.patasunidas.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceStoreTest {
 private class Gateway(var identity:UserIdentity):AuthGateway {var checks=0;override suspend fun login(email:String,senha:String)=AuthSession("token",System.currentTimeMillis()+60000);override suspend fun me(token:String):UserIdentity {checks++;return identity}}
 private suspend fun await(c:()->Boolean){withTimeout(4000){while(!c())delay(10)}}
 private fun check(profile:UserProfile=UserProfile.USUARIO,block:suspend (MockWebServer,SessionController,FinanceStore,Gateway)->Unit)=runBlocking {
  Dispatchers.setMain(UnconfinedTestDispatcher());val server=MockWebServer();server.start();var store:FinanceStore?=null
  try{val gateway=Gateway(UserIdentity(1,"Pessoa","test@example.test",profile,if(profile==UserProfile.USUARIO)null else 1));val sessions=SessionController(gateway);sessions.login("x","y");val a=sessions.state as SessionState.Authenticated;store=FinanceStore(FinanceApi(server.url("/").toString()),sessions,a.session,a.identity);block(server,sessions,store,gateway)}finally{store?.close();server.shutdown();Dispatchers.resetMain()}
 }
 @Test fun logoutEDiferenteUsuarioIgnoramRespostaTardia()=check {s,sessions,store,g->s.enqueue(MockResponse().setBody(pageJson(donationJson)).setBodyDelay(300,TimeUnit.MILLISECONDS));store.loadDonations();assertNotNull(s.takeRequest(2,TimeUnit.SECONDS));sessions.logout();g.identity=g.identity.copy(id=2);sessions.login("x","y");delay(500);assertNull(store.donations)}
 @Test fun trocaOngIgnoraRespostaTardia()=check(UserProfile.ADMINISTRADOR) {s,sessions,store,g->s.enqueue(MockResponse().setBody(pageJson(movementJson)).setBodyDelay(300,TimeUnit.MILLISECONDS));store.loadMovements();assertNotNull(s.takeRequest(2,TimeUnit.SECONDS));g.identity=g.identity.copy(ongId=2);sessions.operationForbidden();delay(500);assertNull(store.movements);assertNull(store.summary);assertEquals(1,s.requestCount)}
 @Test fun fecharCancelaELimpaDados()=check {s,_,store,_->s.enqueue(MockResponse().setBody(pageJson(donationJson)).setBodyDelay(300,TimeUnit.MILLISECONDS));store.loadDonations();assertNotNull(s.takeRequest(2,TimeUnit.SECONDS));store.close();delay(400);assertNull(store.donations);assertFalse(store.busy)}
 @Test fun atualizarListaSincronizaDetalheDeDoacaoExpandido()=check {s,_,store,_->
  s.enqueue(MockResponse().setBody(donationJson));store.detailsDonation(3);await{!store.busy}
  assertEquals(DonationStatus.PENDENTE,store.selectedDonation!!.status)
  s.enqueue(MockResponse().setBody(pageJson(donationJson.replace("PENDENTE","CONFIRMADA"))));store.loadDonations();await{!store.busy}
  assertEquals(DonationStatus.CONFIRMADA,store.donations!!.items.single().status)
  assertEquals(DonationStatus.CONFIRMADA,store.selectedDonation!!.status)
 }
 @Test fun atualizarListaSincronizaDetalheDeMovimentoSelecionado()=check(UserProfile.ADMINISTRADOR) {s,_,store,_->
  s.enqueue(MockResponse().setBody(movementJson));store.detailsMovement(4);await{!store.busy}
  s.enqueue(MockResponse().setBody(pageJson(movementJson.replace("PENDENTE","APROVADA"))));s.enqueue(MockResponse().setBody(summaryJson));store.loadMovements();await{!store.busy}
  assertEquals(MovementStatus.APROVADA,store.movements!!.items.single().status)
  assertEquals(MovementStatus.APROVADA,store.selectedMovement!!.status)
 }
 @Test fun unauthorizedEncerraSessao()=check {s,sessions,store,_->s.enqueue(MockResponse().setResponseCode(401));store.loadDonations();await{sessions.state is SessionState.SignedOut}}
 @Test fun forbiddenRevalidaIdentidade()=check {s,sessions,store,g->s.enqueue(MockResponse().setResponseCode(403));store.loadDonations();await{!store.busy};assertEquals(2,g.checks);assertTrue(sessions.state is SessionState.Authenticated);assertNotNull(store.message)}
 @Test fun notFoundRemoveDetalheAnterior()=check {s,_,store,_->s.enqueue(MockResponse().setBody(donationJson));store.detailsDonation(3);await{!store.busy};s.enqueue(MockResponse().setResponseCode(404));store.detailsDonation(3);await{!store.busy};assertNull(store.selectedDonation);assertTrue(store.message!!.contains("não encontrado"))}
 @Test fun conflitoReconsultaSemRepetirDecisao()=check {s,_,store,_->s.enqueue(MockResponse().setBody(donationJson));store.detailsDonation(3);await{!store.busy};s.enqueue(MockResponse().setResponseCode(409));s.enqueue(MockResponse().setBody(donationJson.replace("PENDENTE","CONFIRMADA")));store.actDonation(FinanceAction.CANCEL){fail()};await{!store.busy};assertEquals(DonationStatus.CONFIRMADA,store.selectedDonation!!.status);assertEquals(3,s.requestCount);assertTrue(store.message!!.contains("situação mudou"))}
 @Test fun acaoAtualizaResumoOficialEImpedeDuplicacao()=check(UserProfile.ADMINISTRADOR) {s,_,store,_->s.enqueue(MockResponse().setBody(movementJson));store.detailsMovement(4);await{!store.busy};s.enqueue(MockResponse().setBody(movementJson.replace("PENDENTE","APROVADA")).setBodyDelay(150,TimeUnit.MILLISECONDS));s.enqueue(MockResponse().setBody(summaryJson));var done=0;store.actMovement(FinanceAction.APPROVE){done++};store.actMovement(FinanceAction.APPROVE){done++};await{!store.busy};assertEquals(1,done);assertEquals(MovementStatus.APROVADA,store.selectedMovement!!.status);assertEquals("28.90",store.summary!!.saldoRealizado);assertEquals(3,s.requestCount);assertEquals("GET",s.takeRequest().method);assertEquals("PATCH",s.takeRequest().method);assertEquals("/api/ong/finance/resumo",s.takeRequest().path)}
 @Test fun voluntarioNaoEnviaAprovacao()=check(UserProfile.VOLUNTARIO) {s,_,store,_->s.enqueue(MockResponse().setBody(movementJson));store.detailsMovement(4);await{!store.busy};store.actMovement(FinanceAction.APPROVE){fail()};await{!store.busy};assertEquals(1,s.requestCount)}
 @Test fun perfisSemAcessoNaoEnviamConsultas() {for(p in listOf(UserProfile.VETERINARIO,UserProfile.ADMIN_PLATAFORMA))check(p){s,_,store,_->store.loadDonations();store.loadMovements();store.loadSummary();assertEquals(0,s.requestCount)}}
 @Test fun filtrosDaListaNaoAlteramResumoAlemDoPeriodo()=check(UserProfile.ADMINISTRADOR) {s,_,store,_->s.enqueue(MockResponse().setBody(pageJson(movementJson)));s.enqueue(MockResponse().setBody(summaryJson));store.setMovementFilters(MovementType.DESPESA,MovementStatus.PENDENTE,FinanceCategory.ALIMENTACAO,"2026-01-01","2026-11-24");await{!store.busy};assertTrue(s.takeRequest().path!!.contains("categoria=ALIMENTACAO"));assertEquals("/api/ong/finance/resumo?de=2026-01-01&ate=2026-11-24",s.takeRequest().path);assertEquals("28.90",store.summary!!.saldoRealizado)}
}
