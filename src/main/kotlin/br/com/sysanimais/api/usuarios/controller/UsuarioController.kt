package br.com.sysanimais.api.usuarios.controller

import br.com.sysanimais.api.equipes.dto.CriarFuncionarioRequest
import br.com.sysanimais.api.equipes.service.EquipeService
import br.com.sysanimais.api.security.UsuarioAuthenticationToken
import br.com.sysanimais.api.usuarios.dto.CadastroPublicoRequest
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.service.UsuarioService

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/usuarios")
class UsuarioController(private val service: UsuarioService, private val equipes: EquipeService) {
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastroPublico(@Valid @RequestBody request: CadastroPublicoRequest) = service.cadastrarPublico(request)

    @PostMapping("/internos")
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastroInterno(@Valid @RequestBody request: CriarFuncionarioRequest, auth: UsuarioAuthenticationToken) =
        equipes.criar(auth.usuario,request)
}
