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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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

    val filteredProducts by remember {
        derivedStateOf {
            productsList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.brand.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
            }.sortedBy { it.name.lowercase() }
        }
    }

    // Function to handle sync queue
    fun performAction(action: PendingAction) {
        coroutineScope.launch {
            try {
                val body = mutableMapOf<String, Any>("sheetName" to action.sheetName, "action" to action.action, "data" to action.data)
                if (action.oldData != null) body["oldData"] = action.oldData
                val response = RetrofitClient.apiService.modifySheet(body)
                if (response.isSuccessful || response.code() == 302) {
                    pendingQueue.remove(action)
                    CacheManager.saveQueue(context, pendingQueue.toList())
                }
            } catch (e: Exception) { /* Keep in queue */ }
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        isEventsLoading = true
        
        withContext(Dispatchers.IO) {
            val cachedProducts = CacheManager.loadProducts(context)
            val cachedEvents = CacheManager.loadEvents(context)
            val cachedSettings = CacheManager.loadSettings(context)
            val cachedQueue = CacheManager.loadQueue(context)
            
            withContext(Dispatchers.Main) {
                productsList.addAll(cachedProducts)
                eventsList.addAll(cachedEvents)
                pendingQueue.addAll(cachedQueue)
                cachedSettings?.let {
                    dynamicSettings.brands.addAll(it.brands); dynamicSettings.categories.addAll(it.categories)
                    dynamicSettings.units.addAll(it.units); dynamicSettings.stores.addAll(it.stores)
                    dynamicSettings.messengerKeys.addAll(it.messengerKeys)
                }
                // Try sync existing queue
                pendingQueue.toList().forEach { performAction(it) }
            }
        }

        try {
            val pResponse = RetrofitClient.apiService.readSheet("Products")
            if (pResponse.isSuccessful) {
                val newProducts = DataParser.parseProducts(pResponse.body())
                productsList.clear(); productsList.addAll(newProducts)
                withContext(Dispatchers.IO) { CacheManager.saveProducts(context, newProducts) }
            }
            val sResponse = RetrofitClient.apiService.readSheet("Settings")
            if (sResponse.isSuccessful) {
                DataParser.parseSettings(sResponse.body(), dynamicSettings)
                withContext(Dispatchers.IO) { CacheManager.saveSettings(context, dynamicSettings) }
            }
            val eResponse = RetrofitClient.apiService.readSheet("Events")
            if (eResponse.isSuccessful) {
                val newEvents = DataParser.parseEvents(eResponse.body())
                eventsList.clear(); eventsList.addAll(newEvents)
                withContext(Dispatchers.IO) { CacheManager.saveEvents(context, newEvents) }
            }
            val aResponse = RetrofitClient.apiService.readSheet("Admin")
            if (aResponse.isSuccessful) {
                aResponse.body()?.getOrNull(1)?.getOrNull(0)?.let { CacheManager.saveAdmin(context, it) }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Working Offline", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false; isEventsLoading = false
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Row { Text("GJStore"); if (pendingQueue.isNotEmpty()) Text(" (Syncing...)", color = Color.Gray, style = MaterialTheme.typography.labelSmall) } },
                    actions = { Button(onClick = { if (isAdminLoggedIn) isAdminLoggedIn = false else showLoginDialog = true }) { Text(if (isAdminLoggedIn) "Logout" else "Admin") } }
                )
                if (isAdminLoggedIn) {
                    TabRow(selectedTabIndex = currentAdminTab) {
                        adminTabs.forEachIndexed { index, title -> Tab(selected = currentAdminTab == index, onClick = { currentAdminTab = index }, text = { Text(title) }) }
                    }
                }
            }
        },
        floatingActionButton = {
            if (isAdminLoggedIn && (currentAdminTab == 0 || currentAdminTab == 3)) {
                FloatingActionButton(onClick = { if (currentAdminTab == 0) { editingProduct = null; showFormDialog = true } else showEventDialog = true }) { Icon(Icons.Default.Add, null) }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (!isAdminLoggedIn) {
                UserDashboard(searchQuery, { searchQuery = it }, filteredProducts, { showEventHistoryDialog = true }, { updated ->
                    val idx = productsList.indexOfFirst { it.id == updated.id }
                    if (idx != -1) {
                        productsList[idx] = updated
                        val action = PendingAction("Products", "update", DataParser.productToRow(updated))
                        pendingQueue.add(action); performAction(action)
                        coroutineScope.launch(Dispatchers.IO) { CacheManager.saveProducts(context, productsList.toList()); CacheManager.saveQueue(context, pendingQueue.toList()) }
                    }
                })
            } else {
                AdminDashboard(productsList, dynamicSettings, eventsList, isEventsLoading, currentAdminTab, { target, actionStr ->
                    val data = DataParser.productToRow(target)
                    val act = PendingAction("Products", actionStr, data)
                    pendingQueue.add(act); performAction(act)
                    coroutineScope.launch(Dispatchers.IO) { CacheManager.saveProducts(context, productsList.toList()); CacheManager.saveQueue(context, pendingQueue.toList()) }
                }, { editingProduct = it; showFormDialog = true }, { editingEvent = it; showEventDialog = true }, { event ->
                    eventsList.remove(event)
                    val data = listOf(event.date, event.details, event.amount.toString())
                    val act = PendingAction("Events", "delete", data, data)
                    pendingQueue.add(act); performAction(act)
                    coroutineScope.launch(Dispatchers.IO) { CacheManager.saveEvents(context, eventsList.toList()); CacheManager.saveQueue(context, pendingQueue.toList()) }
                })
            }
        }
    }

    if (showLoginDialog) AdminLoginDialog({ showLoginDialog = false }, { isAdminLoggedIn = true; showLoginDialog = false })
    if (showEventDialog) EventEntryDialog(editingEvent, { showEventDialog = false; editingEvent = null }, { event ->
        val action = if (editingEvent == null) "add" else "update"
        val data = listOf(event.date, event.details, event.amount.toString())
        val oldData = if (editingEvent != null) listOf(editingEvent!!.date, editingEvent!!.details, editingEvent!!.amount.toString()) else null
        
        if (editingEvent == null) eventsList.add(0, event) else { val idx = eventsList.indexOf(editingEvent); if (idx != -1) eventsList[idx] = event }
        val act = PendingAction("Events", action, data, oldData)
        pendingQueue.add(act); performAction(act)
        coroutineScope.launch(Dispatchers.IO) { CacheManager.saveEvents(context, eventsList.toList()); CacheManager.saveQueue(context, pendingQueue.toList()) }
        showEventDialog = false; editingEvent = null
    })
    if (showEventHistoryDialog) EmployeeEventHistoryDialog(eventsList, isEventsLoading, { showEventHistoryDialog = false }, { showEventDialog = true }, { editingEvent = it; showEventDialog = true })
    if (showFormDialog) AdminProductFormDialog(editingProduct, dynamicSettings, { showFormDialog = false }, { finalized ->
        if (editingProduct == null) productsList.add(finalized) else { val idx = productsList.indexOfFirst { it.id == finalized.id }; if (idx != -1) productsList[idx] = finalized }
        val act = PendingAction("Products", if (editingProduct == null) "add" else "update", DataParser.productToRow(finalized))
        pendingQueue.add(act); performAction(act)
        coroutineScope.launch(Dispatchers.IO) { CacheManager.saveProducts(context, productsList.toList()); CacheManager.saveQueue(context, pendingQueue.toList()) }
        showFormDialog = false
    })
}

