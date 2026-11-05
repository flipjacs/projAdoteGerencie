package br.com.patasunidas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CadastroScreen(
    submitting: Boolean,
    onRegister: (nome: String, email: String, senha: String) -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    var nome by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmacao by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(PU.Cream)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PU.Green)
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)
        ) {
            Brand()
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Crie sua conta",
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Acompanhe os animais e faça parte do Patas Unidas.",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }

        Spacer(Modifier.height(20.dp))
        FieldLabel("Nome completo")
        Spacer(Modifier.height(6.dp))
        PuTextField(nome, { nome = it }, "Seu nome completo", Modifier.padding(horizontal = 24.dp))

        Spacer(Modifier.height(20.dp))
        FieldLabel("E-mail")
        Spacer(Modifier.height(6.dp))
        PuTextField(email, { email = it }, "voce@email.com", Modifier.padding(horizontal = 24.dp), keyboardType = KeyboardType.Email)

        Spacer(Modifier.height(20.dp))
        FieldLabel("Senha")
        Spacer(Modifier.height(6.dp))
        PuTextField(senha, { senha = it }, "Sua senha", Modifier.padding(horizontal = 24.dp), password = true)

        Spacer(Modifier.height(20.dp))
        FieldLabel("Confirmar senha")
        Spacer(Modifier.height(6.dp))
        PuTextField(confirmacao, { confirmacao = it }, "Repita sua senha", Modifier.padding(horizontal = 24.dp), password = true)

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.weight(1f))
        FilledButton(
            text = if (submitting) "Criando conta..." else "Criar conta",
            onClick = {
                if (!submitting) {
                    val nomeLimpo = nome.trim()
                    val emailLimpo = email.trim().lowercase()
                    when {
                        nomeLimpo.isEmpty() || emailLimpo.isEmpty() || senha.isBlank() || confirmacao.isBlank() -> onMessage("Preencha todos os campos")
                        nomeLimpo.length > 100 -> onMessage("Nome muito longo")
                        emailLimpo.length > 120 || !emailLimpo.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) -> onMessage("E-mail inválido")
                        senha.length !in 12..72 -> onMessage("A senha deve ter entre 12 e 72 caracteres")
                        senha != confirmacao -> onMessage("As senhas não coincidem")
                        else -> onRegister(nomeLimpo, emailLimpo, senha)
                    }
                }
            },
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Já possui uma conta? ", color = PU.Ink, fontSize = 14.sp)
            LinkText("Entrar", onClick = onBack)
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}
