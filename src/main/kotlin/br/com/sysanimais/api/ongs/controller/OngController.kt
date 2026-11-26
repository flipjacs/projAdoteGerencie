package br.com.sysanimais.api.ongs.controller

import br.com.sysanimais.api.ongs.domain.EstadoOng
import br.com.sysanimais.api.ongs.dto.*
import br.com.sysanimais.api.ongs.service.OngService
import br.com.sysanimais.api.security.UsuarioAuthenticationToken
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/plataforma/ongs")
class OngController(private val service: OngService) {
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 fun criar(auth: UsuarioAuthenticationToken,@Valid @RequestBody r: CriarOngRequest)=service.criar(auth.usuario,r)
 @GetMapping fun listar(auth: UsuarioAuthenticationToken,@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int)=service.listar(auth.usuario,page,size)
 @GetMapping("/{id}") fun obter(auth: UsuarioAuthenticationToken,@PathVariable id: Int)=service.obter(auth.usuario,id)
 @PutMapping("/{id}") fun atualizar(auth: UsuarioAuthenticationToken,@PathVariable id: Int,@Valid @RequestBody r: AtualizarOngRequest)=service.atualizar(auth.usuario,id,r)
 @PatchMapping("/{id}/suspensao") fun suspender(auth: UsuarioAuthenticationToken,@PathVariable id: Int,@Valid @RequestBody r: SuspenderOngRequest)=service.situacao(auth.usuario,id,EstadoOng.SUSPENSA,r.motivo)
 @PatchMapping("/{id}/reativacao") fun reativar(auth: UsuarioAuthenticationToken,@PathVariable id: Int)=service.situacao(auth.usuario,id,EstadoOng.ATIVA,null)
}
