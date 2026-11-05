package br.com.patasunidas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role

/** Patinha desenhada via Canvas. */
@Composable
fun PawIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun oval(cx: Float, cy: Float, ow: Float, oh: Float, deg: Float = 0f) {
            val center = Offset(cx * w, cy * h)
            rotate(deg, center) {
                drawOval(
                    color = color,
                    topLeft = Offset((cx - ow / 2) * w, (cy - oh / 2) * h),
                    size = Size(ow * w, oh * h)
                )
            }
        }
        oval(0.14f, 0.46f, 0.20f, 0.27f, -25f)
        oval(0.37f, 0.22f, 0.21f, 0.28f, -8f)
        oval(0.63f, 0.22f, 0.21f, 0.28f, 8f)
        oval(0.86f, 0.46f, 0.20f, 0.27f, 25f)
        oval(0.50f, 0.70f, 0.56f, 0.44f)
    }
}

/** Logo "Patas Unidas" usado nos cabeçalhos verdes. */
@Composable
fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PU.Orange),
            contentAlignment = Alignment.Center
        ) {
            PawIcon(PU.Green, Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Patas\nUnidas",
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun FilledButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PU.Green)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun OutlinedPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, PU.Green, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = PU.Ink,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun SerifTitle(text: String, modifier: Modifier = Modifier, size: Int = 22) {
    Text(
        text = text,
        modifier = modifier,
        color = PU.Ink,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 24.dp),
        color = PU.Ink,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
    )
}

@Composable
fun LinkText(text: String, onClick: () -> Unit, fontSize: Int = 14, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.clickable(onClick = onClick),
        color = PU.Green,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize.sp,
        textDecoration = TextDecoration.Underline,
        textAlign = TextAlign.Center
    )
}

@Composable
fun PuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val shape = RoundedCornerShape(14.dp)
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 16.sp, color = PU.Ink),
        cursorBrush = SolidColor(PU.Green),
        visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White, shape)
                    .border(1.dp, PU.Border, shape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(text = placeholder, color = PU.Placeholder, fontSize = 16.sp)
                    }
                    inner()
                }
                if (password) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .semantics { contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha" }
                            .clickable(role = Role.Button) { passwordVisible = !passwordVisible },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.size(20.dp)) {
                            val eye = Path().apply {
                                moveTo(1.dp.toPx(), size.height / 2)
                                quadraticTo(size.width / 2, -2.dp.toPx(), size.width - 1.dp.toPx(), size.height / 2)
                                quadraticTo(size.width / 2, size.height + 2.dp.toPx(), 1.dp.toPx(), size.height / 2)
                            }
                            drawPath(eye, PU.Muted, style = Stroke(width = 1.7.dp.toPx()))
                            drawCircle(PU.Muted, radius = 2.5.dp.toPx())
                            if (!passwordVisible) {
                                drawLine(PU.Muted, Offset(2.dp.toPx(), size.height - 1.dp.toPx()), Offset(size.width - 2.dp.toPx(), 1.dp.toPx()), strokeWidth = 1.7.dp.toPx())
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SegmentedToggle(isTeam: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PU.SegmentTrack)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Segment("Adotante ou padrinho", selected = !isTeam, modifier = Modifier.weight(1f)) { onChange(false) }
        Segment("Equipe da ONG", selected = isTeam, modifier = Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PU.Green else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else PU.Ink,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(interactionSource = interaction, indication = null) { onChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) PU.Green else Color.White)
                .border(1.5.dp, if (checked) PU.Green else PU.CheckBorder, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text = label, color = PU.Ink, fontSize = 14.sp)
    }
}
