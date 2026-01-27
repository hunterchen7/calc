package com.calc.emulator

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import com.calc.emulator.ui.theme.TI84EmulatorTheme
import kotlinx.coroutines.*
import java.io.InputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        const val CYCLES_PER_TICK = 10000
        const val FRAME_INTERVAL_MS = 16L // ~60 FPS
    }

    private val emulator = EmulatorBridge()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!emulator.create()) {
            Log.e(TAG, "Failed to create emulator")
        }

        setContent {
            TI84EmulatorTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EmulatorScreen(emulator)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        emulator.destroy()
    }
}

@Composable
fun EmulatorScreen(emulator: EmulatorBridge) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Emulator state
    var isRunning by remember { mutableStateOf(false) }
    var romLoaded by remember { mutableStateOf(false) }
    var romName by remember { mutableStateOf<String?>(null) }

    // Framebuffer bitmap
    val bitmap = remember {
        Bitmap.createBitmap(
            emulator.getWidth(),
            emulator.getHeight(),
            Bitmap.Config.ARGB_8888
        )
    }
    var frameCounter by remember { mutableIntStateOf(0) }

    // ROM picker launcher
    val romPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                inputStream?.use { stream ->
                    val romBytes = stream.readBytes()
                    val result = emulator.loadRom(romBytes)
                    if (result == 0) {
                        romLoaded = true
                        romName = uri.lastPathSegment ?: "ROM"
                        Log.i("EmulatorScreen", "ROM loaded: ${romBytes.size} bytes")
                    } else {
                        Log.e("EmulatorScreen", "Failed to load ROM: $result")
                    }
                }
            } catch (e: Exception) {
                Log.e("EmulatorScreen", "Error loading ROM", e)
            }
        }
    }

    // Emulation loop
    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (isRunning) {
                withContext(Dispatchers.Default) {
                    emulator.runCycles(MainActivity.CYCLES_PER_TICK)
                }
                frameCounter++
                delay(MainActivity.FRAME_INTERVAL_MS)
            }
        }
    }

    // Update framebuffer on each frame
    LaunchedEffect(frameCounter) {
        emulator.copyFramebufferToBitmap(bitmap)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Control buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { romPicker.launch(arrayOf("*/*")) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (romLoaded) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (romLoaded) "ROM Loaded" else "Import ROM")
            }

            Button(
                onClick = { isRunning = !isRunning },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFFF5722) else Color(0xFF4CAF50)
                )
            ) {
                Text(if (isRunning) "Pause" else "Run")
            }

            Button(
                onClick = {
                    emulator.reset()
                    frameCounter++
                }
            ) {
                Text("Reset")
            }
        }

        // ROM info
        romName?.let {
            Text(
                text = "ROM: $it",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Screen display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(320f / 240f)
                .background(Color.Black, RoundedCornerShape(4.dp))
                .padding(4.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Emulator screen",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Keypad
        Keypad(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onKeyDown = { row, col ->
                emulator.setKey(row, col, true)
                frameCounter++
            },
            onKeyUp = { row, col ->
                emulator.setKey(row, col, false)
                frameCounter++
            }
        )
    }
}

// Key definition with styling info
data class KeyDef(
    val label: String,
    val row: Int,
    val col: Int,
    val style: KeyStyle = KeyStyle.DARK,
    val secondLabel: String? = null,  // Blue 2nd function label
    val alphaLabel: String? = null,   // Green alpha label
    val secondLabelColor: Color? = null,
    val alphaLabelColor: Color? = null
)

enum class KeyStyle {
    DARK,       // Dark gray - most keys
    YELLOW,     // Blue - 2nd key
    GREEN,      // Green - alpha key
    WHITE,      // Light gray - number/function keys
    BLUE,       // Light gray - enter key
    ARROW       // Arrow keys
}

@Composable
fun Keypad(
    modifier: Modifier = Modifier,
    onKeyDown: (row: Int, col: Int) -> Unit,
    onKeyUp: (row: Int, col: Int) -> Unit
) {
    // TI-84 Plus CE accurate keypad layout
    // Based on actual key matrix mapping
    Column(
        modifier = modifier
            .background(Color(0xFF1B1B1B))
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Row 1: Function keys (y=, window, zoom, trace, graph)
        KeyRow(
            keys = listOf(
                KeyDef("y=", 0, 0, KeyStyle.WHITE, secondLabel = "stat plot", alphaLabel = "f1"),
                KeyDef("window", 0, 1, KeyStyle.WHITE, secondLabel = "tblset", alphaLabel = "f2"),
                KeyDef("zoom", 0, 2, KeyStyle.WHITE, secondLabel = "format", alphaLabel = "f3"),
                KeyDef("trace", 0, 3, KeyStyle.WHITE, secondLabel = "calc", alphaLabel = "f4"),
                KeyDef("graph", 0, 4, KeyStyle.WHITE, secondLabel = "table", alphaLabel = "f5")
            ),
            modifier = Modifier.weight(1f),
            onKeyDown = onKeyDown,
            onKeyUp = onKeyUp
        )

        // Rows 2-3: Keys on left, D-pad on right (2 rows only)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Left side: 3x2 grid of keys
            Column(
                modifier = Modifier.weight(3f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Row 2: 2nd, mode, del
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    KeyButton(
                        keyDef = KeyDef("2nd", 1, 0, KeyStyle.YELLOW),
                        modifier = Modifier.weight(1f),
                        onDown = { onKeyDown(1, 0) },
                        onUp = { onKeyUp(1, 0) }
                    )
                    KeyButton(
                        keyDef = KeyDef("mode", 1, 1, secondLabel = "quit"),
                        modifier = Modifier.weight(1f),
                        onDown = { onKeyDown(1, 1) },
                        onUp = { onKeyUp(1, 1) }
                    )
                    KeyButton(
                        keyDef = KeyDef("del", 1, 2, secondLabel = "ins"),
                        modifier = Modifier.weight(1f),
                        onDown = { onKeyDown(1, 2) },
                        onUp = { onKeyUp(1, 2) }
                    )
                }
                // Row 3: alpha, x,t,θ,n, stat
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    KeyButton(
                        keyDef = KeyDef("alpha", 2, 0, KeyStyle.GREEN, secondLabel = "A-lock"),
                        modifier = Modifier.weight(1f),
                        onDown = { onKeyDown(2, 0) },
                        onUp = { onKeyUp(2, 0) }
                    )
                    KeyButton(
                        keyDef = KeyDef("X,T,θ,n", 2, 1, secondLabel = "link"),
                        modifier = Modifier.weight(1f),
                        onDown = { onKeyDown(2, 1) },
                        onUp = { onKeyUp(2, 1) }
                    )
                    KeyButton(
                        keyDef = KeyDef("stat", 2, 2, secondLabel = "list"),
                        modifier = Modifier.weight(1f),
                        onDown = { onKeyDown(2, 2) },
                        onUp = { onKeyUp(2, 2) }
                    )
                }
            }

            // D-Pad on the right (spans 2 rows only)
            DPad(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                onKeyDown = onKeyDown,
                onKeyUp = onKeyUp
            )
        }

        // Row 4: math, apps, prgm, vars, clear (separate row)
        KeyRow(
            keys = listOf(
                KeyDef("math", 3, 0, secondLabel = "test", alphaLabel = "A"),
                KeyDef("apps", 3, 1, secondLabel = "angle", alphaLabel = "B"),
                KeyDef("prgm", 3, 2, secondLabel = "draw", alphaLabel = "C"),
                KeyDef("vars", 3, 3, secondLabel = "distr", alphaLabel = "D"),
                KeyDef("clear", 3, 5)
            ),
            modifier = Modifier.weight(1f),
            onKeyDown = onKeyDown,
            onKeyUp = onKeyUp
        )

        // Row 5: x⁻¹, sin, cos, tan, ^
        KeyRow(
            keys = listOf(
                KeyDef("x⁻¹", 4, 0, secondLabel = "matrix"),
                KeyDef("sin", 4, 1, secondLabel = "sin⁻¹", alphaLabel = "E"),
                KeyDef("cos", 4, 2, secondLabel = "cos⁻¹", alphaLabel = "F"),
                KeyDef("tan", 4, 3, secondLabel = "tan⁻¹", alphaLabel = "G"),
                KeyDef("^", 4, 4, secondLabel = "π", alphaLabel = "H")
            ),
            modifier = Modifier.weight(1f),
            onKeyDown = onKeyDown,
            onKeyUp = onKeyUp
        )

        // Row 6: x², ,, (, ), ÷
        KeyRow(
            keys = listOf(
                KeyDef("x²", 5, 0, secondLabel = "√"),
                KeyDef(",", 5, 1, secondLabel = "EE", alphaLabel = "J"),
                KeyDef("(", 5, 2, secondLabel = "{", alphaLabel = "K"),
                KeyDef(")", 5, 3, secondLabel = "}", alphaLabel = "L"),
                KeyDef("÷", 5, 4, KeyStyle.WHITE, secondLabel = "e", alphaLabel = "M")
            ),
            modifier = Modifier.weight(1f),
            onKeyDown = onKeyDown,
            onKeyUp = onKeyUp
        )

        NumericColumns(
            modifier = Modifier.weight(4.8f),
            onKeyDown = onKeyDown,
            onKeyUp = onKeyUp
        )
    }
}

