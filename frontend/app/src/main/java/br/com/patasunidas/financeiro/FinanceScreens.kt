package br.com.patasunidas.financeiro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.ui.*
import br.com.patasunidas.animais.*
import br.com.patasunidas.BuildConfig
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The institutional mobile tables become labelled rows, as in institutional.css.
private fun financeDate(value: String?): String = if(value == null) "Não registrado" else try {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
} catch (_: Exception) { value }

@Composable private fun FinancePanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color=PU.Paper, shape=RoundedCornerShape(13.dp), border=BorderStroke(1.dp,PU.Border)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp), content=content)
    }
}
@Composable private fun FinanceButton(text: String, enabled: Boolean = true, secondary: Boolean = false, onClick: () -> Unit) =
    WorkspaceAction(text, enabled, secondary, onClick)

@Composable private fun FinanceShell(title:String, description:String, internal:Boolean, onBack:()->Unit, onLogout:()->Unit, onCatalog:()->Unit=onBack, onHome:()->Unit=onBack, showBack:Boolean=true, content:@Composable ColumnScope.()->Unit) {
    BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize().background(PU.Cream).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(PU.Paper).padding(horizontal=20.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(internal) TextButton(onClick=onBack,modifier=Modifier.size(40.dp),contentPadding=PaddingValues(0.dp)){Text("←",fontSize=24.sp)}
            Box(Modifier.size(34.dp).background(PU.Orange,RoundedCornerShape(10.dp)).padding(7.dp)){PawIcon(PU.Green,Modifier.fillMaxSize())}
            Text("Patas Unidas",fontWeight=FontWeight.Bold,color=PU.Green,fontSize=14.sp,modifier=Modifier.weight(1f))
            OutlinedButton(onClick=onLogout){Text("Sair")}
        }
        if(!internal) Row(Modifier.fillMaxWidth().background(PU.Paper).padding(horizontal=20.dp),horizontalArrangement=Arrangement.SpaceEvenly){TextButton(onClick=onHome){Text("Início")};TextButton(onClick=onCatalog){Text("Animais")};Text("Doações",fontWeight=FontWeight.Bold,color=PU.Green,modifier=Modifier.padding(14.dp))}
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            if(showBack)TextButton(onClick=onBack,contentPadding=PaddingValues(0.dp)){Text("← Voltar")}
            if(!internal) Text("Minha área",fontSize=13.sp,color=PU.Foliage,fontWeight=FontWeight.Bold)
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {WorkspaceTitle(title);if(description.isNotBlank()) Text(description,color=PU.Muted,lineHeight=24.sp)}
            content()
        }
    }
}
@Composable private fun FinanceFeedback(store:FinanceStore) {
    if(store.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    store.message?.let { Text(it,color=PU.Green,lineHeight=23.sp,modifier=Modifier.fillMaxWidth().background(PU.CardGreen,RoundedCornerShape(10.dp)).padding(16.dp)) }
}
@Composable private fun FinanceStatus(status: String, label: String, personal: Boolean = false) {
    val tone = when (status) {
        "PENDENTE" -> if (personal) StatusTone.PersonalPending else StatusTone.Pending
        "REALIZADA", "CONFIRMADA" -> if (personal) StatusTone.PersonalSuccess else StatusTone.Success
        "APROVADA" -> StatusTone.Reserved
        else -> if (personal) StatusTone.PersonalMuted else StatusTone.Closed
    }
    WorkspaceStatus(label, tone)
}
@Composable private fun FinanceFact(label:String,value:String) {Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Text(label,color=PU.Muted,fontWeight=FontWeight.Bold,fontSize=12.sp);Text(value,color=PU.Green,lineHeight=23.sp)}}
@Composable private fun FinanceRow(label:String,content:@Composable ()->Unit) {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(label,fontSize=12.sp,color=PU.Muted,fontWeight=FontWeight.Bold,modifier=Modifier.weight(.9f));Box(Modifier.weight(1.4f),contentAlignment=Alignment.CenterEnd){content()}}}
@Composable private fun FinanceRow(label:String,value:String) {FinanceRow(label){Text(value,color=PU.Ink,fontSize=13.sp,textAlign=TextAlign.End)}}
@Composable private fun <T> FinanceSelect(label:String,value:T?,options:List<T>,optionLabel:(T)->String,enabled:Boolean=true,all:String?=null,onChange:(T?)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,fontSize=13.sp,color=PU.Ink,fontWeight=FontWeight.Bold)
        Box {
            OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(8.dp),border=BorderStroke(1.dp,PU.Border)) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(value?.let(optionLabel)?:all.orEmpty(),modifier=Modifier.weight(1f),color=PU.Ink);Text("⌄")}}
            DropdownMenu(expanded,onDismissRequest={expanded=false}) {
                if(all!=null) DropdownMenuItem(text={Text(all)},onClick={expanded=false;onChange(null)})
                options.forEach{option->DropdownMenuItem(text={Text(optionLabel(option))},onClick={expanded=false;onChange(option)})}
            }
        }
    }
}
@Composable private fun FinanceInput(label:String,value:String,onChange:(String)->Unit,enabled:Boolean=true,decimal:Boolean=false,lines:Int=1,max:Int=255,help:String?=null,error:Boolean=false) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,color=PU.Ink,fontWeight=FontWeight.Bold,fontSize=14.sp)
        OutlinedTextField(value=value,onValueChange={if(it.length<=max)onChange(it)},enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp),singleLine=lines==1,minLines=lines,isError=error,keyboardOptions=KeyboardOptions(keyboardType=if(decimal)KeyboardType.Decimal else KeyboardType.Text),prefix=if(decimal)({Text("R$",color=PU.Green,fontWeight=FontWeight.Bold)})else null,placeholder=if(decimal)({Text("0,00",color=PU.Muted)})else null)
        help?.let{Text(it,fontSize=12.sp,color=if(error)MaterialTheme.colorScheme.error else PU.Muted,lineHeight=19.sp)}
    }
}
@Composable private fun FinancePager(page:FinancePage<*>?,busy:Boolean,onPage:(Int)->Unit) {
    if(page!=null&&page.pages>1) {Text("${page.total} registros · Página ${page.page+1} de ${page.pages}",fontSize=13.sp,color=PU.Muted);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton(onClick={onPage(page.page-1)},enabled=!busy&&page.page>0){Text("Anterior")};OutlinedButton(onClick={onPage(page.page+1)},enabled=!busy&&page.page+1<page.pages){Text("Próxima")}}}
}
@Composable private fun FinanceHistory(history:List<FinanceHistory>) {
    HorizontalDivider(color=PU.Border)
    Text("Histórico",fontSize=19.sp,fontWeight=FontWeight.Bold,color=PU.Green)
    history.forEach { event ->
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(when(event.operation){"REGISTRADA"->"Registro criado";"DESPESA_APROVADA"->"Despesa aprovada";"PAGAMENTO_CONFIRMADO"->"Pagamento confirmado";"RECEBIMENTO_APADRINHAMENTO"->"Contribuição de apadrinhamento recebida";"RECEBIMENTO_CONFIRMADO","RECEBIMENTO_DOACAO"->"Recebimento confirmado";"CANCELADA"->"Registro cancelado";else->event.operation},fontWeight=FontWeight.Bold,color=PU.Green,fontSize=14.sp)
            Text(financeDate(event.at)+event.actor?.let{" · ${it.name}"}.orEmpty(),fontSize=12.sp,color=PU.Muted)
            Text(listOfNotNull(event.previous,event.next).joinToString(" → "),fontSize=12.sp,color=PU.Muted)
            event.reason?.let{Text(it,color=PU.Ink,lineHeight=22.sp)}
        }
    }
}
private fun donationNote(item:DonationRecord)=when(item.status){DonationStatus.PENDENTE->"Aguardando confirmação manual da ONG. O registro não confirma um pagamento.";DonationStatus.CONFIRMADA->"Recebimento confirmado em ${financeDate(item.confirmedAt)}.";DonationStatus.CANCELADA->"Registro cancelado em ${financeDate(item.cancelledAt)}."}
private fun actionTitle(action:FinanceAction)=when(action){FinanceAction.APPROVE->"Aprovar despesa";FinanceAction.RECEIVE,FinanceAction.CONFIRM_DONATION->"Confirmar recebimento";FinanceAction.PAY->"Confirmar pagamento";FinanceAction.CANCEL->"Cancelar registro"}
@Composable private fun FinanceDecision(action:FinanceAction,busy:Boolean,donation:Boolean=false,onConfirm:(String)->Unit,onDismiss:()->Unit) {
    var reason by rememberSaveable(action){mutableStateOf("")}
    HorizontalDivider(color=PU.Border)
    Text(actionTitle(action)+"?",fontSize=19.sp,color=PU.Green,fontWeight=FontWeight.Bold)
    Text(when(action){FinanceAction.APPROVE->"A aprovação autoriza a despesa. O pagamento ainda precisará ser confirmado.";FinanceAction.RECEIVE->"Confirme somente se a ONG já recebeu este valor. Ele passará a compor as receitas recebidas.";FinanceAction.CONFIRM_DONATION->"Confirme somente se a ONG já recebeu a doação. O servidor registrará uma única receita realizada.";FinanceAction.PAY->"Confirme somente se a despesa já foi paga. Ela passará a compor as despesas pagas.";FinanceAction.CANCEL->if(donation)"Esta intenção de doação será cancelada e continuará no histórico. Nenhum pagamento será revertido."else"Esta pendência será cancelada e continuará preservada no histórico."},color=PU.Muted,lineHeight=24.sp)
    if(action==FinanceAction.CANCEL&&!donation)FinanceInput("Justificativa (opcional)",reason,{reason=it},enabled=!busy,lines=3,max=1000,help="${reason.length}/1000")
    FinanceButton(if(busy)"Atualizando…"else if(action==FinanceAction.CANCEL&&donation)"Cancelar doação"else actionTitle(action),enabled=!busy){onConfirm(reason)}
    TextButton(onClick=onDismiss,enabled=!busy){Text(if(action==FinanceAction.CANCEL)"Manter registro"else"Voltar às ações")}
}

