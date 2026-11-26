package br.com.sysanimais.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.*
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** No test transaction: both workers and assertions observe real commits/rollbacks. */
@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties=["spring.datasource.url=jdbc:h2:mem:apad_transacao;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","app.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"])
class ApadrinhamentoTransacaoTests @Autowired constructor(val mvc:MockMvc,val db:JdbcTemplate,val json:ObjectMapper,val jwt:JwtEncoder) {
 companion object {val ids=AtomicInteger(1200)}
 var base=0;val user get()=base+1;val other get()=base+2
 @BeforeEach fun fixture(){
  base=ids.addAndGet(10)
  db.update("INSERT INTO ong(id,nome,email) VALUES(?,'ONG transacao',?)",base,"ong$base@k81.invalid")
  for((id,role,ong) in listOf(Triple(base,"administrador",base),Triple(user,"usuario",null),Triple(other,"usuario",null)))db.update("INSERT INTO usuario(id,ong_id,nome,email,senha_hash,perfil,ativo) VALUES(?,?,'Pessoa',?,'x',?,true)",id,ong,"u$id@k81.invalid",role)
  db.update("INSERT INTO animal(id,ong_id,nome,especie,sexo,porte,data_entrada,situacao,situacao_atualizada_em,situacao_alterada_por,ativo,publicado,criado_em,atualizado_em,versao) VALUES(?,?,'Lua','CAO','FEMEA','MEDIO',CURRENT_DATE,'DISPONIVEL',CURRENT_TIMESTAMP,?,true,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",base,base,base)
 }
 private fun auth(id:Int):String{val now=Instant.now();return "Bearer "+jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().subject(id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build())).tokenValue}
 private fun post(path:String,body:String,u:Int)=mvc.post(path){header("Authorization",auth(u));contentType=MediaType.APPLICATION_JSON;content=body}
 private fun patch(path:String,u:Int=base,body:String?=null)=mvc.patch(path){header("Authorization",auth(u));if(body!=null){contentType=MediaType.APPLICATION_JSON;content=body}}
 private fun create():Long{val r=post("/api/sponsorships","""{"animalId":$base,"modalidade":"MENSAL","valor":"25.50"}""",user).andExpect{status{isCreated()}}.andReturn();return json.readTree(r.response.contentAsString)["id"].asLong()}
 private fun part(id:Long)=db.queryForObject("SELECT id FROM parcela_apadrinhamento WHERE apadrinhamento_id=?",Long::class.java,id)!!
 private fun parallel(a:()->Int,b:()->Int):List<Int>{val ready=CountDownLatch(2);val start=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2);try{val jobs=listOf(a,b).map{action->pool.submit(Callable{ready.countDown();check(start.await(10,TimeUnit.SECONDS));action()})};check(ready.await(10,TimeUnit.SECONDS));start.countDown();return jobs.map{it.get(20,TimeUnit.SECONDS)}}finally{pool.shutdownNow()}}
 @Test fun `criacao concorrente impede dois compromissos ativos`(){
  val body="""{"animalId":$base,"modalidade":"MENSAL","valor":"25.50"}"""
  assertEquals(listOf(201,409),parallel({post("/api/sponsorships",body,user).andReturn().response.status},{post("/api/sponsorships",body,user).andReturn().response.status}).sorted())
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM apadrinhamento WHERE animal_id=?",Int::class.java,base))
 }
 @Test fun `duas confirmacoes concorrentes criam uma unica receita`(){
  val id=create();val part=part(id);val path="/api/ong/sponsorships/$id/parcelas/$part/confirmar"
  assertEquals(listOf(200,409),parallel({patch(path).andReturn().response.status},{patch(path).andReturn().response.status}).sorted())
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM movimentacao_financeira WHERE parcela_apadrinhamento_id=?",Int::class.java,part))
  assertEquals("CONFIRMADA",db.queryForObject("SELECT status FROM parcela_apadrinhamento WHERE id=?",String::class.java,part))
 }
 @Test fun `cancelar e confirmar em paralelo preserva recebimento e historico`(){
  val id=create();val part=part(id)
  assertEquals(listOf(200,200),parallel({patch("/api/me/sponsorships/$id/cancelar",user).andReturn().response.status},{patch("/api/ong/sponsorships/$id/parcelas/$part/confirmar").andReturn().response.status}))
  assertEquals("ENCERRADO",db.queryForObject("SELECT status FROM apadrinhamento WHERE id=?",String::class.java,id))
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM movimentacao_financeira WHERE parcela_apadrinhamento_id=?",Int::class.java,part))
 }
 @Test fun `limite de parcelas desfaz falecimento e entrega formal integralmente`(){
  val id=create();db.update("UPDATE apadrinhamento SET data_inicio='2000-01-01' WHERE id=?",id)
  patch("/api/ong/animais/$base/situacao",body="""{"situacao":"FALECIDO","versao":0}""").andExpect{status{isConflict()}}
  assertEquals("DISPONIVEL",db.queryForObject("SELECT situacao FROM animal WHERE id=?",String::class.java,base))
  val r=post("/api/adoption-requests","""{"animalId":$base}""",other).andExpect{status{isCreated()}}.andReturn();val adoption=json.readTree(r.response.contentAsString)["id"].asLong()
  patch("/api/ong/adoption-requests/$adoption/aprovar").andExpect{status{isOk()}}
  post("/api/ong/adoption-requests/$adoption/confirmar-entrega","""{"entregaConfirmada":true}""",base).andExpect{status{isConflict()}}
  assertEquals("EM_PROCESSO_ADOCAO",db.queryForObject("SELECT situacao FROM animal WHERE id=?",String::class.java,base))
  assertEquals("APROVADA",db.queryForObject("SELECT status FROM solicitacao_adocao WHERE id=?",String::class.java,adoption))
  assertEquals("ATIVO",db.queryForObject("SELECT status FROM apadrinhamento WHERE id=?",String::class.java,id))
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM parcela_apadrinhamento WHERE apadrinhamento_id=?",Int::class.java,id))
 }
}
