package com.example
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.example.BuildConfig
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.data.preferences.ThemeMode
import com.example.export.FileShareHelper
import com.example.ui.AppDestination
import com.example.ui.NoqtatiViewModel
import com.example.ui.components.ExportShareBottomSheet
import com.example.ui.screens.ClassesAndStudentsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.GradesAndSubjectsScreen
import com.example.ui.screens.ReportsAndAveragesScreen
import com.example.ui.screens.SettingsBackupScreen
import com.example.ui.screens.SmartScannerScreen
import com.example.ui.theme.NoqtatiTheme

private data class NavTabItem(
    val destination: AppDestination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI, ViewModels, or AI calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val viewModel: NoqtatiViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val darkTheme = when (uiState.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            NoqtatiTheme(darkTheme = darkTheme) {
                NoqtatiRootApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoqtatiRootApp(viewModel: NoqtatiViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val currentDest by viewModel.currentDestination.collectAsStateWithLifecycle()
    val openStudentsFocus by viewModel.openStudentsInClasses.collectAsStateWithLifecycle()
    val pendingExport by viewModel.pendingExport.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = currentDest != AppDestination.DASHBOARD) {
        viewModel.navigateTo(AppDestination.DASHBOARD)
    }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val artifact = pendingExport
        if (uri != null && artifact != null) {
            viewModel.saveArtifactToUserUri(artifact, uri)
            viewModel.dismissExportSheet()
        }
    }

    val navTabs = remember {
        listOf(
            NavTabItem(AppDestination.DASHBOARD, "الرئيسية", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
            NavTabItem(AppDestination.CLASSES, "الأقسام", Icons.Rounded.Class, Icons.Outlined.Class),
            NavTabItem(AppDestination.GRADES, "العلامات", Icons.Rounded.EditNote, Icons.Outlined.EditNote),
            NavTabItem(AppDestination.SCANNER, "التصوير", Icons.Rounded.DocumentScanner, Icons.Outlined.DocumentScanner),
            NavTabItem(AppDestination.REPORTS, "التقارير", Icons.Rounded.Assessment, Icons.Outlined.Assessment)
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (currentDest == AppDestination.DASHBOARD) "NOQTATI – نقطتي" else currentDest.titleAr,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (currentDest == AppDestination.SETTINGS) {
                                    viewModel.navigateTo(AppDestination.DASHBOARD)
                                } else {
                                    viewModel.navigateTo(AppDestination.SETTINGS)
                                }
                            },
                            modifier = Modifier.testTag("topbar_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "الإعدادات والنسخ الاحتياطي",
                                tint = if (currentDest == AppDestination.SETTINGS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        navTabs.forEach { tab ->
                            val selected = currentDest == tab.destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.navigateTo(tab.destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("nav_${tab.destination.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            navTabs.forEach { tab ->
                                val selected = currentDest == tab.destination
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.navigateTo(tab.destination) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentDest) {
                            AppDestination.DASHBOARD -> DashboardScreen(
                                uiState = uiState,
                                onNavigate = { dest, focusStudents ->
                                    viewModel.navigateTo(dest, focusStudents)
                                }
                            )

                            AppDestination.CLASSES -> ClassesAndStudentsScreen(
                                uiState = uiState,
                                initialFocusStudents = openStudentsFocus,
                                onConsumeStudentsFocus = viewModel::clearGlobalStudentsFocus,
                                onAddClass = viewModel::addClass,
                                onUpdateClass = viewModel::updateClass,
                                onDeleteClass = viewModel::deleteClass,
                                onAddStudent = viewModel::addStudent,
                                onUpdateStudent = viewModel::updateStudent,
                                onDeleteStudent = viewModel::deleteStudent,
                                onTransferStudent = viewModel::transferStudent
                            )

                            AppDestination.GRADES -> GradesAndSubjectsScreen(
                                uiState = uiState,
                                onAddSubject = viewModel::addSubject,
                                onUpdateSubject = viewModel::updateSubject,
                                onDeleteSubject = viewModel::deleteSubject,
                                onAddPresetSubjects = viewModel::addPresetSubjects,
                                onSaveGrade = viewModel::saveGrade,
                                onDeleteGrade = viewModel::deleteGrade
                            )

                            AppDestination.SCANNER -> SmartScannerScreen(
                                uiState = uiState,
                                scannerState = scannerState,
                                onSelectClass = viewModel::selectScannerClass,
                                onSelectSubject = viewModel::selectScannerSubject,
                                onSelectSemester = viewModel::selectScannerSemester,
                                onImageSelected = viewModel::setScannerImageUri,
                                onToggleOfflineMode = viewModel::setScannerOfflineMode,
                                onOfflineTextChange = viewModel::updateOfflineInputText,
                                onAnalyzeOnlineAi = viewModel::analyzeCapturedImageWithAi,
                                onAnalyzeOfflineText = viewModel::analyzeOfflineTextInput,
                                onLoadOfflineRosterTable = viewModel::loadOfflineRosterReviewTable,
                                onUpdateRowScore = viewModel::updateExtractedRowScore,
                                onUpdateRowStudent = viewModel::updateExtractedRowStudent,
                                onToggleRowSelected = viewModel::toggleExtractedRowSelected,
                                onRemoveRow = viewModel::removeExtractedRow,
                                onConfirmAndSave = viewModel::confirmAndSaveExtractedGrades,
                                onClearResults = viewModel::clearScannerResults
                            )

                            AppDestination.REPORTS -> ReportsAndAveragesScreen(
                                uiState = uiState,
                                isExporting = isExporting,
                                onExportStudentPdf = viewModel::exportStudentBulletinPdf,
                                onExportClassPdf = viewModel::exportClassGradesPdf,
                                onExportClassExcel = viewModel::exportClassGradesExcel
                            )

                            AppDestination.SETTINGS -> SettingsBackupScreen(
                                uiState = uiState,
                                onUpdateTheme = viewModel::updateThemeMode,
                                onUpdateProfile = viewModel::updateProfile,
                                onToggleAiOcr = viewModel::updateAiOcrEnabled,
                                onAddSemester = viewModel::addSemester,
                                onSetActiveSemester = viewModel::setActiveSemester,
                                onDeleteSemester = viewModel::deleteSemester,
                                onExportBackupToUri = viewModel::exportBackupToUri,
                                onShareBackup = viewModel::prepareShareableBackup,
                                onRestoreBackupFromUri = viewModel::restoreBackupFromUri
                            )
                        }
                    }
                }
            }
        }
    }

    pendingExport?.let { artifact ->
        ExportShareBottomSheet(
            artifact = artifact,
            onShareWhatsApp = {
                FileShareHelper.shareViaWhatsApp(context, artifact.file, artifact.mimeType, artifact.displayTitle)
                viewModel.dismissExportSheet()
            },
            onShareEmail = {
                FileShareHelper.shareViaEmail(context, artifact.file, artifact.mimeType, artifact.displayTitle)
                viewModel.dismissExportSheet()
            },
            onShareGeneral = {
                FileShareHelper.shareFileGeneral(context, artifact.file, artifact.mimeType, artifact.displayTitle)
                viewModel.dismissExportSheet()
            },
            onSaveToDevice = {
                saveDocumentLauncher.launch(artifact.suggestedFileName)
            },
            onDismiss = viewModel::dismissExportSheet
        )
    }
}