@Composable fun FinancialExperience(store:FinanceStore,onBack:()->Unit,onLogout:()->Unit,onCatalog:()->Unit=onBack,onHome:()->Unit=onBack,onSupports:(()->Unit)?=null,onReport:(()->Unit)?=null,onTransparency:(()->Unit)?=null) {
    var view by rememberSaveable {mutableStateOf(if(store.internal)"movements"else"donations")}
    var detailId by rememberSaveable {mutableStateOf<Long?>(null)}
    var action by rememberSaveable(view,detailId){mutableStateOf<FinanceAction?>(null)}
    fun back(){if(store.busy)return;if(detailId!=null){detailId=null;store.clearSelection();action=null}else if(view=="create")view="movements"else onBack()}
    LaunchedEffect(view,detailId,store){
        if(view=="movements"){if(detailId!=null)store.detailsMovement(detailId!!)else{store.loadMovements()}}
        else if(view=="donations"){if(detailId!=null)store.detailsDonation(detailId!!)else store.loadDonations()}
    }
    val title=when{view=="create"->"Novo lançamento";detailId!=null&&view=="movements"->"Lançamento #$detailId";detailId!=null->"Doação #$detailId";view=="movements"->"Financeiro";store.internal->"Doações recebidas";else->"Minhas doações"}
    val description=when{view=="create"->"O lançamento começa pendente. Recebimentos e pagamentos exigem confirmação posterior.";detailId!=null->"";view=="movements"->"Valores realizados e pendências da sua ONG. O resultado realizado não representa saldo bancário.";store.internal->"Acompanhe doações registradas. Somente valores confirmados geram receita realizada.";else->"Contribuições registradas e seus estados de confirmação pela ONG."}
    FinanceShell(title,description,store.internal,::back,onLogout,onCatalog,onHome,showBack=detailId!=null||view=="create") {
        if(store.internal && detailId==null && view!="create") {
            onSupports?.let{FinanceButton("Apadrinhamentos",store.busy.not(),true,it)}
            onReport?.let{FinanceButton("Relatório financeiro",store.busy.not(),true,it)}
            onTransparency?.let{FinanceButton("Transparência",store.busy.not(),true,it)}
        }
        FinanceFeedback(store)
        if(detailId==null&&view=="donations"&&store.internal) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            FilterChip(colors=FilterChipDefaults.filterChipColors(selectedContainerColor=PU.CardGreen,selectedLabelColor=PU.Green),selected=view=="movements",onClick={if(!store.busy)view="movements"},label={Text("Movimentações")})
            FilterChip(colors=FilterChipDefaults.filterChipColors(selectedContainerColor=PU.CardGreen,selectedLabelColor=PU.Green),selected=view=="donations",onClick={if(!store.busy)view="donations"},label={Text("Doações")})
        }
        if(store.busy && ((view=="movements" && detailId==null && store.movements==null) || (view=="donations" && detailId==null && store.donations==null) || (view=="movements" && detailId!=null && store.selectedMovement==null) || (view=="donations" && detailId!=null && store.selectedDonation==null))) {
            FinancePanel {
                Text("Carregando dados financeiros…",color=PU.Muted,fontSize=13.sp)
                listOf(1f,.74f,.48f).forEach{fraction->Box(Modifier.fillMaxWidth(fraction).height(38.dp).background(Color(0xFFE9EEE5),RoundedCornerShape(8.dp)))}
            }
        }
        when {
            view=="create"->MovementForm(store){view="movements"}
            detailId!=null&&view=="movements"->{
                val item=store.selectedMovement?.takeIf{it.id==detailId}
                if(item!=null){
                    FinancePanel {
                        Text(item.description,fontWeight=FontWeight.Bold,fontSize=22.sp,color=PU.Green)
                        FinanceStatus(item.status.name,item.status.label)
                        FinanceFact("Valor",formatMoney(item.value));FinanceFact("Tipo e categoria","${item.type.label} · ${item.category.label}")
                        FinanceFact("Origem",when(item.origin){"MANUAL"->"Lançamento manual";"APADRINHAMENTO"->"Apadrinhamento";else->"Doação #${item.donationId}"})
                        FinanceFact("Cadastro",financeDate(item.created)+item.registeredBy?.let{" · ${it.name}"}.orEmpty())
                        FinanceFact("Aprovação",financeDate(item.approvedAt)+item.approvedBy?.let{" · ${it.name}"}.orEmpty())
                        FinanceFact("Confirmação",financeDate(item.confirmedAt)+item.confirmedBy?.let{" · ${it.name}"}.orEmpty())
                        FinanceFact("Cancelamento",financeDate(item.cancelledAt)+item.cancelledBy?.let{" · ${it.name}"}.orEmpty())
                        item.cancellation?.let{FinanceFact("Justificativa do cancelamento",it)}
                        FinanceHistory(item.history)
                    }
                    FinancePanel {
                        Text("Próximo passo",fontSize=22.sp,fontWeight=FontWeight.Bold,color=PU.Green)
                        Text(when(item.status){MovementStatus.REALIZADA->"Valor realizado e preservado no histórico.";MovementStatus.CANCELADA->"Lançamento cancelado e preservado no histórico.";MovementStatus.APROVADA->"A aprovação autorizou a despesa; o pagamento ainda precisa ser confirmado.";else->"Este valor ainda não compõe o resultado realizado."},color=PU.Muted,lineHeight=24.sp)
                        val available=movementActions(item,store.profile)
                        val current=action?.takeIf{it in available}
                        if(current!=null)FinanceDecision(current,store.busy,onConfirm={reason->store.actMovement(current,reason){action=null}},onDismiss={action=null})
                        else available.forEach{a->FinanceButton(actionTitle(a),!store.busy,a==FinanceAction.CANCEL){action=a}}
                    }
                }
                FinanceButton("Atualizar dados",!store.busy,true){store.detailsMovement(detailId!!)}
            }
            detailId!=null&&view=="donations"->{
                val item=store.selectedDonation?.takeIf{it.id==detailId}
                if(item!=null){
                    FinancePanel {
                        Text(formatMoney(item.value),fontSize=26.sp,fontWeight=FontWeight.ExtraBold,color=PU.Green);FinanceStatus(item.status.name,item.status.label,!store.internal)
                        FinanceFact("ONG",item.ongName);item.donor?.let{FinanceFact("Doador",it.name)}
                        FinanceFact("Mensagem",item.message?:"Nenhuma mensagem.");FinanceFact("Cadastro",financeDate(item.created))
                        FinanceFact("Recebimento confirmado",financeDate(item.confirmedAt)+item.confirmedBy?.let{" · ${it.name}"}.orEmpty())
                        FinanceFact("Cancelamento",financeDate(item.cancelledAt));item.cancellation?.let{FinanceFact("Justificativa do cancelamento",it)}
                        FinanceHistory(item.history)
                    }
                    FinancePanel {
                        Text("Recebimento",fontSize=22.sp,fontWeight=FontWeight.Bold,color=PU.Green);Text(donationNote(item),color=PU.Muted,lineHeight=24.sp)
                        val available=donationActions(item,store.profile)
                        val current=action?.takeIf{it in available}
                        if(current!=null)FinanceDecision(current,store.busy,true,{reason->store.actDonation(current,reason){action=null}},{action=null})
                        else available.forEach{a->FinanceButton(if(a==FinanceAction.CANCEL)"Cancelar doação"else actionTitle(a),!store.busy,a==FinanceAction.CANCEL){action=a}}
                    }
                }
                FinanceButton("Atualizar dados",!store.busy,true){store.detailsDonation(detailId!!)}
            }
            view=="movements"->{
                FinanceButton("Novo lançamento",!store.busy){view="create"}
                store.summary?.let{FinanceSummaryCards(it)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){FilterChip(colors=FilterChipDefaults.filterChipColors(selectedContainerColor=PU.CardGreen,selectedLabelColor=PU.Green),selected=true,onClick={},label={Text("Movimentações")});FilterChip(colors=FilterChipDefaults.filterChipColors(selectedContainerColor=PU.CardGreen,selectedLabelColor=PU.Green),selected=false,onClick={if(!store.busy)view="donations"},label={Text("Doações")})}
                Text("O período do histórico considera o cadastro. No resumo, valores realizados usam a confirmação; pendências usam o cadastro e despesas aprovadas usam a aprovação.",fontSize=12.sp,color=PU.Muted,lineHeight=20.sp)
                MovementFilters(store)
                val page=store.movements
                if(!store.busy&&page?.items?.isEmpty()==true)FinancePanel{Text("Nenhuma movimentação neste filtro",fontWeight=FontWeight.Bold,fontSize=20.sp,color=PU.Green);Text("Cadastre uma pendência ou ajuste os filtros para consultar o histórico.",color=PU.Muted)}
                page?.items?.forEach { item ->FinancePanel {
                    FinanceRow("Movimentação"){Column(horizontalAlignment=Alignment.End){Text(item.description,fontWeight=FontWeight.Bold,textAlign=TextAlign.End,color=PU.Ink);Text("${item.category.label} · ${when(item.origin){"MANUAL"->"Manual";"APADRINHAMENTO"->"Apadrinhamento";else->"Doação"}}",fontSize=12.sp,color=PU.Muted,textAlign=TextAlign.End)}}
                    FinanceRow("Tipo",item.type.label);FinanceRow("Situação"){FinanceStatus(item.status.name,item.status.label)}
                    FinanceRow("Valor"){Text(formatMoney(item.value),color=PU.Green,fontWeight=FontWeight.ExtraBold)}
                    FinanceRow("Cadastro",financeDate(item.created));FinanceRow("Ação"){TextButton(onClick={detailId=item.id},enabled=!store.busy){Text("Ver detalhes",fontWeight=FontWeight.Bold)}}
                }}
                FinancePager(page,store.busy){store.loadMovements(it)}
                if(store.profile.name=="VOLUNTARIO")Text("Você pode registrar pendências. Aprovações e confirmações são realizadas pelo administrador.",fontSize=13.sp,color=PU.Muted,lineHeight=22.sp)
                FinanceButton("Atualizar dados",!store.busy,true){store.loadMovements(page?.page?:0)}
            }
            else->DonationListContent(store,onCatalog,onDetails={detailId=it})
        }
    }
}