@Composable
fun UserDashboard(searchQuery: String, onSearchQueryChange: (String) -> Unit, filteredProducts: List<Product>, onEventHistoryRequested: () -> Unit, onProductUpdated: (Product) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = searchQuery, onValueChange = onSearchQueryChange, label = { Text("Search...") }, modifier = Modifier.weight(1f))
            Button(onClick = onEventHistoryRequested, modifier = Modifier.padding(top = 8.dp).height(40.dp)) { Text("Events") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(filteredProducts) { product ->
                var showDialog by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { showDialog = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${product.name} ${product.formattedSize}${product.unit}", style = MaterialTheme.typography.titleLarge)
                        Text(product.brand, style = MaterialTheme.typography.bodyMedium)
                        Text("₱${product.price}", color = Color(0xFF00FF87), style = MaterialTheme.typography.titleMedium)
                        Text("Stock: ${product.stock}", color = if (product.stock <= product.threshold) Color.Red else Color.White)
                    }
                }
                if (showDialog) {
                    var newStock by remember { mutableStateOf(product.stock.toString()) }
                    AlertDialog(onDismissRequest = { showDialog = false }, title = { Text("Update Stock") }, text = { OutlinedTextField(newStock, { if (it.all { c -> c.isDigit() }) newStock = it }, label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }, confirmButton = { TextButton(onClick = { onProductUpdated(product.copy(stock = newStock.toIntOrNull() ?: product.stock)); showDialog = false }) { Text("Update") } }, dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } })
                }
            }
        }
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
fun AdminDashboard(products: MutableList<Product>, settings: DropdownSettings, eventsList: MutableList<Event>, isEventsLoading: Boolean, currentAdminTab: Int, onUpdateSheet: (Product, String) -> Unit, onEditProductRequested: (Product) -> Unit, onEditEventRequested: (Event) -> Unit, onDeleteEvent: (Event) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        when (currentAdminTab) {
            0 -> AdminProductList(products, onEditProductRequested, { p -> products.remove(p); onUpdateSheet(p, "delete") })
            1 -> ShouldRebuyScreen(products)
            2 -> DropdownSettingsManager(settings)
            3 -> AdminEventsScreen(eventsList, isEventsLoading, onEditEventRequested, onDeleteEvent)
        }
    }
}

