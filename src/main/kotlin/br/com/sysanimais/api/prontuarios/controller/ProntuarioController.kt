package br.com.sysanimais.api.prontuarios.controller

import br.com.sysanimais.api.prontuarios.domain.TipoProntuario
import br.com.sysanimais.api.prontuarios.dto.ProntuarioRequest
import br.com.sysanimais.api.prontuarios.service.*
import br.com.sysanimais.api.animais.dto.PaginaResponse
import br.com.sysanimais.api.security.UsuarioAuthenticationToken
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.data.domain.Page
import org.springframework.http.*
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestController
@RequestMapping("/api/ong/animais/{animalId}/prontuarios")
class ProntuarioController(private val service:ProntuarioService) {
 private fun <T> page(p:Page<T>)=PaginaResponse(p.content,p.number,p.size,p.totalElements,p.totalPages)
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 fun create(a:UsuarioAuthenticationToken,@PathVariable animalId:Long,@RequestBody r:ProntuarioRequest)=service.create(a.usuario,animalId,r)
 @GetMapping
 fun list(a:UsuarioAuthenticationToken,@PathVariable animalId:Long,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="15") size:Int,@RequestParam(required=false) tipo:TipoProntuario?,@RequestParam(required=false) de:String?,@RequestParam(required=false) ate:String?)=page(service.list(a.usuario,animalId,page,size,tipo,de,ate))
 @GetMapping("/{id}")
 fun details(a:UsuarioAuthenticationToken,@PathVariable animalId:Long,@PathVariable id:Long)=service.details(a.usuario,animalId,id)
 @PatchMapping("/{id}/corrigir")
 fun correct(a:UsuarioAuthenticationToken,@PathVariable animalId:Long,@PathVariable id:Long,@RequestBody r:ProntuarioRequest)=service.correct(a.usuario,animalId,id,r)
 @GetMapping("/{id}/correcoes")
 fun history(a:UsuarioAuthenticationToken,@PathVariable animalId:Long,@PathVariable id:Long,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="15") size:Int)=page(service.history(a.usuario,animalId,id,page,size))
}

@RestController
@RequestMapping("/api/me/animais-adotados/{animalId}/prontuarios")
class ProntuarioAdotanteController(private val service:ProntuarioService) {
 private fun <T> page(p:Page<T>)=PaginaResponse(p.content,p.number,p.size,p.totalElements,p.totalPages)
 @GetMapping
 fun list(a:UsuarioAuthenticationToken,@PathVariable animalId:Long,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="15") size:Int,@RequestParam(required=false) tipo:TipoProntuario?,@RequestParam(required=false) de:String?,@RequestParam(required=false) ate:String?)=page(service.adoptedList(a.usuario,animalId,page,size,tipo,de,ate))
 @GetMapping("/{id}")
 fun details(a:UsuarioAuthenticationToken,@PathVariable animalId:Long,@PathVariable id:Long)=service.adoptedDetails(a.usuario,animalId,id)
}

@RestControllerAdvice(assignableTypes=[ProntuarioController::class,ProntuarioAdotanteController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProntuarioErrors {
 @ExceptionHandler(ProntuarioException::class)
 fun clinical(e:ProntuarioException)=ProblemDetail.forStatusAndDetail(e.status,e.message!!).apply{setProperty("code",e.code)}
 @ExceptionHandler(HttpMessageNotReadableException::class,MethodArgumentTypeMismatchException::class)
 fun bad(e:Exception)=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Confira os campos clínicos e filtros informados").apply{setProperty("code","PRONTUARIO_DADOS_INVALIDOS")}
 @ExceptionHandler(DataIntegrityViolationException::class,ObjectOptimisticLockingFailureException::class,PessimisticLockingFailureException::class)
 fun conflict(e:Exception)=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"O registro mudou ou a operação conflita com os dados atuais").apply{setProperty("code","PRONTUARIO_CONFLITO")}
}
