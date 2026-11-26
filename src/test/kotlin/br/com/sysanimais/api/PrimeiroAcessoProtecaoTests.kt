package br.com.sysanimais.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties=["spring.datasource.url=jdbc:h2:mem:primeiro_protecao;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "app.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"])
class PrimeiroAcessoProtecaoTests @Autowired constructor(val mvc: MockMvc) {
 @Test fun `limita corpo e requisicoes invalidas antes da desserializacao`() {
  val url="/api/auth/primeiro-acesso/trocar-senha"
  mvc.post(url) { contentType=MediaType.APPLICATION_JSON;content="{\"email\":\""+"x".repeat(10000)+"\"}" }.andExpect { status { isPayloadTooLarge() } }
  repeat(29) { mvc.post(url) { contentType=MediaType.APPLICATION_JSON;content="{}";header("X-Forwarded-For","203.0.113.$it") }.andExpect { status { isBadRequest() } } }
  mvc.post(url) { contentType=MediaType.APPLICATION_JSON;content="{}";header("X-Forwarded-For","198.51.100.1") }.andExpect { status { isTooManyRequests() };header { exists("Retry-After") } }
 }
}
