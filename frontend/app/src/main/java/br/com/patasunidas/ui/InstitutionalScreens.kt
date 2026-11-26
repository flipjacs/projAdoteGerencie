package br.com.patasunidas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.patasunidas.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun InstitutionalArea(user: UserIdentity, token: String, api: InstitutionalApi, sessions: SessionController, onLogout: () -> Unit, onBack: () -> Unit, onPublicTransparency:()->Unit={}) {
    val global = user.perfil == UserProfile.ADMIN_PLATAFORMA
    val root = if (global) "/api/plataforma/ongs" else "/api/ong/funcionarios"
    var page by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var editing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    var action by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var credential by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    fun execute(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        scope.launch {
            try { block() }
            catch(e: CancellationException) { throw e }
            catch(e: InstitutionalHttpException) {
                message = institutionalMessage(e.status)
                if(e.status == 401) sessions.unauthorized()
                if(e.status == 403) sessions.operationForbidden()
            }
            catch(_: Exception) { message = "Não foi possível conectar ao serviço. Tente novamente." }
            finally { busy = false }
        }
    }
    fun back() {
        if (busy) return
        when { editing || creating -> { editing = false; creating = false }
            selected != null -> { selected = null; revision++ }
            else -> onBack() }
        message = null; credential = null
    }
    BackHandler { back() }
    LaunchedEffect(page, revision) {
        execute { result = api.request("GET", "$root?page=$page&size=20", token) }
    }
    Column(Modifier.fillMaxSize().background(PU.Cream).imePadding()) {
        Row(Modifier.fillMaxWidth().background(PU.Green).statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (!global) InstitutionalMenuButton(Color.White)
            Text(if(global) "Criador da Plataforma" else "Área da ONG", color = Color.White, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onLogout, enabled = !busy) { Text("Sair", color = Color.White) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if(global) TextButton(onClick=onPublicTransparency){Text("Transparência pública")}
            if(!global || selected != null || creating || editing) TextButton(onClick = ::back, enabled = !busy) { Text("← Voltar") }
            Text(if(creating) if(global) "Cadastrar ONG" else "Cadastrar funcionário" else if(editing) "Editar dados" else selected?.optString("nome") ?: if(global) "Organizações da plataforma" else "Funcionários", style = MaterialTheme.typography.headlineMedium, color = PU.Green)
            if(selected == null && !creating) Text(if(global) "Cadastre e acompanhe as ONGs e seus responsáveis." else "Equipe vinculada à sua ONG.", color = PU.Muted)
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { if(selected == null) revision++ else execute { selected = api.request("GET", "$root/${selected!!.getInt("id")}", token); editing = false } }, enabled = !busy) { Text("Recarregar dados") } }
            when {
                creating || editing -> InstitutionalForm(global, if(editing) selected else null, busy) { fields ->
                    execute {
                        val saved = api.request(if(creating) "POST" else "PUT", if(creating) root else "$root/${selected!!.getInt("id")}", token, fields)
                        selected = saved; editing = false; creating = false; message = "Dados salvos."; revision++
                    }
                }
                selected != null -> {
                    val item = selected!!; val id = item.getInt("id")
                    Surface(color = PU.Paper, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if(global) "Dados institucionais" else "Dados da conta", style = MaterialTheme.typography.titleLarge, color = PU.Green)
                            if(global) DemoOngLogo(id)
                            Text(item.getString("email"))
                            if(global) {
                                Text("Situação: ${item.getString("situacao")}")
                                Text(listOf("cidade", "estado").mapNotNull { key -> item.optString(key).takeUnless { it.isBlank() || it == "null" } }.joinToString(", ").ifBlank { "Localidade não informada" })
                            } else {
                                Text("Perfil: ${item.getString("perfil")}")
                                Text(if(item.getBoolean("ativo")) "Conta ativa" else "Conta inativa")
                                Text(if(item.getBoolean("credencialPendente")) "Credencial pendente" else "Primeiro acesso concluído")
                            }
                            Button(onClick = { editing = true }, enabled = !busy) { Text("Editar dados") }
                            OutlinedButton(onClick = { reason = ""; action = "status" }, enabled = !busy) { Text(if(global) if(item.getString("situacao") == "ATIVA") "Suspender ONG" else "Reativar ONG" else if(item.getBoolean("ativo")) "Desativar conta" else "Reativar conta") }
                            if(global) OutlinedButton(onClick = { execute { val admin = api.request("GET", "$root/$id/administrador-inicial", token); message = "Administrador inicial #${admin.getInt("id")} · ${if(admin.getBoolean("credencialPendente")) "Credencial pendente" else "Credencial definida"}" } }, enabled = !busy) { Text("Consultar administrador inicial") }
                            if(!global) OutlinedButton(onClick = { reason = ""; action = "perfil" }, enabled = !busy) { Text("Alterar perfil") }
                            OutlinedButton(onClick = { reason = ""; action = "credencial" }, enabled = !busy) { Text(if(global) "Credencial do administrador inicial" else "Emitir credencial temporária") }
                        }
                    }
                    if(action != null) {
                        var profile by remember(action) { mutableStateOf("VOLUNTARIO") }
                        AlertDialog(onDismissRequest = { if(!busy) action = null }, title = { Text("Confirmar operação") }, text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Confirme a operação para ${item.getString("nome")}.")
                                if(reason.isBlank()) Text("Informe o motivo para confirmar.", color = PU.Muted)
                                OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("Motivo") }, enabled = !busy)
                                if(action == "perfil") ProfilePicker(profile, { profile = it }, !busy)
                            }
                        }, confirmButton = { TextButton(enabled = !busy, onClick = {
                            val operation = action
                            if(reason.isBlank()) { message = "Informe o motivo da operação."; return@TextButton }
                            execute {
                                val versioned = JSONObject().put("versao", item.getLong("versao")).put("motivo", reason)
                                when(operation) {
                                    "credencial" -> {
                                        val path = if(global) "$root/$id/administrador-inicial" else "$root/$id"
                                        val version = if(global) api.request("GET", path, token).getLong("versao") else item.getLong("versao")
                                        credential = api.request("POST", "$path/credencial-temporaria", token, versioned.put("versao", version))
                                    }
                                    "perfil" -> selected = api.request("PATCH", "$root/$id/perfil", token, versioned.put("perfil", profile))
                                    else -> {
                                        val suffix = if(global) if(item.getString("situacao") == "ATIVA") "suspensao" else "reativacao" else if(item.getBoolean("ativo")) "desativacao" else "reativacao"
                                        selected = api.request("PATCH", "$root/$id/$suffix", token, if(global) if(suffix == "suspensao") JSONObject().put("motivo", reason) else null else versioned)
                                    }
                                }
                                action = null; message = "Operação concluída."; revision++
                                selected = api.request("GET", "$root/$id", token)
                            }
                        }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = { action = null }, enabled = !busy) { Text("Cancelar") } })
                    }
                }
                else -> {
                    Button(onClick = { creating = true }, enabled = !busy) { Text(if(global) "Cadastrar ONG" else "Cadastrar funcionário") }
                    val items = result?.optJSONArray("content")
                    if(items != null && items.length() == 0) Text(if(global) "Nenhuma ONG cadastrada." else "Nenhum funcionário cadastrado.", color = PU.Muted)
                    if(items != null) for(i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        Surface(color = PU.Paper, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if(global) DemoOngLogo(item.optInt("id"))
                                Text(item.getString("nome"), style = MaterialTheme.typography.titleMedium, color = PU.Green)
                                Text(item.getString("email"), color = PU.Muted)
                                Text(if(global) item.getString("situacao") else "${item.getString("perfil")} · ${if(item.getBoolean("ativo")) "Ativo" else "Inativo"}")
                                TextButton(onClick = { execute { selected = api.request("GET", "$root/${item.getInt("id")}", token) } }, enabled = !busy) { Text(if(global) "Gerenciar ONG" else "Ver detalhes →") }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { page-- }, enabled = !busy && page > 0) { Text("Anterior") }
                        Text("Página ${page+1}")
                        TextButton(onClick = { page++ }, enabled = !busy && page+1 < (result?.optInt("totalPages") ?: 0)) { Text("Próxima") }
                    }
                }
            }
        }
    }
    credential?.let { value -> AlertDialog(onDismissRequest = { credential = null }, title = { Text("Credencial temporária") }, text = { Column { Text(value.getString("senhaTemporaria")); Text("Expira em ${value.getString("expiraEm")}. Entregue ao responsável para definir sua senha no primeiro acesso.") } }, confirmButton = { TextButton(onClick = { credential = null }) { Text("Fechar") } }) }
}

