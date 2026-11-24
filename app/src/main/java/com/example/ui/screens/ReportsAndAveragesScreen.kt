package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.formatScore
import com.example.domain.AverageCalculator
import com.example.domain.ClassSemesterSummary
import com.example.domain.StudentSemesterReport
import com.example.ui.NoqtatiUiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.NoqtatiSectionHeader
import com.example.ui.components.ScorePillBadge
import com.example.ui.components.SelectorDropdownField
import com.example.ui.theme.GoldTertiaryLight

@Composable
fun ReportsAndAveragesScreen(
    uiState: NoqtatiUiState,
    isExporting: Boolean,
    onExportStudentPdf: (StudentSemesterReport, ClassSemesterSummary) -> Unit,
    onExportClassPdf: (ClassSemesterSummary) -> Unit,
    onExportClassExcel: (ClassSemesterSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedClassId by remember(uiState.classes) {
        mutableStateOf(uiState.classes.firstOrNull()?.id)
    }
    var selectedSemesterId by remember(uiState.semesters) {
        mutableStateOf(uiState.activeSemester?.id)
    }
    var previewingReport by remember { mutableStateOf<StudentSemesterReport?>(null) }

    val selectedClass = uiState.classes.firstOrNull { it.id == selectedClassId } ?: uiState.classes.firstOrNull()
    val selectedSemester = uiState.semesters.firstOrNull { it.id == selectedSemesterId } ?: uiState.activeSemester

    val summary = remember(selectedClass, selectedSemester, uiState.students, uiState.subjects, uiState.grades) {
        if (selectedClass != null && selectedSemester != null) {
            AverageCalculator.computeClassSemesterSummary(
                academicClass = selectedClass,
                semester = selectedSemester,
                students = uiState.students,
                subjects = uiState.subjects,
                grades = uiState.grades
            )
        } else {
            null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NoqtatiSectionHeader(
                title = "المعدلات والتقارير وكشوف النقاط",
                subtitle = "حساب المعدل والترتيب آلياً وتصدير PDF و Excel ومشاركتها"
            )
        }

        if (uiState.classes.isEmpty() || selectedSemester == null) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Assessment,
                    title = "لا توجد بيانات لحساب المعدلات",
                    description = "أضف الأقسام والتلاميذ والمواد ثم أدخل العلامات لعرض الترتيب واستخراج كشوف النقاط."
                )
            }
            return@LazyColumn
        }

        // Class + Semester Selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SelectorDropdownField(
                        label = "القسم",
                        items = uiState.classes,
                        selectedItem = selectedClass,
                        itemLabel = { it.name },
                        onItemSelected = { selectedClassId = it.id },
                        modifier = Modifier.weight(1f)
                    )
                    SelectorDropdownField(
                        label = "الفصل الدراسي",
                        items = uiState.semesters,
                        selectedItem = selectedSemester,
                        itemLabel = { it.name },
                        onItemSelected = { selectedSemesterId = it.id },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (summary != null) {
            // Class Statistics Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "إحصائيات قسم ${summary.academicClass.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${summary.semester.name} • ${summary.academicClass.academicYear}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatPill(
                                label = "المعدل العام",
                                value = summary.classAverage?.let { formatScore(it) } ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                            StatPill(
                                label = "أعلى معدل",
                                value = summary.highestAverage?.let { formatScore(it) } ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                            StatPill(
                                label = "أدنى معدل",
                                value = summary.lowestAverage?.let { formatScore(it) } ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                            StatPill(
                                label = "النجاح",
                                value = summary.passRatePercent?.let { "${it.toInt()}%" } ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Export Class Table Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onExportClassPdf(summary) },
                                enabled = !isExporting && summary.reports.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_class_pdf_button")
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("جدول PDF")
                            }
                            FilledTonalButton(
                                onClick = { onExportClassExcel(summary) },
                                enabled = !isExporting && summary.reports.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_class_excel_button")
                            ) {
                                Icon(Icons.Rounded.TableView, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("تصدير Excel")
                            }
                        }
                    }
                }
            }

            // Ranked Students List
            item {
                NoqtatiSectionHeader(
                    title = "ترتيب التلاميذ وكشوف النقاط الفردية",
                    subtitle = "اضغط على أي تلميذ لمعاينة كشف نقاطه الفردي وتصديره PDF"
                )
            }

            if (summary.reports.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.EmojiEvents,
                        title = "لا يوجد تلاميذ في هذا القسم",
                        description = "أضف تلاميذ القسم وعلاماتهم ليظهر الترتيب والمعدلات هنا."
                    )
                }
            } else {
                items(summary.reports, key = { it.student.id }) { report ->
                    val annualAvg = remember(report.student.id, uiState.subjects, uiState.semesters, uiState.grades) {
                        AverageCalculator.computeAnnualAverage(
                            studentId = report.student.id,
                            subjects = uiState.subjects,
                            semesters = uiState.semesters,
                            grades = uiState.grades
                        )
                    }
                    RankedStudentCard(
                        report = report,
                        annualAverage = annualAvg,
                        onClick = { previewingReport = report }
                    )
                }
            }
        }
    }

    // Individual Student Bulletin Preview Dialog
    val activeReport = previewingReport
    if (activeReport != null && summary != null) {
        StudentBulletinPreviewDialog(
            report = activeReport,
            summary = summary,
            onExportPdf = {
                onExportStudentPdf(activeReport, summary)
                previewingReport = null
            },
            onDismiss = { previewingReport = null }
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RankedStudentCard(
    report: StudentSemesterReport,
    annualAverage: Double?,
    onClick: () -> Unit
) {
    val rankBg = when (report.rank) {
        1 -> GoldTertiaryLight
        2 -> Color(0xFF7B8E9B)
        3 -> Color(0xFFA66B3F)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val rankFg = if (report.rank in 1..3) Color.White else MaterialTheme.colorScheme.onSecondaryContainer

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ranked_student_card_${report.student.id}")
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(rankBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = report.rank?.toString() ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = rankFg
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.student.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val subLine = buildString {
                    append(report.appreciation)
                    if (annualAverage != null) append(" • السنوي: ${formatScore(annualAverage)}")
                }
                Text(
                    text = subLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            ScorePillBadge(score = report.average)
        }
    }
}

@Composable
private fun StudentBulletinPreviewDialog(
    report: StudentSemesterReport,
    summary: ClassSemesterSummary,
    onExportPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("كشف النقاط الفردي", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "${report.student.fullName} • ${report.academicClass.name} • ${report.semester.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("المعدل الفصلي", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = report.average?.let { "${formatScore(it)} / 20" } ?: "—",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "الترتيب: ${report.rank ?: "—"} / ${summary.reports.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = report.appreciation,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(report.lines, key = { it.subject.id }) { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line.subject.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "المعامل: ${formatScore(line.subject.coefficient)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ScorePillBadge(
                                score = line.grade?.finalScore,
                                maxScore = line.subject.maxScore
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExportPdf,
                modifier = Modifier.testTag("export_student_pdf_button")
            ) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("تصدير PDF ومشاركة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
