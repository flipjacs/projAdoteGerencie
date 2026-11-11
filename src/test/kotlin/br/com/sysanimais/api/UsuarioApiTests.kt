package br.com.sysanimais.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import java.time.Instant
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:usuarios;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.locations=classpath:db/migration",
    "app.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
])
class UsuarioApiTests @Autowired constructor(
    private val mvc: MockMvc,
    private val db: JdbcTemplate,
    private val encoder: PasswordEncoder,
    private val json: ObjectMapper,
    private val jwtEncoder: JwtEncoder
) {
    @BeforeEach
    fun reset() {
        db.update("DELETE FROM usuario")
        db.update("DELETE FROM ong")
        db.update("INSERT INTO ong (id, nome, email) VALUES (1, 'Patas Amigas', 'ong@teste.com')")
        db.update("INSERT INTO ong (id, nome, email) VALUES (2, 'Outra ONG', 'outra@teste.com')")
        db.update("INSERT INTO usuario (id, ong_id, nome, email, senha_hash, perfil, ativo) VALUES (10, 1, 'Admin', 'admin@teste.com', ?, 'administrador', true)", encoder.encode("SenhaForte123!"))
    }

    @Test
    fun `cadastro publico cria usuario com senha em hash sem expor senha`() {
        mvc.post("/api/usuarios/cadastro") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Ana Lima","email":" ANA@EXEMPLO.COM ","senha":"SenhaForte123!"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.email") { value("ana@exemplo.com") }
            jsonPath("$.perfil") { value("USUARIO") }
            jsonPath("$.ongId") { value(null) }
            jsonPath("$.senha") { doesNotExist() }
            jsonPath("$.senhaHash") { doesNotExist() }
        }
        val hash = db.queryForObject("SELECT senha_hash FROM usuario WHERE email = 'ana@exemplo.com'", String::class.java)!!
        assertTrue(encoder.matches("SenhaForte123!", hash))
    }

    @Test
    fun `cadastro publico rejeita perfil e ong enviados pelo cliente`() {
        mvc.post("/api/usuarios/cadastro") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Ana Lima","email":"ana@exemplo.com","senha":"SenhaForte123!","perfil":"ADMINISTRADOR","ongId":1}"""
        }.andExpect { status { isBadRequest() } }

        mvc.post("/api/usuarios/cadastro") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Ana Lima","email":"ana@exemplo.com","senha":"SenhaForte123!","tipo":"APADRINHADOR"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `email duplicado retorna conflito sem criar usuario`() {
        mvc.post("/api/usuarios/cadastro") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Outra Ana","email":"ADMIN@TESTE.COM","senha":"SenhaForte123!"}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `outra restricao nao e informada como email duplicado`() {
        db.execute("ALTER TABLE usuario ADD CONSTRAINT ck_nome_teste CHECK (nome <> 'Violacao')")
        try {
            mvc.post("/api/usuarios/cadastro") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"nome":"Violacao","email":"novo@teste.com","senha":"SenhaForte123!"}"""
            }.andExpect {
                status { isInternalServerError() }
                jsonPath("$.detail") { value("Não foi possível concluir a operação") }
            }
        } finally {
            db.execute("ALTER TABLE usuario DROP CONSTRAINT ck_nome_teste")
        }
    }

    @Test
    fun `cadastro publico rejeita senha composta apenas por espacos`() {
        mvc.post("/api/usuarios/cadastro") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Ana Lima","email":"ana@exemplo.com","senha":"            "}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `cadastro interno exige administrador autenticado`() {
        mvc.post("/api/usuarios/internos") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Bia","email":"bia@teste.com","senha":"SenhaForte123!","perfil":"VOLUNTARIO"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `usuario publico autentica mas nao cadastra equipe`() {
        mvc.post("/api/usuarios/cadastro") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Ana Lima","email":"ana@exemplo.com","senha":"SenhaForte123!"}"""
        }.andExpect { status { isCreated() } }
        val login = mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"ana@exemplo.com","senha":"SenhaForte123!"}"""
        }.andExpect { status { isOk() } }.andReturn()
        val token = json.readTree(login.response.contentAsString).get("accessToken").asText()
        assertTrue(token.isNotBlank())
        mvc.post("/api/usuarios/internos") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Bia Rocha","email":"bia@teste.com","senha":"SenhaForte123!","perfil":"VOLUNTARIO"}"""
        }.andExpect { status { isForbidden() } }
        db.update("UPDATE usuario SET ativo = false WHERE email = 'ana@exemplo.com'")
        mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"ana@exemplo.com","senha":"SenhaForte123!"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `administrador cadastra voluntario somente na propria ong`() {
        val login = mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"admin@teste.com","senha":"SenhaForte123!"}"""
        }.andReturn()
        val token = json.readTree(login.response.contentAsString).get("accessToken").asText()
        mvc.post("/api/usuarios/internos") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Bia Rocha","email":"bia@teste.com","senha":"SenhaForte123!","perfil":"VOLUNTARIO"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.ongId") { value(1) }
            jsonPath("$.perfil") { value("VOLUNTARIO") }
        }
        assertEquals(1L, db.queryForObject("SELECT ong_id FROM usuario WHERE email = 'bia@teste.com'", Long::class.java))
    }

    @Test
    fun `administrador nao pode escolher outra ong no corpo`() {
        val token = tokenAdmin()
        mvc.post("/api/usuarios/internos") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Bia Rocha","email":"bia@teste.com","senha":"SenhaForte123!","perfil":"VOLUNTARIO","ongId":2}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `administrador desativado perde acesso mesmo com token ainda valido`() {
        val token = tokenAdmin()
        db.update("UPDATE usuario SET ativo = false WHERE id = 10")
        mvc.post("/api/usuarios/internos") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Bia Rocha","email":"bia@teste.com","senha":"SenhaForte123!","perfil":"VOLUNTARIO"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `administrador sem ong nao pode cadastrar usuario interno`() {
        db.update("UPDATE usuario SET ong_id = NULL WHERE id = 10")
        val token = tokenAdmin()
        mvc.post("/api/usuarios/internos") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Bia Rocha","email":"bia@teste.com","senha":"SenhaForte123!","perfil":"VOLUNTARIO"}"""
        }.andExpect { status { isForbidden() } }
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = 'bia@teste.com'", Int::class.java))
    }

    @Test
    fun `me reflete identidade e perfil atual e bloqueia usuario inativo`() {
        val token = tokenAdmin()
        for (perfil in listOf("administrador", "voluntario", "veterinario", "usuario")) {
            db.update("UPDATE usuario SET perfil = ?, ong_id = ? WHERE id = 10", perfil, if (perfil == "usuario") null else 1)
            mvc.get("/api/auth/me") { header("Authorization", "Bearer $token") }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(10) }
                jsonPath("$.nome") { value("Admin") }
                jsonPath("$.email") { value("admin@teste.com") }
                jsonPath("$.perfil") { value(perfil.uppercase()) }
                if (perfil == "usuario") jsonPath("$.ongId") { value(null) } else jsonPath("$.ongId") { value(1) }
                jsonPath("$.senha") { doesNotExist() }
                jsonPath("$.senhaHash") { doesNotExist() }
            }
        }
        db.update("UPDATE usuario SET ativo = false WHERE id = 10")
        mvc.get("/api/auth/me") { header("Authorization", "Bearer $token") }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `me rejeita token ausente invalido ou expirado`() {
        mvc.get("/api/auth/me").andExpect { status { isUnauthorized() } }
        mvc.get("/api/auth/me") { header("Authorization", "Bearer invalido") }.andExpect { status { isUnauthorized() } }
        val now = Instant.now()
        val claims = JwtClaimsSet.builder().subject("10").issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
        mvc.get("/api/auth/me") { header("Authorization", "Bearer $token") }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `limite de login retorna 429 generico com retry after`() {
        repeat(8) { index ->
            mvc.post("/api/auth/login") {
                header("X-Forwarded-For", "198.51.100.$index")
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"limite-inexistente@teste.com","senha":"SenhaInvalida123!"}"""
            }.andExpect { status { isUnauthorized() } }
        }
        mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"limite-inexistente@teste.com","senha":"SenhaInvalida123!"}"""
        }.andExpect {
            status { isTooManyRequests() }
            header { exists("Retry-After") }
            jsonPath("$.detail") { value("Muitas tentativas. Tente novamente mais tarde.") }
        }
    }

    @Test
    fun `credenciais invalidas retornam resposta equivalente para contas distintas`() {
        db.update("INSERT INTO usuario (id, ong_id, nome, email, senha_hash, perfil, ativo) VALUES (11, NULL, 'Inativo', 'inativo@teste.com', ?, 'usuario', false)", encoder.encode("SenhaForte123!"))
        val responses = listOf("admin@teste.com", "inativo@teste.com", "ausente@teste.com").map { email ->
            mvc.post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$email","senha":"SenhaInvalida123!"}"""
            }.andExpect { status { isUnauthorized() } }.andReturn().response.contentAsString
        }
        assertTrue(responses.distinct().size == 1)
        assertTrue(responses.none { it.contains("SenhaInvalida123!") || it.contains("senha_hash") })
    }

    @Test
    fun `health minimo e publico e demais rotas continuam protegidas`() {
        mvc.get("/actuator/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
            jsonPath("$.components") { doesNotExist() }
            jsonPath("$.details") { doesNotExist() }
        }
        mvc.get("/actuator/health/db").andExpect { status { isUnauthorized() } }
        mvc.get("/actuator/env").andExpect { status { isUnauthorized() } }
        mvc.get("/api/auth/me").andExpect { status { isUnauthorized() } }
        mvc.post("/api/usuarios/internos") { contentType = MediaType.APPLICATION_JSON; content = "{}" }
            .andExpect { status { isUnauthorized() } }
    }

    private fun tokenAdmin(): String {
        val login = mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"admin@teste.com","senha":"SenhaForte123!"}"""
        }.andReturn()
        return json.readTree(login.response.contentAsString).get("accessToken").asText()
    }
}
