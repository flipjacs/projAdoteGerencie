package br.com.sysanimais.api.financeiro.dto

import br.com.sysanimais.api.financeiro.domain.*
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant

/** Same plain decimal syntax and DECIMAL(12,2) limits as Donation::AMOUNT_PATTERN. */
class DinheiroDeserializer:JsonDeserializer<BigDecimal>() {
 override fun deserialize(p:JsonParser,c:DeserializationContext):BigDecimal {
  if(p.currentToken!=JsonToken.VALUE_STRING || !Regex("^(?!0(?:\\.0{1,2})?$)(?:0|[1-9][0-9]{0,9})(?:\\.[0-9]{1,2})?$").matches(p.text)) throw JsonMappingException.from(p,"Valor deve ser string decimal positiva, até 9999999999.99 e duas casas")
  return p.text.toBigDecimal().setScale(2)
 }
}
data class RegistrarDoacaoRequest(@field:NotNull @field:Positive val ongId:Int?,@field:NotNull @param:JsonDeserialize(using=DinheiroDeserializer::class) val valor:BigDecimal?,@field:Size(max=2000) val mensagem:String?=null)
data class RegistrarMovimentoRequest(@field:NotNull val tipo:TipoMovimento?,@field:NotNull val categoria:CategoriaFinanceira?,@field:NotBlank @field:Size(max=255) val descricao:String?,@field:NotNull @param:JsonDeserialize(using=DinheiroDeserializer::class) val valor:BigDecimal?)
data class CancelamentoFinanceiroRequest(@field:Size(max=1000) val justificativa:String?=null)
data class PessoaFinanceira(val id:Int,val nome:String)
data class OngFinanceira(val id:Int,val nome:String)
data class OrigemFinanceira(val tipo:String,val id:Long?)
data class EventoFinanceiro(val operacao:String,val anterior:String?,val posterior:String,val justificativa:String?,val ocorridoEm:Instant,@get:JsonInclude(JsonInclude.Include.NON_NULL) val ator:PessoaFinanceira?)
data class DoacaoResponse(val id:Long,val ong:OngFinanceira,val valor:String,val status:StatusDoacao,val mensagem:String?,val confirmadoEm:Instant?,val canceladoEm:Instant?,val justificativaCancelamento:String?,val createdAt:Instant,val updatedAt:Instant,val versao:Long,val historico:List<EventoFinanceiro>,@get:JsonInclude(JsonInclude.Include.NON_NULL) val doador:PessoaFinanceira?,@get:JsonInclude(JsonInclude.Include.NON_NULL) val confirmadoPor:PessoaFinanceira?,@get:JsonInclude(JsonInclude.Include.NON_NULL) val canceladoPor:PessoaFinanceira?)
data class MovimentoFinanceiroResponse(val id:Long,val ongId:Int,val tipo:TipoMovimento,val categoria:CategoriaFinanceira,val descricao:String,val valor:String,val status:StatusMovimento,val origem:OrigemFinanceira,val registradoPor:PessoaFinanceira?,val aprovadoPor:PessoaFinanceira?,val confirmadoPor:PessoaFinanceira?,val canceladoPor:PessoaFinanceira?,val aprovadoEm:Instant?,val confirmadoEm:Instant?,val canceladoEm:Instant?,val justificativaCancelamento:String?,val createdAt:Instant,val updatedAt:Instant,val versao:Long,val historico:List<EventoFinanceiro>)
data class ResumoFinanceiroResponse(val receitasRealizadas:String,val despesasRealizadas:String,val receitasPendentes:String,val despesasPendentes:String,val despesasAprovadas:String,val saldoRealizado:String)
