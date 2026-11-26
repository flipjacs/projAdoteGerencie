package br.com.patasunidas.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun WorkspaceTitle(title: String) {
    Text(title, color = PU.Green, fontSize = 32.sp, lineHeight = 36.sp,
        letterSpacing = (-1.44).sp, fontWeight = FontWeight(750))
}

@Composable
internal fun WorkspaceAction(
    label: String,
    enabled: Boolean,
    secondary: Boolean,
    onClick: () -> Unit
) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    val shape = RoundedCornerShape(50)
    if (secondary) {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape,
            border = BorderStroke(1.dp, if (enabled) PU.Green else PU.Border)) {
            Text(label, fontWeight = FontWeight(750))
        }
    } else {
        Button(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape) {
            Text(label, fontWeight = FontWeight(750))
        }
    }
}

internal enum class StatusTone(val surface: Color, val ink: Color) {
    PersonalPending(Color(0xFFF7EAD1), Color(0xFF76501E)),
    PersonalSuccess(Color(0xFFE0EEE4), PU.Green),
    PersonalMuted(Color(0xFFE8ECE6), Color(0xFF41574A)),
    Pending(Color(0xFFF5EBDD), Color(0xFF80542D)),
    Success(Color(0xFFE3F1E5), Color(0xFF235C39)),
    Neutral(Color(0xFFEDF0EB), Color(0xFF455A4A)),
    Reserved(Color(0xFFE6EDF4), Color(0xFF355C79)),
    Closed(Color(0xFFF2E6E3), Color(0xFF8C493D))
}

@Composable
internal fun WorkspaceStatus(label: String, tone: StatusTone) {
    Text(label, color = tone.ink, fontSize = 12.sp, lineHeight = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.background(tone.surface, RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp))
}
