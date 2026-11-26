package br.com.patasunidas.apadrinhamentos

import androidx.compose.runtime.*
import br.com.patasunidas.data.*
import kotlinx.coroutines.*

/** One instance belongs to one exact authentication session and identity. */
class SupportStore(val api:SupportApi,private val sessions:SessionController,private val session:AuthSession,private val identity:UserIdentity){
 val profile=identity.perfil
 val internal=canManageSupport(profile)&&identity.hasOng
 val canWrite=profile==UserProfile.ADMINISTRADOR&&identity.hasOng
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var closed=false
 private var conflictDemonstrative=false
 var busy by mutableStateOf(false);private set
 var message by mutableStateOf<String?>(null);private set
 var sponsors by mutableStateOf<SupportPage<SponsorRecord>?>(null);private set
 var selectedSponsor by mutableStateOf<SponsorRecord?>(null);private set
 var installments by mutableStateOf<SupportPage<InstallmentRecord>?>(null);private set
 var report by mutableStateOf<FinancialReport?>(null);private set
 var demonstratives by mutableStateOf<SupportPage<DemonstrativeRecord>?>(null);private set
 var selectedDemonstrative by mutableStateOf<DemonstrativeRecord?>(null);private set
 var sponsorStatus by mutableStateOf<SponsorStatus?>(null);private set
 var animalId by mutableStateOf<Long?>(null);private set
 var mode by mutableStateOf<SponsorMode?>(null);private set
 var installmentStatus by mutableStateOf<InstallmentStatus?>(null);private set
 var overdue by mutableStateOf<Boolean?>(null);private set
 var installmentDe by mutableStateOf("");private set
 var installmentAte by mutableStateOf("");private set
 var demonstrativeStatus by mutableStateOf<DemonstrativeStatus?>(null);private set
 var demonstrativeDe by mutableStateOf("");private set
 var demonstrativeAte by mutableStateOf("");private set
 var reportDe by mutableStateOf("");private set
 var reportAte by mutableStateOf("");private set
 private fun valid():Boolean{val s=sessions.state as? SessionState.Authenticated?:return false;return !closed&&s.session===session&&s.identity==identity&&(profile==UserProfile.USUARIO||internal)}
 private fun clearData(){sponsors=null;selectedSponsor=null;installments=null;report=null;demonstratives=null;selectedDemonstrative=null}
 private suspend fun error(e:SupportHttpException){if(!valid())return;when(e.status){
  401->{clearData();sessions.unauthorized()}
  403->{sessions.operationForbidden();if(valid())message="Você não tem permissão para esta operação." else clearData()}
  404->{selectedSponsor=null;selectedDemonstrative=null;installments=null;message="Registro não encontrado ou indisponível para esta conta ou ONG."}
  400->message="Confira os campos, valores e o período informado."
  409->message="A situação mudou. Revise os dados atualizados antes de confirmar novamente. Seus campos foram preservados."
  429->message="Muitas tentativas. Aguarde antes de tentar novamente."
  else->message="Não foi possível concluir a operação."
 }}
 private fun run(action:suspend()->Unit){if(busy||!valid())return;busy=true;message=null;scope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:SupportHttpException){error(e);if(e.status==409&&valid())refreshConflict()}catch(e:IllegalArgumentException){if(valid())message=e.message?:"Confira os campos informados."}catch(_:Exception){if(valid())message="Não foi possível conectar à API. Tente novamente."}finally{busy=false;if(!valid())clearData()}}}
 private suspend fun readSponsors(page:Int){val r=api.sponsors(session.accessToken,internal,page,sponsorStatus,animalId,mode);if(valid()){sponsors=r;selectedSponsor?.id?.let{id->r.items.firstOrNull{it.id==id}?.let{selectedSponsor=it}}}}
 private suspend fun readSponsor(id:Long){val r=api.sponsorDetails(session.accessToken,internal,id);if(valid())selectedSponsor=r}
 private suspend fun readInstallments(page:Int){val id=selectedSponsor?.id?:return;val r=api.installments(session.accessToken,internal,id,page,installmentStatus,overdue,installmentDe,installmentAte);if(valid())installments=r}
 private suspend fun readReport(){if(!canWrite)return;val r=api.report(session.accessToken,reportDe,reportAte);if(valid())report=r}
 private suspend fun readDemonstratives(page:Int){if(!internal)return;val r=api.demonstratives(session.accessToken,page,demonstrativeStatus,demonstrativeDe,demonstrativeAte);if(valid()){demonstratives=r;selectedDemonstrative?.id?.let{id->r.items.firstOrNull{it.id==id}?.let{selectedDemonstrative=it}}}}
 private suspend fun readDemonstrative(id:Long){if(!internal)return;val r=api.demonstrativeDetails(session.accessToken,id);if(valid())selectedDemonstrative=r}
 private suspend fun safeRead(action:suspend()->Unit){if(!valid())return;try{action()}catch(e:CancellationException){throw e}catch(e:SupportHttpException){if(e.status!=409)error(e)}catch(_:Exception){if(valid())message="Operação processada. Consulte os dados atuais; seus campos foram preservados."}}
 private suspend fun refreshConflict(){
  // GET only; form state belongs to the screen and completion callbacks never run on errors.
  if(selectedSponsor==null&&sponsors==null&&!conflictDemonstrative)safeRead{readSponsors(0)}
  if(selectedDemonstrative==null&&demonstratives==null&&conflictDemonstrative)safeRead{readDemonstratives(0)}
  selectedSponsor?.id?.let{safeRead{readSponsor(it)}}
  if(installments!=null)safeRead{readInstallments(installments?.page?:0)}
  if(sponsors!=null)safeRead{readSponsors(sponsors?.page?:0)}
  selectedDemonstrative?.id?.let{safeRead{readDemonstrative(it)}}
  if(demonstratives!=null)safeRead{readDemonstratives(demonstratives?.page?:0)}
  if(report!=null)safeRead{readReport()}
 }
 private suspend fun refreshSponsorLists(){if(sponsors!=null)safeRead{readSponsors(sponsors?.page?:0)};if(installments!=null)safeRead{readInstallments(installments?.page?:0)}}
 fun loadSponsors(page:Int=0)=run{readSponsors(page)}
 fun setSponsorFilters(status:SponsorStatus?,animalId:Long?=null,mode:SponsorMode?=null){if(busy||!valid())return;sponsorStatus=status;this.animalId=animalId;this.mode=mode;loadSponsors()}
 fun detailsSponsor(id:Long)=run{if(selectedSponsor?.id!=id)installments=null;readSponsor(id)}
 /** Explicit detail retry refreshes the sponsor and its current installment page together. */
 fun reloadSponsor(id:Long)=run{if(selectedSponsor?.id!=id)installments=null;readSponsor(id);if(valid())readInstallments(installments?.page?:0)}
 fun loadInstallments(page:Int=0)=run{readInstallments(page)}
 fun setInstallmentFilters(status:InstallmentStatus?,overdue:Boolean?=null,de:String="",ate:String=""){if(busy||!valid())return;try{validateSupportPeriod(de,ate)}catch(e:IllegalArgumentException){message=e.message;return};installmentStatus=status;this.overdue=overdue;installmentDe=de;installmentAte=ate;loadInstallments()}
 fun createSponsor(animalId:Long,mode:SponsorMode,value:String,done:()->Unit={})=run{if(profile!=UserProfile.USUARIO)return@run;conflictDemonstrative=false;val r=api.createSponsor(session.accessToken,animalId,mode,value);if(valid()){selectedSponsor=r;message="Compromisso registrado. A primeira parcela está pendente; não há pagamento automático.";done();refreshSponsorLists()}}
 fun cancelSponsor(done:()->Unit={})=run{if(profile!=UserProfile.USUARIO)return@run;val r=selectedSponsor?:return@run;if(r.status!=SponsorStatus.ATIVO)return@run;conflictDemonstrative=false;val result=api.cancelSponsor(session.accessToken,r.id);if(valid()){selectedSponsor=result;message="Compromisso encerrado. As contribuições anteriores permanecem registradas.";done();refreshSponsorLists()}}
 fun closeSponsor(reason:String,done:()->Unit={})=run{if(!canWrite)return@run;val r=selectedSponsor?:return@run;if(r.status!=SponsorStatus.ATIVO)return@run;conflictDemonstrative=false;val result=api.closeSponsor(session.accessToken,r.id,reason);if(valid()){selectedSponsor=result;message="Compromisso encerrado. O histórico foi preservado.";done();refreshSponsorLists()}}
 fun confirmInstallment(id:Long,done:()->Unit={})=run{if(!canWrite)return@run;val r=selectedSponsor?:return@run;conflictDemonstrative=false;api.confirmInstallment(session.accessToken,r.id,id);if(valid()){message="Contribuição confirmada. A receita foi registrada pela ONG.";done();safeRead{readSponsor(r.id)};refreshSponsorLists();safeRead{readReport()}}}
 fun loadReport(de:String=reportDe,ate:String=reportAte)=run{if(canWrite){validateSupportPeriod(de,ate);reportDe=de;reportAte=ate;readReport()}}
 fun loadDemonstratives(page:Int=0)=run{readDemonstratives(page)}
 fun setDemonstrativeFilters(status:DemonstrativeStatus?,de:String="",ate:String=""){if(busy||!valid())return;try{validateSupportPeriod(de,ate)}catch(e:IllegalArgumentException){message=e.message;return};demonstrativeStatus=status;demonstrativeDe=de;demonstrativeAte=ate;loadDemonstratives()}
 fun detailsDemonstrative(id:Long)=run{readDemonstrative(id)}
 private suspend fun afterDemonstrative(r:DemonstrativeRecord,done:()->Unit){if(valid()){selectedDemonstrative=r;message="Demonstrativo atualizado.";done();if(demonstratives!=null)safeRead{readDemonstratives(demonstratives?.page?:0)}}}
 fun createDemonstrative(form:DemonstrativeForm,done:()->Unit={})=run{if(canWrite){conflictDemonstrative=true;afterDemonstrative(api.createDemonstrative(session.accessToken,form),done)}}
 fun editDemonstrative(form:DemonstrativeForm,done:()->Unit={})=run{val r=selectedDemonstrative?:return@run;if(canWrite&&r.status==DemonstrativeStatus.RASCUNHO){conflictDemonstrative=true;afterDemonstrative(api.editDemonstrative(session.accessToken,r.id,form),done)}}
 private fun publish(published:Boolean,done:()->Unit)=run{val r=selectedDemonstrative?:return@run;if(!canWrite||published==(r.status==DemonstrativeStatus.PUBLICADO))return@run;conflictDemonstrative=true;val result=api.setPublished(session.accessToken,r.id,published);afterDemonstrative(result,done);if(valid())safeRead{readDemonstrative(r.id)}}
 fun publishDemonstrative(done:()->Unit={})=publish(true,done)
 fun unpublishDemonstrative(done:()->Unit={})=publish(false,done)
 fun clearSelection(){if(!busy){selectedSponsor=null;installments=null;selectedDemonstrative=null}}
 fun close(){closed=true;scope.cancel();busy=false;message=null;clearData()}
}

