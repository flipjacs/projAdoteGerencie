package br.com.patasunidas.prontuarios

import androidx.compose.runtime.*
import br.com.patasunidas.animais.AnimalStatus
import br.com.patasunidas.data.*
import kotlinx.coroutines.*

/** Clinical data belongs to exactly one authenticated session and ONG identity. */
class ProntuarioStore(val api:ProntuarioApi,private val sessions:SessionController,private val session:AuthSession,private val identity:UserIdentity,val animalId:Long,val animalName:String,val animalStatus:AnimalStatus?=null) {
 val profile=identity.perfil
 val internal=profile in listOf(UserProfile.ADMINISTRADOR,UserProfile.VETERINARIO,UserProfile.VOLUNTARIO)
 val canWrite=profile in listOf(UserProfile.ADMINISTRADOR,UserProfile.VETERINARIO)&&identity.hasOng
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var closed=false
 var busy by mutableStateOf(false);private set
 var message by mutableStateOf<String?>(null);private set
 var institutional by mutableStateOf<MedicalPage<InstitutionalRecord>?>(null);private set
 var personal by mutableStateOf<MedicalPage<PersonalRecord>?>(null);private set
 var selectedInstitutional by mutableStateOf<InstitutionalRecord?>(null);private set
 var selectedPersonal by mutableStateOf<PersonalRecord?>(null);private set
 var history by mutableStateOf<MedicalPage<CorrectionRecord>?>(null);private set
 var conflictRemote by mutableStateOf<InstitutionalRecord?>(null);private set
 var conflictPending by mutableStateOf(false);private set
 private var conflictRecordId:Long?=null
 var typeFilter by mutableStateOf<ClinicalType?>(null);private set
 var de by mutableStateOf("");private set
 var ate by mutableStateOf("");private set
 private fun valid():Boolean{val a=sessions.state as? SessionState.Authenticated?:return false;return !closed&&a.session===session&&a.identity==identity&&(profile==UserProfile.USUARIO||internal&&identity.hasOng)}
 private suspend fun read(page:Int){if(internal){val r=api.listInstitutional(session.accessToken,animalId,page,typeFilter,de,ate);if(valid()){institutional=r;selectedInstitutional?.id?.let{id->r.items.firstOrNull{it.id==id}?.let{selectedInstitutional=it}}}}else{val r=api.listPersonal(session.accessToken,animalId,page,typeFilter,de,ate);if(valid()){personal=r;selectedPersonal?.id?.let{id->r.items.firstOrNull{it.id==id}?.let{selectedPersonal=it}}}}}
 private fun clearData(resetConflict:Boolean=true){institutional=null;personal=null;selectedInstitutional=null;selectedPersonal=null;history=null;conflictRemote=null;if(resetConflict){conflictPending=false;conflictRecordId=null}}
 private suspend fun error(e:ProntuarioHttpException){if(!valid())return;when(e.status){
  401->{clearData();sessions.unauthorized()}
  403->{sessions.operationForbidden();if(valid())message="Você não tem permissão para esta operação." else clearData()}
  404->{clearData(resetConflict=false);message="Animal ou registro não encontrado ou indisponível para esta conta ou ONG."}
  400->message="Confira os campos clínicos, a justificativa e o período informado."
  409->message="Este registro mudou ou a situação do animal impede a operação. Revise os dados atualizados antes de confirmar novamente."
  else->message="Não foi possível concluir a operação."
 }}
 private fun run(action:suspend()->Unit){if(busy||!valid())return;busy=true;message=null;scope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:ProntuarioHttpException){error(e)}catch(e:IllegalArgumentException){if(valid())message=e.message?:"Confira os campos informados."}catch(_:Exception){if(valid())message="Não foi possível conectar à API. Tente novamente."}finally{busy=false;if(!valid())clearData()}}}
 fun load(page:Int=0)=run{read(page)}
 fun filters(type:ClinicalType?,de:String="",ate:String="") {if(busy||!valid())return;if((de.isNotEmpty()&&!validClinicalDate(de))||(ate.isNotEmpty()&&!validClinicalDate(ate))||(de.isNotEmpty()&&ate.isNotEmpty()&&ate<de)){message="Informe um período válido (AAAA-MM-DD), com fim igual ou posterior ao início.";return};typeFilter=type;this.de=de;this.ate=ate;load()}
 fun details(id:Long)=run{history=null;if(!conflictPending)conflictRemote=null;if(internal){selectedInstitutional=null;val r=api.institutionalDetails(session.accessToken,animalId,id);if(valid())selectedInstitutional=r}else{selectedPersonal=null;val r=api.personalDetails(session.accessToken,animalId,id);if(valid())selectedPersonal=r}}
 fun loadHistory(id:Long,page:Int=0)=run{if(internal){val r=api.corrections(session.accessToken,animalId,id,page);if(valid())history=r}}
 private suspend fun refreshAfterWrite(){try{if(institutional!=null&&valid())read(institutional!!.page)}catch(e:CancellationException){throw e}catch(e:ProntuarioHttpException){error(e);if(valid()&&e.status !in listOf(401,403,404))message="Operação concluída. Atualize a listagem."}catch(_:Exception){if(valid())message="Operação concluída. Atualize a listagem."}}
 fun create(form:ClinicalForm,done:(Long)->Unit)=run{if(!canWrite)return@run;if(animalStatus in listOf(AnimalStatus.ADOTADO,AnimalStatus.FALECIDO)){message="Este animal não pode receber novos procedimentos clínicos.";return@run};val r=api.create(session.accessToken,animalId,form);if(valid()){selectedInstitutional=r;message=null;done(r.id);refreshAfterWrite()}}
 private suspend fun readConflict(id:Long) {
  try{val latest=api.institutionalDetails(session.accessToken,animalId,id);if(valid()){selectedInstitutional=latest;conflictRemote=latest;message="Este registro mudou. Revise a versão atual e confirme suas alterações novamente."}}
  catch(c:CancellationException){throw c}catch(h:ProntuarioHttpException){error(h)}catch(_:Exception){if(valid())message="O registro mudou. Não foi possível consultar a versão atual. Tente consultar novamente; suas alterações foram preservadas."}
 }
 /** A pending conflict permits only reads until the user reviews the current version. */
 fun retryConflict(id:Long)=run{if(internal&&conflictPending&&conflictRecordId==id)readConflict(id)}
 fun correct(original:InstitutionalRecord,draft:ClinicalForm,reason:String,done:()->Unit)=run{
  if(!canWrite)return@run
  if(conflictPending){message="Consulte e revise a versão atual antes de confirmar novamente.";return@run}
  conflictRemote=null
  try{val r=api.correct(session.accessToken,animalId,original,draft,reason);if(valid()){selectedInstitutional=r;history=null;message=null;done();refreshAfterWrite()}}
  catch(e:ProntuarioHttpException){if(e.status!=409)throw e;error(e);if(valid()){conflictPending=true;conflictRecordId=original.id;readConflict(original.id)};if(valid()&&institutional!=null)try{read(institutional!!.page)}catch(c:CancellationException){throw c}catch(h:ProntuarioHttpException){error(h)}catch(_:Exception){}}
 }
 fun clearSelection(){if(!busy){selectedInstitutional=null;selectedPersonal=null;history=null;if(!conflictPending)conflictRemote=null}}
 fun clearConflict(){conflictRemote=null;conflictPending=false;conflictRecordId=null;message=null}
 fun close(){closed=true;scope.cancel();busy=false;message=null;clearData()}
}
