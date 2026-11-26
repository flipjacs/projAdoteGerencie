package br.com.patasunidas.apadrinhamentos

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.animais.*
import br.com.patasunidas.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@Composable fun SponsorForm(store:SupportStore,api:AnimalApi,animalId:Long,onBack:()->Unit,onLogout:()->Unit,onMySupports:()->Unit,navigation:SupportNavigation){
 var animal by remember{mutableStateOf<PublicAnimal?>(null)};var error by remember{mutableStateOf<String?>(null)};var attempt by remember{mutableIntStateOf(0)}
 var value by rememberSaveable(animalId){mutableStateOf("")};var mode by rememberSaveable(animalId){mutableStateOf(SponsorMode.UNICO)};var created by rememberSaveable(animalId){mutableStateOf(false)}
 LaunchedEffect(api,animalId,attempt){try{error=null;animal=api.publicGet(animalId)}catch(e:CancellationException){throw e}catch(_:Exception){error="Não foi possível carregar o animal. Volte ao catálogo ou tente novamente."}}
 SupportShell("Apadrinhar animal","Escolha um apoio único ou mensal. A ONG confirmará manualmente as parcelas recebidas.",onBack,onLogout,personal=navigation,eyebrow="Patas Unidas"){
  error?.let{Text(it,color=PU.Muted);SupportAction("Tentar novamente",secondary=true){attempt++}}
  if(created)SupportPanel(true){Text("✓ Apadrinhamento iniciado",fontWeight=FontWeight.Bold,color=PU.Green,fontSize=24.sp);Text("A primeira parcela está pendente. As parcelas previstas aparecerão na sua área. Elas não representam cobrança automática.",color=PU.Muted,lineHeight=24.sp);SupportAction("Ver meus apadrinhamentos",onClick=onMySupports)}
  else animal?.let{a->
   Surface(color=PU.Paper,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,PU.Border)){Row(Modifier.fillMaxWidth().heightIn(min=114.dp),verticalAlignment=Alignment.CenterVertically){if(a.fotos.isEmpty()) {
    Column(Modifier.width(112.dp).height(114.dp).background(Color(0xFFE9EEE5)).padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically)) {
     PawIcon(PU.Foliage,Modifier.size(32.dp))
     Text("Fotografia indisponível",fontSize=11.sp,lineHeight=15.sp,color=PU.Foliage,textAlign=TextAlign.Center)
    }
   } else PublicPhoto(api,a.id,a.fotos.firstOrNull{it.principal}?:a.fotos.firstOrNull(),Modifier.width(112.dp).height(114.dp));Column(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Animal",color=PU.Foliage,fontSize=13.sp,fontWeight=FontWeight.Bold);Text(a.nome,fontSize=20.sp,fontWeight=FontWeight.Bold,color=PU.Green);Text(listOfNotNull(a.ongNome,a.cidade,a.estado).joinToString(" · "),color=PU.Muted,fontSize=13.sp)}}}
   SupportPanel(true){Text("Modalidade do apoio",fontWeight=FontWeight.Bold,color=PU.Ink);Row{SponsorMode.entries.forEach{m->FilterChip(selected=mode==m,onClick={if(!store.busy)mode=m},enabled=!store.busy,
    label={Text(if(m==SponsorMode.UNICO)"Apoio único"else"Apoio mensal",fontSize=14.sp,letterSpacing=0.sp,fontWeight=if(mode==m)FontWeight.Bold else FontWeight.Normal)},
    leadingIcon={RadioButton(selected=mode==m,onClick=null,modifier=Modifier.size(16.dp),colors=RadioButtonDefaults.colors(selectedColor=PU.Green,unselectedColor=PU.Muted))},
    shape=RoundedCornerShape(50),
    colors=FilterChipDefaults.filterChipColors(containerColor=PU.Paper,labelColor=PU.Ink,selectedContainerColor=Color(0xFFE9EEE5),selectedLabelColor=PU.Green),
    border=FilterChipDefaults.filterChipBorder(enabled=!store.busy,selected=mode==m,borderColor=PU.Border,selectedBorderColor=PU.Green),
    modifier=Modifier.padding(end=6.dp).heightIn(min=48.dp))}};SupportInput("Valor em reais",value,!store.busy,decimal=true){value=it};Text("Use vírgula ou ponto para os centavos.",color=PU.Muted,fontSize=12.sp);Text("O apoio mensal gera parcelas previstas. A confirmação de recebimento é manual. Não há pagamento automático.",color=PU.Muted,fontSize=13.sp,lineHeight=22.sp);SupportFeedback(store.busy,store.message){attempt++};SupportAction(if(store.busy)"Enviando…"else"Iniciar apadrinhamento",store.busy){store.createSponsor(animalId,mode,value){created=true}}}
  }?:if(error==null)Text("Carregando animal…",color=PU.Muted)else Unit
 }
}

@Composable fun SupportExperience(store:SupportStore,onBack:()->Unit,onLogout:()->Unit,navigation:SupportNavigation?=null,onCatalog:()->Unit=onBack,onFinancialChanged:()->Unit={}){
 var id by rememberSaveable{mutableStateOf<Long?>(null)};var closing by rememberSaveable(id){mutableStateOf(false)};var confirmId by rememberSaveable(id){mutableStateOf<Long?>(null)};var reason by rememberSaveable(id){mutableStateOf("")}
 var animalFilter by rememberSaveable{mutableStateOf(store.animalId?.toString().orEmpty())};var filterError by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(store,id){snapshotFlow{store.busy}.first{!it};if(id==null)store.loadSponsors(store.sponsors?.page?:0)else store.reloadSponsor(id!!)}
 fun back(){if(!store.busy){if(id!=null){id=null;store.clearSelection()}else onBack()}}
 val selected=store.selectedSponsor?.takeIf{it.id==id}
 SupportShell(if(id!=null)if(store.internal)"Apoio a ${selected?.animal?.name.orEmpty()}"else selected?.animal?.name?:"Detalhes do apoio"else if(store.internal)"Apadrinhamentos"else"Meus apadrinhamentos",if(id!=null)"A confirmação de cada parcela é registrada manualmente pela ONG."else if(store.internal)"Acompanhe apoios e parcelas. Valores previstos não são recebimentos confirmados."else"Acompanhe seus apoios e as parcelas previstas ou confirmadas pela ONG.",::back,onLogout,internal=store.internal,personal=if(store.internal)null else navigation,supportsSelected=!store.internal,eyebrow=if(store.internal)null else if(id==null)"Minha área"else"Apadrinhamento",showBack=id!=null){
  SupportFeedback(store.busy,store.message){if(id==null)store.loadSponsors()else store.reloadSponsor(id!!)}
  if(id==null){
   if(!store.internal)SupportAction("Conhecer animais",store.busy,true,onCatalog)
   if(store.internal)SupportPanel{SupportSelect("Situação",store.sponsorStatus,SponsorStatus.entries,{it.label},store.busy){store.setSponsorFilters(it,store.animalId,store.mode)};if(store.internal){SupportSelect("Modalidade",store.mode,SponsorMode.entries,{it.label},store.busy){store.setSponsorFilters(store.sponsorStatus,store.animalId,it)};SupportInput("ID do animal",animalFilter,!store.busy){animalFilter=it};filterError?.let{Text(it,color=MaterialTheme.colorScheme.error)};SupportAction("Aplicar filtro",store.busy,true){val parsed=animalFilter.toLongOrNull();if(animalFilter.isNotBlank()&&(parsed==null||parsed<=0))filterError="Informe um ID de animal positivo."else{filterError=null;store.setSponsorFilters(store.sponsorStatus,parsed,store.mode)}}}}
   store.sponsors?.items?.forEach{r->SupportPanel(!store.internal){
    if(store.internal){SupportFact("Animal","${r.animal.name} · #${r.id}");SupportFact("Padrinho",r.sponsor?.name?:"—");SupportFact("Modalidade",r.mode.label);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Situação",color=PU.Muted,fontSize=12.sp,fontWeight=FontWeight.Bold);SupportStatus(r.status.label,closed=r.status==SponsorStatus.ENCERRADO,internal=store.internal)};SupportFact("Valor previsto",supportMoney(r.value))}
    else{SupportStatus(r.status.label,closed=r.status==SponsorStatus.ENCERRADO,internal=store.internal);Text("Apoio ${if(r.mode==SponsorMode.MENSAL)"mensal"else"único"} · #${r.id}",color=PU.Foliage,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(r.animal.name,fontSize=24.sp,fontWeight=FontWeight.Bold,color=PU.Green);Text("${r.ong.name} · iniciado em ${supportDate(r.startDate)}",fontSize=13.sp,color=PU.Muted);Text(supportMoney(r.value)+if(r.mode==SponsorMode.MENSAL)" / mês"else"",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,color=PU.Green);if(r.status==SponsorStatus.ENCERRADO || r.nextDueDate!=null)Text(if(r.status==SponsorStatus.ATIVO)"Próxima parcela prevista: ${supportDate(r.nextDueDate)}."else"Encerrado em ${supportDate(r.endDate)}. O histórico das parcelas permanece disponível.",color=PU.Muted,fontSize=14.sp,lineHeight=23.sp)}
    TextButton(onClick={if(!store.busy){store.clearSelection();id=r.id}},enabled=!store.busy){Text(if(store.internal)"Ver parcelas"else"Ver parcelas e detalhes",fontWeight=FontWeight.Bold)}
   }}
   if(store.sponsors?.items?.isEmpty()==true)SupportEmpty("Nenhum apadrinhamento neste filtro","Conheça os animais ou altere os filtros para consultar o histórico.")
   SupportPager(store.sponsors,store.busy,store::loadSponsors)
  }else selected?.let{r->
   SupportPanel(!store.internal){SupportStatus(r.status.label,closed=r.status==SponsorStatus.ENCERRADO,internal=store.internal);Text("Apadrinhamento #${r.id}",fontSize=22.sp,fontWeight=FontWeight.Bold,color=PU.Green);SupportFact("Animal",r.animal.name);SupportFact("ONG",r.ong.name);if(store.internal)SupportFact("Padrinho",r.sponsor?.name?:"—");SupportFact("Modalidade",r.mode.label);SupportFact("Valor previsto por parcela",supportMoney(r.value));SupportFact("Início",supportDate(r.startDate));if(r.status==SponsorStatus.ATIVO && r.nextDueDate!=null)SupportFact("Próximo vencimento",supportDate(r.nextDueDate));if(r.status==SponsorStatus.ENCERRADO){SupportFact("Encerramento",supportDate(r.endDate));SupportFact("Motivo",r.closureReason?.label?:"—");r.closureJustification?.let{SupportFact("Justificativa",it)}}
    if(r.status==SponsorStatus.ATIVO&&(!store.internal||store.canWrite)){HorizontalDivider(color=PU.Border);Text("O encerramento preserva parcelas e recebimentos anteriores. Nenhum pagamento será revertido.",color=PU.Muted,lineHeight=23.sp);if(closing){if(store.internal)SupportInput("Justificativa do encerramento",reason,!store.busy,multiline=true){reason=it};SupportNotice(store.message);SupportAction(if(store.busy)"Encerrando…"else if(store.internal)"Confirmar encerramento"else"Encerrar apadrinhamento",store.busy){if(store.internal)store.closeSponsor(reason){closing=false}else store.cancelSponsor{closing=false}};TextButton(onClick={closing=false},enabled=!store.busy){Text("Manter registro")}}else SupportAction(if(store.internal)"Encerrar apadrinhamento"else"Encerrar apadrinhamento",store.busy,true){closing=true}}
   }
   Text("Parcelas",fontSize=24.sp,fontWeight=FontWeight.Bold,color=PU.Green);Text("Uma parcela pendente é uma previsão, não uma cobrança automática.",color=PU.Muted,fontSize=14.sp)
   InstallmentFilters(store)
   store.installments?.items?.forEach{p->SupportPanel(!store.internal){SupportFact("Parcela","#${p.number}");SupportFact("Vencimento",supportDate(p.dueDate));SupportFact("Valor",supportMoney(p.value));SupportStatus(p.status.label,p.status==InstallmentStatus.PENDENTE,p.status==InstallmentStatus.CANCELADA,internal=store.internal);if(p.overdue)Text("Vencida, sem cobrança automática",color=PU.Muted,fontSize=12.sp);p.confirmedAt?.let{SupportFact("Confirmação",supportDate(it))};if(store.internal)p.confirmedBy?.let{SupportFact("Confirmado por",it.name)}
    if(store.canWrite&&p.status==InstallmentStatus.PENDENTE){if(confirmId==p.id){Text("Confirmar recebimento de ${supportMoney(p.value)}? A receita será registrada uma única vez pelo servidor.",color=PU.Muted,lineHeight=23.sp);SupportNotice(store.message);SupportAction("Confirmar recebimento",store.busy){store.confirmInstallment(p.id){confirmId=null;onFinancialChanged()}};TextButton(onClick={confirmId=null},enabled=!store.busy){Text("Voltar")}}else SupportAction("Confirmar recebimento",store.busy,true){confirmId=p.id}}
   }}
   if(store.installments?.items?.isEmpty()==true)SupportEmpty("Nenhuma parcela neste filtro","O histórico permanece disponível ao remover o filtro.")
   SupportPager(store.installments,store.busy,store::loadInstallments)
  }
 }
}
@Composable private fun InstallmentFilters(store:SupportStore){var de by rememberSaveable{mutableStateOf(store.installmentDe)};var ate by rememberSaveable{mutableStateOf(store.installmentAte)};SupportPanel{SupportSelect("Situação",store.installmentStatus,InstallmentStatus.entries,{it.label},store.busy){store.setInstallmentFilters(it,store.overdue,de,ate)};if(store.internal){SupportSelect("Vencidas",store.overdue,listOf(true,false),{if(it)"Somente vencidas"else"Não vencidas"},store.busy){store.setInstallmentFilters(store.installmentStatus,it,de,ate)};SupportInput("De (AAAA-MM-DD)",de,!store.busy){de=it};SupportInput("Até (AAAA-MM-DD)",ate,!store.busy){ate=it};SupportAction("Filtrar parcelas",store.busy,true){store.setInstallmentFilters(store.installmentStatus,store.overdue,de,ate)}}}}
