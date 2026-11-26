package br.com.patasunidas.prontuarios

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.animais.AnimalStatus
import br.com.patasunidas.ui.InstitutionalMenuButton
import br.com.patasunidas.ui.PU
import br.com.patasunidas.ui.WorkspaceAction
import br.com.patasunidas.ui.PawIcon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ClinicalScreen { LIST, CREATE, DETAIL, CORRECT, HISTORY }
private val clinicalKinds = listOf(ClinicalType.CONSULTA, ClinicalType.VACINA, ClinicalType.VERMIFUGACAO, ClinicalType.EXAME, ClinicalType.CIRURGIA, ClinicalType.TRATAMENTO, ClinicalType.MEDICACAO, ClinicalType.OBSERVACAO)
private fun clinicalDate(value: String): String = try {
    val date = if (value.length == 10) LocalDate.parse(value) else Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate()
    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
} catch (_: Exception) { value }

/** Clinical content stays separate from institutional traceability and the adopter projection. */
@Composable
fun ProntuarioExperience(store: ProntuarioStore, onBack: () -> Unit, onLogout: () -> Unit, onCatalog: () -> Unit = onBack, onHome: () -> Unit = onBack) {
    var screen by rememberSaveable(store.animalId) { mutableStateOf(ClinicalScreen.LIST) }
    var id by rememberSaveable(store.animalId) { mutableStateOf<Long?>(null) }
    var success by rememberSaveable(store.animalId) { mutableStateOf<String?>(null) }
    val internalRecord = store.selectedInstitutional?.takeIf { it.id == id }
    val personalRecord = store.selectedPersonal?.takeIf { it.id == id }
    val data = if (store.internal) internalRecord?.data else personalRecord?.data
    val createBlocked = store.animalStatus in setOf(AnimalStatus.ADOTADO, AnimalStatus.FALECIDO)

    LaunchedEffect(store, screen, id) {
        when (screen) {
            ClinicalScreen.LIST -> store.load(store.institutional?.page ?: store.personal?.page ?: 0)
            ClinicalScreen.DETAIL -> id?.let(store::details)
            ClinicalScreen.HISTORY -> if (store.internal) id?.let { store.loadHistory(it) }
            else -> Unit
        }
    }
    fun back() {
        if (store.busy) return
        success = null
        when (screen) {
            ClinicalScreen.LIST -> onBack()
            ClinicalScreen.CORRECT, ClinicalScreen.HISTORY -> { store.clearConflict(); screen = ClinicalScreen.DETAIL }
            else -> { screen = ClinicalScreen.LIST; id = null; store.clearSelection() }
        }
    }
    fun open(recordId: Long) { store.clearSelection(); success = null; id = recordId; screen = ClinicalScreen.DETAIL }
    val title = when (screen) {
        ClinicalScreen.LIST -> if (store.internal) "Prontuário de ${store.animalName}" else "Histórico veterinário"
        ClinicalScreen.CREATE -> "Novo registro clínico"
        ClinicalScreen.CORRECT -> "Corrigir registro clínico"
        ClinicalScreen.HISTORY -> "Histórico de correções"
        ClinicalScreen.DETAIL -> data?.titulo ?: "Registro clínico"
    }
    val description = when (screen) {
        ClinicalScreen.LIST -> if (store.internal) "Histórico clínico do animal #${store.animalId}" else "Registros clínicos autorizados do animal que você adotou. Consulta somente de leitura."
        ClinicalScreen.CREATE -> "Prontuário de ${store.animalName}"
        ClinicalScreen.CORRECT -> "${store.animalName} · ${data?.titulo.orEmpty()}"
        ClinicalScreen.HISTORY -> "Versões anteriores preservadas em ordem cronológica."
        ClinicalScreen.DETAIL -> data?.let { "${it.tipo.label} · ${clinicalDate(it.dataProcedimento)}" }.orEmpty()
    }
    ClinicalShell(title, description, store.internal, store.busy, ::back, onLogout, onCatalog, onHome, screen, store.animalName) {
        if (store.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        success?.let { ClinicalNotice(it) }
        store.message?.let { message ->
            ClinicalNotice(message, error = true)
            if (!store.busy && screen !in setOf(ClinicalScreen.CREATE, ClinicalScreen.CORRECT)) ClinicalAction("Tentar novamente", secondary = true) {
                when (screen) {
                    ClinicalScreen.LIST -> store.load(store.institutional?.page ?: store.personal?.page ?: 0)
                    ClinicalScreen.HISTORY -> id?.let { store.loadHistory(it, store.history?.page ?: 0) }
                    else -> id?.let(store::details)
                }
            }
        }
        when (screen) {
            ClinicalScreen.LIST -> {
                if (store.internal && store.canWrite && !createBlocked) ClinicalAction("Novo registro", enabled = !store.busy) { success = null; screen = ClinicalScreen.CREATE }
                if (store.internal && createBlocked) Text("O histórico permanece consultável. Novos procedimentos comuns não podem ser registrados para este animal.", fontSize = 13.sp, color = PU.Muted, lineHeight = 21.sp)
                if (store.internal) ClinicalFilters(store)
                if (!store.busy && store.message == null) {
                    if (store.internal) store.institutional?.let { page ->
                        if (page.items.isEmpty()) ClinicalEmpty("Nenhum registro clínico encontrado", if (store.typeFilter != null || store.de.isNotBlank() || store.ate.isNotBlank()) "Ajuste os filtros para consultar outras datas ou tipos." else "O histórico deste animal aparecerá aqui após o primeiro registro.")
                        page.items.forEach { item -> ClinicalPanel {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { ClinicalKind(item.data.tipo); Text(clinicalDate(item.data.dataProcedimento), color = PU.Muted, fontSize = 13.sp) }
                            Text(item.data.titulo, color = PU.Green, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(item.data.descricao, color = PU.Ink, lineHeight = 26.sp)
                            Text("Registrado por ${item.registradoPor.nome}", fontSize = 13.sp, color = PU.Muted)
                            ClinicalLink("Abrir registro →") { open(item.id) }
                        } }
                        ClinicalPager(page.page, page.pages, page.total, store.busy, store::load)
                    } else store.personal?.let { page ->
                        if (page.items.isEmpty()) ClinicalEmpty("Nenhum registro clínico encontrado", "A ONG ainda não registrou procedimentos para este animal no período disponível.")
                        page.items.forEach { item ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                Box(Modifier.width(2.dp).fillMaxHeight().background(PU.Border))
                                Column(Modifier.weight(1f).padding(start = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(clinicalDate(item.data.dataProcedimento), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = PU.Foliage)
                                    Text(item.data.tipo.label.uppercase(), color = PU.Foliage, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(item.data.titulo, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = PU.Green)
                                    Text(item.data.descricao, color = PU.Muted, lineHeight = 24.sp, fontSize = 14.sp)
                                    ClinicalLink("Ver registro completo") { open(item.id) }
                                    HorizontalDivider(color = PU.Border)
                                }
                            }
                        }
                        ClinicalPager(page.page, page.pages, page.total, store.busy, store::load)
                    }
                }
            }
            ClinicalScreen.CREATE -> if (store.internal && store.canWrite) {
                if (createBlocked) ClinicalNotice("Este animal não pode receber novos procedimentos clínicos.", true)
                else ClinicalEditor(store, null) { newId -> success = "Registro clínico criado."; id = newId; screen = ClinicalScreen.DETAIL }
            }
            ClinicalScreen.DETAIL -> if (!store.busy && data != null && store.message == null) {
                if (store.internal && store.canWrite) ClinicalAction("Corrigir registro", secondary = true) { success = null; screen = ClinicalScreen.CORRECT }
                ClinicalPanel {
                    if (store.internal) {
                        Text("${data.tipo.label} · ${clinicalDate(data.dataProcedimento)}", color = PU.Foliage, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(data.titulo, color = PU.Green, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                    }
                    Text(data.descricao, lineHeight = 27.sp, color = PU.Ink)
                    if (store.internal) ClinicalFact("Animal", internalRecord?.animalName.orEmpty())
                    if (store.internal || data.profissionalResponsavel.isNotBlank()) ClinicalFact("Profissional responsável", data.profissionalResponsavel.ifBlank { "Não informado" })
                    if (store.internal || data.medicamentos.isNotBlank()) ClinicalFact("Medicamentos", data.medicamentos.ifBlank { "Não informado" })
                    if (store.internal || data.observacoes.isNotBlank()) ClinicalFact("Observações", data.observacoes.ifBlank { "Não informado" })
                }
                if (store.internal) internalRecord?.let { record -> ClinicalPanel {
                    Text("Rastreabilidade", fontWeight = FontWeight.Bold, color = PU.Green, fontSize = 20.sp)
                    Text("Registro original de ${record.registradoPor.nome}, criado em ${clinicalDate(record.criadoEm)}.", color = PU.Muted, lineHeight = 24.sp)
                    Text("Última atualização em ${clinicalDate(record.atualizadoEm)}.", color = PU.Muted, lineHeight = 24.sp)
                    Text("Versão ${record.versao}", fontSize = 13.sp, color = PU.Muted)
                    ClinicalLink("Ver histórico de correções →") { screen = ClinicalScreen.HISTORY }
                } }
            }
            ClinicalScreen.CORRECT -> if (store.internal && store.canWrite && internalRecord != null) ClinicalEditor(store, internalRecord) {
                success = "Correção salva com justificativa. O histórico anterior foi preservado."; screen = ClinicalScreen.DETAIL
            }
            ClinicalScreen.HISTORY -> if (store.internal && !store.busy && store.message == null) store.history?.let { page ->
                if (page.items.isEmpty()) ClinicalEmpty("Nenhuma correção registrada", "O conteúdo atual corresponde ao registro original.")
                page.items.forEach { correction -> ClinicalPanel {
                    Text("Correção #${correction.id} · ${clinicalDate(correction.criadoEm)}", color = PU.Foliage, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(correction.corrigidoPor.nome, color = PU.Green, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    ClinicalFact("Justificativa", correction.justificativa)
                    Text("Versão ${correction.versaoAnterior} → ${correction.versaoPosterior}", fontSize = 13.sp, color = PU.Muted)
                    ClinicalSnapshot("Antes", correction.dadosAnteriores)
                    ClinicalSnapshot("Depois", correction.dadosNovos)
                } }
                ClinicalPager(page.page, page.pages, page.total, store.busy) { next -> id?.let { store.loadHistory(it, next) } }
            }
        }
    }
}

@Composable
private fun ClinicalShell(title: String, description: String, internal: Boolean, busy: Boolean, onBack: () -> Unit, onLogout: () -> Unit, onCatalog: () -> Unit, onHome: () -> Unit, screen: ClinicalScreen, animalName: String, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(PU.Cream).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(PU.Paper).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (internal) InstitutionalMenuButton()
            if (internal) TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp)) { Text("←", fontSize = 24.sp) }
            Box(Modifier.size(34.dp).background(PU.Orange, RoundedCornerShape(10.dp)).padding(7.dp)) { PawIcon(PU.Green, Modifier.fillMaxSize()) }
            Text("Patas Unidas", fontWeight = FontWeight.Bold, color = PU.Green, fontSize = 14.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onLogout, enabled = !busy) { Text("Sair") }
        }
        if (!internal) Row(Modifier.fillMaxWidth().background(PU.Paper), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onHome, enabled = !busy) { Text("Início") }
            TextButton(onClick = onCatalog, enabled = !busy) { Text("Animais") }
            TextButton(onClick = onBack, enabled = !busy) { Text("Minha área", fontWeight = FontWeight.Bold) }
        }
        key(screen) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    ClinicalLink(if (internal) "← Voltar" else if (screen == ClinicalScreen.LIST) "← Animais adotados" else "← Histórico veterinário", !busy, onBack)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!internal) Text(if (screen == ClinicalScreen.LIST) "ANIMAIS ADOTADOS" else animalName, color = PU.Foliage, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(title, fontSize = 32.sp, lineHeight = 37.sp, letterSpacing = (-1.44).sp, fontWeight = FontWeight(750), color = PU.Green)
                        if (description.isNotBlank()) Text(description, color = PU.Muted, fontSize = 15.sp, lineHeight = 24.sp)
                    }
                }
                content()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable private fun ClinicalPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = PU.Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, PU.Border)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
@Composable private fun ClinicalAction(label: String, enabled: Boolean = true, secondary: Boolean = false, onClick: () -> Unit) =
    WorkspaceAction(label, enabled, secondary, onClick)

@Composable private fun ClinicalLink(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun ClinicalNotice(message: String, error: Boolean = false) {
    Text(message, color = if (error) MaterialTheme.colorScheme.error else PU.Green, lineHeight = 23.sp, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().background(if (error) Color(0xFFF7E9E5) else PU.CardGreen, RoundedCornerShape(10.dp)).padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
}
@Composable private fun ClinicalKind(type: ClinicalType) {
    Text(type.label, color = PU.Green, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.background(Color(0xFFE9EEE5), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp))
}
@Composable private fun ClinicalEmpty(title: String, text: String) { ClinicalPanel { Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PU.Green); Text(text, color = PU.Muted, lineHeight = 24.sp) } }
@Composable private fun ClinicalFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(label, color = PU.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(value, color = PU.Green, lineHeight = 24.sp) }
}
@Composable private fun ClinicalPager(page: Int, pages: Int, total: Long, busy: Boolean, change: (Int) -> Unit) {
    if (pages > 1) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$total registros · Página ${page + 1} de $pages", color = PU.Muted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { change(page - 1) }, enabled = !busy && page > 0) { Text("Anterior") }
            OutlinedButton(onClick = { change(page + 1) }, enabled = !busy && page + 1 < pages) { Text("Próxima") }
        }
    }
}

