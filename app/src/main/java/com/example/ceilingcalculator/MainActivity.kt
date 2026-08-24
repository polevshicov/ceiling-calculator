package com.example.ceilingcalculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.math.max

// ─────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────

enum class CeilingMaterial(val label: String) {
    PVC_STANDARD("ПВХ матовый/сатин/глянец"),
    FABRIC("Тканевый (Descor/Cerutti/Clipso)")
}

enum class ProfileType(val label: String) {
    STANDARD("Стандартный с заглушкой"),
    SHADOW("Теневой профиль (EuroKRAAB/Baff)"),
    FLOATING("Парящий с подсветкой")
}

data class Prices(
    val pvcPerSqM: Double = 400.0,
    val fabricPerSqM: Double = 1800.0,
    val standardProfilePerM: Double = 200.0,
    val shadowProfilePerM: Double = 950.0,
    val floatingProfilePerM: Double = 1200.0,
    val lightLinePerM: Double = 1600.0,
    val trackSystemPerM: Double = 2200.0,
    val spotlightInstall: Double = 350.0,
    val chandelierInstall: Double = 700.0,
    val curtainNichePerM: Double = 1100.0,
    val pipeBypass: Double = 300.0,
    val extraAngle: Double = 180.0,
    val hardWallSurcharge: Double = 0.25,
    val minimumOrder: Double = 4000.0
)

data class RoomData(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Комната",
    val areaSqM: String = "",
    val perimeterM: String = "",
    val anglesCount: String = "4",
    val pipesCount: String = "0",
    val material: CeilingMaterial = CeilingMaterial.PVC_STANDARD,
    val profileType: ProfileType = ProfileType.STANDARD,
    val spotlightsCount: String = "0",
    val chandeliersCount: String = "0",
    val lightLinesM: String = "0",
    val trackSystemsM: String = "0",
    val curtainNicheM: String = "0",
    val isHardWall: Boolean = false
)

data class RoomEstimate(
    val room: RoomData,
    val materialCost: Double,
    val profileCost: Double,
    val lightingCost: Double,
    val nichesCost: Double,
    val extrasCost: Double,
    val total: Double
)

data class AppState(
    val rooms: List<RoomData> = listOf(RoomData(name = "Зал")),
    val prices: Prices = Prices(),
    val currentEditRoomId: String? = null
)

// ─────────────────────────────────────────────
// CALCULATION LOGIC
// ─────────────────────────────────────────────

fun calculateRoom(room: RoomData, prices: Prices): RoomEstimate {
    val area = room.areaSqM.toDoubleOrNull() ?: 0.0
    val perimeter = room.perimeterM.toDoubleOrNull() ?: 0.0
    val angles = (room.anglesCount.toIntOrNull() ?: 4).coerceAtLeast(0)
    val pipes = room.pipesCount.toIntOrNull() ?: 0
    val spotlights = room.spotlightsCount.toIntOrNull() ?: 0
    val chandeliers = room.chandeliersCount.toIntOrNull() ?: 0
    val lightLines = room.lightLinesM.toDoubleOrNull() ?: 0.0
    val trackSystems = room.trackSystemsM.toDoubleOrNull() ?: 0.0
    val curtainNiche = room.curtainNicheM.toDoubleOrNull() ?: 0.0

    // Material cost
    val materialPricePerSqM = when (room.material) {
        CeilingMaterial.PVC_STANDARD -> prices.pvcPerSqM
        CeilingMaterial.FABRIC -> prices.fabricPerSqM
    }
    val materialCost = area * materialPricePerSqM

    // Profile cost with hard wall surcharge
    val baseProfilePrice = when (room.profileType) {
        ProfileType.STANDARD -> prices.standardProfilePerM
        ProfileType.SHADOW -> prices.shadowProfilePerM
        ProfileType.FLOATING -> prices.floatingProfilePerM
    }
    val profileSurcharge = if (room.isHardWall) 1.0 + prices.hardWallSurcharge else 1.0
    val profileCost = perimeter * baseProfilePrice * profileSurcharge

    // Lighting cost
    val spotlightCost = spotlights * prices.spotlightInstall
    val chandelierCost = chandeliers * prices.chandelierInstall
    val lightLinesCost = lightLines * prices.lightLinePerM
    val trackCost = trackSystems * prices.trackSystemPerM
    val lightingCost = spotlightCost + chandelierCost + lightLinesCost + trackCost

    // Niches / curtain rails
    val nichesCost = curtainNiche * prices.curtainNichePerM

    // Extras: pipes and extra angles (beyond 4)
    val pipesCost = pipes * prices.pipeBypass
    val extraAngles = max(0, angles - 4)
    val anglesCost = extraAngles * prices.extraAngle
    val extrasCost = pipesCost + anglesCost

    val total = materialCost + profileCost + lightingCost + nichesCost + extrasCost
    return RoomEstimate(room, materialCost, profileCost, lightingCost, nichesCost, extrasCost, total)
}

