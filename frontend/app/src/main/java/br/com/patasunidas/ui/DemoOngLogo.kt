package br.com.patasunidas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.patasunidas.R

/** Logos abertos usados apenas como referências visuais nas duas ONGs fictícias da demo. */
@Composable
fun DemoOngLogo(ongId: Int?, modifier: Modifier = Modifier) {
    val (resource, organization) = when (ongId) {
        1 -> R.drawable.demo_ong_1_logo to "Merlin Animal Rescue"
        2 -> R.drawable.demo_ong_2_logo to "Political Animal Lobby"
        else -> return
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(resource),
            contentDescription = "Logo de referência de $organization",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(88.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PU.Paper)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Logo de referência. ONG demonstrativa sem vínculo com a organização.",
            color = PU.Muted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}
