package br.com.sysanimais.api.animais.dto

import br.com.sysanimais.api.animais.domain.*
import jakarta.validation.constraints.*
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.time.Instant
import java.time.LocalDate

class VersaoDeserializer : JsonDeserializer<Long>() {
 override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Long {
  if (p.currentToken != JsonToken.VALUE_NUMBER_INT) throw com.fasterxml.jackson.databind.JsonMappingException.from(p, "Versão inválida")
  return p.longValue
 }
}

data class AnimalRequest(
 @field:NotBlank @field:Size(max=100) val nome: String,
 @field:NotNull val especie: EspecieAnimal,
 @field:NotNull val sexo: SexoAnimal,
 @field:NotNull val porte: PorteAnimal,
 @field:PastOrPresent val nascimentoEstimado: LocalDate? = null,
 @field:Size(max=2000) val descricao: String? = null,
 @field:Size(max=2000) val historia: String? = null,
 @field:PastOrPresent val dataResgate: LocalDate? = null,
 @field:Size(max=255) val origemResgate: String? = null,
 @field:Size(max=2000) val observacoesResgate: String? = null,
 @field:NotNull @field:PastOrPresent val dataEntrada: LocalDate,
 @field:PositiveOrZero @field:JsonDeserialize(using=VersaoDeserializer::class) val versao: Long? = null
)
data class SituacaoRequest(@field:NotNull val situacao: SituacaoAnimal, @field:NotNull @field:PositiveOrZero @field:JsonDeserialize(using=VersaoDeserializer::class) val versao: Long? = null)
data class FotoResponse(val id: Long, val principal: Boolean, val url: String)
data class AnimalResponse(
 val id: Long, val ongId: Int, val nome: String, val especie: EspecieAnimal,
 val sexo: SexoAnimal, val porte: PorteAnimal, val nascimentoEstimado: LocalDate?,
 val descricao: String?, val historia: String?, val dataResgate: LocalDate?,
 val origemResgate: String?, val observacoesResgate: String?, val dataEntrada: LocalDate,
 val situacao: SituacaoAnimal, val situacaoAtualizadaEm: Instant, val situacaoAlteradaPor: Int,
 val ativo: Boolean, val publicado: Boolean, val criadoEm: Instant, val atualizadoEm: Instant,
 val fotos: List<FotoResponse>, val versao: Long
)

data class AnimalPublicoResponse(val id: Long, val nome: String, val especie: EspecieAnimal, val sexo: SexoAnimal, val porte: PorteAnimal, val descricao: String?, val fotos: List<FotoResponse>)

data class PaginaResponse<T>(val content: List<T>, val number: Int, val size: Int, val totalElements: Long, val totalPages: Int)