@Composable
fun NumericColumns(
    modifier: Modifier = Modifier,
    onKeyDown: (row: Int, col: Int) -> Unit,
    onKeyUp: (row: Int, col: Int) -> Unit
) {
    val keySpacing = 2.dp
    val numberKeyWeight = 1.42f
    val darkKeyWeight = 0.96f
    val enterKeyWeight = 1.22f
    val numberKeyPad = 3.dp
    val outerColumnBottomInset = 14.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Column 1: log, ln, sto→, on
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = outerColumnBottomInset),
            verticalArrangement = Arrangement.spacedBy(keySpacing)
        ) {
            KeyButton(
                keyDef = KeyDef("log", 6, 0, secondLabel = "10ˣ", alphaLabel = "N"),
                modifier = Modifier.weight(darkKeyWeight).fillMaxWidth(),
                onDown = { onKeyDown(6, 0) },
                onUp = { onKeyUp(6, 0) }
            )
            KeyButton(
                keyDef = KeyDef("ln", 7, 0, secondLabel = "eˣ", alphaLabel = "S"),
                modifier = Modifier.weight(darkKeyWeight).fillMaxWidth(),
                onDown = { onKeyDown(7, 0) },
                onUp = { onKeyUp(7, 0) }
            )
            KeyButton(
                keyDef = KeyDef("sto→", 8, 0, secondLabel = "rcl", alphaLabel = "X"),
                modifier = Modifier.weight(darkKeyWeight).fillMaxWidth(),
                onDown = { onKeyDown(8, 0) },
                onUp = { onKeyUp(8, 0) }
            )
            KeyButton(
                keyDef = KeyDef("on", 9, 0, secondLabel = "off"),
                modifier = Modifier.weight(darkKeyWeight).fillMaxWidth(),
                onDown = { onKeyDown(9, 0) },
                onUp = { onKeyUp(9, 0) }
            )
        }

        // Column 2: 7, 4, 1, 0
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(keySpacing)
        ) {
            KeyButton(
                keyDef = KeyDef("7", 6, 1, KeyStyle.WHITE, secondLabel = "u", alphaLabel = "O"),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(6, 1) },
                onUp = { onKeyUp(6, 1) }
            )
            KeyButton(
                keyDef = KeyDef("4", 7, 1, KeyStyle.WHITE, secondLabel = "L4", alphaLabel = "T"),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(7, 1) },
                onUp = { onKeyUp(7, 1) }
            )
            KeyButton(
                keyDef = KeyDef("1", 8, 1, KeyStyle.WHITE, secondLabel = "L1", alphaLabel = "Y"),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(8, 1) },
                onUp = { onKeyUp(8, 1) }
            )
            KeyButton(
                keyDef = KeyDef("0", 9, 1, KeyStyle.WHITE, secondLabel = "catalog", alphaLabel = " "),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(9, 1) },
                onUp = { onKeyUp(9, 1) }
            )
        }

        // Column 3: 8, 5, 2, .
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(keySpacing)
        ) {
            KeyButton(
                keyDef = KeyDef("8", 6, 2, KeyStyle.WHITE, secondLabel = "v", alphaLabel = "P"),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(6, 2) },
                onUp = { onKeyUp(6, 2) }
            )
            KeyButton(
                keyDef = KeyDef("5", 7, 2, KeyStyle.WHITE, secondLabel = "L5", alphaLabel = "U"),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(7, 2) },
                onUp = { onKeyUp(7, 2) }
            )
            KeyButton(
                keyDef = KeyDef("2", 8, 2, KeyStyle.WHITE, secondLabel = "L2", alphaLabel = "Z"),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(8, 2) },
                onUp = { onKeyUp(8, 2) }
            )
            KeyButton(
                keyDef = KeyDef(".", 9, 2, KeyStyle.WHITE, secondLabel = "i", alphaLabel = ":"),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(9, 2) },
                onUp = { onKeyUp(9, 2) }
            )
        }

        // Column 4: 9, 6, 3, (−)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(keySpacing)
        ) {
            KeyButton(
                keyDef = KeyDef("9", 6, 3, KeyStyle.WHITE, secondLabel = "w", alphaLabel = "Q"),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(6, 3) },
                onUp = { onKeyUp(6, 3) }
            )
            KeyButton(
                keyDef = KeyDef("6", 7, 3, KeyStyle.WHITE, secondLabel = "L6", alphaLabel = "V"),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(7, 3) },
                onUp = { onKeyUp(7, 3) }
            )
            KeyButton(
                keyDef = KeyDef("3", 8, 3, KeyStyle.WHITE, secondLabel = "L3", alphaLabel = "θ"),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(8, 3) },
                onUp = { onKeyUp(8, 3) }
            )
            KeyButton(
                keyDef = KeyDef("(−)", 9, 3, KeyStyle.WHITE, secondLabel = "ans", alphaLabel = "?"),
                modifier = Modifier
                    .weight(numberKeyWeight)
                    .fillMaxWidth()
                    .padding(horizontal = numberKeyPad),
                onDown = { onKeyDown(9, 3) },
                onUp = { onKeyUp(9, 3) }
            )
        }

        // Column 5: ×, −, +, enter
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = outerColumnBottomInset),
            verticalArrangement = Arrangement.spacedBy(keySpacing)
        ) {
            KeyButton(
                keyDef = KeyDef("×", 6, 4, KeyStyle.WHITE, secondLabel = "[", alphaLabel = "R"),
                modifier = Modifier.weight(darkKeyWeight).fillMaxWidth(),
                onDown = { onKeyDown(6, 4) },
                onUp = { onKeyUp(6, 4) }
            )
            KeyButton(
                keyDef = KeyDef("−", 7, 4, KeyStyle.WHITE, secondLabel = "]", alphaLabel = "W"),
                modifier = Modifier.weight(darkKeyWeight).fillMaxWidth(),
                onDown = { onKeyDown(7, 4) },
                onUp = { onKeyUp(7, 4) }
            )
            KeyButton(
                keyDef = KeyDef("+", 8, 4, KeyStyle.WHITE, secondLabel = "mem", alphaLabel = "\""),
                modifier = Modifier.weight(darkKeyWeight).fillMaxWidth(),
                onDown = { onKeyDown(8, 4) },
                onUp = { onKeyUp(8, 4) }
            )
            KeyButton(
                keyDef = KeyDef("enter", 9, 4, KeyStyle.BLUE, secondLabel = "entry", alphaLabel = "solve"),
                modifier = Modifier.weight(enterKeyWeight).fillMaxWidth(),
                onDown = { onKeyDown(9, 4) },
                onUp = { onKeyUp(9, 4) }
            )
        }
    }
}

