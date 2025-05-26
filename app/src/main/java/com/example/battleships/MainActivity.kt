package com.example.battleships

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


// Game Logic
data class Ship(val size: Int, var row: Int, var col: Int, var isHorizontal: Boolean)

class GameViewModel : ViewModel() {
    companion object {
        const val BOARD_SIZE = 10
    }

    private val _playerBoard = Array(BOARD_SIZE) { Array(BOARD_SIZE) { ' ' } }
    private val _aiBoard = Array(BOARD_SIZE) { Array(BOARD_SIZE) { ' ' } }
    private val _playerViewOfAi = Array(BOARD_SIZE) { Array(BOARD_SIZE) { ' ' } }
    private var _playerShips = listOf(Ship(5, 0, 0, true), Ship(4, 0, 0, true), Ship(3, 0, 0, true), Ship(3, 0, 0, true), Ship(2, 0, 0, true))
    private val _aiShips = listOf(Ship(5, 0, 0, true), Ship(4, 0, 0, true), Ship(3, 0, 0, true), Ship(3, 0, 0, true), Ship(2, 0, 0, true))
    var gameState by mutableStateOf("place_ships")
    var currentShipIndex by mutableStateOf(0)
    var playerName by mutableStateOf("")
    var message by mutableStateOf("")
    private val totalAiShipTiles by lazy { _aiBoard.sumOf { row -> row.count { it == 'S' } } }
    private var firstAiHit: Pair<Int, Int>? = null
    private var lastAiDirection: String? = null
    private val surroundingTilesToTry = mutableListOf<Pair<Int, Int>>()
    private val sunkAiShips = mutableSetOf<Ship>()
    private val sunkPlayerShips = mutableSetOf<Ship>()

    val playerBoard: Array<Array<Char>> get() = _playerBoard
    val playerViewOfAi: Array<Array<Char>> get() = _playerViewOfAi
    val playerShips: List<Ship> get() = _playerShips

    fun startGame(name: String) {
        playerName = name
        message = "Welcome, $name! Place your ships."
        gameState = "place_ships"
        placeAiShips()
    }

    private fun placeAiShips() {
        val random = java.util.Random()
        _aiShips.forEach { ship ->
            do {
                ship.isHorizontal = random.nextBoolean()
                ship.row = random.nextInt(BOARD_SIZE)
                ship.col = random.nextInt(BOARD_SIZE)
            } while (!canPlaceShip(ship, _aiBoard))
            placeShip(ship, _aiBoard, 'S')
        }
    }

    private fun canPlaceShip(ship: Ship, board: Array<Array<Char>>): Boolean {
        if (ship.isHorizontal && ship.col + ship.size > BOARD_SIZE) return false
        if (!ship.isHorizontal && ship.row + ship.size > BOARD_SIZE) return false

        val startRow = maxOf(0, ship.row - 1)
        val endRow = if (ship.isHorizontal) ship.row + 1 else ship.row + ship.size
        val startCol = maxOf(0, ship.col - 1)
        val endCol = if (ship.isHorizontal) ship.col + ship.size else ship.col + 1

        return (startRow..minOf(BOARD_SIZE - 1, endRow)).all { i ->
            (startCol..minOf(BOARD_SIZE - 1, endCol)).all { j -> board[i][j] == ' ' }
        }
    }

