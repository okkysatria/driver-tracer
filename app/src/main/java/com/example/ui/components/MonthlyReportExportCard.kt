package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.theme.GojekGreen
import com.example.ui.theme.GojekRed
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun MonthlyReportExportCard(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    var taxReportDriverName by remember { mutableStateOf("") }
    var taxReportSelectedMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }
    var taxReportSelectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var isProcessingExport by remember { mutableStateOf(false) }
    var exportModeText by remember { mutableStateOf("") }

    // EXPORT MONTHLY REVENUE REPORT
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Laporan Pendapatan Bulanan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text("Pilih bulan, isi nama pengendara, dan rancang salinan berkas rekapitulasi pendapatan bulanan yang terstruktur untuk pelaporan pendapatan mandiri dalam format dokumen (.doc).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Driver Name input
        OutlinedTextField(
            value = taxReportDriverName,
            onValueChange = { taxReportDriverName = it },
            label = { Text("Nama Lengkap Pengendara", fontSize = 11.sp) },
            placeholder = { Text("DRIVER UTAMA", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth().testTag("tax_driver_name_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GojekGreen,
                focusedLabelColor = GojekGreen
            )
        )

        // Interactive Calendar Picker for Month & Year Selection
        Text("Pilih Periode Laporan (Desain Kalender):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(
            modifier = Modifier.fillMaxWidth().testTag("tax_calendar_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Year picker row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (taxReportSelectedYear > 2020) taxReportSelectedYear-- },
                        modifier = Modifier.size(32.dp).testTag("tax_year_prev_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Tahun Sebelumnya",
                            tint = GojekGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = taxReportSelectedYear.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GojekGreen
                        )
                        Text(
                            text = "Tahun Laporan",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { if (taxReportSelectedYear < 2030) taxReportSelectedYear++ },
                        modifier = Modifier.size(32.dp).testTag("tax_year_next_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Tahun Berikutnya",
                            tint = GojekGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Months Grid (4 rows x 3 columns)
                val monthAbbr = listOf(
                    "JAN", "FEB", "MAR", "APR", "MEI", "JUN",
                    "JUL", "AGS", "SEP", "OKT", "NOV", "DES"
                )
                val monthFull = listOf(
                    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (col in 0 until 3) {
                                val monthIndex = row * 3 + col
                                val mNum = monthIndex + 1
                                val isSel = taxReportSelectedMonth == mNum

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (isSel) GojekGreen else MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSel) GojekGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { taxReportSelectedMonth = mNum }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = monthAbbr[monthIndex],
                                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = monthFull[monthIndex].take(5),
                                            color = if (isSel) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isProcessingExport = true
                        exportModeText = "Menyiapkan laporan bulanan (.doc)..."
                        try {
                            val doc = withContext(Dispatchers.IO) {
                                viewModel.exportMonthlyTaxReport(
                                    taxReportDriverName,
                                    taxReportSelectedYear,
                                    taxReportSelectedMonth
                                )
                            }
                            exportModeText = "Menulis berkas dokumen (.doc)..."
                            val file = withContext(Dispatchers.IO) {
                                val cacheFile = java.io.File(context.cacheDir, "laporan_pendapatan_bulan_${taxReportSelectedMonth}.doc")
                                cacheFile.writeText(doc)
                                cacheFile
                            }
                            val uri = FileProvider.getUriForFile(
                                context,
                                "com.example.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/msword"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Bagikan Laporan Pendapatan Driver (.doc)"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal membuat laporan: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isProcessingExport = false
                        }
                    }
                },
                modifier = Modifier.weight(1f).testTag("export_tax_share_button"),
                colors = ButtonDefaults.buttonColors(containerColor = GojekGreen)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Share Revenue", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bagikan .DOC", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = {
                    val doc = viewModel.exportMonthlyTaxReport(
                        taxReportDriverName,
                        taxReportSelectedYear,
                        taxReportSelectedMonth
                    )
                    val clip = ClipData.newPlainText("Laporan Pendapatan Driver", doc)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(context, "Laporan disalin ke clipboard.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f).testTag("export_tax_copy_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GojekGreen)
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Revenue", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Salin Laporan", fontSize = 11.sp)
            }
        }
    }
}
