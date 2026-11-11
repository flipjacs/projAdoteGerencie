package br.com.sysanimais.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.put
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.multipart
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:animais;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "app.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"])
class AnimalApiTests @Autowired constructor(val mvc: MockMvc, val db: JdbcTemplate, val jwt: JwtEncoder, val json: ObjectMapper) {
    @BeforeEach fun setup() {
        db.update("INSERT INTO ong (id,nome,email) VALUES (101,'A','a@ong.test'),(102,'B','b@ong.test')")
        db.update("INSERT INTO usuario (id,ong_id,nome,email,senha_hash,perfil,ativo) VALUES (101,101,'Admin A','aa@teste.com','x','administrador',true),(102,102,'Admin B','bb@teste.com','x','administrador',true),(103,101,'Vet A','va@teste.com','x','veterinario',true),(104,NULL,'Publico','p@teste.com','x','usuario',true),(105,101,'Vol A','vola@teste.com','x','voluntario',true),(106,102,'Vol B','volb@teste.com','x','voluntario',true),(107,NULL,'Sem ONG','sem@teste.com','x','administrador',true)")
    }
    private fun token(id: Int): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder().subject(id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build()
        return jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }
    private val animal = """{"nome":"Luna","especie":"CAO","sexo":"FEMEA","porte":"MEDIO","dataEntrada":"2025-01-01"}"""
    @Test fun `cadastro restringe perfil e ong e impede acesso por outra ong`() {
        mvc.post("/api/animais") { header("Authorization", "Bearer ${token(104)}"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isForbidden() } }
        mvc.post("/api/animais") { header("Authorization", "Bearer ${token(103)}"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isForbidden() } }
        val response = mvc.post("/api/animais") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isCreated() }; jsonPath("$.ongId") { value(101) } }.andReturn()
        val id = json.readTree(response.response.contentAsString).get("id").asLong()
        mvc.get("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(102)}") }.andExpect { status { isNotFound() } }
        mvc.get("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(103)}") }.andExpect { status { isOk() } }
        mvc.post("/api/animais") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content=animal.dropLast(1)+",\"ongId\":102}" }.andExpect { status { isBadRequest() } }
    }
    private fun create(): Long {
        val response=mvc.post("/api/animais") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isCreated() } }.andReturn()
        return json.readTree(response.response.contentAsString).get("id").asLong()
    }
    @Test fun `edicao transicao e inativacao mantem isolamento e integridade`() {
        val id=create()
        mvc.put("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(102)}"); contentType=MediaType.APPLICATION_JSON; content=animal.dropLast(1)+",\"versao\":0}" }.andExpect { status { isNotFound() } }
        mvc.put("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content=animal.replace("Luna","Lua").dropLast(1)+",\"versao\":0}" }.andExpect { status { isOk() }; jsonPath("$.nome") { value("Lua") } }
        mvc.patch("/api/ong/animais/$id/situacao") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"ADOTADO","versao":1}""" }.andExpect { status { isConflict() } }
        mvc.patch("/api/ong/animais/$id/situacao") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"DISPONIVEL","versao":1}""" }.andExpect { status { isOk() }; jsonPath("$.situacao") { value("DISPONIVEL") } }
        mvc.patch("/api/ong/animais/$id/inativacao") { header("Authorization", "Bearer ${token(101)}") }.andExpect { status { isOk() }; jsonPath("$.ativo") { value(false) } }
        mvc.patch("/api/ong/animais/$id/inativacao") { header("Authorization", "Bearer ${token(101)}") }.andExpect { status { isOk() } }
        mvc.get("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(101)}") }.andExpect { status { isOk() }; jsonPath("$.ativo") { value(false) } }
        mvc.get("/api/animais").andExpect { status { isOk() }; jsonPath("$.totalElements") { value(0) } }
    }
    @Test fun `catalogo publico exige ativo disponivel e publicado`() {
        val id=create()
        db.update("UPDATE animal SET publicado=true, situacao='DISPONIVEL' WHERE id=?",id)
        mvc.get("/api/animais").andExpect { status { isOk() }; jsonPath("$.totalElements") { value(1) }; jsonPath("$.content[0].ongId") { doesNotExist() } }
        db.update("UPDATE animal SET ativo=false WHERE id=?",id)
        mvc.get("/api/animais").andExpect { jsonPath("$.totalElements") { value(0) } }
    }
    @Test fun `fotos validam conteudo e pertencem a ong`() {
        val id=create(); val out=ByteArrayOutputStream(); ImageIO.write(BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out)
        mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer ${token(102)}"); file(MockMultipartFile("arquivo","x.png","image/png",out.toByteArray())) }.andExpect { status { isNotFound() } }
        mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer ${token(101)}"); file(MockMultipartFile("arquivo","x.png","image/png","invalido".toByteArray())) }.andExpect { status { isBadRequest() } }
        mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer ${token(101)}"); file(MockMultipartFile("arquivo","x.png","image/png",ByteArray(5*1024*1024+1))) }.andExpect { status { isBadRequest() } }
        val result=mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer ${token(101)}"); file(MockMultipartFile("arquivo","../../x.png","image/png",out.toByteArray())) }.andExpect { status { isCreated() }; jsonPath("$.principal") { value(true) } }.andReturn()
        val photo=json.readTree(result.response.contentAsString).get("id").asLong()
        mvc.get("/api/ong/animais/$id/fotos/$photo") { header("Authorization", "Bearer ${token(101)}") }.andExpect { status { isOk() }; content { contentType("image/png") } }
        mvc.get("/api/ong/animais/$id/fotos/$photo") { header("Authorization", "Bearer ${token(102)}") }.andExpect { status { isNotFound() } }
    }
    @Test fun `voluntario cadastra e lista exclusivamente a propria ong`() {
        val a=create()
        mvc.post("/api/animais") { header("Authorization", "Bearer ${token(105)}"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isCreated() } }
        mvc.post("/api/animais") { header("Authorization", "Bearer ${token(102)}"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isCreated() } }
        mvc.get("/api/ong/animais") { header("Authorization", "Bearer ${token(101)}") }.andExpect { status { isOk() }; jsonPath("$.totalElements") { value(2) } }
        mvc.get("/api/ong/animais") { header("Authorization", "Bearer ${token(102)}") }.andExpect { status { isOk() }; jsonPath("$.totalElements") { value(1) } }
        mvc.patch("/api/ong/animais/$a/situacao") { header("Authorization", "Bearer ${token(106)}"); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"DISPONIVEL","versao":0}""" }.andExpect { status { isNotFound() } }
        mvc.patch("/api/ong/animais/$a/situacao") { header("Authorization", "Bearer ${token(103)}"); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"DISPONIVEL","versao":0}""" }.andExpect { status { isForbidden() } }
        mvc.get("/api/ong/animais") { header("Authorization", "Bearer ${token(104)}") }.andExpect { status { isForbidden() } }
        mvc.post("/api/animais") { header("Authorization", "Bearer ${token(107)}"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isForbidden() } }
    }
    @Test fun `validacao rejeita entrada desconhecida vazia e datas inconsistentes`() {
        for (bad in listOf(animal.replace("Luna","  "),animal.replace("CAO","LEAO"),animal.replace("2025-01-01","9999-01-01"),animal.dropLast(1)+",\"dataResgate\":\"2025-02-01\"}")) {
            mvc.post("/api/animais") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content=bad }.andExpect { status { isBadRequest() } }
        }
        val id=create()
        mvc.put("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content=animal.dropLast(1)+",\"ongId\":102,\"versao\":0}" }.andExpect { status { isBadRequest() } }
        mvc.patch("/api/ong/animais/$id/situacao") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"APADRINHADO","versao":0}""" }.andExpect { status { isBadRequest() } }
    }
    @Test fun `decima foto e aceita e decima primeira retorna conflito`() {
        val id=create(); val out=ByteArrayOutputStream(); ImageIO.write(BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out)
        repeat(10) { mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer ${token(101)}"); file(MockMultipartFile("arquivo","foto.png","image/png",out.toByteArray())) }.andExpect { status { isCreated() } } }
        mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer ${token(101)}"); file(MockMultipartFile("arquivo","foto.png","image/png",out.toByteArray())) }.andExpect { status { isConflict() } }
        org.junit.jupiter.api.Assertions.assertEquals(10, db.queryForObject("SELECT COUNT(*) FROM animal_foto WHERE animal_id=?",Int::class.java,id))
    }
    @Test fun `edicao rejeita versao antiga e preserva dados`() {
        val id=create()
        val dados=animal.dropLast(1)+",\"versao\":0}"
        mvc.get("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(101)}") }.andExpect { status { isOk() }; jsonPath("$.versao") { value(0) } }
        mvc.put("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content=dados.replace("Luna","Lua") }.andExpect { status { isOk() }; jsonPath("$.versao") { value(1) } }
        mvc.put("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content=dados }.andExpect { status { isConflict() } }
        mvc.get("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(101)}") }.andExpect { jsonPath("$.nome") { value("Lua") } }
        mvc.put("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isBadRequest() } }
    }
    @Test fun `situacao exige versao e rejeita estado desatualizado`() {
        val id=create(); val path="/api/ong/animais/$id/situacao"; val auth="Bearer ${token(101)}"
        mvc.get("/api/ong/animais") { header("Authorization",auth) }.andExpect { jsonPath("$.content[0].versao") { value(0) } }
        mvc.patch(path) { header("Authorization",auth); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"DISPONIVEL"}""" }.andExpect { status { isBadRequest() } }
        mvc.patch(path) { header("Authorization",auth); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"DISPONIVEL","versao":0}""" }.andExpect { status { isOk() }; jsonPath("$.versao") { value(1) } }
        mvc.patch(path) { header("Authorization",auth); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"FALECIDO","versao":0}""" }.andExpect { status { isConflict() }; jsonPath("$.detail") { value("Este animal foi atualizado por outra pessoa. Recarregue os dados antes de salvar novamente.") } }
        mvc.get("/api/ong/animais/$id") { header("Authorization",auth) }.andExpect { jsonPath("$.situacao") { value("DISPONIVEL") } }
    }
}
