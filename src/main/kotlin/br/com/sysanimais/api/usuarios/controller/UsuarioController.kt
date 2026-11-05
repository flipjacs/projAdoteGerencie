package br.com.sysanimais.api.usuarios.controller

import br.com.sysanimais.api.usuarios.dto.CadastroInternoRequest
import br.com.sysanimais.api.usuarios.dto.CadastroPublicoRequest
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.service.UsuarioService

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/usuarios")
class UsuarioController(private val service: UsuarioService) {
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastroPublico(@Valid @RequestBody request: CadastroPublicoRequest) = service.cadastrarPublico(request)

    @PostMapping("/internos")
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastroInterno(@Valid @RequestBody request: CadastroInternoRequest, @org.springframework.security.core.annotation.AuthenticationPrincipal jwt: Jwt): UsuarioResponse =
        service.cadastrarInterno(request, jwt.subject.toInt())
}
