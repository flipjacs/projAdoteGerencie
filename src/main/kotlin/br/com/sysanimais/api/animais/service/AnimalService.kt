package br.com.sysanimais.api.animais.service

import br.com.sysanimais.api.animais.domain.*
import br.com.sysanimais.api.animais.dto.*
import br.com.sysanimais.api.animais.repository.*
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.ongs.repository.OngRepository
import br.com.sysanimais.api.ongs.domain.EstadoOng
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import org.springframework.beans.factory.annotation.Value
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO
import org.slf4j.LoggerFactory

class VersaoAnimalConflitante : RuntimeException()

@Service
class AnimalService(private val animals: AnimalRepository, private val photos: AnimalFotoRepository, private val ongs: OngRepository, private val usuarios: UsuarioRepository, private val adocoes: br.com.sysanimais.api.adocoes.repository.SolicitacaoAdocaoRepository,
 private val encerramentoApadrinhamento:br.com.sysanimais.api.apadrinhamentos.service.EncerramentoApadrinhamentoService,
 @param:Value("\${app.animal-photos-dir:./animal-photos}") private val photoDir: String) {
 private val log = LoggerFactory.getLogger(AnimalService::class.java)
 private fun escritor(u: UsuarioResponse) {
  if(u.perfil !in setOf(Perfil.ADMINISTRADOR, Perfil.VOLUNTARIO)) throw ResponseStatusException(HttpStatus.FORBIDDEN)
 }
 private fun ong(u: UsuarioResponse): Int {
  if(u.perfil !in setOf(Perfil.ADMINISTRADOR, Perfil.VOLUNTARIO, Perfil.VETERINARIO)) throw ResponseStatusException(HttpStatus.FORBIDDEN)
  val id=u.ongId ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
  if(ongs.lockRead(id)?.situacao!=EstadoOng.ATIVA) throw ResponseStatusException(HttpStatus.FORBIDDEN)
  val atual=usuarios.lockByIdRead(u.id) ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
  if(!atual.ativo || atual.credencialPendente || atual.ongId!=id || atual.perfil!=u.perfil || atual.tokenVersao!=u.tokenVersao) throw ResponseStatusException(HttpStatus.FORBIDDEN)
  return id
 }
 private fun find(id: Long, u: UsuarioResponse): Animal = animals.findByIdAndOngId(id, ong(u)) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
 private fun locked(id: Long, u: UsuarioResponse): Animal = animals.lockByIdAndOngId(id, ong(u)) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
 private fun validate(r: AnimalRequest) {
  if (r.dataResgate != null && r.dataResgate.isAfter(r.dataEntrada) || r.nascimentoEstimado != null && r.nascimentoEstimado.isAfter(r.dataEntrada))
   throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Datas inconsistentes")
 }
 private fun assign(a: Animal, r: AnimalRequest) {
  validate(r)
  a.nome=r.nome.trim(); a.especie=r.especie; a.sexo=r.sexo; a.porte=r.porte
  a.nascimentoEstimado=r.nascimentoEstimado; a.descricao=r.descricao; a.historia=r.historia
  a.dataResgate=r.dataResgate; a.origemResgate=r.origemResgate; a.observacoesResgate=r.observacoesResgate; a.dataEntrada=r.dataEntrada
  a.atualizadoEm=Instant.now()
 }
 private fun response(a: Animal, fs: List<AnimalFoto>) = AnimalResponse(a.id!!,a.ongId,a.nome,a.especie,a.sexo,a.porte,a.nascimentoEstimado,a.descricao,a.historia,a.dataResgate,a.origemResgate,a.observacoesResgate,a.dataEntrada,a.situacao,a.situacaoAtualizadaEm,a.situacaoAlteradaPor,a.ativo,a.publicado,a.criadoEm,a.atualizadoEm,fs.map { FotoResponse(it.id!!,it.principal,"/api/ong/animais/${a.id}/fotos/${it.id}") },a.versao)
 @Transactional fun create(u: UsuarioResponse, r: AnimalRequest): AnimalResponse {
  escritor(u)
  if(r.versao != null) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
  val now=Instant.now(); val a=Animal(ongId=ong(u),situacaoAlteradaPor=u.id,criadoEm=now,atualizadoEm=now,situacaoAtualizadaEm=now)
  assign(a,r); return response(animals.saveAndFlush(a), emptyList())
 }
 @Transactional(readOnly=true) fun catalog(page: Int, size: Int, especie: EspecieAnimal? = null, sexo: SexoAnimal? = null, porte: PorteAnimal? = null): Page<AnimalPublicoResponse> {
  if(page<0 || size !in 1..100) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
  val result=animals.catalog(especie,sexo,porte,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")))
  val ids=result.content.mapNotNull { it.id }; val grouped=if(ids.isEmpty()) emptyMap() else photos.findAllByAnimalIdInOrderByIdAsc(ids).groupBy { it.animalId }
  return result.map { a -> AnimalPublicoResponse(a.id!!,a.nome,a.especie,a.sexo,a.porte,a.descricao,grouped[a.id].orEmpty().map { FotoResponse(it.id!!,it.principal,"/api/animais/${a.id}/fotos/${it.id}") }) }
 }
 @Transactional fun publication(u: UsuarioResponse,id: Long,published: Boolean): AnimalResponse {
  escritor(u); val a=locked(id,u)
  if(!a.ativo) throw ResponseStatusException(HttpStatus.CONFLICT,"Animal inativo")
  if(a.publicado!=published) { a.publicado=published; a.atualizadoEm=Instant.now(); animals.flush() }
  return response(a,photos.findAllByAnimalIdOrderByIdAsc(id))
 }
 @Transactional(readOnly=true) fun publicDetails(id: Long): AnimalPublicoDetalhesResponse {
  val a=animals.findByIdAndPublicadoTrueAndAtivoTrueAndSituacao(id,SituacaoAnimal.DISPONIVEL) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
  val o=ongs.findById(a.ongId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
  return AnimalPublicoDetalhesResponse(a.id!!,a.nome,a.especie,a.sexo,a.porte,a.descricao,a.nascimentoEstimado,a.situacao,OngPublicaResponse(o.id!!,o.nome,o.cidade,o.estado),photos.findAllByAnimalIdOrderByIdAsc(id).map { FotoResponse(it.id!!,it.principal,"/api/animais/$id/fotos/${it.id}") })
 }
 @Transactional(readOnly=true) fun publicPhoto(id: Long,fotoId: Long): Pair<String,ByteArray> {
  animals.findByIdAndPublicadoTrueAndAtivoTrueAndSituacao(id,SituacaoAnimal.DISPONIVEL) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
  return readPhoto(id,fotoId)
 }
 @Transactional(readOnly=true) fun get(u: UsuarioResponse,id: Long): AnimalResponse { val a=find(id,u); return response(a,photos.findAllByAnimalIdOrderByIdAsc(id)) }
 @Transactional(readOnly=true) fun list(u: UsuarioResponse, ativo: Boolean?, page: Int, size: Int): Page<AnimalResponse> {
  if(page<0 || size !in 1..100) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
  val pageable=PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")); val ongId=ong(u)
  val result=if(ativo==null) animals.findAllByOngId(ongId,pageable) else animals.findAllByOngIdAndAtivo(ongId,ativo,pageable)
  val ids=result.content.mapNotNull { it.id }; val grouped=if(ids.isEmpty()) emptyMap() else photos.findAllByAnimalIdInOrderByIdAsc(ids).groupBy { it.animalId }
  return result.map { response(it,grouped[it.id].orEmpty()) }
 }
 @Transactional fun edit(u: UsuarioResponse,id: Long,r: AnimalRequest): AnimalResponse { escritor(u); val version=r.versao ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST); val a=locked(id,u); if(a.versao != version) throw VersaoAnimalConflitante(); if(!a.ativo) throw ResponseStatusException(HttpStatus.CONFLICT,"Animal inativo"); assign(a,r); animals.flush(); return response(a,photos.findAllByAnimalIdOrderByIdAsc(id)) }
 @Transactional fun deactivate(u: UsuarioResponse,id: Long): AnimalResponse { escritor(u); val a=locked(id,u); if(a.ativo) { if(adocoes.existsByAnimalIdAndStatus(id,br.com.sysanimais.api.adocoes.domain.StatusSolicitacao.APROVADA)) throw ResponseStatusException(HttpStatus.CONFLICT,"Animal reservado para adoção"); a.ativo=false; a.publicado=false; a.atualizadoEm=Instant.now(); animals.flush() }; return response(a,photos.findAllByAnimalIdOrderByIdAsc(id)) }
 @Transactional fun status(u: UsuarioResponse,id: Long,s: SituacaoAnimal,version: Long): AnimalResponse {
  escritor(u)
  val a=locked(id,u); if(a.versao != version) throw VersaoAnimalConflitante(); if(!a.ativo) throw ResponseStatusException(HttpStatus.CONFLICT,"Animal inativo")
  if(s!=a.situacao && adocoes.existsByAnimalIdAndStatus(id,br.com.sysanimais.api.adocoes.domain.StatusSolicitacao.APROVADA)) throw ResponseStatusException(HttpStatus.CONFLICT,"Use o processo de adoção para liberar a reserva")
  val allowed=when(a.situacao) {
   SituacaoAnimal.EM_TRATAMENTO -> setOf(SituacaoAnimal.DISPONIVEL,SituacaoAnimal.FALECIDO)
   SituacaoAnimal.DISPONIVEL -> setOf(SituacaoAnimal.EM_TRATAMENTO,SituacaoAnimal.EM_PROCESSO_ADOCAO,SituacaoAnimal.FALECIDO)
   SituacaoAnimal.EM_PROCESSO_ADOCAO -> setOf(SituacaoAnimal.DISPONIVEL,SituacaoAnimal.EM_TRATAMENTO,SituacaoAnimal.FALECIDO)
   SituacaoAnimal.ADOTADO, SituacaoAnimal.FALECIDO -> emptySet()
  }
  if(s != a.situacao && s !in allowed) throw ResponseStatusException(HttpStatus.CONFLICT,"Transição de situação não permitida")
  if(s != a.situacao) { val now=Instant.now(); a.situacao=s; a.situacaoAtualizadaEm=now; a.situacaoAlteradaPor=u.id; a.atualizadoEm=now; animals.flush(); if(s==SituacaoAnimal.FALECIDO)encerramentoApadrinhamento.encerrarAtivos(a,br.com.sysanimais.api.apadrinhamentos.domain.MotivoEncerramento.FALECIMENTO_ANIMAL,now) }
  return response(a,photos.findAllByAnimalIdOrderByIdAsc(id))
 }
 @Transactional fun upload(u: UsuarioResponse,id: Long,file: MultipartFile): FotoResponse {
  escritor(u)
  val a=locked(id,u); if(!a.ativo) throw ResponseStatusException(HttpStatus.CONFLICT,"Animal inativo")
  if(photos.countByAnimalId(id)>=10) throw ResponseStatusException(HttpStatus.CONFLICT,"Limite de fotografias atingido")
  if(file.isEmpty || file.size>5L*1024*1024) throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Imagem inválida ou muito grande")
  val bytes=file.bytes
  val stream=ImageIO.createImageInputStream(bytes.inputStream()) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Imagem inválida")
  val type=stream.use { input ->
   val readers=ImageIO.getImageReaders(input); if(!readers.hasNext()) throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Imagem inválida")
   val reader=readers.next(); try {
    val format=reader.formatName.lowercase(); if(format !in setOf("jpeg","jpg","png")) throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Formato não suportado")
    reader.input=input; val w=reader.getWidth(0); val h=reader.getHeight(0)
    if(w<=0 || h<=0 || w.toLong()*h>16_000_000) throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Dimensões inválidas")
    reader.read(0) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Imagem inválida")
    if(format=="png") "image/png" else "image/jpeg"
   } catch(e: ResponseStatusException) { throw e } catch(e: Exception) { throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Imagem inválida") } finally { reader.dispose() }
  }
  val dir=Path.of(photoDir).toAbsolutePath().normalize(); val name=UUID.randomUUID().toString(); val path=dir.resolve(name)
  try { Files.createDirectories(dir); Files.createFile(path) } catch(e: Exception) { throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Não foi possível armazenar a imagem") }
  try { Files.write(path,bytes,StandardOpenOption.WRITE) } catch(e: Exception) { runCatching { Files.deleteIfExists(path) }; throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Não foi possível armazenar a imagem") }
  TransactionSynchronizationManager.registerSynchronization(object: TransactionSynchronization { override fun afterCompletion(status: Int) { if(status != TransactionSynchronization.STATUS_COMMITTED) Files.deleteIfExists(path) } })
  val p=photos.saveAndFlush(AnimalFoto(animalId=id,arquivo=name,tipo=type,principal=photos.countByAnimalId(id)==0L)); a.atualizadoEm=Instant.now()
  return FotoResponse(p.id!!,p.principal,"/api/ong/animais/$id/fotos/${p.id}")
 }
 @Transactional(readOnly=true) fun photo(u: UsuarioResponse,id: Long,fotoId: Long): Pair<String,ByteArray> {
  find(id,u); return readPhoto(id,fotoId)
 }
 private fun readPhoto(id: Long,fotoId: Long): Pair<String,ByteArray> {
  val p=photos.findByIdAndAnimalId(fotoId,id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
  if(!p.arquivo.matches(Regex("[0-9a-f-]{36}"))) throw ResponseStatusException(HttpStatus.NOT_FOUND)
  val path=Path.of(photoDir).toAbsolutePath().normalize().resolve(p.arquivo)
  return try {
   val bytes=Files.newByteChannel(path, StandardOpenOption.READ, java.nio.file.LinkOption.NOFOLLOW_LINKS).use { channel ->
    java.nio.channels.Channels.newInputStream(channel).readNBytes(5*1024*1024+1)
   }
   if(bytes.size > 5*1024*1024) throw java.io.IOException("Arquivo excede limite")
   p.tipo to bytes
  } catch(e: Exception) { log.warn("Arquivo de foto indisponível para animal {} e foto {}", id, fotoId); throw ResponseStatusException(HttpStatus.NOT_FOUND) }
 }
}