@Composable
fun AdminProductList(products: List<Product>, onEdit: (Product) -> Unit, onDelete: (Product) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered by remember { derivedStateOf { products.filter { it.name.contains(query, true) || it.brand.contains(query, true) }.sortedBy { it.name.lowercase() } } }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("Search Inventory") }, modifier = Modifier.fillMaxWidth())
        LazyColumn {
            items(filtered) { p ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) { Text("${p.name} ${p.formattedSize}${p.unit}", style = MaterialTheme.typography.titleLarge); Text("Price: ₱${p.price} (Cost: ₱${p.cost})", color = Color(0xFF00FF87)); Text("Stock: ${p.stock}") }
                        Row { IconButton(onClick = { onEdit(p) }) { Icon(Icons.Default.Edit, null, tint = Color(0xFFFF7D1E)) }; IconButton(onClick = { onDelete(p) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } }
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

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (product == null) "Add Product" else "Edit Product") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                DropdownField("Brand", brand, settings.brands) { brand = it }; DropdownField("Category", cat, settings.categories) { cat = it }; DropdownField("Unit", unit, settings.units) { unit = it }
                OutlinedTextField(size, { size = it }, label = { Text("Size") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cost, { cost = it }, label = { Text("Cost") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Row { RadioButton(mType == "Percentage", { mType = "Percentage" }); Text(" % "); RadioButton(mType == "Fixed", { mType = "Fixed" }); Text(" ₱ ") }
                OutlinedTextField(mVal, { mVal = it }, label = { Text("Markup") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(stock, { stock = it }, label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(thresh, { thresh = it }, label = { Text("Threshold") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                DropdownField("Store", store, settings.stores) { store = it }
            }
        }
    }, confirmButton = { Button(onClick = { onSave(Product(product?.id ?: System.currentTimeMillis().toString(), name, brand, cat, unit, size.toDoubleOrNull() ?: 0.0, cost.toDoubleOrNull() ?: 0.0, store, mType, mVal.toDoubleOrNull() ?: 0.0, 0.0, stock.toIntOrNull() ?: 0, thresh.toIntOrNull() ?: 0, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, value: String, options: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(value, { onValueChange(it); expanded = true }, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor())
        val filtered = options.filter { it.contains(value, true) }
        if (filtered.isNotEmpty()) { ExposedDropdownMenu(expanded, { expanded = false }) { filtered.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onValueChange(it); expanded = false }) } } }
    }
}

@Composable
fun ShouldRebuyScreen(products: List<Product>) {
    val list = products.filter { it.stock <= it.threshold }
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) { items(list) { p -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = Color(0x33FF0000))) { Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(p.name); Text("${p.brand} | ${p.lastBoughtStore}", style = MaterialTheme.typography.bodySmall) }; Text("${p.stock} / ${p.threshold}", color = Color.Red) } } } }
        Button(onClick = { exportManifest(context, list) }, modifier = Modifier.fillMaxWidth()) { Text("Export Manifest (.txt)") }
    }
}