fun calculateTotal(estimates: List<RoomEstimate>, prices: Prices): Double {
    val raw = estimates.sumOf { it.total }
    return max(raw, if (raw > 0) prices.minimumOrder else 0.0)
}

fun buildEstimateText(estimates: List<RoomEstimate>, prices: Prices): String {
    val sb = StringBuilder()
    sb.appendLine("═══════════════════════════════")
    sb.appendLine("  КП — Натяжные потолки")
    sb.appendLine("  г. Качканар")
    sb.appendLine("═══════════════════════════════")
    sb.appendLine()

    estimates.forEach { est ->
        val r = est.room
        sb.appendLine("📐 ${r.name}")
        sb.appendLine("  Материал: ${r.material.label}")
        sb.appendLine("  Площадь: ${r.areaSqM} м²")
        sb.appendLine("  Периметр: ${r.perimeterM} п.м")
        sb.appendLine("  Профиль: ${r.profileType.label}${if (r.isHardWall) " (+25% керамогранит/высота)" else ""}")
        sb.appendLine()
        if (est.materialCost > 0) sb.appendLine("  Полотно: ${est.materialCost.toInt()} руб.")
        if (est.profileCost > 0) sb.appendLine("  Профиль/монтаж: ${est.profileCost.toInt()} руб.")
        if (est.lightingCost > 0) sb.appendLine("  Освещение: ${est.lightingCost.toInt()} руб.")
        if (est.nichesCost > 0) sb.appendLine("  Ниши/карнизы: ${est.nichesCost.toInt()} руб.")
        if (est.extrasCost > 0) sb.appendLine("  Доп. работы: ${est.extrasCost.toInt()} руб.")
        sb.appendLine("  ─────────────────────────")
        sb.appendLine("  Итого по комнате: ${est.total.toInt()} руб.")
        sb.appendLine()
    }

    val rawTotal = estimates.sumOf { it.total }
    val finalTotal = max(rawTotal, if (rawTotal > 0) prices.minimumOrder else 0.0)

    sb.appendLine("═══════════════════════════════")
    if (finalTotal > rawTotal && rawTotal > 0) {
        sb.appendLine("Расчёт: ${rawTotal.toInt()} руб.")
        sb.appendLine("Минимальный заказ: ${prices.minimumOrder.toInt()} руб.")
    }
    sb.appendLine("ИТОГО: ${finalTotal.toInt()} руб.")
    sb.appendLine("═══════════════════════════════")
    sb.appendLine()
    sb.appendLine("Цены актуальны для г. Качканар.")
    sb.appendLine("Для уточнения деталей свяжитесь с нами.")
    return sb.toString()
}

// ─────────────────────────────────────────────
// VIEWMODEL
// ─────────────────────────────────────────────

class CeilingViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun addRoom(name: String = "Комната") {
        _state.update { it.copy(rooms = it.rooms + RoomData(name = name)) }
    }

    fun removeRoom(id: String) {
        _state.update { it.copy(rooms = it.rooms.filter { r -> r.id != id }) }
    }

    fun updateRoom(updated: RoomData) {
        _state.update { s ->
            s.copy(rooms = s.rooms.map { if (it.id == updated.id) updated else it })
        }
    }

    fun setEditRoom(id: String?) {
        _state.update { it.copy(currentEditRoomId = id) }
    }

    fun updatePrices(prices: Prices) {
        _state.update { it.copy(prices = prices) }
    }

    fun getEditRoom(): RoomData? {
        val s = _state.value
        return s.rooms.find { it.id == s.currentEditRoomId }
    }
}