@Composable private fun ClinicalTypeField(value: ClinicalType?, enabled: Boolean, includeAll: Boolean = false, onChange: (ClinicalType?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Tipo", color = PU.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Box {
            OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), border = BorderStroke(1.dp, Color(0xFFC9D5C9)), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = PU.Ink)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(value?.label ?: "Todos"); Text("⌄") }
            }
            DropdownMenu(open, onDismissRequest = { open = false }) {
                if (includeAll) DropdownMenuItem(text = { Text("Todos") }, onClick = { open = false; onChange(null) })
                clinicalKinds.forEach { type -> DropdownMenuItem(text = { Text(type.label) }, onClick = { open = false; onChange(type) }) }
            }
        }
    }
}

@Composable private fun ClinicalDateField(label: String, value: String, enabled: Boolean, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = PU.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = value, onValueChange = onChange, enabled = enabled, textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, lineHeight = 22.sp), placeholder = { Text("AAAA-MM-DD", fontSize = 12.sp) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = clinicalFieldColors(), trailingIcon = {
            IconButton(onClick = {
                val initial = try { LocalDate.parse(value) } catch (_: Exception) { LocalDate.now() }
                DatePickerDialog(context, { _, year, month, day -> onChange("%04d-%02d-%02d".format(year, month + 1, day)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
            }, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Escolher $label" }) { Text("▦", color = PU.Foliage, fontSize = 22.sp) }
        })
    }
}
@Composable private fun clinicalFieldColors() = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, disabledContainerColor = PU.Paper, unfocusedBorderColor = Color(0xFFC9D5C9))

@Composable private fun ClinicalFilters(store: ProntuarioStore) {
    var from by rememberSaveable(store.animalId, store.de) { mutableStateOf(store.de) }
    var until by rememberSaveable(store.animalId, store.ate) { mutableStateOf(store.ate) }
    var error by remember { mutableStateOf<String?>(null) }
    ClinicalPanel {
        ClinicalTypeField(store.typeFilter, !store.busy, true) { store.filters(it, store.de, store.ate) }
        ClinicalDateField("Data inicial", from, !store.busy) { from = it; error = null }
        ClinicalDateField("Data final", until, !store.busy) { until = it; error = null }
        error?.let { ClinicalNotice(it, true) }
        ClinicalAction("Filtrar", !store.busy, true) {
            fun valid(value: String) = value.isEmpty() || try { value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && LocalDate.parse(value).year in 1000..9999 } catch (_: Exception) { false }
            error = when { !valid(from) || !valid(until) -> "Informe datas válidas no formato AAAA-MM-DD."; from.isNotEmpty() && until.isNotEmpty() && until < from -> "A data final deve ser igual ou posterior à inicial."; else -> null }
            if (error == null) store.filters(store.typeFilter, from, until)
        }
        if (store.typeFilter != null || from.isNotBlank() || until.isNotBlank()) ClinicalLink("Limpar filtros", !store.busy) { from = ""; until = ""; error = null; store.filters(null, "", "") }
    }
}

@Composable private fun ClinicalTextField(label: String, value: String, limit: Int, busy: Boolean, multiline: Boolean = false, onChange: (String) -> Unit) {
    val count = value.codePointCount(0, value.length)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = PU.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value, onValueChange = onChange, enabled = !busy, textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, lineHeight = 22.sp), modifier = Modifier.fillMaxWidth(), singleLine = !multiline, minLines = if (multiline) if (limit == 10000) 5 else 3 else 1, isError = count > limit, shape = RoundedCornerShape(10.dp), colors = clinicalFieldColors())
        if (count > limit) Text("$count / $limit caracteres", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.End))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ClinicalEditor(store: ProntuarioStore, initial: InstitutionalRecord?, done: (Long) -> Unit) {
    // Key only by identity: a conflict response must never replace the in-progress draft.
    var baseline by remember(initial?.id) { mutableStateOf(initial) }
    var draft by remember(initial?.id) { mutableStateOf(initial?.data ?: ClinicalForm()) }
    var reason by rememberSaveable(initial?.id) { mutableStateOf("") }
    var error by remember(initial?.id) { mutableStateOf<String?>(null) }
    var reviewed by remember(initial?.id) { mutableStateOf(false) }
    val actionRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null) actionRequester.bringIntoView() }
    val remote = store.conflictRemote?.takeIf { it.id == initial?.id }
    ClinicalPanel {
        if (initial != null) Text("A correção atualiza os dados clínicos e preserva os valores anteriores no histórico. O autor original permanece identificado.", color = PU.Muted, fontSize = 13.sp, lineHeight = 21.sp)
        if (store.conflictPending && remote == null && baseline != null) {
            ClinicalNotice("O registro mudou. Suas alterações foram preservadas, mas ainda é necessário consultar e revisar a versão atual antes de salvar novamente.", true)
            ClinicalAction("Consultar versão atual", !store.busy, true) {
                baseline?.let { store.retryConflict(it.id) }
            }
        }
        if (remote != null) {
            ClinicalNotice("Este registro mudou desde que foi aberto. Suas alterações foram preservadas. Compare os dados e revise a versão atual antes de salvar novamente.", true)
            ClinicalSnapshot("Suas alterações", draft)
            ClinicalSnapshot("Versão atual · ${remote.versao}", remote.data)
            ClinicalAction("Revisar versão atual", !store.busy, true) {
                draft = rebaseClinicalDraft(requireNotNull(baseline).data, draft, remote.data)
                baseline = remote
                store.clearConflict()
                error = null
                reviewed = true
            }
        }
        if (reviewed) ClinicalNotice("Versão atual carregada. Os campos que você alterou foram mantidos. Revise o formulário e confirme em Salvar correção.")
        ClinicalTypeField(draft.tipo, !store.busy) { type -> if (type != null) { draft = draft.copy(tipo = type); error = null } }
        ClinicalDateField("Data do procedimento", draft.dataProcedimento, !store.busy) { draft = draft.copy(dataProcedimento = it); error = null }
        ClinicalTextField("Título", draft.titulo, 120, store.busy) { draft = draft.copy(titulo = it); error = null }
        ClinicalTextField("Descrição clínica", draft.descricao, 10000, store.busy, true) { draft = draft.copy(descricao = it); error = null }
        ClinicalTextField("Profissional responsável (opcional)", draft.profissionalResponsavel, 120, store.busy) { draft = draft.copy(profissionalResponsavel = it); error = null }
        ClinicalTextField("Medicamentos (opcional)", draft.medicamentos, 5000, store.busy, true) { draft = draft.copy(medicamentos = it); error = null }
        ClinicalTextField("Observações (opcional)", draft.observacoes, 5000, store.busy, true) { draft = draft.copy(observacoes = it); error = null }
        if (initial != null) ClinicalTextField("Justificativa da correção", reason, 2000, store.busy, true) { reason = it; error = null }
        Column(Modifier.bringIntoViewRequester(actionRequester), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            error?.let { ClinicalNotice(it, true) }
            ClinicalAction(if (store.busy) "Salvando…" else if (initial == null) "Registrar procedimento" else "Salvar correção", !store.busy && remote == null && !store.conflictPending) {
                if (!store.busy) {
                    val original = baseline
                    error = draft.validate() ?: if (original != null) when {
                        reason.isBlank() -> "Informe a justificativa da correção."
                        reason.trim().codePointCount(0, reason.trim().length) > 2000 -> "A justificativa deve ter até 2.000 caracteres."
                        original.data.normalized() == draft.normalized() -> "Altere ao menos um campo clínico antes de salvar."
                        else -> null
                    } else null
                    if (error == null) {
                        if (original == null) store.create(draft, done)
                        else store.correct(original, draft, reason) { done(original.id) }
                    }
                }
            }
        }
    }
}

@Composable private fun ClinicalSnapshot(title: String, form: ClinicalForm) {
    Surface(color = PU.Paper, shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, PU.Border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(title, color = PU.Green, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            ClinicalFact("Tipo", form.tipo.label)
            ClinicalFact("Título", form.titulo)
            ClinicalFact("Descrição", form.descricao)
            ClinicalFact("Data", clinicalDate(form.dataProcedimento))
            if (form.profissionalResponsavel.isNotBlank()) ClinicalFact("Profissional", form.profissionalResponsavel)
            if (form.medicamentos.isNotBlank()) ClinicalFact("Medicamentos", form.medicamentos)
            if (form.observacoes.isNotBlank()) ClinicalFact("Observações", form.observacoes)
        }
    }
}
