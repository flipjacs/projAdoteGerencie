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
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:animais;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "app.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "app.animal-photos-dir=build/test-fotos-fase134"])
class AnimalApiTests @Autowired constructor(val mvc: MockMvc, val db: JdbcTemplate, val jwt: JwtEncoder, val json: ObjectMapper, val em: jakarta.persistence.EntityManager, val service: br.com.sysanimais.api.animais.service.AnimalService) {
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
    @Test fun `publicacao independente da situacao protege detalhes e filtra catalogo`() {
        val id=create()
        mvc.get("/api/animais/$id").andExpect { status { isNotFound() } }
        mvc.patch("/api/ong/animais/$id/publicar") { header("Authorization", "Bearer ${token(103)}") }.andExpect { status { isForbidden() } }
        mvc.patch("/api/ong/animais/$id/publicar") { header("Authorization", "Bearer ${token(102)}") }.andExpect { status { isNotFound() } }
        mvc.patch("/api/ong/animais/$id/publicar") { header("Authorization", "Bearer ${token(101)}") }.andExpect { status { isOk() }; jsonPath("$.publicado") { value(true) }; jsonPath("$.situacao") { value("EM_TRATAMENTO") } }
        mvc.get("/api/animais/$id").andExpect { status { isNotFound() } }
        val current=service.get(br.com.sysanimais.api.usuarios.dto.UsuarioResponse(101,"Admin A","aa@teste.com",br.com.sysanimais.api.usuarios.domain.Perfil.ADMINISTRADOR,101),id)
        mvc.patch("/api/ong/animais/$id/situacao") { header("Authorization", "Bearer ${token(101)}");contentType=MediaType.APPLICATION_JSON;content="""{"situacao":"DISPONIVEL","versao":${current.versao}}""" }.andExpect { status { isOk() } }
        mvc.get("/api/animais?especie=CAO&sexo=FEMEA&porte=MEDIO&size=1").andExpect { status { isOk() };jsonPath("$.totalElements") { value(1) } }
        mvc.get("/api/animais?page=1&size=1").andExpect { status { isOk() };jsonPath("$.content.length()") { value(0) };jsonPath("$.totalElements") { value(1) } }
        mvc.get("/api/animais?page=-1").andExpect { status { isBadRequest() } }
        mvc.get("/api/animais?size=101").andExpect { status { isBadRequest() } }
        mvc.get("/api/animais?especie=GATO").andExpect { jsonPath("$.totalElements") { value(0) } }
        mvc.get("/api/animais?especie=INVALIDA").andExpect { status { isBadRequest() } }
        mvc.get("/api/animais/$id").andExpect { status { isOk() };jsonPath("$.id") { value(id) };jsonPath("$.ong.nome") { value("A") };jsonPath("$.situacaoAlteradaPor") { doesNotExist() } }
        mvc.patch("/api/ong/animais/$id/despublicar") { header("Authorization", "Bearer ${token(105)}") }.andExpect { status { isOk() };jsonPath("$.publicado") { value(false) } }
        mvc.get("/api/animais/$id").andExpect { status { isNotFound() } }
        mvc.get("/api/animais").andExpect { jsonPath("$.totalElements") { value(0) } }
    }
    @Test fun `suspensao revoga token esconde catalogo e reativacao preserva dados`() {
        db.update("INSERT INTO usuario (id,ong_id,nome,email,senha_hash,perfil,ativo) VALUES (108,NULL,'Global','global@teste.invalid','x','admin_plataforma',true)")
        val staffToken=token(101)
        val id=create()
        val bytes=ByteArrayOutputStream(); ImageIO.write(BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes)
        val uploaded=mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer $staffToken"); file(MockMultipartFile("arquivo","foto.png","image/png",bytes.toByteArray())) }.andExpect { status { isCreated() } }.andReturn()
        val fotoId=json.readTree(uploaded.response.contentAsString).get("id").asLong()
        db.update("UPDATE animal SET publicado=true, situacao='DISPONIVEL' WHERE id=?",id); em.clear()
        mvc.get("/api/animais").andExpect { jsonPath("$.totalElements") { value(1) } }
        mvc.get("/api/animais/$id/fotos/$fotoId").andExpect { status { isOk() } }
        mvc.patch("/api/plataforma/ongs/101/suspensao") { header("Authorization", "Bearer ${token(108)}"); contentType=MediaType.APPLICATION_JSON; content="""{"motivo":"Teste de suspensão"}""" }.andExpect { status { isOk() }; jsonPath("$.situacao") { value("SUSPENSA") } }
        em.clear()
        mvc.get("/api/ong/animais/$id") { header("Authorization", "Bearer $staffToken") }.andExpect { status { isForbidden() }; jsonPath("$.code") { value("ACESSO_NEGADO") } }
        mvc.post("/api/animais") { header("Authorization", "Bearer $staffToken"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isForbidden() } }
        mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer $staffToken"); file(MockMultipartFile("arquivo","foto.png","image/png",bytes.toByteArray())) }.andExpect { status { isForbidden() } }
        mvc.get("/api/animais").andExpect { jsonPath("$.totalElements") { value(0) } }
        mvc.get("/api/animais/$id/fotos/$fotoId").andExpect { status { isNotFound() } }
        mvc.get("/api/auth/me") { header("Authorization", "Bearer ${token(102)}") }.andExpect { status { isOk() } }
        db.update("UPDATE usuario SET ativo=false WHERE id=105")
        mvc.patch("/api/plataforma/ongs/101/reativacao") { header("Authorization", "Bearer ${token(108)}") }.andExpect { status { isOk() } }
        em.clear()
        mvc.get("/api/ong/animais/$id") { header("Authorization", "Bearer $staffToken") }.andExpect { status { isOk() } }
        mvc.get("/api/auth/me") { header("Authorization", "Bearer ${token(105)}") }.andExpect { status { isForbidden() } }
        mvc.get("/api/animais").andExpect { jsonPath("$.totalElements") { value(1) } }
        mvc.get("/api/animais/$id/fotos/$fotoId").andExpect { status { isOk() } }
        org.junit.jupiter.api.Assertions.assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM auditoria_institucional WHERE ong_id=101",Int::class.java))
    }
    @Test fun `global nao herda permissoes internas nem com ong invalida`() {
        db.update("INSERT INTO usuario (id,ong_id,nome,email,senha_hash,perfil,ativo) VALUES (108,NULL,'Global','global@teste.invalid','x','admin_plataforma',true)")
        val id=create(); val global=token(108)
        mvc.get("/api/ong/animais") { header("Authorization", "Bearer $global") }.andExpect { status { isForbidden() } }
        mvc.get("/api/ong/animais/$id") { header("Authorization", "Bearer $global") }.andExpect { status { isForbidden() } }
        mvc.post("/api/animais") { header("Authorization", "Bearer $global"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isForbidden() } }
        mvc.put("/api/ong/animais/$id") { header("Authorization", "Bearer $global"); contentType=MediaType.APPLICATION_JSON; content=animal.dropLast(1)+",\"versao\":0}" }.andExpect { status { isForbidden() } }
        mvc.patch("/api/ong/animais/$id/situacao") { header("Authorization", "Bearer $global"); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"DISPONIVEL","versao":0}""" }.andExpect { status { isForbidden() } }
        mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer $global"); file(MockMultipartFile("arquivo","x.png","image/png",byteArrayOf(1))) }.andExpect { status { isForbidden() } }
        mvc.get("/api/ong/animais/$id/fotos/1") { header("Authorization", "Bearer $global") }.andExpect { status { isForbidden() } }
        val fake=br.com.sysanimais.api.usuarios.dto.UsuarioResponse(108,"Global","global@teste.invalid",br.com.sysanimais.api.usuarios.domain.Perfil.ADMIN_PLATAFORMA,101)
        val ex=org.junit.jupiter.api.assertThrows<org.springframework.web.server.ResponseStatusException> { service.list(fake,null,0,20) }
        org.junit.jupiter.api.Assertions.assertEquals(403,ex.statusCode.value())
        db.update("UPDATE usuario SET ong_id=101 WHERE id=108")
        em.clear()
        mvc.get("/api/auth/me") { header("Authorization", "Bearer $global") }.andExpect { status { isForbidden() } }
    }
    @Test fun `cadastro restringe perfil e ong e impede acesso por outra ong`() {
        mvc.post("/api/animais") { header("Authorization", "Bearer ${token(104)}"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isForbidden() } }
        mvc.post("/api/animais") { header("Authorization", "Bearer ${token(103)}"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isForbidden() } }
        val response = mvc.post("/api/animais") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content=animal }.andExpect { status { isCreated() }; jsonPath("$.ongId") { value(101) } }.andReturn()
        val id = json.readTree(response.response.contentAsString).get("id").asLong()
        mvc.get("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(102)}") }.andExpect { status { isNotFound() } }
        mvc.get("/api/ong/animais/$id") { header("Authorization", "Bearer ${token(103)}") }.andExpect { status { isOk() } }
        mvc.post("/api/animais") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content=animal.dropLast(1)+",\"ongId\":102}" }.andExpect { status { isBadRequest() } }
    }
    @Test fun `veterinario nao altera animal nem por chamada direta ao servico`() {
        val id=create()
        val vet=br.com.sysanimais.api.usuarios.dto.UsuarioResponse(103,"Veterinário","vet@teste.invalid",br.com.sysanimais.api.usuarios.domain.Perfil.VETERINARIO,101)
        val ex=org.junit.jupiter.api.assertThrows<org.springframework.web.server.ResponseStatusException> { service.deactivate(vet,id) }
        org.junit.jupiter.api.Assertions.assertEquals(403,ex.statusCode.value())
        org.junit.jupiter.api.Assertions.assertEquals(true,db.queryForObject("SELECT ativo FROM animal WHERE id=?",Boolean::class.java,id))
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
        mvc.patch(path) { header("Authorization",auth); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"FALECIDO","versao":0}""" }.andExpect { status { isConflict() }; jsonPath("$.detail") { value("Este animal foi atualizado por outra pessoa. Recarregue os dados antes de salvar novamente.") }; jsonPath("$.code") { value("ANIMAL_VERSION_CONFLICT") } }
        mvc.get("/api/ong/animais/$id") { header("Authorization",auth) }.andExpect { jsonPath("$.situacao") { value("DISPONIVEL") } }
    }
    @Test fun `matriz de transicoes respeita estado e idempotencia`() {
        val id=create(); val estados=listOf("EM_TRATAMENTO","DISPONIVEL","EM_PROCESSO_ADOCAO","ADOTADO","FALECIDO")
        val permitidas=mapOf("EM_TRATAMENTO" to setOf("DISPONIVEL","FALECIDO"), "DISPONIVEL" to setOf("EM_TRATAMENTO","EM_PROCESSO_ADOCAO","FALECIDO"), "EM_PROCESSO_ADOCAO" to setOf("DISPONIVEL","EM_TRATAMENTO","FALECIDO"))
        for(origem in estados) for(destino in estados) {
            em.flush();em.clear();db.update("UPDATE animal SET situacao=?, ativo=true WHERE id=?",origem,id)
            val version=db.queryForObject("SELECT versao FROM animal WHERE id=?",Long::class.java,id)!!
            val result=mvc.patch("/api/ong/animais/$id/situacao") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"$destino","versao":$version}""" }.andReturn().response.status
            org.junit.jupiter.api.Assertions.assertEquals(if(origem==destino || destino in permitidas[origem].orEmpty()) 200 else 409,result,"$origem -> $destino")
        }
        em.flush();em.clear();db.update("UPDATE animal SET ativo=false WHERE id=?",id)
        val version=db.queryForObject("SELECT versao FROM animal WHERE id=?",Long::class.java,id)!!
        mvc.patch("/api/ong/animais/$id/situacao") { header("Authorization", "Bearer ${token(101)}"); contentType=MediaType.APPLICATION_JSON; content="""{"situacao":"DISPONIVEL","versao":$version}""" }.andExpect { status { isConflict() } }
    }
    @Test fun `foto com arquivo ausente retorna 404 e preserva metadado`() {
        val id=create(); val out=ByteArrayOutputStream(); ImageIO.write(BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out)
        val result=mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer ${token(101)}"); file(MockMultipartFile("arquivo","foto.png","image/png",out.toByteArray())) }.andExpect { status { isCreated() } }.andReturn()
        val foto=json.readTree(result.response.contentAsString).get("id").asLong()
        val name=db.queryForObject("SELECT arquivo FROM animal_foto WHERE id=?",String::class.java,foto)!!
        org.junit.jupiter.api.Assertions.assertTrue(name.matches(Regex("[0-9a-f-]{36}")))
        java.nio.file.Files.delete(java.nio.file.Path.of("build/test-fotos-fase134").resolve(name))
        val response=mvc.get("/api/ong/animais/$id/fotos/$foto") { header("Authorization", "Bearer ${token(101)}") }.andExpect { status { isNotFound() } }.andReturn().response.contentAsString
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("test-fotos-fase134"))
        org.junit.jupiter.api.Assertions.assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM animal_foto WHERE id=?",Int::class.java,foto))
    }
    @Test fun `foto privada nao segue link simbolico`() {
        val id=create(); val out=ByteArrayOutputStream(); ImageIO.write(BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out)
        val result=mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer ${token(101)}"); file(MockMultipartFile("arquivo","foto.png","image/png",out.toByteArray())) }.andExpect { status { isCreated() } }.andReturn()
        val foto=json.readTree(result.response.contentAsString).get("id").asLong();val name=db.queryForObject("SELECT arquivo FROM animal_foto WHERE id=?",String::class.java,foto)!!
        val dir=java.nio.file.Path.of("build/test-fotos-fase134").toAbsolutePath();val target=java.nio.file.Files.createTempFile("b02-segredo-", ".tmp")
        try {
            java.nio.file.Files.writeString(target,"segredo-b02");val path=dir.resolve(name);java.nio.file.Files.delete(path);java.nio.file.Files.createSymbolicLink(path,target)
            val response=mvc.get("/api/ong/animais/$id/fotos/$foto") { header("Authorization", "Bearer ${token(101)}") }.andExpect { status { isNotFound() } }.andReturn().response.contentAsString
            org.junit.jupiter.api.Assertions.assertFalse(response.contains("segredo-b02"))
        } finally { java.nio.file.Files.deleteIfExists(target) }
    }
    @Test fun `jpeg com extensao falsa e identificado pelo conteudo`() {
        val id=create();val out=ByteArrayOutputStream();ImageIO.write(BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"jpg",out)
        val result=mvc.multipart("/api/ong/animais/$id/fotos") { header("Authorization", "Bearer ${token(101)}");file(MockMultipartFile("arquivo","../../imagem.png","image/png",out.toByteArray())) }.andExpect { status { isCreated() } }.andReturn()
        val foto=json.readTree(result.response.contentAsString).get("id").asLong()
        mvc.get("/api/ong/animais/$id/fotos/$foto") { header("Authorization", "Bearer ${token(101)}") }.andExpect { status { isOk() };content { contentType("image/jpeg") } }
    }
    @Test fun `token revogado durante espera nao permite consulta interna`() {
        val ator=br.com.sysanimais.api.usuarios.dto.UsuarioResponse(101,"Admin A","aa@teste.com",br.com.sysanimais.api.usuarios.domain.Perfil.ADMINISTRADOR,101)
        db.update("UPDATE usuario SET token_versao=token_versao+1 WHERE id=101")
        em.clear()
        val erro=org.junit.jupiter.api.assertThrows<org.springframework.web.server.ResponseStatusException> { service.list(ator,null,0,20) }
        org.junit.jupiter.api.Assertions.assertEquals(403,erro.statusCode.value())
    }

}
