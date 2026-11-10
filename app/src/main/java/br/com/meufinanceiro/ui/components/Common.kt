package br.com.meufinanceiro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.AmountPrivacy
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.ui.theme.Accent
import br.com.meufinanceiro.ui.theme.Bg
import br.com.meufinanceiro.ui.theme.Border
import br.com.meufinanceiro.ui.theme.Danger
import br.com.meufinanceiro.ui.theme.Income
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.Surface2
import br.com.meufinanceiro.ui.theme.Surface3
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import br.com.meufinanceiro.ui.theme.categoryStyle
import java.time.YearMonth

/**
 * Cartão base do app.
 *
 * Usa um gradiente sutil (topo levemente mais claro) e uma borda que "acende"
 * no topo, criando o efeito de vidro fosco sem exagerar no contraste.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    color: Color = Surface1,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(color, Color.White, 0.045f),
                        color,
                        lerp(color, Bg, 0.55f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color(0x24FFFFFF), Color(0x0AFFFFFF)),
                ),
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun GradientCard(
    brush: Brush,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color(0x30FFFFFF), Color(0x0AFFFFFF)),
                ),
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        // O olho fica sempre no topo à direita, sem disputar espaço com o
        // seletor de mês.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(100))
                    .background(Accent.copy(alpha = 0.75f)),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = eyebrow.uppercase(),
                color = TextFaint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
                modifier = Modifier.weight(1f),
            )
            PrivacyToggle()
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = TextMain, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = TextMuted, fontSize = 13.sp)
        }
        // O seletor de mês (ou outra ação) fica em uma linha própria para não
        // espremer títulos longos como "Movimentações".
        if (trailing != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                trailing.invoke()
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    caption: String? = null,
) {
    SectionTitle(
        title = title,
        caption = caption,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

@Composable
fun MonthSelector(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(100)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(lerp(Surface1, Color.White, 0.05f), Surface1),
                ),
            )
            .border(1.dp, Border, shape)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.ChevronLeft, "Mês anterior", tint = TextMuted, modifier = Modifier.size(18.dp))
        }
        Text(
            text = FinanceCalculator.formatMonth(month),
            color = TextMain,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.ChevronRight, "Próximo mês", tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: Double,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    GlassCard(modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = icon, tint = color, size = 34.dp, iconSize = 17.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = label.uppercase(),
                color = TextFaint,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = FinanceCalculator.brl(value),
            color = TextMain,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null) {
            Text(caption, color = TextFaint, fontSize = 10.sp)
        }
    }
}

@Composable
fun ProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(100))
            .background(Surface3),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(100))
                .background(
                    Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0.75f), color),
                    ),
                ),
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(100)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.26f), shape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(100)
    val background by animateColorAsState(
        targetValue = if (selected) Accent else Surface2,
        animationSpec = tween(180),
        label = "filterBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) Bg else TextMuted,
        animationSpec = tween(180),
        label = "filterForeground",
    )
    Box(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else Border,
                shape = shape,
            )
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun AmountText(
    value: Double,
    isIncome: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
) {
    val hidden = AmountPrivacy.hidden
    Text(
        text = if (hidden) {
            FinanceCalculator.brl(value)
        } else {
            (if (isIncome) "+ " else "− ") + FinanceCalculator.brl(value)
        },
        color = if (isIncome) Income else Danger,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        modifier = modifier,
    )
}

@Composable
fun CategoryBadge(category: String) {
    val style = categoryStyle(category)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(style.color.copy(alpha = 0.12f))
            .border(1.dp, style.color.copy(alpha = 0.22f), RoundedCornerShape(100))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(style.icon, null, tint = style.color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(category, color = style.color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = TextMain) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(lerp(Surface2, Color.White, 0.05f), Surface1),
                    ),
                )
                .border(1.dp, Border, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = TextMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = TextMuted, fontSize = 12.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Accent)
                    .clickable { onAction() }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(actionLabel, color = Bg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
