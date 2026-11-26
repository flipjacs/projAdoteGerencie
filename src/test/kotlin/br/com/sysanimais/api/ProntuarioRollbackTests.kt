package br.com.sysanimais.api

import br.com.sysanimais.api.prontuarios.domain.CorrecaoProntuario
import br.com.sysanimais.api.prontuarios.repository.CorrecaoProntuarioRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.mockito.ArgumentMatchers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.*
import java.time.Instant

/** No outer transaction: observes rollback of the real service, not a test transaction. */
@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties=["spring.datasource.url=jdbc:h2:mem:prontuario_rollback;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","app.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"])
class ProntuarioRollbackTests @Autowired constructor(val mvc:MockMvc,val db:JdbcTemplate,val jwt:JwtEncoder,val json:ObjectMapper) {
 @MockitoSpyBean lateinit var corrections:CorrecaoProntuarioRepository
 private fun auth():String {val now=Instant.now();return "Bearer "+jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().subject("851").issuedAt(now).expiresAt(now.plusSeconds(3600)).build())).tokenValue}
 @Test fun `falha ao gravar historico desfaz dados clinicos e versao`(){
  db.update("INSERT INTO ong(id,nome,email) VALUES(851,'ONG','rollback@medical.invalid')")
  db.update("INSERT INTO usuario(id,ong_id,nome,email,senha_hash,perfil,ativo) VALUES(851,851,'Vet','vet@rollback.invalid','x','veterinario',true)")
  db.update("INSERT INTO animal(id,ong_id,nome,especie,sexo,porte,data_entrada,situacao,situacao_atualizada_em,situacao_alterada_por,ativo,publicado,criado_em,atualizado_em,versao) VALUES(851,851,'Lua','CAO','FEMEA','MEDIO',CURRENT_DATE,'DISPONIVEL',CURRENT_TIMESTAMP,851,true,false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)")
  val base="/api/ong/animais/851/prontuarios"
  val r=mvc.post(base){header("Authorization",auth());contentType=MediaType.APPLICATION_JSON;content="""{"tipo":"CONSULTA","titulo":"Original","descricao":"Avaliação","dataProcedimento":"2020-01-01"}"""}.andExpect{status{isCreated()}}.andReturn()
  val id=json.readTree(r.response.contentAsString)["id"].asLong()
  Mockito.doThrow(DataIntegrityViolationException("Falha de persistência simulada")).`when`(corrections).saveAndFlush(ArgumentMatchers.any(CorrecaoProntuario::class.java))
  mvc.patch("$base/$id/corrigir"){header("Authorization",auth());contentType=MediaType.APPLICATION_JSON;content="""{"titulo":"Revisado","justificativa":"Revisão","versao":0}"""}.andExpect{status{isConflict()}}
  assertEquals("Original",db.queryForObject("SELECT titulo FROM prontuario WHERE id=?",String::class.java,id))
  assertEquals(0L,db.queryForObject("SELECT versao FROM prontuario WHERE id=?",Long::class.java,id))
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM correcao_prontuario",Int::class.java))
 }
}