    private fun placeShip(ship: Ship, board: Array<Array<Char>>, char: Char) {
        if (ship.isHorizontal) {
            (ship.col until ship.col + ship.size).forEach { j -> board[ship.row][j] = char }
        } else {
            (ship.row until ship.row + ship.size).forEach { i -> board[i][ship.col] = char }
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
        handleAttack(row, col, _playerViewOfAi, _aiBoard, _aiShips, sunkAiShips, true)
    }

    private fun aiAttack() {
        val random = java.util.Random()
        val (row, col) = when {
            surroundingTilesToTry.isNotEmpty() -> surroundingTilesToTry.removeAt(0)
            firstAiHit != null && lastAiDirection != null -> firstAiHit!!.let { (hitRow, hitCol) ->
                val newPair = when (lastAiDirection) {
                    "up" -> {
                        lastAiDirection = "down"
                        hitRow + 1 to hitCol
                    }
                    "down" -> {
                        lastAiDirection = "up"
                        hitRow - 1 to hitCol
                    }
                    "left" -> {
                        lastAiDirection = "right"
                        hitRow to hitCol + 1
                    }
                    "right" -> {
                        lastAiDirection = "left"
                        hitRow to hitCol - 1
                    }
                    else -> hitRow to hitCol
                }
                if (newPair.first in 0 until BOARD_SIZE && newPair.second in 0 until BOARD_SIZE &&
                    _playerBoard[newPair.first][newPair.second] != 'H' && _playerBoard[newPair.first][newPair.second] != 'M') {
                    newPair
                } else {
                    lastAiDirection = null
                    firstAiHit = null
                    generateRandomTile(random)
                }
            }
            else -> generateRandomTile(random)
        }
        handleAttack(row, col, _playerBoard, _playerBoard, _playerShips, sunkPlayerShips, false)
    }

    private fun generateRandomTile(random: java.util.Random): Pair<Int, Int> {
        var row: Int
        var col: Int
        do {
            row = random.nextInt(BOARD_SIZE)
            col = random.nextInt(BOARD_SIZE)
        } while (_playerBoard[row][col] == 'H' || _playerBoard[row][col] == 'M')
        return row to col
    }

    private fun handleAttack(
        row: Int,
        col: Int,
        viewBoard: Array<Array<Char>>,
        actualBoard: Array<Array<Char>>,
        ships: List<Ship>,
        sunkShips: MutableSet<Ship>,
        isPlayer: Boolean
    ) {
        val isHit = actualBoard[row][col] == 'S'
        viewBoard[row][col] = if (isHit) 'H' else 'M'

        val newlySunkShip = checkIfShipSunk(ships, viewBoard, actualBoard, sunkShips)
        if (newlySunkShip != null) sunkShips.add(newlySunkShip)

        message = buildString {
            append(if (isHit) "${if (isPlayer) "Hit!" else "AI hit your ship!"} " else "${if (isPlayer) "Miss." else "AI missed."} ")
            if (newlySunkShip != null) append("Sunk! ")
            append(if (isHit) "${if (isPlayer) "Go again." else "AI's turn again."}" else "${if (isPlayer) "AI's turn." else "Your turn."}")
        }

        if (checkWin(viewBoard)) {
            gameState = "game_over"
            message = "${if (isPlayer) playerName else "AI"}, ${if (isPlayer) "you win!" else "you lose!"}"
            return
        }

        if (isHit) {
            if (!isPlayer) {
                if (firstAiHit == null) firstAiHit = row to col
                surroundingTilesToTry.clear()
                listOf(
                    (row - 1 to col) to "up",
                    (row + 1 to col) to "down",
                    (row to col - 1) to "left",
                    (row to col + 1) to "right"
                ).forEach { (pos, dir) ->
                    val (r, c) = pos
                    if (r in 0 until BOARD_SIZE && c in 0 until BOARD_SIZE && _playerBoard[r][c] != 'H' && _playerBoard[r][c] != 'M') {
                        surroundingTilesToTry.add(r to c)
                        lastAiDirection = dir
                    }
                }
                if (newlySunkShip != null) {
                    firstAiHit = null
                    lastAiDirection = null
                    surroundingTilesToTry.clear()
                }
                if (surroundingTilesToTry.isNotEmpty()) aiAttack()
            }
        } else if (isPlayer) {
            viewModelScope.launch { delay(500); aiAttack() }
        } else {
            viewModelScope.launch { delay(500) }
            if (surroundingTilesToTry.isEmpty()) {
                if (firstAiHit != null && lastAiDirection != null) return
                firstAiHit = null
                lastAiDirection = null
            }
        }
    }

    private fun checkIfShipSunk(ships: List<Ship>, viewBoard: Array<Array<Char>>, actualBoard: Array<Array<Char>>, sunkShips: MutableSet<Ship>): Ship? {
        return ships.firstOrNull { ship ->
            ship !in sunkShips && run {
                val range = if (ship.isHorizontal) ship.col until ship.col + ship.size else ship.row until ship.row + ship.size
                range.all { idx ->
                    val (r, c) = if (ship.isHorizontal) ship.row to idx else idx to ship.col
                    actualBoard[r][c] == 'S' && viewBoard[r][c] == 'H'
                }
            }
        }
    }

    private fun checkWin(board: Array<Array<Char>>): Boolean {
        val hits = board.sumOf { row -> row.count { it == 'H' } }
        val totalShipTiles = if (board === _playerViewOfAi) totalAiShipTiles else _playerShips.sumOf { it.size }
        return hits == totalShipTiles
    }

    fun restartGame() {
        _playerBoard.forEach { it.fill(' ') }
        _aiBoard.forEach { it.fill(' ') }
        _playerViewOfAi.forEach { it.fill(' ') }
        _playerShips = listOf(Ship(5, 0, 0, true), Ship(4, 0, 0, true), Ship(3, 0, 0, true), Ship(3, 0, 0, true), Ship(2, 0, 0, true))
        gameState = "place_ships"
        currentShipIndex = 0
        message = "Welcome, $playerName! Place your ships."
        firstAiHit = null
        lastAiDirection = null
        surroundingTilesToTry.clear()
        sunkAiShips.clear()
        sunkPlayerShips.clear()
        placeAiShips()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = GameViewModel()
        setContent { BattleshipsApp(viewModel) }
    }
}

@Composable
fun BattleshipsApp(viewModel: GameViewModel) {
    var nameField by remember { mutableStateOf(TextFieldValue()) }
    if (viewModel.playerName.isEmpty()) {
        NameEntryScreen(nameField, { nameField = it }, { if (nameField.text.isNotBlank()) viewModel.startGame(nameField.text) })
    } else {
        GameScreen(viewModel)
    }
}

@Composable
fun NameEntryScreen(nameField: TextFieldValue, onNameChange: (TextFieldValue) -> Unit, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter Your Name", fontSize = 24.sp)
        Spacer(Modifier.height(16.dp))
        TextField(value = nameField, onValueChange = onNameChange, label = { Text("Name") })
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart) { Text("Start Game") }
    }
}

