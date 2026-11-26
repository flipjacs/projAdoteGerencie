package br.com.patasunidas.apadrinhamentos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.patasunidas.ui.*
import kotlinx.coroutines.flow.first

@Composable fun SupportReportScreen(store:SupportStore,onBack:()->Unit,onLogout:()->Unit){
 var de by rememberSaveable{mutableStateOf(store.reportDe)};var ate by rememberSaveable{mutableStateOf(store.reportAte)}
 LaunchedEffect(store){snapshotFlow{store.busy}.first{!it};store.loadReport()}
 SupportShell("Relatório financeiro","Totais oficiais da ONG. Valores realizados e pendências seguem o período informado.",onBack,onLogout,internal=true){if(!store.canWrite){Text("Você não tem permissão para consultar este relatório.");return@SupportShell};SupportPanel{SupportInput("De (AAAA-MM-DD)",de,!store.busy){de=it};SupportInput("Até (AAAA-MM-DD)",ate,!store.busy){ate=it};SupportAction("Aplicar período",store.busy,true){store.loadReport(de,ate)}};SupportFeedback(store.busy,store.message){store.loadReport(de,ate)};store.report?.let{r->SupportPanel{SupportMetric("Receitas realizadas",supportMoney(r.receitasRealizadas));SupportMetric("Despesas realizadas",supportMoney(r.despesasRealizadas));SupportMetric("Receitas pendentes",supportMoney(r.receitasPendentes));SupportMetric("Despesas pendentes",supportMoney(r.despesasPendentes));SupportMetric("Despesas aprovadas",supportMoney(r.despesasAprovadas));SupportMetric("Saldo realizado",supportMoney(r.saldoRealizado));SupportMetric("Quantidade de movimentações",r.quantidadeMovimentacoes.toString());Text(r.periodSemantics,color=PU.Muted,lineHeight=23.sp);SupportCategories("Receitas por categoria",r.receitasPorCategoria);SupportCategories("Despesas por categoria",r.despesasPorCategoria)}}}
}
@Composable fun TransparencyExperience(store:SupportStore,onBack:()->Unit,onLogout:()->Unit){
 var id by rememberSaveable{mutableStateOf<Long?>(null)};var creating by rememberSaveable{mutableStateOf(false)};var confirming by rememberSaveable(id){mutableStateOf(false)}
 var de by rememberSaveable{mutableStateOf(store.demonstrativeDe)};var ate by rememberSaveable{mutableStateOf(store.demonstrativeAte)}
 LaunchedEffect(store,id,creating){if(!creating){snapshotFlow{store.busy}.first{!it};if(id==null)store.loadDemonstratives(store.demonstratives?.page?:0)else store.detailsDemonstrative(id!!)}}
 fun back(){if(!store.busy){when{creating->creating=false;id!=null->{id=null;store.clearSelection()};else->onBack()}}}
 val r=store.selectedDemonstrative?.takeIf{it.id==id}
 SupportShell(if(creating)"Criar demonstrativo"else r?.title?:if(id!=null)"Demonstrativo"else"Transparência",if(creating)"O rascunho usa dados consolidados do período. A publicação atualiza o retrato financeiro antes de torná-lo público."else if(id==null)"Prepare e publique demonstrativos consolidados da sua ONG."else"${supportDate(r?.startDate)} a ${supportDate(r?.endDate)}",::back,onLogout,internal=true,showBack=creating||id!=null){
  SupportFeedback(store.busy,store.message){if(id==null)store.loadDemonstratives()else store.detailsDemonstrative(id!!)}
  when{
   creating&&store.canWrite->SupportPanel{DemonstrativeEditor(store,null){creating=false;id=store.selectedDemonstrative?.id}}
   id==null->{if(store.canWrite)SupportAction("Criar demonstrativo",store.busy){creating=true};SupportPanel{SupportSelect("Situação",store.demonstrativeStatus,DemonstrativeStatus.entries,{it.label},store.busy){store.setDemonstrativeFilters(it,de,ate)};SupportInput("De (AAAA-MM-DD)",de,!store.busy){de=it};SupportInput("Até (AAAA-MM-DD)",ate,!store.busy){ate=it};SupportAction("Filtrar demonstrativos",store.busy,true){store.setDemonstrativeFilters(store.demonstrativeStatus,de,ate)}};store.demonstratives?.items?.forEach{item->SupportPanel{SupportFact("Título",item.title);SupportFact("Período","${supportDate(item.startDate)} a ${supportDate(item.endDate)}");SupportStatus(item.status.label,pending=item.status==DemonstrativeStatus.RASCUNHO);SupportFact("Publicação",supportDate(item.publishedAt));SupportAction("Ver demonstrativo",store.busy,true){store.clearSelection();id=item.id}}};if(store.demonstratives?.items?.isEmpty()==true)SupportEmpty("Nenhum demonstrativo neste filtro","Altere os filtros para consultar outro período.");SupportPager(store.demonstratives,store.busy,store::loadDemonstratives)}
   r!=null->{SupportPanel{SupportStatus(r.status.label,pending=r.status==DemonstrativeStatus.RASCUNHO);r.description?.let{Text(it,color=PU.Ink,lineHeight=25.sp)};SupportFact("Publicado em",supportDate(r.publishedAt));r.unpublishedAt?.let{SupportFact("Despublicado em",supportDate(it))};Text("Retrato consolidado do período. Os valores são calculados pelo servidor.",color=PU.Muted,fontSize=13.sp,lineHeight=22.sp);SupportSnapshot(r.data)}
    if(store.canWrite){val published=r.status==DemonstrativeStatus.PUBLICADO;SupportPanel{Text(if(published)"Despublicar demonstrativo"else"Revisar e publicar",fontSize=24.sp,fontWeight=FontWeight.Bold,color=PU.Green);Text(if(published)"O demonstrativo deixará de aparecer na consulta pública. O histórico será preservado."else"Ao publicar, o servidor atualiza o retrato financeiro do período e o torna público.",color=PU.Muted,lineHeight=23.sp);if(confirming){Text(if(published)"Confirma a despublicação?"else"Confirma a publicação?",color=PU.Ink);SupportNotice(store.message);SupportAction(if(published)"Confirmar despublicação"else"Confirmar publicação",store.busy){if(published)store.unpublishDemonstrative{confirming=false}else store.publishDemonstrative{confirming=false}};TextButton(onClick={confirming=false},enabled=!store.busy){Text("Voltar")}}else SupportAction(if(published)"Despublicar"else"Publicar demonstrativo",store.busy){confirming=true}}
     if(!published)SupportPanel{Text("Editar rascunho",fontSize=24.sp,fontWeight=FontWeight.Bold,color=PU.Green);DemonstrativeEditor(store,r){}}
    }
   }
  }
 }
}
@Composable private fun DemonstrativeEditor(store:SupportStore,initial:DemonstrativeRecord?,done:()->Unit){
 var title by rememberSaveable(initial?.id){mutableStateOf(initial?.title.orEmpty())};var description by rememberSaveable(initial?.id){mutableStateOf(initial?.description.orEmpty())};var start by rememberSaveable(initial?.id){mutableStateOf(initial?.startDate.orEmpty())};var end by rememberSaveable(initial?.id){mutableStateOf(initial?.endDate.orEmpty())};var error by remember{mutableStateOf<String?>(null)}
 SupportInput("Título",title,!store.busy){title=it};SupportInput("Descrição (opcional)",description,!store.busy,multiline=true){description=it};SupportInput("Início do período (AAAA-MM-DD)",start,!store.busy){start=it};SupportInput("Fim do período (AAAA-MM-DD)",end,!store.busy){end=it};error?.let{Text(it,color=MaterialTheme.colorScheme.error)};SupportNotice(store.message);SupportAction(if(store.busy)"Salvando…"else if(initial==null)"Criar rascunho"else"Salvar alterações",store.busy){val form=DemonstrativeForm(title,description,start,end);error=form.validate();if(error==null){if(initial==null)store.createDemonstrative(form,done)else store.editDemonstrative(form,done)}}
}

