package br.com.sysanimais.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.*
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@SpringBootTest @AutoConfigureMockMvc @Transactional
@TestPropertySource(properties=["spring.datasource.url=jdbc:h2:mem:credenciais;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "app.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"])
class CredenciaisApiTests @Autowired constructor(val mvc: MockMvc,val db: JdbcTemplate,val jwt: JwtEncoder,val encoder: PasswordEncoder,val json: ObjectMapper) {
 @jakarta.persistence.PersistenceContext lateinit var entityManager: jakarta.persistence.EntityManager
 @BeforeEach fun fixtures() {
  db.update("INSERT INTO ong(id,nome,email) VALUES(401,'ONG A','a@cred.invalid'),(402,'ONG B','b@cred.invalid')")
  val hash=encoder.encode("SenhaForte123!")
  db.update("INSERT INTO usuario(id,ong_id,nome,email,senha_hash,perfil,ativo,credencial_pendente) VALUES(401,401,'Admin','admin@cred.invalid',?,'administrador',true,false),(402,401,'Pendente','pendente@cred.invalid',?,'voluntario',false,true),(403,402,'Outro','outro@cred.invalid',?,'administrador',true,false),(404,NULL,'Global','global@cred.invalid',?,'admin_plataforma',true,false),(405,401,'Inicial','inicial@cred.invalid',?,'administrador',false,true)",hash,hash,hash,hash,hash)
  db.update("UPDATE usuario SET provisionamento_virgem=true WHERE id IN (402,405)")
  db.update("UPDATE usuario SET primeiro_acesso_concluido=false WHERE id=405")
  db.update("INSERT INTO auditoria_institucional(ong_id,ator_id,usuario_alvo_id,operacao,estado_posterior) VALUES(401,404,405,'ADMIN_INICIAL_PROVISIONADO','PENDENTE')")
 }
 private fun auth(id: Int): String { val now=Instant.now();val claims=JwtClaimsSet.builder().subject(id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build();return "Bearer "+jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).tokenValue }
 @Test fun `emissao exige troca sem jwt operacional e consumo invalida senha temporaria`() {
  val emission=mvc.post("/api/ong/funcionarios/402/credencial-temporaria") { header("Authorization",auth(401));contentType=MediaType.APPLICATION_JSON;content="""{"versao":0,"motivo":"Primeiro acesso"}""" }.andExpect { status { isOk() };header { string("Cache-Control","no-store") } }.andReturn()
  val temporary=json.readTree(emission.response.contentAsString).get("senhaTemporaria").asText()
  assertNotEquals(temporary,db.queryForObject("SELECT senha_hash FROM usuario WHERE id=402",String::class.java))
  mvc.post("/api/auth/login") { contentType=MediaType.APPLICATION_JSON;content=json.writeValueAsString(mapOf("email" to "pendente@cred.invalid","senha" to temporary)) }.andExpect { status { isUnauthorized() } }
  mvc.get("/api/auth/me") { header("Authorization",auth(402)) }.andExpect { status { isForbidden() } }
  val troca=json.writeValueAsString(mapOf("email" to "pendente@cred.invalid","senhaTemporaria" to temporary,"novaSenha" to "Minha frase segura 2026!"))
  mvc.post("/api/auth/primeiro-acesso/trocar-senha") { contentType=MediaType.APPLICATION_JSON;content=troca }.andExpect { status { isNoContent() } }
  mvc.post("/api/auth/primeiro-acesso/trocar-senha") { contentType=MediaType.APPLICATION_JSON;content=troca }.andExpect { status { isUnauthorized() } }
  mvc.post("/api/auth/login") { contentType=MediaType.APPLICATION_JSON;content=json.writeValueAsString(mapOf("email" to "pendente@cred.invalid","senha" to "Minha frase segura 2026!")) }.andExpect { status { isOk() } }
 }
 @Test fun `administracao global emite somente para inicial identificada`() {
  mvc.get("/api/plataforma/ongs/401/administrador-inicial"){ header("Authorization",auth(404)) }.andExpect { status { isOk() };jsonPath("$.id") { value(405) } }
  mvc.post("/api/plataforma/ongs/401/administrador-inicial/credencial-temporaria") { header("Authorization",auth(404));contentType=MediaType.APPLICATION_JSON;content="""{"versao":0,"motivo":"Ativação inicial"}""" }.andExpect { status { isOk() } }
  mvc.post("/api/ong/funcionarios/402/credencial-temporaria") { header("Authorization",auth(403));contentType=MediaType.APPLICATION_JSON;content="""{"versao":0,"motivo":"Teste"}""" }.andExpect { status { isNotFound() } }
  mvc.post("/api/ong/funcionarios/402/credencial-temporaria") { header("Authorization",auth(404));contentType=MediaType.APPLICATION_JSON;content="""{"versao":0,"motivo":"Teste"}""" }.andExpect { status { isForbidden() } }
 }
 @Test fun `ong suspensa exige administrador ativo e permite provisionar o inicial`() {
  db.update("UPDATE usuario SET ativo=false WHERE id=401")
  db.update("UPDATE ong SET situacao='SUSPENSA' WHERE id=401")
  mvc.patch("/api/plataforma/ongs/401/reativacao") { header("Authorization",auth(404)) }.andExpect { status { isConflict() } }
  mvc.get("/api/plataforma/ongs/401/administrador-inicial") { header("Authorization",auth(404)) }.andExpect { status { isOk() } }
  val emitted=mvc.post("/api/plataforma/ongs/401/administrador-inicial/credencial-temporaria") { header("Authorization",auth(404));contentType=MediaType.APPLICATION_JSON;content="""{"versao":0,"motivo":"Primeiro acesso"}""" }.andExpect { status { isOk() } }.andReturn()
  val temporary=json.readTree(emitted.response.contentAsString).get("senhaTemporaria").asText()
  mvc.patch("/api/plataforma/ongs/401/reativacao") { header("Authorization",auth(404)) }.andExpect { status { isOk() } }
  val body=json.writeValueAsString(mapOf("email" to "inicial@cred.invalid","senhaTemporaria" to temporary,"novaSenha" to "Minha frase segura 2026!"))
  mvc.post("/api/auth/primeiro-acesso/trocar-senha") { contentType=MediaType.APPLICATION_JSON;content=body }.andExpect { status { isNoContent() } }
 }
 @Test fun `reemissao revoga segredo anterior e prazo expirado bloqueia troca`() {
  fun emitir(v: Long)=mvc.post("/api/ong/funcionarios/402/credencial-temporaria") { header("Authorization",auth(401));contentType=MediaType.APPLICATION_JSON;content="""{"versao":$v,"motivo":"Teste"}""" }.andExpect { status { isOk() } }.andReturn().response.contentAsString.let(json::readTree)
  val a=emitir(0); val b=emitir(a.get("versao").longValue())
  fun trocar(senha: String)=mvc.post("/api/auth/primeiro-acesso/trocar-senha") { contentType=MediaType.APPLICATION_JSON;content=json.writeValueAsString(mapOf("email" to "pendente@cred.invalid","senhaTemporaria" to senha,"novaSenha" to "Minha frase segura 2026!")) }
  trocar(a.get("senhaTemporaria").asText()).andExpect { status { isUnauthorized() } }
  db.update("UPDATE usuario SET credencial_temporaria_expira_em=? WHERE id=402",java.sql.Timestamp.from(Instant.now().minusSeconds(5)))
  entityManager.clear()
  trocar(b.get("senhaTemporaria").asText()).andExpect { status { isUnauthorized() } }
 }
 @Test fun `ultimo administrador nao pode receber redefinicao e funcionario desativado nao e reativado`() {
  mvc.post("/api/ong/funcionarios/401/credencial-temporaria") { header("Authorization",auth(401));contentType=MediaType.APPLICATION_JSON;content="""{"versao":0,"motivo":"Teste"}""" }.andExpect { status { isConflict() };jsonPath("$.code") { value("ULTIMO_ADMINISTRADOR") } }
  db.update("UPDATE usuario SET provisionamento_virgem=false WHERE id=402")
  mvc.post("/api/ong/funcionarios/402/credencial-temporaria") { header("Authorization",auth(401));contentType=MediaType.APPLICATION_JSON;content="""{"versao":0,"motivo":"Teste"}""" }.andExpect { status { isConflict() } }
  assertEquals(false,db.queryForObject("SELECT ativo FROM usuario WHERE id=402",Boolean::class.java))
 }
 @Test fun `redefinicao revoga jwt anterior e nao altera perfil nem ong`() {
  db.update("UPDATE usuario SET ativo=true,credencial_pendente=false,provisionamento_virgem=false WHERE id=402")
  val anterior=auth(402)
  mvc.get("/api/auth/me") { header("Authorization",anterior) }.andExpect { status { isOk() } }
  mvc.post("/api/ong/funcionarios/402/credencial-temporaria") { header("Authorization",auth(401));contentType=MediaType.APPLICATION_JSON;content="""{"versao":0,"motivo":"Redefinição"}""" }.andExpect { status { isOk() } }
  mvc.get("/api/auth/me") { header("Authorization",anterior) }.andExpect { status { isForbidden() } }
  assertEquals("voluntario",db.queryForObject("SELECT perfil FROM usuario WHERE id=402",String::class.java))
  assertEquals(401,db.queryForObject("SELECT ong_id FROM usuario WHERE id=402",Int::class.java))
 }

}
