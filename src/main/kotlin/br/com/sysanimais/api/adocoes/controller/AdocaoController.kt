package br.com.sysanimais.api.adocoes.controller

import br.com.sysanimais.api.adocoes.domain.StatusSolicitacao
import br.com.sysanimais.api.adocoes.dto.*
import br.com.sysanimais.api.adocoes.service.AdocaoService
import br.com.sysanimais.api.animais.dto.PaginaResponse
import br.com.sysanimais.api.security.UsuarioAuthenticationToken
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
class AdocaoController(private val service:AdocaoService) {
 private fun <T> page(p:Page<T>)=PaginaResponse(p.content,p.number,p.size,p.totalElements,p.totalPages)
 @PostMapping("/api/adoption-requests") @ResponseStatus(HttpStatus.CREATED)
 fun create(auth:UsuarioAuthenticationToken,@Valid @RequestBody r:SolicitarAdocaoRequest)=service.create(auth.usuario,r)
 @GetMapping("/api/me/adoption-requests")
 fun mine(auth:UsuarioAuthenticationToken,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="20") size:Int)=page(service.mine(auth.usuario,page,size))
 @GetMapping("/api/me/adoption-requests/{id}")
 fun ownDetails(auth:UsuarioAuthenticationToken,@PathVariable id:Long)=service.ownDetails(auth.usuario,id)
 @PatchMapping("/api/me/adoption-requests/{id}/cancelar")
 fun cancel(auth:UsuarioAuthenticationToken,@PathVariable id:Long)=service.cancel(auth.usuario,id)
 @GetMapping("/api/ong/adoption-requests")
 fun list(auth:UsuarioAuthenticationToken,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="20") size:Int,@RequestParam(required=false) status:StatusSolicitacao?,@RequestParam(required=false) animalId:Long?)=page(service.internalList(auth.usuario,page,size,status,animalId))
 @GetMapping("/api/ong/adoption-requests/{id}")
 fun details(auth:UsuarioAuthenticationToken,@PathVariable id:Long)=service.internalDetails(auth.usuario,id)
 @PatchMapping("/api/ong/adoption-requests/{id}/aprovar")
 fun approve(auth:UsuarioAuthenticationToken,@PathVariable id:Long)=service.approve(auth.usuario,id)
 @PatchMapping("/api/ong/adoption-requests/{id}/rejeitar")
 fun reject(auth:UsuarioAuthenticationToken,@PathVariable id:Long,@Valid @RequestBody r:JustificativaRequest)=service.reject(auth.usuario,id,r)
 @PatchMapping("/api/ong/adoption-requests/{id}/cancelar-aprovacao")
 fun cancelApproval(auth:UsuarioAuthenticationToken,@PathVariable id:Long,@Valid @RequestBody r:JustificativaRequest)=service.cancelApproval(auth.usuario,id,r)
 @PostMapping("/api/ong/adoption-requests/{id}/confirmar-entrega")
 fun deliver(auth:UsuarioAuthenticationToken,@PathVariable id:Long,@Valid @RequestBody r:ConfirmarEntregaRequest)=service.deliver(auth.usuario,id)
}
