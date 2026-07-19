package dev.sivarj.assistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sivarj.assistant.data.Category

/** "Parent ▸ Child" for sub-categories, plain name for top-level ones. */
fun categoryDisplayName(category: Category, byId: Map<String, Category>): String {
    val parent = category.parentId?.let { byId[it] }
    return if (parent != null) "${parent.name} ▸ ${category.name}" else category.name
}

/**
 * Dropdown over the existing two-level category tree with an inline
 * "New category…" flow (name + optional parent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreate: (name: String, parentId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val byId = categories.associateBy { it.id }
    val sorted = categories.sortedBy { categoryDisplayName(it, byId).lowercase() }
    val selectedLabel = selectedId?.let { id -> byId[id]?.let { categoryDisplayName(it, byId) } } ?: "None"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            sorted.forEach { category ->
                DropdownMenuItem(
                    text = { Text(categoryDisplayName(category, byId)) },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("+ New category…") },
                onClick = {
                    expanded = false
                    showCreateDialog = true
                },
            )
        }
    }

    if (showCreateDialog) {
        CreateCategoryDialog(
            topLevelCategories = categories.filter { it.parentId == null },
            onCreate = { name, parentId ->
                onCreate(name, parentId)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCategoryDialog(
    topLevelCategories: List<Category>,
    onCreate: (name: String, parentId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    var parentExpanded by remember { mutableStateOf(false) }
    val parentLabel = topLevelCategories.find { it.id == parentId }?.name ?: "None (top-level)"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = parentExpanded,
                    onExpandedChange = { parentExpanded = it },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = parentLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Parent category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = parentExpanded, onDismissRequest = { parentExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("None (top-level)") },
                            onClick = {
                                parentId = null
                                parentExpanded = false
                            },
                        )
                        topLevelCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    parentId = category.id
                                    parentExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), parentId) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
