package br.com.patasunidas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.data.DemoAnimal
import br.com.patasunidas.data.DemoAnimals
import br.com.patasunidas.data.Species

enum class AnimalFilter(val label: String) { ALL("Todos"), DOGS("Cães"), CATS("Gatos") }

@Composable
fun AnimalsScreen(
    filter: AnimalFilter,
    onFilterChange: (AnimalFilter) -> Unit,
    onAnimalClick: (String) -> Unit,
    onHome: () -> Unit,
    onSoon: () -> Unit
) {
    val animals = remember(filter) {
        DemoAnimals.all.filter {
            when (filter) {
                AnimalFilter.ALL -> true
                AnimalFilter.DOGS -> it.species == Species.DOG
                AnimalFilter.CATS -> it.species == Species.CAT
            }
        }
    }
    Column(Modifier.fillMaxSize().background(PU.Cream)) {
        Box(
            Modifier.fillMaxWidth().background(PU.Green).statusBarsPadding()
                .padding(vertical = 23.dp, horizontal = 24.dp)
        ) {
            Text("Animais", color = Color.White, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, fontSize = 30.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimalFilter.entries.forEach { option ->
                val selected = option == filter
                Box(
                    Modifier.clip(RoundedCornerShape(22.dp))
                        .background(if (selected) PU.Green else Color.White)
                        .border(1.dp, if (selected) PU.Green else PU.Border, RoundedCornerShape(22.dp))
                        .clickable(role = Role.Tab) { onFilterChange(option) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(option.label, color = if (selected) Color.White else PU.Muted, fontSize = 14.sp)
                }
            }
        }
        Text(
            "${animals.size} animais disponíveis (dados de exemplo)",
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 10.dp),
            color = PU.Muted,
            fontSize = 13.sp
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(animals, key = { it.id }) { animal ->
                AnimalListCard(animal) { onAnimalClick(animal.id) }
            }
        }
        AppBottomNav(selected = 1, onHome = onHome, onAnimals = {}, onSoon = onSoon)
    }
}

@Composable
private fun AnimalListCard(animal: DemoAnimal, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().height(112.dp)
            .shadow(2.dp, shape).clip(shape).background(Color.White)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Image(
            painter = painterResource(animal.photo),
            contentDescription = "Foto ilustrativa de ${animal.species.label.lowercase()}: ${animal.name}",
            modifier = Modifier.size(112.dp),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 10.dp, top = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(animal.name, modifier = Modifier.weight(1f), color = PU.Ink,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(animal.age, color = PU.Muted, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            Text("${animal.species.label}, ${animal.size.lowercase()}, ${animal.sex.lowercase()}",
                color = PU.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(animal.health.summary, color = PU.Green, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
