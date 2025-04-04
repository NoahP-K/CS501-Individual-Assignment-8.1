package com.example.individualassignment_81

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// 1. Entity: Represents the table in SQLite using Room
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Auto-generated primary key
    val description: String, // task description
    val done: Boolean      // true if task is finished, false if pending
)

// 2. DAO: Data Access Object defines DB operations
@Dao
interface UserDao {
    // Return all tasks as a Flow to observe real-time changes
    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun getAll(): Flow<List<Task>>

    //return a flow of all pending tasks
    @Query("SELECT * FROM tasks WHERE done=false ORDER BY id DESC")
    fun getAllPending(): Flow<List<Task>>

    //return a flow of all done tasks
    @Query("SELECT * FROM tasks WHERE done=true ORDER BY id DESC")
    fun getAllDone(): Flow<List<Task>>

    // Insert task (replace if conflict)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    // Update task record
    @Update
    suspend fun update(task: Task)

    // Delete task record
    @Delete
    suspend fun delete(task: Task)
}

// 3. Room Database definition
@Database(entities = [Task::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Create or return singleton instance of DB
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "user_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

// 4. ViewModel: Handles DB operations + state tracking
class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).userDao()
    val allTasks: Flow<List<Task>> = dao.getAll() // Observable list of all tasks
    val doneTasks: Flow<List<Task>> = dao.getAllDone() // Observable list of done tasks
    val pendingTasks: Flow<List<Task>> = dao.getAllPending() // Observable list of pending tasks


    private val _lastUpdatedId = MutableStateFlow<Int?>(null) // Track last updated ID
    val lastUpdatedId: StateFlow<Int?> = _lastUpdatedId

    // Add new task
    fun addTask(description: String, done: Boolean) {
        viewModelScope.launch {
            dao.insert(Task(description = description, done = done))
        }
    }

    // Update existing task
    fun updateTask(task: Task) {
        viewModelScope.launch {
            dao.update(task)
            _lastUpdatedId.value = task.id // Mark this user as updated
            delay(2000) // Clear mark after 2 seconds
            _lastUpdatedId.value = null
        }
    }

    // Delete task
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.delete(task)
        }
    }
}

// 5. MainActivity: Entry point
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val context = LocalContext.current
                // Get ViewModel using factory since it needs Application context
                val viewModel: TaskViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
                        context.applicationContext as Application
                    )
                )
                MainScreen(viewModel) // Call main screen
            }
        }
    }
}

// 6. Composable UI for managing tasks
@Composable
fun MainScreen(viewModel: TaskViewModel) {
    var filter by remember { mutableStateOf("none")}    //indicates what to filter for
    //select query based on filter value
    val users by when(filter){
        "done"->viewModel.doneTasks.collectAsState(initial = emptyList())
        "pending"->viewModel.pendingTasks.collectAsState(initial = emptyList())
        else->viewModel.allTasks.collectAsState(initial = emptyList())
    }
    val lastUpdatedId by viewModel.lastUpdatedId.collectAsState() // Track updated tasks

    var description by remember { mutableStateOf("") } // description input

    Column(Modifier.padding(16.dp)) {
        // Input for description
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Describe your task") },
            modifier = Modifier.fillMaxWidth()
                .padding(top = 20.dp)
        )

        // Button to add user to DB
        Button(
            onClick = {
                if (description.isNotBlank()) {
                    viewModel.addTask(description, false)
                    description = "" // Clear input
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Add Task")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // Separate input and list

        Row(

        ){
            Button(
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 75.dp),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray // just set what you need
                ),
                onClick = {filter = "none"}
            ){
                Text(text = "All Tasks",
                    fontSize = 15.sp)
            }
            Button(
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 75.dp),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.LightGray // just set what you need
                ),
                onClick = {filter = "pending"}
            ){
                Text(text = "Pending Tasks",
                    fontSize = 15.sp)
            }
            Button(
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 75.dp),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray // just set what you need
                ),
                onClick = {filter = "done"}
            ){
                Text(text = "Completed Tasks",
                    fontSize = 15.sp)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // Separate input and list
        // Scrollable list of users using LazyColumn
        LazyColumn {
            items(users) { task ->
                var updatedDescription by remember(task.id) { mutableStateOf(task.description) } // Editable name
                var updatedCompletion by remember(task.id) { mutableStateOf(task.done) } // Editable age

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    //button to check as done
                    IconButton(
                        modifier = Modifier.fillMaxHeight().border(3.dp, Color.Black),
                        onClick = {
                                updatedCompletion = !updatedCompletion
                                val updatedTask = task.copy(
                                    description = updatedDescription,
                                    done = updatedCompletion
                                )
                                viewModel.updateTask(updatedTask)
                        },
                        //show the checkmark only when the task is considered done
                        colors =  IconButtonColors(
                            containerColor = Color.White,
                            contentColor = if(updatedCompletion) Color.Black else Color.White,
                            disabledContainerColor = Color.White,
                            disabledContentColor = Color.White
                        )
                    ) {
                        Icon(
                            modifier = Modifier.fillMaxSize(),
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Done?")
                    }

                    Column(Modifier.weight(1f)) {
                        // Editable name field
                        OutlinedTextField(
                            value = updatedDescription,
                            onValueChange = { updatedDescription = it },
                            label = { Text("Edit Name") },
                            singleLine = true
                        )

                        // Display update indicator if this user was just updated
                        if (task.id == lastUpdatedId) {
                            Text("Updated!", color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Column {
                        // Update task button
                        Button(
                            onClick = {
                                if (updatedDescription.isNotBlank()) {
                                    val updatedTask = task.copy(
                                        description = updatedDescription,
                                        done = updatedCompletion
                                    )
                                    viewModel.updateTask(updatedTask)
                                }
                            }
                        ) {
                            Text("Update")
                        }

                        Spacer(Modifier.height(4.dp))

                        // Delete user button
                        Button(onClick = { viewModel.deleteTask(task) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}