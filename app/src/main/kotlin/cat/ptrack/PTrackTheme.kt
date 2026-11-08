package cat.ptrack

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBCF46A),
    onPrimary = Color(0xFF182900),
    primaryContainer = Color(0xFF294432),
    onPrimaryContainer = Color(0xFFE9FFD0),
    secondary = Color(0xFF77DCD2),
    secondaryContainer = Color(0xFF164744),
    onSecondaryContainer = Color(0xFFB0FFF4),
    background = Color(0xFF10231E),
    onBackground = Color(0xFFF0F7F1),
    surface = Color(0xFF10231E),
    onSurface = Color(0xFFF0F7F1),
    surfaceContainer = Color(0xFF1C342B),
    surfaceContainerHigh = Color(0xFF244035),
    onSurfaceVariant = Color(0xFFB8CEC1),
    outline = Color(0xFF8DA79A),
    outlineVariant = Color(0xFF3B594D)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF365900),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF3BA),
    onPrimaryContainer = Color(0xFF1A2D08),
    secondary = Color(0xFF006962),
    secondaryContainer = Color(0xFFB3F1E8),
    onSecondaryContainer = Color(0xFF003C37),
    background = Color(0xFFF4F8F2),
    onBackground = Color(0xFF17251C),
    surface = Color(0xFFF4F8F2),
    onSurface = Color(0xFF17251C),
    surfaceContainer = Color(0xFFE6EFE6),
    surfaceContainerHigh = Color(0xFFDCEADE),
    onSurfaceVariant = Color(0xFF43544A),
    outline = Color(0xFF697D70),
    outlineVariant = Color(0xFFBFCFC4)
)

private val PTrackShapes = Shapes(
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
internal fun PTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = PTrackShapes,
        content = content
    )
}
