package br.com.patasunidas.financeiro

import br.com.patasunidas.data.UserProfile
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

internal val donationJson="""{"id":3,"ong":{"id":1,"nome":"ONG"},"valor":"36.15","status":"PENDENTE","mensagem":null,"createdAt":"2026-11-24T12:00:00Z","updatedAt":"2026-11-24T12:00:00Z","versao":0,"historico":[{"operacao":"REGISTRADA","anterior":null,"posterior":"PENDENTE","justificativa":null,"ocorridoEm":"2026-11-24T12:00:00Z"}]}"""
internal val movementJson="""{"id":4,"ongId":1,"tipo":"DESPESA","categoria":"ALIMENTACAO","descricao":"Ração","valor":"7.25","status":"PENDENTE","origem":{"tipo":"MANUAL","id":null},"registradoPor":{"id":1,"nome":"Pessoa"},"createdAt":"2026-11-24T12:00:00Z","updatedAt":"2026-11-24T12:00:00Z","versao":0,"historico":[]}"""
internal val summaryJson="""{"receitasRealizadas":"36.15","despesasRealizadas":"7.25","receitasPendentes":"700.30","despesasPendentes":"0.00","despesasAprovadas":"0.00","saldoRealizado":"28.90"}"""
internal fun pageJson(record:String)="""{"content":[$record],"number":0,"size":20,"totalElements":1,"totalPages":1}"""
class FinanceApiTest {
 private fun check(block:suspend (MockWebServer,FinanceApi)->Unit)=runBlocking {val s=MockWebServer();s.start();try{block(s,FinanceApi(s.url("/").toString()))}finally{s.shutdown()}}
 @Test fun dinheiroDecimalExatoSemArredondamento() {
  assertEquals("36.15",normalizeMoney("36,15"));assertEquals("10.00",normalizeMoney("10"));assertEquals("9999999999.99",normalizeMoney("9999999999,99"));assertEquals("R$ 36,15",formatMoney("36.15"));assertEquals("R$ 20.000.000.000,10",formatMoney("20000000000.10"));assertEquals("-R$ 0,10",formatMoney("-0.10"))
  for(v in listOf("0","-1","01.25","1.001","1.000","1e2","1,000.00","10000000000.00","NaN"))try{normalizeMoney(v);fail(v)}catch(_:IllegalArgumentException){}
  assertEquals("0.30",("0.10".toBigDecimal()+"0.20".toBigDecimal()).toPlainString())
 }
 @Test fun criacaoDoacaoEMovimentosEnviamStringsESomenteCamposAutorizados()=check {s,api->
  s.enqueue(MockResponse().setResponseCode(201).setBody(donationJson));val d=api.createDonation("jwt",1,"36,15","Oi");assertEquals("36.15",d.value);assertNull(d.history[0].actor)
  var q=s.takeRequest();assertEquals("/api/donations",q.path);assertEquals("POST",q.method);assertEquals("Bearer jwt",q.getHeader("Authorization"));var j=JSONObject(q.body.readUtf8());assertEquals(setOf("ongId","valor","mensagem"),j.keys().asSequence().toSet());assertTrue(j.get("valor") is String);assertEquals("36.15",j.getString("valor"))
  for(t in MovementType.entries){s.enqueue(MockResponse().setResponseCode(201).setBody(movementJson));api.createMovement("jwt",t,FinanceCategory.forType(t).first(),"  Ração  ","7,25");q=s.takeRequest();assertEquals("/api/ong/finance/movimentacoes",q.path);j=JSONObject(q.body.readUtf8());assertEquals(setOf("tipo","categoria","descricao","valor"),j.keys().asSequence().toSet());assertEquals("7.25",j.getString("valor"));assertEquals("Ração",j.getString("descricao"))}
  try{api.createMovement("jwt",MovementType.RECEITA,FinanceCategory.DOACAO,"Inválida","1");fail()}catch(_:IllegalArgumentException){};assertEquals(3,s.requestCount)
 }
 @Test fun listagensFiltrosDetalhesEResumoUsamContratoKotlin()=check {s,api->
  s.enqueue(MockResponse().setBody(pageJson(donationJson)));assertEquals(1L,api.donations("t",false,0,DonationStatus.PENDENTE,"2026-01-01").total);assertEquals("/api/me/donations?page=0&size=20",s.takeRequest().path)
  s.enqueue(MockResponse().setBody(pageJson(donationJson)));api.donations("t",true,2,DonationStatus.CONFIRMADA,"2026-01-01","2026-11-24",100);assertEquals("/api/ong/donations?page=2&size=100&status=CONFIRMADA&de=2026-01-01&ate=2026-11-24",s.takeRequest().path)
  for(internal in listOf(false,true)){s.enqueue(MockResponse().setBody(donationJson));api.donationDetails("t",internal,3);assertEquals("/api/${if(internal) "ong" else "me"}/donations/3",s.takeRequest().path)}
  s.enqueue(MockResponse().setBody(pageJson(movementJson)));api.movements("t",3,MovementType.DESPESA,MovementStatus.APROVADA,FinanceCategory.ALIMENTACAO,"2026-01-01","2026-11-24");assertEquals("/api/ong/finance/movimentacoes?page=3&size=20&tipo=DESPESA&status=APROVADA&categoria=ALIMENTACAO&de=2026-01-01&ate=2026-11-24",s.takeRequest().path)
  s.enqueue(MockResponse().setBody(movementJson));api.movementDetails("t",4);assertEquals("/api/ong/finance/movimentacoes/4",s.takeRequest().path)
  s.enqueue(MockResponse().setBody(summaryJson));assertEquals("28.90",api.summary("t","2026-01-01","2026-11-24").saldoRealizado);assertEquals("/api/ong/finance/resumo?de=2026-01-01&ate=2026-11-24",s.takeRequest().path)
 }
 @Test fun todasTransicoesUsamPatchJustificativaOpcionalESemVersao()=check {s,api->
  for((internal,action) in listOf(false to FinanceAction.CANCEL,true to FinanceAction.CANCEL,true to FinanceAction.CONFIRM_DONATION)){s.enqueue(MockResponse().setBody(donationJson));api.actDonation("t",internal,3,action);val q=s.takeRequest();assertEquals("PATCH",q.method);assertEquals("/api/${if(internal) "ong" else "me"}/donations/3/${action.path}",q.path);assertEquals(0,JSONObject(q.body.readUtf8()).length())}
  for(a in listOf(FinanceAction.APPROVE,FinanceAction.RECEIVE,FinanceAction.PAY,FinanceAction.CANCEL)){s.enqueue(MockResponse().setBody(movementJson));api.actMovement("t",4,a,"Motivo");val q=s.takeRequest();assertEquals("PATCH",q.method);assertEquals("/api/ong/finance/movimentacoes/4/${a.path}",q.path);val j=JSONObject(q.body.readUtf8());assertEquals(if(a==FinanceAction.CANCEL)1 else 0,j.length());if(a==FinanceAction.CANCEL)assertEquals("Motivo",j.getString("justificativa"))}
 }
 @Test fun errosNaoRepetemEscrita()=check {s,api->for(code in listOf(400,401,403,404,409)){s.enqueue(MockResponse().setResponseCode(code).setBody("""{"code":"FINANCEIRO_ERRO"}"""));try{api.createDonation("t",1,"1","");fail()}catch(e:FinanceHttpException){assertEquals(code,e.status);assertEquals("FINANCEIRO_ERRO",e.code)}};assertEquals(5,s.requestCount)}
 @Test fun cincoPerfisNaoConcedemAprovacaoAoVoluntario()=check {s,api->s.enqueue(MockResponse().setBody(donationJson));val d=api.donationDetails("t",false,3);s.enqueue(MockResponse().setBody(movementJson));val m=api.movementDetails("t",4);for(p in UserProfile.entries){assertEquals(p in listOf(UserProfile.ADMINISTRADOR,UserProfile.VOLUNTARIO),canManageFinance(p));assertEquals(p==UserProfile.ADMINISTRADOR,movementActions(m,p).isNotEmpty());assertEquals(p in listOf(UserProfile.USUARIO,UserProfile.ADMINISTRADOR),donationActions(d,p).isNotEmpty());assertTrue(movementActions(m.copy(status=MovementStatus.REALIZADA),p).isEmpty());assertTrue(movementActions(m.copy(origin="DOACAO"),p).isEmpty())};assertEquals(listOf(FinanceAction.PAY,FinanceAction.CANCEL),movementActions(m.copy(status=MovementStatus.APROVADA),UserProfile.ADMINISTRADOR))}
}
