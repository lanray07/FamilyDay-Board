package com.familyday.board.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

val Teal = Color(0xFF277C78)
val Cream = Color(0xFFF8F7F2)
val Peach = Color(0xFFF7E5D5)
val Sage = Color(0xFFE7EEE3)
val MemberColours = listOf(Teal, Color(0xFFAB6530), Color(0xFF7464A1), Color(0xFF3F779C), Color(0xFFAA4D69), Color(0xFF537646))
private val Light = lightColorScheme(primary = Teal, onPrimary = Color.White, primaryContainer = Color(0xFFDDEDE6),
    secondary = Color(0xFF8A5127), secondaryContainer = Peach, background = Cream, surface = Color(0xFFFFFEFA),
    surfaceVariant = Color(0xFFEFEEE7), onBackground = Color(0xFF263238), onSurface = Color(0xFF263238), onSurfaceVariant = Color(0xFF515D57))
private val Dark = darkColorScheme(primary = Color(0xFF9BD1C4), primaryContainer = Color(0xFF244C47),
    secondary = Color(0xFFE9A66B), secondaryContainer = Color(0xFF4A3728), background = Color(0xFF18221F),
    surface = Color(0xFF222E29), surfaceVariant = Color(0xFF303E37), onSurface = Color(0xFFF0F1E9), onBackground = Color(0xFFF0F1E9))
@Composable fun FamilyDayTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        typography = Typography(
            displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 38.sp, lineHeight = 44.sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 27.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
            labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)), content = content)
}