@Composable private fun FinanceSummaryCards(summary:FinanceSummary) {
    val values=listOf("Receitas recebidas" to summary.receitasRealizadas,"Despesas pagas" to summary.despesasRealizadas,"Resultado realizado" to summary.saldoRealizado,"Receitas pendentes" to summary.receitasPendentes,"Despesas pendentes" to summary.despesasPendentes,"Despesas aprovadas, não pagas" to summary.despesasAprovadas)
    BoxWithConstraints {
        val columns=if(maxWidth<310.dp)1 else 2
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            values.chunked(columns).forEach { row ->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                row.forEach{(label,value)->
                    val result=label=="Resultado realizado"
                    Surface(color=if(result)PU.Green else PU.Paper,shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,if(result)PU.Green else PU.Border),modifier=Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth().heightIn(min=90.dp).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(label,color=if(result)Color.White else PU.Muted,fontSize=12.sp,fontWeight=FontWeight.Bold,lineHeight=18.sp)
                            Text(formatMoney(value),color=if(result)Color.White else PU.Green,fontWeight=FontWeight.ExtraBold,fontSize=21.sp,lineHeight=28.sp)
                        }
                    }
                }
            }}
        }
    }
}
@Composable private fun MovementFilters(store:FinanceStore) {
    var type by rememberSaveable{mutableStateOf(store.movementType)}
    var status by rememberSaveable{mutableStateOf(store.movementStatus)}
    var category by rememberSaveable{mutableStateOf(store.movementCategory)}
    var de by rememberSaveable{mutableStateOf(store.de)}
    var ate by rememberSaveable{mutableStateOf(store.ate)}
    // Values remain separate from the official summary, whose only filter is the period.
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FinanceSelect("Tipo",type,MovementType.entries.toList(),{it.label},!store.busy,"Todos"){type=it;category=null}
        FinanceSelect("Categoria",category,FinanceCategory.entries.filter{c->type==null||if(type==MovementType.RECEITA)c in listOf(FinanceCategory.EVENTO,FinanceCategory.OUTRA_RECEITA,FinanceCategory.DOACAO,FinanceCategory.APADRINHAMENTO)else c !in listOf(FinanceCategory.EVENTO,FinanceCategory.OUTRA_RECEITA,FinanceCategory.DOACAO,FinanceCategory.APADRINHAMENTO)},{it.label},!store.busy,"Todas"){category=it}
        FinanceSelect("Situação",status,MovementStatus.entries.toList(),{it.label},!store.busy,"Todas"){status=it}
        FinanceDates(de,ate,{de=it},{ate=it},!store.busy)
        FinanceButton("Aplicar filtros",!store.busy,true){store.setMovementFilters(type,status,category,de,ate)}
        if(type!=null||status!=null||category!=null||de.isNotEmpty()||ate.isNotEmpty())TextButton(onClick={type=null;status=null;category=null;de="";ate="";store.setMovementFilters(null,null,null)},enabled=!store.busy){Text("Limpar filtros")}
    }
}
@Composable private fun FinanceDates(de:String,ate:String,onDe:(String)->Unit,onAte:(String)->Unit,enabled:Boolean) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)){FinanceInput("De",de,onDe,enabled,max=10,help="AAAA-MM-DD")}
        Column(Modifier.weight(1f)){FinanceInput("Até",ate,onAte,enabled,max=10,help="AAAA-MM-DD")}
    }
}
@Composable private fun DonationFilters(store:FinanceStore) {
    var status by rememberSaveable{mutableStateOf(store.donationStatus)}
    var de by rememberSaveable{mutableStateOf(store.de)}
    var ate by rememberSaveable{mutableStateOf(store.ate)}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FinanceSelect("Situação",status,DonationStatus.entries.toList(),{it.label},!store.busy,"Todas"){status=it}
        FinanceDates(de,ate,{de=it},{ate=it},!store.busy)
        FinanceButton("Aplicar filtros",!store.busy,true){store.setDonationFilters(status,de,ate)}
        if(status!=null||de.isNotEmpty()||ate.isNotEmpty())TextButton(onClick={status=null;de="";ate="";store.setDonationFilters(null)},enabled=!store.busy){Text("Limpar filtros")}
    }
}
@Composable private fun DonationListContent(store:FinanceStore,onCatalog:()->Unit,onDetails:(Long)->Unit) {
    var cancelling by rememberSaveable{mutableStateOf<Long?>(null)}
    var expanded by rememberSaveable{mutableStateOf<Long?>(null)}
    if(store.internal)DonationFilters(store)else FinanceButton("Ver animais e ONGs",!store.busy,true,onCatalog)
    val page=store.donations
    if(!store.busy&&page?.items?.isEmpty()==true)FinancePanel {
        Text(if(store.internal)"Nenhuma doação neste filtro"else"Nenhuma doação registrada",fontSize=20.sp,fontWeight=FontWeight.Bold,color=PU.Green)
        Text(if(store.internal)"Altere os filtros para consultar o histórico."else"Você pode escolher uma ONG a partir do detalhe de um animal no catálogo.",color=PU.Muted,lineHeight=24.sp)
        if(!store.internal)FinanceButton("Explorar catálogo",onClick=onCatalog)
    }
    page?.items?.forEach { listed ->
        val item=store.selectedDonation?.takeIf{it.id==listed.id}?:listed
        FinancePanel {
            if(store.internal) {
                FinanceRow("Doador",item.donor?.name?:"Não identificado")
                FinanceRow("Situação"){FinanceStatus(item.status.name,item.status.label)}
                FinanceRow("Valor"){Text(formatMoney(item.value),color=PU.Green,fontWeight=FontWeight.ExtraBold)}
                FinanceRow("Cadastro",financeDate(item.created))
                FinanceRow("Ação"){TextButton(onClick={onDetails(item.id)},enabled=!store.busy){Text("Ver doação",fontWeight=FontWeight.Bold)}}
            } else {
                FinanceStatus(item.status.name,item.status.label,true)
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("Doação #${item.id}",color=PU.Foliage,fontWeight=FontWeight.Bold,fontSize=12.sp)
                    Text(item.ongName,color=PU.Green,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    Text("Registrada em ${financeDate(item.created)}",color=PU.Muted,fontSize=13.sp)
                }
                Text(formatMoney(item.value),fontSize=26.sp,fontWeight=FontWeight.ExtraBold,color=PU.Green)
                Text(donationNote(item),color=PU.Muted,lineHeight=24.sp,fontSize=14.sp)
                item.cancellation?.let{FinanceFact("Motivo registrado",it)}
                TextButton(onClick={expanded=if(expanded==item.id)null else item.id;if(expanded!=null)store.detailsDonation(item.id)},enabled=!store.busy,contentPadding=PaddingValues(0.dp)){Text(if(expanded==item.id)"Ocultar detalhes"else"Ver detalhes e histórico",fontWeight=FontWeight.Bold)}
                if(expanded==item.id){item.message?.let{FinanceFact("Minha mensagem",it)};FinanceHistory(item.history)}
                if(FinanceAction.CANCEL in donationActions(item,store.profile)) {
                    if(cancelling==item.id) {
                        FinanceDecision(FinanceAction.CANCEL,store.busy||store.selectedDonation?.id!=item.id,true,{reason->store.actDonation(FinanceAction.CANCEL,reason){cancelling=null}},{cancelling=null})
                    } else TextButton(onClick={cancelling=item.id;store.detailsDonation(item.id)},enabled=!store.busy,contentPadding=PaddingValues(0.dp)){Text("Cancelar doação",fontWeight=FontWeight.Bold)}
                }
            }
        }
    }
    FinancePager(page,store.busy){store.loadDonations(it)}
    FinanceButton("Atualizar doações",!store.busy,true){store.loadDonations(page?.page?:0)}
}

