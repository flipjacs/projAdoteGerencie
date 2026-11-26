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
import org.springframework.test.web.servlet.put
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import java.time.Instant
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.junit.jupiter.api.extension.ExtendWith
import br.com.sysanimais.api.equipes.service.EquipeService
import br.com.sysanimais.api.equipes.dto.CriarFuncionarioRequest
import br.com.sysanimais.api.equipes.service.EquipeException
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.domain.Perfil
import org.junit.jupiter.api.Assertions.assertThrows
import org.springframework.web.server.ResponseStatusException

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
    private val jwtEncoder: JwtEncoder,
    private val equipeService: EquipeService,
    private val ongService: br.com.sysanimais.api.ongs.service.OngService
) {
    companion object { private val testOrigin = java.util.concurrent.atomic.AtomicInteger(20) }
    @BeforeEach
    fun reset() {
        db.update("DELETE FROM auditoria_equipe")
        db.update("DELETE FROM auditoria_institucional")
        db.update("UPDATE ong SET criado_por = NULL")
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
    @ExtendWith(OutputCaptureExtension::class)
    fun `cadastro duplicado nao registra email nem senha`(output: CapturedOutput) {
        mvc.post("/api/usuarios/cadastro") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Pessoa","email":"admin@teste.com","senha":"SenhaForte123!"}"""
        }.andExpect { status { isConflict() } }
        assertTrue(!output.toString().contains("admin@teste.com", ignoreCase = true))
        assertTrue(!output.toString().contains("SenhaForte123!"))
    }

    @Test
    fun `cadastro limita requisicoes por origem mesmo com dados invalidos`() {
        repeat(10) {
            mvc.post("/api/usuarios/cadastro") {
                with { request -> request.remoteAddr = "192.0.2.77"; request }
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isBadRequest() } }
        }
        mvc.post("/api/usuarios/cadastro") {
            with { request -> request.remoteAddr = "192.0.2.77"; request }
            header("X-Forwarded-For", "203.0.113.1")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isTooManyRequests() }; header { exists("Retry-After") } }
        mvc.post("/api/usuarios/cadastro") {
            with { request -> request.remoteAddr = "192.0.2.78"; request }
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `cadastros concorrentes mantem unicidade sem dados sensiveis nos logs`(output: CapturedOutput) {
        val email = "concorrencia-b04@teste.invalid"
        val senha = "SenhaConcorreB04!"
        val inicio = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map { index -> pool.submit<Int> {
                inicio.await()
                mvc.post("/api/usuarios/cadastro") {
                    with { request -> request.remoteAddr = "192.0.2.${90 + index}"; request }
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nome":"Teste","email":"$email","senha":"$senha"}"""
                }.andReturn().response.status
            } }
            inicio.countDown()
            assertEquals(listOf(201, 409), futures.map { it.get() }.sorted())
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = ?", Int::class.java, email))
            assertTrue(!output.toString().contains(email))
            assertTrue(!output.toString().contains(senha))
        } finally { pool.shutdownNow() }
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
            content = """{"nome":"Bia Rocha","email":"bia@teste.com","perfil":"VOLUNTARIO"}"""
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
            content = """{"nome":"Bia Rocha","email":"bia@teste.com","perfil":"VOLUNTARIO"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.ongId") { value(1) }
            jsonPath("$.perfil") { value("VOLUNTARIO") }
            jsonPath("$.credencialPendente") { value(true) }
            jsonPath("$.ativo") { value(false) }
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
        val token = tokenAdmin()
        db.update("UPDATE usuario SET ong_id = NULL WHERE id = 10")
        mvc.post("/api/usuarios/internos") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Bia Rocha","email":"bia@teste.com","senha":"SenhaForte123!","perfil":"VOLUNTARIO"}"""
        }.andExpect { status { isForbidden() } }
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = 'bia@teste.com'", Int::class.java))
    }

    @Test
    fun `administrador global autentica sem ong e perde acesso quando desativado`() {
        db.update("INSERT INTO usuario (id, ong_id, nome, email, senha_hash, perfil, ativo) VALUES (12, NULL, 'Global', 'global@teste.invalid', ?, 'admin_plataforma', true)", encoder.encode("SenhaForte123!"))
        val response = mvc.post("/api/auth/login") { contentType=MediaType.APPLICATION_JSON; content="""{"email":"global@teste.invalid","senha":"SenhaForte123!"}""" }.andExpect { status { isOk() } }.andReturn()
        val token = json.readTree(response.response.contentAsString).get("accessToken").asText()
        mvc.get("/api/auth/me") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() }; jsonPath("$.perfil") { value("ADMIN_PLATAFORMA") }; jsonPath("$.ongId") { value(null) }; jsonPath("$.tokenVersao") { doesNotExist() } }
        mvc.get("/api/ong/animais") { header("Authorization", "Bearer $token") }.andExpect { status { isForbidden() } }
        mvc.post("/api/usuarios/internos") { header("Authorization", "Bearer $token"); contentType=MediaType.APPLICATION_JSON; content="""{"nome":"Teste","email":"novo@teste.invalid","senha":"SenhaForte123!","perfil":"VOLUNTARIO"}""" }.andExpect { status { isForbidden() } }
        db.update("UPDATE usuario SET perfil='usuario' WHERE id=12")
        mvc.get("/api/auth/me") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() }; jsonPath("$.perfil") { value("USUARIO") } }
        db.update("UPDATE usuario SET ativo=false WHERE id=12")
        mvc.get("/api/auth/me") { header("Authorization", "Bearer $token") }.andExpect { status { isForbidden() } }
        val antigoAdmin = tokenAdmin()
        db.update("UPDATE usuario SET ong_id=NULL, perfil='admin_plataforma' WHERE id=10")
        mvc.get("/api/auth/me") { header("Authorization", "Bearer $antigoAdmin") }.andExpect { status { isOk() }; jsonPath("$.perfil") { value("ADMIN_PLATAFORMA") } }
        mvc.get("/api/ong/animais") { header("Authorization", "Bearer $antigoAdmin") }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `plataforma provisiona ong e administrador pendente sem credencial utilizavel`() {
        db.update("UPDATE usuario SET ong_id=NULL, perfil='admin_plataforma' WHERE id=10")
        val now = Instant.now()
        val claims = JwtClaimsSet.builder().subject("10").issuedAt(now).expiresAt(now.plusSeconds(3600)).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
        mvc.get("/api/plataforma/ongs/1") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() }; jsonPath("$.criadoEm") { value(null) } }
        mvc.post("/api/plataforma/ongs") {
            header("Authorization", "Bearer $token"); contentType=MediaType.APPLICATION_JSON
            content="""{"nome":"ONG","email":"ong@exemplo.invalid","identificacao":"invalida","administrador":{"nome":"Pessoa","email":"alguem@exemplo.invalid"}}"""
        }.andExpect { status { isBadRequest() } }
        val result = mvc.post("/api/plataforma/ongs") {
            header("Authorization", "Bearer $token")
            contentType=MediaType.APPLICATION_JSON
            content="""{"nome":"ONG Sintetica","email":"ong@exemplo.invalid","administrador":{"nome":"Pessoa Teste","email":"pendente@exemplo.invalid"}}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val ongId=json.readTree(result.response.contentAsString).get("id").asInt()
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM usuario WHERE email='pendente@exemplo.invalid' AND ong_id=? AND ativo=false AND credencial_pendente=true",Int::class.java,ongId))
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM auditoria_institucional WHERE ong_id=? AND operacao='ADMIN_INICIAL_PROVISIONADO' AND usuario_alvo_id IS NOT NULL",Int::class.java,ongId))
        mvc.get("/api/plataforma/ongs/$ongId") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() }; jsonPath("$.situacao") { value("ATIVA") } }
        val update="""{"nome":"ONG Atualizada","email":"ong@exemplo.invalid","versao":0}"""
        mvc.put("/api/plataforma/ongs/$ongId") { header("Authorization", "Bearer $token"); contentType=MediaType.APPLICATION_JSON; content=update.replace("\"versao\":0", "\"versao\":0.5") }.andExpect { status { isBadRequest() } }
        mvc.put("/api/plataforma/ongs/$ongId") { header("Authorization", "Bearer $token"); contentType=MediaType.APPLICATION_JSON; content=update }.andExpect { status { isOk() }; jsonPath("$.versao") { value(1) } }
        mvc.put("/api/plataforma/ongs/$ongId") { header("Authorization", "Bearer $token"); contentType=MediaType.APPLICATION_JSON; content=update }.andExpect { status { isConflict() } }
        mvc.post("/api/auth/login") { contentType=MediaType.APPLICATION_JSON; content="""{"email":"pendente@exemplo.invalid","senha":"SenhaForte123!"}""" }.andExpect { status { isUnauthorized() } }
        db.update("UPDATE usuario SET ativo=true, senha_hash=? WHERE email='pendente@exemplo.invalid'",encoder.encode("SenhaForte123!"))
        mvc.post("/api/auth/login") { contentType=MediaType.APPLICATION_JSON; content="""{"email":"pendente@exemplo.invalid","senha":"SenhaForte123!"}""" }.andExpect { status { isUnauthorized() } }
        val pendingId=db.queryForObject("SELECT id FROM usuario WHERE email='pendente@exemplo.invalid'",Int::class.java)!!
        val pendingClaims=JwtClaimsSet.builder().subject(pendingId.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build()
        val pendingToken=jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),pendingClaims)).tokenValue
        mvc.get("/api/auth/me") { header("Authorization", "Bearer $pendingToken") }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `somente perfil global pode acessar administracao institucional`() {
        val token=tokenAdmin()
        for (perfil in listOf("usuario", "administrador", "voluntario", "veterinario")) {
            db.update("UPDATE usuario SET perfil=?, ong_id=? WHERE id=10",perfil,if(perfil=="usuario") null else 1)
            mvc.get("/api/plataforma/ongs") { header("Authorization", "Bearer $token") }.andExpect { status { isForbidden() } }
            assertEquals(403,assertThrows(ResponseStatusException::class.java) { ongService.listar(br.com.sysanimais.api.usuarios.dto.UsuarioResponse(10,"", "",br.com.sysanimais.api.usuarios.domain.Perfil.USUARIO,null),0,20) }.statusCode.value())
        }
        mvc.get("/api/plataforma/ongs").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `nao permite atribuir perfil global e servico exige administrador ativo`() {
        val token = tokenAdmin()
        mvc.post("/api/usuarios/cadastro") { contentType=MediaType.APPLICATION_JSON; content="""{"nome":"Teste","email":"global@teste.invalid","senha":"SenhaForte123!","perfil":"ADMIN_PLATAFORMA"}""" }.andExpect { status { isBadRequest() } }
        mvc.post("/api/usuarios/internos") { header("Authorization", "Bearer $token"); contentType=MediaType.APPLICATION_JSON; content="""{"nome":"Teste","email":"global@teste.invalid","perfil":"ADMIN_PLATAFORMA"}""" }.andExpect { status { isBadRequest() } }
        db.update("UPDATE usuario SET perfil='voluntario' WHERE id=10")
        val request = CriarFuncionarioRequest("Teste", "novo@teste.invalid", Perfil.VOLUNTARIO)
        val actor = UsuarioResponse(10,"Admin","admin@teste.com",Perfil.ADMINISTRADOR,1)
        val error = assertThrows(EquipeException::class.java) { equipeService.criar(actor, request) }
        assertEquals(403, error.status.value())
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = 'global@teste.invalid'", Int::class.java))
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
                with { request -> request.remoteAddr = "192.0.2.180"; request }
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
            with { request -> request.remoteAddr = "192.0.2.${testOrigin.incrementAndGet()}"; request }
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"admin@teste.com","senha":"SenhaForte123!"}"""
        }.andReturn()
        return json.readTree(login.response.contentAsString).get("accessToken").asText()
    }
    @Test
    fun `revogacao de token global impede suspensao antes de adquirir bloqueio`() {
        db.update("UPDATE usuario SET ong_id=NULL,perfil='admin_plataforma' WHERE id=10")
        val ator=UsuarioResponse(10,"Global","global@teste.invalid",Perfil.ADMIN_PLATAFORMA,null)
        db.update("UPDATE usuario SET token_versao=token_versao+1 WHERE id=10")
        val erro=assertThrows(ResponseStatusException::class.java) {
            ongService.situacao(ator,1,br.com.sysanimais.api.ongs.domain.EstadoOng.SUSPENSA,"Teste")
        }
        assertEquals(403,erro.statusCode.value())
        assertEquals("ATIVA",db.queryForObject("SELECT situacao FROM ong WHERE id=1",String::class.java))
    }

}
