package br.com.sysanimais.api.equipes.controller

import br.com.sysanimais.api.equipes.dto.*
import br.com.sysanimais.api.equipes.service.EquipeService
import br.com.sysanimais.api.security.UsuarioAuthenticationToken
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ong/funcionarios")
class EquipeController(private val service: EquipeService) {
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 fun criar(auth: UsuarioAuthenticationToken,@Valid @RequestBody r: CriarFuncionarioRequest)=service.criar(auth.usuario,r)
 @GetMapping fun listar(auth: UsuarioAuthenticationToken,@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int)=service.listar(auth.usuario,page,size)
 @GetMapping("/{id}") fun obter(auth: UsuarioAuthenticationToken,@PathVariable id: Int)=service.obter(auth.usuario,id)
 @PutMapping("/{id}") fun atualizar(auth: UsuarioAuthenticationToken,@PathVariable id: Int,@Valid @RequestBody r: AtualizarFuncionarioRequest)=service.atualizar(auth.usuario,id,r)
 @PatchMapping("/{id}/perfil") fun perfil(auth: UsuarioAuthenticationToken,@PathVariable id: Int,@Valid @RequestBody r: AlterarPerfilRequest)=service.alterarPerfil(auth.usuario,id,r)
 @PatchMapping("/{id}/desativacao") fun desativar(auth: UsuarioAuthenticationToken,@PathVariable id: Int,@Valid @RequestBody r: AlterarAtivoRequest)=service.alterarAtivo(auth.usuario,id,false,r)
 @PatchMapping("/{id}/reativacao") fun reativar(auth: UsuarioAuthenticationToken,@PathVariable id: Int,@Valid @RequestBody r: AlterarAtivoRequest)=service.alterarAtivo(auth.usuario,id,true,r)
}
