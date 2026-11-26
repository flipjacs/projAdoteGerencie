package br.com.patasunidas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import br.com.patasunidas.data.*
import java.io.IOException

@Composable
private fun AuthLayout(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(PU.Cream).imePadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().background(PU.Green).statusBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text("Patas ", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Unidas", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PU.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = PU.Muted)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun AuthField(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false, email: Boolean = false, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (email) KeyboardType.Email else KeyboardType.Text),
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) ({ TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Mostrar") } }) else null,
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PU.Paper, unfocusedContainerColor = PU.Paper))
}

@Composable
private fun AuthButton(text: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Feedback(message: String?) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun LoginScreen(onLogin: (String, String, Boolean) -> Unit, onRegister: () -> Unit, onSoon: () -> Unit,
    onFirstAccess: () -> Unit = {}, onExplore: () -> Unit = {}, busy: Boolean = false, loginBlocked: Boolean = false, message: String? = null,
    retryIdentity: Boolean = false, onRetryIdentity: () -> Unit = {}, onCancelIdentity: () -> Unit = {}) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AuthLayout("Entre na sua conta", "Use o e-mail e a senha cadastrados no Patas Unidas.") {
        AuthField("E-mail", email, { email = it }, email = true, enabled = !busy && !retryIdentity)
        AuthField("Senha", password, { password = it }, password = true, enabled = !busy && !retryIdentity)
        Feedback(message)
        AuthButton(if (busy) "Consultando sua conta..." else if (loginBlocked) "Aguarde para tentar novamente" else if (retryIdentity) "Tentar novamente" else "Entrar", busy, !loginBlocked) {
            if (retryIdentity) onRetryIdentity() else { val submitted = password; password = ""; onLogin(email.trim(), submitted, false) }
        }
        if (retryIdentity) TextButton(onClick = onCancelIdentity) { Text("Cancelar e voltar ao login") }
        TextButton(onClick = onExplore, enabled = !busy) { Text("Explorar animais sem entrar") }
        TextButton(onClick = onRegister, enabled = !busy && !retryIdentity) { Text("Ainda não tem uma conta? Cadastre-se") }
        TextButton(onClick = onFirstAccess, enabled = !busy && !retryIdentity) { Text("Recebeu uma credencial? Definir senha") }
    }
}

internal fun validEmail(value: String) = value.length <= 120 && value.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
internal fun validPassword(value: String) = value.isNotBlank() && value.length in 12..72 && value.toByteArray(Charsets.UTF_8).size <= 72

@Composable
fun FirstAccessScreen(api: AuthApi, onBack: () -> Unit, onSuccess: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var temporary by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AuthLayout("Defina sua senha", "Use o e-mail e a credencial temporária recebida para ativar sua conta.") {
        AuthField("E-mail", email, { email = it }, email = true, enabled = !busy)
        AuthField("Senha temporária", temporary, { temporary = it }, password = true, enabled = !busy)
        AuthField("Nova senha", password, { password = it }, password = true, enabled = !busy)
        Text("Use de 12 a 72 caracteres e no máximo 72 bytes.", style = MaterialTheme.typography.bodySmall, color = PU.Muted)
        Feedback(message)
        AuthButton(if (busy) "Salvando..." else "Definir senha", busy) {
            val normalized = email.trim()
            message = when {
                !validEmail(normalized) -> "Informe um e-mail válido."
                temporary.isBlank() || temporary.length > 72 || temporary.toByteArray(Charsets.UTF_8).size > 72 -> "Informe uma credencial temporária válida."
                !validPassword(password) -> "A senha deve ter de 12 a 72 caracteres e no máximo 72 bytes."
                temporary == password -> "Escolha uma senha diferente da temporária."
                else -> null
            }
            if (message == null) {
                busy = true
                scope.launch {
                    try { api.firstAccess(normalized, temporary, password); temporary = ""; password = ""; onSuccess() }
                    catch (_: InvalidTemporaryCredentialException) { message = "Credencial inválida ou expirada." }
                    catch (_: InvalidRegistrationException) { message = "Confira os dados informados." }
                    catch (_: LoginRateLimitedException) { message = "Muitas tentativas. Aguarde antes de tentar novamente." }
                    catch (_: IOException) { message = "Não foi possível conectar à API. Tente novamente." }
                    catch (_: Exception) { message = "Não foi possível definir a senha. Tente novamente." }
                    finally { busy = false }
                }
            }
        }
        TextButton(onClick = onBack, enabled = !busy) { Text("Voltar para entrar") }
    }
}

@Composable
fun RegistrationForm(submitting: Boolean, onRegister: (String, String, String) -> Unit, onBack: () -> Unit, onMessage: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    AuthLayout("Crie sua conta", "Uma conta para acompanhar os animais do Patas Unidas.") {
        AuthField("Nome completo", name, { name = it }, enabled = !submitting)
        AuthField("E-mail", email, { email = it }, email = true, enabled = !submitting)
        AuthField("Senha", password, { password = it }, password = true, enabled = !submitting)
        Text("Use de 12 a 72 caracteres e no máximo 72 bytes.", style = MaterialTheme.typography.bodySmall, color = PU.Muted)
        AuthField("Confirmar senha", confirmation, { confirmation = it }, password = true, enabled = !submitting)
        Feedback(message)
        AuthButton(if (submitting) "Criando conta..." else "Criar conta", submitting) {
            val normalized = email.trim().lowercase()
            message = when {
                name.isBlank() || name.trim().length > 100 -> "Informe um nome de até 100 caracteres."
                !validEmail(normalized) -> "Informe um e-mail válido."
                !validPassword(password) -> "A senha deve ter de 12 a 72 caracteres e no máximo 72 bytes."
                password != confirmation -> "As senhas não coincidem."
                else -> null
            }
            if (message == null) onRegister(name.trim(), normalized, password)
        }
        TextButton(onClick = onBack, enabled = !submitting) { Text("Já possui uma conta? Entrar") }
    }
}

@Composable
fun PlatformHomeScreen(name: String, onLogout: () -> Unit) {
    AuthLayout("Criador da Plataforma", "Olá, $name. Sua conta foi autenticada.") {
        Text("A administração da plataforma ainda não está disponível no aplicativo.", color = PU.Muted)
        AuthButton("Sair", false, onClick = onLogout)
    }
}
