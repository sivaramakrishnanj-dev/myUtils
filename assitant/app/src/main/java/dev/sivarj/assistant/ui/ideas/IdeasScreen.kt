package dev.sivarj.assistant.ui.ideas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sivarj.assistant.data.AppDatabase
import dev.sivarj.assistant.data.Category
import dev.sivarj.assistant.data.CategoryType
import dev.sivarj.assistant.data.Idea
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
    val ideas by vm.ideas.collectAsState()
    val categories by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<Idea?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val byId = categories.associateBy { it.id }
    val grouped = ideas.groupBy { idea ->
        val cat = idea.categoryId?.let { byId[it] }
        val top = cat?.parentId?.let { byId[it] } ?: cat
        top?.name ?: "Uncategorized"
    }.toSortedMap(compareBy { if (it == "Uncategorized") "￿" else it.lowercase() })

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ideas") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) { Icon(Icons.Default.Add, contentDescription = "New idea") }
        },
    ) { padding ->
        if (ideas.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No ideas captured yet.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grouped.forEach { (groupName, groupIdeas) ->
                    item(key = "header-$groupName") {
                        Text(
                            groupName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(groupIdeas, key = { it.id }) { idea ->
                        Card(onClick = {
                            editing = idea
                            showEditor = true
                        }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(idea.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                    val sub = idea.categoryId?.let { id -> byId[id]?.takeIf { it.parentId != null }?.name }
                                    if (sub != null) {
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                                IconButton(onClick = { vm.delete(idea) }) {
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
            var content by remember { mutableStateOf(editing?.content ?: "") }
            var categoryId by remember { mutableStateOf(editing?.categoryId) }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (editing == null) "New idea" else "Edit idea",
                    style = MaterialTheme.typography.titleMedium,
                )
                DictationField(
                    value = content,
                    onValueChange = { content = it },
                    label = "Your idea",
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { categoryId = it },
                    onCreate = vm::createCategory,
                )
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        enabled = content.isNotBlank(),
                        onClick = {
                            val base = editing ?: Idea(content = content.trim())
                            vm.save(base.copy(content = content.trim(), categoryId = categoryId))
                            showEditor = false
                        },
                    ) { Text("Save") }
                }
            }
        }
    }
}
