package br.com.sysanimais.api.transparencia.controller

import br.com.sysanimais.api.financeiro.service.*
import br.com.sysanimais.api.security.UsuarioAuthenticationToken
import br.com.sysanimais.api.transparencia.domain.StatusDemonstrativo
import br.com.sysanimais.api.transparencia.service.*
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.*
import org.springframework.http.*
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestController
class TransparenciaController(private val service: TransparenciaService, private val finance: RelatorioFinanceiroService) {
    private fun query(params: Map<String, String>, allowed: Set<String>) {
        if (params.keys.any { it !in allowed }) transparenciaBad("Filtro não permitido")
    }
    @GetMapping("/api/ong/relatorios/financeiro")
    fun report(a: UsuarioAuthenticationToken, @RequestParam(required = false) de: String?, @RequestParam(required = false) ate: String?, @RequestParam params: Map<String, String>): br.com.sysanimais.api.financeiro.dto.RelatorioFinanceiroResponse {
        query(params, setOf("de", "ate")); return finance.report(a.usuario, de, ate)
    }
    @PostMapping("/api/ong/transparencia") @ResponseStatus(HttpStatus.CREATED)
    fun create(a: UsuarioAuthenticationToken, @RequestBody input: JsonNode) = service.create(a.usuario, input)
    @GetMapping("/api/ong/transparencia")
    fun list(a: UsuarioAuthenticationToken, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "15") size: Int,
             @RequestParam(required = false) status: StatusDemonstrativo?, @RequestParam(required = false) de: String?, @RequestParam(required = false) ate: String?, @RequestParam params: Map<String, String>): br.com.sysanimais.api.animais.dto.PaginaResponse<br.com.sysanimais.api.transparencia.dto.DemonstrativoInternoResponse> {
        query(params, setOf("page", "size", "status", "de", "ate")); return service.list(a.usuario, page, size, status, de, ate)
    }
    @GetMapping("/api/ong/transparencia/{id}")
    fun details(a: UsuarioAuthenticationToken, @PathVariable id: Long) = service.details(a.usuario, id)
    @PatchMapping("/api/ong/transparencia/{id}")
    fun edit(a: UsuarioAuthenticationToken, @PathVariable id: Long, @RequestBody input: JsonNode) = service.edit(a.usuario, id, input)
    @PatchMapping("/api/ong/transparencia/{id}/publicar")
    fun publish(a: UsuarioAuthenticationToken, @PathVariable id: Long, @RequestBody(required = false) input: JsonNode?) = service.publish(a.usuario, id, true, input)
    @PatchMapping("/api/ong/transparencia/{id}/despublicar")
    fun unpublish(a: UsuarioAuthenticationToken, @PathVariable id: Long, @RequestBody(required = false) input: JsonNode?) = service.publish(a.usuario, id, false, input)
    @GetMapping("/api/public/ongs/{ongId}/transparencia")
    fun publicList(@PathVariable ongId: Int, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "15") size: Int,
                   @RequestParam params: Map<String, String>): br.com.sysanimais.api.animais.dto.PaginaResponse<br.com.sysanimais.api.transparencia.dto.DemonstrativoPublicoResponse> {
        query(params, setOf("page", "size")); return service.publicList(ongId, page, size)
    }
    @GetMapping("/api/public/ongs/{ongId}/transparencia/{id}")
    fun publicDetails(@PathVariable ongId: Int, @PathVariable id: Long) = service.publicDetails(ongId, id)
}

@RestControllerAdvice(assignableTypes = [TransparenciaController::class]) @Order(Ordered.HIGHEST_PRECEDENCE)
class TransparenciaErrors {
    @ExceptionHandler(FinanceiroException::class)
    fun domain(e: FinanceiroException) = ProblemDetail.forStatusAndDetail(e.status, e.message!!).apply { setProperty("code", e.code) }
    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun invalid(e: Exception) = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Confira os campos, datas e filtros de transparência").apply { setProperty("code", "TRANSPARENCIA_DADOS_INVALIDOS") }
    @ExceptionHandler(DataIntegrityViolationException::class, ObjectOptimisticLockingFailureException::class, PessimisticLockingFailureException::class)
    fun conflict(e: Exception) = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A operação conflita com o estado atual do demonstrativo").apply { setProperty("code", "TRANSPARENCIA_CONFLITO") }
}
