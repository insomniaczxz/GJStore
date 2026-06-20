package com.example.gjstore

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.example.gjstore.data.DropdownSettings
import com.example.gjstore.data.Product
import com.google.gson.Gson
import java.io.File
import java.io.FileWriter
import kotlinx.coroutines.launch
import android.content.ContentValues
import android.provider.MediaStore
import android.content.Intent
import androidx.core.content.FileProvider
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.material.icons.filled.SystemUpdate
import android.os.Build
import java.io.OutputStream
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var isAdminLoggedIn by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showEventDialog by remember { mutableStateOf(false) }
    var showEventHistoryDialog by remember { mutableStateOf(false) }
    var currentAdminTab by remember { mutableStateOf(0) }
    val adminTabs = listOf("Products", "Rebuy", "Settings", "Events")

    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showFormDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val productsList = remember { mutableStateListOf<Product>() }
    val eventsList = remember { mutableStateListOf<com.example.gjstore.data.Event>() }
    var isLoading by remember { mutableStateOf(true) }
    var isEventsLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dynamicSettings = remember { DropdownSettings() }

    LaunchedEffect(Unit) {
        try {
            isLoading = true
            isEventsLoading = true

            // 1. Fetching Products List Catalog
            val productResponse = com.example.gjstore.network.RetrofitClient.apiService.readSheet(sheetName = "Products")
            if (productResponse.isSuccessful && productResponse.body() != null) {
                val rawRows = productResponse.body()!!
                productsList.clear()
                if (rawRows.size > 1) {
                    for (i in 1 until rawRows.size) {
                        val row = rawRows[i]
                        if (row.size >= 13) {
                            productsList.add(
                                Product(
                                    id = row[0], name = row[1], brand = row[2], category = row[3],
                                    unit = row[4], size = row[5].toDoubleOrNull() ?: 0.0,
                                    cost = row[6].toDoubleOrNull() ?: 0.0, lastBoughtStore = row[7],
                                    markupType = row[8], markupValue = row[9].toDoubleOrNull() ?: 0.0,
                                    stock = row[11].toIntOrNull() ?: 0, threshold = row[12].toIntOrNull() ?: 0,
                                    date = if (row.size > 13) row[13] else ""
                                )
                            )
                        } else if (row.size >= 2) {
                            productsList.add(
                                Product(
                                    id = row[0],
                                    name = row[1],
                                    brand = if (row.size > 2) row[2] else "",
                                    category = if (row.size > 3) row[3] else "",
                                    unit = if (row.size > 4) row[4] else "",
                                    size = if (row.size > 5) row[5].toDoubleOrNull() ?: 0.0 else 0.0,
                                    cost = if (row.size > 6) row[6].toDoubleOrNull() ?: 0.0 else 0.0,
                                    lastBoughtStore = if (row.size > 7) row[7] else "",
                                    markupType = if (row.size > 8) row[8] else "Percentage",
                                    markupValue = if (row.size > 9) row[9].toDoubleOrNull() ?: 0.0 else 0.0,
                                    stock = if (row.size > 11) row[11].toIntOrNull() ?: 0 else 0,
                                    threshold = if (row.size > 12) row[12].toIntOrNull() ?: 0 else 0,
                                    date = if (row.size > 13) row[13] else ""
                                )
                            )
                        }
                    }
                }
            } else {
                coroutineScope.launch {
                    Toast.makeText(context, "Products sheet load failed: ${productResponse.message()}", Toast.LENGTH_LONG).show()
                }
            }

            // 2. Fetching Configured Settings Parameters
            val settingsResponse = com.example.gjstore.network.RetrofitClient.apiService.readSheet(sheetName = "Settings")
            if (settingsResponse.isSuccessful && settingsResponse.body() != null) {
                val dataGrid = settingsResponse.body()!!
                
                dynamicSettings.brands.clear()
                dynamicSettings.categories.clear()
                dynamicSettings.units.clear()
                dynamicSettings.stores.clear()
                dynamicSettings.messengerKeys.clear()

                if (dataGrid.size > 1) {
                    for (rowIndex in 1 until dataGrid.size) {
                        val rowValues = dataGrid[rowIndex]
                        if (rowValues.isNotEmpty() && rowValues[0].isNotBlank()) {
                            dynamicSettings.brands.add(rowValues[0].trim())
                        }
                        if (rowValues.size > 1 && rowValues[1].isNotBlank()) {
                            dynamicSettings.categories.add(rowValues[1].trim())
                        }
                        if (rowValues.size > 2 && rowValues[2].isNotBlank()) {
                            dynamicSettings.units.add(rowValues[2].trim())
                        }
                        if (rowValues.size > 3 && rowValues[3].isNotBlank()) {
                            dynamicSettings.stores.add(rowValues[3].trim())
                        }
                        if (rowValues.size > 4 && rowValues[4].isNotBlank()) {
                            dynamicSettings.messengerKeys.add(rowValues[4].trim())
                        }
                    }
                }
            }

            // 3. Fetching Events
            val eventsResponse = com.example.gjstore.network.RetrofitClient.apiService.readSheet(sheetName = "Events")
            if (eventsResponse.isSuccessful && eventsResponse.body() != null) {
                val rawRows = eventsResponse.body()!!
                eventsList.clear()
                for (i in 1 until rawRows.size) {
                    val row = rawRows[i]
                    if (row.size >= 3) {
                        eventsList.add(0, com.example.gjstore.data.Event(row[0], row[1], row[2].toDoubleOrNull() ?: 0.0))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            coroutineScope.launch {
                Toast.makeText(context, "Network/Parsing Error: App cannot connect to Sheet.", Toast.LENGTH_LONG).show()
            }
        } finally {
            isLoading = false
            isEventsLoading = false
        }
    }


    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("GJStore") },
                    actions = {
                        Button(onClick = { if (isAdminLoggedIn) isAdminLoggedIn = false else showLoginDialog = true }) {
                            Text(if (isAdminLoggedIn) "Logout Admin" else "Admin Login")
                        }
                    }
                )
                if (isAdminLoggedIn) {
                    TabRow(selectedTabIndex = currentAdminTab) {
                        adminTabs.forEachIndexed { index, title ->
                            Tab(selected = currentAdminTab == index, onClick = { currentAdminTab = index }, text = { Text(title) })
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (isAdminLoggedIn) {
                if (currentAdminTab == 0) {
                    FloatingActionButton(onClick = {
                        editingProduct = null
                        showFormDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Product")
                    }
                } else if (currentAdminTab == 3) {
                    FloatingActionButton(onClick = {
                        showEventDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Event")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!isAdminLoggedIn) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search Name, Brand, or Category...") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { showEventHistoryDialog = true },
                            modifier = Modifier.padding(top = 8.dp).height(40.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text("Events", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn {
                        val filteredList = productsList.filter {
                            it.name.contains(searchQuery, ignoreCase = true) || 
                            it.brand.contains(searchQuery, ignoreCase = true) ||
                            it.category.contains(searchQuery, ignoreCase = true)
                        }.sortedBy { it.name.lowercase() }
                        items(filteredList) { product ->
                            var showStockEditDialog by remember { mutableStateOf(false) }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { showStockEditDialog = true },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = "${product.name} ${product.formattedSize}${product.unit} ${product.category}", style = MaterialTheme.typography.titleLarge)
                                    Text(text = product.brand, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = "₱${product.price}", color = Color(0xFF00FF87), style = MaterialTheme.typography.titleMedium)
                                    Text(text = "Stock Remaining: ${product.stock}", color = if(product.stock <= product.threshold) Color.Red else Color.White)
                                }
                            }

                            if (showStockEditDialog) {
                                var newStock by remember { mutableStateOf(product.stock.toString()) }
                                var isSaving by remember { mutableStateOf(false) }
                                
                                AlertDialog(
                                    onDismissRequest = { if (!isSaving) showStockEditDialog = false },
                                    title = { Text("Update Stock: ${product.name}") },
                                    text = {
                                        OutlinedTextField(
                                            value = newStock,
                                            onValueChange = { if (it.all { char -> char.isDigit() }) newStock = it },
                                            label = { Text("Current Stock") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            enabled = !isSaving,
                                            onClick = {
                                                val stockInt = newStock.toIntOrNull() ?: product.stock
                                                isSaving = true
                                                coroutineScope.launch {
                                                    try {
                                                        val updatedProduct = product.copy(stock = stockInt)
                                                        val productRowData = listOf(
                                                            updatedProduct.id, updatedProduct.name, updatedProduct.brand,
                                                            updatedProduct.category, updatedProduct.unit, updatedProduct.size.toString(),
                                                            updatedProduct.cost.toString(), updatedProduct.lastBoughtStore,
                                                            updatedProduct.markupType, updatedProduct.markupValue.toString(),
                                                            updatedProduct.price.toString(), updatedProduct.stock.toString(),
                                                            updatedProduct.threshold.toString(),
                                                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                                        )
                                                        val requestBody = mapOf("sheetName" to "Products", "action" to "update", "data" to productRowData)
                                                        val response = com.example.gjstore.network.RetrofitClient.apiService.modifySheet(requestBody)
                                                        
                                                        if (response.isSuccessful || response.code() == 302) {
                                                            val idx = productsList.indexOfFirst { it.id == product.id }
                                                            if (idx != -1) productsList[idx] = updatedProduct
                                                            Toast.makeText(context, "Stock updated!", Toast.LENGTH_SHORT).show()
                                                            showStockEditDialog = false
                                                        } else {
                                                            Toast.makeText(context, "Sync failed.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Error updating stock.", Toast.LENGTH_SHORT).show()
                                                    } finally {
                                                        isSaving = false
                                                    }
                                                }
                                            }
                                        ) { Text("Update") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showStockEditDialog = false }) { Text("Cancel") }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                AdminDashboard(
                    products = productsList,
                    settings = dynamicSettings,
                    eventsList = eventsList,
                    isEventsLoading = isEventsLoading,
                    currentAdminTab = currentAdminTab,
                    onUpdateSheet = { targetProduct, executionAction ->
                        coroutineScope.launch {
                            try {
                                val productRowData = listOf(
                                    targetProduct.id,
                                    targetProduct.name,
                                    targetProduct.brand,
                                    targetProduct.category,
                                    targetProduct.unit,
                                    targetProduct.size.toString(),
                                    targetProduct.cost.toString(),
                                    targetProduct.lastBoughtStore,
                                    targetProduct.markupType,
                                    targetProduct.markupValue.toString(),
                                    targetProduct.price.toString(),
                                    targetProduct.stock.toString(),
                                    targetProduct.threshold.toString(),
                                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                )

                                Toast.makeText(context, "Syncing change with Google Sheets...", Toast.LENGTH_SHORT).show()

                                val requestBody = mapOf(
                                    "sheetName" to "Products",
                                    "action" to executionAction,
                                    "data" to productRowData
                                )

                                val response = com.example.gjstore.network.RetrofitClient.apiService.modifySheet(requestBody)

                                if (response.isSuccessful || response.code() == 302) {
                                    val successMessage = when (executionAction) {
                                        "add" -> "Product added successfully!"
                                        "update" -> "Product updated successfully!"
                                        "delete" -> "Product removed successfully!"
                                        else -> "Google Sheet Sync Successful!"
                                    }
                                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Cloud sync failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Connection error: Data may not have saved.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onEditProductRequested = { product ->
                        editingProduct = product
                        showFormDialog = true
                    }
                )
            }
        }
    }


    if (showLoginDialog) {
        var passwordInput by remember { mutableStateOf("") }
        var isCheckingPassword by remember { mutableStateOf(false) }
        var passwordVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isCheckingPassword) showLoginDialog = false },
            title = { Text("Admin Authentication") },
            text = {
                Column {
                    OutlinedTextField(
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Enter Password") },
                        singleLine = true,
                        enabled = !isCheckingPassword,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            val description = if (passwordVisible) "Hide password" else "Show password"

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        }
                    )
                    if (isCheckingPassword) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isCheckingPassword,
                    onClick = {
                        if (passwordInput.isEmpty()) return@TextButton
                        isCheckingPassword = true
                        coroutineScope.launch {
                            try {
                                val response = com.example.gjstore.network.RetrofitClient.apiService.readSheet(sheetName = "Admin")
                                if (response.isSuccessful && response.body() != null) {
                                    val rawRows = response.body()!!
                                    if (rawRows.size > 1 && rawRows[1].isNotEmpty() && passwordInput == rawRows[1][0]) {
                                        isAdminLoggedIn = true
                                        showLoginDialog = false
                                    } else {
                                        Toast.makeText(context, "Access Denied.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Connection Error.", Toast.LENGTH_SHORT).show()
                            } finally {
                                isCheckingPassword = false
                            }
                        }
                    }
                ) { Text("Login") }
            },
            dismissButton = { TextButton(onClick = { showLoginDialog = false }) { Text("Cancel") } }
        )
    }

    if (showEventDialog) {
        EventEntryDialog(
            onDismiss = { showEventDialog = false },
            onSave = { event ->
                coroutineScope.launch {
                    try {
                        val requestBody = mapOf(
                            "sheetName" to "Events",
                            "action" to "add",
                            "data" to listOf(event.date, event.details, event.amount.toString())
                        )
                        val response = com.example.gjstore.network.RetrofitClient.apiService.modifySheet(requestBody)
                        if (response.isSuccessful || response.code() == 302) {
                            eventsList.add(0, event) // Add to local list immediately
                            Toast.makeText(context, "Event recorded successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to record event.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Network error while saving event.", Toast.LENGTH_SHORT).show()
                    }
                }
                showEventDialog = false
            }
        )
    }

    if (showEventHistoryDialog) {
        EmployeeEventHistoryDialog(
            events = eventsList,
            isLoading = isEventsLoading,
            onDismiss = { showEventHistoryDialog = false },
            onAddRequested = {
                showEventDialog = true
            }
        )
    }

    if (showFormDialog) {
        AdminProductFormDialog(
            product = editingProduct,
            settings = dynamicSettings,
            onDismiss = { showFormDialog = false },
            onSave = { finalizedProduct ->
                if (editingProduct == null) {
                    productsList.add(finalizedProduct)
                    coroutineScope.launch {
                        try {
                            val productRowData = listOf(
                                finalizedProduct.id, finalizedProduct.name, finalizedProduct.brand,
                                finalizedProduct.category, finalizedProduct.unit, finalizedProduct.size.toString(),
                                finalizedProduct.cost.toString(), finalizedProduct.lastBoughtStore,
                                finalizedProduct.markupType, finalizedProduct.markupValue.toString(),
                                finalizedProduct.price.toString(), finalizedProduct.stock.toString(),
                                finalizedProduct.threshold.toString(),
                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            )
                            val requestBody = mapOf("sheetName" to "Products", "action" to "add", "data" to productRowData)
                            com.example.gjstore.network.RetrofitClient.apiService.modifySheet(requestBody)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                } else {
                    val index = productsList.indexOfFirst { it.id == finalizedProduct.id }
                    if (index != -1) productsList[index] = finalizedProduct
                    coroutineScope.launch {
                        try {
                            val productRowData = listOf(
                                finalizedProduct.id, finalizedProduct.name, finalizedProduct.brand,
                                finalizedProduct.category, finalizedProduct.unit, finalizedProduct.size.toString(),
                                finalizedProduct.cost.toString(), finalizedProduct.lastBoughtStore,
                                finalizedProduct.markupType, finalizedProduct.markupValue.toString(),
                                finalizedProduct.price.toString(), finalizedProduct.stock.toString(),
                                finalizedProduct.threshold.toString(),
                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            )
                            val requestBody = mapOf("sheetName" to "Products", "action" to "update", "data" to productRowData)
                            com.example.gjstore.network.RetrofitClient.apiService.modifySheet(requestBody)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
                showFormDialog = false
            }
        )
    }
}

@Composable
fun EmployeeEventHistoryDialog(
    events: List<com.example.gjstore.data.Event>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAddRequested: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Event History")
                TextButton(onClick = onAddRequested) {
                    Text("+ Add New")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.7f)) {
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (events.isEmpty() && !isLoading) {
                    Text("No events recorded yet.", modifier = Modifier.padding(16.dp))
                }
                LazyColumn {
                    items(events) { event ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = event.date.substringBefore("GMT").substringBefore("standard").trim(), style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = "₱${event.amount}", 
                                        style = MaterialTheme.typography.bodyMedium, 
                                        color = if (event.amount < 0) Color.Red else Color(0xFF00FF87)
                                    )
                                }
                                Text(text = event.details, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun EventEntryDialog(onDismiss: () -> Unit, onSave: (com.example.gjstore.data.Event) -> Unit) {
    var details by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Event") },
        text = {
            Column {
                Text("Date: $date", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = isExpense, onClick = { isExpense = true })
                    Text("Expense (-)")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = !isExpense, onClick = { isExpense = false })
                    Text("Addition (+)")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Event Details") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₱)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                var amount = amountText.toDoubleOrNull() ?: 0.0
                if (isExpense && amount > 0) amount = -amount
                onSave(com.example.gjstore.data.Event(date, details, amount))
            }) {
                Text("Save Event")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AdminDashboard(
    products: MutableList<Product>,
    settings: DropdownSettings,
    eventsList: MutableList<com.example.gjstore.data.Event>,
    isEventsLoading: Boolean,
    currentAdminTab: Int,
    onUpdateSheet: (Product, String) -> Unit,
    onEditProductRequested: (Product) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when (currentAdminTab) {
            0 -> AdminProductList(
                products = products,
                onEditRequested = onEditProductRequested,
                onDeleteRequested = { product ->
                    products.remove(product)
                    onUpdateSheet(product, "delete")
                }
            )
            1 -> ShouldRebuyScreen(products = products)
            2 -> DropdownSettingsManager(settings)
            3 -> AdminEventsScreen(eventsList, isEventsLoading)
        }
    }
}

@Composable
fun AdminProductList(
    products: List<Product>,
    onEditRequested: (Product) -> Unit,
    onDeleteRequested: (Product) -> Unit
) {
    var adminSearchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = adminSearchQuery,
            onValueChange = { adminSearchQuery = it },
            label = { Text("Search Inventory (Admin Mode)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            val filteredList = products.filter {
                it.name.contains(adminSearchQuery, ignoreCase = true) || 
                it.brand.contains(adminSearchQuery, ignoreCase = true) ||
                it.category.contains(adminSearchQuery, ignoreCase = true)
            }.sortedBy { it.name.lowercase() }
            items(filteredList) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${product.name} ${product.formattedSize}${product.unit} ${product.category}", style = MaterialTheme.typography.titleLarge)
                            Text(text = product.brand, style = MaterialTheme.typography.bodyMedium)
                            Text(text = "Price: ₱${product.price} (Cost: ₱${product.cost})", color = Color(0xFF00FF87), style = MaterialTheme.typography.titleMedium)
                            Text(text = "Stock Remaining: ${product.stock}", color = if(product.stock <= product.threshold) Color.Red else Color.White)
                            if (product.date.isNotBlank()) {
                                Text(text = "Last Modified: ${product.date.substringBefore("GMT").substringBefore("standard").trim()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                        Row {
                            IconButton(onClick = { onEditRequested(product) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFFFF7D1E))
                            }
                            IconButton(onClick = { onDeleteRequested(product) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
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
fun AdminProductFormDialog(
    product: Product?,
    settings: DropdownSettings,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var size by remember { mutableStateOf(product?.size?.toString() ?: "") }
    var cost by remember { mutableStateOf(product?.cost?.toString() ?: "") }
    var markupVal by remember { mutableStateOf(product?.markupValue?.toString() ?: "") }
    var stock by remember { mutableStateOf(product?.stock?.toString() ?: "") }
    var threshold by remember { mutableStateOf(product?.threshold?.toString() ?: "") }

    var selectedBrand by remember { mutableStateOf(product?.brand ?: "") }
    var selectedCategory by remember { mutableStateOf(product?.category ?: "") }
    var selectedUnit by remember { mutableStateOf(product?.unit ?: "") }
    var selectedStore by remember { mutableStateOf(product?.lastBoughtStore ?: "") }
    var selectedMarkupType by remember { mutableStateOf(product?.markupType ?: "Percentage") }

    var brandMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    var storeMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Add New Product" else "Edit Product Details") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, modifier = Modifier.fillMaxWidth())

                    // BRAND
                    ExposedDropdownMenuBox(
                        expanded = brandMenuExpanded,
                        onExpandedChange = { brandMenuExpanded = !brandMenuExpanded },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedBrand,
                            onValueChange = { 
                                selectedBrand = it
                                brandMenuExpanded = true 
                            },
                            label = { Text("Brand") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandMenuExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        val filteredBrands = settings.brands.filter { it.contains(selectedBrand, ignoreCase = true) }
                        if (filteredBrands.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = brandMenuExpanded,
                                onDismissRequest = { brandMenuExpanded = false }
                            ) {
                                filteredBrands.forEach { brand ->
                                    DropdownMenuItem(
                                        text = { Text(brand) },
                                        onClick = {
                                            selectedBrand = brand
                                            brandMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // CATEGORY
                    ExposedDropdownMenuBox(
                        expanded = categoryMenuExpanded,
                        onExpandedChange = { categoryMenuExpanded = !categoryMenuExpanded },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = { 
                                selectedCategory = it
                                categoryMenuExpanded = true 
                            },
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        val filteredCategories = settings.categories.filter { it.contains(selectedCategory, ignoreCase = true) }
                        if (filteredCategories.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = categoryMenuExpanded,
                                onDismissRequest = { categoryMenuExpanded = false }
                            ) {
                                filteredCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category) },
                                        onClick = {
                                            selectedCategory = category
                                            categoryMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // UNIT
                    ExposedDropdownMenuBox(
                        expanded = unitMenuExpanded,
                        onExpandedChange = { unitMenuExpanded = !unitMenuExpanded },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedUnit,
                            onValueChange = { 
                                selectedUnit = it
                                unitMenuExpanded = true 
                            },
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        val filteredUnits = settings.units.filter { it.contains(selectedUnit, ignoreCase = true) }
                        if (filteredUnits.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = unitMenuExpanded,
                                onDismissRequest = { unitMenuExpanded = false }
                            ) {
                                filteredUnits.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit) },
                                        onClick = {
                                            selectedUnit = unit
                                            unitMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(value = size, onValueChange = { size = it }, label = { Text("Size") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Cost Price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                    Text("Markup Type Selection:", modifier = Modifier.padding(top = 8.dp))
                    Row {
                        Row(modifier = Modifier.clickable { selectedMarkupType = "Percentage" }) {
                            RadioButton(selected = selectedMarkupType == "Percentage", onClick = { selectedMarkupType = "Percentage" })
                            Text("Percentage (%)", modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(modifier = Modifier.clickable { selectedMarkupType = "Fixed" }) {
                            RadioButton(selected = selectedMarkupType == "Fixed", onClick = { selectedMarkupType = "Fixed" })
                            Text("Fixed (₱)", modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                        }
                    }

                    OutlinedTextField(value = markupVal, onValueChange = { markupVal = it }, label = { Text("Markup Value") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                    val calculatedPrice = remember(cost, markupVal, selectedMarkupType) {
                        val c = cost.toDoubleOrNull() ?: 0.0
                        val m = markupVal.toDoubleOrNull() ?: 0.0
                        val raw = if (selectedMarkupType == "Percentage") c * (1 + (m / 100)) else c + m
                        kotlin.math.ceil(raw)
                    }
                    Text("Calculated Selling Price: ₱$calculatedPrice", color = Color(0xFF00FF87), modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("Stock Level") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = threshold, onValueChange = { threshold = it }, label = { Text("Low Stock Warning Limit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                    // LAST BOUGHT STORE
                    ExposedDropdownMenuBox(
                        expanded = storeMenuExpanded,
                        onExpandedChange = { storeMenuExpanded = !storeMenuExpanded },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedStore,
                            onValueChange = { 
                                selectedStore = it
                                storeMenuExpanded = true 
                            },
                            label = { Text("Last Bought Store") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = storeMenuExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        val filteredStores = settings.stores.filter { it.contains(selectedStore, ignoreCase = true) }
                        if (filteredStores.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = storeMenuExpanded,
                                onDismissRequest = { storeMenuExpanded = false }
                            ) {
                                filteredStores.forEach { store ->
                                    DropdownMenuItem(
                                        text = { Text(store) },
                                        onClick = {
                                            selectedStore = store
                                            storeMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalized = Product(
                    id = product?.id ?: System.currentTimeMillis().toString(),
                    name = name, brand = selectedBrand, category = selectedCategory, unit = selectedUnit,
                    size = size.toDoubleOrNull() ?: 0.0, cost = cost.toDoubleOrNull() ?: 0.0,
                    lastBoughtStore = selectedStore, markupType = selectedMarkupType, markupValue = markupVal.toDoubleOrNull() ?: 0.0,
                    stock = stock.toIntOrNull() ?: 0, threshold = threshold.toIntOrNull() ?: 0,
                    date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                )
                onSave(finalized)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ShouldRebuyScreen(products: List<Product>) {
    val reorderManifestList = products.filter { it.stock <= it.threshold }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(reorderManifestList) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x33FF0000))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleSmall)
                            Text("${product.brand} | ${product.lastBoughtStore}", style = MaterialTheme.typography.bodySmall)
                            Text("Last Cost: ₱${product.cost}", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("${product.stock} / ${product.threshold}", color = Color.Red, style = MaterialTheme.typography.titleSmall)
                            Text("Stock/Min", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                        }
                    }
                }
            }
        }
        val context = LocalContext.current

        Button(
            onClick = {
                try {
                    val fileName = "GJStore_Rebuy_Manifest_${System.currentTimeMillis()}.txt"
                    val manifestText = StringBuilder().apply {
                        append("GJSTORE REBUY MANIFEST\n")
                        append("=======================\n\n")
                        reorderManifestList.forEach { item ->
                            append("- ${item.name} (${item.brand}) -> Current Stock: ${item.stock} (Threshold: ${item.threshold}) | Last Cost: ₱${item.cost}\n")
                        }
                    }.toString()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                        }

                        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        uri?.let {
                            context.contentResolver.openOutputStream(it)?.use { stream ->
                                stream.write(manifestText.toByteArray())
                            }
                            Toast.makeText(context, "Manifest saved to Downloads!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // For older Android versions
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val file = File(downloadsDir, fileName)
                        file.writeText(manifestText)
                        Toast.makeText(context, "Manifest saved to Downloads!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Failed to export file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Order Manifest File (.txt)")
        }
    }

}

@Composable
fun AdminEventsScreen(events: List<com.example.gjstore.data.Event>, isLoading: Boolean) {
    if (isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(events) { event ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = event.date.substringBefore("GMT").substringBefore("standard").trim(), style = MaterialTheme.typography.labelMedium)
                        Text(text = "₱${event.amount}", style = MaterialTheme.typography.titleMedium, color = if (event.amount < 0) Color.Red else Color(0xFF00FF87))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = event.details, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

}

@Composable
fun DropdownSettingsManager(settings: DropdownSettings) {
    var subTabState by remember { mutableStateOf(0) }
    val sections = listOf("Brands", "Categories", "Units", "Stores", "Messenger Keys")

    var entryInputText by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf(-1) }
    var oldValueForUpdate by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Explicitly resolve the list based on state to ensure observability
    val currentWorkingList = when(subTabState) {
        0 -> settings.brands
        1 -> settings.categories
        2 -> settings.units
        3 -> settings.stores
        else -> settings.messengerKeys
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            ScrollableTabRow(
                selectedTabIndex = subTabState,
                edgePadding = 0.dp,
                modifier = Modifier.weight(1f)
            ) {
                sections.forEachIndexed { index, title ->
                    Tab(selected = subTabState == index, onClick = {
                        subTabState = index
                        entryInputText = ""
                        editingIndex = -1
                        oldValueForUpdate = ""
                    }, text = { Text(title) })
                }
            }
            
            IconButton(onClick = {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val apkUrl = "https://raw.githubusercontent.com/insomniaczxz/GJStore/main/app/build/outputs/apk/debug/app-debug.apk"
                        val connection = URL(apkUrl).openConnection() as HttpURLConnection
                        connection.connect()
                        
                        if (connection.responseCode == 200) {
                            val apkFile = File(context.cacheDir, "update.apk")
                            connection.inputStream.use { input ->
                                apkFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                setDataAndType(contentUri, "application/vnd.android.package-archive")
                            }
                            context.startActivity(installIntent)
                        } else {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(context, "Update not found on GitHub.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "Failed to download update.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }) {
                Icon(Icons.Default.SystemUpdate, contentDescription = "Update App", tint = Color(0xFFFF7D1E))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = entryInputText,
                onValueChange = { entryInputText = it },
                label = { Text(if (editingIndex == -1) "Add New to ${sections[subTabState]}" else "Modify Selection") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (entryInputText.isNotBlank()) {
                        val targetedValue = entryInputText.trim()

                        coroutineScope.launch {
                            try {
                                Toast.makeText(context, "Syncing with server...", Toast.LENGTH_SHORT).show()

                                val syncPayload = mutableListOf("", "", "", "", "")
                                syncPayload[subTabState] = targetedValue

                                val oldPayload = mutableListOf("", "", "", "", "")
                                oldPayload[subTabState] = oldValueForUpdate

                                val requestBody = mutableMapOf<String, Any>(
                                    "sheetName" to "Settings",
                                    "action" to (if (editingIndex == -1) "add" else "update"),
                                    "data" to syncPayload
                                )
                                
                                if (editingIndex != -1) {
                                    requestBody["oldData"] = oldPayload
                                }

                                val response = com.example.gjstore.network.RetrofitClient.apiService.modifySheet(requestBody)

                                // Check for success (200 OK or 302 Redirect)
                                if (response.isSuccessful || response.code() == 302) {
                                    if (editingIndex == -1) {
                                        // Add to local list only if not a duplicate in the UI
                                        if (!currentWorkingList.contains(targetedValue)) {
                                            currentWorkingList.add(targetedValue)
                                        }
                                    } else {
                                        currentWorkingList[editingIndex] = targetedValue
                                        editingIndex = -1
                                        oldValueForUpdate = ""
                                    }
                                    entryInputText = ""
                                    Toast.makeText(context, "Success!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Server Error: ${response.code()}", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Network Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(if (editingIndex == -1) "Add" else "Update")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(currentWorkingList) { stringValue ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringValue,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            IconButton(onClick = {
                                editingIndex = currentWorkingList.indexOf(stringValue)
                                entryInputText = stringValue
                                oldValueForUpdate = stringValue
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Item", tint = Color.LightGray)
                            }
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    try {
                                        Toast.makeText(context, "Removing...", Toast.LENGTH_SHORT).show()

                                        val deletePayload = mutableListOf("", "", "", "", "")
                                        deletePayload[subTabState] = stringValue

                                        val requestBody = mapOf(
                                            "sheetName" to "Settings",
                                            "action" to "delete",
                                            "data" to deletePayload
                                        )

                                        val response = com.example.gjstore.network.RetrofitClient.apiService.modifySheet(requestBody)

                                        if (response.isSuccessful || response.code() == 302) {
                                            currentWorkingList.remove(stringValue)
                                            if (entryInputText == stringValue) {
                                                editingIndex = -1
                                                entryInputText = ""
                                            }
                                            Toast.makeText(context, "Removed!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Error: ${response.code()}", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Item", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }

}