fun exportManifest(context: Context, list: List<Product>) {
    try {
        val fileName = "Manifest_${System.currentTimeMillis()}.txt"
        val text = StringBuilder("REBUY MANIFEST\n\n").apply { list.forEach { append("- ${it.name} (${it.brand}) Stock: ${it.stock}/${it.threshold}\n") } }.toString()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, fileName); put(MediaStore.MediaColumns.MIME_TYPE, "text/plain"); put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS) }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri -> context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }; Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }
        } else {
            val file = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
            file.writeText(text); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) { Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show() }
}

@Composable
fun AdminEventsScreen(events: List<Event>, isLoading: Boolean, onEdit: (Event) -> Unit, onDelete: (Event) -> Unit) {
    if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(events) { e ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(e.date.split(" ")[0], style = MaterialTheme.typography.labelMedium); Text("₱${e.amount}", color = if (e.amount < 0) Color.Red else Color(0xFF00FF87)) }; Text(e.details) }
                    Row { IconButton(onClick = { onEdit(e) }) { Icon(Icons.Default.Edit, null, tint = Color(0xFFFF7D1E)) }; IconButton(onClick = { onDelete(e) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } }
                }
            }
        }
    }
}

@Composable
fun DropdownSettingsManager(settings: DropdownSettings) {
    var subTab by remember { mutableIntStateOf(0) }
    val sections = listOf("Brands", "Categories", "Units", "Stores", "Messenger")
    var input by remember { mutableStateOf("") }
    var editIdx by remember { mutableIntStateOf(-1) }
    var oldVal by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    val list = when(subTab) { 0 -> settings.brands; 1 -> settings.categories; 2 -> settings.units; 3 -> settings.stores; else -> settings.messengerKeys }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ScrollableTabRow(selectedTabIndex = subTab, edgePadding = 0.dp, modifier = Modifier.weight(1f)) { sections.forEachIndexed { i, t -> Tab(subTab == i, { subTab = i; input = ""; editIdx = -1 }, text = { Text(t) }) } }
            IconButton(onClick = { updateApp(context, scope) }) { Icon(Icons.Default.SystemUpdate, null, tint = Color(0xFFFF7D1E)) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, label = { Text(if (editIdx == -1) "Add" else "Update") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                if (input.isNotBlank()) {
                    val newVal = input.trim(); val payload = MutableList(5) { "" }.apply { set(subTab, newVal) }
                    val action = if (editIdx == -1) "add" else "update"
                    val body = mutableMapOf<String, Any>("sheetName" to "Settings", "action" to action, "data" to payload)
                    if (editIdx != -1) body["oldData"] = MutableList(5) { "" }.apply { set(subTab, oldVal) }
                    scope.launch { try { if (RetrofitClient.apiService.modifySheet(body).isSuccessful) { if (editIdx == -1) { if (!list.contains(newVal)) list.add(newVal) } else { list[editIdx] = newVal; editIdx = -1 }; input = ""; withContext(Dispatchers.IO) { CacheManager.saveSettings(context, settings) } } } catch (e: Exception) {} }
                }
            }) { Text(if (editIdx == -1) "Add" else "Update") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 16.dp)) {
            items(list) { s ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(s, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editIdx = list.indexOf(s); input = s; oldVal = s }) { Icon(Icons.Default.Edit, null, tint = Color.LightGray) }
                        IconButton(onClick = { scope.launch { try { val p = MutableList(5) { "" }.apply { set(subTab, s) }; if (RetrofitClient.apiService.modifySheet(mapOf("sheetName" to "Settings", "action" to "delete", "data" to p)).isSuccessful) { list.remove(s); withContext(Dispatchers.IO) { CacheManager.saveSettings(context, settings) } } } catch (e: Exception) {} } }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    }
                }
            }
        }
    }
}

