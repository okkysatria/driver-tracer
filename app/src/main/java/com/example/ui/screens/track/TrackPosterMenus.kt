package com.example.ui.screens.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GojekGreen
import com.example.ui.theme.GojekYellow

data class ThemeOption(
    val id: String,
    val name: String,
    val bgColor: Color,
    val accentColor: Color,
    val description: String
)

@Composable
fun ResponsiveThemeSelector(
    themes: List<ThemeOption>,
    selectedThemeId: String?,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Tema",
                tint = GojekGreen,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Pilih Tema",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(themes) { theme ->
                ThemeSelectorItem(
                    theme = theme,
                    isSelected = selectedThemeId == theme.id,
                    onClick = { onThemeSelected(theme.id) }
                )
            }
        }
    }
}

@Composable
fun ThemeSelectorItem(
    theme: ThemeOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) theme.bgColor.copy(alpha = 0.2f) else Color.Transparent
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, theme.accentColor)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(theme.bgColor, RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(theme.accentColor, RoundedCornerShape(5.dp))
                        .align(Alignment.Center)
                )
            }
            Text(
                text = theme.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
        }
    }
}

@Composable
fun PosterEditSection(
    title: String,
    icon: androidx.compose.material.icons.materialIcon,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = GojekGreen,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        content()
    }
}

@Composable
fun OptionChipsRow(
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selectedOption == option,
                onClick = { onOptionSelected(option) },
                label = {
                    Text(
                        text = option,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GojekGreen,
                    selectedLabelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun PosterStatsDisplay(
    distance: String,
    duration: String,
    earnings: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatDisplayItem(
            label = "Jarak",
            value = distance,
            icon = Icons.Default.NearMe
        )
        StatDisplayItem(
            label = "Durasi",
            value = duration,
            icon = Icons.Default.Schedule
        )
        StatDisplayItem(
            label = "Earnings",
            value = earnings,
            icon = Icons.Default.MonetizationOn
        )
    }
}

@Composable
fun StatDisplayItem(
    label: String,
    value: String,
    icon: androidx.compose.material.icons.materialIcon
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = GojekGreen,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ActionButtonsRow(
    onSave: () -> Unit,
    onShare: () -> Unit,
    isSaving: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GojekGreen),
            shape = RoundedCornerShape(10.dp),
            enabled = !isSaving
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text("Simpan", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onShare,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GojekYellow),
            shape = RoundedCornerShape(10.dp),
            enabled = !isSaving
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Bagikan", fontWeight = FontWeight.Bold)
        }
    }
}
