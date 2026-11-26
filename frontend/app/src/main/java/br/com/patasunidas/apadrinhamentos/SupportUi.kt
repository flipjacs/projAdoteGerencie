package br.com.patasunidas.apadrinhamentos

import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.ui.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

internal fun supportMoney(value:String)=try{NumberFormat.getCurrencyInstance(Locale("pt","BR")).format(BigDecimal(value))}catch(_:Exception){value}
internal fun supportDate(value:String?)=value?.take(10)?.let{if(it.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) "${it.substring(8)}/${it.substring(5,7)}/${it.take(4)}" else it}?:"—"
internal fun categoryLabel(value:String)=value.lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}
data class SupportNavigation(val home:()->Unit,val adoptions:()->Unit,val donations:()->Unit,val sponsorships:()->Unit,val adopted:()->Unit)
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SupportShell(title:String,description:String,onBack:()->Unit,onAccount:()->Unit,internal:Boolean=false,personal:SupportNavigation?=null,supportsSelected:Boolean=false,signedIn:Boolean=true,eyebrow:String?=null,showBack:Boolean=true,public:Boolean=false,onPublicHome:()->Unit=onBack,onPublicCatalog:()->Unit=onBack,content:@Composable ColumnScope.()->Unit){
 BackHandler(onBack=onBack)
 Column(Modifier.fillMaxSize().background(PU.Cream).statusBarsPadding().navigationBarsPadding().imePadding()){
  Column(if(public)Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp).background(PU.Green,RoundedCornerShape(50)).padding(horizontal=16.dp)else Modifier.fillMaxWidth().background(PU.Paper).padding(horizontal=16.dp)){
   Row(Modifier.fillMaxWidth().heightIn(min=if(public)67.dp else 76.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
    if(internal)OutlinedButton(onClick=onBack,contentPadding=PaddingValues(0.dp),shape=RoundedCornerShape(13.dp),modifier=Modifier.size(44.dp)){Text("←",fontSize=24.sp)}
    Box(Modifier.size(34.dp).background(PU.Orange,RoundedCornerShape(12.dp)).padding(7.dp)){PawIcon(PU.Green,Modifier.fillMaxSize())}
    Text("Patas Unidas",fontSize=14.sp,fontWeight=FontWeight.Bold,color=if(public)Color.White else PU.Green,modifier=Modifier.weight(1f))
    OutlinedButton(onClick=onAccount,modifier=Modifier.heightIn(min=44.dp)){Text(if(signedIn)"Sair" else "Entrar",color=if(public)Color.White else PU.Green,fontSize=12.sp,fontWeight=FontWeight.Bold)}
   }
   personal?.let { nav->
    HorizontalDivider(color=PU.Border)
    listOf("Início" to nav.home,"Adoções" to nav.adoptions,"Doações" to nav.donations,"Apadrinhamentos" to nav.sponsorships,"Adotados" to nav.adopted).chunked(3).forEach { links ->
     Row(Modifier.fillMaxWidth()) {
      links.forEach { (label,action) ->
       val active = supportsSelected && label == "Apadrinhamentos"
       TextButton(onClick=action,
        modifier=Modifier.weight(1f).heightIn(min=46.dp).semantics { selected = active },
        colors=ButtonDefaults.textButtonColors(containerColor=if(active) Color(0xFFE9EEE5) else Color.Transparent),
        contentPadding=PaddingValues(horizontal=4.dp)) {
        Text(label,fontSize=11.5.sp,letterSpacing=0.sp,maxLines=1,fontWeight=FontWeight.Bold)
       }
      }
      repeat(3-links.size){Spacer(Modifier.weight(1f))}
     }
    }
   }
  }
  Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start=16.dp,end=16.dp,top=if(personal!=null) 22.dp else 28.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
   if(showBack)TextButton(onClick=onBack,contentPadding=PaddingValues(0.dp)){Text("← Voltar",fontWeight=FontWeight.Bold)}
   Column(verticalArrangement=Arrangement.spacedBy(12.dp)){eyebrow?.let{Text(it,color=PU.Foliage,fontSize=13.sp,fontWeight=FontWeight.Bold)};WorkspaceTitle(title);if(description.isNotEmpty())Text(description,color=PU.Muted,fontSize=15.sp,lineHeight=25.sp)}
   content()
  }
  if(public)Row(Modifier.fillMaxWidth().background(PU.Paper).padding(vertical=10.dp),horizontalArrangement=Arrangement.SpaceEvenly){TextButton(onClick=onPublicHome){Text("Início")};TextButton(onClick=onPublicCatalog){Text("Animais")};TextButton(onClick=onAccount){Text(if(signedIn)"Sair"else"Entrar")}}
 }
}
@Composable internal fun SupportPanel(personal:Boolean=false,content:@Composable ColumnScope.()->Unit){Surface(color=PU.Paper,shape=RoundedCornerShape(if(personal)20.dp else 13.dp),border=BorderStroke(1.dp,PU.Border)){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)}}
@Composable internal fun SupportAction(label: String, busy: Boolean = false, secondary: Boolean = false, onClick: () -> Unit) =
    WorkspaceAction(label, !busy, secondary, onClick)
