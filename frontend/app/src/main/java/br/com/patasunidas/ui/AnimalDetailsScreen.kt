package br.com.patasunidas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.data.DemoAnimal

@Composable
fun AnimalDetailsScreen(animal: DemoAnimal, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(PU.Cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(PU.Green)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Color.White)
                    .semantics { contentDescription = "Voltar para animais" }
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = PU.Green, fontSize = 26.sp)
            }
            Text(animal.name, color = Color.White, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, fontSize = 25.sp)
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Image(
                painter = painterResource(animal.photo),
                contentDescription = "Foto ilustrativa de ${animal.species.label.lowercase()}: ${animal.name}",
                modifier = Modifier.fillMaxWidth().height(198.dp),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(animal.status.uppercase(), color = PU.Green, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(3.dp))
                Text(animal.name, color = PU.Ink, fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, fontSize = 31.sp)
                Spacer(Modifier.height(12.dp))
                Text(animal.description, color = PU.Muted, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailInfo("Espécie", animal.species.label, Modifier.weight(1f))
                    DetailInfo("Porte", animal.size, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailInfo("Idade", animal.age, Modifier.weight(1f))
                    DetailInfo("Sexo", animal.sex, Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                SerifTitle("Resumo de saúde", size = 22)
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    HealthInfo("Vacinas", animal.health.vaccines)
                    HorizontalDivider(color = PU.Divider)
                    HealthInfo("Castração", animal.health.neutering)
                    HorizontalDivider(color = PU.Divider)
                    HealthInfo("Vermifugação", animal.health.deworming)
                    HorizontalDivider(color = PU.Divider)
                    HealthInfo("Exames", animal.health.exams)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DetailInfo(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(15.dp)).background(Color.White)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, color = PU.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = PU.Ink, fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun HealthInfo(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, color = PU.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = PU.Muted, fontSize = 13.sp)
    }
}
