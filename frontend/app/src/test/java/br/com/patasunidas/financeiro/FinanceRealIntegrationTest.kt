package br.com.patasunidas.financeiro

import br.com.patasunidas.data.AuthApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in against the isolated K6.1 fixture only; never production or Web data. */
class FinanceRealIntegrationTest {
 @Test fun intencaoRecebimentoAprovacaoPagamentoResumoECancelamento()=runBlocking {
  val url=System.getenv("K62_API");assumeTrue(url=="http://127.0.0.1:18082/")
  val auth=AuthApi(url!!)
  val admin=auth.login(System.getenv("K62_ADMIN_EMAIL"),System.getenv("K62_ADMIN_PASSWORD")).accessToken
  val donor=auth.login(System.getenv("K62_DONOR_EMAIL"),System.getenv("K62_DONOR_PASSWORD")).accessToken
  val ongId=auth.me(admin).ongId!!;val api=FinanceApi(url);val before=api.summary(admin)
  val donation=api.createDonation(donor,ongId,"0,30","Integração Android K6.2")
  assertEquals(DonationStatus.PENDENTE,donation.status);assertNull(donation.donor)
  assertTrue(api.donations(donor,false).items.any {it.id==donation.id})
  assertEquals(before.receitasRealizadas,api.summary(admin).receitasRealizadas)
  val confirmed=api.actDonation(admin,true,donation.id,FinanceAction.CONFIRM_DONATION)
  assertEquals(DonationStatus.CONFIRMADA,confirmed.status);assertTrue(confirmed.history.any {it.operation=="RECEBIMENTO_CONFIRMADO"})
  val expense=api.createMovement(admin,MovementType.DESPESA,FinanceCategory.ALIMENTACAO,"Integração Android K6.2","0,10")
  val approved=api.actMovement(admin,expense.id,FinanceAction.APPROVE)
  assertEquals(MovementStatus.APROVADA,approved.status)
  assertEquals(before.despesasRealizadas,api.summary(admin).despesasRealizadas)
  val paid=api.actMovement(admin,expense.id,FinanceAction.PAY)
  assertEquals(MovementStatus.REALIZADA,paid.status);assertNotNull(paid.approvedAt);assertNotNull(paid.confirmedAt)
  assertTrue(paid.history.any {it.operation=="DESPESA_APROVADA"});assertTrue(paid.history.any {it.operation=="PAGAMENTO_CONFIRMADO"})
  val after=api.summary(admin)
  assertEquals(before.receitasRealizadas.toBigDecimal()+"0.30".toBigDecimal(),after.receitasRealizadas.toBigDecimal())
  assertEquals(before.despesasRealizadas.toBigDecimal()+"0.10".toBigDecimal(),after.despesasRealizadas.toBigDecimal())
  assertEquals(before.saldoRealizado.toBigDecimal()+"0.20".toBigDecimal(),after.saldoRealizado.toBigDecimal())
  try{api.actMovement(admin,expense.id,FinanceAction.PAY);fail()}catch(e:FinanceHttpException){assertEquals(409,e.status)}
  val cancelled=api.createDonation(donor,ongId,"1","")
  assertEquals(DonationStatus.CANCELADA,api.actDonation(donor,false,cancelled.id,FinanceAction.CANCEL,"Teste de cancelamento").status)
  assertEquals(after,api.summary(admin))
 }
}
