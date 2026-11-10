package br.com.meufinanceiro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.ui.theme.Accent
import br.com.meufinanceiro.ui.theme.Bg
import br.com.meufinanceiro.ui.theme.Border
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.Surface2
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted

/**
 * Kit de componentes visuais do app.
 *
 * A ideia é manter a mesma linguagem em todas as telas: cartões grafite,
 * ícones em badges suaves, tags compactas e controles segmentados.
 */

@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 21.dp,
    corner: Dp? = null,
) {
    val shape = RoundedCornerShape(corner ?: (size * 0.32f))
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = 0.13f))
            .border(1.dp, tint.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun CardHeader(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = icon, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextMain,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = TextMain,
    caption: String? = null,
    background: Color = Surface2,
    borderColor: Color = Border,
    labelColor: Color = TextFaint,
    valueSize: TextUnit = 15.sp,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = labelColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = accent,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100))
            .background(Surface1)
            .border(1.dp, Border, RoundedCornerShape(100))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (selected) Accent else Color.Transparent,
                animationSpec = tween(durationMillis = 180),
                label = "segmentBackground",
            )
            val foreground by animateColorAsState(
                targetValue = if (selected) Bg else TextMuted,
                animationSpec = tween(durationMillis = 180),
                label = "segmentForeground",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(100))
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = foreground,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = TextMain, fontSize = 14.sp),
        cursorBrush = SolidColor(Accent),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface1)
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
                    .padding(start = 14.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = TextFaint, fontSize = 13.sp)
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            "Limpar",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun Tag(
    text: String,
    color: Color = TextMuted,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(5.dp))
        }
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
fun SectionTitle(
    title: String,
    caption: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextMain,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = (-0.2).sp,
            )
            if (caption != null) {
                Spacer(Modifier.height(2.dp))
                Text(caption, color = TextFaint, fontSize = 11.sp)
            }
        }
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(Surface1)
                    .border(1.dp, Border, RoundedCornerShape(100))
                    .clickable { onAction() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = actionLabel,
                    color = TextMain,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
