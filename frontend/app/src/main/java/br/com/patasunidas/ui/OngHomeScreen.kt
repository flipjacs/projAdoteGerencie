package br.com.patasunidas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.data.UserIdentity

@Composable
fun OngHomeScreen(user: UserIdentity, onLogout: () -> Unit, onAnimals: () -> Unit, onEmployees: () -> Unit = {}, onAdoptions: () -> Unit = {}, onFinance: () -> Unit = {}, onPublicTransparency:()->Unit={}) {
    Column(Modifier.fillMaxSize().background(PU.Cream)) {
        Row(Modifier.fillMaxWidth().background(PU.Green).statusBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Brand()
            Text("Sair", color = Color.White, modifier = Modifier.clickable(onClick = onLogout).padding(12.dp), fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("Área da ONG", color = PU.Ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            Spacer(Modifier.height(12.dp))
            Text("Olá, ${user.nome}", color = PU.Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text("Perfil: ${user.perfil.name.lowercase().replaceFirstChar { it.uppercase() }}", color = PU.Muted, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(if (user.hasOng) "ONG vinculada: ${user.ongId}" else "Sua conta não possui uma ONG vinculada. Procure um administrador.", color = PU.Ink, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            if (user.hasOng) { FilledButton("Animais da ONG", onAnimals); Spacer(Modifier.height(24.dp)) }
            if (user.hasOng && br.com.patasunidas.data.canManageEmployees(user.perfil)) {
                FilledButton("Funcionários", onEmployees)
            }
            if (user.hasOng && br.com.patasunidas.adocoes.canManageAdoptions(user.perfil)) { Spacer(Modifier.height(24.dp)); FilledButton("Solicitações de adoção", onAdoptions) }
            if(user.hasOng && br.com.patasunidas.financeiro.canManageFinance(user.perfil)) { Spacer(Modifier.height(24.dp)); FilledButton("Financeiro e doações", onFinance) }
            Spacer(Modifier.height(24.dp)); FilledButton("Transparência pública",onPublicTransparency)
            Text("Os dados completos da instituição ainda não estão disponíveis nesta área.", color = PU.Muted, modifier = Modifier.padding(top = 20.dp))
        }
    }
}
