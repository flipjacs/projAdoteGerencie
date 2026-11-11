package br.com.sysanimais.api.animais.controller

import br.com.sysanimais.api.animais.dto.*
import br.com.sysanimais.api.animais.service.AnimalService
import br.com.sysanimais.api.security.UsuarioAuthenticationToken
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
class AnimalController(private val service: AnimalService) {
 private fun <T> asResponse(value: Page<T>) = PaginaResponse(value.content, value.number, value.size, value.totalElements, value.totalPages)
 @GetMapping("/api/animais") fun catalog(@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int)=asResponse(service.catalog(page,size))
 @GetMapping("/api/animais/{id}/fotos/{fotoId}") fun publicPhoto(@PathVariable id: Long,@PathVariable fotoId: Long): ResponseEntity<ByteArray> { val (type,bytes)=service.publicPhoto(id,fotoId); return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(bytes) }
 @PostMapping("/api/animais") @ResponseStatus(HttpStatus.CREATED)
 fun create(auth: UsuarioAuthenticationToken,@Valid @RequestBody body: AnimalRequest)=service.create(auth.usuario,body)
 @GetMapping("/api/ong/animais")
 fun list(auth: UsuarioAuthenticationToken,@RequestParam(required=false) ativo: Boolean?,@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int)=asResponse(service.list(auth.usuario,ativo,page,size))
 @GetMapping("/api/ong/animais/{id}") fun get(auth: UsuarioAuthenticationToken,@PathVariable id: Long)=service.get(auth.usuario,id)
 @PutMapping("/api/ong/animais/{id}") fun edit(auth: UsuarioAuthenticationToken,@PathVariable id: Long,@Valid @RequestBody body: AnimalRequest)=service.edit(auth.usuario,id,body)
 @PatchMapping("/api/ong/animais/{id}/inativacao") fun deactivate(auth: UsuarioAuthenticationToken,@PathVariable id: Long)=service.deactivate(auth.usuario,id)
 @PatchMapping("/api/ong/animais/{id}/situacao") fun status(auth: UsuarioAuthenticationToken,@PathVariable id: Long,@Valid @RequestBody body: SituacaoRequest)=service.status(auth.usuario,id,body.situacao,body.versao!!)
 @PostMapping("/api/ong/animais/{id}/fotos",consumes=[MediaType.MULTIPART_FORM_DATA_VALUE]) @ResponseStatus(HttpStatus.CREATED)
 fun upload(auth: UsuarioAuthenticationToken,@PathVariable id: Long,@RequestPart("arquivo") file: MultipartFile)=service.upload(auth.usuario,id,file)
 @GetMapping("/api/ong/animais/{id}/fotos/{fotoId}") fun photo(auth: UsuarioAuthenticationToken,@PathVariable id: Long,@PathVariable fotoId: Long): ResponseEntity<ByteArray> { val (type,bytes)=service.photo(auth.usuario,id,fotoId); return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(bytes) }
}
