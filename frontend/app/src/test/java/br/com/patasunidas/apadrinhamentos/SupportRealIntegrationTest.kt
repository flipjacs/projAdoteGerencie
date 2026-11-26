package br.com.patasunidas.apadrinhamentos

import br.com.patasunidas.data.AuthApi
import br.com.patasunidas.financeiro.FinanceApi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/** Opt-in only: existing K8.1 isolated API18084/MySQL, never a demo/production URL. */
class SupportRealIntegrationTest {
 @Test fun nineteenEndpointsAgainstIsolatedMysql()=runBlocking {
  assumeTrue(System.getenv("PATAS_K82_REAL")=="1")
  val privateDir=File(File("/tmp/patas-k81-current").readText().trim())
  val accounts=JSONObject(File(privateDir,"accounts.json").readText())
  val fixture=JSONObject(File(privateDir,"k82-client-fixture.json").readText())
  val api=SupportApi("http://127.0.0.1:18084/")
  val finance=FinanceApi("http://127.0.0.1:18084/")
  val auth=AuthApi("http://127.0.0.1:18084/")
  suspend fun token(role:String):String {val a=accounts.getJSONObject(role);return auth.login(a.getString("email"),a.getString("senha")).accessToken}
  val admin=token("admin_a");val owner=token("user_b");val other=token("user_a");val volunteer=token("volunteer")
  val aid=fixture.getLong("animalId");val oid=fixture.getInt("ongId")
  val before=finance.summary(admin)
  val created=api.createSponsor(owner,aid,SponsorMode.UNICO,"0,01")
  val id=created.id;val part=created.firstInstallment!!
  assertEquals(InstallmentStatus.PENDENTE,part.status)
  assertTrue(api.sponsors(owner,false).items.any{it.id==id})
  assertNull(api.sponsorDetails(owner,false,id).sponsor)
  assertTrue(api.installments(owner,false,id).items.any{it.id==part.id})
  assertTrue(api.sponsors(admin,true,animalId=aid,mode=SponsorMode.UNICO).items.any{it.id==id})
  assertNotNull(api.sponsorDetails(admin,true,id).sponsor)
  assertEquals(part.id,api.installments(admin,true,id).items.single().id)
  assertEquals(InstallmentStatus.CONFIRMADA,api.confirmInstallment(admin,id,part.id).status)
  assertEquals(before.receitasRealizadas.toBigDecimal()+BigDecimal("0.01"),finance.summary(admin).receitasRealizadas.toBigDecimal())
  try{api.confirmInstallment(admin,id,part.id);fail("Recebimento duplicado deveria conflitar")}catch(e:SupportHttpException){assertEquals(409,e.status)}
  try{api.sponsorDetails(other,false,id);fail("Outro titular deveria ser ocultado")}catch(e:SupportHttpException){assertEquals(404,e.status)}
  try{api.confirmInstallment(volunteer,id,part.id);fail("Voluntário não confirma")}catch(e:SupportHttpException){assertEquals(403,e.status)}
  assertEquals(SponsorStatus.ENCERRADO,api.cancelSponsor(owner,id).status)
  assertEquals(InstallmentStatus.CONFIRMADA,api.installments(owner,false,id).items.single().status)
  val monthly=api.createSponsor(owner,aid,SponsorMode.MENSAL,"1.00")
  assertEquals(SponsorClosureReason.ENCERRAMENTO_ONG,api.closeSponsor(admin,monthly.id,"Validação de contratos Android").closureReason)
  val official=api.report(admin,"2020-01-01","9999-12-31")
  assertTrue(official.receitasPorCategoria.containsKey("APADRINHAMENTO"))
  val form=DemonstrativeForm("Validação cliente K8.2","Dados exclusivamente sintéticos","2020-01-01","9999-12-31")
  val draft=api.createDemonstrative(admin,form);assertEquals(DemonstrativeStatus.RASCUNHO,draft.status)
  assertTrue(api.demonstratives(admin).items.any{it.id==draft.id})
  assertEquals(draft.id,api.demonstrativeDetails(admin,draft.id).id)
  assertEquals("Revisão cliente K8.2",api.editDemonstrative(admin,draft.id,form.copy(title="Revisão cliente K8.2")).title)
  val published=api.setPublished(admin,draft.id,true)
  assertEquals(DemonstrativeStatus.PUBLICADO,published.status)
  assertTrue(api.publicDemonstratives(oid).items.any{it.id==draft.id})
  assertEquals(published.data,api.publicDetails(oid,draft.id).data)
  assertEquals(DemonstrativeStatus.RASCUNHO,api.setPublished(admin,draft.id,false).status)
  try{api.publicDetails(oid,draft.id);fail("Rascunho não deve ser público")}catch(e:SupportHttpException){assertEquals(404,e.status)}
  val evidence=System.getenv("PATAS_K82_EVIDENCE")
  if(evidence!=null)File(evidence).writeText(JSONObject().put("verdict","PASS").put("apiPort",18084).put("database","patas_k81").put("endpoints",19).put("duplicateConfirmation",409).put("otherOwner",404).put("volunteerConfirmation",403).put("publicAfterUnpublish",404).toString(2)+"\n")
 }
}
