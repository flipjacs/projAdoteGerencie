package br.com.patasunidas.adocoes

import androidx.compose.runtime.*
import br.com.patasunidas.data.*
import kotlinx.coroutines.*

/** Session-scoped state; late responses cannot cross identities or ONG boundaries. */
class AdoptionStore(val api:AdoptionApi,private val sessions:SessionController,private val session:AuthSession,private val identity:UserIdentity,private val changed:()->Unit={}) {
 val internal=canManageAdoptions(identity.perfil)
 val profile=identity.perfil
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var closed=false
 var busy by mutableStateOf(false);private set
 var message by mutableStateOf<String?>(null);private set
 var result by mutableStateOf<AdoptionPage?>(null);private set
 var selected by mutableStateOf<AdoptionRecord?>(null);private set
 var filter by mutableStateOf<AdoptionStatus?>(null);private set
 var animalFilter by mutableStateOf<Long?>(null);private set
 private fun valid():Boolean {val s=sessions.state as? SessionState.Authenticated ?: return false;return !closed && s.session===session && s.identity==identity && (profile==UserProfile.USUARIO || internal && identity.hasOng)}
 private suspend fun refresh() {val p=api.list(session.accessToken,internal,result?.page ?: 0,filter,animalFilter);if(valid())result=p}
 private fun run(action:suspend ()->Unit) {if(busy||!valid())return;busy=true;message=null;scope.launch {try{action()}catch(e:CancellationException){throw e}catch(e:AdoptionHttpException){if(valid())when(e.status){401->sessions.unauthorized();403->{sessions.operationForbidden();if(valid())message="Você não tem permissão para esta operação."};404->{selected=null;message="Solicitação não encontrada ou indisponível para esta conta."};409->{message="A situação mudou ou já existe uma solicitação. Confira os dados atualizados antes de tentar novamente.";try{val id=selected?.id; if(id!=null){val current=api.details(session.accessToken,internal,id);if(valid())selected=current};refresh()}catch(refreshError:AdoptionHttpException){if(valid()&&refreshError.status==401)sessions.unauthorized();if(valid()&&refreshError.status==403)sessions.operationForbidden()}catch(e:CancellationException){throw e}catch(_:Exception){}};400->message="Confira os campos e a justificativa informada.";429->message="Muitas tentativas. Aguarde antes de tentar novamente.";else->message="Não foi possível concluir a operação."}}catch(_:Exception){if(valid())message="Não foi possível conectar à API. Tente novamente."}finally{if(valid())busy=false}}}
 fun load(page:Int=0)=run {val p=api.list(session.accessToken,internal,page,filter,animalFilter);if(valid())result=p}
 fun filters(status:AdoptionStatus?,animalId:Long?){if(busy)return;filter=status;animalFilter=animalId;load()}
 fun details(id:Long)=run {val r=api.details(session.accessToken,internal,id);if(valid())selected=r}
 fun clearSelection(){if(!busy)selected=null}
 fun create(animalId:Long,text:String,done:()->Unit)=run {val r=api.create(session.accessToken,animalId,text);if(valid()){selected=r;changed();done()}}
 fun act(action:AdoptionAction,reason:String,confirmed:Boolean,done:()->Unit)=run {val r=selected ?: return@run;if(action !in actionsFor(r.status,profile))return@run;val updated=api.act(session.accessToken,internal,r.id,action,reason,confirmed);if(valid()){selected=updated;message="Solicitação atualizada.";changed();done();try{refresh()}catch(e:CancellationException){throw e}catch(_:Exception){message="Solicitação atualizada. Recarregue a listagem."}}}
 suspend fun photo(api:br.com.patasunidas.animais.AnimalApi,animalId:Long):android.graphics.Bitmap? {
  if(!valid()||!internal)return null
  return try {val a=api.get(session.accessToken,animalId);val p=a.fotos.firstOrNull{it.principal}?:a.fotos.firstOrNull();val image=p?.let{api.photo(session.accessToken,animalId,it.id)};if(valid())image else null}
  catch(e:CancellationException){throw e}
  catch(e:br.com.patasunidas.animais.AnimalHttpException){if(valid()&&e.status==401)sessions.unauthorized();if(valid()&&e.status==403)sessions.operationForbidden();null}
  catch(_:Exception){null}
 }
 fun close(){closed=true;scope.cancel();selected=null;result=null}
}
