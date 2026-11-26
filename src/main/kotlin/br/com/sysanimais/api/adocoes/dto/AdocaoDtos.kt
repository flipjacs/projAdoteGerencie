package br.com.sysanimais.api.adocoes.dto

import br.com.sysanimais.api.adocoes.domain.StatusSolicitacao
import jakarta.validation.constraints.*
import java.time.Instant

data class SolicitarAdocaoRequest(@field:NotNull @field:Positive val animalId:Long?,@field:Size(max=2000) val mensagem:String?=null)
data class JustificativaRequest(@field:NotBlank @field:Size(max=1000) val justificativa:String)
data class ConfirmarEntregaRequest(@field:AssertTrue val entregaConfirmada:Boolean=false)
data class AnimalResumo(val id:Long,val nome:String)
data class OngResumo(val id:Int,val nome:String)
data class PessoaResumo(val id:Int,val nome:String,val email:String?=null)
data class EntregaResponse(val id:Long,val dataEntrega:Instant)
data class HistoricoResponse(val operacao:String,val anterior:String?,val posterior:String,val justificativa:String?,val ocorridoEm:Instant)
data class SolicitacaoResponse(val id:Long,val animal:AnimalResumo,val ong:OngResumo,val status:StatusSolicitacao,val mensagem:String?,val justificativaRejeicao:String?,val justificativaCancelamento:String?,val analisadoEm:Instant?,val canceladoEm:Instant?,val adocao:EntregaResponse?,val createdAt:Instant,val updatedAt:Instant,val versao:Long,val solicitante:PessoaResumo?,val analisadoPor:PessoaResumo?,val canceladoPor:PessoaResumo?,val historico:List<HistoricoResponse>)
