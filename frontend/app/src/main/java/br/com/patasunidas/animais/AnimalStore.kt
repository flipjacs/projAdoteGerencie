package br.com.patasunidas.animais

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.*
import br.com.patasunidas.data.*
import kotlinx.coroutines.*
import java.io.IOException

class AnimalStore(val api:AnimalApi,private val sessions:SessionController,private val session:AuthSession,private val ongId:Int?) {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 var busy by mutableStateOf(false); private set
 var message by mutableStateOf<String?>(null); private set
 var items by mutableStateOf<List<Animal>>(emptyList()); private set
 var selected by mutableStateOf<Animal?>(null); private set
 var conflict by mutableStateOf(false); private set
 var currentRemote by mutableStateOf<Animal?>(null); private set
 var total by mutableLongStateOf(0); private set
 var page by mutableIntStateOf(0); private set
 var pages by mutableIntStateOf(0); private set
 var active by mutableStateOf<Boolean?>(true); private set
 private var checkingIdentity = false
 private fun valid():Boolean { val current=sessions.state as? SessionState.Authenticated ?: return false; return current.session === session && current.identity.isInternal && current.identity.ongId == ongId && ongId != null }
 private fun run(action:suspend ()->Unit) {
  if(busy || !valid()) return
  busy=true; message=null
  scope.launch {
   try { action() }
   catch(e:CancellationException) { throw e }
   catch(e:AnimalHttpException) { if(valid()) when(e.status) {
     401 -> sessions.unauthorized()
     403 -> { sessions.operationForbidden(); if(valid()) message="Acesso não permitido ou conta indisponível." }
     400 -> message="Confira os dados ou o formato da imagem."
     404 -> message="Animal ou foto não encontrado para esta ONG."
     409 -> { conflict=e.versionConflict; message=if(e.versionConflict) "Este animal foi atualizado por outra pessoa. Recarregue os dados antes de salvar novamente." else "A operação conflita com o estado atual do animal. Atualize e tente novamente." }
     413 -> message="A imagem excede o tamanho permitido."
     else -> message="Não foi possível concluir a operação. Tente novamente."
    } }
   catch(_:ImageValidationException) { if(valid()) message="Selecione um JPEG ou PNG de até 5 MiB e 16 milhões de pixels." }
   catch(_:IOException) { if(valid()) message="Não foi possível conectar à API. Verifique a conexão." }
   catch(_:Exception) { if(valid()) message="Não foi possível concluir a operação." }
   finally { if(valid()) busy=false }
  }
 }
 fun filter(value:Boolean?) { if(busy) return; active=value; load(0) }
 fun load(number:Int=0) = run { val result=api.list(session.accessToken,number,active); if(valid()) { items=result.items; page=result.number; pages=result.pages; total=result.total } }
 fun details(id:Long,onDone:()->Unit={}) = run { val a=api.get(session.accessToken,id); if(valid()) { if(selected?.id != id) { conflict=false; currentRemote=null }; selected=a; onDone() } }
 fun save(id:Long?,form:AnimalForm,versao:Long?,onDone:()->Unit) {
  val error=form.validate(); if(error!=null) { message=error; return }
  run { val a=api.save(session.accessToken,id,form,versao); if(valid()) { selected=a; conflict=false; currentRemote=null; message=if(id==null) "Animal cadastrado. Agora você pode adicionar fotos." else "Animal atualizado."; onDone() } }
 }
 fun setStatus(status:AnimalStatus,versao:Long) { val a=selected ?: return; if(!a.ativo || status !in a.situacao.next()) return; run { val updated=api.status(session.accessToken,a.id,status,versao); if(valid()) { selected=updated; message="Situação atualizada." } } }
 fun fetchCurrent(id:Long) = run { val a=api.get(session.accessToken,id); if(valid() && selected?.id==id) currentRemote=a }
 fun acceptCurrent(id:Long):Animal? { val a=currentRemote?.takeIf { it.id==id } ?: return null; selected=a; currentRemote=null; conflict=false; message=null; return a }
 fun publication(published:Boolean) { val a=selected ?: return; if(!a.ativo) return; run { val updated=api.publication(session.accessToken,a.id,published); if(valid()) { selected=updated; message=if(published) "Animal publicado. A visibilidade depende da situação disponível." else "Animal despublicado." } } }
 fun deactivate() { val a=selected ?: return; if(!a.ativo) return; run { val updated=api.deactivate(session.accessToken,a.id); if(valid()) { selected=updated; message="Animal inativado." } } }
 fun upload(uri:Uri,resolver:ContentResolver) { val a=selected ?: return; if(!a.ativo) return; run { api.upload(session.accessToken,a.id,uri,resolver); if(valid()) { selected=api.get(session.accessToken,a.id); message="Fotografia enviada." } } }
 fun photoFailure(e:AnimalHttpException) { if(!valid()) return; if(e.status==401) sessions.unauthorized() else if(e.status==403 && !checkingIdentity) scope.launch { checkingIdentity=true; try { sessions.operationForbidden() } finally { checkingIdentity=false } } }
 fun token()=session.accessToken
 fun close() { scope.cancel() }
}
