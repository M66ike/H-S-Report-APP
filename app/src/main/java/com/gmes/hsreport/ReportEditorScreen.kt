package com.gmes.hsreport

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEditorScreen(store: ReportStore, reportId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var report by remember(reportId) {
        mutableStateOf(store.getReport(reportId) ?: ReportData(id = reportId))
    }
    var page by remember(reportId) { mutableIntStateOf(0) }
    val people = remember(reportId) { store.listPeople() }
    var signing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var optionVersion by remember { mutableIntStateOf(0) }
    val sectorOptions = remember(optionVersion) { store.getSectorOptions() }
    val territoryOptions = remember(optionVersion) { store.getTerritoryOptions() }

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
        if (ok && qid != null && path != null) {
            update { it.copy(photos = it.photos + (qid to path)) }
        } else if (path != null) {
            File(path).delete()
        }
        pendingPhotoId = null
        pendingCameraPath = null
        pendingCameraUri = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingCameraUri?.let(cameraLauncher::launch)
        } else {
            pendingCameraPath?.let { File(it).delete() }
            pendingPhotoId = null
            pendingCameraPath = null
            pendingCameraUri = null
            Toast.makeText(context, "Camera permission is needed to take evidence photos.", Toast.LENGTH_LONG).show()
        }
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

    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        try {
            if (uri != null) {
                excelBytes?.let { bytes ->
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                Toast.makeText(context, "Excel report exported.", Toast.LENGTH_SHORT).show()
            }
        } finally {
            excelBytes = null
            exporting = false
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        try {
            if (uri != null) {
                pdfBytes?.let { bytes ->
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                Toast.makeText(context, "PDF report exported.", Toast.LENGTH_SHORT).show()
            }
        } finally {
            pdfBytes = null
            exporting = false
        }
    }

    val titles = remember {
        listOf("Details") + ReportTemplate.sections.map(::sectionLabel) + listOf("Review")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(report.projectName.ifBlank { "New H&S Report" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showOptions = true }) {
                        Icon(Icons.Default.Settings, "Report suggestions", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GmesTeal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SectionNavigator(titles, page) { page = it }
            HorizontalDivider()

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    0 -> DetailsPage(
                        report = report,
                        people = people,
                        sectorOptions = sectorOptions,
                        territoryOptions = territoryOptions,
                        onUpdate = ::update
                    )

                    in 1..ReportTemplate.sections.size -> {
                        val section = ReportTemplate.sections[page - 1]
                        SectionPage(
                            report = report,
                            section = section,
                            onAnswer = { id, value ->
                                update { it.copy(answers = it.answers + (id to value)) }
                            },
                            onItemComment = { id, value ->
                                update { current ->
                                    val updatedComments = if (value.isBlank()) {
                                        current.comments - id
                                    } else {
                                        current.comments + (id to value)
                                    }
                                    val combined = section.questions.mapNotNull { q ->
                                        updatedComments[q.id]
                                            ?.trim()
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { "${q.id} - $it" }
                                    }.joinToString("\n")
                                    val finalComments = if (combined.isBlank()) {
                                        updatedComments - section.id
                                    } else {
                                        updatedComments + (section.id to combined)
                                    }
                                    current.copy(comments = finalComments)
                                }
                            },
                            onCamera = { qid ->
                                val file = createPhotoFile(context, report.id, qid)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                pendingPhotoId = qid
                                pendingCameraPath = file.absolutePath
                                pendingCameraUri = uri
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    cameraLauncher.launch(uri)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onGallery = { qid ->
                                pendingPhotoId = qid
                                galleryLauncher.launch("image/*")
                            },
                            onRemovePhoto = { qid ->
                                report.photos[qid]?.let { File(it).delete() }
                                update { it.copy(photos = it.photos - qid) }
                            }
                        )
                    }

                    else -> ReviewPage(
                        report = report,
                        onSign = { signing = true },
                        onComplete = {
                            update { it.copy(status = if (it.status == "Completed") "Draft" else "Completed") }
                        },
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

                if (exporting) {
                    Box(
                        Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.78f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = GmesTeal)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { page-- }, enabled = page > 0) { Text("Previous") }
                Text("${page + 1} / ${titles.size}", color = Color.Gray)
                Button(
                    onClick = { page++ },
                    enabled = page < titles.lastIndex,
                    colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)
                ) { Text("Next") }
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

    if (showOptions) {
        ReportOptionsDialog(
            sectorOptions = sectorOptions,
            territoryOptions = territoryOptions,
            onDismiss = { showOptions = false },
            onSave = { sectors, territories ->
                store.saveSectorOptions(sectors)
                store.saveTerritoryOptions(territories)
                optionVersion++
                showOptions = false
            }
        )
    }
}

private fun sectionLabel(section: SectionDef): String = when (section.id) {
    "policy" -> "Policy"
    "ppe" -> "PPE"
    "equipment" -> "Equipment"
    "operational" -> "Operational"
    "client" -> "Client"
    else -> section.id
}

@Composable
private fun SectionNavigator(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        titles.forEachIndexed { index, title ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(title) }
            )
        }
    }
}

@Composable
private fun DetailsPage(
    report: ReportData,
    people: List<Person>,
    sectorOptions: List<String>,
    territoryOptions: List<String>,
    onUpdate: ((ReportData) -> ReportData) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Report Details", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GmesTeal)
        EditField("Client", report.client) { value -> onUpdate { it.copy(client = value) } }
        EditField("Site Visit Date (YYYY-MM-DD)", report.visitDate) { value -> onUpdate { it.copy(visitDate = value) } }
        EditField("Project Name", report.projectName) { value -> onUpdate { it.copy(projectName = value) } }
        EditField("GMES Project No.", report.projectNo) { value -> onUpdate { it.copy(projectNo = value) } }

        PersonPicker("Person Completing Report / Reviewer", report.personName, people) { person ->
            onUpdate {
                it.copy(
                    personId = person.id,
                    personName = person.name,
                    personRole = person.role,
                    reviewerId = person.id,
                    reviewerName = person.name
                )
            }
        }

        EditField("Person Role", report.personRole) { value -> onUpdate { it.copy(personRole = value) } }

        SuggestionField(
            label = "Sector / Project Type",
            value = report.sector,
            suggestions = sectorOptions,
            onValue = { value -> onUpdate { it.copy(sector = value) } }
        )

        EditField("GMES Site Manager / Supervisor", report.managerName) { value -> onUpdate { it.copy(managerName = value) } }
        EditField("Manager / Supervisor Role", report.managerRole) { value -> onUpdate { it.copy(managerRole = value) } }

        SuggestionField(
            label = "Territory",
            value = report.territory,
            suggestions = territoryOptions,
            onValue = { value -> onUpdate { it.copy(territory = value) } }
        )

        Text(
            "Tap the settings icon at the top to add, edit or remove Sector / Project Type and Territory suggestions. You can always type a different value manually.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Text(
            "The person completing the report is also used automatically as the reviewer on every section.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        if (people.isEmpty()) {
            Text(
                "Tip: add regular report authors from the People & Reviewers menu on the home screen.",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun EditField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun PersonPicker(label: String, selected: String, people: List<Person>, onSelect: (Person) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = people.isNotEmpty()
        ) {
            Text(
                selected.ifBlank { if (people.isEmpty()) "No saved people" else "Select person" },
                modifier = Modifier.weight(1f)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            people.forEach { person ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(person.name)
                            if (person.role.isNotBlank()) {
                                Text(person.role, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    },
                    onClick = {
                        onSelect(person)
                        expanded = false
                    }
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
    onItemComment: (String, String) -> Unit,
    onCamera: (String) -> Unit,
    onGallery: (String) -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(section.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GmesTeal)
                if (section.subtitle.isNotBlank()) {
                    Text(section.subtitle, color = Color.Gray, fontSize = 12.sp)
                }
            }
            if (section.scored) ScoreBadge(report.sectionPercent(section))
        }

        section.questions.forEach { q ->
            QuestionCard(
                question = q,
                selected = report.answers[q.id],
                comment = report.comments[q.id].orEmpty(),
                photoPath = if (q.photoRecommended) report.photos[q.id] else null,
                onAnswer = { onAnswer(q.id, it) },
                onComment = { onItemComment(q.id, it) },
                onCamera = { onCamera(q.id) },
                onGallery = { onGallery(q.id) },
                onRemovePhoto = { onRemovePhoto(q.id) }
            )
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (section.id == "client") "Comments on Client items" else "Combined section comments",
                    fontWeight = FontWeight.Bold,
                    color = GmesTeal
                )
                Text(
                    "This is the single comments box that will be used on the exported report.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                val combined = report.comments[section.id].orEmpty()
                Text(
                    combined.ifBlank { "No comments entered for this section." },
                    color = if (combined.isBlank()) Color.Gray else Color.Unspecified
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    Box(
        modifier = Modifier
            .size(62.dp)
            .background(
                if (score > 80) Color(0xFF7CDD12) else Color(0xFFFFE300),
                RoundedCornerShape(31.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text("$score%", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuestionCard(
    question: QuestionDef,
    selected: String?,
    comment: String,
    photoPath: String?,
    onAnswer: (String) -> Unit,
    onComment: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    question.id,
                    fontWeight = FontWeight.Bold,
                    color = GmesTeal,
                    modifier = Modifier.width(48.dp)
                )
                Text(question.text, modifier = Modifier.weight(1f))
                question.weight?.let {
                    Text(
                        "${(it * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                question.answers.forEach { answer ->
                    FilterChip(
                        selected = selected == answer.label,
                        onClick = { onAnswer(answer.label) },
                        label = { Text(answer.label) }
                    )
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = onComment,
                label = { Text("Optional comment") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3
            )

            if (question.photoRecommended) {
                HorizontalDivider()

                if (!photoPath.isNullOrBlank() && File(photoPath).exists()) {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = "Evidence for ${question.id}",
                        modifier = Modifier.fillMaxWidth().height(190.dp).background(Color.LightGray),
                        contentScale = ContentScale.Fit
                    )
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onCamera) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Camera")
                    }
                    OutlinedButton(onClick = onGallery) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Gallery")
                    }
                    if (!photoPath.isNullOrBlank()) {
                        TextButton(onClick = onRemovePhoto) {
                            Icon(Icons.Default.Delete, null)
                            Text("Remove")
                        }
                    }
                }
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Review & Export", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GmesTeal)

        Card(
            colors = CardDefaults.cardColors(containerColor = GmesLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Overall Score", fontWeight = FontWeight.Bold)
                Text(
                    "${report.overallPercent()}%",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = GmesTeal
                )
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
                Text(report.personName.ifBlank { "Not selected" })

                if (report.signaturePath.isNotBlank() && File(report.signaturePath).exists()) {
                    AsyncImage(
                        model = File(report.signaturePath),
                        contentDescription = "Signature",
                        modifier = Modifier.fillMaxWidth().height(95.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("No signature captured", color = Color.Gray)
                }

                Button(
                    onClick = onSign,
                    colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)
                ) {
                    Icon(Icons.Default.Signature, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (report.signaturePath.isBlank()) "Sign Report" else "Replace Signature")
                }
            }
        }

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (report.status == "Completed") Color.Gray else GmesTeal
            )
        ) {
            Icon(Icons.Default.CheckCircle, null)
            Spacer(Modifier.width(7.dp))
            Text(if (report.status == "Completed") "Mark as Draft" else "Mark Report Complete")
        }

        HorizontalDivider()
        Text("Exports", fontWeight = FontWeight.Bold)

        Button(
            onClick = onExportExcel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)
        ) {
            Icon(Icons.Default.FileDownload, null)
            Spacer(Modifier.width(7.dp))
            Text("Export Excel (.xlsx)")
        }

        OutlinedButton(onClick = onExportPdf, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PictureAsPdf, null, tint = GmesTeal)
            Spacer(Modifier.width(7.dp))
            Text("Export PDF")
        }

        Text(
            "The report author is used as the reviewer throughout the exported report.",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SignatureDialog(
    onDismiss: () -> Unit,
    onSave: (List<List<Offset>>, IntSize) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Sign inside the box. The signature is stored with this report.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color.White)
                        .border(1.dp, Color.Gray)
                        .onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current = listOf(it) },
                                onDragEnd = {
                                    if (current.size > 1) strokes.add(current)
                                    current = emptyList()
                                },
                                onDragCancel = { current = emptyList() },
                                onDrag = { change, _ ->
                                    change.consume()
                                    current = current + change.position
                                }
                            )
                        }
                ) {
                    fun drawStroke(points: List<Offset>) {
                        if (points.size < 2) return
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path,
                            Color.Black,
                            style = Stroke(width = 4f, cap = StrokeCap.Round)
                        )
                    }
                    strokes.forEach(::drawStroke)
                    drawStroke(current)
                }
                TextButton(onClick = {
                    strokes.clear()
                    current = emptyList()
                }) { Text("Clear") }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val all = strokes.toList() + listOfNotNull(current.takeIf { it.size > 1 })
                    onSave(all, padSize)
                },
                enabled = strokes.isNotEmpty() || current.size > 1,
                colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)
            ) { Text("Save Signature") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
