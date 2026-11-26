package br.com.sysanimais.api.ongs.dto

import br.com.sysanimais.api.ongs.domain.Ong
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.Instant
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

class VersaoOngDeserializer : JsonDeserializer<Long>() {
 override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Long {
  if(p.currentToken != JsonToken.VALUE_NUMBER_INT) throw JsonMappingException.from(p,"Versão inválida")
  return p.longValue
 }
}

data class AdministradorInicial(@field:NotBlank @field:Size(max=100) val nome: String,
 @field:NotBlank @field:Email @field:Size(max=120) val email: String)
data class CriarOngRequest(@field:NotBlank @field:Size(max=120) val nome: String,
 @field:NotBlank @field:Email @field:Size(max=120) val email: String,
 @field:Pattern(regexp="^$|^[0-9]{11}$|^[0-9]{14}$") val identificacao: String? = null,
 @field:Size(max=20) val telefone: String? = null,
 @field:Size(max=80) val cidade: String? = null,
 @field:Pattern(regexp="[A-Za-z]{2}") val estado: String? = null,
 @field:Valid @field:NotNull val administrador: AdministradorInicial)
data class AtualizarOngRequest(@field:NotBlank @field:Size(max=120) val nome: String,
 @field:NotBlank @field:Email @field:Size(max=120) val email: String,
 @field:Pattern(regexp="^$|^[0-9]{11}$|^[0-9]{14}$") val identificacao: String? = null,
 @field:Size(max=20) val telefone: String? = null,
 @field:Size(max=80) val cidade: String? = null,
 @field:Pattern(regexp="[A-Za-z]{2}") val estado: String? = null,
 @field:NotNull @field:PositiveOrZero @field:JsonDeserialize(using=VersaoOngDeserializer::class) val versao: Long?)
data class SuspenderOngRequest(@field:NotBlank @field:Size(max=500) val motivo: String)
data class OngResponse(val id: Int, val nome: String, val email: String, val identificacao: String?, val telefone: String?, val cidade: String?, val estado: String?, val situacao: String, val criadoEm: Instant?, val atualizadoEm: Instant?, val versao: Long) {
 companion object { fun from(o: Ong) = OngResponse(o.id!!,o.nome,o.email,o.identificacao,o.telefone,o.cidade,o.estado,o.situacao.name,o.criadoEm,o.atualizadoEm,o.versao) }
}
