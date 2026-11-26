package br.com.sysanimais.api.apadrinhamentos.controller

import br.com.sysanimais.api.apadrinhamentos.domain.*
import br.com.sysanimais.api.apadrinhamentos.dto.*
import br.com.sysanimais.api.apadrinhamentos.service.*
import br.com.sysanimais.api.animais.dto.PaginaResponse
import br.com.sysanimais.api.security.UsuarioAuthenticationToken
import br.com.sysanimais.api.financeiro.service.FinanceiroException
import jakarta.validation.Valid
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.*
import org.springframework.data.domain.Page
import org.springframework.http.*
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestController
class ApadrinhamentoController(private val service:ApadrinhamentoService) {
 private fun <T> page(p:Page<T>)=PaginaResponse(p.content,p.number,p.size,p.totalElements,p.totalPages)
 private fun empty(body:Map<String,Any?>?){if(!body.isNullOrEmpty())apadBad("Esta operação não aceita campos no corpo")}
 @PostMapping("/api/sponsorships") @ResponseStatus(HttpStatus.CREATED)
 fun create(a:UsuarioAuthenticationToken,@Valid @RequestBody r:RegistrarApadrinhamentoRequest)=service.create(a.usuario,r)
 @GetMapping("/api/me/sponsorships")
 fun mine(a:UsuarioAuthenticationToken,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="15") size:Int,@RequestParam(required=false) status:StatusApadrinhamento?)=page(service.list(a.usuario,false,page,size,status,null,null))
 @GetMapping("/api/me/sponsorships/{id}")
 fun own(a:UsuarioAuthenticationToken,@PathVariable id:Long)=service.details(a.usuario,id,false)
 @GetMapping("/api/me/sponsorships/{id}/parcelas")
 fun ownParts(a:UsuarioAuthenticationToken,@PathVariable id:Long,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="15") size:Int,@RequestParam(required=false) status:StatusParcela?)=page(service.installments(a.usuario,id,false,page,size,status,null,null,null))
 @PatchMapping("/api/me/sponsorships/{id}/cancelar")
 fun cancel(a:UsuarioAuthenticationToken,@PathVariable id:Long,@RequestBody(required=false) body:Map<String,Any?>?):ApadrinhamentoResponse {empty(body);return service.close(a.usuario,id,false,null)}
 @GetMapping("/api/ong/sponsorships")
 fun list(a:UsuarioAuthenticationToken,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="15") size:Int,@RequestParam(required=false) status:StatusApadrinhamento?,@RequestParam(required=false) animalId:Long?,@RequestParam(required=false) modalidade:ModalidadeApadrinhamento?)=page(service.list(a.usuario,true,page,size,status,animalId,modalidade))
 @GetMapping("/api/ong/sponsorships/{id}")
 fun details(a:UsuarioAuthenticationToken,@PathVariable id:Long)=service.details(a.usuario,id,true)
 @GetMapping("/api/ong/sponsorships/{id}/parcelas")
 fun parts(a:UsuarioAuthenticationToken,@PathVariable id:Long,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="15") size:Int,@RequestParam(required=false) status:StatusParcela?,@RequestParam(required=false) vencidas:Boolean?,@RequestParam(required=false) de:String?,@RequestParam(required=false) ate:String?)=page(service.installments(a.usuario,id,true,page,size,status,vencidas,de,ate))
 @PatchMapping("/api/ong/sponsorships/{id}/parcelas/{parcelaId}/confirmar")
 fun confirm(a:UsuarioAuthenticationToken,@PathVariable id:Long,@PathVariable parcelaId:Long,@RequestBody(required=false) body:Map<String,Any?>?):ParcelaResponse {empty(body);return service.confirm(a.usuario,id,parcelaId)}
 @PatchMapping("/api/ong/sponsorships/{id}/encerrar")
 fun close(a:UsuarioAuthenticationToken,@PathVariable id:Long,@Valid @RequestBody r:EncerrarApadrinhamentoRequest)=service.close(a.usuario,id,true,r)
}
/** Also handles domain conflicts propagated by the existing adoption/death transactions. */
@RestControllerAdvice @Order(Ordered.HIGHEST_PRECEDENCE)
class ApadrinhamentoDomainErrors {
 @ExceptionHandler(ApadrinhamentoException::class)
 fun domain(e:ApadrinhamentoException)=ProblemDetail.forStatusAndDetail(e.status,e.message!!).apply{setProperty("code",e.code)}
}
@RestControllerAdvice(assignableTypes=[ApadrinhamentoController::class]) @Order(Ordered.HIGHEST_PRECEDENCE)
class ApadrinhamentoErrors {
 @ExceptionHandler(MethodArgumentNotValidException::class,HttpMessageNotReadableException::class,MethodArgumentTypeMismatchException::class)
 fun bad(e:Exception)=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Confira os campos e filtros do apadrinhamento").apply{setProperty("code","APADRINHAMENTO_DADOS_INVALIDOS")}
 @ExceptionHandler(DataIntegrityViolationException::class,PessimisticLockingFailureException::class,ObjectOptimisticLockingFailureException::class)
 fun conflict(e:Exception)=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"A operação conflita com o estado atual do apadrinhamento").apply{setProperty("code","APADRINHAMENTO_CONFLITO")}
 @ExceptionHandler(FinanceiroException::class)
 fun finance(e:FinanceiroException)=ProblemDetail.forStatusAndDetail(e.status,"Não foi possível registrar o recebimento financeiro").apply{setProperty("code",e.code)}
}
