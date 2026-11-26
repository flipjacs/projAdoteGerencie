package br.com.sysanimais.api

import br.com.sysanimais.api.financeiro.domain.MovimentacaoFinanceira
import br.com.sysanimais.api.financeiro.repository.MovimentacaoFinanceiraRepository
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

/** No outer test transaction: observes the service's actual committed/rolled-back result. */
@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties=["spring.datasource.url=jdbc:h2:mem:financeiro_rollback;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","app.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"])
class FinanceiroRollbackTests @Autowired constructor(val mvc:MockMvc,val db:JdbcTemplate,val jwt:JwtEncoder,val json:ObjectMapper) {
 @MockitoSpyBean lateinit var movements:MovimentacaoFinanceiraRepository
 private fun auth(id:Int):String {val now=Instant.now();return "Bearer "+jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().subject(id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build())).tokenValue}
 @Test fun `falha da receita desfaz confirmacao e historico da doacao`(){
  db.update("INSERT INTO ong(id,nome,email) VALUES(901,'ONG','rollback@finance.invalid')")
  db.update("INSERT INTO usuario(id,ong_id,nome,email,senha_hash,perfil,ativo) VALUES(901,901,'Admin','admin@rollback.invalid','x','administrador',true),(902,NULL,'Doador','doador@rollback.invalid','x','usuario',true)")
  val created=mvc.post("/api/donations"){header("Authorization",auth(902));contentType=MediaType.APPLICATION_JSON;content="""{"ongId":901,"valor":"50.00"}"""}.andExpect{status{isCreated()}}.andReturn()
  val id=json.readTree(created.response.contentAsString)["id"].asLong()
  Mockito.doThrow(DataIntegrityViolationException("Falha financeira simulada")).`when`(movements).saveAndFlush(ArgumentMatchers.any(MovimentacaoFinanceira::class.java))
  mvc.patch("/api/ong/donations/$id/confirmar"){header("Authorization",auth(901))}.andExpect{status{isConflict()}}
  assertEquals("PENDENTE",db.queryForObject("SELECT status FROM doacao WHERE id=?",String::class.java,id))
  assertNull(db.queryForObject("SELECT confirmado_em FROM doacao WHERE id=?",java.sql.Timestamp::class.java,id))
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM movimentacao_financeira",Int::class.java))
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM historico_financeiro WHERE doacao_id=?",Int::class.java,id))
 }
}
