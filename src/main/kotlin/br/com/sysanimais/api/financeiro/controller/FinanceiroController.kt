package br.com.sysanimais.api.financeiro.controller

import br.com.sysanimais.api.financeiro.domain.*
import br.com.sysanimais.api.financeiro.dto.*
import br.com.sysanimais.api.financeiro.service.*
import br.com.sysanimais.api.animais.dto.PaginaResponse
import br.com.sysanimais.api.security.UsuarioAuthenticationToken
import jakarta.validation.Valid
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.data.domain.Page
import org.springframework.http.*
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestController
class FinanceiroController(private val service:FinanceiroService) {
 private fun <T> page(p:Page<T>)=PaginaResponse(p.content,p.number,p.size,p.totalElements,p.totalPages)
 private fun empty(body:Map<String,Any?>?){if(!body.isNullOrEmpty())throw FinanceiroException(HttpStatus.BAD_REQUEST,"FINANCEIRO_DADOS_INVALIDOS","Esta operação não aceita campos no corpo")}
 @PostMapping("/api/donations") @ResponseStatus(HttpStatus.CREATED)
 fun donate(a:UsuarioAuthenticationToken,@Valid @RequestBody r:RegistrarDoacaoRequest)=service.donate(a.usuario,r)
 @GetMapping("/api/me/donations")
 fun mine(a:UsuarioAuthenticationToken,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="20") size:Int)=page(service.mine(a.usuario,page,size))
 @GetMapping("/api/me/donations/{id}")
 fun own(a:UsuarioAuthenticationToken,@PathVariable id:Long)=service.ownDonation(a.usuario,id)
 @PatchMapping("/api/me/donations/{id}/cancelar")
 fun cancelOwn(a:UsuarioAuthenticationToken,@PathVariable id:Long,@Valid @RequestBody(required=false) r:CancelamentoFinanceiroRequest?)=service.cancelDonation(a.usuario,id,false,r)
 @GetMapping("/api/ong/donations")
 fun donations(a:UsuarioAuthenticationToken,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="20") size:Int,@RequestParam(required=false) status:StatusDoacao?,@RequestParam(required=false) de:String?,@RequestParam(required=false) ate:String?)=page(service.donationList(a.usuario,page,size,status,de,ate))
 @GetMapping("/api/ong/donations/{id}")
 fun donation(a:UsuarioAuthenticationToken,@PathVariable id:Long)=service.donationDetails(a.usuario,id)
 @PatchMapping("/api/ong/donations/{id}/confirmar")
 fun confirm(a:UsuarioAuthenticationToken,@PathVariable id:Long,@RequestBody(required=false) body:Map<String,Any?>?):DoacaoResponse {empty(body);return service.confirmDonation(a.usuario,id)}
 @PatchMapping("/api/ong/donations/{id}/cancelar")
 fun cancel(a:UsuarioAuthenticationToken,@PathVariable id:Long,@Valid @RequestBody(required=false) r:CancelamentoFinanceiroRequest?)=service.cancelDonation(a.usuario,id,true,r)
 @PostMapping("/api/ong/finance/movimentacoes") @ResponseStatus(HttpStatus.CREATED)
 fun create(a:UsuarioAuthenticationToken,@Valid @RequestBody r:RegistrarMovimentoRequest)=service.create(a.usuario,r)
 @GetMapping("/api/ong/finance/movimentacoes")
 fun list(a:UsuarioAuthenticationToken,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="20") size:Int,@RequestParam(required=false) tipo:TipoMovimento?,@RequestParam(required=false) status:StatusMovimento?,@RequestParam(required=false) categoria:CategoriaFinanceira?,@RequestParam(required=false) de:String?,@RequestParam(required=false) ate:String?)=page(service.list(a.usuario,page,size,tipo,status,categoria,de,ate))
 @GetMapping("/api/ong/finance/movimentacoes/{id}")
 fun details(a:UsuarioAuthenticationToken,@PathVariable id:Long)=service.details(a.usuario,id)
 @PatchMapping("/api/ong/finance/movimentacoes/{id}/aprovar")
 fun approve(a:UsuarioAuthenticationToken,@PathVariable id:Long,@RequestBody(required=false) body:Map<String,Any?>?):MovimentoFinanceiroResponse {empty(body);return service.change(a.usuario,id,"aprovar")}
 @PatchMapping("/api/ong/finance/movimentacoes/{id}/confirmar-recebimento")
 fun receipt(a:UsuarioAuthenticationToken,@PathVariable id:Long,@RequestBody(required=false) body:Map<String,Any?>?):MovimentoFinanceiroResponse {empty(body);return service.change(a.usuario,id,"confirmar-recebimento")}
 @PatchMapping("/api/ong/finance/movimentacoes/{id}/confirmar-pagamento")
 fun payment(a:UsuarioAuthenticationToken,@PathVariable id:Long,@RequestBody(required=false) body:Map<String,Any?>?):MovimentoFinanceiroResponse {empty(body);return service.change(a.usuario,id,"confirmar-pagamento")}
 @PatchMapping("/api/ong/finance/movimentacoes/{id}/cancelar")
 fun cancelMovement(a:UsuarioAuthenticationToken,@PathVariable id:Long,@Valid @RequestBody(required=false) r:CancelamentoFinanceiroRequest?)=service.change(a.usuario,id,"cancelar",r)
 @GetMapping("/api/ong/finance/resumo")
 fun summary(a:UsuarioAuthenticationToken,@RequestParam(required=false) de:String?,@RequestParam(required=false) ate:String?)=service.summary(a.usuario,de,ate)
}

@RestControllerAdvice(assignableTypes=[FinanceiroController::class]) @Order(Ordered.HIGHEST_PRECEDENCE)
class FinanceiroErrors {
 @ExceptionHandler(FinanceiroException::class)
 fun finance(e:FinanceiroException)=ProblemDetail.forStatusAndDetail(e.status,e.message!!).apply{setProperty("code",e.code)}
 @ExceptionHandler(MethodArgumentNotValidException::class,HttpMessageNotReadableException::class,MethodArgumentTypeMismatchException::class)
 fun bad(e:Exception)=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Confira os campos, valores decimais e filtros financeiros").apply{setProperty("code","FINANCEIRO_DADOS_INVALIDOS")}
 @ExceptionHandler(DataIntegrityViolationException::class,ObjectOptimisticLockingFailureException::class,PessimisticLockingFailureException::class)
 fun conflict(e:Exception)=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"O registro financeiro mudou ou a operação conflita com os dados atuais").apply{setProperty("code","FINANCEIRO_CONFLITO")}
}
