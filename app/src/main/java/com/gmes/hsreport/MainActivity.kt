package com.gmes.hsreport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Signature
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val GmesTeal = Color(0xFF0C6E6A)
private val GmesDark = Color(0xFF4D4D4D)
private val GmesLight = Color(0xFFF3F6F6)

private sealed interface Screen {
    data object Home : Screen
    data object Reports : Screen
    data object People : Screen
    data class Editor(val reportId: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = GmesTeal,
                    secondary = GmesTeal,
                    surfaceVariant = GmesLight
                )
            ) {
                Surface(Modifier.fillMaxSize()) {
                    HsReportApp(applicationContext)
                }
            }
        }
    }
}

@Composable
private fun HsReportApp(context: Context) {
    val store = remember { ReportStore(context) }
    var screen: Screen by remember { mutableStateOf(Screen.Home) }
    var refresh by remember { mutableIntStateOf(0) }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            onNew = {
                val report = ReportData()
                store.saveReport(report)
                screen = Screen.Editor(report.id)
            },
            onReports = { screen = Screen.Reports },
            onPeople = { screen = Screen.People }
        )
        Screen.Reports -> ReportsScreen(
            reports = remember(refresh) { store.listReports() },
            onBack = { screen = Screen.Home },
            onOpen = { screen = Screen.Editor(it.id) },
            onDelete = {
                store.deleteReport(it.id)
                refresh++
            },
            onDuplicate = {
                val copy = it.copy(
                    id = UUID.randomUUID().toString(),
                    status = "Draft",
                    signaturePath = "",
                    updatedAt = System.currentTimeMillis()
                )
                store.saveReport(copy)
                refresh++
                screen = Screen.Editor(copy.id)
            }
        )
        Screen.People -> PeopleScreen(
            store = store,
            onBack = { screen = Screen.Home }
        )
        is Screen.Editor -> ReportEditor(
            context = context,
            store = store,
            reportId = current.reportId,
            onBack = {
                refresh++
                screen = Screen.Reports
            }
        )
    }
}

