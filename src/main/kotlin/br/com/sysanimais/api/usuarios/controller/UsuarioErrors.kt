package br.com.sysanimais.api.usuarios.controller

import br.com.sysanimais.api.usuarios.service.EmailEmUsoException
import br.com.sysanimais.api.usuarios.service.EmailInvalidoException
import br.com.sysanimais.api.usuarios.service.PerfilInvalidoException

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.dao.DataAccessException
import br.com.sysanimais.api.auth.controller.LoginRateLimitException
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpHeaders

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(br.com.sysanimais.api.equipes.service.EquipeException::class)
    fun equipe(ex: br.com.sysanimais.api.equipes.service.EquipeException): ProblemDetail = ProblemDetail.forStatusAndDetail(ex.status, ex.message!!).apply { setProperty("code",ex.code) }
    @ExceptionHandler(br.com.sysanimais.api.animais.service.VersaoAnimalConflitante::class, org.springframework.orm.ObjectOptimisticLockingFailureException::class, jakarta.persistence.OptimisticLockException::class)
    fun versaoAnimal(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Este animal foi atualizado por outra pessoa. Recarregue os dados antes de salvar novamente.").apply { setProperty("code", "ANIMAL_VERSION_CONFLICT") }

    @ExceptionHandler(LoginRateLimitException::class)
    fun limite(ex: LoginRateLimitException): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, ex.retryAfterSeconds.toString())
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente novamente mais tarde."))

    @ExceptionHandler(DataAccessException::class)
    fun integridade(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir a operação")

    @ExceptionHandler(EmailEmUsoException::class)
    fun conflito(ex: EmailEmUsoException): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message!!)
        .apply { setProperty("code", "EMAIL_ALREADY_REGISTERED") }

    @ExceptionHandler(PerfilInvalidoException::class, EmailInvalidoException::class, MethodArgumentNotValidException::class, HttpMessageNotReadableException::class)
    fun invalido(ex: Exception): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados de cadastro inválidos")
        .apply { setProperty("code", "DADOS_INVALIDOS") }
}