/** Anonymous store uses a reduced DTO and never receives an access token. */
class PublicSupportStore(val api:SupportApi,val ongId:Int){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var closed=false
 var busy by mutableStateOf(false);private set
 var message by mutableStateOf<String?>(null);private set
 var records by mutableStateOf<SupportPage<PublicDemonstrativeRecord>?>(null);private set
 var selected by mutableStateOf<PublicDemonstrativeRecord?>(null);private set
 private fun run(action:suspend()->Unit){if(busy||closed)return;busy=true;message=null;scope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:SupportHttpException){if(!closed){if(e.status==404){selected=null;records=null};message=when(e.status){404->"Demonstrativo ou ONG não encontrado ou indisponível.";429->"Muitas tentativas. Aguarde antes de tentar novamente.";else->"Não foi possível consultar a transparência."}}}catch(_:Exception){if(!closed)message="Não foi possível conectar à API. Tente novamente."}finally{busy=false}}}
 fun load(page:Int=0)=run{val r=api.publicDemonstratives(ongId,page);if(!closed)records=r}
 fun details(id:Long)=run{selected=null;val r=api.publicDetails(ongId,id);if(!closed)selected=r}
 fun clearSelection(){if(!busy)selected=null}
 fun close(){closed=true;scope.cancel();busy=false;records=null;selected=null;message=null}
}
