package br.com.patasunidas.animais

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable private fun Header(title:String,onBack:()->Unit) {
 Column(Modifier.fillMaxWidth().background(PU.Cream)) {
  Row(Modifier.fillMaxWidth().background(PU.Paper).statusBarsPadding().padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
   Box(Modifier.size(32.dp).background(PU.Orange,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) { PawIcon(PU.Green,Modifier.size(20.dp)) }
   Text("Patas Unidas",color=PU.Green,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=10.dp))
  }
  TextButton(onClick=onBack,modifier=Modifier.padding(start=12.dp)){Text("← Voltar")}
  Text(title,style=MaterialTheme.typography.headlineMedium,color=PU.Green,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=20.dp))
 }
}
@Composable private fun Feedback(store:AnimalStore) {
 if(store.busy) { LinearProgressIndicator(Modifier.fillMaxWidth(),color=PU.Green) }
 store.message?.let { Text(it,color=PU.Ink,modifier=Modifier.padding(vertical=12.dp)) }
}
@Composable private fun Photo(store:AnimalStore,animalId:Long,photo:AnimalPhoto,modifier:Modifier=Modifier) {
 val bmp by produceState<Bitmap?>(null,animalId,photo.id,store) {
  value=try { store.api.photo(store.token(),animalId,photo.id) } catch(e:AnimalHttpException) { store.photoFailure(e); null } catch(_:Exception) { null }
 }
 Box(modifier.background(PU.CardGreen,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
  if(bmp==null) Text("Foto indisponível",color=PU.Muted,fontSize=12.sp) else Image(bmp!!.asImageBitmap(),"Fotografia do animal",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
 }
}
@Composable fun OngAnimalsScreen(store:AnimalStore,canEdit:Boolean,onBack:()->Unit,onCreate:()->Unit,onDetail:(Long)->Unit) {
 LaunchedEffect(store) { store.load() }
 Column(Modifier.fillMaxSize().background(PU.Cream)) {
  Header("Animais da ONG",onBack)
  Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp)) {
   Text("Gerencie os registros, a situação e a publicação dos animais da sua organização.",color=PU.Muted,modifier=Modifier.padding(bottom=16.dp)); Feedback(store); if(store.message != null && !store.busy) TextButton(onClick={store.load(store.page)}) { Text("Atualizar lista") }; Text("${store.total} animais",color=PU.Muted)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    listOf(true to "Ativos",false to "Inativos",null to "Todos").forEach { (value,label) -> FilterChip(colors=FilterChipDefaults.filterChipColors(containerColor=PU.Paper,selectedContainerColor=PU.Green,selectedLabelColor=androidx.compose.ui.graphics.Color.White),shape=RoundedCornerShape(50),selected=store.active==value,onClick={store.filter(value)},label={Text(label)},enabled=!store.busy) }
   }
   if(canEdit) FilledButton("Cadastrar animal",onCreate,enabled=!store.busy)
  }
  if(store.items.isEmpty()&&!store.busy) Text("Nenhum animal encontrado. Atualize ou altere o filtro.",modifier=Modifier.padding(20.dp),color=PU.Muted)
  LazyColumn(Modifier.weight(1f).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=12.dp)) {
   items(store.items,key={it.id}) { a ->
    Row(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White,RoundedCornerShape(16.dp)).clickable { if(!store.busy) onDetail(a.id) }.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
     val p=a.fotos.firstOrNull { it.principal } ?: a.fotos.firstOrNull(); if(p!=null) Photo(store,a.id,p,Modifier.size(72.dp)) else Box(Modifier.size(72.dp).background(PU.CardGreen,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) { Text("Sem foto",fontSize=12.sp) }
     Column(Modifier.padding(start=12.dp)) { Text(a.form.nome,fontWeight=FontWeight.Bold,color=PU.Ink); Text("${especies[a.form.especie]} · ${sexos[a.form.sexo]} · ${portes[a.form.porte]}",fontSize=13.sp); Text("${a.situacao.label} · ${if(a.ativo) "Ativo" else "Inativo"}",color=PU.Muted,fontSize=13.sp); Text(if(a.publicado) "Publicado" else "Não publicado",color=PU.Foliage,fontSize=13.sp) }
    }
   }
  }
  Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
   TextButton(onClick={store.load(store.page-1)},enabled=!store.busy&&store.page>0) { Text("Anterior") }
   Text("Página ${if(store.pages==0) 0 else store.page+1} de ${store.pages}",modifier=Modifier.padding(top=12.dp))
   TextButton(onClick={store.load(store.page+1)},enabled=!store.busy&&store.page+1<store.pages) { Text("Próxima") }
  }
 }
}
private val formSaver=listSaver<AnimalForm,String>(save={listOf(it.nome,it.especie,it.sexo,it.porte,it.dataEntrada,it.nascimentoEstimado,it.descricao,it.historia,it.dataResgate,it.origemResgate,it.observacoesResgate)},restore={AnimalForm(it[0],it[1],it[2],it[3],it[4],it[5],it[6],it[7],it[8],it[9],it[10])})
@Composable private fun SelectField(label:String,value:String,choices:Map<String,String>,change:(String)->Unit) {
 var expanded by remember { mutableStateOf(false) }
 Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
  Text(label,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,color=PU.Ink)
  Box { OutlinedButton(onClick={expanded=true},shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(choices[value] ?: "Selecione",color=PU.Ink);Text("⌄",color=PU.Muted) } };DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { choices.forEach { (key,text) -> DropdownMenuItem(text={Text(text)},onClick={change(key);expanded=false}) } } }
 }
}
@Composable private fun AnimalInputField(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier,singleLine:Boolean=false) {
 Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
  Text(label,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,color=PU.Ink)
  OutlinedTextField(value=value,onValueChange=onValueChange,modifier=Modifier.fillMaxWidth(),singleLine=singleLine,shape=RoundedCornerShape(10.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=PU.Paper,unfocusedContainerColor=PU.Paper))
 }
}
@Composable fun AnimalFormScreen(store:AnimalStore,initial:Animal?,onBack:()->Unit,onDone:()->Unit) {
 var original by rememberSaveable(initial?.id,stateSaver=formSaver) { mutableStateOf(initial?.form ?: AnimalForm()) }
 var form by rememberSaveable(initial?.id,stateSaver=formSaver) { mutableStateOf(original) }
 var originalVersion by rememberSaveable(initial?.id) { mutableStateOf(initial?.versao) }
 var confirm by remember { mutableStateOf(false) }; var discardDraft by remember { mutableStateOf(false) }
 fun back() { if(!store.busy) { if(form!=original) confirm=true else onBack() } }
 BackHandler { back() }
 if(discardDraft) AlertDialog(onDismissRequest={discardDraft=false},title={Text("Descartar rascunho?")},text={Text("Suas alterações serão substituídas pelos dados atuais.")},confirmButton={TextButton(onClick={ initial?.id?.let { store.acceptCurrent(it) }?.let { form=it.form; original=it.form; originalVersion=it.versao }; discardDraft=false }){Text("Descartar e recarregar")}},dismissButton={TextButton(onClick={discardDraft=false}){Text("Continuar revisando")}})
 if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Descartar alterações?")},text={Text("As informações não salvas serão perdidas.")},confirmButton={TextButton(onClick={confirm=false;onBack()}){Text("Descartar")}},dismissButton={TextButton(onClick={confirm=false}){Text("Continuar editando")}})
 Column(Modifier.fillMaxSize().background(PU.Cream)) { Header(if(initial==null) "Cadastrar animal" else "Editar animal",::back)
  Text("O animal será vinculado automaticamente à sua ONG e começará não publicado.",color=PU.Muted,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(horizontal=20.dp,vertical=12.dp))
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(20.dp).background(PU.Paper,RoundedCornerShape(16.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
   Feedback(store)
   if(store.conflict && initial!=null) {
    Text("Os dados deste animal foram alterados enquanto você editava. Seu rascunho foi preservado.")
    TextButton(onClick={store.fetchCurrent(initial.id)},enabled=!store.busy){Text("Consultar versão atual")}
    store.currentRemote?.takeIf { it.id==initial.id }?.let { remote ->
     Text("Dados atuais: ${remote.form.nome} · ${remote.situacao.label} · versão ${remote.versao}")
     formDifferences(form,remote.form).forEach { (label,draft,current) -> Text("$label — seu rascunho: ${draft.ifEmpty { "(vazio)" }}; atual: ${current.ifEmpty { "(vazio)" }}") }
     TextButton(onClick={discardDraft=true},enabled=!store.busy){Text("Descartar alterações e recarregar")}
    }
   }
   AnimalInputField(value=form.nome,onValueChange={if(it.length<=100) form=form.copy(nome=it)},label="Nome *",modifier=Modifier.fillMaxWidth(),singleLine=true)
   SelectField("Espécie *",form.especie,especies){form=form.copy(especie=it)}
   SelectField("Sexo *",form.sexo,sexos){form=form.copy(sexo=it)}
   SelectField("Porte *",form.porte,portes){form=form.copy(porte=it)}
   AnimalInputField(form.dataEntrada,{form=form.copy(dataEntrada=it.take(10))},label="Data de entrada * (AAAA-MM-DD)",modifier=Modifier.fillMaxWidth(),singleLine=true)
   AnimalInputField(form.nascimentoEstimado,{form=form.copy(nascimentoEstimado=it.take(10))},label="Nascimento estimado (AAAA-MM-DD)",modifier=Modifier.fillMaxWidth(),singleLine=true)
   AnimalInputField(form.dataResgate,{form=form.copy(dataResgate=it.take(10))},label="Data do resgate (AAAA-MM-DD)",modifier=Modifier.fillMaxWidth(),singleLine=true)
   AnimalInputField(form.origemResgate,{if(it.length<=255)form=form.copy(origemResgate=it)},label="Origem do resgate",modifier=Modifier.fillMaxWidth())
   AnimalInputField(form.descricao,{if(it.length<=2000)form=form.copy(descricao=it)},label="Descrição",modifier=Modifier.fillMaxWidth())
   AnimalInputField(form.historia,{if(it.length<=2000)form=form.copy(historia=it)},label="História",modifier=Modifier.fillMaxWidth())
   AnimalInputField(form.observacoesResgate,{if(it.length<=2000)form=form.copy(observacoesResgate=it)},label="Observações do resgate",modifier=Modifier.fillMaxWidth())
   FilledButton(if(store.busy) "Salvando..." else "Salvar",{store.save(initial?.id,form,originalVersion,onDone)},enabled=!store.busy)
  }
 }
}
@Composable fun OngAnimalDetailsScreen(store:AnimalStore,canEdit:Boolean,onBack:()->Unit,onEdit:()->Unit,onProntuario:(()->Unit)?=null) {
 val a=store.selected; var pendingStatus by remember { mutableStateOf<Pair<AnimalStatus,Long>?>(null) }; var deactivate by remember { mutableStateOf(false) }; var uri by remember { mutableStateOf<Uri?>(null) }; var publication by remember { mutableStateOf<Boolean?>(null) }; val context=LocalContext.current
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri=it }
 BackHandler { if(!store.busy) onBack() }
 pendingStatus?.let { (target,version) -> AlertDialog(onDismissRequest={pendingStatus=null},title={Text("Alterar situação?")},text={Text("Alterar para ${target.label}?")},confirmButton={TextButton(onClick={pendingStatus=null;store.setStatus(target,version)}){Text("Confirmar")}},dismissButton={TextButton(onClick={pendingStatus=null}){Text("Cancelar")}}) }
 publication?.let { target -> AlertDialog(onDismissRequest={publication=null},title={Text(if(target) "Publicar animal?" else "Despublicar animal?")},text={Text("A publicação é independente da situação. Só animais disponíveis aparecem no catálogo.")},confirmButton={TextButton(onClick={publication=null;store.publication(target)},enabled=!store.busy){Text("Confirmar")}},dismissButton={TextButton(onClick={publication=null}){Text("Cancelar")}}) }
 if(deactivate) AlertDialog(onDismissRequest={deactivate=false},title={Text("Inativar animal?")},text={Text("O animal deixará de estar ativo. O registro e as fotos serão preservados.")},confirmButton={TextButton(onClick={deactivate=false;store.deactivate()}){Text("Inativar")}},dismissButton={TextButton(onClick={deactivate=false}){Text("Cancelar")}})
 Column(Modifier.fillMaxSize().background(PU.Cream)) { Header("Detalhes do animal") { if(!store.busy) onBack() }
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   Feedback(store)
   if(a!=null) {
    if(store.conflict) {
     Text("Os dados foram alterados. Consulte a versão atual antes de tentar novamente.")
     TextButton(onClick={store.fetchCurrent(a.id)},enabled=!store.busy){Text("Consultar versão atual")}
     store.currentRemote?.takeIf { it.id==a.id }?.let { remote -> Text("Situação atual: ${remote.situacao.label}"); TextButton(onClick={store.acceptCurrent(a.id)},enabled=!store.busy){Text("Atualizar detalhes")} }
    }
    Text(a.form.nome,style=MaterialTheme.typography.headlineLarge,color=PU.Green,fontWeight=FontWeight.Bold); Text("${especies[a.form.especie]} · ${sexos[a.form.sexo]} · ${portes[a.form.porte]}")
    Text("Situação: ${a.situacao.label}",fontWeight=FontWeight.Bold); Text(if(a.ativo) "Cadastro ativo" else "Cadastro inativo"); Text(if(a.publicado) "Publicado" else "Não publicado",color=PU.Foliage)
    Text("Entrada: ${a.form.dataEntrada}"); if(a.form.nascimentoEstimado.isNotEmpty()) Text("Nascimento estimado: ${a.form.nascimentoEstimado}")
    if(a.form.dataResgate.isNotEmpty()) Text("Resgate: ${a.form.dataResgate}"); if(a.form.origemResgate.isNotEmpty()) Text("Origem: ${a.form.origemResgate}")
    listOf("Descrição" to a.form.descricao,"História" to a.form.historia,"Observações do resgate" to a.form.observacoesResgate).forEach { (title,value) -> if(value.isNotEmpty()) { Text(title,fontWeight=FontWeight.Bold);Text(value) } }
    onProntuario?.let { open -> OutlinedButton(onClick=open,enabled=!store.busy,modifier=Modifier.fillMaxWidth()){Text("Ver prontuário")} }
    if(canEdit&&a.ativo) {
     FilledButton("Editar animal",onEdit,enabled=!store.busy)
     OutlinedButton(onClick={publication=!a.publicado},enabled=!store.busy,modifier=Modifier.fillMaxWidth()){Text(if(a.publicado) "Despublicar animal" else "Publicar animal")}
     if(a.situacao.next().isNotEmpty()) { Text("Alterar situação",fontWeight=FontWeight.Bold); a.situacao.next().forEach { option -> OutlinedButton(onClick={pendingStatus=option to a.versao},enabled=!store.busy,modifier=Modifier.fillMaxWidth()) {Text(option.label)} } }
     OutlinedButton(onClick={deactivate=true},enabled=!store.busy,modifier=Modifier.fillMaxWidth()){Text("Inativar animal")}
    }
    Text("Fotografias",style=MaterialTheme.typography.titleLarge,color=PU.Green)
    if(a.fotos.isEmpty()) Text("Nenhuma fotografia cadastrada.",color=PU.Muted)
    a.fotos.forEach { p -> Photo(store,a.id,p,Modifier.fillMaxWidth().aspectRatio(4f/3f)); if(p.principal) Text("Foto principal",color=PU.Muted) }
    if(canEdit&&a.ativo&&a.fotos.size<10) {
     OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!store.busy,modifier=Modifier.fillMaxWidth()){Text("Selecionar fotografia")}
     uri?.let { selected ->
      val preview by produceState<Bitmap?>(null,selected) { value=withContext(Dispatchers.IO) { try { val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }; context.contentResolver.openInputStream(selected)?.use { BitmapFactory.decodeStream(it,null,options) }; if(options.outWidth<=0 || options.outHeight<=0 || options.outWidth.toLong()*options.outHeight>16_000_000) null else { var sample=1; while(options.outWidth/sample>600 || options.outHeight/sample>600)sample*=2; context.contentResolver.openInputStream(selected)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) } } } catch(_:Exception){ null } } }
      if(preview!=null) Image(preview!!.asImageBitmap(),"Prévia da fotografia",Modifier.fillMaxWidth().height(180.dp),contentScale=ContentScale.Fit) else Text("Não foi possível mostrar a prévia.")
      FilledButton("Enviar fotografia",{store.upload(selected,context.contentResolver);uri=null},enabled=!store.busy)
     }
    }
   }
  }
 }
}
