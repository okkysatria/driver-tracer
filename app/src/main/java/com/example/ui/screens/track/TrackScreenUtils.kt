package com.example.ui.screens.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GojekGreen

enum class StravaCardTheme(
    val id: String,
    val displayName: String,
    val bgColor: Color,
    val accentColor: Color,
    val cardColor: Color,
    val textPrimary: Color
) {
    GOJEK_EMERALD("gojek_emerald", "Sleek Emerald",
        Color(0xFF041E15), Color(0xFF00AA13), Color(0xFF0C2C20), Color(0xFFFFFFFF)),
    STRAVA_ORANGE("strava_orange", "Strava Sunset",
        Color(0xFF0F1012), Color(0xFFFC6100), Color(0xFF1B1D21), Color(0xFFFFFFFF)),
    MIDNIGHT_ONYX("midnight_onyx", "Midnight Onyx",
        Color(0xFF020911), Color(0xFF00E5FF), Color(0xFF0A1625), Color(0xFFFFFFFF)),
    CYBER_PULSE("cyber_pulse", "Cyber Pulse",
        Color(0xFF000000), Color(0xFFCCFF00), Color(0xFF121212), Color(0xFFFFFFFF))
}

@Composable
fun ThemePreviewCard(
    theme: StravaCardTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) theme.bgColor.copy(alpha = 0.3f) else Color.Transparent
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, theme.accentColor)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(theme.bgColor, RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(theme.accentColor, RoundedCornerShape(6.dp))
                        .align(Alignment.Center)
                )
            }
            Text(
                text = theme.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun StatsRowCompact(
    label: String,
    value: String,
    unit: String = "",
    icon: androidx.compose.material.icons.materialIcon? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = GojekGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "$"value",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PosterActionButton(
    label: String,
    icon: androidx.compose.material.icons.materialIcon,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) GojekGreen else MaterialTheme.colorScheme.secondary
        ),
        shape = RoundedCornerShape(10.dp),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun PosterPreview(
    themeColor: Color,
    stats: Map<String, String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.75f),
        colors = CardDefaults.cardColors(containerColor = themeColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Track Preview",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = value,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Text(
                text = "Driver Tracker",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}
