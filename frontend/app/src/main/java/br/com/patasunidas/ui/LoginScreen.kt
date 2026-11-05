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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tela de login com dois modos (alternados pelo seletor):
 *  - Adotante ou padrinho
 *  - Equipe da ONG
 * A autenticação usa a API do SysAnimais.
 */
@Composable
fun LoginScreen(
    onLogin: (email: String, senha: String, isTeam: Boolean) -> Unit,
    onRegister: () -> Unit,
    onSoon: () -> Unit
) {
    var isTeam by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var keep by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(PU.Cream)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Cabeçalho verde
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
                text = "Bem-vindo de volta!",
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isTeam) {
                    "Acesse o painel para gerenciar animais, adoções, apadrinhamentos e a saúde dos resgatados."
                } else {
                    "Acompanhe seus pedidos e apadrinhamentos, ou entre como equipe da ONG."
                },
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }

        Spacer(Modifier.height(10.dp))
        SegmentedToggle(
            isTeam = isTeam,
            onChange = { isTeam = it },
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel(if (isTeam) "E-mail institucional" else "E-mail")
        Spacer(Modifier.height(6.dp))
        PuTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = if (isTeam) "equipe@suaong.org.br" else "voce@email.com",
            keyboardType = KeyboardType.Email,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(20.dp))
        FieldLabel("Senha")
        Spacer(Modifier.height(6.dp))
        PuTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Sua senha",
            password = true,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // Empurra o rodapé para o fim da tela
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CheckRow(checked = keep, onChange = { keep = it }, label = "Manter conectado")
            LinkText("Esqueci a senha", onClick = onSoon, fontSize = 13)
        }

        Spacer(Modifier.height(14.dp))
        FilledButton(
            text = if (isTeam) "Entrar no painel" else "Entrar",
            onClick = { onLogin(email.trim(), password, isTeam) },
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Depois de entrar, você volta para os animais.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            color = PU.Muted,
            fontSize = 14.sp,
            textAlign = if (isTeam) TextAlign.Start else TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isTeam) "Sua ONG ainda não tem cadastro? " else "Não tem uma conta? ",
                color = PU.Ink,
                fontSize = 14.sp
            )
            LinkText(
                text = if (isTeam) "Cadastrar ONG" else "Cadastre-se",
                onClick = if (isTeam) onSoon else onRegister
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Use o e-mail e a senha da sua conta.",
            modifier = Modifier.padding(horizontal = 24.dp),
            color = PU.Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

