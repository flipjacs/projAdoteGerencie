package br.com.sysanimais.api.apadrinhamentos.dto

import br.com.sysanimais.api.apadrinhamentos.domain.*
import br.com.sysanimais.api.financeiro.dto.DinheiroDeserializer
import br.com.sysanimais.api.animais.dto.VersaoDeserializer
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class RegistrarApadrinhamentoRequest(
 @field:NotNull @field:Positive @param:JsonDeserialize(using=VersaoDeserializer::class) val animalId:Long?,
 @field:NotBlank val modalidade:String?,
 @field:NotNull @param:JsonDeserialize(using=DinheiroDeserializer::class) val valor:BigDecimal?
)
data class EncerrarApadrinhamentoRequest(@field:NotBlank val justificativa:String?)
data class AnimalApadrinhamento(val id:Long,val nome:String)
data class OngApadrinhamento(val id:Int,val nome:String)
data class PadrinhoResponse(val id:Int,val nome:String,val email:String)
data class ConfirmadorParcela(val id:Int,val nome:String)
data class ParcelaResponse(val id:Long,val numero:Int,val valor:String,val vencimento:LocalDate,val status:StatusParcela,val vencida:Boolean,val confirmadoEm:Instant?,@get:JsonInclude(JsonInclude.Include.NON_NULL) val confirmadoPor:ConfirmadorParcela?)
data class ApadrinhamentoResponse(
 val id:Long,val animal:AnimalApadrinhamento,val ong:OngApadrinhamento,val modalidade:ModalidadeApadrinhamento,
 val valor:String,val status:StatusApadrinhamento,val dataInicio:LocalDate,val dataEncerramento:LocalDate?,
 val motivoEncerramento:MotivoEncerramento?,val justificativaEncerramento:String?,val proximoVencimento:LocalDate?,
 val criadoEm:Instant,val atualizadoEm:Instant,val versao:Long,
 @get:JsonInclude(JsonInclude.Include.NON_NULL) val primeiraParcela:ParcelaResponse?=null,
 @get:JsonInclude(JsonInclude.Include.NON_NULL) val padrinho:PadrinhoResponse?=null
)