@Composable
fun DPad(
    modifier: Modifier = Modifier,
    onKeyDown: (row: Int, col: Int) -> Unit,
    onKeyUp: (row: Int, col: Int) -> Unit
) {
    val sweepAngle = 90f
    val segmentColor = Color(0xFFE3E3E3)
    val pressedColor = Color(0xFFCECECE)
    val borderColor = Color(0xFFB5B5B5)
    val arrowColor = Color(0xFF2B2B2B)
    val gapColor = Color(0xFF1B1B1B)
    val gapWidthScale = 0.16f
    val innerRadiusScale = 0.45f

    val upAngle = 270f
    val leftAngle = 180f
    val rightAngle = 0f
    val downAngle = 90f

    var pressedDir by remember { mutableStateOf<DPadDirection?>(null) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = { offset ->
                    val hitSize = Size(size.width.toFloat(), size.height.toFloat())
                    val hit = hitTestDPad(
                        offset = offset,
                        size = hitSize,
                        sweepAngle = sweepAngle,
                        innerRadiusScale = innerRadiusScale,
                        gapWidthScale = gapWidthScale
                    )
                    if (hit == null) {
                        return@detectTapGestures
                    }
                    pressedDir = hit
                    when (hit) {
                        DPadDirection.UP -> onKeyDown(1, 3)
                        DPadDirection.LEFT -> onKeyDown(2, 3)
                        DPadDirection.RIGHT -> onKeyDown(2, 4)
                        DPadDirection.DOWN -> onKeyDown(3, 4)
                    }
                    try {
                        awaitRelease()
                    } finally {
                        when (hit) {
                            DPadDirection.UP -> onKeyUp(1, 3)
                            DPadDirection.LEFT -> onKeyUp(2, 3)
                            DPadDirection.RIGHT -> onKeyUp(2, 4)
                            DPadDirection.DOWN -> onKeyUp(3, 4)
                        }
                        pressedDir = null
                    }
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        DPadSegment(
            startAngle = upAngle - sweepAngle / 2f,
            sweepAngle = sweepAngle,
            directionAngle = upAngle,
            innerRadiusScale = innerRadiusScale,
            gapWidthScale = gapWidthScale,
            fillColor = segmentColor,
            pressedColor = pressedColor,
            borderColor = borderColor,
            arrowColor = arrowColor,
            isPressed = pressedDir == DPadDirection.UP
        )
        DPadSegment(
            startAngle = leftAngle - sweepAngle / 2f,
            sweepAngle = sweepAngle,
            directionAngle = leftAngle,
            innerRadiusScale = innerRadiusScale,
            gapWidthScale = gapWidthScale,
            fillColor = segmentColor,
            pressedColor = pressedColor,
            borderColor = borderColor,
            arrowColor = arrowColor,
            isPressed = pressedDir == DPadDirection.LEFT
        )
        DPadSegment(
            startAngle = rightAngle - sweepAngle / 2f,
            sweepAngle = sweepAngle,
            directionAngle = rightAngle,
            innerRadiusScale = innerRadiusScale,
            gapWidthScale = gapWidthScale,
            fillColor = segmentColor,
            pressedColor = pressedColor,
            borderColor = borderColor,
            arrowColor = arrowColor,
            isPressed = pressedDir == DPadDirection.RIGHT
        )
        DPadSegment(
            startAngle = downAngle - sweepAngle / 2f,
            sweepAngle = sweepAngle,
            directionAngle = downAngle,
            innerRadiusScale = innerRadiusScale,
            gapWidthScale = gapWidthScale,
            fillColor = segmentColor,
            pressedColor = pressedColor,
            borderColor = borderColor,
            arrowColor = arrowColor,
            isPressed = pressedDir == DPadDirection.DOWN
        )

        DPadGaps(
            gapWidthScale = gapWidthScale,
            color = gapColor
        )

        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF1B1B1B), CircleShape)
        )
    }
}

