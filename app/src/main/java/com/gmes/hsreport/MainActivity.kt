package com.gmes.hsreport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

private sealed interface AppScreen {
    data object Home : AppScreen
    data object Reports : AppScreen
    data object People : AppScreen
    data class Editor(val reportId: String) : AppScreen
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
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val store = remember { ReportStore(context) }
    var screen: AppScreen by remember { mutableStateOf(AppScreen.Home) }
    var refresh by remember { mutableIntStateOf(0) }

    when (val current = screen) {
        AppScreen.Home -> HomeScreen(
            onNew = {
                val report = ReportData()
                store.saveReport(report)
                screen = AppScreen.Editor(report.id)
            },
            onReports = { screen = AppScreen.Reports },
            onPeople = { screen = AppScreen.People }
        )
        AppScreen.Reports -> ReportsScreen(
            reports = remember(refresh) { store.listReports() },
            onBack = { screen = AppScreen.Home },
            onOpen = { screen = AppScreen.Editor(it.id) },
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
                screen = AppScreen.Editor(copy.id)
            }
        )
        AppScreen.People -> PeopleScreen(store = store, onBack = { screen = AppScreen.Home })
        is AppScreen.Editor -> ReportEditorScreen(
            store = store,
            reportId = current.reportId,
            onBack = {
                refresh++
                screen = AppScreen.Reports
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
        Text("GMES", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = GmesTeal)
        Text("Health & Safety Site Visit Reports", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(32.dp))
        HomeButton("New Report", Icons.Default.Add, onNew)
        Spacer(Modifier.height(12.dp))
        HomeButton("Completed / Saved Reports", Icons.Default.Description, onReports)
        Spacer(Modifier.height(12.dp))
        HomeButton("People & Reviewers", Icons.Default.People, onPeople)
        Spacer(Modifier.height(24.dp))
        Text(
            "Offline - reports and evidence photos stay on this device until you export them.",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun HomeButton(text: String, icon: ImageVector, onClick: () -> Unit) {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GmesTeal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No reports yet.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reports, key = { it.id }) { report ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(report) },
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(report.projectName.ifBlank { "Untitled report" }, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("${report.client.ifBlank { "No client" }}  •  ${displayDate(report.visitDate)}")
                                Text(
                                    "${report.status}  •  ${report.overallPercent()}%",
                                    color = if (report.status == "Completed") GmesTeal else Color.Gray,
                                    fontWeight = FontWeight.SemiBold
                                )
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
            confirmButton = {
                TextButton(onClick = { onDelete(report); deleteTarget = null }) { Text("Delete") }
            },
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("People & Reviewers") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { edit = Person(name = "") }) {
                        Icon(Icons.Default.Add, "Add", tint = Color.White)
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
            Text(
                "Add each regular reporter/reviewer once, then select them in future reports.",
                Modifier.padding(16.dp),
                color = Color.Gray
            )
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(people, key = { it.id }) { person ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(person.name, fontWeight = FontWeight.Bold)
                                if (person.role.isNotBlank()) Text(person.role)
                                if (person.shortName.isNotBlank()) {
                                    Text("Print name: ${person.shortName}", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            IconButton(onClick = { edit = person }) { Icon(Icons.Default.Edit, "Edit") }
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
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(person.copy(name = name.trim(), role = role.trim(), shortName = shortName.trim()))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