@Composable
private fun ProfilePicker(value: String, change: (String) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text("Perfil: $value") }; DropdownMenu(expanded, { expanded = false }) { listOf("VOLUNTARIO", "VETERINARIO", "ADMINISTRADOR").forEach { profile -> DropdownMenuItem(text = { Text(profile) }, onClick = { change(profile); expanded = false }) } } }
}

@Composable
private fun InstitutionalForm(global: Boolean, initial: JSONObject?, busy: Boolean, save: (JSONObject) -> Unit) {
    val keys = if(global) listOf("nome", "email", "identificacao", "telefone", "cidade", "estado") else if(initial == null) listOf("nome", "email") else listOf("nome")
    val values = remember(initial) { mutableStateMapOf<String,String>().apply { keys.forEach { put(it, initial?.optString(it)?.takeUnless { it == "null" } ?: "") }; put("adminNome", ""); put("adminEmail", "") } }
    var profile by remember { mutableStateOf("VOLUNTARIO") }
    var error by remember { mutableStateOf<String?>(null) }
    Surface(color = PU.Paper, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val fields = keys + if(global && initial == null) listOf("adminNome", "adminEmail") else emptyList()
            val labels = mapOf("nome" to if(global) "Nome da ONG" else "Nome", "email" to "E-mail", "identificacao" to "CNPJ ou CPF", "telefone" to "Telefone", "cidade" to "Cidade", "estado" to "Estado (UF)", "adminNome" to "Nome do primeiro administrador", "adminEmail" to "E-mail do primeiro administrador")
            fields.forEach { key -> OutlinedTextField(values[key] ?: "", { values[key] = it.take(when(key) { "nome" -> if(global) 120 else 100; "adminNome" -> 100; "cidade" -> 80; "estado" -> 2; "telefone", "identificacao" -> 20; else -> 120 }) }, label = { Text(labels[key]!!) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            if(!global && initial == null) ProfilePicker(profile, { profile = it }, !busy)
            if(global && initial == null) Text("A ONG será criada ativa. O administrador precisará receber uma credencial temporária e definir sua senha.", color = PU.Muted)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                if(values["nome"].isNullOrBlank() || ("email" in keys && !validEmail(values["email"].orEmpty().trim())) || (global && initial == null && (values["adminNome"].isNullOrBlank() || !validEmail(values["adminEmail"].orEmpty().trim())))) { error = "Preencha o nome e os e-mails válidos." }
                else {
                    val body = JSONObject(); keys.forEach { key -> body.put(key, values[key]?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL) }
                    if(initial != null) body.put("versao", initial.getLong("versao"))
                    else if(global) body.put("administrador", JSONObject().put("nome", values["adminNome"]!!.trim()).put("email", values["adminEmail"]!!.trim())) else body.put("perfil", profile)
                    save(body)
                }
            }, enabled = !busy) { Text(if(busy) "Salvando…" else if(initial != null) "Salvar alterações" else if(global) "Cadastrar ONG" else "Cadastrar funcionário") }
        }
    }
}