@Composable
fun KeyRow(
    keys: List<KeyDef>,
    modifier: Modifier = Modifier,
    onKeyDown: (row: Int, col: Int) -> Unit,
    onKeyUp: (row: Int, col: Int) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        keys.forEach { keyDef ->
            KeyButton(
                keyDef = keyDef,
                modifier = Modifier.weight(1f),
                onDown = { onKeyDown(keyDef.row, keyDef.col) },
                onUp = { onKeyUp(keyDef.row, keyDef.col) }
            )
        }
    }
}

@Composable
fun DPadSegment(
    startAngle: Float,
    sweepAngle: Float,
    directionAngle: Float,
    innerRadiusScale: Float,
    gapWidthScale: Float,
    fillColor: Color,
    pressedColor: Color,
    borderColor: Color,
    arrowColor: Color,
    isPressed: Boolean
) {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val outerRadius = min(size.width, size.height) / 2f
        val innerRadius = outerRadius * innerRadiusScale
        val strokeWidth = outerRadius * 0.035f
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadiusAdjusted = outerRadius - strokeWidth * 0.2f
        val innerRadiusAdjusted = innerRadius + strokeWidth * 0.15f
        val outerRect = Rect(
            left = center.x - outerRadiusAdjusted,
            top = center.y - outerRadiusAdjusted,
            right = center.x + outerRadiusAdjusted,
            bottom = center.y + outerRadiusAdjusted
        )
        val innerRect = Rect(
            left = center.x - innerRadiusAdjusted,
            top = center.y - innerRadiusAdjusted,
            right = center.x + innerRadiusAdjusted,
            bottom = center.y + innerRadiusAdjusted
        )

        val segmentPath = Path().apply {
            arcTo(outerRect, startAngle, sweepAngle, false)
            arcTo(innerRect, startAngle + sweepAngle, -sweepAngle, false)
            close()
        }

        val activeFill = if (isPressed) pressedColor else fillColor
        val rimColor = if (isPressed) {
            blendColors(borderColor, Color.Black, 0.35f)
        } else {
            blendColors(borderColor, Color.White, 0.35f)
        }
        val innerRim = if (isPressed) {
            blendColors(activeFill, Color.Black, 0.15f)
        } else {
            blendColors(activeFill, Color.White, 0.18f)
        }

        drawPath(segmentPath, color = activeFill)
        drawPath(segmentPath, color = rimColor, style = Stroke(width = strokeWidth))
        drawPath(segmentPath, color = innerRim, style = Stroke(width = strokeWidth * 0.6f))

        val arrowRadius = (innerRadius + outerRadius) * 0.5f
        val arrowCenter = Offset(
            x = center.x + cos(directionAngle.toRadians()) * arrowRadius,
            y = center.y + sin(directionAngle.toRadians()) * arrowRadius
        )
        val arrowLength = outerRadius * 0.09f
        val arrowWidth = outerRadius * 0.16f
        val forward = Offset(
            x = cos(directionAngle.toRadians()),
            y = sin(directionAngle.toRadians())
        )
        val perpendicular = Offset(-forward.y, forward.x)
        val tip = arrowCenter + forward * arrowLength
        val baseCenter = arrowCenter - forward * (arrowLength * 0.45f)
        val left = baseCenter + perpendicular * (arrowWidth * 0.5f)
        val right = baseCenter - perpendicular * (arrowWidth * 0.5f)

        val arrowPath = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(left.x, left.y)
            lineTo(right.x, right.y)
            close()
        }
        drawPath(arrowPath, color = arrowColor)
    }
}

