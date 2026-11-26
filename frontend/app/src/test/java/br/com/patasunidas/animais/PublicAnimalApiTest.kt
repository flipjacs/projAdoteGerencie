package br.com.patasunidas.animais

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class PublicAnimalApiTest {
 private val publicAnimal="""{"id":9,"nome":"Luna","especie":"CAO","sexo":"FEMEA","porte":"MEDIO","descricao":null,"fotos":[{"id":4,"principal":true,"url":"/api/animais/9/fotos/4"}]}"""
 @Test fun filtrosEPaginacaoNaoEnviamCredencial() = runBlocking {
  val server=MockWebServer();server.start()
  try {
   server.enqueue(MockResponse().setBody("""{"content":[$publicAnimal],"number":2,"totalPages":4,"totalElements":61}"""))
   val result=AnimalApi(server.url("/").toString()).publicList(2,"CAO","FEMEA","MEDIO")
   val r=server.takeRequest();assertNull(r.getHeader("Authorization"));assertEquals("/api/animais?page=2&size=20&especie=CAO&sexo=FEMEA&porte=MEDIO",r.path)
   assertEquals(2,result.page);assertEquals(4,result.pages);assertEquals(61L,result.total);assertEquals("Luna",result.items.single().nome);assertNull(result.items.single().descricao)
  } finally {server.shutdown()}
 }
 @Test fun detalhesInterpretamOngEFotos() = runBlocking {
  val server=MockWebServer();server.start()
  try {server.enqueue(MockResponse().setBody(publicAnimal.dropLast(1)+""", "nascimentoEstimado":"2024-01-01","ong":{"nome":"ONG Teste","cidade":"Manaus","estado":"AM"}}"""))
   val a=AnimalApi(server.url("/").toString()).publicGet(9);assertEquals("ONG Teste",a.ongNome);assertEquals("2024-01-01",a.nascimentoEstimado);assertTrue(a.fotos.single().principal);assertNull(server.takeRequest().getHeader("Authorization"))
  } finally {server.shutdown()}
 }
 @Test fun animalIndisponivelRetorna404() = runBlocking {
  val server=MockWebServer();server.start()
  try {server.enqueue(MockResponse().setResponseCode(404));try {AnimalApi(server.url("/").toString()).publicGet(9);fail()} catch(e:AnimalHttpException){assertEquals(404,e.status)}} finally {server.shutdown()}
 }
 @Test fun fotografiaPublicaUsaRotaDaApiSemBearer() = runBlocking {
  val server=MockWebServer();server.start()
  try {val image=byteArrayOf(1,2,3);server.enqueue(MockResponse().setBody(Buffer().write(image)).setHeader("Content-Type","image/png"));assertArrayEquals(image,AnimalApi(server.url("/").toString()).publicPhotoBytes(9,4));val r=server.takeRequest();assertEquals("/api/animais/9/fotos/4",r.path);assertNull(r.getHeader("Authorization"))} finally {server.shutdown()}
 }
 @Test fun publicacaoEDespublicacaoPreservamSituacao() = runBlocking {
  val server=MockWebServer();server.start()
  try {val api=AnimalApi(server.url("/").toString())
   for(published in listOf(true,false)) {
    server.enqueue(MockResponse().setBody("""{"versao":3,"id":9,"ongId":2,"nome":"Luna","especie":"CAO","sexo":"FEMEA","porte":"MEDIO","dataEntrada":"2025-01-01","situacao":"EM_TRATAMENTO","ativo":true,"publicado":$published,"fotos":[]}"""))
    val a=api.publication("teste",9,published);assertEquals(published,a.publicado);assertEquals(AnimalStatus.EM_TRATAMENTO,a.situacao)
    val r=server.takeRequest();assertEquals("PATCH",r.method);assertEquals("Bearer teste",r.getHeader("Authorization"));assertEquals("/api/ong/animais/9/"+if(published) "publicar" else "despublicar",r.path);assertEquals(0L,r.bodySize)
   }
  } finally {server.shutdown()}
 }
}
