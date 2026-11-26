package br.com.sysanimais.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
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
@TestPropertySource(properties=["spring.datasource.url=jdbc:h2:mem:equipe;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "app.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"])
class EquipeApiTests @Autowired constructor(val mvc: MockMvc,val db: JdbcTemplate,val jwt: JwtEncoder,val encoder: PasswordEncoder,val json: ObjectMapper) {
 @Autowired lateinit var equipe: br.com.sysanimais.api.equipes.service.EquipeService
 @Autowired lateinit var usuarios: br.com.sysanimais.api.usuarios.repository.UsuarioRepository
 @jakarta.persistence.PersistenceContext lateinit var em: jakarta.persistence.EntityManager
 @BeforeEach fun fixtures() {
  db.update("INSERT INTO ong(id,nome,email) VALUES(301,'ONG A','a@equipe.invalid'),(302,'ONG B','b@equipe.invalid')")
  for ((id,ong,perfil,pendente) in listOf(listOf(301,301,"administrador",false),listOf(302,302,"administrador",false),listOf(303,301,"voluntario",false),listOf(304,301,"administrador",true))) {
   db.update("INSERT INTO usuario(id,ong_id,nome,email,senha_hash,perfil,ativo,credencial_pendente) VALUES(?,?,?,?,?,?,true,?)",id,ong,"Teste", "e$id@equipe.invalid",encoder.encode("SenhaForte123!"),perfil,pendente)
  }
  db.update("INSERT INTO usuario(id,ong_id,nome,email,senha_hash,perfil,ativo,credencial_pendente) VALUES(306,NULL,'Global','global@equipe.invalid',?,'admin_plataforma',true,false),(307,NULL,'Publico','publico@equipe.invalid',?,'usuario',true,false),(308,301,'Vet','vet@equipe.invalid',?,'veterinario',true,false)",encoder.encode("SenhaForte123!"),encoder.encode("SenhaForte123!"),encoder.encode("SenhaForte123!"))
 }
 private fun token(id: Int): String { val now=Instant.now(); val claims=JwtClaimsSet.builder().subject(id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build();return jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).tokenValue }
 private fun auth(id: Int)="Bearer ${token(id)}"
 @Test fun `cadastro cria conta pendente e recusa senha ou ong arbitraria`() {
  val payload="""{"nome":"Pessoa Teste","email":"novo@equipe.invalid","perfil":"VOLUNTARIO"}"""
  val result=mvc.post("/api/ong/funcionarios") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content=payload }.andExpect { status { isCreated() };jsonPath("$.credencialPendente") { value(true) };jsonPath("$.senhaHash") { doesNotExist() } }.andReturn()
  val id=json.readTree(result.response.contentAsString).get("id").asInt()
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM usuario WHERE id=? AND ong_id=301 AND credencial_pendente=true AND ativo=false",Int::class.java,id))
  mvc.get("/api/auth/me") { header("Authorization",auth(id)) }.andExpect { status { isForbidden() } }
  mvc.post("/api/usuarios/internos") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content=payload.dropLast(1)+",\"senha\":\"SenhaForte123!\"}" }.andExpect { status { isBadRequest() } }
  mvc.post("/api/ong/funcionarios") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content=payload.dropLast(1)+",\"ongId\":302}" }.andExpect { status { isBadRequest() } }
 }
 @Test fun `isola ong e protege ultimo administrador apto`() {
  mvc.get("/api/ong/funcionarios/302") { header("Authorization",auth(301)) }.andExpect { status { isNotFound() } }
  mvc.get("/api/ong/funcionarios") { header("Authorization",auth(301)) }.andExpect { status { isOk() };jsonPath("$.totalElements") { value(4) } }
  mvc.patch("/api/ong/funcionarios/302/desativacao") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"motivo":"Teste","versao":0}""" }.andExpect { status { isNotFound() } }
  mvc.patch("/api/ong/funcionarios/302/reativacao") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"versao":0}""" }.andExpect { status { isNotFound() } }
  mvc.patch("/api/ong/funcionarios/302/perfil") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"perfil":"VOLUNTARIO","motivo":"Teste","versao":0}""" }.andExpect { status { isNotFound() } }
  mvc.put("/api/ong/funcionarios/302") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"nome":"Outro","versao":0}""" }.andExpect { status { isNotFound() } }
  mvc.patch("/api/ong/funcionarios/301/desativacao") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"motivo":"Teste","versao":0}""" }.andExpect { status { isConflict() } }
  for(id in listOf(303,306,307,308)) mvc.get("/api/ong/funcionarios") { header("Authorization",auth(id)) }.andExpect { status { isForbidden() } }
  mvc.get("/api/ong/funcionarios").andExpect { status { isUnauthorized() } }
 }
 @Test fun `perfil atualizado reflete no token e versao antiga nao sobrescreve`() {
  val token=auth(303)
  mvc.patch("/api/ong/funcionarios/303/perfil") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"perfil":"VETERINARIO","motivo":"Mudança aprovada","versao":0}""" }.andExpect { status { isOk() };jsonPath("$.versao") { value(1) } }
  mvc.get("/api/auth/me") { header("Authorization",token) }.andExpect { status { isOk() };jsonPath("$.perfil") { value("VETERINARIO") } }
  mvc.put("/api/ong/funcionarios/303") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"nome":"Nome antigo","versao":0}""" }.andExpect { status { isConflict() };jsonPath("$.code") { value("FUNCIONARIO_VERSAO_CONFLITANTE") } }
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM auditoria_equipe WHERE funcionario_id=303 AND operacao='PERFIL_ALTERADO'",Int::class.java))
 }
 @Test fun `reativacao nao autentica conta com credencial pendente`() {
  mvc.patch("/api/ong/funcionarios/304/desativacao") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"motivo":"Teste","versao":0}""" }.andExpect { status { isOk() };jsonPath("$.ativo") { value(false) } }
  mvc.patch("/api/ong/funcionarios/304/reativacao") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"versao":1}""" }.andExpect { status { isOk() };jsonPath("$.ativo") { value(true) };jsonPath("$.credencialPendente") { value(true) } }
  mvc.get("/api/auth/me") { header("Authorization",auth(304)) }.andExpect { status { isForbidden() } }
 }
 @Test fun `reativacao nao restaura jwt emitido antes da desativacao`() {
  val antigo=auth(303)
  mvc.patch("/api/ong/funcionarios/303/desativacao") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"motivo":"Teste","versao":0}""" }.andExpect { status { isOk() } }
  mvc.get("/api/auth/me") { header("Authorization",antigo) }.andExpect { status { isForbidden() } }
  mvc.patch("/api/ong/funcionarios/303/reativacao") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"versao":1}""" }.andExpect { status { isOk() } }
  mvc.get("/api/auth/me") { header("Authorization",antigo) }.andExpect { status { isForbidden() } }
  val login=mvc.post("/api/auth/login") { contentType=MediaType.APPLICATION_JSON;content="""{"email":"e303@equipe.invalid","senha":"SenhaForte123!"}""" }.andExpect { status { isOk() } }.andReturn()
  val novo=json.readTree(login.response.contentAsString).get("accessToken").asText()
  mvc.get("/api/auth/me") { header("Authorization","Bearer $novo") }.andExpect { status { isOk() } }
 }
 @Test fun `downgrade so e permitido enquanto restar administrador apto`() {
  db.update("INSERT INTO usuario(id,ong_id,nome,email,senha_hash,perfil,ativo,credencial_pendente) VALUES(305,301,'Admin 2','e305@equipe.invalid',?,'administrador',true,false)",encoder.encode("SenhaForte123!"))
  mvc.patch("/api/ong/funcionarios/301/perfil") { header("Authorization",auth(301));contentType=MediaType.APPLICATION_JSON;content="""{"perfil":"VOLUNTARIO","motivo":"Mudança aprovada","versao":0}""" }.andExpect { status { isOk() } }
  mvc.patch("/api/ong/funcionarios/305/desativacao") { header("Authorization",auth(305));contentType=MediaType.APPLICATION_JSON;content="""{"motivo":"Teste","versao":0}""" }.andExpect { status { isConflict() };jsonPath("$.code") { value("ULTIMO_ADMINISTRADOR") } }
 }
 @Test fun `versao de token revogada apos autenticacao impede escrita no servico`() {
  val ator=br.com.sysanimais.api.usuarios.dto.UsuarioResponse.from(usuarios.findById(301).orElseThrow())
  db.update("UPDATE usuario SET token_versao=token_versao+1 WHERE id=301")
  em.clear()
  val erro=org.junit.jupiter.api.Assertions.assertThrows(br.com.sysanimais.api.equipes.service.EquipeException::class.java) {
   equipe.atualizar(ator,303,br.com.sysanimais.api.equipes.dto.AtualizarFuncionarioRequest("Não deve alterar",0))
  }
  assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,erro.status)
  assertEquals("Teste",db.queryForObject("SELECT nome FROM usuario WHERE id=303",String::class.java))
 }

}
