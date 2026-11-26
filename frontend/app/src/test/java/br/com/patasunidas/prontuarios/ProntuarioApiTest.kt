package br.com.patasunidas.prontuarios

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

internal val clinicalJson="""{"tipo":"CONSULTA","titulo":"Avaliação","descricao":"Clínica","dataProcedimento":"2020-01-01","profissionalResponsavel":"Ana","medicamentos":"A","observacoes":null}"""
internal val recordJson=clinicalJson.dropLast(1)+""", "id":3,"animal":{"id":7,"nome":"Lua"},"registradoPor":{"id":2,"nome":"Veterinária"},"criadoEm":"2026-11-24T12:00:00Z","atualizadoEm":"2026-11-24T12:00:00Z","versao":0}"""
internal fun medicalPage(record:String)= """{"content":[$record],"number":1,"size":15,"totalElements":16,"totalPages":2}"""
internal fun form()=ClinicalForm(titulo="Avaliação",descricao="Clínica",dataProcedimento="2020-01-01",profissionalResponsavel="Ana",medicamentos="A")
class ProntuarioApiTest {
 private fun check(block:suspend(MockWebServer,ProntuarioApi)->Unit)=runBlocking {val s=MockWebServer();s.start();try{block(s,ProntuarioApi(s.url("/").toString()))}finally{s.shutdown()}}
 @Test fun validacaoUnicodeDataObrigatoriosELimites(){
  assertEquals(8,ClinicalType.entries.size);assertNull(form().copy(titulo="🐾".repeat(120)).validate());assertNotNull(form().copy(titulo="🐾".repeat(121)).validate())
  for(f in listOf(form().copy(titulo=" "),form().copy(descricao=""),form().copy(descricao="a".repeat(10001)),form().copy(dataProcedimento="2999-01-01"),form().copy(dataProcedimento="2020-02-30"),form().copy(dataProcedimento="0999-01-01"),form().copy(profissionalResponsavel="a".repeat(121)),form().copy(medicamentos="a".repeat(5001)),form().copy(observacoes="a".repeat(5001))))assertNotNull(f.validate())
 }
 @Test fun seteEndpointsFiltrosPaginaETipos()=check{s,a->
  for(t in ClinicalType.entries){s.enqueue(MockResponse().setBody(recordJson.replace("CONSULTA",t.name)));val r=a.create("jwt",7,form().copy(tipo=t));assertEquals(t,r.data.tipo);val q=s.takeRequest();assertEquals("POST",q.method);assertEquals("Bearer jwt",q.getHeader("Authorization"));val j=JSONObject(q.body.readUtf8());assertEquals(7,j.length());assertTrue(j.isNull("observacoes"));assertFalse(j.has("versao"))}
  s.enqueue(MockResponse().setBody(medicalPage(recordJson)));val p=a.listInstitutional("jwt",7,1,ClinicalType.VACINA,"2020-01-01","2020-12-31");assertEquals(16L,p.total);assertEquals(1,p.page);assertEquals("/api/ong/animais/7/prontuarios?page=1&size=15&tipo=VACINA&de=2020-01-01&ate=2020-12-31",s.takeRequest().path)
  s.enqueue(MockResponse().setBody(recordJson));val original=a.institutionalDetails("jwt",7,3);assertEquals("/api/ong/animais/7/prontuarios/3",s.takeRequest().path)
  s.enqueue(MockResponse().setBody(recordJson));a.correct("jwt",7,original,form().copy(titulo="Novo",medicamentos=" ")," Revisão ");val q=s.takeRequest();assertEquals("PATCH",q.method);assertEquals("/api/ong/animais/7/prontuarios/3/corrigir",q.path);val j=JSONObject(q.body.readUtf8());assertEquals(setOf("titulo","medicamentos","justificativa","versao"),j.keys().asSequence().toSet());assertTrue(j.isNull("medicamentos"));assertEquals(0,j.getLong("versao"));assertEquals("Revisão",j.getString("justificativa"))
  val history="""{"id":4,"criadoEm":"2026-11-24T12:01:00Z","justificativa":"Revisão","corrigidoPor":{"id":2,"nome":"Ana"},"dadosAnteriores":$clinicalJson,"dadosNovos":$clinicalJson,"versaoAnterior":0,"versaoPosterior":1}"""
  s.enqueue(MockResponse().setBody(medicalPage(history)));assertEquals(1L,a.corrections("jwt",7,3,1).items.single().versaoPosterior);assertEquals("/api/ong/animais/7/prontuarios/3/correcoes?page=1&size=15",s.takeRequest().path)
  s.enqueue(MockResponse().setBody(medicalPage(recordJson)));val personal=a.listPersonal("jwt",7).items.single();assertEquals("Lua",personal.animalName);assertEquals(setOf("id","animalId","animalName","data"),PersonalRecord::class.java.declaredFields.filter{!it.isSynthetic&&!java.lang.reflect.Modifier.isStatic(it.modifiers)}.map{it.name}.toSet());assertEquals("/api/me/animais-adotados/7/prontuarios?page=0&size=15",s.takeRequest().path)
  s.enqueue(MockResponse().setBody(recordJson));a.personalDetails("jwt",7,3);assertEquals("/api/me/animais-adotados/7/prontuarios/3",s.takeRequest().path)
  for((draft,reason) in listOf(form() to "Motivo",form().copy(titulo="Novo") to " ",form().copy(titulo="Novo") to "a".repeat(2001))){try{a.correct("jwt",7,original,draft,reason);fail()}catch(_:IllegalArgumentException){}}
 }
 @Test fun falhasNaoRepetemEscrita()=check{s,a->for(code in listOf(400,401,403,404,409)){s.enqueue(MockResponse().setResponseCode(code).setBody("""{"code":"PRONTUARIO_CONFLITO"}"""));try{a.create("t",7,form());fail()}catch(e:ProntuarioHttpException){assertEquals(code,e.status);assertEquals("PRONTUARIO_CONFLITO",e.code)}};assertEquals(5,s.requestCount)}
 @Test fun rebasePreservaIntencaoSemSobrescreverMudancasRemotas(){val old=form();val draft=old.copy(titulo="Local",observacoes="Nota");val remote=old.copy(descricao="Remota",medicamentos="B");assertEquals(remote.copy(titulo="Local",observacoes="Nota"),rebaseClinicalDraft(old,draft,remote))}
}
