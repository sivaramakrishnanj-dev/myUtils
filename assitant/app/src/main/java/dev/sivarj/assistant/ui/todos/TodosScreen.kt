package dev.sivarj.assistant.ui.todos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.sivarj.assistant.data.Todo
import dev.sivarj.assistant.data.TodoStatus
import dev.sivarj.assistant.ui.appViewModel
import dev.sivarj.assistant.ai.ContentType
import dev.sivarj.assistant.ui.components.CategoryPicker
import dev.sivarj.assistant.ui.components.DictationField
import dev.sivarj.assistant.ui.components.EnrichButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodosScreen(vm: TodosViewModel = appViewModel()) {
    val todos by vm.todos.collectAsState()
    val categories by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<Todo?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val byId = categories.associateBy { it.id }
    // Group by top-level category name; sub-category todos roll up under their parent.
    val grouped = todos.groupBy { todo ->
        val cat = todo.categoryId?.let { byId[it] }
        val top = cat?.parentId?.let { byId[it] } ?: cat
        top?.name ?: "Uncategorized"
    }.toSortedMap(compareBy { if (it == "Uncategorized") "￿" else it.lowercase() })

    Scaffold(
        topBar = { TopAppBar(title = { Text("Todos") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) { Icon(Icons.Default.Add, contentDescription = "Add todo") }
        },
    ) { padding ->
        if (todos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No todos yet. Tap + to add one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grouped.forEach { (groupName, groupTodos) ->
                    item(key = "header-$groupName") {
                        Text(
                            groupName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(groupTodos, key = { it.id }) { todo ->
                        TodoRow(
                            todo = todo,
                            subLabel = todo.categoryId?.let { id ->
                                byId[id]?.takeIf { it.parentId != null }?.name
                            },
                            onToggle = { vm.toggleDone(todo) },
                            onClick = {
                                editing = todo
                                showEditor = true
                            },
                            onDelete = { vm.delete(todo) },
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        ModalBottomSheet(onDismissRequest = { showEditor = false }) {
            TodoEditor(
                initial = editing,
                categories = categories,
                onCreateCategory = vm::createCategory,
                onSave = { todo ->
                    vm.save(todo)
                    showEditor = false
                },
            )
        }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    subLabel: String?,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = todo.status == TodoStatus.DONE, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (todo.status == TodoStatus.DONE) TextDecoration.LineThrough else null,
                )
                if (subLabel != null) {
                    Text(subLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun TodoEditor(
    initial: Todo?,
    categories: List<dev.sivarj.assistant.data.Category>,
    onCreateCategory: (String, String?) -> Unit,
    onSave: (Todo) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (initial == null) "New todo" else "Edit todo",
            style = MaterialTheme.typography.titleMedium,
        )
        DictationField(
            value = title,
            onValueChange = { title = it },
            label = "Title",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        EnrichButton(
            rawText = title,
            contentType = ContentType.TODO,
            onEnriched = { enriched ->
                title = enriched.lines().firstOrNull()?.removePrefix("- ")?.trim() ?: enriched.trim()
            },
        )
        DictationField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notes",
            minLines = 2,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        EnrichButton(
            rawText = notes,
            contentType = ContentType.TODO,
            onEnriched = { notes = it },
        )
        CategoryPicker(
            categories = categories,
            selectedId = categoryId,
            onSelect = { categoryId = it },
            onCreate = onCreateCategory,
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.End) {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val base = initial ?: Todo(title = title.trim())
                    onSave(base.copy(title = title.trim(), notes = notes.trim(), categoryId = categoryId))
                },
            ) { Text("Save") }
        }
    }
}
