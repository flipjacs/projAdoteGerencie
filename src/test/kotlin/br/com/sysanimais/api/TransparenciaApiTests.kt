package br.com.sysanimais.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.*
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@SpringBootTest @AutoConfigureMockMvc @Transactional
@TestPropertySource(properties=["spring.datasource.url=jdbc:h2:mem:transparencia;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","app.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"])
class TransparenciaApiTests @Autowired constructor(val mvc:MockMvc,val db:JdbcTemplate,val json:ObjectMapper,val jwt:JwtEncoder) {
 @jakarta.persistence.PersistenceContext lateinit var em:jakarta.persistence.EntityManager
 private val base="/api/ong/transparencia"
 private val public="/api/public/ongs/801/transparencia"
 private val payload="""{"titulo":"Demonstrativo","descricao":"Informação publicável","periodoInicio":"2026-11-01","periodoFim":"2026-11-30"}"""
 @BeforeEach fun fixtures(){
  // SQL timestamp literals represent UTC, matching Hibernate Instant parameters and the API period.
  db.execute("SET TIME ZONE 'UTC'")
  db.update("INSERT INTO ong(id,nome,email) VALUES(801,'ONG A','a@trans.invalid'),(802,'ONG B','b@trans.invalid')")
  for((id,ong,role) in listOf(Triple(801,801,"administrador"),Triple(802,802,"administrador"),Triple(803,801,"voluntario"),Triple(804,801,"veterinario"),Triple(805,null,"usuario"),Triple(806,null,"admin_plataforma"))) db.update("INSERT INTO usuario(id,ong_id,nome,email,senha_hash,perfil,ativo) VALUES(?,?,?,?,?,?,true)",id,ong,"Pessoa $id","u$id@trans.invalid","x",role)
 }
 private fun auth(id:Int):String {val now=Instant.now();return "Bearer "+jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().subject(id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build())).tokenValue}
 private fun post(body:String=payload,user:Int=801)=mvc.post(base){header("Authorization",auth(user));contentType=MediaType.APPLICATION_JSON;content=body}
 private fun patch(path:String,body:String?=null,user:Int=801)=mvc.patch(path){header("Authorization",auth(user));if(body!=null){contentType=MediaType.APPLICATION_JSON;content=body}}
 private fun get(path:String,user:Int=801)=mvc.get(path){header("Authorization",auth(user))}
 private fun create():Long= json.readTree(post().andExpect{status{isCreated()}}.andReturn().response.contentAsString)["id"].asLong()
 private fun movement(value:String,type:String="RECEITA",status:String="REALIZADA",ong:Int=801,created:String="2026-10-01 12:00:00",confirmed:String="2026-11-30 23:59:59"){val category=if(type=="RECEITA")"EVENTO"else"ALIMENTACAO";db.update("INSERT INTO movimentacao_financeira(ong_id,tipo,categoria,descricao,valor,status,registrado_por,confirmado_por,confirmado_em,aprovado_por,aprovado_em,criado_em,atualizado_em) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",ong,type,category,"segredo@example.invalid",value.toBigDecimal(),status,801,if(status=="REALIZADA")801 else null,if(status=="REALIZADA")confirmed else null,if(type=="DESPESA" && status in listOf("REALIZADA","APROVADA"))801 else null,if(type=="DESPESA" && status in listOf("REALIZADA","APROVADA"))confirmed else null,created,created)}
 @Test fun `snapshot congela publicacao e republicacao atualiza sem dados privados`(){
  movement("0.10");movement("0.20");movement("0.07","DESPESA");movement("100.00",status="PENDENTE");movement("999.00",ong=802);movement("500.00",confirmed="2026-12-01 00:00:00");movement("600.00",confirmed="2026-10-31 23:59:59")
  val id=create();mvc.get("$public/$id").andExpect{status{isNotFound()}}
  patch("$base/$id/publicar").andExpect{status{isOk()};jsonPath("$.dados.resultadoLiquido"){value("0.23")};jsonPath("$.dados.quantidadeMovimentacoes"){value(3)}}
  movement("1.00");val response=mvc.get("$public/$id").andExpect{status{isOk()};jsonPath("$.dados.resultadoLiquido"){value("0.23")};jsonPath("$.status"){doesNotExist()};jsonPath("$.publicadoPor"){doesNotExist()}}.andReturn().response.contentAsString
  assertFalse(response.contains("segredo"));assertEquals(6,json.readTree(response)["dados"].size());assertEquals(setOf("id","ong","titulo","descricao","periodoInicio","periodoFim","publicadoEm","dados"),json.readTree(response).fieldNames().asSequence().toSet())
  patch("$base/$id/publicar").andExpect{status{isConflict()}};patch("$base/$id","""{"titulo":"Novo"}""").andExpect{status{isConflict()}}
  patch("$base/$id/despublicar").andExpect{status{isOk()};jsonPath("$.dados.resultadoLiquido"){value("0.23")};jsonPath("$.despublicadoEm"){exists()}};mvc.get("$public/$id").andExpect{status{isNotFound()}}
  patch("$base/$id/publicar").andExpect{status{isOk()};jsonPath("$.dados.resultadoLiquido"){value("1.23")};jsonPath("$.despublicadoEm"){exists()}}
 }
 @Test fun `rascunho atualiza apenas quando periodo muda e rejeita campos invalidos`(){val id=create();movement("10.00");patch("$base/$id","""{"titulo":" Novo ","descricao":null}""").andExpect{status{isOk()};jsonPath("$.titulo"){value("Novo")};jsonPath("$.dados.resultadoLiquido"){value("0.00")}};patch("$base/$id","""{"periodoInicio":"2026-11-01"}""").andExpect{jsonPath("$.dados.resultadoLiquido"){value("0.00")}};patch("$base/$id","""{"periodoInicio":"2026-11-02"}""").andExpect{jsonPath("$.dados.resultadoLiquido"){value("10.00")}}
  for(body in listOf("""{"titulo":12}""","""{"titulo":null}""","""{"periodoFim":null}""","""{"dados":{}}""","""{"periodoFim":"2026-02-30"}""","""{"periodoFim":"2026-01-01"}"""))patch("$base/$id",body).andExpect{status{isBadRequest()}}
  patch("$base/$id/publicar","""{"status":"PUBLICADO"}""").andExpect{status{isBadRequest()}}
  post(payload.replace("Demonstrativo","á".repeat(120))).andExpect{status{isCreated()}};post(payload.replace("Demonstrativo","😀".repeat(121))).andExpect{status{isBadRequest()}}
 }
 @Test fun `perfis e isolamento autorizam apenas operacoes previstas`(){val id=create();get(base,803).andExpect{status{isOk()}};for(user in listOf(803,804,805,806)){post(user=user).andExpect{status{isForbidden()}};patch("$base/$id/publicar",user=user).andExpect{status{isForbidden()}};get("/api/ong/relatorios/financeiro",user).andExpect{status{isForbidden()}}};for(user in listOf(804,805,806))get(base,user).andExpect{status{isForbidden()}};get("$base/$id",802).andExpect{status{isNotFound()}};patch("$base/$id/publicar",user=802).andExpect{status{isNotFound()}};mvc.get(base).andExpect{status{isUnauthorized()}};patch("$base/$id/publicar");db.update("UPDATE ong SET situacao='SUSPENSA' WHERE id=801");em.clear();mvc.get("$public/$id").andExpect{status{isNotFound()}};get(base).andExpect{status{isForbidden()}}}
 @Test fun `relatorio usa datas distintas e filtra ONG com decimal exato`(){movement("10.05");movement("3.02","DESPESA","APROVADA");movement("7.00",status="PENDENTE",created="2026-11-30 12:00:00");movement("999.00",ong=802);get("/api/ong/relatorios/financeiro?de=2026-11-30&ate=2026-11-30").andExpect{status{isOk()};jsonPath("$.receitasRealizadas"){value("10.05")};jsonPath("$.despesasAprovadas"){value("3.02")};jsonPath("$.receitasPendentes"){value("7.00")};jsonPath("$.quantidadeMovimentacoes"){value(1)};jsonPath("$.receitasPorCategoria.EVENTO"){value("10.05")}}}
 @Test fun `paginacao admite periodos repetidos filtros e limites`(){repeat(16){val id=create();patch("$base/$id/publicar").andExpect{status{isOk()}}};get(base).andExpect{jsonPath("$.content.length()"){value(15)};jsonPath("$.totalElements"){value(16)}};get("$base?page=1").andExpect{jsonPath("$.content.length()"){value(1)}};mvc.get(public).andExpect{status{isOk()};jsonPath("$.content.length()"){value(15)}};get("$base?de=2026-12-01").andExpect{jsonPath("$.totalElements"){value(0)}};for(query in listOf("page=-1","page=2147483647&size=100","size=101","status=OUTRO","de=2026-02-30","ate=2026-01-01&de=2026-02-01","ongId=802"))get("$base?$query").andExpect{status{isBadRequest()}}}
 @Test fun `projecao publica elimina dados extras acidentalmente persistidos`(){val id=create();patch("$base/$id/publicar");em.flush();em.clear();val data=db.queryForObject("SELECT dados_consolidados FROM demonstrativo_transparencia WHERE id=?",String::class.java,id)!!;val tree=json.readTree(data) as com.fasterxml.jackson.databind.node.ObjectNode;tree.put("doadorEmail","vazamento@example.invalid");db.update("UPDATE demonstrativo_transparencia SET dados_consolidados=? WHERE id=?",tree.toString(),id);val body=mvc.get("$public/$id").andExpect{status{isOk()}}.andReturn().response.contentAsString;assertFalse(body.contains("vazamento"));assertEquals(6,json.readTree(body)["dados"].size())}
}
