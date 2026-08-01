package dev.sivarj.assistant.ui.ideas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sivarj.assistant.AssistantApp
import dev.sivarj.assistant.ai.ContentType
import dev.sivarj.assistant.ai.EnrichResult
import dev.sivarj.assistant.data.AppDatabase
import dev.sivarj.assistant.data.Category
import dev.sivarj.assistant.data.CategoryType
import dev.sivarj.assistant.data.Idea
import dev.sivarj.assistant.domain.parseNoteJson
import dev.sivarj.assistant.ui.appViewModel
import dev.sivarj.assistant.ui.components.CategoryPicker
import dev.sivarj.assistant.ui.components.DictationField
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IdeasViewModel(private val db: AppDatabase) : ViewModel() {
    val ideas = db.ideaDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = db.categoryDao().observeByType(CategoryType.IDEA)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(idea: Idea) {
        viewModelScope.launch { db.ideaDao().upsert(idea.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun delete(idea: Idea) {
        viewModelScope.launch { db.ideaDao().softDelete(idea.id) }
    }

    fun createCategory(name: String, parentId: String?) {
        viewModelScope.launch {
            db.categoryDao().upsert(Category(name = name, type = CategoryType.IDEA, parentId = parentId))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeasScreen(vm: IdeasViewModel = appViewModel()) {
    val notes by vm.ideas.collectAsState()
    val categories by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<Idea?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val byId = categories.associateBy { it.id }
    val grouped = notes.groupBy { note ->
        val cat = note.categoryId?.let { byId[it] }
        val top = cat?.parentId?.let { byId[it] } ?: cat
        top?.name ?: "Uncategorized"
    }.toSortedMap(compareBy { if (it == "Uncategorized") "￿" else it.lowercase() })

    Scaffold(
        topBar = { TopAppBar(title = { Text("Notes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) { Icon(Icons.Default.Add, contentDescription = "New note") }
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No notes yet.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grouped.forEach { (groupName, groupNotes) ->
                    item(key = "header-$groupName") {
                        Text(
                            groupName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(groupNotes, key = { it.id }) { note ->
                        Card(onClick = {
                            editing = note
                            showEditor = true
                        }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    if (note.title.isNotBlank()) {
                                        Text(note.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            note.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                        )
                                    } else {
                                        Text(note.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                    }
                                    val sub = note.categoryId?.let { id -> byId[id]?.takeIf { it.parentId != null }?.name }
                                    if (sub != null) {
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                                IconButton(onClick = { vm.delete(note) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ModalBottomSheet(onDismissRequest = { showEditor = false }) {
            NoteEditor(
                // Keying on the edited note's id (or "new") resets all editor
                // state when a different note — or a fresh one — is opened.
                key = editing?.id ?: "new",
                initial = editing,
                categories = categories,
                onCreateCategory = vm::createCategory,
                onSave = { note ->
                    vm.save(note)
                    showEditor = false
                },
            )
        }
    }
}

@Composable
private fun NoteEditor(
    key: String,
    initial: Idea?,
    categories: List<Category>,
    onCreateCategory: (String, String?) -> Unit,
    onSave: (Idea) -> Unit,
) {
    var title by remember(key) { mutableStateOf(initial?.title ?: "") }
    var content by remember(key) { mutableStateOf(initial?.content ?: "") }
    var categoryId by remember(key) { mutableStateOf(initial?.categoryId) }
    var polishing by remember(key) { mutableStateOf(false) }
    var polishError by remember(key) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as AssistantApp

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (initial == null) "New note" else "Edit note",
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title (AI suggests one; edit freely)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DictationField(
            value = content,
            onValueChange = { content = it },
            label = "Your note",
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            enabled = content.isNotBlank() && !polishing,
            onClick = {
                polishing = true
                polishError = null
                scope.launch {
                    when (val result = app.enrichmentService.enrich(content, ContentType.IDEA)) {
                        is EnrichResult.Success -> {
                            // The polish prompt is user-editable, so the response may
                            // be {"title","body"} JSON or plain prose — handle both.
                            val polished = parseNoteJson(result.text)
                            content = polished.body
                            if (title.isBlank()) {
                                title = polished.title.ifBlank {
                                    // Prompt didn't supply a title; ask for one directly.
                                    app.enrichmentService.suggestTitle(polished.body).orEmpty()
                                }
                            }
                        }
                        is EnrichResult.Failure -> polishError = result.error
                    }
                    polishing = false
                }
            },
        ) {
            if (polishing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(6.dp))
                Text("Polishing…")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Polish with AI")
            }
        }
        if (polishError != null) {
            Text(polishError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
        CategoryPicker(
            categories = categories,
            selectedId = categoryId,
            onSelect = { categoryId = it },
            onCreate = onCreateCategory,
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.End) {
            TextButton(
                enabled = content.isNotBlank(),
                onClick = {
                    val base = initial ?: Idea(content = content.trim())
                    onSave(
                        base.copy(
                            title = title.trim(),
                            content = content.trim(),
                            categoryId = categoryId,
                        )
                    )
                },
            ) { Text("Save") }
        }
    }
}
