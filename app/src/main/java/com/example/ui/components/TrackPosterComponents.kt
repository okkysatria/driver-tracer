package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

data class MenuOption(
    val id: String,
    val label: String,
    val icon: androidx.compose.material.icons.materialIcon,
    val description: String? = null
)

@Composable
fun ResponsiveMenuGrid(
    items: List<MenuOption>,
    selectedItem: String?,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { item ->
                    MenuItemCard(
                        item = item,
                        isSelected = selectedItem == item.id,
                        onClick = { onItemSelected(item.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun MenuItemCard(
    item: MenuOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GojekGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, GojekGreen)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(28.dp),
                tint = if (isSelected) GojekGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
        }
    }
}

@Composable
fun HorizontalMenuScroll(
    items: List<MenuOption>,
    selectedItem: String?,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(items) { item ->
            HorizontalMenuItemCard(
                item = item,
                isSelected = selectedItem == item.id,
                onClick = { onItemSelected(item.id) }
            )
        }
    }
}

@Composable
fun HorizontalMenuItemCard(
    item: MenuOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GojekGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5dp, GojekGreen)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) GojekGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ExpandableMenuSection(
    title: String,
    icon: androidx.compose.material.icons.materialIcon,
    items: List<MenuOption>,
    selectedItem: String?,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = true,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(isExpanded) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    expanded = !expanded
                    onExpandChange(!expanded)
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = GojekGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            ResponsiveMenuGrid(
                items = items,
                selectedItem = selectedItem,
                onItemSelected = onItemSelected,
                columns = 3
            )
        }
    }
}
