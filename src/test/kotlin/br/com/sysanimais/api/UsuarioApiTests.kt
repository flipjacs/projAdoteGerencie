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
    private val json: ObjectMapper
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

    private fun tokenAdmin(): String {
        val login = mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"admin@teste.com","senha":"SenhaForte123!"}"""
        }.andReturn()
        return json.readTree(login.response.contentAsString).get("accessToken").asText()
    }
}