private enum class DPadDirection {
    UP,
    LEFT,
    RIGHT,
    DOWN
}

@Composable
fun DPadGaps(
    gapWidthScale: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val outerRadius = min(size.width, size.height) / 2f
        val gapWidth = outerRadius * gapWidthScale
        val rectLength = outerRadius * 2.1f
        val center = Offset(size.width / 2f, size.height / 2f)
        val rectTop = Offset(center.x - gapWidth / 2f, center.y - rectLength / 2f)
        val rectSize = Size(gapWidth, rectLength)
        rotate(45f, center) {
            drawRect(color = color, topLeft = rectTop, size = rectSize)
        }
        rotate(-45f, center) {
            drawRect(color = color, topLeft = rectTop, size = rectSize)
        }
    }
}

@Composable
fun KeyButton(
    keyDef: KeyDef,
    modifier: Modifier = Modifier,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    // Colors tuned to the TI-84 Plus CE image
    val baseColor = when (keyDef.style) {
        KeyStyle.YELLOW -> Color(0xFF6AB6E6) // 2nd key blue
        KeyStyle.GREEN -> Color(0xFF6DBE45)  // alpha key green
        KeyStyle.WHITE -> Color(0xFFE6E6E6)  // light gray keys
        KeyStyle.BLUE -> Color(0xFFDCDCDC)   // enter key (slightly darker light gray)
        KeyStyle.ARROW -> Color(0xFF4A4A4A)  // arrow keys
        KeyStyle.DARK -> Color(0xFF2D2D2D)   // dark keys
    }

    val textColor = when (keyDef.style) {
        KeyStyle.GREEN -> Color(0xFF1A1A1A)
        KeyStyle.WHITE, KeyStyle.BLUE -> Color(0xFF1A1A1A)
        else -> Color(0xFFF7F7F7)
    }

    val secondLabelColor = keyDef.secondLabelColor ?: Color(0xFF79C9FF)
    val alphaLabelColor = keyDef.alphaLabelColor ?: Color(0xFF7EC64B)
    val isNumberKey = isNumberClusterKey(keyDef.label)
    val keyShape = when (keyDef.style) {
        KeyStyle.WHITE, KeyStyle.BLUE -> if (isNumberKey) RoundedCornerShape(4.dp) else RoundedCornerShape(5.dp)
        KeyStyle.YELLOW, KeyStyle.GREEN -> RoundedCornerShape(7.dp)
        else -> RoundedCornerShape(6.dp)
    }
    val borderDarken = when (keyDef.style) {
        KeyStyle.WHITE, KeyStyle.BLUE -> 0.4f
        KeyStyle.DARK -> 0.48f
        else -> 0.35f
    }
    val borderWidth = if (keyDef.style == KeyStyle.WHITE || keyDef.style == KeyStyle.BLUE) 1.5.dp else 1.dp
    val borderColor = blendColors(baseColor, Color.Black, borderDarken)
    val topColor = blendColors(baseColor, Color.White, 0.16f)
    val bottomColor = blendColors(baseColor, Color.Black, 0.18f)
    val pressedTop = blendColors(baseColor, Color.Black, 0.22f)
    val pressedBottom = blendColors(baseColor, Color.Black, 0.32f)
    val keyBrush = Brush.verticalGradient(listOf(if (isPressed) pressedTop else topColor, if (isPressed) pressedBottom else bottomColor))

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Secondary labels above key: yellow (2nd) on left, green (alpha) on right - spaced apart
        val labelRowHeight = if (isNumberKey) 11.dp else 14.dp
        if (keyDef.secondLabel != null || keyDef.alphaLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(labelRowHeight)
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = keyDef.secondLabel ?: "",
                    color = secondLabelColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1
                )
                Text(
                    text = keyDef.alphaLabel ?: "",
                    color = alphaLabelColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1
                )
            }
        } else {
            Spacer(modifier = Modifier.height(labelRowHeight))
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Main key button with border effect
        val mainFontSize = if (isNumberKey) 21.sp else 17.sp
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(borderWidth, borderColor, keyShape)
                .background(keyBrush, keyShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            onDown()
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                                onUp()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = keyDef.label,
                color = textColor,
                fontSize = mainFontSize,
                fontWeight = if (keyDef.style == KeyStyle.WHITE || keyDef.style == KeyStyle.BLUE) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
        }

        // Small spacer at bottom
        Spacer(modifier = Modifier.height(2.dp))
    }
}

