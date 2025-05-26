package com.example.battleships

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Room Database Setup
@Entity(tableName = "game_results")
data class GameResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playerName: String,
    val outcome: String,
    val timestamp: Long
)

@Dao
interface GameResultDao {
    @Insert
    suspend fun insert(result: GameResult)

    @Query("SELECT * FROM game_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<GameResult>>
}

@Database(entities = [GameResult::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameResultDao(): GameResultDao
}

// Game Logic
data class Ship(val size: Int, var row: Int, var col: Int, var isHorizontal: Boolean)

class GameViewModel : ViewModel() {
    private val _playerBoard = Array(10) { Array(10) { ' ' } } // 'S' for ship, 'H' for hit, 'M' for miss
    private val _aiBoard = Array(10) { Array(10) { ' ' } }
    private val _playerViewOfAi = Array(10) { Array(10) { ' ' } }
    private var _playerShips = listOf(Ship(5, 0, 0, true), Ship(4, 0, 0, true), Ship(3, 0, 0, true), Ship(3, 0, 0, true), Ship(2, 0, 0, true))
    private val _aiShips = listOf(Ship(5, 0, 0, true), Ship(4, 0, 0, true), Ship(3, 0, 0, true), Ship(3, 0, 0, true), Ship(2, 0, 0, true))
    var gameState by mutableStateOf("place_ships")
    var currentShipIndex by mutableStateOf(0)
    var playerName by mutableStateOf("")
    var message by mutableStateOf("")
    var gameResults by mutableStateOf<List<GameResult>>(emptyList())
    private lateinit var dao: GameResultDao

    // Public accessors
    val playerBoard: Array<Array<Char>> get() = _playerBoard
    val playerViewOfAi: Array<Array<Char>> get() = _playerViewOfAi
    val playerShips: List<Ship> get() = _playerShips

    fun setDatabase(dao: GameResultDao) {
        this.dao = dao
        viewModelScope.launch {
            dao.getAllResults().collect { gameResults = it }
        }
    }

    fun startGame(name: String) {
        playerName = name
        message = "Welcome, $name! Place your ships."
        gameState = "place_ships"
        placeAiShips()
    }

    private fun placeAiShips() {
        val random = java.util.Random()
        for (ship in _aiShips) {
            var placed = false
            while (!placed) {
                ship.isHorizontal = random.nextBoolean()
                ship.row = random.nextInt(10)
                ship.col = random.nextInt(10)
                if (canPlaceShip(ship, _aiBoard)) {
                    placeShip(ship, _aiBoard, 'S')
                    placed = true
                }
            }
        }
    }

    private fun canPlaceShip(ship: Ship, board: Array<Array<Char>>): Boolean {
        if (ship.isHorizontal) {
            if (ship.col + ship.size > 10) return false
            for (j in ship.col until ship.col + ship.size) {
                if (board[ship.row][j] != ' ') return false
            }
        } else {
            if (ship.row + ship.size > 10) return false
            for (i in ship.row until ship.row + ship.size) {
                if (board[i][ship.col] != ' ') return false
            }
        }
        return true
    }

    private fun placeShip(ship: Ship, board: Array<Array<Char>>, char: Char) {
        if (ship.isHorizontal) {
            for (j in ship.col until ship.col + ship.size) {
                board[ship.row][j] = char
            }
        } else {
            for (i in ship.row until ship.row + ship.size) {
                board[i][ship.col] = char
            }
        }
    }

    fun tryPlaceShip(row: Int, col: Int, isHorizontal: Boolean) {
        if (gameState != "place_ships" || currentShipIndex >= _playerShips.size) return
        val ship = _playerShips[currentShipIndex].copy(row = row, col = col, isHorizontal = isHorizontal)
        if (canPlaceShip(ship, _playerBoard)) {
            placeShip(ship, _playerBoard, 'S')
            currentShipIndex++
            if (currentShipIndex == _playerShips.size) {
                gameState = "playing"
                message = "$playerName, attack the enemy board!"
            }
        } else {
            message = "Cannot place ship there. Try again."
        }
    }

    fun attack(row: Int, col: Int) {
        if (gameState != "playing" || _playerViewOfAi[row][col] != ' ') return
        _playerViewOfAi[row][col] = if (_aiBoard[row][col] == 'S') 'H' else 'M'
        if (checkWin(_playerViewOfAi)) {
            gameState = "game_over"
            message = "$playerName, you win!"
            saveResult("Win")
        } else {
            aiAttack()
        }
    }

    private fun aiAttack() {
        val random = java.util.Random()
        var row: Int
        var col: Int
        do {
            row = random.nextInt(10)
            col = random.nextInt(10)
        } while (_playerBoard[row][col] == 'H' || _playerBoard[row][col] == 'M')
        _playerBoard[row][col] = if (_playerBoard[row][col] == 'S') 'H' else 'M'
        if (checkWin(_playerBoard)) {
            gameState = "game_over"
            message = "$playerName, you lose!"
            saveResult("Loss")
        } else if (_playerBoard[row][col] == 'H') {
            message = "AI hit your ship! Your turn."
        } else {
            message = "AI missed. Your turn."
        }
    }

    private fun checkWin(board: Array<Array<Char>>): Boolean {
        return board.all { row -> row.all { it != 'S' } }
    }

    private fun saveResult(outcome: String) {
        viewModelScope.launch {
            dao.insert(GameResult(playerName = playerName, outcome = outcome, timestamp = System.currentTimeMillis()))
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "game-db").build()
        val viewModel = GameViewModel()
        viewModel.setDatabase(db.gameResultDao())
        setContent {
            BattleshipsApp(viewModel)
        }
    }
}

@Composable
fun BattleshipsApp(viewModel: GameViewModel) {
    var nameField by remember { mutableStateOf(TextFieldValue()) }
    if (viewModel.playerName.isEmpty()) {
        NameEntryScreen(
            nameField = nameField,
            onNameChange = { nameField = it },
            onStart = { viewModel.startGame(nameField.text) }
        )
    } else {
        GameScreen(viewModel)
    }
}

@Composable
fun NameEntryScreen(nameField: TextFieldValue, onNameChange: (TextFieldValue) -> Unit, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter Your Name", fontSize = 24.sp)
        Spacer(Modifier.height(16.dp))
        TextField(
            value = nameField,
            onValueChange = onNameChange,
            label = { Text("Name") }
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { if (nameField.text.isNotBlank()) onStart() }) {
            Text("Start Game")
        }
    }
}

@Composable
fun GameScreen(viewModel: GameViewModel) {
    var isHorizontal by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(viewModel.message, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        if (viewModel.gameState == "place_ships") {
            Text("Place Ship ${viewModel.currentShipIndex + 1} (Size: ${viewModel.playerShips.getOrNull(viewModel.currentShipIndex)?.size})")
            Button(onClick = { isHorizontal = !isHorizontal }) {
                Text(if (isHorizontal) "Horizontal" else "Vertical")
            }
        }
        Text("Your Board")
        Board(viewModel.playerBoard, onClick = { row, col ->
            if (viewModel.gameState == "place_ships") {
                viewModel.tryPlaceShip(row, col, isHorizontal)
            }
        })
        if (viewModel.gameState == "playing" || viewModel.gameState == "game_over") {
            Text("Enemy Board")
            Board(viewModel.playerViewOfAi, onClick = { row, col ->
                if (viewModel.gameState == "playing") {
                    viewModel.attack(row, col)
                }
            })
        }
        Spacer(Modifier.height(16.dp))
        Text("Game History")
        LazyColumn {
            items(viewModel.gameResults) { result ->
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                val dateTime = LocalDateTime.ofEpochSecond(result.timestamp / 1000, 0, java.time.ZoneOffset.UTC)
                Text("${result.playerName}: ${result.outcome} at ${formatter.format(dateTime)}")
            }
        }
    }
}

@Composable
fun Board(board: Array<Array<Char>>, onClick: (Int, Int) -> Unit) {
    Column {
        for (i in 0 until 10) {
            Row {
                for (j in 0 until 10) {
                    val color = when (board[i][j]) {
                        'S' -> Color.Blue
                        'H' -> Color.Red
                        'M' -> Color.Gray
                        else -> Color.LightGray
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(color)
                            .clickable { onClick(i, j) }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (dragAmount > 0) onClick(i, j)
                                }
                            }
                    )
                }
            }
        }
    }
}

// Attribution: This is an original implementation of the Battleships game for Android, inspired by standard Battleships game rules. No direct code samples were used from external sources.