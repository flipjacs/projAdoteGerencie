package br.com.patasunidas.financeiro

import androidx.compose.runtime.*
import br.com.patasunidas.data.*
import kotlinx.coroutines.*

/** Bound to one authenticated identity; each HTTP call also cancels with this scope. */
class FinanceStore(val api:FinanceApi,private val sessions:SessionController,private val session:AuthSession,private val identity:UserIdentity) {
 val profile=identity.perfil
 val internal=canManageFinance(profile)
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var closed=false
 var busy by mutableStateOf(false);private set
 var message by mutableStateOf<String?>(null);private set
 var donations by mutableStateOf<FinancePage<DonationRecord>?>(null);private set
 var movements by mutableStateOf<FinancePage<MovementRecord>?>(null);private set
 var summary by mutableStateOf<FinanceSummary?>(null);private set
 var selectedDonation by mutableStateOf<DonationRecord?>(null);private set
 var selectedMovement by mutableStateOf<MovementRecord?>(null);private set
 var donationStatus by mutableStateOf<DonationStatus?>(null);private set
 var movementType by mutableStateOf<MovementType?>(null);private set
 var movementStatus by mutableStateOf<MovementStatus?>(null);private set
 var movementCategory by mutableStateOf<FinanceCategory?>(null);private set
 var de by mutableStateOf("");private set
 var ate by mutableStateOf("");private set
 private fun valid():Boolean {val s=sessions.state as? SessionState.Authenticated ?: return false;return !closed && s.session===session && s.identity==identity && (profile==UserProfile.USUARIO || internal && identity.hasOng)}
 private suspend fun readDonations(page:Int) {val r=api.donations(session.accessToken,internal,page,donationStatus,de,ate);if(valid()){
  donations=r
  selectedDonation?.id?.let {id->r.items.firstOrNull {it.id==id}?.let {selectedDonation=it}}
 }}
 private suspend fun readMovements(page:Int) {val r=api.movements(session.accessToken,page,movementType,movementStatus,movementCategory,de,ate);if(valid()){
  movements=r
  selectedMovement?.id?.let {id->r.items.firstOrNull {it.id==id}?.let {selectedMovement=it}}
 }}
 private suspend fun readSummary() {if(!internal)return;val r=api.summary(session.accessToken,de,ate);if(valid())summary=r}
 private suspend fun httpError(e:FinanceHttpException) {if(!valid())return;when(e.status){
  401->sessions.unauthorized()
  403->{sessions.operationForbidden();if(valid())message="Você não tem permissão para esta operação." else clearData()}
  404->{selectedDonation=null;selectedMovement=null;message="Registro não encontrado ou indisponível para esta conta ou ONG."}
  400->message="Confira os campos, valores e o período informado."
  409->message="A situação mudou. Confira os dados atualizados antes de tentar novamente."
  429->message="Muitas tentativas. Aguarde antes de tentar novamente."
  else->message="Não foi possível concluir a operação."
 }}
 private fun run(action:suspend ()->Unit) {if(busy||!valid())return;busy=true;message=null;scope.launch {try{action()}catch(e:CancellationException){throw e}catch(e:FinanceHttpException){httpError(e);if(e.status==409&&valid())rereadConflict()}catch(_:IllegalArgumentException){if(valid())message="Confira o valor, a categoria e os campos informados."}catch(_:Exception){if(valid())message="Não foi possível conectar à API. Tente novamente."}finally{if(valid())busy=false}}}
 private suspend fun rereadConflict() {
  // Only GETs: never repeat an uncertain write.
  val jobs=listOf<suspend ()->Unit>(
   {selectedDonation?.id?.let {val r=api.donationDetails(session.accessToken,internal,it);if(valid())selectedDonation=r}},
   {selectedMovement?.id?.let {val r=api.movementDetails(session.accessToken,it);if(valid())selectedMovement=r}},
   {if(donations!=null)readDonations(donations?.page?:0)},
   {if(movements!=null)readMovements(movements?.page?:0)},
   {readSummary()}
  )
  for(job in jobs){if(!valid())return;try{job()}catch(e:CancellationException){throw e}catch(e:FinanceHttpException){if(e.status!=409)httpError(e)}catch(_:Exception){}}
 }
 private suspend fun refreshedAfterWrite() {
  // Official aggregate is independent of list filters and page size.
  try{readSummary();if(valid()&&donations!=null)readDonations(donations?.page?:0);if(valid()&&movements!=null)readMovements(movements?.page?:0)}
  catch(e:CancellationException){throw e}catch(e:FinanceHttpException){httpError(e);if(valid()&&e.status !in listOf(401,403))message="Operação concluída. Atualize a listagem e o resumo."}
  catch(_:Exception){if(valid())message="Operação concluída. Atualize a listagem e o resumo."}
 }
 fun loadDonations(page:Int=0)=run {readDonations(page)}
 fun loadMovements(page:Int=0)=run {if(internal){readMovements(page);if(valid())readSummary()}}
 fun loadSummary()=run {readSummary()}
 fun setDonationFilters(status:DonationStatus?,de:String="",ate:String="") {if(busy||!valid())return;donationStatus=status;this.de=de;this.ate=ate;loadDonations()}
 fun setMovementFilters(type:MovementType?,status:MovementStatus?,category:FinanceCategory?,de:String="",ate:String="") {if(busy||!valid())return;movementType=type;movementStatus=status;movementCategory=category;this.de=de;this.ate=ate;loadMovements()}
 fun detailsDonation(id:Long)=run {selectedMovement=null;val r=api.donationDetails(session.accessToken,internal,id);if(valid())selectedDonation=r}
 fun detailsMovement(id:Long)=run {if(internal){selectedDonation=null;val r=api.movementDetails(session.accessToken,id);if(valid())selectedMovement=r}}
 fun clearSelection(){if(!busy){selectedDonation=null;selectedMovement=null}}
 fun createDonation(ongId:Int,value:String,message:String,done:()->Unit)=run {if(profile!=UserProfile.USUARIO)return@run;val r=api.createDonation(session.accessToken,ongId,value,message);if(valid()){selectedDonation=r;this.message="Intenção de doação registrada. O recebimento será confirmado pela ONG.";done();refreshedAfterWrite()}}
 fun createMovement(type:MovementType,category:FinanceCategory,description:String,value:String,done:()->Unit)=run {if(!internal)return@run;val r=api.createMovement(session.accessToken,type,category,description,value);if(valid()){selectedMovement=r;message="Lançamento registrado como pendente.";done();refreshedAfterWrite()}}
 fun actDonation(action:FinanceAction,reason:String="",done:()->Unit)=run {val r=selectedDonation?:return@run;if(action !in donationActions(r,profile))return@run;val updated=api.actDonation(session.accessToken,internal,r.id,action,reason);if(valid()){selectedDonation=updated;message="Doação atualizada.";done();refreshedAfterWrite()}}
 fun actMovement(action:FinanceAction,reason:String="",done:()->Unit)=run {val r=selectedMovement?:return@run;if(action !in movementActions(r,profile))return@run;val updated=api.actMovement(session.accessToken,r.id,action,reason);if(valid()){selectedMovement=updated;message="Lançamento atualizado.";done();refreshedAfterWrite()}}
 private fun clearData(){donations=null;movements=null;summary=null;selectedDonation=null;selectedMovement=null}
 fun close(){closed=true;scope.cancel();busy=false;message=null;clearData()}
}
