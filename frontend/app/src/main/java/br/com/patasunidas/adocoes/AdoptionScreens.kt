package br.com.patasunidas.adocoes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.animais.*
import br.com.patasunidas.ui.*
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun date(value:String)=try {Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}catch(_:Exception){value}
@Composable private fun Panel(content:@Composable ColumnScope.()->Unit){Surface(color=PU.Paper,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,PU.Border)){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}}
@Composable private fun Action(text: String, enabled: Boolean = true, secondary: Boolean = false, onClick: () -> Unit) =
    WorkspaceAction(text, enabled, secondary, onClick)
@Composable private fun AdoptionShell(title:String,description:String,onBack:()->Unit,onLogout:()->Unit,internal:Boolean=false,showBack:Boolean=true,onCatalog:()->Unit=onBack,onHome:()->Unit=onCatalog,content:@Composable ColumnScope.()->Unit){
 BackHandler(onBack=onBack)
 Column(Modifier.fillMaxSize().background(PU.Cream).statusBarsPadding().navigationBarsPadding().imePadding()){
  Row(Modifier.fillMaxWidth().background(PU.Paper).padding(horizontal=20.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){if(internal)InstitutionalMenuButton();if(internal)TextButton(onClick=onBack,contentPadding=PaddingValues(0.dp),modifier=Modifier.size(40.dp)){Text("←",fontSize=24.sp)};Row(Modifier.weight(1f).padding(start=if(internal)10.dp else 0.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.size(34.dp).background(PU.Orange,RoundedCornerShape(12.dp)).padding(7.dp)){PawIcon(PU.Green,Modifier.fillMaxSize())};Text("Patas Unidas",fontWeight=FontWeight.Bold,fontSize=14.sp,color=PU.Green)};OutlinedButton(onClick=onLogout){Text("Sair")}}
  if(!internal)Row(Modifier.fillMaxWidth().background(PU.Paper).padding(horizontal=20.dp),horizontalArrangement=Arrangement.SpaceEvenly){TextButton(onClick=onHome){Text("Início")};TextButton(onClick=onCatalog){Text("Animais")};Text("Adoções",color=PU.Green,fontWeight=FontWeight.Bold,modifier=Modifier.padding(14.dp))}
  Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
   if(showBack)TextButton(onClick=onBack,contentPadding=PaddingValues(0.dp)){Text("← Voltar")}
   else if(!internal)Text("Minha área",color=PU.Foliage,fontSize=13.sp,fontWeight=FontWeight.Bold)
   Column(verticalArrangement=Arrangement.spacedBy(12.dp)){WorkspaceTitle(title);Text(description,color=PU.Muted,lineHeight=24.sp)}
   content()
  }
 }
}
@Composable private fun Badge(status: AdoptionStatus, internal: Boolean) {
    val tone = when (status) {
        AdoptionStatus.PENDENTE -> if (internal) StatusTone.Pending else StatusTone.PersonalPending
        AdoptionStatus.APROVADA -> if (internal) StatusTone.Reserved else StatusTone.PersonalPending
        AdoptionStatus.CONCLUIDA -> if (internal) StatusTone.Success else StatusTone.PersonalSuccess
        AdoptionStatus.CANCELADA, AdoptionStatus.REJEITADA ->
            if (internal) StatusTone.Closed else StatusTone.PersonalMuted
    }
    WorkspaceStatus(status.label, tone)
}
private fun note(status:AdoptionStatus)=when(status){AdoptionStatus.PENDENTE->"A ONG ainda está analisando seu pedido.";AdoptionStatus.APROVADA->"O animal está reservado. A adoção será concluída após a confirmação da entrega pela ONG.";AdoptionStatus.REJEITADA->"A ONG não aprovou esta solicitação.";AdoptionStatus.CANCELADA->"Esta solicitação foi cancelada. O histórico foi preservado.";AdoptionStatus.CONCLUIDA->"A entrega foi confirmada. Adoção concluída."}
@Composable private fun Feedback(store:AdoptionStore){if(store.busy)LinearProgressIndicator(Modifier.fillMaxWidth());store.message?.let{Text(it,color=PU.Green,modifier=Modifier.fillMaxWidth().background(PU.CardGreen,RoundedCornerShape(12.dp)).padding(16.dp))}}
@Composable fun AdoptionExperience(store:AdoptionStore,onBack:()->Unit,onLogout:()->Unit,onCatalog:()->Unit=onBack,onMedical:((Long,String)->Unit)?=null){
 var id by rememberSaveable {mutableStateOf<Long?>(null)}
 var showMessage by rememberSaveable { mutableStateOf(false) }
 var decision by rememberSaveable(id) {mutableStateOf<AdoptionAction?>(null)}
 var reason by rememberSaveable(id,decision){mutableStateOf("")}
 var confirmed by rememberSaveable(id,decision){mutableStateOf(false)}
 var animalTerm by rememberSaveable{mutableStateOf(store.animalFilter?.toString().orEmpty())}
 var filterError by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(id,store){if(id==null)store.load(store.result?.page ?: 0)else store.details(id!!)}
 val record=store.selected?.takeIf{it.id==id}
 fun back(){if(store.busy)return;if(id!=null){id=null;decision=null;store.clearSelection()}else onBack()}
 AdoptionShell(if(id!=null&&store.internal)"Solicitação #$id" else if(store.internal)"Solicitações de adoção" else "Minhas adoções",if(id!=null&&store.internal)if(store.internal)"Análise e histórico da decisão"else"Acompanhe sua solicitação e a decisão da ONG."else if(store.internal)"Acompanhe os pedidos recebidos e as decisões da sua ONG."else"Acompanhe cada solicitação até a decisão da ONG e, quando ocorrer, a entrega do animal.",::back,onLogout,internal=store.internal,showBack=id!=null&&store.internal,onCatalog=onCatalog,onHome=onBack){
  Feedback(store)
  if(id==null||!store.internal){
   if(store.internal)Panel{
    var open by remember{mutableStateOf(false)}
    Text("Situação",fontWeight=FontWeight.Bold,fontSize=13.sp)
    Box{OutlinedButton(onClick={open=true},enabled=!store.busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(store.filter?.label ?: "Todas");Text("⌄")}};DropdownMenu(open,{open=false}){DropdownMenuItem(text={Text("Todas")},onClick={open=false;store.filters(null,store.animalFilter)});AdoptionStatus.entries.forEach{s->DropdownMenuItem(text={Text(s.label)},onClick={open=false;store.filters(s,store.animalFilter)})}}}
    Text("ID do animal",fontSize=13.sp,fontWeight=FontWeight.Bold)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
     OutlinedTextField(value=animalTerm,onValueChange={animalTerm=it.filter(Char::isDigit).take(18);filterError=null},placeholder={Text("Ex.: 25",fontSize=14.sp)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.weight(1f),isError=filterError!=null,shape=RoundedCornerShape(8.dp))
     OutlinedButton(onClick={val n=animalTerm.toLongOrNull();if(animalTerm.isNotBlank()&&(n==null||n<=0))filterError="Informe um ID positivo."else store.filters(store.filter,n)},enabled=!store.busy,contentPadding=PaddingValues(horizontal=14.dp),modifier=Modifier.heightIn(min=48.dp)){Text("Filtrar",fontSize=13.sp)}
    }
    filterError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    if(store.filter!=null||store.animalFilter!=null)TextButton(onClick={animalTerm="";store.filters(null,null)},enabled=!store.busy){Text("Limpar filtros")}
   }
   if(!store.internal)Action("Conhecer animais",!store.busy,true,onCatalog)
   val p=store.result
   if(!store.busy&&p?.items?.isEmpty()==true)Panel{Text("Nenhuma solicitação encontrada",fontWeight=FontWeight.Bold,fontSize=20.sp);Text(if(store.internal)"Novos pedidos aparecerão aqui quando forem enviados."else"Quando você solicitar uma adoção, poderá acompanhar o andamento por aqui.",color=PU.Muted)}
   p?.items?.forEach{listed->
    val r=if(!store.internal)store.selected?.takeIf{it.id==listed.id}?:listed else listed
    Panel{
     if(store.internal){
      TableFact("Animal",r.animalName+" · #${r.id}");TableFact("Solicitante",r.requester.orEmpty());TableFact("Recebida em",date(r.created));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Situação",fontSize=12.sp,color=PU.Muted);Badge(r.status,true)}
      TextButton(onClick={id=r.id},enabled=!store.busy,contentPadding=PaddingValues(0.dp)){Text(if(r.status==AdoptionStatus.PENDENTE)"Analisar solicitação →"else"Ver solicitação →")}
     }else{
      Badge(r.status,false);Text("Solicitação #${r.id}",fontSize=12.sp,color=PU.Foliage,fontWeight=FontWeight.Bold);Text(r.animalName,fontSize=23.sp,fontWeight=FontWeight.Bold,color=PU.Green);Text("${r.ongName} · ${date(r.created)}",fontSize=13.sp,color=PU.Muted);Text(note(r.status),color=PU.Muted,lineHeight=23.sp)
      r.rejection?.let{Text("Justificativa: $it",color=PU.Muted,lineHeight=23.sp)};r.cancellation?.let{Text("Motivo registrado: $it",color=PU.Muted,lineHeight=23.sp)};r.delivered?.let{Text("Entrega confirmada em ${date(it)}.",color=PU.Green)}
      if(r.status==AdoptionStatus.CONCLUIDA && r.delivered!=null && onMedical!=null) {
       TextButton(onClick={onMedical(r.animalId,r.animalName)},enabled=!store.busy,contentPadding=PaddingValues(0.dp)){Text("Ver histórico veterinário de ${r.animalName}",fontWeight=FontWeight.Bold)}
      }
      if(!r.message.isNullOrBlank()){
       TextButton(onClick={showMessage=!(id==r.id&&showMessage);id=r.id;decision=null},enabled=!store.busy,contentPadding=PaddingValues(0.dp)){Text("Ver minha mensagem",fontWeight=FontWeight.Bold)}
       if(id==r.id&&showMessage)Text(r.message,color=PU.Muted,lineHeight=23.sp)
      }
      if(AdoptionAction.CANCEL in actionsFor(r.status,store.profile)){
       if(id==r.id&&decision==AdoptionAction.CANCEL){
        Text("Cancelar solicitação?",fontWeight=FontWeight.Bold);Text("O pedido permanecerá no seu histórico. Confirme se deseja cancelar.",color=PU.Muted,lineHeight=23.sp)
        Action(if(store.busy)"Cancelando…"else"Cancelar solicitação",!store.busy&&store.selected?.id==r.id){store.act(AdoptionAction.CANCEL,"",false){decision=null}}
        TextButton(onClick={decision=null},enabled=!store.busy){Text("Manter registro")}
       }else TextButton(onClick={id=r.id;decision=AdoptionAction.CANCEL},enabled=!store.busy){Text("Cancelar solicitação",fontWeight=FontWeight.Bold)}
      }
     }
    }
   }
   if(p!=null&&p.pages>1){Text("${p.total} solicitações · Página ${p.page+1} de ${p.pages}",color=PU.Muted,fontSize=13.sp);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton(onClick={store.load(p.page-1)},enabled=!store.busy&&p.page>0){Text("Anterior")};OutlinedButton(onClick={store.load(p.page+1)},enabled=!store.busy&&p.page+1<p.pages){Text("Próxima")}}}
   TextButton(onClick={store.load(p?.page?:0)},enabled=!store.busy){Text("Atualizar solicitações")}
  }else if(record!=null){
   Panel{
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Pedido recebido em ${date(record.created)}",color=PU.Muted,fontSize=12.sp);Text(record.animalName,fontSize=24.sp,fontWeight=FontWeight.Bold,color=PU.Green)};Badge(record.status,store.internal)}
    if(store.internal){
     val api=remember{AnimalApi(br.com.patasunidas.BuildConfig.API_BASE_URL)}
     val photo by produceState<android.graphics.Bitmap?>(null,record.id,store){value=store.photo(api,record.animalId)}
     photo?.let{Image(it.asImageBitmap(),"Fotografia de ${record.animalName}",Modifier.fillMaxWidth(.8f).aspectRatio(4f/3f).clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Crop)}
    }
    Fact("Animal",record.animalName);if(!store.internal)Fact("ONG",record.ongName);record.requester?.let{Fact("Solicitante",it)};record.email?.let{Fact("E-mail",it)};Fact("Mensagem",record.message?:"Nenhuma mensagem enviada.")
    HorizontalDivider(color=PU.Border);Text("Histórico",fontWeight=FontWeight.Bold,fontSize=18.sp);Text("Solicitação registrada em ${date(record.created)}.",color=PU.Muted)
    record.history.forEach{h->Text("${date(h.at)} · ${AdoptionStatus.entries.firstOrNull{it.name==h.next}?.label?:h.next}${h.reason?.let{"\n$it"}.orEmpty()}",color=PU.Muted,lineHeight=23.sp)}
    record.rejection?.let{Fact("Justificativa da rejeição",it)};record.cancellation?.let{Fact("Motivo do cancelamento",it)};record.delivered?.let{Text("Entrega confirmada em ${date(it)}. Adoção concluída.",color=PU.Green)}
   }
   Panel{
    Text(if(store.internal)when(record.status){AdoptionStatus.PENDENTE->"Decidir solicitação";AdoptionStatus.APROVADA->"Animal reservado";else->"Situação da solicitação"}else"Acompanhamento",fontSize=23.sp,fontWeight=FontWeight.Bold,color=PU.Green)
    Text(if(store.internal)when(record.status){AdoptionStatus.PENDENTE->"A aprovação reserva o animal. A entrega será confirmada depois.";AdoptionStatus.APROVADA->"A adoção ainda não foi concluída. Confirme a entrega somente quando ela ocorrer.";else->"Esta solicitação permanece no histórico da ONG."}else note(record.status),color=PU.Muted,lineHeight=24.sp)
    val available=actionsFor(record.status,store.profile)
    val action=decision?.takeIf{it in available}
    if(action==null)available.forEachIndexed{i,a->Action(a.title,!store.busy,i>0){decision=a}}
    else{
     Text(action.heading,fontSize=19.sp,fontWeight=FontWeight.Bold)
     Text(when(action){AdoptionAction.APPROVE->"O animal ficará reservado para esta pessoa. A adoção só será concluída após confirmar a entrega.";AdoptionAction.DELIVER->"Esta ação formaliza a entrega, conclui a adoção e encerra os pedidos concorrentes conforme o servidor.";AdoptionAction.REJECT->"Informe a justificativa para preservar a decisão no histórico.";AdoptionAction.CANCEL_APPROVAL->"A reserva será liberada e os outros pedidos pendentes continuarão para análise.";AdoptionAction.CANCEL->"O pedido permanecerá no seu histórico. Confirme se deseja cancelar."},color=PU.Muted,lineHeight=24.sp)
     val needsReason=action==AdoptionAction.REJECT||action==AdoptionAction.CANCEL_APPROVAL
     if(needsReason)OutlinedTextField(value=reason,onValueChange={if(it.length<=1000)reason=it},label={Text("Justificativa")},minLines=4,enabled=!store.busy,modifier=Modifier.fillMaxWidth(),supportingText={Text("${reason.length}/1000")})
     if(action==AdoptionAction.DELIVER)Row(Modifier.fillMaxWidth().clickable(enabled=!store.busy){confirmed=!confirmed},verticalAlignment=Alignment.CenterVertically){Checkbox(confirmed,{confirmed=it},enabled=!store.busy);Text("Confirmo que o animal foi entregue ao adotante.",modifier=Modifier.weight(1f),lineHeight=23.sp)}
     Action(if(store.busy)"Salvando…"else action.submit,!store.busy&&(!needsReason||reason.isNotBlank())&&(action!=AdoptionAction.DELIVER||confirmed)){store.act(action,reason,confirmed){decision=null}}
     TextButton(onClick={decision=null},enabled=!store.busy){Text(if(action==AdoptionAction.CANCEL)"Manter registro"else"Voltar às ações")}
    }
   }
   TextButton(onClick={store.details(id!!)},enabled=!store.busy){Text("Atualizar solicitação")}
  }else if(!store.busy)Action("Tentar novamente",secondary=true){store.details(id!!)}
 }
}
@Composable private fun TableFact(label:String,value:String){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp)){Text(label,fontSize=12.sp,color=PU.Muted,modifier=Modifier.weight(1f));Text(value,fontSize=13.sp,color=PU.Ink,modifier=Modifier.weight(1.4f),textAlign=androidx.compose.ui.text.style.TextAlign.End)}}
@Composable private fun Fact(label:String,value:String){Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text(label,color=PU.Muted,fontSize=12.sp);Text(value,color=PU.Ink,lineHeight=24.sp)}}

