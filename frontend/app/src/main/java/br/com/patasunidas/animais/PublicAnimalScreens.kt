package br.com.patasunidas.animais

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PublicAnimalsExperience(api: AnimalApi, signedIn: Boolean, onAccount: () -> Unit, onAdopt: (Long) -> Unit = {}, onMyRequests: () -> Unit = {}, initialAnimalId: Long? = null, initialHome: Boolean = true, onDonate: (PublicAnimal) -> Unit = {}, onMyDonations: () -> Unit = {}, onSponsor:(Long)->Unit={}, onMySupports:()->Unit={}, onTransparency:(Int?)->Unit={}, personalActions:Boolean=signedIn, onPublicBack:(()->Unit)?=null, onGuestAccount:((Long?)->Unit)?=null) {
 var home by rememberSaveable { mutableStateOf(initialAnimalId == null && initialHome) }
 var id by rememberSaveable { mutableStateOf<Long?>(initialAnimalId) }
 var species by rememberSaveable { mutableStateOf<String?>(null) }
 var sex by rememberSaveable { mutableStateOf<String?>(null) }
 var size by rememberSaveable { mutableStateOf<String?>(null) }
 var moreFilters by rememberSaveable { mutableStateOf(false) }
 var page by rememberSaveable { mutableIntStateOf(0) }
 var attempt by remember { mutableIntStateOf(0) }
 var result by remember { mutableStateOf<PublicAnimalPage?>(null) }
 var selected by remember { mutableStateOf<PublicAnimal?>(null) }
 var loading by remember { mutableStateOf(false) }
 var error by remember { mutableStateOf<String?>(null) }
 LaunchedEffect(id, page, species, sex, size, attempt, home) {
  loading=true; error=null; selected=null; result=null
  try { if(id!=null) selected=api.publicGet(id!!) else result=api.publicList(page,if(home) null else species,if(home) null else sex,if(home) null else size) }
  catch(e:CancellationException) { throw e }
  catch(e:AnimalHttpException) { error=if(e.status==404) "Este animal não está disponível no catálogo." else "Não foi possível carregar os animais agora." }
  catch(_:Exception) { error="Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente." }
  finally { loading=false }
 }
 BackHandler(enabled=id!=null || !home) { if(id!=null) id=null else home=true }
 Column(Modifier.fillMaxSize().background(PU.Cream)) {
  Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp,vertical=12.dp).clip(RoundedCornerShape(50)).background(PU.Green).padding(horizontal=18.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(PU.Orange),contentAlignment=Alignment.Center) { PawIcon(PU.Green,Modifier.size(20.dp)) }
    Text("Patas Unidas",color=Color.White,fontWeight=FontWeight.Bold,fontSize=17.sp)
   }
   onPublicBack?.let { TextButton(onClick=it){Text("Minha área",color=Color.White,fontSize=12.sp)} }
   TextButton(onClick={ if (!signedIn && onGuestAccount != null) onGuestAccount(id) else onAccount() }) { Text(if(signedIn) "Sair" else "Entrar",color=Color.White) }
  }
  LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=24.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
   if(id!=null) {
    item { TextButton(onClick={id=null}) { Text("← Voltar aos animais") } }
    selected?.let { animal ->
     item { AnimalGallery(api,animal) }
     item {
      Text("Disponível para adoção",color=PU.Green,style=MaterialTheme.typography.labelMedium,modifier=Modifier.background(androidx.compose.ui.graphics.Color(0xFFE1EEE2),RoundedCornerShape(50)).padding(horizontal=14.dp,vertical=7.dp))
      Text(animal.nome,fontSize=35.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,color=PU.Ink)
      Text("${especies[animal.especie]} · ${sexos[animal.sexo]}",color=PU.Foliage,modifier=Modifier.padding(top=8.dp))
     }
     animal.descricao?.let { item { Text(it,color=PU.Muted,lineHeight=26.sp) } }
     item {
      Surface(color=PU.Paper,shape=RoundedCornerShape(12.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
       Text("Informações",fontWeight=FontWeight.Bold,color=PU.Green)
       Text("Espécie: ${especies[animal.especie]}");Text("Porte: ${portes[animal.porte]}");Text("Sexo: ${sexos[animal.sexo]}")
       animal.nascimentoEstimado?.let { Text("Nascimento estimado: $it") }
      } }
     }
     item { HorizontalDivider(color=PU.Border); Text("ONG responsável",fontWeight=FontWeight.Bold,fontSize=20.sp,modifier=Modifier.padding(top=20.dp)); Text(animal.ongNome ?: "Não informada",color=PU.Green); DemoOngLogo(animal.ongId); Text(listOfNotNull(animal.cidade,animal.estado).joinToString(", "),color=PU.Muted) }
     if(personalActions||!signedIn) item { Button(onClick={onAdopt(animal.id)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Solicitar adoção")}; Text("A solicitação será analisada pela ONG responsável.",color=PU.Muted,fontSize=13.sp,modifier=Modifier.padding(top=8.dp)) }
     if(personalActions||!signedIn) item { OutlinedButton(onClick={onSponsor(animal.id)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Apadrinhar ${animal.nome}")} }
     if(animal.ongId!=null) item { TextButton(onClick={onTransparency(animal.ongId)}){Text("Transparência da ONG")} }
     if(animal.ongId!=null&&(personalActions||!signedIn)) item { OutlinedButton(onClick={onDonate(animal)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Doar para ${animal.ongNome ?: "a ONG"}")} }
    }
   } else {
    item {
     Text(if(home) "Cada vida merece um novo começo." else "Encontre uma companhia para a vida.",fontSize=36.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,color=PU.Ink)
     Text(if(home) "O Patas Unidas aproxima pessoas e ONGs de proteção animal para transformar histórias por meio da adoção e do cuidado." else "Conheça os animais disponíveis nas ONGs parceiras.",color=PU.Muted,lineHeight=26.sp,modifier=Modifier.padding(top=16.dp))
     if(home) Button(onClick={home=false;page=0},modifier=Modifier.padding(top=20.dp),colors=ButtonDefaults.buttonColors(containerColor=PU.Orange,contentColor=PU.Green)) { Text("Conhecer os animais") }
    }
    if(!home) item {
     Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      (mapOf<String?,String>(null to "Todos")+especies).forEach { (value,label) -> FilterChip(shape=RoundedCornerShape(50),modifier=Modifier.heightIn(min=44.dp),colors=FilterChipDefaults.filterChipColors(containerColor=PU.Paper,selectedContainerColor=PU.Green,selectedLabelColor=Color.White),selected=species==value,onClick={species=value;page=0},label={Text(if(value=="CAO") "Cães" else if(value=="GATO") "Gatos" else if(value=="OUTRO") "Outros" else label,fontWeight=FontWeight.Bold)}) }
     }
     if(moreFilters) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      PublicFilter("Sexo",sex,sexos) { sex=it;page=0 };PublicFilter("Porte",size,portes) { size=it;page=0 }
     }
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
      if(!loading && error==null) Text("${result?.total ?: 0} ${if(result?.total==1L) "animal encontrado" else "animais encontrados"}",color=PU.Muted,fontSize=13.sp)
      TextButton(onClick={moreFilters=!moreFilters}){Text(if(moreFilters) "Ocultar filtros" else "Sexo e porte",fontSize=13.sp)}
     }
    }
    if(home) item { TextButton(onClick={onTransparency(null)}){Text("Transparência pública")} }
    if(home) item { Text("Histórias que podem encontrar um lar.",style=MaterialTheme.typography.headlineSmall,color=PU.Green) }
    items(if(home) result?.items.orEmpty().take(3) else result?.items.orEmpty(),key={it.id}) { animal ->
     Surface(onClick={id=animal.id},color=PU.Paper,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,PU.Border)) {
      Column {
       PublicPhoto(api,animal.id,animal.fotos.firstOrNull { it.principal } ?: animal.fotos.firstOrNull(),Modifier.fillMaxWidth().aspectRatio(4f/3f))
       Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(animal.nome,fontSize=25.sp,fontWeight=FontWeight.Bold,color=PU.Ink)
        Text("${especies[animal.especie]} · ${portes[animal.porte]}",color=PU.Muted,fontSize=14.sp)
        Text("Conhecer ${animal.nome} ›",color=PU.Green,fontWeight=FontWeight.Bold,fontSize=14.sp)
       }
      }
     }
    }
    if(!loading && error==null && result?.items?.isEmpty()==true) item {
     Surface(color=PU.Paper,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,PU.Border)) { Column(Modifier.fillMaxWidth().padding(28.dp)) { Text("Nenhum animal encontrado",fontWeight=FontWeight.Bold,color=PU.Ink);Text("As ONGs ainda não publicaram animais disponíveis para estes filtros.",color=PU.Muted,modifier=Modifier.padding(top=12.dp)) } }
    }
    if(!home && (result?.pages ?: 0)>1) item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
     TextButton(onClick={page--},enabled=page>0&&!loading) { Text("Anterior") };Text("Página ${page+1}");TextButton(onClick={page++},enabled=page+1<(result?.pages ?: 0)&&!loading) { Text("Próxima") }
    } }
   }
   if(loading) item { LinearProgressIndicator(Modifier.fillMaxWidth());Text("Carregando animais…",color=PU.Muted) }
   error?.let { item { Text(it,color=PU.Muted);OutlinedButton(onClick={attempt++}) { Text("Tentar novamente") } } }
  }
  if(signedIn&&personalActions) TextButton(onClick=onMySupports,modifier=Modifier.fillMaxWidth().background(PU.Paper)){Text("Meus apadrinhamentos")}
  if(signedIn&&personalActions) TextButton(onClick=onMyDonations,modifier=Modifier.fillMaxWidth().background(PU.Paper)){Text("Minhas doações")}
  Row(Modifier.fillMaxWidth().background(PU.Paper).navigationBarsPadding().padding(8.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
   TextButton(onClick={id=null;home=true;page=0}){Text("Início")};TextButton(onClick={id=null;home=false}){Text("Animais")};TextButton(onClick=if(!personalActions&&signedIn) onPublicBack?:onAccount else if(signedIn) onMyRequests else ({ if (onGuestAccount != null) onGuestAccount(id) else onAccount() })){Text(if(!personalActions&&signedIn) "Minha área" else if(signedIn) "Minhas adoções" else "Entrar")}
  }
 }
}
@Composable private fun PublicFilter(label:String,value:String?,options:Map<String,String>,change:(String?)->Unit) {
 var open by remember { mutableStateOf(false) }
 Box { OutlinedButton(onClick={open=true}){Text("$label: ${options[value] ?: "Todos"}")}; DropdownMenu(open,{open=false}) { DropdownMenuItem(text={Text("Todos")},onClick={change(null);open=false});options.forEach { (key,text) -> DropdownMenuItem(text={Text(text)},onClick={change(key);open=false}) } } }
}
@Composable fun PublicPhoto(api:AnimalApi,id:Long,photo:AnimalPhoto?,modifier:Modifier=Modifier) {
 val bitmap by produceState<Bitmap?>(null,id,photo?.id,api) {
  if(photo!=null) value=try { val bytes=api.publicPhotoBytes(id,photo.id);withContext(Dispatchers.IO) { val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts);if(opts.outWidth<=0||opts.outHeight<=0||opts.outWidth.toLong()*opts.outHeight>16_000_000) null else { var sample=1;while(opts.outWidth/sample>800||opts.outHeight/sample>800)sample*=2;BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample }) } } } catch(e:CancellationException){throw e}catch(_:Exception){null}
 }
 Box(modifier.background(PU.CardGreen),contentAlignment=Alignment.Center) { if(bitmap==null) Text("Fotografia indisponível",color=PU.Foliage,fontSize=13.sp) else Image(bitmap!!.asImageBitmap(),"Fotografia do animal",Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
}
@Composable private fun AnimalGallery(api:AnimalApi,animal:PublicAnimal) {
 var current by remember(animal.id) { mutableStateOf(animal.fotos.firstOrNull { it.principal } ?: animal.fotos.firstOrNull()) }
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
  PublicPhoto(api,animal.id,current,Modifier.fillMaxWidth().aspectRatio(4f/3f).clip(RoundedCornerShape(20.dp)))
  if(animal.fotos.size>1) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) { animal.fotos.forEach { photo -> Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).border(if(current==photo) 2.dp else 0.dp,PU.Green,RoundedCornerShape(12.dp)).clickable { current=photo }) { PublicPhoto(api,animal.id,photo,Modifier.fillMaxSize()) } } }
 }
}