// ─────────────────────────────────────────────
// MAIN ACTIVITY
// ─────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CeilingCalculatorTheme {
                CeilingApp()
            }
        }
    }
}

@Composable
fun CeilingCalculatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1565C0),
            secondary = Color(0xFF0288D1),
            background = Color(0xFFF5F7FA),
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Color(0xFF1A1A2E),
            onSurface = Color(0xFF1A1A2E)
        ),
        content = content
    )
}

// ─────────────────────────────────────────────
// NAVIGATION
// ─────────────────────────────────────────────

@Composable
fun CeilingApp() {
    val navController = rememberNavController()
    val vm: CeilingViewModel = viewModel()

    Scaffold(
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "rooms",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("rooms") { RoomsScreen(vm, navController) }
            composable("edit_room") { EditRoomScreen(vm, navController) }
            composable("total") { TotalScreen(vm) }
            composable("prices") { PricesScreen(vm) }
        }
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == "rooms",
            onClick = { navController.navigate("rooms") { launchSingleTop = true } },
            icon = { Icon(Icons.Default.Add, null) },
            label = { Text("Комнаты") }
        )
        NavigationBarItem(
            selected = currentRoute == "total",
            onClick = { navController.navigate("total") { launchSingleTop = true } },
            icon = { Icon(Icons.Default.Edit, null) },
            label = { Text("Итого") }
        )
        NavigationBarItem(
            selected = currentRoute == "prices",
            onClick = { navController.navigate("prices") { launchSingleTop = true } },
            icon = { Icon(Icons.Default.Edit, null) },
            label = { Text("Цены") }
        )
    }
}

