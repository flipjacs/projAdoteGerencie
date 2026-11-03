package br.com.sysanimais.api

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(EmailEmUsoException::class)
    fun conflito(ex: EmailEmUsoException): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message!!)

    @ExceptionHandler(PerfilInvalidoException::class, EmailInvalidoException::class, MethodArgumentNotValidException::class, HttpMessageNotReadableException::class)
    fun invalido(ex: Exception): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados de cadastro inválidos")
}
