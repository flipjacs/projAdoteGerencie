package br.com.patasunidas.apadrinhamentos

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

internal val installmentJson="""{"id":7,"numero":1,"valor":"25.50","vencimento":"2026-11-01","status":"PENDENTE","vencida":true,"confirmadoEm":null}"""
internal val sponsorJson="""{"id":3,"animal":{"id":2,"nome":"Lua"},"ong":{"id":1,"nome":"ONG"},"modalidade":"MENSAL","valor":"25.50","status":"ATIVO","dataInicio":"2026-11-01","dataEncerramento":null,"motivoEncerramento":null,"justificativaEncerramento":null,"proximoVencimento":"2026-11-01","criadoEm":"2026-11-01T00:00:00Z","atualizadoEm":"2026-11-01T00:00:00Z","versao":0,"primeiraParcela":$installmentJson}"""
internal val dataJson="""{"receitasRealizadas":"99999999999.99","despesasRealizadas":"0.00","resultadoLiquido":"99999999999.99","receitasPorCategoria":{"APADRINHAMENTO":"25.50"},"despesasPorCategoria":{},"quantidadeMovimentacoes":1}"""
internal val publicJson="""{"id":4,"ong":{"id":1,"nome":"ONG"},"titulo":"Prestação","descricao":null,"periodoInicio":"2026-11-01","periodoFim":"2026-11-30","publicadoEm":null,"dados":$dataJson}"""
internal val demonstrativeJson=publicJson.dropLast(1)+""","status":"RASCUNHO","publicadoPor":null,"despublicadoEm":null,"createdAt":"2026-11-01T00:00:00Z","updatedAt":"2026-11-01T00:00:00Z"}"""
internal val reportJson="""{"periodo":{"de":null,"ate":null},"semanticaPeriodo":"Oficial","receitasRealizadas":"25.50","despesasRealizadas":"0.00","receitasPendentes":"0.00","despesasPendentes":"0.00","despesasAprovadas":"0.00","saldoRealizado":"25.50","quantidadeMovimentacoes":1,"receitasPorCategoria":{"APADRINHAMENTO":"25.50"},"despesasPorCategoria":{}}"""
internal fun supportPage(j:String)="""{"content":[$j],"number":0,"size":15,"totalElements":1,"totalPages":1}"""
class SupportApiTest {
 private fun check(block:suspend(MockWebServer,SupportApi)->Unit)=runBlocking {val s=MockWebServer();s.start();try{block(s,SupportApi(s.url("/").toString()))}finally{s.shutdown()}}
 @Test fun nineteenEndpointsAndStrictPayloads()=check{s,a->
  for(mode in SponsorMode.entries){s.enqueue(MockResponse().setBody(sponsorJson));assertEquals("25.50",a.createSponsor("jwt",2,mode,"25,50").value);val q=s.takeRequest();assertEquals("/api/sponsorships",q.path);assertEquals("POST",q.method);val j=JSONObject(q.body.readUtf8());assertEquals(setOf("animalId","modalidade","valor"),j.keys().asSequence().toSet());assertTrue(j.get("valor") is String);assertEquals(mode.name,j.getString("modalidade"))}
  for(internal in listOf(false,true)){
   val prefix="/api/${if(internal) "ong" else "me"}/sponsorships"
   s.enqueue(MockResponse().setBody(supportPage(sponsorJson)));assertEquals(15,a.sponsors("t",internal).size);assertEquals("$prefix?page=0&size=15",s.takeRequest().path)
   s.enqueue(MockResponse().setBody(sponsorJson));a.sponsorDetails("t",internal,3);assertEquals("$prefix/3",s.takeRequest().path)
   s.enqueue(MockResponse().setBody(supportPage(installmentJson)));assertTrue(a.installments("t",internal,3).items.single().overdue);assertEquals("$prefix/3/parcelas?page=0&size=15",s.takeRequest().path)
  }
  for((path,action) in listOf("/api/me/sponsorships/3/cancelar" to suspend{a.cancelSponsor("t",3)},"/api/ong/sponsorships/3/encerrar" to suspend{a.closeSponsor("t",3,"Motivo")})){s.enqueue(MockResponse().setBody(sponsorJson));action();val q=s.takeRequest();assertEquals(path,q.path);assertEquals("PATCH",q.method)}
  s.enqueue(MockResponse().setBody(installmentJson));a.confirmInstallment("t",3,7);assertEquals("/api/ong/sponsorships/3/parcelas/7/confirmar",s.takeRequest().path)
  s.enqueue(MockResponse().setBody(reportJson));assertEquals("25.50",a.report("t").saldoRealizado);assertEquals("/api/ong/relatorios/financeiro",s.takeRequest().path)
  val f=DemonstrativeForm("Prestação","","2026-11-01","2026-11-30")
  s.enqueue(MockResponse().setBody(demonstrativeJson));a.createDemonstrative("t",f);assertEquals("POST",s.takeRequest().method)
  s.enqueue(MockResponse().setBody(supportPage(demonstrativeJson)));a.demonstratives("t");assertEquals("/api/ong/transparencia?page=0&size=15",s.takeRequest().path)
  s.enqueue(MockResponse().setBody(demonstrativeJson));a.demonstrativeDetails("t",4);assertEquals("/api/ong/transparencia/4",s.takeRequest().path)
  s.enqueue(MockResponse().setBody(demonstrativeJson));a.editDemonstrative("t",4,f);val edit=s.takeRequest();assertEquals("PATCH",edit.method);assertEquals(setOf("titulo","descricao","periodoInicio","periodoFim"),JSONObject(edit.body.readUtf8()).keys().asSequence().toSet())
  for(p in listOf(true,false)){s.enqueue(MockResponse().setBody(demonstrativeJson));a.setPublished("t",4,p);val q=s.takeRequest();assertEquals("/api/ong/transparencia/4/${if(p)"publicar" else "despublicar"}",q.path);assertEquals("{}",q.body.readUtf8())}
  s.enqueue(MockResponse().setBody(supportPage(publicJson)));a.publicDemonstratives(1);assertNull(s.takeRequest().getHeader("Authorization"))
  s.enqueue(MockResponse().setBody(publicJson.dropLast(1)+""","padrinho":{"email":"secret"},"status":"PUBLICADO"}"""));val public=a.publicDetails(1,4);assertEquals("99999999999.99",public.data.resultadoLiquido);assertEquals(8,PublicDemonstrativeRecord::class.java.declaredFields.count{!it.isSynthetic&&!java.lang.reflect.Modifier.isStatic(it.modifiers)});assertNull(s.takeRequest().getHeader("Authorization"))
 }
 @Test fun onlyBackendFiltersAndPagination()=check{s,a->
  s.enqueue(MockResponse().setBody(supportPage(sponsorJson)));a.sponsors("t",true,2,SponsorStatus.ATIVO,2,SponsorMode.UNICO,100);assertEquals("/api/ong/sponsorships?page=2&size=100&status=ATIVO&animalId=2&modalidade=UNICO",s.takeRequest().path)
  s.enqueue(MockResponse().setBody(supportPage(installmentJson)));a.installments("t",true,3,0,InstallmentStatus.PENDENTE,true,"2026-01-01","2026-12-31");assertEquals("/api/ong/sponsorships/3/parcelas?page=0&size=15&status=PENDENTE&vencidas=true&de=2026-01-01&ate=2026-12-31",s.takeRequest().path)
  for(size in listOf(0,101))try{a.sponsors("t",false,size=size);fail()}catch(_:IllegalArgumentException){}
 }
 @Test fun validatesUnicodeDatesMoneyAndAutomaticFinanceOrigin(){assertNull(DemonstrativeForm("🐾".repeat(120),"","2026-01-01","2026-01-01").validate());assertNotNull(DemonstrativeForm("🐾".repeat(121),"","2026-01-01","2026-01-01").validate());assertNotNull(DemonstrativeForm("x","","2026-02-30","2026-03-01").validate());assertEquals(3,InstallmentStatus.entries.size);assertFalse(br.com.patasunidas.financeiro.FinanceCategory.APADRINHAMENTO in br.com.patasunidas.financeiro.FinanceCategory.forType(br.com.patasunidas.financeiro.MovementType.RECEITA))}
 @Test fun domainErrorsNeverRepeatWrites()=check{s,a->for(code in listOf(400,401,403,404,409,429)){s.enqueue(MockResponse().setResponseCode(code).setBody("""{"code":"DOMAIN"}"""));try{a.createSponsor("t",2,SponsorMode.MENSAL,"1");fail()}catch(e:SupportHttpException){assertEquals(code,e.status);assertEquals("DOMAIN",e.code)}};assertEquals(6,s.requestCount)}
 @Test fun brokenConnectionRecoversOnlyReadsNeverWrites() {
  check{s,a->
   // A successful exchange makes the next connection reusable, as in the idle-pool failure.
   s.enqueue(MockResponse().setBody(supportPage(sponsorJson)))
   a.sponsors("transport-test",false)
   s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
   s.enqueue(MockResponse().setBody(supportPage(sponsorJson)))
   assertEquals(3L,a.sponsors("transport-test",false).items.single().id)
   assertEquals(3,s.requestCount)
  }
  check{s,a->
   s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
   s.enqueue(MockResponse().setBody(sponsorJson))
   try{a.createSponsor("transport-test",2,SponsorMode.UNICO,"1");fail("Uma escrita incerta deve falhar sem repetição.")}catch(_:java.io.IOException){}
   assertEquals(1,s.requestCount)
  }
  check{s,a->
   // Also protect an uncertain write on the same reusable pool used by reads.
   s.enqueue(MockResponse().setBody(supportPage(sponsorJson)))
   a.sponsors("transport-test",false)
   s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
   s.enqueue(MockResponse().setBody(sponsorJson))
   try{a.createSponsor("transport-test",2,SponsorMode.UNICO,"1");fail("Uma escrita enviada não pode ser repetida.")}catch(_:java.io.IOException){}
   assertEquals(2,s.requestCount)
  }
 }

}