@Composable
private fun HomeScreen(onNew: () -> Unit, onReports: () -> Unit, onPeople: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("GMES", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = GmesTeal)
        Text("Health & Safety Site Visit Reports", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(32.dp))
        HomeButton("New Report", Icons.Default.Add, onNew)
        Spacer(Modifier.height(12.dp))
        HomeButton("Completed / Saved Reports", Icons.Default.Description, onReports)
        Spacer(Modifier.height(12.dp))
        HomeButton("People & Reviewers", Icons.Default.People, onPeople)
        Spacer(Modifier.height(24.dp))
        Text("Offline - reports and photographs stay on this device until you export them.", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun HomeButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsScreen(
    reports: List<ReportData>,
    onBack: () -> Unit,
    onOpen: (ReportData) -> Unit,
    onDelete: (ReportData) -> Unit,
    onDuplicate: (ReportData) -> Unit
) {
    var deleteTarget by remember { mutableStateOf<ReportData?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Reports") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GmesTeal, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No reports yet.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(reports, key = { it.id }) { report ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(report) },
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(report.projectName.ifBlank { "Untitled report" }, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("${report.client.ifBlank { "No client" }}  •  ${displayDate(report.visitDate)}")
                                Text("${report.status}  •  ${report.overallPercent()}%", color = if (report.status == "Completed") GmesTeal else Color.Gray, fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(onClick = { onDuplicate(report) }) { Icon(Icons.Default.Edit, "Duplicate") }
                            IconButton(onClick = { deleteTarget = report }) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFB3261E)) }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { report ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete report?") },
            text = { Text(report.projectName.ifBlank { "This report" }) },
            confirmButton = { TextButton(onClick = { onDelete(report); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleScreen(store: ReportStore, onBack: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val people = remember(refresh) { store.listPeople() }
    var edit by remember { mutableStateOf<Person?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("People & Reviewers") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { adding = true; edit = Person(name = "") }) { Icon(Icons.Default.Add, "Add", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GmesTeal, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text("Save people here once, then select them when creating reports.", Modifier.padding(16.dp), color = Color.Gray)
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(people, key = { it.id }) { person ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(person.name, fontWeight = FontWeight.Bold)
                                if (person.role.isNotBlank()) Text(person.role)
                                if (person.shortName.isNotBlank()) Text("Print name: ${person.shortName}", fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { adding = false; edit = person }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton(onClick = { store.deletePerson(person.id); refresh++ }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }

    edit?.let { person ->
        PersonDialog(
            person = person,
            onDismiss = { edit = null },
            onSave = {
                store.savePerson(it)
                refresh++
                edit = null
            }
        )
    }
}

@Composable
private fun PersonDialog(person: Person, onDismiss: () -> Unit, onSave: (Person) -> Unit) {
    var name by remember(person.id) { mutableStateOf(person.name) }
    var role by remember(person.id) { mutableStateOf(person.role) }
    var shortName by remember(person.id) { mutableStateOf(person.shortName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (person.name.isBlank()) "Add Person" else "Edit Person") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(role, { role = it }, label = { Text("Role") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(shortName, { shortName = it }, label = { Text("Print name / initials") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onSave(person.copy(name = name.trim(), role = role.trim(), shortName = shortName.trim())) }, colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportEditor(context: Context, store: ReportStore, reportId: String, onBack: () -> Unit) {
    var report by remember(reportId) { mutableStateOf(store.getReport(reportId) ?: ReportData(id = reportId)) }
    var page by remember(reportId) { mutableIntStateOf(0) }
    val people = remember { store.listPeople() }
    var signing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    fun update(block: (ReportData) -> ReportData) {
        report = block(report).copy(updatedAt = System.currentTimeMillis())
        store.saveReport(report)
    }

    var pendingPhotoId by remember { mutableStateOf<String?>(null) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val qid = pendingPhotoId
        val path = pendingCameraPath
        if (ok && qid != null && path != null) update { it.copy(photos = it.photos + (qid to path)) }
        pendingPhotoId = null
        pendingCameraPath = null
        pendingCameraUri = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingCameraUri?.let { cameraLauncher.launch(it) }
        else Toast.makeText(context, "Camera permission is needed to take evidence photos.", Toast.LENGTH_LONG).show()
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val qid = pendingPhotoId
        if (uri != null && qid != null) {
            val path = copyUriToReportPhoto(context, report.id, qid, uri)
            if (path != null) update { it.copy(photos = it.photos + (qid to path)) }
        }
        pendingPhotoId = null
    }

    var excelBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let { target ->
            excelBytes?.let { bytes -> context.contentResolver.openOutputStream(target)?.use { it.write(bytes) } }
            Toast.makeText(context, "Excel report exported.", Toast.LENGTH_SHORT).show()
        }
        excelBytes = null
        exporting = false
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { target ->
            pdfBytes?.let { bytes -> context.contentResolver.openOutputStream(target)?.use { it.write(bytes) } }
            Toast.makeText(context, "PDF report exported.", Toast.LENGTH_SHORT).show()
        }
        pdfBytes = null
        exporting = false
    }

    val titles = listOf("Details") + ReportTemplate.sections.map { it.id.replaceFirstChar(Char::uppercase) } + listOf("Review")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(report.projectName.ifBlank { "New H&S Report" }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GmesTeal, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SectionNavigator(titles, page) { page = it }
            HorizontalDivider()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    0 -> DetailsPage(report, people, onUpdate = ::update)
                    in 1..ReportTemplate.sections.size -> SectionPage(
                        report = report,
                        section = ReportTemplate.sections[page - 1],
                        onAnswer = { id, value -> update { it.copy(answers = it.answers + (id to value)) } },
                        onComment = { value ->
                            val section = ReportTemplate.sections[page - 1]
                            update { it.copy(comments = it.comments + (section.id to value)) }
                        },
                        onCamera = { qid ->
                            val file = createPhotoFile(context, report.id, qid)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            pendingPhotoId = qid
                            pendingCameraPath = file.absolutePath
                            pendingCameraUri = uri
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch(uri)
                            else permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onGallery = { qid -> pendingPhotoId = qid; galleryLauncher.launch("image/*") },
                        onRemovePhoto = { qid ->
                            report.photos[qid]?.let { File(it).delete() }
                            update { it.copy(photos = it.photos - qid) }
                        }
                    )
                    else -> ReviewPage(
                        report = report,
                        onSign = { signing = true },
                        onComplete = { update { it.copy(status = if (it.status == "Completed") "Draft" else "Completed") } },
                        onExportExcel = {
                            exporting = true
                            try {
                                excelBytes = ExcelExporter.create(context, report)
                                excelLauncher.launch(fileBase(report) + ".xlsx")
                            } catch (e: Exception) {
                                exporting = false
                                Toast.makeText(context, "Excel export failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        onExportPdf = {
                            exporting = true
                            try {
                                pdfBytes = PdfExporter.create(context, report)
                                pdfLauncher.launch(fileBase(report) + ".pdf")
                            } catch (e: Exception) {
                                exporting = false
                                Toast.makeText(context, "PDF export failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
                if (exporting) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GmesTeal) }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Previous") }
                Text("${page + 1} / ${titles.size}", modifier = Modifier.align(Alignment.CenterVertically), color = Color.Gray)
                Button(onClick = { if (page < titles.lastIndex) page++ }, enabled = page < titles.lastIndex, colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)) { Text("Next") }
            }
        }
    }

    if (signing) {
        SignatureDialog(
            onDismiss = { signing = false },
            onSave = { strokes, size ->
                val path = saveSignature(context, report.id, strokes, size)
                if (path != null) update { it.copy(signaturePath = path) }
                signing = false
            }
        )
    }
}

@Composable
private fun SectionNavigator(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScrollCompat().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        titles.forEachIndexed { index, title ->
            FilterChip(selected = selected == index, onClick = { onSelect(index) }, label = { Text(title) })
        }
    }
}

private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollStateCompat()))

@Composable
private fun rememberScrollStateCompat() = rememberScrollState()

@Composable
private fun DetailsPage(report: ReportData, people: List<Person>, onUpdate: ((ReportData) -> ReportData) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Report Details", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GmesTeal)
        EditField("Client", report.client) { value -> onUpdate { it.copy(client = value) } }
        EditField("Site Visit Date (YYYY-MM-DD)", report.visitDate) { value -> onUpdate { it.copy(visitDate = value) } }
        EditField("Project Name", report.projectName) { value -> onUpdate { it.copy(projectName = value) } }
        EditField("GMES Project No.", report.projectNo) { value -> onUpdate { it.copy(projectNo = value) } }
        PersonPicker("Person Completing Report", report.personName, people) { person ->
            onUpdate { it.copy(personId = person.id, personName = person.name, personRole = person.role) }
        }
        EditField("Person Role", report.personRole) { value -> onUpdate { it.copy(personRole = value) } }
        EditField("Sector", report.sector) { value -> onUpdate { it.copy(sector = value) } }
        EditField("GMES Site Manager / Supervisor", report.managerName) { value -> onUpdate { it.copy(managerName = value) } }
        EditField("Manager / Supervisor Role", report.managerRole) { value -> onUpdate { it.copy(managerRole = value) } }
        EditField("Territory", report.territory) { value -> onUpdate { it.copy(territory = value) } }
        PersonPicker("Reviewer", report.reviewerName, people) { person -> onUpdate { it.copy(reviewerId = person.id, reviewerName = person.name) } }
        if (people.isEmpty()) Text("Tip: add people from the People & Reviewers menu on the home screen.", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun EditField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun PersonPicker(label: String, selected: String, people: List<Person>, onSelect: (Person) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), enabled = people.isNotEmpty()) {
            Text(selected.ifBlank { if (people.isEmpty()) "No saved people" else "Select person" }, modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            people.forEach { person ->
                DropdownMenuItem(
                    text = { Column { Text(person.name); if (person.role.isNotBlank()) Text(person.role, fontSize = 11.sp, color = Color.Gray) } },
                    onClick = { onSelect(person); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SectionPage(
    report: ReportData,
    section: SectionDef,
    onAnswer: (String, String) -> Unit,
    onComment: (String) -> Unit,
    onCamera: (String) -> Unit,
    onGallery: (String) -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(section.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GmesTeal)
                if (section.subtitle.isNotBlank()) Text(section.subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            if (section.scored) ScoreBadge(report.sectionPercent(section))
        }
        section.questions.forEach { q ->
            QuestionCard(
                question = q,
                selected = report.answers[q.id],
                photoPath = report.photos[q.id],
                onAnswer = { onAnswer(q.id, it) },
                onCamera = { onCamera(q.id) },
                onGallery = { onGallery(q.id) },
                onRemovePhoto = { onRemovePhoto(q.id) }
            )
        }
        OutlinedTextField(
            value = report.comments[section.id].orEmpty(),
            onValueChange = onComment,
            label = { Text(if (section.id == "client") "Comments on Client items" else "Section comments") },
            modifier = Modifier.fillMaxWidth().height(125.dp)
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    Box(
        modifier = Modifier.size(62.dp).background(if (score > 80) Color(0xFF7CDD12) else Color(0xFFFFE300), RoundedCornerShape(31.dp)),
        contentAlignment = Alignment.Center
    ) { Text("$score%", fontWeight = FontWeight.Bold) }
}

@Composable
private fun QuestionCard(
    question: QuestionDef,
    selected: String?,
    photoPath: String?,
    onAnswer: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(question.id, fontWeight = FontWeight.Bold, color = GmesTeal, modifier = Modifier.width(48.dp))
                Text(question.text, modifier = Modifier.weight(1f))
                question.weight?.let { Text("${(it * 100).toInt()}%", fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(start = 8.dp)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                question.answers.forEach { answer ->
                    FilterChip(selected = selected == answer.label, onClick = { onAnswer(answer.label) }, label = { Text(answer.label) })
                }
            }
            HorizontalDivider()
            if (!photoPath.isNullOrBlank() && File(photoPath).exists()) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = "Evidence for ${question.id}",
                    modifier = Modifier.fillMaxWidth().height(190.dp).background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCamera) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(5.dp)); Text("Camera") }
                OutlinedButton(onClick = onGallery) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(5.dp)); Text("Gallery") }
                if (!photoPath.isNullOrBlank()) TextButton(onClick = onRemovePhoto) { Icon(Icons.Default.Delete, null); Text("Remove") }
            }
        }
    }
}

@Composable
private fun ReviewPage(
    report: ReportData,
    onSign: () -> Unit,
    onComplete: () -> Unit,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Review & Export", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GmesTeal)
        Card(colors = CardDefaults.cardColors(containerColor = GmesLight), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Overall Score", fontWeight = FontWeight.Bold)
                Text("${report.overallPercent()}%", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = GmesTeal)
                ReportTemplate.sections.filter { it.scored }.forEach { section ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(section.title.substringAfter("- ").trim(), modifier = Modifier.weight(1f))
                        Text("${report.sectionPercent(section)}%", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reviewer", fontWeight = FontWeight.Bold)
                Text(report.reviewerName.ifBlank { report.personName.ifBlank { "Not selected" } })
                if (report.signaturePath.isNotBlank() && File(report.signaturePath).exists()) {
                    AsyncImage(model = File(report.signaturePath), contentDescription = "Signature", modifier = Modifier.fillMaxWidth().height(95.dp), contentScale = ContentScale.Fit)
                } else Text("No signature captured", color = Color.Gray)
                Button(onClick = onSign, colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)) {
                    Icon(Icons.Default.Signature, null); Spacer(Modifier.width(6.dp)); Text(if (report.signaturePath.isBlank()) "Sign Report" else "Replace Signature")
                }
            }
        }
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (report.status == "Completed") Color.Gray else GmesTeal)) {
            Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(7.dp)); Text(if (report.status == "Completed") "Mark as Draft" else "Mark Report Complete")
        }
        HorizontalDivider()
        Text("Exports", fontWeight = FontWeight.Bold)
        Button(onClick = onExportExcel, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)) {
            Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(7.dp)); Text("Export Excel (.xlsx)")
        }
        OutlinedButton(onClick = onExportPdf, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PictureAsPdf, null, tint = GmesTeal); Spacer(Modifier.width(7.dp)); Text("Export PDF")
        }
        Text("Excel uses the same fixed FM_038 report positions for the header, question answers, comments and section scores, so it can be copied/imported into the existing combined scoring workbook.", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun SignatureDialog(onDismiss: () -> Unit, onSave: (List<List<Offset>>, IntSize) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sign inside the box. This signature is stored with this report.", fontSize = 12.sp, color = Color.Gray)
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(220.dp).background(Color.White).border(1.dp, Color.Gray)
                        .onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current = listOf(it) },
                                onDragEnd = { if (current.size > 1) strokes += current; current = emptyList() },
                                onDragCancel = { current = emptyList() },
                                onDrag = { change, _ -> change.consume(); current = current + change.position }
                            )
                        }
                ) {
                    fun drawStroke(points: List<Offset>) {
                        if (points.size < 2) return
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, Color.Black, style = Stroke(width = 4f, cap = StrokeCap.Round))
                    }
                    strokes.forEach(::drawStroke)
                    drawStroke(current)
                }
                TextButton(onClick = { strokes.clear(); current = emptyList() }) { Text("Clear") }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(strokes.toList() + listOfNotNull(current.takeIf { it.size > 1 }), padSize) }, enabled = strokes.isNotEmpty() || current.size > 1, colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)) { Text("Save Signature") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun createPhotoFile(context: Context, reportId: String, questionId: String): File {
    val dir = File(context.filesDir, "reports/$reportId/photos").apply { mkdirs() }
    return File(dir, "${questionId.replace('.', '_')}_${System.currentTimeMillis()}.jpg")
}

private fun copyUriToReportPhoto(context: Context, reportId: String, questionId: String, uri: Uri): String? = try {
    val dir = File(context.filesDir, "reports/$reportId/photos").apply { mkdirs() }
    val ext = guessExtension(context, uri)
    val file = File(dir, "${questionId.replace('.', '_')}_${System.currentTimeMillis()}.$ext")
    context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
    file.absolutePath
} catch (_: Exception) { null }

private fun guessExtension(context: Context, uri: Uri): String {
    val type = context.contentResolver.getType(uri).orEmpty()
    if (type.contains("png")) return "png"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val name = c.getString(0).orEmpty()
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in listOf("png", "jpg", "jpeg")) return if (ext == "jpeg") "jpg" else ext
        }
    }
    return "jpg"
}

private fun saveSignature(context: Context, reportId: String, strokes: List<List<Offset>>, size: IntSize): String? = try {
    if (size.width <= 0 || size.height <= 0) return null
    val width = 1200
    val height = 400
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.TRANSPARENT)
    val sx = width.toFloat() / size.width
    val sy = height.toFloat() / size.height
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        strokeWidth = 5f
        strokeCap = AndroidPaint.Cap.ROUND
        style = AndroidPaint.Style.STROKE
    }
    strokes.forEach { points ->
        if (points.size >= 2) {
            val path = android.graphics.Path()
            path.moveTo(points.first().x * sx, points.first().y * sy)
            points.drop(1).forEach { path.lineTo(it.x * sx, it.y * sy) }
            canvas.drawPath(path, paint)
        }
    }
    val dir = File(context.filesDir, "reports/$reportId").apply { mkdirs() }
    val file = File(dir, "signature.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    file.absolutePath
} catch (_: Exception) { null }

private fun fileBase(report: ReportData): String {
    val date = report.visitDate.replace("-", "")
    val project = report.projectName.ifBlank { "H-S-Report" }.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(" ", "_")
    return "${date}_${project}"
}

private fun displayDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
} catch (_: Exception) { iso }