@Composable internal fun SupportInput(label:String,value:String,enabled:Boolean=true,multiline:Boolean=false,decimal:Boolean=false,change:(String)->Unit){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text(label,fontSize=13.sp,color=PU.Ink,fontWeight=FontWeight.Bold);OutlinedTextField(value,onValueChange=change,enabled=enabled,singleLine=!multiline,minLines=if(multiline)3 else 1,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp),keyboardOptions=KeyboardOptions(keyboardType=if(decimal)KeyboardType.Decimal else KeyboardType.Text),prefix=if(decimal)({Text("R$",modifier=Modifier.padding(end=8.dp),fontWeight=FontWeight.Bold,color=PU.Green)})else null,colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color.White,focusedContainerColor=Color.White,unfocusedBorderColor=PU.Border))}}
@Composable internal fun <T> SupportSelect(label:String,value:T?,options:List<T>,name:(T)->String,busy:Boolean=false,all:String?="Todas",change:(T?)->Unit){var open by remember{mutableStateOf(false)};Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text(label,fontWeight=FontWeight.Bold,color=PU.Ink,fontSize=13.sp);Box{OutlinedButton(onClick={open=true},enabled=!busy,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,PU.Border),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(value?.let(name)?:all.orEmpty(),color=PU.Ink);Text("⌄")}};DropdownMenu(open,{open=false}){if(all!=null)DropdownMenuItem(text={Text(all)},onClick={open=false;change(null)});options.forEach{x->DropdownMenuItem(text={Text(name(x))},onClick={open=false;change(x)})}}}}}
@Composable internal fun SupportStatus(
    label: String, pending: Boolean = false, closed: Boolean = false, internal: Boolean = true
) {
    val tone = when {
        pending -> if (internal) StatusTone.Pending else StatusTone.PersonalPending
        closed -> if (internal) StatusTone.Neutral else StatusTone.PersonalMuted
        else -> if (internal) StatusTone.Success else StatusTone.PersonalSuccess
    }
    WorkspaceStatus(label, tone)
}
@Composable internal fun SupportFact(label:String,value:String){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)){Text(label,color=PU.Muted,fontSize=12.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(value,color=PU.Ink,fontSize=14.sp,textAlign=TextAlign.End,modifier=Modifier.weight(1.4f))}}
@Composable internal fun SupportNotice(message:String?){message?.let{val error=it.startsWith("Não")||it.startsWith("Confira")||it.startsWith("A situação")||it.startsWith("Registro não")||it.startsWith("Informe")||it.startsWith("Muitas");Text(it,color=if(error)Color(0xFFA13F34)else PU.Green,lineHeight=23.sp,modifier=Modifier.fillMaxWidth().background(if(error)Color(0xFFFDF0EC)else Color(0xFFE0EEE4),RoundedCornerShape(10.dp)).padding(16.dp))}}
@Composable internal fun SupportFeedback(busy:Boolean,message:String?,retry:()->Unit){if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text("Carregando informações…",color=PU.Muted)};SupportNotice(message);if(message!=null)TextButton(onClick=retry,enabled=!busy){Text("Atualizar dados")}}
@Composable internal fun SupportPager(page:SupportPage<*>?,busy:Boolean,change:(Int)->Unit){if(page!=null&&page.pages>1){Text("${page.total} registros · Página ${page.page+1} de ${page.pages}",color=PU.Muted,fontSize=13.sp);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton(onClick={change(page.page-1)},enabled=!busy&&page.page>0){Text("Anterior")};OutlinedButton(onClick={change(page.page+1)},enabled=!busy&&page.page+1<page.pages){Text("Próxima")}}}}
@Composable internal fun SupportEmpty(title:String,text:String){SupportPanel{Text(title,color=PU.Green,fontWeight=FontWeight.Bold,fontSize=20.sp);Text(text,color=PU.Muted,lineHeight=24.sp)}}
@Composable internal fun SupportMetric(label:String,value:String){Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFFE9EEE5),RoundedCornerShape(12.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(label,color=PU.Muted,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(value,color=PU.Green,fontSize=17.sp,fontWeight=FontWeight.ExtraBold)}}
@Composable internal fun SupportCategories(title:String,values:Map<String,String>){Text(title,color=PU.Green,fontWeight=FontWeight.Bold,fontSize=17.sp);if(values.isEmpty())Text("Sem ${if(title.startsWith("Receitas"))"receitas"else"despesas"} no período.",color=PU.Muted);values.forEach{(category,value)->SupportFact(categoryLabel(category),supportMoney(value));HorizontalDivider(color=PU.Border)}}
@Composable internal fun SupportSnapshot(data:PublicTransparencyData){SupportMetric("Receitas realizadas",supportMoney(data.receitasRealizadas));SupportMetric("Despesas realizadas",supportMoney(data.despesasRealizadas));SupportMetric("Resultado líquido",supportMoney(data.resultadoLiquido));SupportMetric("Movimentações realizadas",data.quantidadeMovimentacoes.toString());SupportCategories("Receitas por categoria",data.receitasPorCategoria);SupportCategories("Despesas por categoria",data.despesasPorCategoria)}