@Composable fun PublicTransparencyExperience(api:SupportApi,initialOngId:Int?,signedIn:Boolean,onBack:()->Unit,onAccount:()->Unit,onHome:()->Unit=onBack,onCatalog:()->Unit=onBack){
 var ongId by rememberSaveable{mutableStateOf(initialOngId)};var input by rememberSaveable{mutableStateOf(initialOngId?.toString().orEmpty())};var id by rememberSaveable{mutableStateOf<Long?>(null)};var error by remember{mutableStateOf<String?>(null)}
 val store=ongId?.let{remember(api,it){PublicSupportStore(api,it)}};DisposableEffect(store){onDispose{store?.close()}}
 LaunchedEffect(store,id){if(store!=null){snapshotFlow{store.busy}.first{!it};if(id==null)store.load()else store.details(id!!)}}
 fun back(){when{ id!=null->{id=null;store?.clearSelection()};ongId!=null->{ongId=null};else->onBack()}}
 val r=store?.selected
 SupportShell(if(id!=null)r?.title?:"Demonstrativo público"else store?.records?.items?.firstOrNull()?.ong?.name?:"Transparência",if(id!=null)"${r?.ong?.name.orEmpty()} · ${supportDate(r?.startDate)} a ${supportDate(r?.endDate)}"else if(ongId!=null)"Somente demonstrativos publicados por uma ONG ativa aparecem nesta página."else"Consulte os demonstrativos financeiros publicados pelas ONGs.",::back,onAccount,signedIn=signedIn,eyebrow=if(id!=null)"Demonstrativo público"else"Dados públicos",public=true,onPublicHome=onHome,onPublicCatalog=onCatalog){
  if(ongId==null){SupportPanel{Text("Consultar ONG",fontSize=22.sp,fontWeight=FontWeight.Bold,color=PU.Green);SupportInput("Identificador da ONG",input){input=it};error?.let{Text(it,color=MaterialTheme.colorScheme.error)};SupportAction("Consultar transparência"){val parsed=input.toIntOrNull();if(parsed==null||parsed<=0)error="Informe um identificador de ONG válido."else{error=null;ongId=parsed}}}}
  else if(store!=null){SupportFeedback(store.busy,store.message){if(id==null)store.load()else store.details(id!!)}
   if(id==null){store.records?.items?.forEach{item->Surface(color=PU.Paper,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,PU.Border)){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("${supportDate(item.startDate)} a ${supportDate(item.endDate)}",color=PU.Foliage,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(item.title,fontSize=20.sp,lineHeight=24.sp,letterSpacing=(-0.6).sp,fontWeight=FontWeight.Bold,color=PU.Green,textDecoration=TextDecoration.Underline);item.description?.let{Text(it,color=PU.Ink,fontSize=14.sp,lineHeight=21.sp)};Text("Resultado líquido: ${supportMoney(item.data.resultadoLiquido)}",color=PU.Ink,fontSize=14.sp,lineHeight=21.sp);TextButton(onClick={id=item.id},enabled=!store.busy,contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp)){Text("Consultar demonstrativo",fontSize=13.sp,fontWeight=FontWeight.Bold)}}}};if(store.records?.items?.isEmpty()==true)SupportEmpty("Nenhum demonstrativo publicado","Esta ONG ainda não possui demonstrativos públicos disponíveis.");SupportPager(store.records,store.busy,store::load)}
   else if(r!=null)SupportPanel{r.description?.let{Text(it,color=PU.Ink,lineHeight=25.sp)};Text("Publicado em ${supportDate(r.publishedAt)}. Os valores abaixo são o retrato consolidado do período no momento da publicação.",color=PU.Muted,fontSize=13.sp,lineHeight=22.sp);SupportSnapshot(r.data)}
  }
 }
}
