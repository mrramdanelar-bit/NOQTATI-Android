package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.repository.formatScore
import com.example.domain.AverageCalculator
import com.example.ui.AppDestination
import com.example.ui.NoqtatiUiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.NoqtatiSectionHeader
import com.example.ui.components.ScorePillBadge

private data class DashboardTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val containerColor: Color,
    val iconColor: Color,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun DashboardScreen(
    uiState: NoqtatiUiState,
    onNavigate: (AppDestination, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSemester = uiState.activeSemester

    val classSummaries = remember(uiState.classes, uiState.students, uiState.subjects, uiState.grades, activeSemester) {
        if (activeSemester == null) {
            emptyList()
        } else {
            uiState.classes.map { academicClass ->
                AverageCalculator.computeClassSemesterSummary(
                    academicClass = academicClass,
                    semester = activeSemester,
                    students = uiState.students,
                    subjects = uiState.subjects,
                    grades = uiState.grades
                )
            }
        }
    }

    val overallPassRate = remember(classSummaries) {
        val totalGraded = classSummaries.sumOf { it.gradedStudentsCount }
        val totalPassed = classSummaries.sumOf { it.passCount }
        if (totalGraded > 0) AverageCalculator.round2((totalPassed.toDouble() / totalGraded) * 100.0) else null
    }

    val colorScheme = MaterialTheme.colorScheme
    val tiles = listOf(
        DashboardTile(
            title = "الأقسام",
            subtitle = "${uiState.classes.size} قسم نشط",
            icon = Icons.Rounded.Class,
            containerColor = colorScheme.primaryContainer,
            iconColor = colorScheme.onPrimaryContainer,
            testTag = "tile_classes",
            onClick = { onNavigate(AppDestination.CLASSES, false) }
        ),
        DashboardTile(
            title = "التلاميذ",
            subtitle = "${uiState.students.size} تلميذ مسجل",
            icon = Icons.Rounded.Groups,
            containerColor = colorScheme.secondaryContainer,
            iconColor = colorScheme.onSecondaryContainer,
            testTag = "tile_students",
            onClick = { onNavigate(AppDestination.CLASSES, true) }
        ),
        DashboardTile(
            title = "إدخال العلامات",
            subtitle = "${uiState.subjects.size} مادة دراسية",
            icon = Icons.Rounded.EditNote,
            containerColor = colorScheme.tertiaryContainer,
            iconColor = colorScheme.onTertiaryContainer,
            testTag = "tile_grades",
            onClick = { onNavigate(AppDestination.GRADES, false) }
        ),
        DashboardTile(
            title = "التصوير الذكي",
            subtitle = "OCR + ذكاء اصطناعي",
            icon = Icons.Rounded.DocumentScanner,
            containerColor = Color(0xFF0F6B5E).copy(alpha = 0.14f),
            iconColor = colorScheme.primary,
            testTag = "tile_scanner",
            onClick = { onNavigate(AppDestination.SCANNER, false) }
        ),
        DashboardTile(
            title = "حساب المعدلات",
            subtitle = "الترتيب والمعاملات",
            icon = Icons.Rounded.Calculate,
            containerColor = colorScheme.secondaryContainer.copy(alpha = 0.85f),
            iconColor = colorScheme.onSecondaryContainer,
            testTag = "tile_averages",
            onClick = { onNavigate(AppDestination.REPORTS, false) }
        ),
        DashboardTile(
            title = "التقارير وكشوف النقاط",
            subtitle = "تصدير PDF و Excel",
            icon = Icons.Rounded.Description,
            containerColor = colorScheme.tertiaryContainer.copy(alpha = 0.85f),
            iconColor = colorScheme.onTertiaryContainer,
            testTag = "tile_reports",
            onClick = { onNavigate(AppDestination.REPORTS, false) }
        ),
        DashboardTile(
            title = "الإعدادات والنسخ الاحتياطي",
            subtitle = "الوضع الليلي وحفظ البيانات",
            icon = Icons.Rounded.Backup,
            containerColor = colorScheme.surfaceVariant,
            iconColor = colorScheme.onSurfaceVariant,
            testTag = "tile_settings",
            onClick = { onNavigate(AppDestination.SETTINGS, false) }
        )
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Banner
        item {
            DashboardHeroCard(
                teacherName = uiState.settings.teacherName,
                schoolName = uiState.settings.schoolName,
                semesterName = activeSemester?.name ?: "الفصل الأول",
                academicYear = uiState.settings.defaultAcademicYear,
                onOpenSettings = { onNavigate(AppDestination.SETTINGS, false) }
            )
        }

        // KPI Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMiniCard(
                    label = "الأقسام",
                    value = uiState.classes.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    label = "التلاميذ",
                    value = uiState.students.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    label = "العلامات",
                    value = uiState.grades.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    label = "النجاح",
                    value = overallPassRate?.let { "${it.toInt()}%" } ?: "—",
                    highlight = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Smart OCR Spotlight Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_smart_ocr_cta")
                    .clickable { onNavigate(AppDestination.SCANNER, false) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "التصوير الذكي",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مسح كشف النقاط بالكاميرا (OCR + AI)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "صوّر ورقة العلامات لاستخراج الأسماء والنقاط آلياً ومراجعتها قبل الحفظ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // 7-Module Quick Grid
        item {
            NoqtatiSectionHeader(
                title = "لوحة التحكم",
                subtitle = "وصول سريع إلى جميع وظائف إدارة التعليم"
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(2).forEach { rowTiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowTiles.forEach { tile ->
                            DashboardTileCard(
                                tile = tile,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowTiles.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Live Class Averages Overview
        item {
            NoqtatiSectionHeader(
                title = "ملخص الأقسام (${activeSemester?.name ?: "الفصل الحالي"})",
                subtitle = "نظرة سريعة على المعدل العام ونسبة النجاح"
            )
        }

        if (classSummaries.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.School,
                    title = "ابدأ بإضافة أول قسم دراسي",
                    description = "أنشئ أقسامك الدراسية وسجّل التلاميذ والمواد ليتم حساب المعدلات وكشوف النقاط تلقائياً.",
                    actionLabel = "إضافة قسم جديد",
                    onAction = { onNavigate(AppDestination.CLASSES, false) }
                )
            }
        } else {
            items(classSummaries, key = { it.academicClass.id }) { summary ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(AppDestination.REPORTS, false) },
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
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Class,
                                contentDescription = summary.academicClass.name,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = summary.academicClass.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${summary.reports.size} تلميذ • ${summary.academicClass.level} • ${summary.academicClass.academicYear}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            ScorePillBadge(score = summary.classAverage)
                            if (summary.passRatePercent != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "نجاح ${formatScore(summary.passRatePercent)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun DashboardHeroCard(
    teacherName: String,
    schoolName: String,
    semesterName: String,
    academicYear: String,
    onOpenSettings: () -> Unit
) {
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF0E685B), Color(0xFF09473E), Color(0xFF1A4B5C))
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
    ) {
        // Subtle architectural arch geometry on Canvas
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = size.height * 0.85f,
                center = Offset(size.width * 0.12f, size.height * 0.15f)
            )
            drawCircle(
                color = Color(0xFFE6AE4C).copy(alpha = 0.12f),
                radius = size.height * 0.55f,
                center = Offset(size.width * 0.08f, size.height * 0.95f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = size.height * 0.6f,
                center = Offset(size.width * 0.88f, size.height * 0.9f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "$semesterName • $academicYear",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .testTag("hero_settings_button")
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "الإعدادات",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (teacherName.isNotBlank()) "مرحباً الأستاذ $teacherName" else "NOQTATI – نقطتي",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = schoolName.ifBlank { "نظام ذكي متكامل لإدارة الأقسام والعلامات وكشوف النقاط" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun KpiMiniCard(
    label: String,
    value: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
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
private fun DashboardTileCard(
    tile: DashboardTile,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag(tile.testTag)
            .clickable { tile.onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tile.containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = tile.title,
                    tint = tile.iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
