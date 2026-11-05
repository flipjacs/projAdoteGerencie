package br.com.patasunidas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.data.DemoAnimal
import br.com.patasunidas.data.DemoAnimals

@Composable
fun HomeScreen(onAnimals: () -> Unit, onAnimalClick: (String) -> Unit, onSoon: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PU.Cream)
    ) {
        // Cabeçalho verde
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PU.Green)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Brand()
        }

        // Conteúdo rolável
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "ADOÇÃO E APADRINHAMENTO",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = PU.Green,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Um lar para quem foi resgatado. Contas claras\npara quem ajuda.",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = PU.Ink,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 31.sp,
                lineHeight = 34.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Veja os animais que esperam por uma família e envie o pedido direto para a ONG responsável.",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = PU.Muted,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            FilledButton(
                "Ver animais para adoção",
                onClick = onAnimals,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(15.dp))
            OutlinedPrimaryButton(
                "Quero apadrinhar",
                onClick = onSoon,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(18.dp))
            SerifTitle("Esperando por você", Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(DemoAnimals.all, key = { _, animal -> animal.id }) { _, animal ->
                    AnimalCard(animal) { onAnimalClick(animal.id) }
                }
            }
            Spacer(Modifier.height(14.dp))
            SerifTitle("Como adotar", Modifier.padding(horizontal = 24.dp), size = 20)
            Spacer(Modifier.height(24.dp))
        }

        AppBottomNav(selected = 0, onHome = {}, onAnimals = onAnimals, onSoon = onSoon)
    }
}

@Composable
private fun AnimalCard(animal: DemoAnimal, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .width(150.dp)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(Color.White)
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(animal.photo),
            contentDescription = "Foto ilustrativa de ${animal.species.label.lowercase()}: ${animal.name}",
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            contentScale = ContentScale.Crop
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(41.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                animal.name,
                color = PU.Ink,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(animal.age, color = PU.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun AppBottomNav(selected: Int, onHome: () -> Unit, onAnimals: () -> Unit, onSoon: () -> Unit) {
    val items = listOf("Início", "Animais", "Apadrinhar")
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PU.Divider)
        )
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            items.forEachIndexed { index, label ->
                val isSelected = index == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable {
                            when (index) {
                                0 -> onHome()
                                1 -> onAnimals()
                                else -> onSoon()
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (isSelected) PU.Green else Color.Transparent)
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = if (isSelected) PU.Ink else PU.Muted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