private fun blendColors(base: Color, overlay: Color, ratio: Float): Color {
    val clamped = ratio.coerceIn(0f, 1f)
    return Color(
        red = base.red + (overlay.red - base.red) * clamped,
        green = base.green + (overlay.green - base.green) * clamped,
        blue = base.blue + (overlay.blue - base.blue) * clamped,
        alpha = base.alpha + (overlay.alpha - base.alpha) * clamped
    )
}

private fun isNumberClusterKey(label: String): Boolean {
    return (label.length == 1 && label[0].isDigit()) || label == "." || label == "(−)"
}

private fun isPointInSegment(
    point: Offset,
    size: Size,
    startAngle: Float,
    sweepAngle: Float,
    innerRadiusScale: Float,
    gapWidthScale: Float
): Boolean {
    val center = Offset(size.width / 2f, size.height / 2f)
    val dx = point.x - center.x
    val dy = point.y - center.y
    val radius = sqrt(dx * dx + dy * dy)
    val outerRadius = min(size.width, size.height) / 2f
    val innerRadius = outerRadius * innerRadiusScale
    if (radius < innerRadius || radius > outerRadius) {
        return false
    }
    if (isPointInGap(dx, dy, outerRadius, gapWidthScale)) {
        return false
    }
    val angle = (atan2(dy, dx) * 180f / PI).toFloat()
    val normalized = (angle + 360f) % 360f
    val end = (startAngle + sweepAngle) % 360f
    return if (sweepAngle >= 360f) {
        true
    } else if (startAngle <= end) {
        normalized in startAngle..end
    } else {
        normalized >= startAngle || normalized <= end
    }
}