// ─────────────────────────────────────────────
// ROOMS LIST SCREEN
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(vm: CeilingViewModel, navController: NavHostController) {
    val state by vm.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newRoomName by remember { mutableStateOf("") }

    val roomSuggestions = listOf("Зал", "Спальня", "Кухня", "Прихожая", "Ванная", "Детская", "Кабинет", "Лоджия")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Натяжные потолки — Качканар", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Добавить комнату")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (state.rooms.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет комнат. Нажмите + чтобы добавить.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.rooms, key = { _, r -> r.id }) { _, room ->
                        val est = calculateRoom(room, state.prices)
                        RoomCard(
                            room = room,
                            estimate = est,
                            onEdit = {
                                vm.setEditRoom(room.id)
                                navController.navigate("edit_room")
                            },
                            onDelete = { vm.removeRoom(room.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; newRoomName = "" },
            title = { Text("Добавить комнату") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newRoomName,
                        onValueChange = { newRoomName = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Быстрый выбор:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    FlowRowSuggestions(roomSuggestions) { newRoomName = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newRoomName.trim().ifEmpty { "Комната" }
                    vm.addRoom(name)
                    showAddDialog = false
                    newRoomName = ""
                }) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; newRoomName = "" }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun FlowRowSuggestions(suggestions: List<String>, onSelect: (String) -> Unit) {
    var rowItems = suggestions.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rowItems.forEach { rowChunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowChunk.forEach { s ->
                    SuggestionChip(
                        onClick = { onSelect(s) },
                        label = { Text(s, fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}

@Composable
fun RoomCard(room: RoomData, estimate: RoomEstimate, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(room.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${room.material.label.take(20)}... | ${room.profileType.label.take(15)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    "Площадь: ${room.areaSqM.ifEmpty { "—" }} м² | Периметр: ${room.perimeterM.ifEmpty { "—" }} п.м",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${estimate.total.toInt()} руб.",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Редактировать", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Удалить", tint = Color(0xFFD32F2F))
            }
        }
    }
}

// ─────────────────────────────────────────────
// EDIT ROOM SCREEN
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRoomScreen(vm: CeilingViewModel, navController: NavHostController) {
    val state by vm.state.collectAsState()
    val originalRoom = vm.getEditRoom() ?: return

    var name by remember { mutableStateOf(originalRoom.name) }
    var area by remember { mutableStateOf(originalRoom.areaSqM) }
    var perimeter by remember { mutableStateOf(originalRoom.perimeterM) }
    var angles by remember { mutableStateOf(originalRoom.anglesCount) }
    var pipes by remember { mutableStateOf(originalRoom.pipesCount) }
    var material by remember { mutableStateOf(originalRoom.material) }
    var profileType by remember { mutableStateOf(originalRoom.profileType) }
    var spotlights by remember { mutableStateOf(originalRoom.spotlightsCount) }
    var chandeliers by remember { mutableStateOf(originalRoom.chandeliersCount) }
    var lightLines by remember { mutableStateOf(originalRoom.lightLinesM) }
    var trackSystems by remember { mutableStateOf(originalRoom.trackSystemsM) }
    var curtainNiche by remember { mutableStateOf(originalRoom.curtainNicheM) }
    var isHardWall by remember { mutableStateOf(originalRoom.isHardWall) }

    fun buildUpdated() = originalRoom.copy(
        name = name,
        areaSqM = area,
        perimeterM = perimeter,
        anglesCount = angles,
        pipesCount = pipes,
        material = material,
        profileType = profileType,
        spotlightsCount = spotlights,
        chandeliersCount = chandeliers,
        lightLinesM = lightLines,
        trackSystemsM = trackSystems,
        curtainNicheM = curtainNiche,
        isHardWall = isHardWall
    )

    val liveEstimate = remember(
        area, perimeter, angles, pipes, material, profileType,
        spotlights, chandeliers, lightLines, trackSystems, curtainNiche, isHardWall, state.prices
    ) { calculateRoom(buildUpdated(), state.prices) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Параметры: $name") },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.updateRoom(buildUpdated())
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Add, "Сохранить и назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Room name
            SectionHeader("Основное")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название комнаты") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Площадь, м²") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = perimeter,
                    onValueChange = { perimeter = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Периметр, п.м") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = angles,
                    onValueChange = { angles = it.filter { c -> c.isDigit() } },
                    label = { Text("Углов (шт)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = pipes,
                    onValueChange = { pipes = it.filter { c -> c.isDigit() } },
                    label = { Text("Трубы (шт)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Material
            SectionHeader("Материал")
            CeilingMaterial.entries.forEach { mat ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(selected = material == mat, onClick = { material = mat })
                    Spacer(Modifier.width(4.dp))
                    Text(mat.label, fontSize = 14.sp)
                }
            }

            // Profile
            SectionHeader("Тип профиля")
            ProfileType.entries.forEach { prof ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(selected = profileType == prof, onClick = { profileType = prof })
                    Spacer(Modifier.width(4.dp))
                    Text(prof.label, fontSize = 14.sp)
                }
            }

            // Lighting
            SectionHeader("Освещение")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = spotlights,
                    onValueChange = { spotlights = it.filter { c -> c.isDigit() } },
                    label = { Text("Точечные (шт)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = chandeliers,
                    onValueChange = { chandeliers = it.filter { c -> c.isDigit() } },
                    label = { Text("Люстры (шт)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lightLines,
                    onValueChange = { lightLines = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Световые линии (м)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = trackSystems,
                    onValueChange = { trackSystems = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Трековые системы (м)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Extras
            SectionHeader("Дополнительно")
            OutlinedTextField(
                value = curtainNiche,
                onValueChange = { curtainNiche = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Ниша под гардину/карниз (п.м)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = isHardWall, onCheckedChange = { isHardWall = it })
                Spacer(Modifier.width(4.dp))
                Text("Стена из керамогранита / высотные работы (+25% к профилю)", fontSize = 13.sp)
            }

            // Live preview
            Spacer(Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Предварительный расчёт", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    EstimateRow("Полотно", liveEstimate.materialCost)
                    EstimateRow("Профиль/монтаж", liveEstimate.profileCost)
                    EstimateRow("Освещение", liveEstimate.lightingCost)
                    EstimateRow("Ниши/карнизы", liveEstimate.nichesCost)
                    EstimateRow("Доп. работы", liveEstimate.extrasCost)
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Итого", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${liveEstimate.total.toInt()} руб.", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Button(
                onClick = {
                    vm.updateRoom(buildUpdated())
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить")
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
    HorizontalDivider()
}

@Composable
fun EstimateRow(label: String, value: Double) {
    if (value > 0) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp)
            Text("${value.toInt()} руб.", fontSize = 13.sp)
        }
    }
}

// ─────────────────────────────────────────────
// TOTAL SCREEN
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalScreen(vm: CeilingViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val estimates = state.rooms.map { calculateRoom(it, state.prices) }
    val rawTotal = estimates.sumOf { it.total }
    val finalTotal = calculateTotal(estimates, state.prices)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Итоговая смета") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (estimates.isEmpty() || estimates.all { it.total == 0.0 }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Добавьте комнаты и укажите параметры.", color = Color.Gray, modifier = Modifier.padding(24.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(estimates, key = { _, e -> e.room.id }) { _, est ->
                        RoomEstimateCard(est)
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (finalTotal > rawTotal && rawTotal > 0) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Расчёт по работам:", color = Color.White.copy(alpha = 0.8f))
                                        Text("${rawTotal.toInt()} руб.", color = Color.White.copy(alpha = 0.8f))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Минимальный заказ:", color = Color.White.copy(alpha = 0.8f))
                                        Text("${state.prices.minimumOrder.toInt()} руб.", color = Color.White.copy(alpha = 0.8f))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("ИТОГО К ОПЛАТЕ:", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                                    Text("${finalTotal.toInt()} руб.", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val text = buildEstimateText(estimates, state.prices)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("КП Натяжные потолки", text)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "КП скопировано в буфер обмена", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Сформировать КП (скопировать)")
                }
            }
        }
    }
}

@Composable
fun RoomEstimateCard(est: RoomEstimate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(est.room.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "${est.room.material.label} | ${est.room.profileType.label}",
                fontSize = 12.sp,
                color = Color.Gray
            )
            if (est.room.isHardWall) {
                Text("(+25% керамогранит/высота)", fontSize = 11.sp, color = Color(0xFFE65100))
            }
            Spacer(Modifier.height(6.dp))
            EstimateRow("Полотно (${est.room.areaSqM} м²)", est.materialCost)
            EstimateRow("Профиль/монтаж (${est.room.perimeterM} п.м)", est.profileCost)
            EstimateRow("Освещение", est.lightingCost)
            EstimateRow("Ниши/карнизы", est.nichesCost)
            EstimateRow("Доп. работы", est.extrasCost)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Итого:", fontWeight = FontWeight.SemiBold)
                Text("${est.total.toInt()} руб.", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ─────────────────────────────────────────────
// PRICES SCREEN
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricesScreen(vm: CeilingViewModel) {
    val state by vm.state.collectAsState()
    var p by remember(state.prices) { mutableStateOf(state.prices) }

    fun Double.str() = if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()

    var pvcStr by remember(state.prices) { mutableStateOf(p.pvcPerSqM.str()) }
    var fabricStr by remember(state.prices) { mutableStateOf(p.fabricPerSqM.str()) }
    var stdProfileStr by remember(state.prices) { mutableStateOf(p.standardProfilePerM.str()) }
    var shadowProfileStr by remember(state.prices) { mutableStateOf(p.shadowProfilePerM.str()) }
    var floatingProfileStr by remember(state.prices) { mutableStateOf(p.floatingProfilePerM.str()) }
    var lightLineStr by remember(state.prices) { mutableStateOf(p.lightLinePerM.str()) }
    var trackStr by remember(state.prices) { mutableStateOf(p.trackSystemPerM.str()) }
    var spotlightStr by remember(state.prices) { mutableStateOf(p.spotlightInstall.str()) }
    var chandelierStr by remember(state.prices) { mutableStateOf(p.chandelierInstall.str()) }
    var nicheStr by remember(state.prices) { mutableStateOf(p.curtainNichePerM.str()) }
    var pipeStr by remember(state.prices) { mutableStateOf(p.pipeBypass.str()) }
    var angleStr by remember(state.prices) { mutableStateOf(p.extraAngle.str()) }
    var surchargeStr by remember(state.prices) { mutableStateOf((p.hardWallSurcharge * 100).str()) }
    var minOrderStr by remember(state.prices) { mutableStateOf(p.minimumOrder.str()) }

    fun save() {
        val updated = Prices(
            pvcPerSqM = pvcStr.toDoubleOrNull() ?: p.pvcPerSqM,
            fabricPerSqM = fabricStr.toDoubleOrNull() ?: p.fabricPerSqM,
            standardProfilePerM = stdProfileStr.toDoubleOrNull() ?: p.standardProfilePerM,
            shadowProfilePerM = shadowProfileStr.toDoubleOrNull() ?: p.shadowProfilePerM,
            floatingProfilePerM = floatingProfileStr.toDoubleOrNull() ?: p.floatingProfilePerM,
            lightLinePerM = lightLineStr.toDoubleOrNull() ?: p.lightLinePerM,
            trackSystemPerM = trackStr.toDoubleOrNull() ?: p.trackSystemPerM,
            spotlightInstall = spotlightStr.toDoubleOrNull() ?: p.spotlightInstall,
            chandelierInstall = chandelierStr.toDoubleOrNull() ?: p.chandelierInstall,
            curtainNichePerM = nicheStr.toDoubleOrNull() ?: p.curtainNichePerM,
            pipeBypass = pipeStr.toDoubleOrNull() ?: p.pipeBypass,
            extraAngle = angleStr.toDoubleOrNull() ?: p.extraAngle,
            hardWallSurcharge = (surchargeStr.toDoubleOrNull() ?: (p.hardWallSurcharge * 100)) / 100.0,
            minimumOrder = minOrderStr.toDoubleOrNull() ?: p.minimumOrder
        )
        vm.updatePrices(updated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактирование цен") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader("Материалы (руб/м²)")
            PriceField("ПВХ матовый/сатин/глянец", pvcStr) { pvcStr = it }
            PriceField("Тканевый (Descor/Cerutti)", fabricStr) { fabricStr = it }

            SectionHeader("Профиль (руб/п.м)")
            PriceField("Стандартный с заглушкой", stdProfileStr) { stdProfileStr = it }
            PriceField("Теневой (EuroKRAAB/Baff)", shadowProfileStr) { shadowProfileStr = it }
            PriceField("Парящий с подсветкой", floatingProfileStr) { floatingProfileStr = it }

            SectionHeader("Системы освещения (руб/п.м или шт)")
            PriceField("Световые линии (руб/п.м)", lightLineStr) { lightLineStr = it }
            PriceField("Трековые системы (руб/п.м)", trackStr) { trackStr = it }
            PriceField("Точечный светильник (руб/шт)", spotlightStr) { spotlightStr = it }
            PriceField("Люстра (руб/шт)", chandelierStr) { chandelierStr = it }

            SectionHeader("Дополнительно")
            PriceField("Ниша/гардина (руб/п.м)", nicheStr) { nicheStr = it }
            PriceField("Обход трубы (руб/шт)", pipeStr) { pipeStr = it }
            PriceField("Угол свыше 4-х (руб/шт)", angleStr) { angleStr = it }
            PriceField("Доплата керамогранит/высота (%)", surchargeStr) { surchargeStr = it }

            SectionHeader("Условия")
            PriceField("Минимальный заказ (руб)", minOrderStr) { minOrderStr = it }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить цены")
            }

            TextButton(
                onClick = {
                    val def = Prices()
                    vm.updatePrices(def)
                    pvcStr = def.pvcPerSqM.toLong().toString()
                    fabricStr = def.fabricPerSqM.toLong().toString()
                    stdProfileStr = def.standardProfilePerM.toLong().toString()
                    shadowProfileStr = def.shadowProfilePerM.toLong().toString()
                    floatingProfileStr = def.floatingProfilePerM.toLong().toString()
                    lightLineStr = def.lightLinePerM.toLong().toString()
                    trackStr = def.trackSystemPerM.toLong().toString()
                    spotlightStr = def.spotlightInstall.toLong().toString()
                    chandelierStr = def.chandelierInstall.toLong().toString()
                    nicheStr = def.curtainNichePerM.toLong().toString()
                    pipeStr = def.pipeBypass.toLong().toString()
                    angleStr = def.extraAngle.toLong().toString()
                    surchargeStr = (def.hardWallSurcharge * 100).toLong().toString()
                    minOrderStr = def.minimumOrder.toLong().toString()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сбросить к базовым значениям")
            }
        }
    }
}

@Composable
fun PriceField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(label, maxLines = 1) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            Text("руб.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
        }
    )
}
