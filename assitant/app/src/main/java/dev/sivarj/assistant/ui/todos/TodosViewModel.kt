package dev.sivarj.assistant.ui.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sivarj.assistant.data.AppDatabase
import dev.sivarj.assistant.data.Category
import dev.sivarj.assistant.data.CategoryType
import dev.sivarj.assistant.data.Todo
import dev.sivarj.assistant.data.TodoStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodosViewModel(private val db: AppDatabase) : ViewModel() {
    val todos = db.todoDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = db.categoryDao().observeByType(CategoryType.TODO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(todo: Todo) {
        viewModelScope.launch { db.todoDao().upsert(todo.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun toggleDone(todo: Todo) {
        val flipped = if (todo.status == TodoStatus.OPEN) TodoStatus.DONE else TodoStatus.OPEN
        save(todo.copy(status = flipped))
    }

    fun delete(todo: Todo) {
        viewModelScope.launch { db.todoDao().softDelete(todo.id) }
    }

    fun createCategory(name: String, parentId: String?) {
        viewModelScope.launch {
            db.categoryDao().upsert(Category(name = name, type = CategoryType.TODO, parentId = parentId))
        }
    }
}