private fun isPointInGap(dx: Float, dy: Float, outerRadius: Float, gapWidthScale: Float): Boolean {
    val gapWidth = outerRadius * gapWidthScale
    val threshold = gapWidth * 0.5f * 1.41421356f
    return abs(dy - dx) < threshold || abs(dy + dx) < threshold
}

private fun hitTestDPad(
    offset: Offset,
    size: Size,
    sweepAngle: Float,
    innerRadiusScale: Float,
    gapWidthScale: Float
): DPadDirection? {
    val upStart = 270f - sweepAngle / 2f
    val leftStart = 180f - sweepAngle / 2f
    val rightStart = 0f - sweepAngle / 2f
    val downStart = 90f - sweepAngle / 2f

    return when {
        isPointInSegment(offset, size, upStart, sweepAngle, innerRadiusScale, gapWidthScale) -> DPadDirection.UP
        isPointInSegment(offset, size, leftStart, sweepAngle, innerRadiusScale, gapWidthScale) -> DPadDirection.LEFT
        isPointInSegment(offset, size, rightStart, sweepAngle, innerRadiusScale, gapWidthScale) -> DPadDirection.RIGHT
        isPointInSegment(offset, size, downStart, sweepAngle, innerRadiusScale, gapWidthScale) -> DPadDirection.DOWN
        else -> null
    }
}

private fun Float.toRadians(): Float {
    return (this / 180f) * PI.toFloat()
}

private operator fun Offset.plus(other: Offset): Offset = Offset(x + other.x, y + other.y)
private operator fun Offset.minus(other: Offset): Offset = Offset(x - other.x, y - other.y)
private operator fun Offset.times(value: Float): Offset = Offset(x * value, y * value)
