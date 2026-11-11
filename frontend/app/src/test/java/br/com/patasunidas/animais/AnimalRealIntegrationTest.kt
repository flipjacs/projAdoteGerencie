package br.com.patasunidas.animais

import br.com.patasunidas.data.AuthApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class AnimalRealIntegrationTest {
 @Test fun `cliente conecta ao backend isolado e gerencia animal`() = runBlocking {
  val url=System.getenv("PATA_TEST_API"); val email=System.getenv("PATA_TEST_EMAIL"); val password=System.getenv("PATA_TEST_PASSWORD")
  assumeTrue(url=="http://127.0.0.1:18081/" && email!=null && password!=null)
  val auth=AuthApi(url!!); val session=auth.login(email!!,password!!); val identity=auth.me(session.accessToken); assertNotNull(identity.ongId)
  val api=AnimalApi(url); val input=AnimalForm(nome="Animal teste Android",especie="CAO",sexo="FEMEA",porte="MEDIO",dataEntrada="2025-01-01",historia="História mantida")
  val created=api.save(session.accessToken,null,input); assertEquals("História mantida",created.form.historia)
  assertTrue(api.list(session.accessToken,0,true).items.any { it.id==created.id }); val fetched=api.get(session.accessToken,created.id)
  val edited=api.save(session.accessToken,created.id,fetched.form.copy(nome="Animal atualizado"),fetched.versao);assertEquals("História mantida",edited.form.historia)
  assertTrue(edited.versao>fetched.versao)
  try { api.save(session.accessToken,created.id,fetched.form,fetched.versao); fail("versão antiga aceita") } catch(e:AnimalHttpException) { assertEquals(409,e.status);assertTrue(e.versionConflict) }
  val current=api.get(session.accessToken,created.id);assertEquals("Animal atualizado",current.form.nome)
  val reviewed=api.save(session.accessToken,created.id,current.form.copy(nome="Animal revisado"),current.versao)
  try { api.status(session.accessToken,created.id,AnimalStatus.DISPONIVEL,current.versao);fail("situação antiga aceita") } catch(e:AnimalHttpException) { assertEquals(409,e.status);assertTrue(e.versionConflict) }
  assertEquals(AnimalStatus.DISPONIVEL,api.status(session.accessToken,created.id,AnimalStatus.DISPONIVEL,reviewed.versao).situacao)
  assertFalse(api.deactivate(session.accessToken,created.id).ativo)
 }
}