@Composable fun AdoptionRequestScreen(store:AdoptionStore,animalApi:AnimalApi,animalId:Long,onBack:()->Unit,onLogout:()->Unit,onMyRequests:()->Unit,onCatalog:()->Unit=onBack,onHome:()->Unit=onCatalog){
 var text by rememberSaveable(animalId){mutableStateOf("")};var success by rememberSaveable(animalId){mutableStateOf(false)}
 var animal by remember{mutableStateOf<PublicAnimal?>(null)};var error by remember{mutableStateOf<String?>(null)};var attempt by remember{mutableIntStateOf(0)}
 LaunchedEffect(animalId,attempt){try{error=null;animal=animalApi.publicGet(animalId)}catch(e:CancellationException){throw e}catch(_:Exception){error="Este animal não está disponível agora. Volte ao catálogo ou tente novamente."}}
 AdoptionShell(if(success)"Solicitação enviada"else"Solicitar adoção",if(success)"A solicitação está pendente de análise pela ONG. Você pode acompanhar o andamento na sua área."else"Envie uma mensagem breve à ONG responsável pelo animal.",onBack,onLogout,onCatalog=onCatalog,onHome=onHome){
  Feedback(store)
  if(success)Action("Minhas adoções",onClick=onMyRequests)
  else{
   error?.let{Text(it,color=MaterialTheme.colorScheme.error);Action("Tentar novamente",secondary=true){attempt++}}
   animal?.let{a->Surface(color=PU.Paper,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,PU.Border)){Row(Modifier.fillMaxWidth().heightIn(min=140.dp),verticalAlignment=Alignment.CenterVertically){PublicPhoto(animalApi,a.id,a.fotos.firstOrNull{it.principal}?:a.fotos.firstOrNull(),Modifier.width(112.dp).height(140.dp));Column(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Animal",color=PU.Foliage,fontSize=13.sp);Text(a.nome,fontSize=20.sp,fontWeight=FontWeight.Bold,color=PU.Green);Text(a.ongNome.orEmpty(),color=PU.Muted,fontSize=13.sp);Text(listOfNotNull(a.cidade,a.estado).joinToString(", "),color=PU.Muted,fontSize=13.sp)}}}
    Panel{Text("Mensagem para a ONG (opcional)",fontSize=14.sp,fontWeight=FontWeight.Bold);OutlinedTextField(value=text,onValueChange={if(it.length<=2000)text=it},minLines=4,enabled=!store.busy,modifier=Modifier.fillMaxWidth(),supportingText={Text("${text.length}/2000")});Action(if(store.busy)"Enviando…"else"Enviar solicitação",!store.busy){store.create(animalId,text){success=true}}}
   }
  }
 }
}