@Composable private fun MovementForm(store:FinanceStore,onDone:()->Unit) {
    var type by rememberSaveable{mutableStateOf(MovementType.RECEITA)}
    var category by rememberSaveable{mutableStateOf(FinanceCategory.EVENTO)}
    var value by rememberSaveable{mutableStateOf("")}
    var description by rememberSaveable{mutableStateOf("")}
    var error by rememberSaveable{mutableStateOf<String?>(null)}
    var success by rememberSaveable{mutableStateOf(false)}
    if(success)FinancePanel {
        Text("Lançamento registrado como pendente.",fontSize=22.sp,fontWeight=FontWeight.Bold,color=PU.Green)
        Text("O valor ainda não compõe o resultado realizado.",color=PU.Muted)
        FinanceButton("Ver lançamentos",onClick=onDone)
    }else FinancePanel {
        FinanceSelect("Tipo",type,MovementType.entries.toList(),{it.label},!store.busy){next->next?.let{type=it;category=if(it==MovementType.RECEITA)FinanceCategory.EVENTO else FinanceCategory.ALIMENTACAO}}
        val categories=if(type==MovementType.RECEITA)listOf(FinanceCategory.EVENTO,FinanceCategory.OUTRA_RECEITA)else listOf(FinanceCategory.ALIMENTACAO,FinanceCategory.MEDICAMENTOS,FinanceCategory.VETERINARIO,FinanceCategory.TRANSPORTE,FinanceCategory.MANUTENCAO,FinanceCategory.OUTRA_DESPESA)
        FinanceSelect("Categoria",category,categories,{it.label},!store.busy){it?.let{category=it}}
        FinanceInput("Valor em reais",value,{value=it;error=null},!store.busy,decimal=true,help="Use vírgula ou ponto para os centavos.")
        FinanceInput("Descrição",description,{description=it;error=null},!store.busy,max=255,help="${description.length}/255")
        error?.let{Text(it,color=MaterialTheme.colorScheme.error,lineHeight=22.sp)}
        FinanceButton(if(store.busy)"Registrando…"else"Registrar pendência",!store.busy){
            error=null
            val normalized=try{normalizeMoney(value)}catch(_:IllegalArgumentException){error="Informe um valor positivo, com até duas casas decimais.";null}
            if(description.isBlank())error="Informe a descrição do lançamento."
            if(normalized!=null&&description.isNotBlank())store.createMovement(type,category,description.trim(),normalized){success=true}
        }
    }
}

