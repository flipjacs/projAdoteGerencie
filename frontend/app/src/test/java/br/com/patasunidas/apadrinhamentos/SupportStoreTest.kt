package br.com.patasunidas.apadrinhamentos
import br.com.patasunidas.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SupportStoreTest {
 private class Gateway(var identity:UserIdentity):AuthGateway {var checks=0;override suspend fun login(email:String,senha:String)=AuthSession("token",System.currentTimeMillis()+60000);override suspend fun me(token:String):UserIdentity{checks++;return identity}}
 private suspend fun await(c:()->Boolean){withTimeout(4000){while(!c())delay(10)}}
 private fun check(p:UserProfile=UserProfile.USUARIO,block:suspend(MockWebServer,SessionController,SupportStore,Gateway)->Unit)=runBlocking{Dispatchers.setMain(UnconfinedTestDispatcher());val s=MockWebServer();s.start();var store:SupportStore?=null;try{val g=Gateway(UserIdentity(1,"Pessoa","x@test",p,if(p==UserProfile.USUARIO)null else 1));val sessions=SessionController(g);sessions.login("x","y");val auth=sessions.state as SessionState.Authenticated;store=SupportStore(SupportApi(s.url("/").toString()),sessions,auth.session,auth.identity);block(s,sessions,store,g)}finally{store?.close();s.shutdown();Dispatchers.resetMain()}}
 @Test fun retrySameSponsorReloadsInstallmentsAfterFailure()=check{s,_,store,_->
  s.enqueue(MockResponse().setBody(sponsorJson));store.detailsSponsor(3);await{!store.busy}
  s.enqueue(MockResponse().setResponseCode(500));store.loadInstallments();await{!store.busy}
  assertNull(store.installments);assertNotNull(store.message)
  s.enqueue(MockResponse().setBody(sponsorJson));s.enqueue(MockResponse().setBody(supportPage(installmentJson)))
  store.reloadSponsor(3);await{!store.busy}
  assertNotNull("Retry of the same sponsor must recover its installments",store.installments)
  assertEquals(7L,store.installments!!.items.single().id)
  val requests=(1..4).map{s.takeRequest(1,TimeUnit.SECONDS)!!}
  assertEquals(listOf("/api/me/sponsorships/3","/api/me/sponsorships/3/parcelas?page=0&size=15","/api/me/sponsorships/3","/api/me/sponsorships/3/parcelas?page=0&size=15"),requests.map{it.path})
  assertTrue(requests.all{it.method=="GET"})
 }
 @Test fun duplicateCreationBlockedAndNoAutomaticPayment()=check{s,_,store,_->s.enqueue(MockResponse().setBody(sponsorJson).setBodyDelay(150,TimeUnit.MILLISECONDS));var done=0;store.createSponsor(2,SponsorMode.MENSAL,"25.50"){done++};store.createSponsor(2,SponsorMode.MENSAL,"25.50"){done++};await{!store.busy};assertEquals(1,done);assertEquals(1,s.requestCount);assertEquals(InstallmentStatus.PENDENTE,store.selectedSponsor!!.firstInstallment!!.status);assertTrue(store.message!!.contains("automático"))}
 @Test fun conflictFetchesOnlyAndDoesNotCallFormCompletion()=check{s,_,store,_->s.enqueue(MockResponse().setBody(sponsorJson));store.detailsSponsor(3);await{!store.busy};s.enqueue(MockResponse().setResponseCode(409));s.enqueue(MockResponse().setBody(sponsorJson.replace("ATIVO","ENCERRADO")));store.cancelSponsor{fail()};await{!store.busy};assertEquals(SponsorStatus.ENCERRADO,store.selectedSponsor!!.status);assertEquals(listOf("GET","PATCH","GET"),(1..3).map{s.takeRequest().method});assertTrue(store.message!!.contains("mudou"))}
 @Test fun lateResultsAfterLogoutOrIdentityChangeAreDiscarded()=check{s,sessions,store,g->s.enqueue(MockResponse().setBody(supportPage(sponsorJson)).setBodyDelay(250,TimeUnit.MILLISECONDS));store.loadSponsors();assertNotNull(s.takeRequest(2,TimeUnit.SECONDS));sessions.logout();g.identity=g.identity.copy(id=2);sessions.login("x","y");delay(400);assertNull(store.sponsors);assertFalse(store.busy)}
 @Test fun statusErrorsAndRevalidation()=check{s,sessions,store,g->for(code in listOf(400,403,404,429)){s.enqueue(MockResponse().setResponseCode(code));store.loadSponsors();await{!store.busy};assertNotNull(store.message)};assertEquals(2,g.checks);s.enqueue(MockResponse().setResponseCode(401));store.loadSponsors();await{!store.busy};assertTrue(sessions.state is SessionState.SignedOut);assertNull(store.sponsors)}
 @Test fun profilesEnforceOperationalPermissions(){for(p in UserProfile.entries)check(p){s,_,store,_->if(p!=UserProfile.USUARIO){store.createSponsor(2,SponsorMode.UNICO,"1"){fail()};await{!store.busy}};if(p!=UserProfile.ADMINISTRADOR){store.loadReport();await{!store.busy};store.createDemonstrative(DemonstrativeForm("x","","2026-01-01","2026-02-01")){fail()};await{!store.busy}};if(p in listOf(UserProfile.VETERINARIO,UserProfile.ADMIN_PLATAFORMA)){store.loadSponsors();store.loadDemonstratives()};assertEquals(0,s.requestCount)}}
 @Test fun changingOngDiscardsPendingResponseAndCloseClears()=check(UserProfile.ADMINISTRADOR){s,sessions,store,g->s.enqueue(MockResponse().setBody(supportPage(sponsorJson)).setBodyDelay(250,TimeUnit.MILLISECONDS));store.loadSponsors();s.takeRequest();g.identity=g.identity.copy(ongId=2);sessions.operationForbidden();delay(400);assertNull(store.sponsors);store.close();assertFalse(store.busy)}
 @Test fun confirmedInstallmentRefreshesOfficialReportOnly()=check(UserProfile.ADMINISTRADOR){s,_,store,_->s.enqueue(MockResponse().setBody(sponsorJson));store.detailsSponsor(3);await{!store.busy};s.enqueue(MockResponse().setBody(installmentJson.replace("PENDENTE","CONFIRMADA")));s.enqueue(MockResponse().setBody(sponsorJson));s.enqueue(MockResponse().setBody(reportJson));store.confirmInstallment(7){};await{!store.busy};assertEquals("25.50",store.report!!.saldoRealizado);val requests=(1..4).map{s.takeRequest()};assertEquals(1,requests.count{it.method=="PATCH"});assertFalse(requests.any{it.path!!.contains("movimentacoes")})}
 @Test fun publicStoreUsesNoTokenPreservesSnapshotAndRemovesUnavailableDetails()=runBlocking {
  Dispatchers.setMain(UnconfinedTestDispatcher());val s=MockWebServer();s.start();val store=PublicSupportStore(SupportApi(s.url("/").toString()),1)
  try{s.enqueue(MockResponse().setBody(supportPage(publicJson)));store.load();await{!store.busy};assertEquals("99999999999.99",store.records!!.items.single().data.resultadoLiquido);assertNull(s.takeRequest().getHeader("Authorization"));s.enqueue(MockResponse().setBody(publicJson));store.details(4);await{!store.busy};assertEquals("99999999999.99",store.selected!!.data.resultadoLiquido);s.enqueue(MockResponse().setResponseCode(404));store.details(4);await{!store.busy};assertNull(store.selected);assertNull(store.records)}finally{store.close();s.shutdown();Dispatchers.resetMain()}
 }

}
