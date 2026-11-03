package br.com.sysanimais.api

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/usuarios")
class UsuarioController(private val service: UsuarioService, private val repository: UsuarioRepository) {
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastroPublico(@Valid @RequestBody request: CadastroPublicoRequest) = service.cadastrarPublico(request)

    @PostMapping("/internos")
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastroInterno(@Valid @RequestBody request: CadastroInternoRequest, @org.springframework.security.core.annotation.AuthenticationPrincipal jwt: Jwt): UsuarioResponse {
        val admin = repository.findById(jwt.subject.toInt()).orElseThrow()
        return service.cadastrarInterno(request, admin.ongId!!)
    }
}