@Composable fun DonationForm(store:FinanceStore,ongId:Int,ongName:String,onBack:()->Unit,onLogout:()->Unit,onMyDonations:()->Unit,animalId:Long?=null,onCatalog:()->Unit=onBack,onHome:()->Unit=onCatalog) {
    var value by rememberSaveable(ongId){mutableStateOf("")}
    var message by rememberSaveable(ongId){mutableStateOf("")}
    var error by rememberSaveable(ongId){mutableStateOf<String?>(null)}
    var success by rememberSaveable(ongId){mutableStateOf(false)}
    val animalApi=remember{AnimalApi(BuildConfig.API_BASE_URL)}
    var animal by remember(animalId){mutableStateOf<PublicAnimal?>(null)}
    LaunchedEffect(animalId){if(animalId!=null)try{animal=animalApi.publicGet(animalId)}catch(e:CancellationException){throw e}catch(_:Exception){animal=null}}
    FinanceShell("Registrar uma doação","Registre sua intenção de contribuir com a ONG. Não há pagamento automático nesta plataforma.",false,onBack,onLogout,onCatalog,onHome) {
        FinanceFeedback(store)
        if(success) Surface(color=Color(0xFFE0EEE4),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Color(0xFFB7D7C3))) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("✓",fontSize=30.sp,color=PU.Green)
                Text("Doação registrada",fontSize=26.sp,color=PU.Green,fontWeight=FontWeight.Bold)
                Text("A doação está pendente. A ONG confirmará o recebimento manualmente.",color=PU.Muted,lineHeight=24.sp)
                FinanceButton("Minhas doações",onClick=onMyDonations)
            }
        }else {
            Surface(color=PU.Paper,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,PU.Border)) {
                Row(Modifier.fillMaxWidth().heightIn(min=132.dp),verticalAlignment=Alignment.CenterVertically) {
                    animal?.let{a->PublicPhoto(animalApi,a.id,a.fotos.firstOrNull{it.principal}?:a.fotos.firstOrNull(),Modifier.width(112.dp).height(140.dp))}
                    Column(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Text("ONG beneficiária",color=PU.Foliage,fontWeight=FontWeight.Bold,fontSize=13.sp)
                        Text(ongName,color=PU.Green,fontSize=21.sp,fontWeight=FontWeight.Bold)
                        animal?.let{a->Text("Conhecida por ${a.nome}"+listOfNotNull(a.cidade,a.estado).takeIf{it.isNotEmpty()}?.joinToString(", ",prefix=" · ").orEmpty(),color=PU.Muted,fontSize=13.sp,lineHeight=20.sp)}
                    }
                }
            }
            if(store.profile.name!="USUARIO"||ongId<=0)FinancePanel {Text("Doação indisponível",fontWeight=FontWeight.Bold,color=PU.Green);Text("Entre com uma conta de usuário e escolha uma ONG no catálogo para registrar sua doação.",color=PU.Muted)}
            else FinancePanel {
                FinanceInput("Valor em reais",value,{value=it;error=null},!store.busy,decimal=true,help=error?:"Use vírgula ou ponto para os centavos.",error=error!=null)
                FinanceInput("Mensagem para a ONG (opcional)",message,{message=it},!store.busy,lines=4,max=2000,help="${message.length}/2000")
                Text("Registrar não confirma que a ONG recebeu o valor.",color=PU.Muted,fontSize=12.sp,lineHeight=20.sp)
                FinanceButton(if(store.busy)"Enviando…"else"Registrar doação",!store.busy){
                    error=null
                    val normalized=try{normalizeMoney(value)}catch(_:IllegalArgumentException){error="Informe um valor positivo, com até duas casas decimais.";null}
                    if(normalized!=null)store.createDonation(ongId,normalized,message){success=true}
                }
            }
        }
    }
}
