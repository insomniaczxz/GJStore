package com.example.gjstore

import android.os.Bundle
import android.widget.Toast
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.gjstore.data.*
import com.example.gjstore.network.RetrofitClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GJStoreDarkTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun GJStoreDarkTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFFFF7D1E),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        onBackground = Color.White,
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = darkColorScheme, content = content)
}

data class PendingAction(val sheetName: String, val action: String, val data: List<String?>, val oldData: List<String?>? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var searchQuery by remember { mutableStateOf("") }
    var isAdminLoggedIn by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showEventDialog by remember { mutableStateOf(false) }
    var showEventHistoryDialog by remember { mutableStateOf(false) }
    var currentAdminTab by remember { mutableIntStateOf(0) }
    val adminTabs = listOf("Products", "Rebuy", "Settings", "Events")

    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var showFormDialog by remember { mutableStateOf(false) }

    val productsList = remember { mutableStateListOf<Product>() }
    val eventsList = remember { mutableStateListOf<Event>() }
    val pendingQueue = remember { mutableStateListOf<PendingAction>() }
    var isLoading by remember { mutableStateOf(true) }
    var isEventsLoading by remember { mutableStateOf(false) }
    val dynamicSettings = remember { DropdownSettings() }

    var appPin by remember { mutableStateOf(CacheManager.loadPin(context)) }
    var isPinEnabled by remember { mutableStateOf(CacheManager.loadPinEnabled(context)) }
    var isAppLocked by remember { mutableStateOf(isPinEnabled) }

    val filteredProducts by remember {
        derivedStateOf {
            productsList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.brand.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
            }.sortedBy { it.name.lowercase() }
        }
    }

    suspend fun refreshData() {
        isLoading = true; isEventsLoading = true
        try {
            val pRes = RetrofitClient.apiService.readSheet("Products")
            if (pRes.isSuccessful) {
                val newP = DataParser.parseProducts(pRes.body())
                productsList.clear(); productsList.addAll(newP)
                withContext(Dispatchers.IO) { CacheManager.saveProducts(context, newP) }
            }
            val sRes = RetrofitClient.apiService.readSheet("Settings")
            if (sRes.isSuccessful) {
                DataParser.parseSettings(sRes.body(), dynamicSettings)
                withContext(Dispatchers.IO) { CacheManager.saveSettings(context, dynamicSettings) }
            }
            val eRes = RetrofitClient.apiService.readSheet("Events")
            if (eRes.isSuccessful) {
                val newE = DataParser.parseEvents(eRes.body())
                eventsList.clear(); eventsList.addAll(newE)
                withContext(Dispatchers.IO) { CacheManager.saveEvents(context, newE) }
            }
        } catch (e: Exception) { 
            withContext(Dispatchers.Main) { Toast.makeText(context, "Refresh Failed", Toast.LENGTH_SHORT).show() }
        } finally { isLoading = false; isEventsLoading = false }
    }

    fun performAction(action: PendingAction) {
        coroutineScope.launch {
            try {
                val body = mutableMapOf<String, Any>("sheetName" to action.sheetName, "action" to action.action, "data" to action.data)
                if (action.oldData != null) body["oldData"] = action.oldData
                val response = RetrofitClient.apiService.modifySheet(body)
                if (response.isSuccessful || response.code() == 302) {
                    val respStr = response.body()?.string() ?: ""
                    if (respStr.contains("success")) {
                        pendingQueue.remove(action)
                        CacheManager.saveQueue(context, pendingQueue.toList())
                    } else if (respStr.contains("error")) {
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Sync Error: Row not found", Toast.LENGTH_LONG).show() }
                    }
                }
            } catch (e: Exception) { /* Keep in queue */ }
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true; isEventsLoading = true
        withContext(Dispatchers.IO) {
            val cachedProducts = CacheManager.loadProducts(context)
            val cachedEvents = CacheManager.loadEvents(context)
            val cachedSettings = CacheManager.loadSettings(context)
            val cachedQueue = CacheManager.loadQueue(context)
            val cachedPin = CacheManager.loadPin(context)
            val cachedPinEnabled = CacheManager.loadPinEnabled(context)
            withContext(Dispatchers.Main) {
                productsList.addAll(cachedProducts); eventsList.addAll(cachedEvents); pendingQueue.addAll(cachedQueue)
                appPin = cachedPin
                isPinEnabled = cachedPinEnabled
                cachedSettings?.let {
                    dynamicSettings.brands.clear(); dynamicSettings.brands.addAll(it.brands)
                    dynamicSettings.categories.clear(); dynamicSettings.categories.addAll(it.categories)
                    dynamicSettings.units.clear(); dynamicSettings.units.addAll(it.units)
                    dynamicSettings.stores.clear(); dynamicSettings.stores.addAll(it.stores)
                    dynamicSettings.messengerKeys.clear(); dynamicSettings.messengerKeys.addAll(it.messengerKeys)
                }
                pendingQueue.toList().forEach { performAction(it) }
            }
        }
        refreshData()
        try {
            val aRes = RetrofitClient.apiService.readSheet("Admin")
            if (aRes.isSuccessful) {
                val row = aRes.body()?.getOrNull(1)
                row?.getOrNull(0)?.let { CacheManager.saveAdmin(context, it) }
                row?.getOrNull(1)?.let { 
                    CacheManager.savePin(context, it)
                    withContext(Dispatchers.Main) { appPin = it }
                }
            }
        } catch (e: Exception) {}
    }

    if (isAppLocked && isPinEnabled) {
        PinLockScreen(appPin, onCorrectPin = { isAppLocked = false })
    } else {
        Scaffold(
            topBar = {
                Surface(tonalElevation = 3.dp) {
                    Column {
                        CenterAlignedTopAppBar(
                            title = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("G&J Sari-Sari Store", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    if (pendingQueue.isNotEmpty()) {
                                        Text("Syncing...", color = Color(0xFFFF7D1E), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { if (isAdminLoggedIn) isAdminLoggedIn = false else showLoginDialog = true },
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAdminLoggedIn) Icons.AutoMirrored.Filled.Logout else Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            },
                            navigationIcon = {
                                if (!isAdminLoggedIn) {
                                    IconButton(onClick = { showEventHistoryDialog = true }) {
                                        Icon(Icons.AutoMirrored.Filled.EventNote, null)
                                    }
                                }
                            }
                        )
                        if (isAdminLoggedIn) {
                            SecondaryTabRow(
                                selectedTabIndex = currentAdminTab,
                                containerColor = Color.Transparent,
                                divider = {}
                            ) {
                                adminTabs.forEachIndexed { i, t ->
                                    Tab(
                                        selected = currentAdminTab == i,
                                        onClick = { currentAdminTab = i },
                                        text = { Text(t, style = MaterialTheme.typography.labelLarge) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (isAdminLoggedIn && (currentAdminTab == 0 || currentAdminTab == 3)) {
                    ExtendedFloatingActionButton(
                        onClick = { if (currentAdminTab == 0) { editingProduct = null; showFormDialog = true } else showEventDialog = true },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text(if (currentAdminTab == 0) "Product" else "Event") },
                        containerColor = Color(0xFFFF7D1E),
                        contentColor = Color.White
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!isAdminLoggedIn) {
                        UserDashboard(
                            searchQuery, 
                            { searchQuery = it }, 
                            filteredProducts, 
                            isLoading,
                            { coroutineScope.launch { refreshData() } },
                            { updated ->
                                val idx = productsList.indexOfFirst { it.id == updated.id }
                                if (idx != -1) {
                                    productsList[idx] = updated
                                    val act = PendingAction("Products", "update", DataParser.productToRow(updated))
                                    pendingQueue.add(act); performAction(act)
                                    coroutineScope.launch(Dispatchers.IO) { CacheManager.saveProducts(context, productsList.toList()); CacheManager.saveQueue(context, pendingQueue.toList()) }
                                    Toast.makeText(context, "Stock Updated", Toast.LENGTH_SHORT).show()
                                }
                            },
                            { updatedList ->
                                updatedList.forEach { updated ->
                                    val idx = productsList.indexOfFirst { it.id == updated.id }
                                    if (idx != -1) {
                                        productsList[idx] = updated
                                        val act = PendingAction("Products", "update", DataParser.productToRow(updated))
                                        pendingQueue.add(act); performAction(act)
                                    }
                                }
                                coroutineScope.launch(Dispatchers.IO) { CacheManager.saveProducts(context, productsList.toList()); CacheManager.saveQueue(context, pendingQueue.toList()) }
                                Toast.makeText(context, "Stocks Updated", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        AdminDashboard(
                            productsList, 
                            dynamicSettings, 
                            eventsList, 
                            isLoading, 
                            currentAdminTab, 
                            { coroutineScope.launch { refreshData() } },
                            { target, action ->
                                val data = DataParser.productToRow(target)
                                val act = PendingAction("Products", action, data)
                                pendingQueue.add(act); performAction(act)
                                coroutineScope.launch(Dispatchers.IO) { CacheManager.saveProducts(context, productsList.toList()); CacheManager.saveQueue(context, pendingQueue.toList()) }
                                Toast.makeText(context, "Product ${action.replaceFirstChar { it.uppercase() }}d", Toast.LENGTH_SHORT).show()
                            }, 
                            { editingProduct = it; showFormDialog = true }, 
                            { editingEvent = it; showEventDialog = true }, 
                            { event ->
                                eventsList.remove(event)
                                val data = DataParser.eventToRow(event)
                                val act = PendingAction("Events", "delete", data, data)
                                pendingQueue.add(act); performAction(act)
                                coroutineScope.launch(Dispatchers.IO) { CacheManager.saveEvents(context, eventsList.toList()); CacheManager.saveQueue(context, pendingQueue.toList()) }
                                Toast.makeText(context, "Event Deleted", Toast.LENGTH_SHORT).show()
                            }, 
                            { sheet, action, data, oldData ->
                                val act = PendingAction(sheet, action, data, oldData)
                                pendingQueue.add(act); performAction(act)
                                coroutineScope.launch(Dispatchers.IO) { CacheManager.saveSettings(context, dynamicSettings); CacheManager.saveQueue(context, pendingQueue.toList()) }
                                Toast.makeText(context, "Settings updated", Toast.LENGTH_SHORT).show()
                            },
                            isPinEnabled,
                            { isPinEnabled = it }
                        )
                    }
                }
            }
        }

        if (showLoginDialog) AdminLoginDialog({ showLoginDialog = false }, { isAdminLoggedIn = true; showLoginDialog = false })
        if (showEventDialog) EventEntryDialog(editingEvent, { showEventDialog = false; editingEvent = null }, { event ->
            val action = if (editingEvent == null) "add" else "update"
            val data = DataParser.eventToRow(event)
            val oldData = if (editingEvent != null) DataParser.eventToRow(editingEvent!!) else null
            
            if (editingEvent == null) eventsList.add(0, event) else { val idx = eventsList.indexOf(editingEvent); if (idx != -1) eventsList[idx] = event }
            val act = PendingAction("Events", action, data, oldData)
            pendingQueue.add(act); performAction(act)
            coroutineScope.launch(Dispatchers.IO) { CacheManager.saveEvents(context, eventsList.toList()); CacheManager.saveQueue(context, pendingQueue.toList()) }
            Toast.makeText(context, if (editingEvent == null) "Event Added" else "Event Updated", Toast.LENGTH_SHORT).show()
            showEventDialog = false; editingEvent = null
        })
        if (showEventHistoryDialog) EmployeeEventHistoryDialog(eventsList, isEventsLoading, { showEventHistoryDialog = false }, { showEventDialog = true })
        if (showFormDialog) AdminProductFormDialog(editingProduct, dynamicSettings, { showFormDialog = false }, { finalized ->
            val isUpdating = editingProduct != null && finalized.id == editingProduct?.id
            if (isUpdating) {
                val idx = productsList.indexOfFirst { it.id == finalized.id }
                if (idx != -1) productsList[idx] = finalized
            } else {
                productsList.add(finalized)
            }
            val action = if (isUpdating) "update" else "add"
            val act = PendingAction("Products", action, DataParser.productToRow(finalized))
            pendingQueue.add(act); performAction(act)

            // Auto-add new dropdown options to Settings for faster product addition
            val newSettings = listOf(
                0 to finalized.brand.trim().takeIf { it.isNotBlank() && !dynamicSettings.brands.any { b -> b.equals(it, ignoreCase = true) } },
                1 to finalized.category.trim().takeIf { it.isNotBlank() && !dynamicSettings.categories.any { c -> c.equals(it, ignoreCase = true) } },
                2 to finalized.unit.trim().takeIf { it.isNotBlank() && !dynamicSettings.units.any { u -> u.equals(it, ignoreCase = true) } },
                3 to finalized.lastBoughtStore.trim().takeIf { it.isNotBlank() && !dynamicSettings.stores.any { s -> s.equals(it, ignoreCase = true) } }
            )

            newSettings.forEach { (index, value) ->
                if (value != null) {
                    when (index) {
                        0 -> dynamicSettings.brands.add(value)
                        1 -> dynamicSettings.categories.add(value)
                        2 -> dynamicSettings.units.add(value)
                        3 -> dynamicSettings.stores.add(value)
                    }
                    val settingsData = MutableList(5) { "" }.apply { set(index, value) }
                    val settingsAct = PendingAction("Settings", "add", settingsData)
                    pendingQueue.add(settingsAct); performAction(settingsAct)
                }
            }

            if (newSettings.any { it.second != null }) {
                coroutineScope.launch(Dispatchers.IO) { CacheManager.saveSettings(context, dynamicSettings) }
            }

            coroutineScope.launch(Dispatchers.IO) { CacheManager.saveProducts(context, productsList.toList()); CacheManager.saveQueue(context, pendingQueue.toList()) }
            Toast.makeText(context, if (isUpdating) "Product Updated" else "Product Added", Toast.LENGTH_SHORT).show()
            showFormDialog = false
        })
    }
}

@Composable
fun PinLockScreen(correctPin: String, onCorrectPin: () -> Unit) {
    var pinInput by remember { mutableStateOf("") }
    val pinToMatch = correctPin.ifBlank { "041823" }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color(0xFFFF7D1E).copy(alpha = 0.1f),
                modifier = Modifier.size(100.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(24.dp).size(48.dp),
                    tint = Color(0xFFFF7D1E)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Enter your PIN to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(6) { index ->
                    val isFilled = index < pinInput.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                color = if (isFilled) Color(0xFFFF7D1E) else Color.DarkGray,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Number Pad
            val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "DEL")
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (i in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        for (j in 0 until 3) {
                            val text = numbers[i * 3 + j]
                            if (text.isNotEmpty()) {
                                PinButton(text) {
                                    if (text == "DEL") {
                                        if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                    } else if (pinInput.length < 6) {
                                        pinInput += text
                                        if (pinInput == pinToMatch) onCorrectPin()
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.size(72.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (text == "DEL") Color.Transparent else Color(0xFF1E1E1E),
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text == "DEL") {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null, tint = Color.LightGray)
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDashboard(
    searchQuery: String, 
    onSearchQueryChange: (String) -> Unit, 
    filteredProducts: List<Product>, 
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onProductUpdated: (Product) -> Unit,
    onBatchUpdate: (List<Product>) -> Unit
) {
    var isBatchMode by remember { mutableStateOf(false) }
    val batchChanges = remember { mutableStateMapOf<String, String>() }

    val displayProducts by remember {
        derivedStateOf {
            val list = filteredProducts
            if (isBatchMode) list else list.filter { it.price > 0 }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search products, brands...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQueryChange("") }) { Icon(Icons.Default.Close, null) } },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            IconButton(
                onClick = { 
                    isBatchMode = !isBatchMode
                    if (!isBatchMode) batchChanges.clear()
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = if (isBatchMode) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = null,
                    tint = if (isBatchMode) Color(0xFFFF7D1E) else Color.White
                )
            }
        }

        if (isBatchMode) {
            Button(
                onClick = {
                    val updatedProducts = batchChanges.mapNotNull { (id, stockStr) ->
                        val product = filteredProducts.find { it.id == id }
                        product?.copy(stock = stockStr.toIntOrNull() ?: product.stock)
                    }
                    onBatchUpdate(updatedProducts)
                    isBatchMode = false
                    batchChanges.clear()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7D1E)),
                enabled = batchChanges.isNotEmpty()
            ) {
                Text("Save All Changes")
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayProducts, key = { it.id }) { product ->
                    ProductCard(product, isBatchMode, batchChanges, onProductUpdated)
                }
            }
        }
    }
}

@Composable
fun ProductCard(product: Product, isBatchMode: Boolean, batchChanges: MutableMap<String, String>, onProductUpdated: (Product) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val isLowStock = product.stock <= product.threshold

    ElevatedCard(
        onClick = { if (!isBatchMode) showDialog = true },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${product.name} ${product.formattedSize}${product.unit}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = product.brand,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                if (!isBatchMode) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (isLowStock) Color.Red.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Stock: ${product.stock}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLowStock) Color.Red else Color.White
                        )
                    }
                }
            }
            
            if (isBatchMode) {
                val currentEdit = batchChanges[product.id] ?: product.stock.toString()
                OutlinedTextField(
                    value = currentEdit,
                    onValueChange = { if (it.all { c -> c.isDigit() }) batchChanges[product.id] = it },
                    modifier = Modifier.width(100.dp),
                    label = { Text("Stock") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        if (currentEdit.isNotEmpty()) {
                            IconButton(onClick = { batchChanges[product.id] = "" }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true
                )
            } else {
                Text(
                    text = "₱${product.price}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF00FF87),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showDialog) {
        var newStock by remember { mutableStateOf(product.stock.toString()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Update Stock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(product.name, style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = newStock,
                        onValueChange = { if (it.all { c -> c.isDigit() }) newStock = it },
                        label = { Text("New Stock Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onProductUpdated(product.copy(stock = newStock.toIntOrNull() ?: product.stock))
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7D1E))
                ) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AdminLoginDialog(onDismiss: () -> Unit, onLoginSuccess: () -> Unit) {
    var passwordInput by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Admin Login") }, text = { OutlinedTextField(passwordInput, { passwordInput = it }, label = { Text("Password") }, visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } }) }, confirmButton = { TextButton(onClick = { if (passwordInput == CacheManager.loadAdmin(context)) onLoginSuccess() else Toast.makeText(context, "Denied", Toast.LENGTH_SHORT).show() }) { Text("Login") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun AdminDashboard(
    products: MutableList<Product>, 
    settings: DropdownSettings, 
    eventsList: MutableList<Event>, 
    isLoading: Boolean, 
    currentAdminTab: Int, 
    onRefresh: () -> Unit,
    onUpdateSheet: (Product, String) -> Unit, 
    onEditProductRequested: (Product) -> Unit, 
    onEditEventRequested: (Event) -> Unit, 
    onDeleteEvent: (Event) -> Unit, 
    onSettingsAction: (String, String, List<String?>, List<String?>?) -> Unit,
    isPinEnabled: Boolean,
    onPinEnabledChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when (currentAdminTab) {
            0 -> AdminProductList(products, isLoading, onRefresh, onEditProductRequested, { p -> products.remove(p); onUpdateSheet(p, "delete") })
            1 -> ShouldRebuyScreen(products)
            2 -> DropdownSettingsManager(settings, onSettingsAction, isPinEnabled, onPinEnabledChange)
            3 -> AdminEventsScreen(eventsList, isLoading, onRefresh, onEditEventRequested, onDeleteEvent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProductList(products: List<Product>, isLoading: Boolean, onRefresh: () -> Unit, onEdit: (Product) -> Unit, onDelete: (Product) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered by remember { derivedStateOf { products.filter { it.name.contains(query, true) || it.brand.contains(query, true) || it.category.contains(query, true) }.sortedBy { it.name.lowercase() } } }
    
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search Inventory...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Inventory, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { p ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${p.name} ${p.formattedSize}${p.unit}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("₱${p.price} (Cost: ₱${p.cost})", color = Color(0xFF00FF87), style = MaterialTheme.typography.bodySmall)
                                if (p.lastBoughtStore.isNotBlank()) {
                                    Text("Store: ${p.lastBoughtStore}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Text("Stock: ${p.stock}", color = if (p.stock <= p.threshold) Color.Red else Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                            Row { 
                                IconButton(onClick = { onEdit(p) }) { Icon(Icons.Default.Edit, null, tint = Color(0xFFFF7D1E)) }
                                IconButton(onClick = { onDelete(p) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } 
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProductFormDialog(product: Product?, settings: DropdownSettings, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var size by remember { mutableStateOf(product?.size?.toString() ?: "") }
    var cost by remember { mutableStateOf(product?.cost?.toString() ?: "") }
    var mVal by remember { mutableStateOf(product?.markupValue?.toString() ?: "") }
    var stock by remember { mutableStateOf(product?.stock?.toString() ?: "") }
    var thresh by remember { mutableStateOf(product?.threshold?.toString() ?: "") }
    var brand by remember { mutableStateOf(product?.brand ?: "") }
    var cat by remember { mutableStateOf(product?.category ?: "") }
    var unit by remember { mutableStateOf(product?.unit ?: "") }
    var store by remember { mutableStateOf(product?.lastBoughtStore ?: "") }
    var mType by remember { mutableStateOf(product?.markupType ?: "Percentage") }
    var ideal by remember { mutableStateOf(product?.idealStock?.toString() ?: "") }
    var sellPrice by remember { mutableStateOf(product?.price?.let { kotlin.math.ceil(it).toInt().toString() } ?: "") }

    val sortedBrands = remember { derivedStateOf { settings.brands.sortedBy { it.lowercase() } } }
    val sortedCategories = remember { derivedStateOf { settings.categories.sortedBy { it.lowercase() } } }
    val sortedUnits = remember { derivedStateOf { settings.units.sortedBy { it.lowercase() } } }
    val sortedStores = remember { derivedStateOf { settings.stores.sortedBy { it.lowercase() } } }

    fun recalcPrice(newCost: String? = null, newMarkup: String? = null, newType: String? = null) {
        val c = (newCost ?: cost).toDoubleOrNull() ?: 0.0
        val v = (newMarkup ?: mVal).toDoubleOrNull() ?: 0.0
        val type = newType ?: mType
        val res = if (type == "Percentage") c * (1 + v / 100) else c + v
        if (res >= 0) sellPrice = kotlin.math.ceil(res).toInt().toString()
    }

    fun recalcMarkup(newSellPrice: String) {
        val c = cost.toDoubleOrNull() ?: 0.0
        val p = newSellPrice.toDoubleOrNull() ?: 0.0
        if (c > 0) {
            val res = if (mType == "Percentage") ((p / c) - 1) * 100 else p - c
            mVal = if (mType == "Percentage") "%.2f".format(Locale.US, res) else if (res % 1.0 == 0.0) res.toInt().toString() else "%.2f".format(Locale.US, res)
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (product == null) "Add Product" else "Edit Product",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    item {
                        OutlinedTextField(
                            value = name, 
                            onValueChange = { name = it }, 
                            label = { Text("Product Name", style = MaterialTheme.typography.bodySmall) }, 
                            modifier = Modifier.fillMaxWidth(), 
                            textStyle = MaterialTheme.typography.bodyMedium,
                            trailingIcon = { if (name.isNotEmpty()) IconButton(onClick = { name = "" }) { Icon(Icons.Default.Close, null) } }
                        )
                        DropdownField("Brand", brand, sortedBrands.value) { brand = it }
                        DropdownField("Category", cat, sortedCategories.value) { cat = it }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) { DropdownField("Unit", unit, sortedUnits.value) { unit = it } }
                            OutlinedTextField(
                                value = size, 
                                onValueChange = { size = it }, 
                                label = { Text("Size", style = MaterialTheme.typography.bodySmall) }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = { if (size.isNotEmpty()) IconButton(onClick = { size = "" }) { Icon(Icons.Default.Close, null) } }
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = cost, 
                                onValueChange = { cost = it; recalcPrice(newCost = it) }, 
                                label = { Text("Cost", style = MaterialTheme.typography.bodySmall) }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = { if (cost.isNotEmpty()) IconButton(onClick = { cost = ""; recalcPrice(newCost = "") }) { Icon(Icons.Default.Close, null) } }
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                SegmentedButton(
                                    selected = mType == "Percentage",
                                    onClick = { mType = "Percentage"; recalcMarkup(sellPrice) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) { Text("%", style = MaterialTheme.typography.labelSmall) }
                                SegmentedButton(
                                    selected = mType == "Fixed",
                                    onClick = { mType = "Fixed"; recalcMarkup(sellPrice) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) { Text("₱", style = MaterialTheme.typography.labelSmall) }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = mVal,
                                onValueChange = { mVal = it; recalcPrice(newMarkup = it) },
                                label = { Text("Markup", style = MaterialTheme.typography.bodySmall) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = { if (mVal.isNotEmpty()) IconButton(onClick = { mVal = ""; recalcPrice(newMarkup = "") }) { Icon(Icons.Default.Close, null) } }
                            )
                            OutlinedTextField(
                                value = sellPrice,
                                onValueChange = { sellPrice = it; recalcMarkup(it) },
                                label = { Text("Price", style = MaterialTheme.typography.bodySmall) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = { if (sellPrice.isNotEmpty()) IconButton(onClick = { sellPrice = ""; recalcMarkup("") }) { Icon(Icons.Default.Close, null) } }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        OutlinedTextField(
                            value = stock, 
                            onValueChange = { if (it.all { c -> c.isDigit() }) stock = it }, 
                            label = { Text("Stock", style = MaterialTheme.typography.bodySmall) }, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            trailingIcon = { if (stock.isNotEmpty()) IconButton(onClick = { stock = "" }) { Icon(Icons.Default.Close, null) } }
                        )
                        OutlinedTextField(
                            value = thresh, 
                            onValueChange = { if (it.all { c -> c.isDigit() }) thresh = it }, 
                            label = { Text("Threshold", style = MaterialTheme.typography.bodySmall) }, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            trailingIcon = { if (thresh.isNotEmpty()) IconButton(onClick = { thresh = "" }) { Icon(Icons.Default.Close, null) } }
                        )
                        OutlinedTextField(
                            value = ideal, 
                            onValueChange = { if (it.all { c -> c.isDigit() }) ideal = it }, 
                            label = { Text("Ideal Stock", style = MaterialTheme.typography.bodySmall) }, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            trailingIcon = { if (ideal.isNotEmpty()) IconButton(onClick = { ideal = "" }) { Icon(Icons.Default.Close, null) } }
                        )

                        DropdownField("Store", store, sortedStores.value) { store = it }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    if (product != null) {
                        OutlinedButton(
                            onClick = { 
                                onSave(Product(System.currentTimeMillis().toString(), name, brand, cat, unit, size.toDoubleOrNull() ?: 0.0, cost.toDoubleOrNull() ?: 0.0, store, mType, mVal.toDoubleOrNull() ?: 0.0, sellPrice.toDoubleOrNull() ?: 0.0, stock.toIntOrNull() ?: 0, thresh.toIntOrNull() ?: 0, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()), ideal.toIntOrNull() ?: 0)) 
                            },
                            modifier = Modifier.weight(1.5f)
                        ) { Text("Save as New", textAlign = TextAlign.Center) }
                    }
                    Button(
                        onClick = { 
                            onSave(Product(product?.id ?: System.currentTimeMillis().toString(), name, brand, cat, unit, size.toDoubleOrNull() ?: 0.0, cost.toDoubleOrNull() ?: 0.0, store, mType, mVal.toDoubleOrNull() ?: 0.0, sellPrice.toDoubleOrNull() ?: 0.0, stock.toIntOrNull() ?: 0, thresh.toIntOrNull() ?: 0, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()), ideal.toIntOrNull() ?: 0)) 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7D1E)),
                        modifier = Modifier.weight(1.5f)
                    ) { Text("Save", textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, value: String, options: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, 
            onValueChange = { onValueChange(it); expanded = true }, 
            label = { Text(label) }, 
            trailingIcon = { 
                Row {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                }
            },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
        )
        val filtered = options.filter { it.contains(value, true) }
        if (filtered.isNotEmpty()) { 
            ExposedDropdownMenu(expanded, { expanded = false }) { 
                filtered.forEach { 
                    DropdownMenuItem(text = { Text(it) }, onClick = { onValueChange(it); expanded = false }) 
                } 
            } 
        }
    }
}

@Composable
fun ShouldRebuyScreen(products: List<Product>) {
    val list = products.filter { it.stock <= it.threshold }.sortedBy { it.name.lowercase() }
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().background(Color.Red.copy(alpha = 0.1f)).padding(16.dp)) {
            Text(
                text = "${list.size} items are below threshold",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
        }
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { 
            items(list) { p -> 
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(), 
                    colors = CardDefaults.elevatedCardColors(containerColor = Color(0x11FF0000))
                ) { 
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                        Column { 
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${p.brand} | Last: ${p.lastBoughtStore}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) 
                        } 
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${p.stock} / ${p.threshold}", color = Color.Red, fontWeight = FontWeight.Bold) 
                            Text("Buy: ${p.idealStock - p.stock}", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                        }
                    } 
                } 
            } 
        }
        
        Button(
            onClick = { exportManifest(context, list) }, 
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7D1E))
        ) { 
            Icon(Icons.Default.Download, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Manifest (.txt)") 
        }
    }
}

fun exportManifest(context: Context, list: List<Product>) {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val grouped = list.groupBy { it.category }.toSortedMap(compareBy { it.lowercase() })

        // File 1: Simple Order List for shopping
        val fileName1 = "G&J_Sari-Sari_Store_Orderlist_$timestamp.txt"
        val text1 = StringBuilder("G&J SARI-SARI STORE ORDER LIST\n\n").apply {
            grouped.forEach { (category, products) ->
                append("--- ${category.ifBlank { "UNCATEGORIZED" }.uppercase()} ---\n")
                products.sortedBy { it.name.lowercase() }.forEach {
                    val qtyToBuy = it.idealStock - it.stock
                    append("- ${it.name} ${it.formattedSize}${it.unit} --- ${if (qtyToBuy > 0) qtyToBuy else 0}x\n")
                }
                append("\n")
            }
        }.toString()

        // File 2: Detailed Rebuy Info
        val fileName2 = "G&J_Sari-Sari_Store_Rebuy_$timestamp.txt"
        val text2 = StringBuilder("G&J SARI-SARI STORE REBUY DETAILS\n\n").apply {
            grouped.forEach { (category, products) ->
                append("--- ${category.ifBlank { "UNCATEGORIZED" }.uppercase()} ---\n")
                products.sortedBy { it.name.lowercase() }.forEach {
                    append("- ${it.name}\n")
                    append("  Last Cost: ₱${it.cost} | Store: ${it.lastBoughtStore}\n\n")
                }
                append("\n")
            }
        }.toString()

        saveToDownloads(context, fileName1, text1)
        saveToDownloads(context, fileName2, text2)
        
        Toast.makeText(context, "Manifests Saved to Downloads", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) { Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show() }
}

private fun saveToDownloads(context: Context, fileName: String, content: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        }
    } else {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        File(downloadsDir, fileName).writeText(content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminEventsScreen(events: List<Event>, isLoading: Boolean, onRefresh: () -> Unit, onEdit: (Event) -> Unit, onDelete: (Event) -> Unit) {
    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(events) { e ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(e.dateCreated, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text("₱${e.amount}", color = if (e.amount < 0) Color.Red else Color(0xFF00FF87), fontWeight = FontWeight.Bold)
                            }
                            Text(e.details, style = MaterialTheme.typography.bodyLarge)
                            Text("By: ${e.createdBy}${if (e.editedBy.isNotBlank()) " | Ed: ${e.editedBy} (${e.editedDate})" else ""}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Row { 
                            IconButton(onClick = { onEdit(e) }) { Icon(Icons.Default.Edit, null, tint = Color(0xFFFF7D1E)) }
                            IconButton(onClick = { onDelete(e) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } 
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DropdownSettingsManager(settings: DropdownSettings, onAction: (String, String, List<String?>, List<String?>?) -> Unit, isPinEnabled: Boolean, onPinEnabledChange: (Boolean) -> Unit) {
    var subTab by remember { mutableIntStateOf(0) }
    val sections = listOf("Brands", "Categories", "Units", "Stores", "Messenger", "Security")
    var input by remember { mutableStateOf("") }; var editIdx by remember { mutableIntStateOf(-1) }; var oldVal by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                ScrollableTabRow(
                    selectedTabIndex = subTab, 
                    edgePadding = 0.dp, 
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    divider = {}
                ) { 
                    sections.forEachIndexed { i, t -> 
                        Tab(subTab == i, { subTab = i; input = ""; editIdx = -1 }, text = { Text(t) }) 
                    } 
                }
                IconButton(onClick = { updateApp(context, scope) }) { 
                    Icon(Icons.Default.SystemUpdate, null, tint = Color(0xFFFF7D1E)) 
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (subTab == 5) {
                SecuritySettings(onAction, isPinEnabled, onPinEnabledChange)
            } else {
                val list = when(subTab) { 
                    0 -> settings.brands; 1 -> settings.categories; 2 -> settings.units; 3 -> settings.stores; else -> settings.messengerKeys 
                }
                val sortedList by remember(subTab) { derivedStateOf { list.sortedBy { it.lowercase() } } }

                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input, 
                            onValueChange = { input = it }, 
                            label = { Text(if (editIdx == -1) "Add New" else "Update Entry") }, 
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            trailingIcon = { if (input.isNotEmpty()) IconButton(onClick = { input = "" }) { Icon(Icons.Default.Close, null) } }
                        )
                        Button(
                            onClick = {
                                if (input.isNotBlank()) {
                                    val newVal = input.trim(); val payload = MutableList(5) { "" }.apply { set(subTab, newVal) }
                                    val action = if (editIdx == -1) "add" else "update"
                                    val oldPayload = if (editIdx != -1) MutableList(5) { "" }.apply { set(subTab, oldVal) } else null
                                    if (editIdx == -1) { if (!list.contains(newVal)) list.add(newVal) } else { list[editIdx] = newVal; editIdx = -1 }
                                    onAction("Settings", action, payload, oldPayload); input = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7D1E))
                        ) { 
                            Text(if (editIdx == -1) "Add" else "Update") 
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sortedList) { s ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(s, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    IconButton(onClick = { editIdx = list.indexOf(s); input = s; oldVal = s }) { 
                                        Icon(Icons.Default.Edit, null, tint = Color.LightGray) 
                                    }
                                    IconButton(onClick = { 
                                        val p = MutableList(5) { "" }.apply { set(subTab, s) }
                                        list.remove(s)
                                        onAction("Settings", "delete", p, p) 
                                    }) { 
                                        Icon(Icons.Default.Delete, null, tint = Color.Red) 
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecuritySettings(onAction: (String, String, List<String?>, List<String?>?) -> Unit, isPinEnabled: Boolean, onPinEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var adminPw by remember { mutableStateOf(CacheManager.loadAdmin(context)) }
    var appPin by remember { mutableStateOf(CacheManager.loadPin(context)) }
    
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Security Settings", style = MaterialTheme.typography.titleLarge, color = Color(0xFFFF7D1E))
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Enable App PIN Lock", modifier = Modifier.weight(1f))
            Switch(checked = isPinEnabled, onCheckedChange = { 
                onPinEnabledChange(it)
                CacheManager.savePinEnabled(context, it)
            })
        }

        OutlinedTextField(
            value = adminPw,
            onValueChange = { adminPw = it },
            label = { Text("Admin Login Password") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = { 
                onAction("Admin", "update", listOf(adminPw, appPin, "N/A"), null)
                CacheManager.saveAdmin(context, adminPw)
            }) { Icon(Icons.Default.Save, null) } }
        )

        OutlinedTextField(
            value = appPin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) appPin = it },
            label = { Text("App Unlock PIN (6 Digits)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = { IconButton(onClick = { 
                if (appPin.length == 6) {
                    onAction("Admin", "update", listOf(adminPw, appPin, "N/A"), null)
                    CacheManager.savePin(context, appPin)
                } else {
                    Toast.makeText(context, "PIN must be 6 digits", Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Default.Save, null) } }
        )
        
        Text("Note: Changes are synced to Google Sheets.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

fun updateApp(context: Context, scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        try {
            val url = URL("https://raw.githubusercontent.com/insomniaczxz/GJStore/main/release/app-debug.apk")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            if (conn.responseCode == 200) {
                val file = File(context.cacheDir, "update.apk")
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val errorMsg = when(conn.responseCode) {
                    404 -> "Update file not found on GitHub"
                    403 -> "Access denied by GitHub"
                    else -> "Server error: ${conn.responseCode}"
                }
                withContext(Dispatchers.Main) { Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) { 
            withContext(Dispatchers.Main) { Toast.makeText(context, "Update Failed: ${e.message}", Toast.LENGTH_SHORT).show() } 
        }
    }
}

@Composable
fun EmployeeEventHistoryDialog(events: List<Event>, isLoading: Boolean, onDismiss: () -> Unit, onAddRequested: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Events"); IconButton(onClick = onAddRequested) { Icon(Icons.AutoMirrored.Filled.NoteAdd, null, tint = Color(0xFFFF7D1E)) } } }, text = { Column(modifier = Modifier.fillMaxHeight(0.7f)) { if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth()); LazyColumn { items(events) { e -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(e.dateCreated, style = MaterialTheme.typography.labelSmall); Text("₱${e.amount}", color = if (e.amount < 0) Color.Red else Color(0xFF00FF87)) }; Text(e.details, style = MaterialTheme.typography.bodySmall); Text("By: ${e.createdBy}${if (e.editedBy.isNotBlank()) " | Ed: ${e.editedBy}" else ""}", style = MaterialTheme.typography.labelSmall, color = Color.Gray) } } } } } } }, confirmButton = { TextButton(onDismiss) { Text("Close") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEntryDialog(event: Event? = null, onDismiss: () -> Unit, onSave: (Event) -> Unit) {
    var details by remember { mutableStateOf(event?.details ?: "") }
    var amount by remember { mutableStateOf(event?.amount?.toString() ?: "") }
    var person by remember { mutableStateOf(if (event == null) "" else if (event.editedBy.isNotBlank()) event.editedBy else event.createdBy) }
    val dateDisplay = if (event == null) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) else event.dateCreated

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = if (event == null) "New Event" else "Edit Event",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Text("Date: $dateDisplay", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                
                OutlinedTextField(
                    value = details, 
                    onValueChange = { details = it }, 
                    label = { Text("Details / Description") }, 
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    trailingIcon = { if (details.isNotEmpty()) IconButton(onClick = { details = "" }) { Icon(Icons.Default.Close, null) } }
                )
                
                OutlinedTextField(
                    value = amount, 
                    onValueChange = { if (it.isEmpty() || it == "-" || it.toDoubleOrNull() != null) amount = it }, 
                    label = { Text("Amount (₱)") }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null) },
                    trailingIcon = { if (amount.isNotEmpty()) IconButton(onClick = { amount = "" }) { Icon(Icons.Default.Close, null) } }
                )
                
                OutlinedTextField(
                    value = person, 
                    onValueChange = { 
                        person = it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() }
                    },
                    label = { Text(if (event == null) "Your Name (Created By)" else "Your Name (Edited By)") }, 
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    trailingIcon = { if (person.isNotEmpty()) IconButton(onClick = { person = "" }) { Icon(Icons.Default.Close, null) } }
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            val now = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date())
                            val a = amount.toDoubleOrNull() ?: 0.0
                            val finalized = if (event == null) Event(now, details, a, person, "", "") 
                                            else event.copy(details = details, amount = a, editedBy = person, editedDate = now)
                            onSave(finalized)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7D1E))
                    ) { Text("Save Event") }
                }
            }
        }
    }
}

object CacheManager {
    fun saveProducts(ctx: Context, list: List<Product>) = File(ctx.filesDir, "products_cache.json").writeText(Gson().toJson(list))
    fun loadProducts(ctx: Context): List<Product> = try { val f = File(ctx.filesDir, "products_cache.json"); if (f.exists()) Gson().fromJson(f.readText(), object : TypeToken<List<Product>>() {}.type) else emptyList() } catch (e: Exception) { emptyList() }
    fun saveEvents(ctx: Context, list: List<Event>) = File(ctx.filesDir, "events_cache.json").writeText(Gson().toJson(list))
    fun loadEvents(ctx: Context): List<Event> = try { val f = File(ctx.filesDir, "events_cache.json"); if (f.exists()) Gson().fromJson(f.readText(), object : TypeToken<List<Event>>() {}.type) else emptyList() } catch (e: Exception) { emptyList() }
    fun saveSettings(ctx: Context, s: DropdownSettings) = File(ctx.filesDir, "settings_cache.json").writeText(Gson().toJson(mapOf("brands" to s.brands, "categories" to s.categories, "units" to s.units, "stores" to s.stores, "messenger" to s.messengerKeys)))
    fun loadSettings(ctx: Context): DropdownSettings? = try {
        val f = File(ctx.filesDir, "settings_cache.json")
        if (f.exists()) {
            val m: Map<String, List<String>> = Gson().fromJson(f.readText(), object : TypeToken<Map<String, List<String>>>() {}.type)
            DropdownSettings().apply { m["brands"]?.let { brands.addAll(it) }; m["categories"]?.let { categories.addAll(it) }; m["units"]?.let { units.addAll(it) }; m["stores"]?.let { stores.addAll(it) }; m["messenger"]?.let { messengerKeys.addAll(it) } }
        } else null
    } catch (e: Exception) { null }
    fun saveAdmin(ctx: Context, pw: String) = File(ctx.filesDir, "admin_cache.txt").writeText(pw)
    fun loadAdmin(ctx: Context): String = try { val f = File(ctx.filesDir, "admin_cache.txt"); if (f.exists()) f.readText() else "" } catch (e: Exception) { "" }
    fun savePin(ctx: Context, pin: String) = File(ctx.filesDir, "pin_cache.txt").writeText(pin)
    fun loadPin(ctx: Context): String = try { val f = File(ctx.filesDir, "pin_cache.txt"); if (f.exists()) f.readText() else "041823" } catch (e: Exception) { "041823" }
    fun savePinEnabled(ctx: Context, enabled: Boolean) = File(ctx.filesDir, "pin_enabled.txt").writeText(enabled.toString())
    fun loadPinEnabled(ctx: Context): Boolean = try { val f = File(ctx.filesDir, "pin_enabled.txt"); if (f.exists()) f.readText().trim().toBoolean() else true } catch (e: Exception) { true }
    fun saveQueue(ctx: Context, q: List<PendingAction>) = File(ctx.filesDir, "queue_cache.json").writeText(Gson().toJson(q))
    fun loadQueue(ctx: Context): List<PendingAction> = try { val f = File(ctx.filesDir, "queue_cache.json"); if (f.exists()) Gson().fromJson(f.readText(), object : TypeToken<List<PendingAction>>() {}.type) else emptyList() } catch (e: Exception) { emptyList() }
}

object DataParser {
    fun parseProducts(body: List<List<String>>?): List<Product> {
        val list = mutableListOf<Product>()
        body?.drop(1)?.forEach { row ->
            if (row.size >= 2) list.add(Product(
                row[0], 
                row[1], 
                row.getOrElse(2) { "" }, 
                row.getOrElse(3) { "" }, 
                row.getOrElse(4) { "" }, 
                row.getOrNull(5)?.toDoubleOrNull() ?: 0.0, 
                row.getOrNull(6)?.toDoubleOrNull() ?: 0.0, 
                row.getOrElse(7) { "" }, 
                row.getOrElse(8) { "Percentage" },
                row.getOrNull(9)?.toDoubleOrNull() ?: 0.0, 
                row.getOrNull(10)?.toDoubleOrNull() ?: 0.0,
                row.getOrNull(11)?.toIntOrNull() ?: 0, 
                row.getOrNull(12)?.toIntOrNull() ?: 0, 
                row.getOrElse(13) { "" },
                row.getOrNull(14)?.toIntOrNull() ?: 0
            ))
        }
        return list
    }
    fun parseSettings(body: List<List<String>>?, ds: DropdownSettings) {
        ds.brands.clear(); ds.categories.clear(); ds.units.clear(); ds.stores.clear(); ds.messengerKeys.clear()
        body?.drop(1)?.forEach { row ->
            row.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { ds.brands.add(it.trim()) }
            row.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { ds.categories.add(it.trim()) }
            row.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { ds.units.add(it.trim()) }
            row.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { ds.stores.add(it.trim()) }
            row.getOrNull(4)?.takeIf { it.isNotBlank() }?.let { ds.messengerKeys.add(it.trim()) }
        }
    }
    fun parseEvents(body: List<List<String>>?): List<Event> = body?.drop(1)?.map { row ->
        Event(row.getOrElse(0){""}, row.getOrElse(1){""}, row.getOrNull(2)?.toDoubleOrNull() ?: 0.0, row.getOrElse(3){""}, row.getOrElse(4){""}, row.getOrElse(5){""})
    }?.reversed() ?: emptyList()
    fun productToRow(p: Product) = listOf(p.id, p.name, p.brand, p.category, p.unit, p.size.toString(), p.cost.toString(), p.lastBoughtStore, p.markupType, p.markupValue.toString(), p.price.toString(), p.stock.toString(), p.threshold.toString(), p.date.ifBlank { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()) }, p.idealStock.toString())
    fun eventToRow(e: Event) = listOf(e.dateCreated, e.details, e.amount.toString(), e.createdBy, e.editedBy, e.editedDate)
}