@Composable
fun GameScreen(viewModel: GameViewModel) {
    var isHorizontal by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(viewModel.message, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        if (viewModel.gameState == "place_ships") {
            viewModel.playerShips.getOrNull(viewModel.currentShipIndex)?.size?.let { size ->
                Text("Place Ship ${viewModel.currentShipIndex + 1} (Size: $size)")
                Button(onClick = { isHorizontal = !isHorizontal }) {
                    Text(if (isHorizontal) "Horizontal" else "Vertical")
                }
            }
        }
        Text("Your Board")
        Board(viewModel.playerBoard) { row, col ->
            if (viewModel.gameState == "place_ships") viewModel.tryPlaceShip(row, col, isHorizontal)
        }
        if (viewModel.gameState in listOf("playing", "game_over")) {
            Text("Enemy Board")
            Board(viewModel.playerViewOfAi) { row, col ->
                if (viewModel.gameState == "playing") viewModel.attack(row, col)
            }
        }
        if (viewModel.gameState == "game_over") {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.restartGame() }) { Text("Restart Game") }
        }
    }
}

@Composable
fun Board(board: Array<Array<Char>>, onClick: (Int, Int) -> Unit) {
    Column {
        repeat(GameViewModel.BOARD_SIZE) { i ->
            Row {
                repeat(GameViewModel.BOARD_SIZE) { j ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .border(1.dp, Color.Black)
                            .background(when (board[i][j]) {
                                'S' -> Color.Blue
                                'H' -> Color.Red
                                'M' -> Color.Gray
                                else -> Color.LightGray
                            })
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