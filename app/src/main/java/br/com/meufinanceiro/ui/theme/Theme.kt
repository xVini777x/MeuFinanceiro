package br.com.meufinanceiro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Paleta "black premium": fundo quase preto, cards grafite e acento branco.
// Verde/vermelho ficam reservados para receitas/despesas.
// ---------------------------------------------------------------------------

val Bg = Color(0xFF08080A)
val BgSoft = Color(0xFF0D0D10)
val Surface1 = Color(0xFF131316)
val Surface2 = Color(0xFF1A1A1E)
val Surface3 = Color(0xFF242428)

/** Acento principal do app (branco gelo). */
val Accent = Color(0xFFEDEDEF)

/** Mantido como apelido para o acento; evita quebrar referências antigas. */
val Mint = Accent

/** Receitas / valores positivos. */
val Income = Color(0xFF6EE7A8)

val Teal = Color(0xFF6FD8C8)
val Purple = Color(0xFFA99BE8)
val Blue = Color(0xFF7FA8E8)
val Amber = Color(0xFFE8C36A)
val Pink = Color(0xFFE58FB8)

/** Despesas / valores negativos. */
val Danger = Color(0xFFFF7A8A)

val TextMain = Color(0xFFF4F4F6)
val TextMuted = Color(0xFF9A9AA2)
val TextFaint = Color(0xFF6A6A72)
val Border = Color(0x14FFFFFF)

val GradientBalance = Brush.linearGradient(
    listOf(Color(0xFF232328), Color(0xFF161619), Color(0xFF0D0D10)),
)
val GradientCard = Brush.linearGradient(
    listOf(Color(0xFF202025), Color(0xFF17171A), Color(0xFF0F0F12)),
)
val GradientHero = Brush.linearGradient(
    listOf(Color(0xFF1B1B1F), Color(0xFF131316), Color(0xFF0C0C0E)),
)
val GradientMint = Brush.linearGradient(
    listOf(Color(0xFFF2F2F4), Color(0xFFC9C9CF)),
)

private val FinanceTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        letterSpacing = (-1.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
    ),
)

private val FinanceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Bg,
            secondary = Accent,
            onSecondary = Bg,
            tertiary = Accent,
            background = Bg,
            onBackground = TextMain,
            surface = Surface1,
            onSurface = TextMain,
            surfaceVariant = Surface2,
            onSurfaceVariant = TextMuted,
            outline = Border,
            error = Danger,
        ),
        typography = FinanceTypography,
        shapes = FinanceShapes,
        content = content,
    )
}
