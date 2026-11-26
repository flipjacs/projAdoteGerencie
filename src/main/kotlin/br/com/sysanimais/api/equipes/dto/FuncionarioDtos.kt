package br.com.sysanimais.api.equipes.dto

import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.domain.Usuario
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import jakarta.validation.constraints.*

class VersaoFuncionarioDeserializer : JsonDeserializer<Long>() {
 override fun deserialize(p: JsonParser,ctx: DeserializationContext): Long {
  if(p.currentToken != JsonToken.VALUE_NUMBER_INT) throw JsonMappingException.from(p,"Versão inválida")
  return p.longValue
 }
}
data class CriarFuncionarioRequest(
 @field:NotBlank @field:Size(max=100) val nome: String,
 @field:NotBlank @field:Email @field:Size(max=120) val email: String,
 @field:NotNull val perfil: Perfil
)
data class AtualizarFuncionarioRequest(
 @field:NotBlank @field:Size(max=100) val nome: String,
 @field:NotNull @field:PositiveOrZero @field:JsonDeserialize(using=VersaoFuncionarioDeserializer::class) val versao: Long?
)
data class AlterarPerfilRequest(
 @field:NotNull val perfil: Perfil,
 @field:NotNull @field:PositiveOrZero @field:JsonDeserialize(using=VersaoFuncionarioDeserializer::class) val versao: Long?,
 @field:NotBlank @field:Size(max=500) val motivo: String
)
data class AlterarAtivoRequest(
 @field:NotNull @field:PositiveOrZero @field:JsonDeserialize(using=VersaoFuncionarioDeserializer::class) val versao: Long?,
 @field:Size(max=500) val motivo: String? = null
)
data class FuncionarioResponse(val id: Int,val ongId: Int,val nome: String,val email: String,val perfil: Perfil,val ativo: Boolean,val credencialPendente: Boolean,val versao: Long) {
 companion object { fun from(u: Usuario)=FuncionarioResponse(u.id!!,u.ongId!!,u.nome,u.email,u.perfil,u.ativo,u.credencialPendente,u.versao) }
}
