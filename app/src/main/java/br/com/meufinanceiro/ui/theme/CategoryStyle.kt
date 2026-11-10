package br.com.meufinanceiro.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.meufinanceiro.data.Categories

data class CategoryStyle(val color: Color, val icon: ImageVector)

fun categoryStyle(category: String): CategoryStyle = when (category) {
    Categories.FOOD -> CategoryStyle(Color(0xFFE8A06A), Icons.Rounded.Restaurant)
    Categories.MARKET -> CategoryStyle(Color(0xFF7BD8A8), Icons.Rounded.LocalGroceryStore)
    Categories.SHOPPING -> CategoryStyle(Color(0xFFE58FB8), Icons.Rounded.ShoppingBag)
    Categories.TRANSPORT -> CategoryStyle(Color(0xFF7FA8E8), Icons.Rounded.DirectionsCar)
    Categories.HOUSING -> CategoryStyle(Color(0xFFA99BE8), Icons.Rounded.Home)
    Categories.HEALTH -> CategoryStyle(Color(0xFFE88A8A), Icons.Rounded.LocalHospital)
    Categories.LEISURE -> CategoryStyle(Color(0xFFE8C36A), Icons.Rounded.SportsEsports)
    Categories.SUBSCRIPTIONS -> CategoryStyle(Color(0xFF6FC8D8), Icons.Rounded.Subscriptions)
    Categories.EDUCATION -> CategoryStyle(Color(0xFF8F9BE8), Icons.Rounded.School)
    Categories.TRAVEL -> CategoryStyle(Color(0xFF6FD8C8), Icons.Rounded.Flight)
    Categories.PETS -> CategoryStyle(Color(0xFFE8A8A8), Icons.Rounded.Pets)
    Categories.SALARY -> CategoryStyle(Color(0xFF6EE7A8), Icons.Rounded.Payments)
    Categories.INVESTMENTS -> CategoryStyle(Color(0xFFE8D06A), Icons.Rounded.Savings)
    Categories.TRANSFERS -> CategoryStyle(Color(0xFF9A9AA2), Icons.Rounded.SwapHoriz)
    Categories.FEES -> CategoryStyle(Color(0xFFC98A6A), Icons.Rounded.Percent)
    else -> CategoryStyle(Color(0xFF8E8E95), Icons.Rounded.MoreHoriz)
}

fun categoryColor(category: String): Color = categoryStyle(category).color