fun updateApp(context: Context, scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) { try { val url = URL("https://raw.githubusercontent.com/insomniaczxz/GJStore/main/release/app-debug.apk"); val conn = url.openConnection() as HttpURLConnection; if (conn.responseCode == 200) { val file = File(context.cacheDir, "update.apk"); conn.inputStream.use { i -> file.outputStream().use { o -> i.copyTo(o) } }; val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }) } } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show() } } }
}

@Composable
fun EmployeeEventHistoryDialog(events: List<Event>, isLoading: Boolean, onDismiss: () -> Unit, onAddRequested: () -> Unit, onEditRequested: (Event) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("History"); TextButton(onAddRequested) { Text("+ Add") } } }, text = { Column(modifier = Modifier.fillMaxHeight(0.7f)) { if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth()); LazyColumn { items(events) { e -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(e.date.split(" ")[0], style = MaterialTheme.typography.labelSmall); Text("₱${e.amount}", color = if (e.amount < 0) Color.Red else Color(0xFF00FF87)) }; Text(e.details, style = MaterialTheme.typography.bodySmall) }; IconButton(onClick = { onEditRequested(e) }) { Icon(Icons.Default.Edit, null, tint = Color(0xFFFF7D1E)) } } } } } } }, confirmButton = { TextButton(onDismiss) { Text("Close") } })
}

@Composable
fun EventEntryDialog(event: Event? = null, onDismiss: () -> Unit, onSave: (Event) -> Unit) {
    var details by remember { mutableStateOf(event?.details ?: "") }; var amount by remember { mutableStateOf(event?.amount?.let { Math.abs(it).toString() } ?: "") }; var isExp by remember { mutableStateOf(event?.amount?.let { it < 0 } ?: true) }
    val date = event?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (event == null) "Add Event" else "Edit Event") }, text = { Column { Text("Date: $date"); Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { RadioButton(isExp, { isExp = true }); Text("Expense"); Spacer(modifier = Modifier.width(16.dp)); RadioButton(!isExp, { isExp = false }); Text("Income") }; OutlinedTextField(details, { details = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) } }, confirmButton = { Button(onClick = { var a = amount.toDoubleOrNull() ?: 0.0; if (isExp && a > 0) a = -a; onSave(Event(date, details, a)) }) { Text("Save") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
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
    fun saveQueue(ctx: Context, q: List<PendingAction>) = File(ctx.filesDir, "queue_cache.json").writeText(Gson().toJson(q))
    fun loadQueue(ctx: Context): List<PendingAction> = try { val f = File(ctx.filesDir, "queue_cache.json"); if (f.exists()) Gson().fromJson(f.readText(), object : TypeToken<List<PendingAction>>() {}.type) else emptyList() } catch (e: Exception) { emptyList() }
}

object DataParser {
    fun parseProducts(body: List<List<String>>?): List<Product> {
        val list = mutableListOf<Product>()
        body?.drop(1)?.forEach { row ->
            if (row.size >= 2) list.add(Product(row[0], row[1], row.getOrElse(2) { "" }, row.getOrElse(3) { "" }, row.getOrElse(4) { "" }, row.getOrNull(5)?.toDoubleOrNull() ?: 0.0, row.getOrNull(6)?.toDoubleOrNull() ?: 0.0, row.getOrElse(7) { "" }, row.getOrElse(8) { "Percentage" }, row.getOrNull(9)?.toDoubleOrNull() ?: 0.0, 0.0, row.getOrNull(11)?.toIntOrNull() ?: 0, row.getOrNull(12)?.toIntOrNull() ?: 0, row.getOrElse(13) { "" }))
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
    fun parseEvents(body: List<List<String>>?): List<Event> = body?.drop(1)?.map { row -> Event(row[0], row[1], row.getOrNull(2)?.toDoubleOrNull() ?: 0.0) }?.reversed() ?: emptyList()
    fun productToRow(p: Product) = listOf(p.id, p.name, p.brand, p.category, p.unit, p.size.toString(), p.cost.toString(), p.lastBoughtStore, p.markupType, p.markupValue.toString(), p.price.toString(), p.stock.toString(), p.threshold.toString(), SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
}
