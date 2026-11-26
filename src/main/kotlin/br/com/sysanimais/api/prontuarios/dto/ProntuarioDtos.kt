package br.com.sysanimais.api.prontuarios.dto

import br.com.sysanimais.api.prontuarios.domain.TipoProntuario
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate

/** Flat JSON input; a map preserves the difference between omitted and explicitly null fields.
 * The service accepts only the documented clinical fields (plus correction reason/version). */
class ProntuarioRequest @JsonCreator(mode=JsonCreator.Mode.DELEGATING) constructor(val campos:Map<String,JsonNode>)

data class DadosClinicos(
 val tipo:TipoProntuario, val titulo:String, val descricao:String, val dataProcedimento:LocalDate,
 val profissionalResponsavel:String?, val medicamentos:String?, val observacoes:String?
)
data class PessoaProntuario(val id:Int,val nome:String)
data class AnimalProntuario(val id:Long,val nome:String)
data class ProntuarioResponse(
 val id:Long,val animal:AnimalProntuario,val tipo:TipoProntuario,val titulo:String,val descricao:String,
 val dataProcedimento:LocalDate,val profissionalResponsavel:String?,val medicamentos:String?,val observacoes:String?,
 val registradoPor:PessoaProntuario,val criadoEm:Instant,val atualizadoEm:Instant,val versao:Long
)
/** No institutional actor, audit timestamps, version, or corrections exposed to the adopter. */
data class ProntuarioAdotanteResponse(
 val id:Long,val animal:AnimalProntuario,val tipo:TipoProntuario,val titulo:String,val descricao:String,
 val dataProcedimento:LocalDate,val profissionalResponsavel:String?,val medicamentos:String?,val observacoes:String?
)
data class CorrecaoProntuarioResponse(
 val id:Long,val criadoEm:Instant,val justificativa:String,val corrigidoPor:PessoaProntuario,
 val dadosAnteriores:DadosClinicos,val dadosNovos:DadosClinicos,val versaoAnterior:Long,val versaoPosterior:Long
)
