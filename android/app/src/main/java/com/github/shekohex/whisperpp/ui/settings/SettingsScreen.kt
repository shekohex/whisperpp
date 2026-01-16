package com.github.shekohex.whisperpp.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.shekohex.whisperpp.ui.components.VoiceWaveform
import com.github.shekohex.whisperpp.*
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.*
import com.github.shekohex.whisperpp.updater.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class SettingsScreen(val route: String) {
    object Main : SettingsScreen("main")
    object Backend : SettingsScreen("backend")
    object PostProcessing : SettingsScreen("post_processing")
    object Keyboard : SettingsScreen("keyboard")
    object ProviderEdit : SettingsScreen("provider_edit?id={id}") {
        fun createRoute(id: String? = null) = "provider_edit?id=${id ?: ""}"
    }
}

@Composable
fun SettingsNavigation(dataStore: DataStore<Preferences>, showUpdate: Boolean = false) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SettingsScreen.Main.route) {
        composable(SettingsScreen.Main.route) {
            MainSettingsScreen(dataStore, navController, showUpdate)
        }
        composable(SettingsScreen.Backend.route) {
            BackendSettingsScreen(dataStore, navController)
        }
        composable(SettingsScreen.PostProcessing.route) {
            SmartFixSettingsScreen(dataStore, navController)
        }
        composable(SettingsScreen.Keyboard.route) {
            KeyboardSettingsScreen(dataStore, navController)
        }
        composable(
            route = SettingsScreen.ProviderEdit.route,
            arguments = listOf(navArgument("id") { nullable = true })
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("id")
            ProviderEditScreen(dataStore, navController, providerId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    dataStore: DataStore<Preferences>,
    navController: NavHostController,
    showUpdate: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsState by dataStore.data.collectAsState(initial = null)
    val repository = remember { SettingsRepository(dataStore) }
    val providers by repository.providers.collectAsState(initial = emptyList())
    
    val activeBackendId = settingsState?.get(SPEECH_TO_TEXT_BACKEND)
    val activeBackendName = providers.find { it.id == activeBackendId }?.name ?: "Select Provider"

    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val appVersionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val json = repository.exportSettings(appVersionName)
                    context.contentResolver.openOutputStream(it)?.use { output ->
                        output.write(json.toByteArray())
                    }
                    Toast.makeText(context, "Settings exported successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
            showImportConfirmDialog = true
        }
    }

    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { 
                showImportConfirmDialog = false
                pendingImportUri = null
            },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Import Settings") },
            text = { Text("This will override your current settings. Are you sure you want to continue?") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirmDialog = false
                    pendingImportUri?.let { uri ->
                        scope.launch {
                            try {
                                val json = context.contentResolver.openInputStream(uri)?.use { input ->
                                    input.bufferedReader().readText()
                                } ?: throw Exception("Could not read file")
                                when (val result = repository.importSettings(json)) {
                                    is ImportResult.Success -> {
                                        Toast.makeText(context, "Settings imported successfully", Toast.LENGTH_SHORT).show()
                                    }
                                    is ImportResult.Error -> {
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    pendingImportUri = null
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImportConfirmDialog = false 
                    pendingImportUri = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Info, contentDescription = "Help")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SuggestedCard()
            }

            item {
                SettingsGroup(title = "Configuration") {
                    SettingsItem(
                        icon = Icons.Default.Mic,
                        title = "Transcription Backend",
                        subtitle = activeBackendName,
                        onClick = { navController.navigate(SettingsScreen.Backend.route) }
                    )
                    SettingsItem(
                        icon = Icons.Default.SmartToy,
                        title = "Smart Fix (LLM)",
                        subtitle = if (settingsState?.get(SMART_FIX_ENABLED) == true) "Enabled" else "Disabled",
                        onClick = { navController.navigate(SettingsScreen.PostProcessing.route) }
                    )
                    SettingsItem(
                        icon = Icons.Default.Keyboard,
                        title = "Keyboard Behavior",
                        subtitle = "Haptics, Auto-start, etc.",
                        onClick = { navController.navigate(SettingsScreen.Keyboard.route) }
                    )
                }
            }

            item {
                SettingsGroup(title = "Data") {
                    SettingsItem(
                        icon = Icons.Default.Upload,
                        title = "Export Settings",
                        subtitle = "Save settings to file",
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            exportLauncher.launch("whisper_settings_${timestamp}.json")
                        }
                    )
                    SettingsItem(
                        icon = Icons.Default.Download,
                        title = "Import Settings",
                        subtitle = "Restore from file",
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        }
                    )
                }
            }

            item {
                UpdateSettingsSection(dataStore, showUpdate)
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VoiceWaveform(
                        modifier = Modifier.size(120.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsEditDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendSettingsScreen(dataStore: DataStore<Preferences>, navController: NavHostController) {
    val repository = remember { SettingsRepository(dataStore) }
    val providers by repository.providers.collectAsState(initial = emptyList())
    val settingsState by dataStore.data.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val activeBackendId = settingsState?.get(SPEECH_TO_TEXT_BACKEND)
    
    var showAddOptions by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Providers") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddOptions = true }) {
                Icon(Icons.Default.Add, "Add Provider")
            }
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        if (providers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No providers added yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(providers.size) { index ->
                    val provider = providers[index]
                    ProviderCard(
                        provider = provider,
                        isActive = provider.id == activeBackendId,
                        onSelectActive = {
                            scope.launch {
                                dataStore.edit { it[SPEECH_TO_TEXT_BACKEND] = provider.id }
                            }
                        },
                        onClick = {
                            navController.navigate(SettingsScreen.ProviderEdit.createRoute(provider.id))
                        },
                        onDelete = {
                            scope.launch {
                                val currentList = providers.toMutableList()
                                currentList.removeAt(index)
                                repository.saveProviders(currentList)
                                if (provider.id == activeBackendId) {
                                    dataStore.edit { it.remove(SPEECH_TO_TEXT_BACKEND) }
                                }
                            }
                        }
                    )
                }
            }
        }
        
        if (showAddOptions) {
            AlertDialog(
                onDismissRequest = { showAddOptions = false },
                title = { Text("Add Provider") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListItem(
                            headlineContent = { Text("Custom Provider") },
                            leadingContent = { Icon(Icons.Default.Edit, null) },
                            modifier = Modifier.clickable {
                                showAddOptions = false
                                navController.navigate(SettingsScreen.ProviderEdit.createRoute(null))
                            }
                        )
                        HorizontalDivider()
                        Text("Presets", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        Presets.AllPresets.forEach { preset ->
                            ListItem(
                                headlineContent = { Text(preset.name) },
                                supportingContent = { Text(preset.type.toString()) },
                                leadingContent = { Icon(Icons.Default.Bookmarks, null) },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        val newProvider = preset.copy(id = UUID.randomUUID().toString())
                                        val currentList = providers.toMutableList()
                                        currentList.add(newProvider)
                                        repository.saveProviders(currentList)
                                        showAddOptions = false
                                        // Optional: Navigate to edit screen immediately to let user enter API key
                                        navController.navigate(SettingsScreen.ProviderEdit.createRoute(newProvider.id))
                                    }
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddOptions = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun ProviderCard(provider: ServiceProvider, isActive: Boolean, onSelectActive: () -> Unit, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isActive, onClick = onSelectActive)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Dns, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(provider.type.toString(), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
                Icon(Icons.Default.ChevronRight, null)
            }
            if (provider.models.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${provider.models.size} Models Configured", 
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(dataStore: DataStore<Preferences>, navController: NavHostController, providerId: String?) {
    val repository = remember { SettingsRepository(dataStore) }
    val providers by repository.providers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    // State
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ProviderType.OPENAI) }
    var endpoint by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(listOf<ModelConfig>()) }
    var temperature by remember { mutableStateOf(0.0f) }
    var prompt by remember { mutableStateOf("") }
    var languageCode by remember { mutableStateOf("auto") }
    var timeout by remember { mutableStateOf(10000) }
    
    // Thinking State
    var thinkingEnabled by remember { mutableStateOf(false) }
    var thinkingType by remember { mutableStateOf(ThinkingType.LEVEL) }
    var thinkingBudget by remember { mutableStateOf(4096) }
    var thinkingLevel by remember { mutableStateOf("medium") }
    
    // Type Dropdown State
    var typeExpanded by remember { mutableStateOf(false) }
    // Language Dropdown State
    var languageExpanded by remember { mutableStateOf(false) }

    val commonLanguages = listOf(
        "auto" to "Auto Detect",
        "en" to "English",
        "ar" to "Arabic",
        "zh" to "Chinese",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "ja" to "Japanese",
        "ko" to "Korean",
        "ru" to "Russian"
    )

    // Initialize state when providers load
    LaunchedEffect(providers, providerId) {
        if (providerId != null && providerId.isNotEmpty()) {
            val provider = providers.find { it.id == providerId }
            if (provider != null) {
                name = provider.name
                type = provider.type
                endpoint = provider.endpoint
                apiKey = provider.apiKey
                models = provider.models
                temperature = provider.temperature
                prompt = provider.prompt
                languageCode = provider.languageCode
                timeout = provider.timeout
                thinkingEnabled = provider.thinkingEnabled
                thinkingType = provider.thinkingType
                thinkingBudget = provider.thinkingBudget
                thinkingLevel = provider.thinkingLevel
            }
        } else if (name.isEmpty()) {
            name = "New Provider"
        }
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(if (providerId == null) "New Provider" else "Edit Provider") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val newProvider = ServiceProvider(
                            id = providerId ?: UUID.randomUUID().toString(),
                            name = name,
                            type = type,
                            endpoint = endpoint,
                             apiKey = apiKey,
                             models = models,
                             temperature = temperature,
                             prompt = prompt,
                             languageCode = languageCode,
                             timeout = timeout,
                             thinkingEnabled = thinkingEnabled,
                             thinkingType = thinkingType,
                             thinkingBudget = thinkingBudget,
                             thinkingLevel = thinkingLevel
                         )
                        scope.launch {
                            val currentList = providers.toMutableList()
                            val index = currentList.indexOfFirst { it.id == newProvider.id }
                            if (index >= 0) {
                                currentList[index] = newProvider
                            } else {
                                currentList.add(newProvider)
                            }
                            repository.saveProviders(currentList)
                            navController.popBackStack()
                        }
                    }) {
                        Text("Save")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Provider Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Provider Type Dropdown
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = !typeExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = type.toString(),
                    onValueChange = {},
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    ProviderType.values().forEach { providerType ->
                        DropdownMenuItem(
                            text = { Text(providerType.toString()) },
                            onClick = {
                                type = providerType
                                typeExpanded = false
                            }
                        )
                    }
                }
            }
            
            // Language Dropdown
            ExposedDropdownMenuBox(
                expanded = languageExpanded,
                onExpandedChange = { languageExpanded = !languageExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                val languageLabel = commonLanguages.find { it.first == languageCode }?.second ?: languageCode
                OutlinedTextField(
                    readOnly = true,
                    value = languageLabel,
                    onValueChange = {},
                    label = { Text("Language (for Transcription)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = languageExpanded,
                    onDismissRequest = { languageExpanded = false }
                ) {
                    commonLanguages.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                languageCode = code
                                languageExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("Endpoint URL") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = timeout.toString(),
                onValueChange = { timeout = it.toIntOrNull() ?: 10000 },
                label = { Text("Timeout (ms)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Temperature: ${"%.2f".format(temperature)}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..1f,
                    steps = 20,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("System Prompt / Context") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Thinking Configuration", style = MaterialTheme.typography.titleMedium)
            
            SettingsToggle(
                icon = Icons.Default.Psychology,
                title = "Enable Thinking (Global Toggle)",
                checked = thinkingEnabled,
                onCheckedChange = { thinkingEnabled = it }
            )
            
            AnimatedVisibility(visible = thinkingEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Thinking Mode", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = thinkingType == ThinkingType.LEVEL,
                            onClick = { thinkingType = ThinkingType.LEVEL },
                            label = { Text("Level") }
                        )
                        FilterChip(
                            selected = thinkingType == ThinkingType.BUDGET,
                            onClick = { thinkingType = ThinkingType.BUDGET },
                            label = { Text("Budget (Tokens)") }
                        )
                    }
                    
                    if (thinkingType == ThinkingType.LEVEL) {
                        var levelExpanded by remember { mutableStateOf(false) }
                        val levels = listOf("minimal", "low", "medium", "high", "xhigh")
                        ExposedDropdownMenuBox(
                            expanded = levelExpanded,
                            onExpandedChange = { levelExpanded = !levelExpanded }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = thinkingLevel,
                                onValueChange = {},
                                label = { Text("Thinking Level") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = levelExpanded,
                                onDismissRequest = { levelExpanded = false }
                            ) {
                                levels.forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level) },
                                        onClick = {
                                            thinkingLevel = level
                                            levelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Column {
                            Text("Budget: $thinkingBudget tokens", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = thinkingBudget.toFloat(),
                                onValueChange = { thinkingBudget = it.toInt() },
                                valueRange = 1024f..32768f,
                                steps = 31,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider()
            
            Text("Models", style = MaterialTheme.typography.titleMedium)
            
            models.forEachIndexed { index, model ->
                 ModelEditCard(
                     model = model, 
                     onUpdate = { updated -> 
                         val newModels = models.toMutableList()
                         newModels[index] = updated
                         models = newModels
                     },
                     onDelete = {
                         val newModels = models.toMutableList()
                         newModels.removeAt(index)
                         models = newModels
                     }
                 )
            }
            
            Button(
                onClick = { 
                    val newModels = models.toMutableList()
                    newModels.add(ModelConfig(UUID.randomUUID().toString(), "New Model"))
                    models = newModels
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Model")
            }
            
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
fun ModelEditCard(model: ModelConfig, onUpdate: (ModelConfig) -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = model.name,
                    onValueChange = { onUpdate(model.copy(name = it)) },
                    label = { Text("Model Name") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model.id,
                onValueChange = { onUpdate(model.copy(id = it)) },
                label = { Text("Model ID (API)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = model.isThinking,
                    onCheckedChange = { onUpdate(model.copy(isThinking = it)) }
                )
                Text("Thinking Model")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFixSettingsScreen(dataStore: DataStore<Preferences>, navController: NavHostController) {
    val settingsState by dataStore.data.collectAsState(initial = null)
    val repository = remember { SettingsRepository(dataStore) }
    val providers by repository.providers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    val selectedProviderId = settingsState?.get(SMART_FIX_BACKEND) ?: ""
    val selectedModelId = settingsState?.get(SMART_FIX_MODEL) ?: ""
    val selectedProvider = providers.find { it.id == selectedProviderId } ?: providers.firstOrNull()

    // State for Prompt Edit
    var showPromptDialog by remember { mutableStateOf(false) }

    // Dropdown States
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Smart Fix") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(16.dp))
            SettingsGroup(title = "Behavior") {
                SettingsToggle(
                    icon = Icons.Default.AutoFixHigh,
                    title = "Enable Smart Fix",
                    checked = settingsState?.get(SMART_FIX_ENABLED) ?: false,
                    onCheckedChange = { scope.launch { dataStore.edit { s -> s[SMART_FIX_ENABLED] = it } } }
                )
                SettingsToggle(
                    icon = Icons.Default.History,
                    title = "Use Context",
                    checked = settingsState?.get(USE_CONTEXT) ?: false,
                    onCheckedChange = { scope.launch { dataStore.edit { s -> s[USE_CONTEXT] = it } } }
                )
                SettingsItem(
                    icon = Icons.Default.EditNote,
                    title = "Custom Prompt",
                    subtitle = (settingsState?.get(SMART_FIX_PROMPT) ?: "Default").take(20) + "...",
                    onClick = { showPromptDialog = true }
                )
            }

            SettingsGroup(title = "LLM Configuration") {
                // Provider Selection
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = !providerExpanded },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = selectedProvider?.name ?: "Select Provider",
                        onValueChange = {},
                        label = { Text("Backend Provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        if (providers.isEmpty()) {
                            DropdownMenuItem(text = { Text("No Providers Configured") }, onClick = { providerExpanded = false })
                        }
                        providers.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.name) },
                                onClick = {
                                    scope.launch {
                                        dataStore.edit { 
                                            it[SMART_FIX_BACKEND] = provider.id 
                                            // Reset model when provider changes
                                            it[SMART_FIX_MODEL] = provider.models.firstOrNull()?.id ?: ""
                                        }
                                    }
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                // Model Selection
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { if (selectedProvider != null) modelExpanded = !modelExpanded },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val currentModelName = selectedProvider?.models?.find { it.id == selectedModelId }?.name ?: selectedModelId
                    OutlinedTextField(
                        readOnly = true,
                        enabled = selectedProvider != null,
                        value = if (currentModelName.isNotEmpty()) currentModelName else "Select Model",
                        onValueChange = {},
                        label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        selectedProvider?.models?.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name) },
                                onClick = {
                                    scope.launch {
                                        dataStore.edit { it[SMART_FIX_MODEL] = model.id }
                                    }
                                    modelExpanded = false
                                }
                            )
                        }
                        if (selectedProvider?.models.isNullOrEmpty()) {
                             DropdownMenuItem(text = { Text("No Models") }, onClick = { modelExpanded = false })
                        }
                    }
                }
            }
        }

        if (showPromptDialog) {
            SettingsEditDialog(
                title = "Custom System Prompt",
                initialValue = settingsState?.get(SMART_FIX_PROMPT) ?: "",
                onDismiss = { showPromptDialog = false },
                onConfirm = { newValue ->
                    scope.launch {
                        dataStore.edit { it[SMART_FIX_PROMPT] = newValue }
                        showPromptDialog = false
                    }
                }
            )
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSettingsScreen(dataStore: DataStore<Preferences>, navController: NavHostController) {
    val settingsState by dataStore.data.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Keyboard") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(16.dp))
            SettingsGroup(title = "Automation") {
                SettingsToggle(
                    icon = Icons.Default.Mic,
                    title = "Auto Start Recording",
                    checked = settingsState?.get(AUTO_RECORDING_START) ?: true,
                    onCheckedChange = { scope.launch { dataStore.edit { s -> s[AUTO_RECORDING_START] = it } } }
                )
                SettingsToggle(
                    icon = Icons.Default.SwapHoriz,
                    title = "Auto Switch Back",
                    checked = settingsState?.get(AUTO_SWITCH_BACK) ?: false,
                    onCheckedChange = { scope.launch { dataStore.edit { s -> s[AUTO_SWITCH_BACK] = it } } }
                )
                SettingsToggle(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = "Auto Transcribe",
                    checked = settingsState?.get(AUTO_TRANSCRIBE_ON_PAUSE) ?: true,
                    onCheckedChange = { scope.launch { dataStore.edit { s -> s[AUTO_TRANSCRIBE_ON_PAUSE] = it } } }
                )
            }

            SettingsGroup(title = "UI Extras") {
                SettingsToggle(
                    icon = Icons.Default.SpaceBar,
                    title = "Add Trailing Space",
                    checked = settingsState?.get(ADD_TRAILING_SPACE) ?: false,
                    onCheckedChange = { scope.launch { dataStore.edit { s -> s[ADD_TRAILING_SPACE] = it } } }
                )
                SettingsToggle(
                    icon = Icons.Default.QuestionMark,
                    title = "Cancel Confirmation",
                    checked = settingsState?.get(CANCEL_CONFIRMATION) ?: true,
                    onCheckedChange = { scope.launch { dataStore.edit { s -> s[CANCEL_CONFIRMATION] = it } } }
                )
            }

            SettingsGroup(title = "Feedback") {
                SettingsToggle(
                    icon = Icons.Default.Vibration,
                    title = "Haptic Feedback",
                    checked = settingsState?.get(HAPTIC_FEEDBACK_ENABLED) ?: true,
                    onCheckedChange = { scope.launch { dataStore.edit { s -> s[HAPTIC_FEEDBACK_ENABLED] = it } } }
                )
                SettingsToggle(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Sound Effects",
                    checked = settingsState?.get(SOUND_EFFECTS_ENABLED) ?: true,
                    onCheckedChange = { scope.launch { dataStore.edit { s -> s[SOUND_EFFECTS_ENABLED] = it } } }
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
fun SuggestedCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission State
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasMicPermission = isGranted }
    )

    // IME State
    fun checkImeEnabled(): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledImes = imm.enabledInputMethodList
        return enabledImes.any { it.packageName == context.packageName }
    }

    var isImeEnabled by remember { mutableStateOf(checkImeEnabled()) }

    // Re-check on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                isImeEnabled = checkImeEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f).clickable {
                if (!hasMicPermission) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (hasMicPermission) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Icon(
                    Icons.Default.Mic,
                    null,
                    tint = if (hasMicPermission) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Microphone",
                    fontWeight = FontWeight.Bold,
                    color = if (hasMicPermission) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    if (hasMicPermission) "Permission Granted" else "Tap to Grant",
                    fontSize = 12.sp,
                    color = if (hasMicPermission) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        Card(
            modifier = Modifier.weight(1f).clickable {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isImeEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Icon(
                    Icons.Default.Keyboard,
                    null,
                    tint = if (isImeEnabled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Keyboard",
                    fontWeight = FontWeight.Bold,
                    color = if (isImeEnabled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (isImeEnabled) "Active" else "Tap to Enable",
                    fontSize = 12.sp,
                    color = if (isImeEnabled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsToggle(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettingsSection(dataStore: DataStore<Preferences>, showUpdate: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsState by dataStore.data.collectAsState(initial = null)
    val lifecycleOwner = LocalLifecycleOwner.current

    val updateManager = remember { UpdateManager(context) }
    val checkResult by updateManager.checkResult.collectAsState()
    val downloadState by updateManager.downloadState.collectAsState()
    val isChecking by updateManager.isChecking.collectAsState()
    val installResult by updateManager.installResult.collectAsState()

    LaunchedEffect(Unit) {
        updateManager.observeExistingDownload(scope)
        if (showUpdate) {
            val channel = UpdateChannel.fromString(currentChannel)
            updateManager.checkForUpdate(scope, channel)
        }
    }

    val currentChannel = settingsState?.get(UPDATE_CHANNEL) ?: "stable"
    var channelExpanded by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && showPermissionDialog) {
                if (updateManager.canInstallPackages()) {
                    showPermissionDialog = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            updateManager.close()
        }
    }

    LaunchedEffect(installResult) {
        if (installResult is InstallResult.PermissionRequired) {
            showPermissionDialog = true
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = { Icon(Icons.Default.Security, null) },
            title = { Text("Permission Required") },
            text = { Text("To install updates, please allow installing apps from this source.") },
            confirmButton = {
                TextButton(onClick = {
                    updateManager.openInstallPermissionSettings()
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    SettingsGroup(title = context.getString(R.string.settings_check_for_updates)) {
        ExposedDropdownMenuBox(
            expanded = channelExpanded,
            onExpandedChange = { channelExpanded = !channelExpanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val channelLabel = if (currentChannel == "nightly") {
                context.getString(R.string.settings_update_channel_nightly)
            } else {
                context.getString(R.string.settings_update_channel_stable)
            }
            OutlinedTextField(
                readOnly = true,
                value = channelLabel,
                onValueChange = {},
                label = { Text(context.getString(R.string.settings_update_channel)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = channelExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = channelExpanded,
                onDismissRequest = { channelExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(context.getString(R.string.settings_update_channel_stable)) },
                    onClick = {
                        scope.launch { dataStore.edit { it[UPDATE_CHANNEL] = "stable" } }
                        channelExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(context.getString(R.string.settings_update_channel_nightly)) },
                    onClick = {
                        scope.launch { dataStore.edit { it[UPDATE_CHANNEL] = "nightly" } }
                        channelExpanded = false
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    val channel = UpdateChannel.fromString(currentChannel)
                    updateManager.checkForUpdate(scope, channel)
                },
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.update_checking))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.settings_check_for_updates))
                }
            }
        }

        when (val result = checkResult) {
            is UpdateCheckResult.Available -> {
                UpdateAvailableCard(
                    release = result.release,
                    downloadState = downloadState,
                    onDownload = { updateManager.downloadUpdate(scope) },
                    onInstall = { path -> updateManager.installUpdate(path) },
                    onCancel = { updateManager.cancelDownload(scope) },
                    modifier = Modifier.padding(16.dp)
                )
            }
            is UpdateCheckResult.UpToDate -> {
                Text(
                    text = context.getString(R.string.update_up_to_date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            is UpdateCheckResult.Error -> {
                Text(
                    text = context.getString(R.string.update_failed, result.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            null -> {}
        }
    }
}
