package br.com.patasunidas.adocoes
import br.com.patasunidas.animais.*
import br.com.patasunidas.data.AuthApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
class AdoptionRealIntegrationTest {
 @Test fun cicloRealReservaEntregaRejeicaoECancelamentos()=runBlocking {
  val url=System.getenv("PATA_TEST_API");assumeTrue(url=="http://127.0.0.1:18081/");val auth=AuthApi(url!!)
  val admin=auth.login(System.getenv("PATA_TEST_EMAIL"),System.getenv("PATA_TEST_PASSWORD")).accessToken
  val user=auth.login(System.getenv("PATA_USER_EMAIL"),System.getenv("PATA_USER_PASSWORD")).accessToken
  val animals=AnimalApi(url);val api=AdoptionApi(url)
  suspend fun animal():Long {var a=animals.save(admin,null,AnimalForm(nome="Integração adoção",especie="CAO",sexo="MACHO",porte="MEDIO",dataEntrada="2026-11-22"));a=animals.status(admin,a.id,AnimalStatus.DISPONIVEL,a.versao);animals.publication(admin,a.id,true);return a.id}
  suspend fun request():AdoptionRecord=api.create(user,animal(),"Mensagem de integração Android")
  val r=request();assertEquals(AdoptionStatus.PENDENTE,r.status);assertTrue(api.list(user,false).items.any{it.id==r.id});assertEquals(r.id,api.details(user,false,r.id).id)
  assertTrue(api.list(admin,true,status=AdoptionStatus.PENDENTE,animalId=r.animalId).items.any{it.id==r.id})
  assertEquals(AdoptionStatus.APROVADA,api.act(admin,true,r.id,AdoptionAction.APPROVE).status);assertEquals(AnimalStatus.EM_PROCESSO_ADOCAO,animals.get(admin,r.animalId).situacao)
  val delivered=api.act(admin,true,r.id,AdoptionAction.DELIVER,confirmed=true);assertEquals(AdoptionStatus.CONCLUIDA,delivered.status);assertNotNull(delivered.delivered);assertEquals(AnimalStatus.ADOTADO,animals.get(admin,r.animalId).situacao)
  try{api.act(admin,true,r.id,AdoptionAction.DELIVER,confirmed=true);fail()}catch(e:AdoptionHttpException){assertEquals(409,e.status)}
  val rejected=request();assertEquals(AdoptionStatus.REJEITADA,api.act(admin,true,rejected.id,AdoptionAction.REJECT,"Justificativa acadêmica").status)
  val cancelled=request();assertEquals(AdoptionStatus.CANCELADA,api.act(user,false,cancelled.id,AdoptionAction.CANCEL).status)
  val released=request();api.act(admin,true,released.id,AdoptionAction.APPROVE);assertEquals(AdoptionStatus.CANCELADA,api.act(admin,true,released.id,AdoptionAction.CANCEL_APPROVAL,"Reserva liberada no teste").status);assertEquals(AnimalStatus.DISPONIVEL,animals.get(admin,released.animalId).situacao)
 }
}
