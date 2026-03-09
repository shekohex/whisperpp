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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.datastore.preferences.core.Preferences as PreferencesCore
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
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.github.shekohex.whisperpp.updater.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

sealed class SettingsScreen(val route: String) {
    companion object {
        fun resolveStartRoute(route: String?): String {
            val normalized = route?.trim().orEmpty()
            if (normalized.isBlank()) {
                return Main.route
            }
            return if (isSupportedStartRoute(normalized)) normalized else Main.route
        }

        private fun isSupportedStartRoute(route: String): Boolean {
            return when {
                route == Main.route -> true
                route == Backend.route -> true
                route == PostProcessing.route -> true
                route == Keyboard.route -> true
                route == PrivacySafety.route -> true
                route == ProviderSelections.route -> true
                route == LanguageDefaults.route -> true
                route == Presets.route -> true
                route == PromptsProfiles.route -> true
                route == AppMappings.route -> true
                route.startsWith("app_mapping_detail?") -> true
                route.startsWith("prompt_profile_edit?") -> true
                route.startsWith("provider_edit?") -> true
                else -> false
            }
        }
    }

    object Main : SettingsScreen("main")
    object Backend : SettingsScreen("backend")
    object PostProcessing : SettingsScreen("post_processing")
    object Keyboard : SettingsScreen("keyboard")
    object PrivacySafety : SettingsScreen("privacy_safety")
    object ProviderSelections : SettingsScreen("provider_selections")
    object LanguageDefaults : SettingsScreen("language_defaults")
    object Presets : SettingsScreen("presets")
    object PromptsProfiles : SettingsScreen("prompts_profiles")
    object AppMappings : SettingsScreen("app_mappings")
    object AppMappingDetail : SettingsScreen("app_mapping_detail?packageName={packageName}") {
        fun createRoute(packageName: String): String {
            val encodedPackageName = Uri.encode(packageName)
            return "app_mapping_detail?packageName=$encodedPackageName"
        }
    }
    object PromptProfileEdit : SettingsScreen("prompt_profile_edit?id={id}") {
        fun createRoute(id: String? = null): String {
            val encodedId = Uri.encode(id ?: "")
            return "prompt_profile_edit?id=$encodedId"
        }
    }
    object ProviderEdit : SettingsScreen("provider_edit?id={id}&cloneFromId={cloneFromId}") {
        fun createRoute(id: String? = null, cloneFromId: String? = null): String {
            val encodedId = Uri.encode(id ?: "")
            val encodedCloneFromId = Uri.encode(cloneFromId ?: "")
            return "provider_edit?id=$encodedId&cloneFromId=$encodedCloneFromId"
        }
    }
}

@Composable
fun SettingsNavigation(
    dataStore: DataStore<Preferences>,
    showUpdate: Boolean = false,
    startRoute: String = SettingsScreen.Main.route,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val repository = remember { SettingsRepository(dataStore) }
    var migrationReady by remember { mutableStateOf(false) }
    val startDestination = SettingsScreen.resolveStartRoute(startRoute)

    LaunchedEffect(Unit) {
        repository.migrateProviderSchemaV2IfNeeded(context)
        repository.migrateProviderSelectionsV2IfNeeded()
        repository.migratePromptsProfilesSchemaV1IfNeeded()
        migrationReady = true
    }

    if (!migrationReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(SettingsScreen.Main.route) {
            SettingsHomeScreen(dataStore, navController, showUpdate)
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
        composable(SettingsScreen.PrivacySafety.route) {
            PrivacySafetyScreen(dataStore, navController)
        }
        composable(SettingsScreen.ProviderSelections.route) {
            ProviderSelectionsScreen(dataStore, navController)
        }
        composable(SettingsScreen.LanguageDefaults.route) {
            LanguageDefaultsScreen(dataStore, navController)
        }
        composable(SettingsScreen.Presets.route) {
            PresetsSettingsScreen(dataStore, navController)
        }
        composable(SettingsScreen.PromptsProfiles.route) {
            PromptsProfilesScreen(dataStore, navController)
        }
        composable(SettingsScreen.AppMappings.route) {
            AppMappingsScreen(dataStore, navController)
        }
        composable(
            route = SettingsScreen.AppMappingDetail.route,
            arguments = listOf(
                navArgument("packageName") { nullable = true },
            )
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName")?.trim().orEmpty()
            if (packageName.isBlank()) {
                navController.popBackStack()
                return@composable
            }
            AppMappingDetailScreen(dataStore, navController, packageName)
        }
        composable(
            route = SettingsScreen.PromptProfileEdit.route,
            arguments = listOf(
                navArgument("id") { nullable = true },
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.trim()?.takeIf { it.isNotBlank() }
            PromptProfileEditScreen(dataStore, navController, id)
        }
        composable(
            route = SettingsScreen.ProviderEdit.route,
            arguments = listOf(
                navArgument("id") { nullable = true },
                navArgument("cloneFromId") { nullable = true }
            )
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("id")
            val cloneFromId = backStackEntry.arguments?.getString("cloneFromId")
            ProviderEditScreen(dataStore, navController, providerId, cloneFromId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptsProfilesScreen(dataStore: DataStore<Preferences>, navController: NavHostController) {
    val repository = remember { SettingsRepository(dataStore) }
    val basePrompt by repository.globalBasePrompt.collectAsState(initial = "")
    val promptProfiles by repository.promptProfiles.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var draftPrompt by remember { mutableStateOf("") }
    var userEditing by remember { mutableStateOf(false) }

    LaunchedEffect(basePrompt) {
        if (!userEditing) {
            draftPrompt = basePrompt
        }
    }

    val hasChanges = remember(basePrompt, draftPrompt) { draftPrompt != basePrompt }

    var pendingDeleteProfile by remember { mutableStateOf<PromptProfile?>(null) }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Prompts & profiles") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.PromptsProfiles.route)
                    TextButton(
                        onClick = {
                            scope.launch {
                                repository.saveGlobalBasePrompt(draftPrompt)
                                userEditing = false
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = hasChanges,
                    ) {
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Tap Save to apply base prompt edits. Profiles and app mappings update when you edit their entries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsGroup(title = "Global base prompt") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = draftPrompt,
                        onValueChange = {
                            userEditing = true
                            draftPrompt = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 12,
                        label = { Text("Base prompt") },
                        placeholder = { Text("e.g. Improve clarity, fix punctuation, keep tone.") },
                    )
                    Text(
                        text = "Used as the foundation for enhancement prompting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsGroup(title = "Prompt profiles") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Add profile") },
                        supportingContent = { Text("Name + optional prompt append") },
                        leadingContent = { Icon(Icons.Default.Add, null) },
                        modifier = Modifier.clickable {
                            navController.navigate(SettingsScreen.PromptProfileEdit.createRoute(null))
                        }
                    )
                    HorizontalDivider()

                    if (promptProfiles.isEmpty()) {
                        ListItem(
                            headlineContent = { Text("No profiles yet") },
                            supportingContent = { Text("Create one to reuse prompt append text across apps.") },
                            leadingContent = { Icon(Icons.Default.Bookmarks, null) },
                        )
                    } else {
                        val sortedProfiles = remember(promptProfiles) {
                            promptProfiles.sortedBy { it.name.lowercase(Locale.getDefault()) }
                        }
                        sortedProfiles.forEachIndexed { index, profile ->
                            ListItem(
                                headlineContent = { Text(profile.name) },
                                supportingContent = {
                                    val preview = profile.promptAppend.ifBlank { "No append" }
                                    Text(preview.take(80))
                                },
                                leadingContent = { Icon(Icons.Default.Bookmarks, null) },
                                trailingContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(onClick = {
                                            navController.navigate(SettingsScreen.PromptProfileEdit.createRoute(profile.id))
                                        }) {
                                            Icon(Icons.Default.Edit, "Edit")
                                        }
                                        IconButton(onClick = { pendingDeleteProfile = profile }) {
                                            Icon(Icons.Default.Delete, "Delete")
                                        }
                                    }
                                },
                                modifier = Modifier.clickable {
                                    navController.navigate(SettingsScreen.PromptProfileEdit.createRoute(profile.id))
                                }
                            )
                            if (index != sortedProfiles.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            SettingsGroup(title = "Per-app") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("App mappings") },
                        supportingContent = { Text("Map apps to a prompt profile") },
                        leadingContent = { Icon(Icons.Default.Apps, null) },
                        modifier = Modifier.clickable {
                            navController.navigate(SettingsScreen.AppMappings.route)
                        }
                    )
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (pendingDeleteProfile != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteProfile = null },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Delete profile?") },
            text = {
                val name = pendingDeleteProfile?.name.orEmpty()
                Text("Delete \"$name\"? App mappings that reference it will be kept and will fallback until updated.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = pendingDeleteProfile?.id.orEmpty()
                    pendingDeleteProfile = null
                    scope.launch {
                        repository.deletePromptProfile(id)
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProfile = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptProfileEditScreen(
    dataStore: DataStore<Preferences>,
    navController: NavHostController,
    profileId: String?,
) {
    val repository = remember { SettingsRepository(dataStore) }
    val profiles by repository.promptProfiles.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isNew = profileId.isNullOrBlank()
    val generatedId = remember { UUID.randomUUID().toString() }
    val effectiveId = if (isNew) generatedId else profileId.orEmpty()

    val existing = remember(profiles, profileId) { profiles.firstOrNull { it.id == profileId } }

    var name by remember { mutableStateOf("") }
    var append by remember { mutableStateOf("") }
    var initializedFromExisting by remember { mutableStateOf(false) }

    LaunchedEffect(existing?.id, isNew) {
        if (isNew) {
            return@LaunchedEffect
        }
        if (!initializedFromExisting) {
            if (existing == null) {
                Toast.makeText(context, "Profile not found", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
                return@LaunchedEffect
            }
            name = existing.name
            append = existing.promptAppend
            initializedFromExisting = true
        }
    }

    val canSave = remember(name) { name.trim().isNotBlank() }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(if (isNew) "New profile" else "Edit profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.PromptsProfiles.route)
                    TextButton(
                        onClick = {
                            scope.launch {
                                repository.upsertPromptProfile(
                                    PromptProfile(
                                        id = effectiveId,
                                        name = name,
                                        promptAppend = append,
                                    )
                                )
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        },
                        enabled = canSave,
                    ) {
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Save stores this profile immediately so it can be reused in app mappings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsGroup(title = "Profile") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Name") },
                    )
                    OutlinedTextField(
                        value = append,
                        onValueChange = { append = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 10,
                        label = { Text("Prompt append (optional)") },
                    )
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

private data class AppMappingPickerApp(
    val packageName: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMappingsScreen(dataStore: DataStore<Preferences>, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SettingsRepository(dataStore) }
    val appMappings by repository.appPromptMappings.collectAsState(initial = emptyList())
    val promptProfiles by repository.promptProfiles.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var manualPackageName by remember { mutableStateOf("") }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }

    var showBulkAssignDialog by remember { mutableStateOf(false) }
    var bulkSelectedProfileId by remember { mutableStateOf<String?>(null) }

    val launcherApps by produceState(initialValue = emptyList<AppMappingPickerApp>(), context) {
        value = withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(queryIntent, 0)
                .mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo?.packageName?.trim().orEmpty()
                    if (packageName.isEmpty()) {
                        null
                    } else {
                        val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                        AppMappingPickerApp(
                            packageName = packageName,
                            label = label.ifEmpty { packageName },
                        )
                    }
                }
                .distinctBy { it.packageName }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
    }

    val visibleApps = remember(launcherApps, appMappings, searchQuery) {
        val installed = launcherApps.associateBy { it.packageName }
        val mappedOnly = appMappings.asSequence()
            .map { it.packageName.trim() }
            .filter { it.isNotBlank() && !installed.containsKey(it) }
            .distinct()
            .map { pkg -> AppMappingPickerApp(packageName = pkg, label = pkg) }
            .toList()

        val merged = (launcherApps + mappedOnly)
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        val filter = searchQuery.trim().lowercase(Locale.US)
        if (filter.isBlank()) {
            merged
        } else {
            merged.filter {
                it.label.lowercase(Locale.US).contains(filter) ||
                    it.packageName.lowercase(Locale.US).contains(filter)
            }
        }
    }

    fun toggleSelection(packageName: String) {
        selectedPackages = if (selectedPackages.contains(packageName)) {
            selectedPackages - packageName
        } else {
            selectedPackages + packageName
        }
    }

    fun assignProfileToPackages(profileId: String?) {
        val normalizedProfileId = profileId?.trim()?.takeIf { it.isNotBlank() }
        scope.launch {
            val byPkg = appMappings.associateBy { it.packageName }.toMutableMap()
            selectedPackages.forEach { pkg ->
                val existing = byPkg[pkg]
                val updated = (existing ?: AppPromptMapping(packageName = pkg)).copy(profileId = normalizedProfileId)
                byPkg[pkg] = updated
            }
            repository.saveAppPromptMappings(byPkg.values.toList())
            Toast.makeText(context, "Assigned", Toast.LENGTH_SHORT).show()
            showBulkAssignDialog = false
            selectionMode = false
            selectedPackages = emptySet()
        }
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(if (selectionMode) "${selectedPackages.size} selected" else "App mappings")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.AppMappings.route)
                    if (selectionMode) {
                        IconButton(
                            onClick = {
                                bulkSelectedProfileId = null
                                showBulkAssignDialog = true
                            },
                            enabled = selectedPackages.isNotEmpty(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Assign")
                        }
                        IconButton(onClick = {
                            selectionMode = false
                            selectedPackages = emptySet()
                        }) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    } else {
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(Icons.Default.DoneAll, "Multi-select")
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsGroup(title = "Search") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search installed apps") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = manualPackageName,
                        onValueChange = { manualPackageName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Add by package name") },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            val normalized = manualPackageName.trim()
                            if (normalized.isNotBlank()) {
                                scope.launch {
                                    repository.upsertAppPromptMapping(AppPromptMapping(packageName = normalized))
                                    manualPackageName = ""
                                    navController.navigate(SettingsScreen.AppMappingDetail.createRoute(normalized))
                                }
                            }
                        },
                        enabled = manualPackageName.trim().isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add")
                    }
                }
            }

            SettingsGroup(title = "Installed apps") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (visibleApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No matching apps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val profileById = remember(promptProfiles) { promptProfiles.associateBy { it.id } }
                        val mappingByPackage = remember(appMappings) { appMappings.associateBy { it.packageName } }

                        visibleApps.forEachIndexed { index, app ->
                            val mapping = mappingByPackage[app.packageName]
                            val mappedProfileId = mapping?.profileId?.trim().orEmpty()
                            val resolvedProfile = mappedProfileId.takeIf { it.isNotBlank() }?.let { id -> profileById[id] }
                            val missingProfile = mappedProfileId.isNotBlank() && resolvedProfile == null
                            val profileName = when {
                                mappedProfileId.isBlank() -> "None"
                                resolvedProfile != null -> resolvedProfile.name
                                else -> "Missing"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectionMode) {
                                            toggleSelection(app.packageName)
                                        } else {
                                            navController.navigate(SettingsScreen.AppMappingDetail.createRoute(app.packageName))
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (selectionMode) {
                                    Checkbox(
                                        checked = selectedPackages.contains(app.packageName),
                                        onCheckedChange = { toggleSelection(app.packageName) },
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = profileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (missingProfile) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (missingProfile) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (index < visibleApps.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showBulkAssignDialog) {
        val sortedProfiles = remember(promptProfiles) {
            promptProfiles.sortedBy { it.name.lowercase(Locale.getDefault()) }
        }
        AlertDialog(
            onDismissRequest = { showBulkAssignDialog = false },
            title = { Text("Assign profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListItem(
                        headlineContent = { Text("None") },
                        leadingContent = {
                            RadioButton(
                                selected = bulkSelectedProfileId == null,
                                onClick = { bulkSelectedProfileId = null },
                            )
                        },
                        modifier = Modifier.clickable { bulkSelectedProfileId = null },
                    )
                    HorizontalDivider()
                    sortedProfiles.forEach { profile ->
                        ListItem(
                            headlineContent = { Text(profile.name) },
                            supportingContent = {
                                val preview = profile.promptAppend.ifBlank { "No append" }
                                Text(preview.take(80))
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = bulkSelectedProfileId == profile.id,
                                    onClick = { bulkSelectedProfileId = profile.id },
                                )
                            },
                            modifier = Modifier.clickable { bulkSelectedProfileId = profile.id },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { assignProfileToPackages(bulkSelectedProfileId) }) {
                    Text("Assign")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkAssignDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMappingDetailScreen(
    dataStore: DataStore<Preferences>,
    navController: NavHostController,
    packageName: String,
) {
    val repository = remember { SettingsRepository(dataStore) }
    val providers by repository.providers.collectAsState(initial = emptyList())
    val appMappings by repository.appPromptMappings.collectAsState(initial = emptyList())
    val promptProfiles by repository.promptProfiles.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val existing = remember(appMappings, packageName) {
        appMappings.firstOrNull { it.packageName == packageName }
    }

    val initial = remember(existing, packageName) { existing ?: AppPromptMapping(packageName = packageName) }

    var profileId by remember { mutableStateOf<String?>(null) }
    var appendMode by remember { mutableStateOf(AppendMode.APPEND) }
    var appPromptAppend by remember { mutableStateOf("") }

    var sttOverrideProviderId by remember { mutableStateOf("") }
    var sttOverrideModelId by remember { mutableStateOf("") }
    var textOverrideProviderId by remember { mutableStateOf("") }
    var textOverrideModelId by remember { mutableStateOf("") }

    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(initial.packageName) {
        if (!initialized) {
            profileId = initial.profileId
            appendMode = initial.appendMode
            appPromptAppend = initial.appPromptAppend.orEmpty()
            sttOverrideProviderId = initial.sttOverride?.providerId.orEmpty()
            sttOverrideModelId = initial.sttOverride?.modelId.orEmpty()
            textOverrideProviderId = initial.textOverride?.providerId.orEmpty()
            textOverrideModelId = initial.textOverride?.modelId.orEmpty()
            initialized = true
        }
    }

    val canSave = remember(initialized) { initialized }
    val missingProfile = remember(profileId, promptProfiles) {
        val id = profileId?.trim().orEmpty()
        id.isNotBlank() && promptProfiles.none { it.id == id }
    }

    fun overrideOrNull(providerId: String, modelId: String): ProviderModelOverride? {
        val p = providerId.trim()
        val m = modelId.trim()
        return if (p.isBlank() && m.isBlank()) {
            null
        } else {
            ProviderModelOverride(providerId = p, modelId = m)
        }
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("App mapping") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.AppMappings.route)
                    TextButton(
                        onClick = {
                            scope.launch {
                                val normalizedProfileId = profileId?.trim()?.takeIf { it.isNotBlank() }
                                val normalizedAppend = appPromptAppend.trim().takeIf { it.isNotBlank() }
                                val sttOverride = overrideOrNull(sttOverrideProviderId, sttOverrideModelId)
                                val textOverride = overrideOrNull(textOverrideProviderId, textOverrideModelId)
                                repository.upsertAppPromptMapping(
                                    AppPromptMapping(
                                        packageName = packageName,
                                        profileId = normalizedProfileId,
                                        appendMode = appendMode,
                                        appPromptAppend = normalizedAppend,
                                        sttOverride = sttOverride,
                                        textOverride = textOverride,
                                    )
                                )
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        },
                        enabled = canSave,
                    ) {
                        Text("Save")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Save applies this mapping right away. Leave any field empty to inherit the global defaults.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsGroup(title = "App") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsGroup(title = "Profile") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    val selectedProfile = promptProfiles.firstOrNull { it.id == profileId }
                    val label = selectedProfile?.name ?: profileId?.takeIf { it.isNotBlank() } ?: "None"
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = label,
                            onValueChange = {},
                            label = { Text("Prompt profile") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    profileId = null
                                    expanded = false
                                },
                            )
                            promptProfiles
                                .sortedBy { it.name.lowercase(Locale.getDefault()) }
                                .forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile.name) },
                                        onClick = {
                                            profileId = profile.id
                                            expanded = false
                                        },
                                    )
                                }
                        }
                    }

                    if (missingProfile) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Text(
                                    text = "Missing profile; mapping will fallback until updated.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            }

            SettingsGroup(title = "Prompt append") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = appendMode == AppendMode.APPEND,
                            onClick = { appendMode = AppendMode.APPEND },
                            label = { Text("Append") },
                        )
                        FilterChip(
                            selected = appendMode == AppendMode.NO_APPEND,
                            onClick = { appendMode = AppendMode.NO_APPEND },
                            label = { Text("No append") },
                        )
                    }

                    if (appendMode == AppendMode.NO_APPEND) {
                        Text(
                            text = "Append disabled for this app. Base prompt still applies.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        OutlinedTextField(
                            value = appPromptAppend,
                            onValueChange = { appPromptAppend = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 10,
                            label = { Text("App-specific append (optional)") },
                        )
                    }
                }
            }

            ProviderModelSelectionEditor(
                title = "Dictation override (STT)",
                providers = providers,
                allowedKinds = setOf(ModelKind.STT, ModelKind.MULTIMODAL),
                providerId = sttOverrideProviderId,
                modelId = sttOverrideModelId,
                onUpdate = { providerId, modelId ->
                    sttOverrideProviderId = providerId
                    sttOverrideModelId = modelId
                },
            )

            ProviderModelSelectionEditor(
                title = "Enhancement override (Text)",
                providers = providers,
                allowedKinds = setOf(ModelKind.TEXT, ModelKind.MULTIMODAL),
                providerId = textOverrideProviderId,
                modelId = textOverrideModelId,
                onUpdate = { providerId, modelId ->
                    textOverrideProviderId = providerId
                    textOverrideModelId = modelId
                },
            )

            if (existing != null) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repository.deleteAppPromptMapping(packageName)
                            Toast.makeText(context, "Removed mapping", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remove mapping")
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderModelSelectionEditor(
    title: String,
    providers: List<ServiceProvider>,
    allowedKinds: Set<ModelKind>,
    providerId: String,
    modelId: String,
    onUpdate: (String, String) -> Unit,
) {
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    val selectedProvider = remember(providers, providerId) { providers.firstOrNull { it.id == providerId } }
    val selectedModel = remember(selectedProvider, modelId) { selectedProvider?.models?.firstOrNull { it.id == modelId } }

    fun isCompatible(model: ModelConfig): Boolean {
        return model.kind in allowedKinds
    }

    SettingsGroup(title = title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = !providerExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val providerLabel = selectedProvider?.name
                    ?: providerId.takeIf { it.isNotBlank() }
                    ?: "Inherit"
                OutlinedTextField(
                    readOnly = true,
                    value = providerLabel,
                    onValueChange = {},
                    label = { Text("Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Inherit") },
                        onClick = {
                            onUpdate("", "")
                            providerExpanded = false
                        },
                    )
                    providers.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.name) },
                            onClick = {
                                onUpdate(provider.id, "")
                                providerExpanded = false
                            },
                        )
                    }
                    if (providers.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No providers configured") },
                            onClick = { providerExpanded = false },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { if (selectedProvider != null) modelExpanded = !modelExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val modelLabel = when {
                    selectedProvider == null -> modelId.takeIf { it.isNotBlank() } ?: "Inherit"
                    selectedModel != null -> selectedModel.name
                    modelId.isNotBlank() -> modelId
                    else -> "Inherit"
                }
                OutlinedTextField(
                    readOnly = true,
                    enabled = selectedProvider != null,
                    value = modelLabel,
                    onValueChange = {},
                    label = { Text("Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Inherit") },
                        onClick = {
                            onUpdate(providerId, "")
                            modelExpanded = false
                        },
                    )
                    val compatibleModels = selectedProvider?.models?.filter(::isCompatible).orEmpty()
                    compatibleModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.name) },
                            onClick = {
                                onUpdate(providerId, model.id)
                                modelExpanded = false
                            },
                        )
                    }
                    if (compatibleModels.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No compatible models") },
                            onClick = { modelExpanded = false },
                        )
                    }
                }
            }

            if (providerId.isNotBlank() || modelId.isNotBlank()) {
                OutlinedButton(
                    onClick = { onUpdate("", "") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDefaultsScreen(dataStore: DataStore<Preferences>, navController: NavHostController) {
    val repository = remember { SettingsRepository(dataStore) }
    val providers by repository.providers.collectAsState(initial = emptyList())
    val languageDefaults by repository.profiles.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var draftLanguageCode by remember { mutableStateOf("") }
    var expandedCode by remember { mutableStateOf<String?>(null) }

    fun upsert(updated: LanguageProfile) {
        val normalized = updated.languageCode.trim()
        if (normalized.isBlank()) {
            return
        }
        scope.launch {
            val next = languageDefaults.toMutableList()
            val index = next.indexOfFirst { it.languageCode.equals(normalized, ignoreCase = true) }
            val effective = updated.copy(languageCode = normalized)
            if (index >= 0) {
                next[index] = effective
            } else {
                next.add(effective)
            }
            repository.saveProfiles(next)
        }
    }

    fun delete(languageCode: String) {
        val normalized = languageCode.trim()
        if (normalized.isBlank()) {
            return
        }
        scope.launch {
            val next = languageDefaults.filterNot { it.languageCode.equals(normalized, ignoreCase = true) }
            repository.saveProfiles(next)
            Toast.makeText(context, "Removed", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Per-language defaults") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.LanguageDefaults.route)
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Add a language when one global setup is not enough. Changes save as soon as you choose them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (languageDefaults.isEmpty()) {
                SettingsGroup(title = "Languages") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No per-language defaults yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val sorted = remember(languageDefaults) {
                    languageDefaults.sortedBy { it.languageCode.lowercase(Locale.getDefault()) }
                }
                sorted.forEach { profile ->
                    val code = profile.languageCode
                    val isExpanded = expandedCode.equals(code, ignoreCase = true)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = code,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "STT + Text defaults",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { expandedCode = if (isExpanded) null else code }) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand",
                                    )
                                }
                                IconButton(onClick = { delete(code) }) {
                                    Icon(Icons.Default.Delete, "Delete")
                                }
                            }

                            if (isExpanded) {
                                ProviderModelSelectionEditor(
                                    title = "Dictation (STT)",
                                    providers = providers,
                                    allowedKinds = setOf(ModelKind.STT, ModelKind.MULTIMODAL),
                                    providerId = profile.transcriptionProviderId,
                                    modelId = profile.transcriptionModelId,
                                    onUpdate = { providerId, modelId ->
                                        upsert(
                                            profile.copy(
                                                transcriptionProviderId = providerId,
                                                transcriptionModelId = modelId,
                                            )
                                        )
                                    },
                                )

                                ProviderModelSelectionEditor(
                                    title = "Enhancement (Text)",
                                    providers = providers,
                                    allowedKinds = setOf(ModelKind.TEXT, ModelKind.MULTIMODAL),
                                    providerId = profile.smartFixProviderId,
                                    modelId = profile.smartFixModelId,
                                    onUpdate = { providerId, modelId ->
                                        upsert(
                                            profile.copy(
                                                smartFixProviderId = providerId,
                                                smartFixModelId = modelId,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                draftLanguageCode = ""
            },
            title = { Text("Add language") },
            text = {
                OutlinedTextField(
                    value = draftLanguageCode,
                    onValueChange = { draftLanguageCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Language code") },
                    placeholder = { Text("e.g. en, en-US, ar") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = draftLanguageCode.trim()
                        val exists = languageDefaults.any { it.languageCode.equals(normalized, ignoreCase = true) }
                        if (normalized.isBlank()) {
                            return@TextButton
                        }
                        if (exists) {
                            Toast.makeText(context, "Already exists", Toast.LENGTH_SHORT).show()
                            showAddDialog = false
                            draftLanguageCode = ""
                            expandedCode = normalized
                            return@TextButton
                        }
                        scope.launch {
                            repository.saveProfiles(
                                languageDefaults + LanguageProfile(
                                    languageCode = normalized,
                                    transcriptionProviderId = "",
                                    transcriptionModelId = "",
                                    smartFixProviderId = "",
                                    smartFixModelId = "",
                                )
                            )
                            Toast.makeText(context, "Added", Toast.LENGTH_SHORT).show()
                            expandedCode = normalized
                            showAddDialog = false
                            draftLanguageCode = ""
                        }
                    },
                    enabled = draftLanguageCode.trim().isNotBlank(),
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        draftLanguageCode = ""
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsSettingsScreen(dataStore: DataStore<Preferences>, navController: NavHostController) {
    val repository = remember { SettingsRepository(dataStore) }
    val enhancementPresetId by repository.enhancementPresetId.collectAsState(initial = TRANSFORM_PRESET_ID_CLEANUP)
    val commandPresetId by repository.commandPresetId.collectAsState(initial = TRANSFORM_PRESET_ID_TONE_REWRITE)
    val scope = rememberCoroutineScope()

    val enhancementPreset = remember(enhancementPresetId) {
        presetById(enhancementPresetId) ?: presetById(TRANSFORM_PRESET_ID_CLEANUP)
    }
    val commandPreset = remember(commandPresetId) {
        presetById(commandPresetId) ?: presetById(TRANSFORM_PRESET_ID_TONE_REWRITE)
    }

    var showEnhancementPicker by remember { mutableStateOf(false) }
    var showCommandPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.settings_transform_presets_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.Presets.route)
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Preset changes apply to the next enhancement or command run.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsGroup(title = stringResource(R.string.settings_transform_presets_group_title)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_transform_presets_enhancement_label)) },
                        supportingContent = {
                            val title = enhancementPreset?.let { stringResource(it.titleRes) }
                                ?: stringResource(R.string.transform_preset_cleanup_title)
                            val helper = stringResource(R.string.settings_transform_presets_enhancement_helper)
                            Text("$title • $helper")
                        },
                        leadingContent = { Icon(Icons.Default.AutoFixHigh, null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        modifier = Modifier.clickable { showEnhancementPicker = true },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_transform_presets_command_label)) },
                        supportingContent = {
                            val title = commandPreset?.let { stringResource(it.titleRes) }
                                ?: stringResource(R.string.transform_preset_tone_rewrite_title)
                            val helper = stringResource(R.string.settings_transform_presets_command_helper)
                            Text("$title • $helper")
                        },
                        leadingContent = { Icon(Icons.Default.EditNote, null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        modifier = Modifier.clickable { showCommandPicker = true },
                    )
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showEnhancementPicker) {
        AlertDialog(
            onDismissRequest = { showEnhancementPicker = false },
            title = { Text(stringResource(R.string.settings_transform_presets_picker_title)) },
            text = {
                Column {
                    TRANSFORM_PRESETS.forEachIndexed { index, preset ->
                        ListItem(
                            headlineContent = { Text(stringResource(preset.titleRes)) },
                            supportingContent = { Text(stringResource(preset.descriptionRes)) },
                            leadingContent = {
                                RadioButton(
                                    selected = enhancementPresetId == preset.id,
                                    onClick = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                showEnhancementPicker = false
                                scope.launch { repository.setEnhancementPresetId(preset.id) }
                            },
                        )
                        if (index < TRANSFORM_PRESETS.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEnhancementPicker = false }) {
                    Text(stringResource(R.string.settings_close))
                }
            },
        )
    }

    if (showCommandPicker) {
        AlertDialog(
            onDismissRequest = { showCommandPicker = false },
            title = { Text(stringResource(R.string.settings_transform_presets_picker_title)) },
            text = {
                Column {
                    TRANSFORM_PRESETS.forEachIndexed { index, preset ->
                        ListItem(
                            headlineContent = { Text(stringResource(preset.titleRes)) },
                            supportingContent = { Text(stringResource(preset.descriptionRes)) },
                            leadingContent = {
                                RadioButton(
                                    selected = commandPresetId == preset.id,
                                    onClick = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                showCommandPicker = false
                                scope.launch { repository.setCommandPresetId(preset.id) }
                            },
                        )
                        if (index < TRANSFORM_PRESETS.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCommandPicker = false }) {
                    Text(stringResource(R.string.settings_close))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectionsScreen(dataStore: DataStore<Preferences>, navController: NavHostController) {
    val settingsState by dataStore.data.collectAsState(initial = null)
    val repository = remember { SettingsRepository(dataStore) }
    val providers by repository.providers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val sttProviderId = settingsState?.get(ACTIVE_STT_PROVIDER_ID).orEmpty()
    val sttModelId = settingsState?.get(ACTIVE_STT_MODEL_ID).orEmpty()
    val textProviderId = settingsState?.get(ACTIVE_TEXT_PROVIDER_ID).orEmpty()
    val textModelId = settingsState?.get(ACTIVE_TEXT_MODEL_ID).orEmpty()
    val commandProviderId = settingsState?.get(COMMAND_TEXT_PROVIDER_ID).orEmpty()
    val commandModelId = settingsState?.get(COMMAND_TEXT_MODEL_ID).orEmpty()

    var sttProviderExpanded by remember { mutableStateOf(false) }
    var sttModelExpanded by remember { mutableStateOf(false) }
    var textProviderExpanded by remember { mutableStateOf(false) }
    var textModelExpanded by remember { mutableStateOf(false) }
    var commandProviderExpanded by remember { mutableStateOf(false) }
    var commandModelExpanded by remember { mutableStateOf(false) }

    val sttProvider = providers.find { it.id == sttProviderId }
    val textProvider = providers.find { it.id == textProviderId }
    val commandProvider = providers.find { it.id == commandProviderId }

    fun isSttCompatible(model: ModelConfig): Boolean {
        return model.kind == ModelKind.STT || model.kind == ModelKind.MULTIMODAL
    }

    fun isTextCompatible(model: ModelConfig): Boolean {
        return model.kind == ModelKind.TEXT || model.kind == ModelKind.MULTIMODAL
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Provider selections") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.ProviderSelections.route)
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Selections save immediately. Choose a provider first, then choose one of its compatible models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsGroup(title = "Defaults") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ListItem(
                        headlineContent = { Text("Per-language defaults") },
                        supportingContent = { Text("Choose STT and text defaults per language") },
                        leadingContent = { Icon(Icons.Default.Translate, null) },
                        modifier = Modifier.clickable {
                            navController.navigate(SettingsScreen.LanguageDefaults.route)
                        },
                    )
                }
            }

            SettingsGroup(title = "Dictation (STT)") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = sttProviderExpanded,
                        onExpandedChange = { sttProviderExpanded = !sttProviderExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = sttProvider?.name ?: "None",
                            onValueChange = {},
                            label = { Text("Provider") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sttProviderExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = sttProviderExpanded,
                            onDismissRequest = { sttProviderExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    scope.launch {
                                        dataStore.edit {
                                            it.remove(ACTIVE_STT_PROVIDER_ID)
                                            it.remove(ACTIVE_STT_MODEL_ID)
                                        }
                                    }
                                    sttProviderExpanded = false
                                }
                            )
                            if (providers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No Providers Configured") },
                                    onClick = { sttProviderExpanded = false }
                                )
                            }
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        scope.launch {
                                            dataStore.edit {
                                                it[ACTIVE_STT_PROVIDER_ID] = provider.id
                                                it.remove(ACTIVE_STT_MODEL_ID)
                                            }
                                        }
                                        sttProviderExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = sttModelExpanded,
                        onExpandedChange = { if (sttProvider != null) sttModelExpanded = !sttModelExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val selectedModel = sttProvider?.models?.firstOrNull { it.id == sttModelId }
                        val modelLabel = when {
                            sttProvider == null -> "Select provider first"
                            selectedModel != null -> selectedModel.name
                            sttModelId.isNotBlank() -> sttModelId
                            else -> "Select Model"
                        }
                        OutlinedTextField(
                            readOnly = true,
                            enabled = sttProvider != null,
                            value = modelLabel,
                            onValueChange = {},
                            label = { Text("Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sttModelExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = sttModelExpanded,
                            onDismissRequest = { sttModelExpanded = false }
                        ) {
                            val compatibleModels = sttProvider?.models?.filter(::isSttCompatible).orEmpty()
                            compatibleModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.name) },
                                    onClick = {
                                        scope.launch {
                                            dataStore.edit { it[ACTIVE_STT_MODEL_ID] = model.id }
                                        }
                                        sttModelExpanded = false
                                    }
                                )
                            }
                            if (compatibleModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No compatible models") },
                                    onClick = { sttModelExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            SettingsGroup(title = "Enhancement (Text)") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = textProviderExpanded,
                        onExpandedChange = { textProviderExpanded = !textProviderExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = textProvider?.name ?: "None",
                            onValueChange = {},
                            label = { Text("Provider") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = textProviderExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = textProviderExpanded,
                            onDismissRequest = { textProviderExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    scope.launch {
                                        dataStore.edit {
                                            it.remove(ACTIVE_TEXT_PROVIDER_ID)
                                            it.remove(ACTIVE_TEXT_MODEL_ID)
                                        }
                                    }
                                    textProviderExpanded = false
                                }
                            )
                            if (providers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No Providers Configured") },
                                    onClick = { textProviderExpanded = false }
                                )
                            }
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        scope.launch {
                                            dataStore.edit {
                                                it[ACTIVE_TEXT_PROVIDER_ID] = provider.id
                                                it.remove(ACTIVE_TEXT_MODEL_ID)
                                            }
                                        }
                                        textProviderExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = textModelExpanded,
                        onExpandedChange = { if (textProvider != null) textModelExpanded = !textModelExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val selectedModel = textProvider?.models?.firstOrNull { it.id == textModelId }
                        val modelLabel = when {
                            textProvider == null -> "Select provider first"
                            selectedModel != null -> selectedModel.name
                            textModelId.isNotBlank() -> textModelId
                            else -> "Select Model"
                        }
                        OutlinedTextField(
                            readOnly = true,
                            enabled = textProvider != null,
                            value = modelLabel,
                            onValueChange = {},
                            label = { Text("Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = textModelExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = textModelExpanded,
                            onDismissRequest = { textModelExpanded = false }
                        ) {
                            val compatibleModels = textProvider?.models?.filter(::isTextCompatible).orEmpty()
                            compatibleModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.name) },
                                    onClick = {
                                        scope.launch {
                                            dataStore.edit { it[ACTIVE_TEXT_MODEL_ID] = model.id }
                                        }
                                        textModelExpanded = false
                                    }
                                )
                            }
                            if (compatibleModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No compatible models") },
                                    onClick = { textModelExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            SettingsGroup(title = "Advanced") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (commandProviderId.isBlank() && commandModelId.isBlank()) {
                            "Command mode uses the enhancement selection by default."
                        } else {
                            "Command override is set. Clear it to inherit enhancement selection changes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    ExposedDropdownMenuBox(
                        expanded = commandProviderExpanded,
                        onExpandedChange = { commandProviderExpanded = !commandProviderExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val providerLabel = when {
                            commandProviderId.isBlank() && commandModelId.isBlank() -> "Inherit (Enhancement selection)"
                            commandProvider != null -> commandProvider.name
                            commandProviderId.isNotBlank() -> commandProviderId
                            else -> "Inherit (Enhancement selection)"
                        }
                        OutlinedTextField(
                            readOnly = true,
                            value = providerLabel,
                            onValueChange = {},
                            label = { Text("Command Provider") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = commandProviderExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = commandProviderExpanded,
                            onDismissRequest = { commandProviderExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Inherit (Enhancement selection)") },
                                onClick = {
                                    scope.launch {
                                        dataStore.edit {
                                            it.remove(COMMAND_TEXT_PROVIDER_ID)
                                            it.remove(COMMAND_TEXT_MODEL_ID)
                                        }
                                    }
                                    commandProviderExpanded = false
                                }
                            )
                            if (providers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No Providers Configured") },
                                    onClick = { commandProviderExpanded = false }
                                )
                            }
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        scope.launch {
                                            dataStore.edit {
                                                it[COMMAND_TEXT_PROVIDER_ID] = provider.id
                                                it.remove(COMMAND_TEXT_MODEL_ID)
                                            }
                                        }
                                        commandProviderExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = commandModelExpanded,
                        onExpandedChange = { if (commandProvider != null) commandModelExpanded = !commandModelExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val selectedModel = commandProvider?.models?.firstOrNull { it.id == commandModelId }
                        val modelLabel = when {
                            commandProvider == null -> "Inherit"
                            selectedModel != null -> selectedModel.name
                            commandModelId.isNotBlank() -> commandModelId
                            else -> "Select Model"
                        }
                        OutlinedTextField(
                            readOnly = true,
                            enabled = commandProvider != null,
                            value = modelLabel,
                            onValueChange = {},
                            label = { Text("Command Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = commandModelExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = commandModelExpanded,
                            onDismissRequest = { commandModelExpanded = false }
                        ) {
                            val compatibleModels = commandProvider?.models?.filter(::isTextCompatible).orEmpty()
                            compatibleModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.name) },
                                    onClick = {
                                        scope.launch {
                                            dataStore.edit { it[COMMAND_TEXT_MODEL_ID] = model.id }
                                        }
                                        commandModelExpanded = false
                                    }
                                )
                            }
                            if (compatibleModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No compatible models") },
                                    onClick = { commandModelExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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
    val context = LocalContext.current
    val repository = remember { SettingsRepository(dataStore) }
    val secretsStore = remember { SecretsStore(context) }
    val providers by repository.providers.collectAsState(initial = emptyList())
    val settingsState by dataStore.data.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val activeBackendId = settingsState?.get(SPEECH_TO_TEXT_BACKEND)

    var blockedDeleteProvider by remember { mutableStateOf<ServiceProvider?>(null) }
    var blockedDeleteRefs by remember { mutableStateOf<List<String>>(emptyList()) }
    
    var showAddOptions by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Providers") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.Backend.route)
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
                item {
                    Text(
                        text = "Add or test providers here, then open Provider selections to choose the active defaults Whisper++ should use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                            val refs = settingsState?.let { selectionReferencesProvider(provider.id, it) }.orEmpty()
                            if (refs.isNotEmpty()) {
                                blockedDeleteProvider = provider
                                blockedDeleteRefs = refs
                            } else {
                                scope.launch {
                                    val currentList = providers.toMutableList()
                                    currentList.removeAt(index)
                                    repository.saveProviders(currentList)
                                    secretsStore.clearProviderApiKey(provider.id)
                                    if (provider.id == activeBackendId) {
                                        dataStore.edit { it.remove(SPEECH_TO_TEXT_BACKEND) }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        if (blockedDeleteProvider != null) {
            AlertDialog(
                onDismissRequest = {
                    blockedDeleteProvider = null
                    blockedDeleteRefs = emptyList()
                },
                icon = { Icon(Icons.Default.Warning, null) },
                title = { Text("Can't delete selected provider") },
                text = {
                    val providerName = blockedDeleteProvider?.name.orEmpty()
                    val refsText = blockedDeleteRefs.joinToString(", ")
                    Text("$providerName is selected for: $refsText. Reassign or clear the selection first.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        blockedDeleteProvider = null
                        blockedDeleteRefs = emptyList()
                        navController.navigate(SettingsScreen.ProviderSelections.route)
                    }) {
                        Text("Go to selections")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        blockedDeleteProvider = null
                        blockedDeleteRefs = emptyList()
                    }) {
                        Text("Cancel")
                    }
                }
            )
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

private fun selectionReferencesProvider(providerId: String, prefs: PreferencesCore): List<String> {
    val refs = mutableListOf<String>()
    if (prefs[ACTIVE_STT_PROVIDER_ID] == providerId) {
        refs.add("Dictation (STT)")
    }
    if (prefs[ACTIVE_TEXT_PROVIDER_ID] == providerId) {
        refs.add("Enhancement (Text)")
    }
    if (prefs[COMMAND_TEXT_PROVIDER_ID] == providerId) {
        refs.add("Command override")
    }
    return refs
}

private fun selectionReferencesModel(providerId: String, modelId: String, prefs: PreferencesCore): List<String> {
    val refs = mutableListOf<String>()
    if (prefs[ACTIVE_STT_PROVIDER_ID] == providerId && prefs[ACTIVE_STT_MODEL_ID] == modelId) {
        refs.add("Dictation (STT)")
    }
    if (prefs[ACTIVE_TEXT_PROVIDER_ID] == providerId && prefs[ACTIVE_TEXT_MODEL_ID] == modelId) {
        refs.add("Enhancement (Text)")
    }
    if (prefs[COMMAND_TEXT_PROVIDER_ID] == providerId && prefs[COMMAND_TEXT_MODEL_ID] == modelId) {
        refs.add("Command override")
    }
    return refs
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    dataStore: DataStore<Preferences>,
    navController: NavHostController,
    providerId: String?,
    cloneFromId: String?
) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(dataStore) }
    val secretsStore = remember { SecretsStore(context) }
    val providers by repository.providers.collectAsState(initial = emptyList())
    val settingsState by dataStore.data.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val existingProviderId = providerId?.trim().orEmpty().takeIf { it.isNotEmpty() }
    val isEditingExisting = existingProviderId != null
    val isCloning = !isEditingExisting && !cloneFromId.isNullOrBlank()
    val effectiveProviderId = remember(existingProviderId, cloneFromId) {
        existingProviderId ?: UUID.randomUUID().toString()
    }
    
    // State
    var name by remember { mutableStateOf("") }
    var nameTouched by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(ProviderType.OPENAI) }
    var baseUrl by remember { mutableStateOf("") }
    var baseUrlTouched by remember { mutableStateOf(false) }
    var authMode by remember { mutableStateOf(ProviderAuthMode.API_KEY) }
    var authModeTouched by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyLast4 by remember { mutableStateOf<String?>(null) }
    var hasStoredApiKey by remember { mutableStateOf(false) }
    var cloneApiKeyLast4 by remember { mutableStateOf<String?>(null) }
    var hasCloneStoredApiKey by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(listOf<ModelConfig>()) }
    var blockedDeleteModel by remember { mutableStateOf<ModelConfig?>(null) }
    var blockedDeleteModelRefs by remember { mutableStateOf<List<String>>(emptyList()) }
    var temperature by remember { mutableStateOf(0.0f) }
    var prompt by remember { mutableStateOf("") }
    var languageCode by remember { mutableStateOf("auto") }
    var timeout by remember { mutableStateOf(10000) }
    
    // Thinking State
    var thinkingEnabled by remember { mutableStateOf(false) }
    var thinkingType by remember { mutableStateOf(ThinkingType.LEVEL) }
    var thinkingBudget by remember { mutableStateOf(4096) }
    var thinkingLevel by remember { mutableStateOf("medium") }

    var isTestRunning by remember { mutableStateOf(false) }
    var isImportRunning by remember { mutableStateOf(false) }
    var lastImportSummary by remember { mutableStateOf<String?>(null) }
    var showRawResponseDialog by remember { mutableStateOf(false) }
    var rawResponseTitle by remember { mutableStateOf("") }
    var rawResponseStatus by remember { mutableStateOf<Int?>(null) }
    var rawResponseBody by remember { mutableStateOf("") }
    var rawResponseError by remember { mutableStateOf<String?>(null) }

    var useCustomTextTestModel by remember { mutableStateOf(false) }
    var customTextTestModelId by remember { mutableStateOf("") }

    var useCustomSttTestModel by remember { mutableStateOf(false) }
    var customSttTestModelId by remember { mutableStateOf("") }

    var sttAudioUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSttTestAfterPick by remember { mutableStateOf(false) }

    val isAnyActionRunning = isTestRunning || isImportRunning
    
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

    fun defaultBaseUrlForType(providerType: ProviderType): String {
        return when (providerType) {
            ProviderType.OPENAI -> "https://api.openai.com/v1"
            ProviderType.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
            ProviderType.WHISPER_ASR -> "https://YOUR_SERVER_IP:9000/asr"
            ProviderType.CUSTOM -> ""
        }
    }

    fun defaultAuthModeForType(providerType: ProviderType): ProviderAuthMode {
        return when (providerType) {
            ProviderType.WHISPER_ASR -> ProviderAuthMode.NO_AUTH
            else -> ProviderAuthMode.API_KEY
        }
    }

    fun defaultNameFor(type: ProviderType, baseUrl: String): String {
        val host = baseUrl.trim().toHttpUrlOrNull()?.host?.takeIf { it.isNotBlank() }
        return if (host == null) type.toString() else "${type} $host"
    }

    fun normalizedBaseUrl(value: String): String? {
        val parsed = value.trim().toHttpUrlOrNull() ?: return null
        return parsed
            .newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
    }

    var initialLoadKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(providers, existingProviderId, cloneFromId) {
        val loadKey = "${existingProviderId.orEmpty()}|${cloneFromId.orEmpty()}"
        if (initialLoadKey == loadKey) {
            return@LaunchedEffect
        }

        if (isEditingExisting) {
            val provider = providers.find { it.id == existingProviderId }
            if (provider == null) {
                return@LaunchedEffect
            }
            initialLoadKey = loadKey
            name = provider.name
            nameTouched = true
            type = provider.type
            baseUrl = provider.endpoint
            baseUrlTouched = true
            authMode = provider.authMode
            authModeTouched = true
            models = provider.models
            temperature = provider.temperature
            prompt = provider.prompt
            languageCode = provider.languageCode
            timeout = provider.timeout
            thinkingEnabled = provider.thinkingEnabled
            thinkingType = provider.thinkingType
            thinkingBudget = provider.thinkingBudget
            thinkingLevel = provider.thinkingLevel

            val existingId = existingProviderId ?: return@LaunchedEffect
            val last4 = secretsStore.getProviderApiKeyLast4(existingId)
            apiKeyLast4 = last4
            hasStoredApiKey = last4 != null
            cloneApiKeyLast4 = null
            hasCloneStoredApiKey = false
            return@LaunchedEffect
        }

        if (isCloning) {
            val sourceId = cloneFromId?.trim().orEmpty().takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
            val sourceProvider = providers.find { it.id == sourceId }
            if (sourceProvider == null) {
                return@LaunchedEffect
            }
            initialLoadKey = loadKey
            name = sourceProvider.name
            nameTouched = true
            type = sourceProvider.type
            baseUrl = sourceProvider.endpoint
            baseUrlTouched = true
            authMode = sourceProvider.authMode
            authModeTouched = true
            models = sourceProvider.models
            temperature = sourceProvider.temperature
            prompt = sourceProvider.prompt
            languageCode = sourceProvider.languageCode
            timeout = sourceProvider.timeout
            thinkingEnabled = sourceProvider.thinkingEnabled
            thinkingType = sourceProvider.thinkingType
            thinkingBudget = sourceProvider.thinkingBudget
            thinkingLevel = sourceProvider.thinkingLevel

            val last4 = secretsStore.getProviderApiKeyLast4(sourceId)
            cloneApiKeyLast4 = last4
            hasCloneStoredApiKey = last4 != null
            apiKeyLast4 = null
            hasStoredApiKey = false
            return@LaunchedEffect
        }

        initialLoadKey = loadKey
        if (baseUrl.isBlank() && !baseUrlTouched) {
            baseUrl = defaultBaseUrlForType(type)
        }
        if (!authModeTouched) {
            authMode = defaultAuthModeForType(type)
        }
        if (name.isBlank() && !nameTouched) {
            name = defaultNameFor(type, baseUrl)
        }
    }

    LaunchedEffect(type, isEditingExisting) {
        if (isEditingExisting) {
            return@LaunchedEffect
        }
        if (!baseUrlTouched) {
            baseUrl = defaultBaseUrlForType(type)
        }
        if (!authModeTouched) {
            authMode = defaultAuthModeForType(type)
        }
    }

    LaunchedEffect(type, baseUrl, isEditingExisting) {
        if (isEditingExisting) {
            return@LaunchedEffect
        }
        if (!nameTouched) {
            name = defaultNameFor(type, baseUrl)
        }
    }

    val parsedBaseUrl = remember(baseUrl) { baseUrl.trim().toHttpUrlOrNull() }
    val isHttpsBaseUrl = parsedBaseUrl?.scheme == "https"
    val isBaseUrlValid = parsedBaseUrl != null && isHttpsBaseUrl
    val isNameValid = name.trim().isNotEmpty()
    val hasAnyApiKey = hasStoredApiKey || apiKeyInput.trim().isNotEmpty() || (isCloning && hasCloneStoredApiKey)
    val isAuthValid = when (authMode) {
        ProviderAuthMode.NO_AUTH -> true
        ProviderAuthMode.API_KEY -> hasAnyApiKey
    }
    val canSave = isNameValid && isBaseUrlValid && isAuthValid

    fun resolveApiKeyForTests(): String {
        if (authMode != ProviderAuthMode.API_KEY) {
            return ""
        }
        val typed = apiKeyInput.trim()
        if (typed.isNotEmpty()) {
            return typed
        }
        val id = existingProviderId ?: return ""
        return secretsStore.getProviderApiKey(id).orEmpty()
    }

    fun showRawResult(title: String, status: Int?, body: String, error: String?) {
        rawResponseTitle = title
        rawResponseStatus = status
        rawResponseBody = redactSecretsForUi(body)
        rawResponseError = error
        showRawResponseDialog = true
    }

    fun providerFromForm(modelsOverride: List<ModelConfig> = models): ServiceProvider {
        return ServiceProvider(
            id = effectiveProviderId,
            name = name.trim(),
            type = type,
            endpoint = baseUrl.trim(),
            authMode = authMode,
            apiKey = "",
            models = modelsOverride,
            temperature = temperature,
            prompt = prompt,
            languageCode = languageCode,
            timeout = timeout,
            thinkingEnabled = thinkingEnabled,
            thinkingType = thinkingType,
            thinkingBudget = thinkingBudget,
            thinkingLevel = thinkingLevel,
        )
    }

    suspend fun runTextTest(modelId: String) {
        val endpointUrl = buildProviderTextTestUrl(
            providerType = type,
            baseUrl = baseUrl,
            modelId = modelId,
        ) ?: run {
            showRawResult("Text test", null, "", "Invalid base URL")
            return
        }

        val requestBody = if (type == ProviderType.GEMINI) {
            JSONObject().apply {
                put(
                    "contents",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put(
                                    "parts",
                                    JSONArray().apply {
                                        put(JSONObject().apply { put("text", "Reply with OK") })
                                    }
                                )
                            }
                        )
                    }
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0)
                        put("maxOutputTokens", 16)
                    }
                )
            }.toString().toRequestBody("application/json".toMediaType())
        } else {
            JSONObject().apply {
                put("model", modelId)
                put(
                    "messages",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", "Reply with OK")
                            }
                        )
                    }
                )
                put("temperature", 0)
                put("max_tokens", 16)
            }.toString().toRequestBody("application/json".toMediaType())
        }

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)

        if (authMode == ProviderAuthMode.API_KEY) {
            val apiKey = resolveApiKeyForTests()
            if (apiKey.isNotBlank()) {
                if (type == ProviderType.GEMINI) {
                    requestBuilder.addHeader("x-goog-api-key", apiKey)
                } else {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }
            }
        }

        val request = requestBuilder.build()
        val (status, body, error) = withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    Triple(response.code, response.body?.string().orEmpty(), null)
                }
            } catch (e: Exception) {
                Triple(null, "", e.message)
            }
        }

        showRawResult("Text test", status, body, error)
    }

    suspend fun runSttTest(modelId: String, audioUri: Uri) {
        val endpointUrl = buildProviderSttTestUrl(baseUrl) ?: run {
            showRawResult("STT test", null, "", "Invalid base URL")
            return
        }

        val tempFile = withContext(Dispatchers.IO) {
            val mimeType = context.contentResolver.getType(audioUri).orEmpty()
            val file = File.createTempFile("provider_test_", null, context.cacheDir)
            context.contentResolver.openInputStream(audioUri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to open audio file")
            Pair(file, mimeType)
        }

        val (file, mimeType) = tempFile

        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody(mimeType.toMediaTypeOrNull())
                )
                .addFormDataPart("model", modelId)
                .build()

            val requestBuilder = Request.Builder()
                .url(endpointUrl)
                .post(body)

            if (authMode == ProviderAuthMode.API_KEY) {
                val apiKey = resolveApiKeyForTests()
                if (apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }
            }

            val request = requestBuilder.build()
            val (status, rawBody, error) = withContext(Dispatchers.IO) {
                try {
                    httpClient.newCall(request).execute().use { response ->
                        Triple(response.code, response.body?.string().orEmpty(), null)
                    }
                } catch (e: Exception) {
                    Triple(null, "", e.message)
                }
            }

            showRawResult("STT test", status, rawBody, error)
        } finally {
            withContext(Dispatchers.IO) {
                runCatching { file.delete() }
            }
        }
    }

    suspend fun importModels() {
        val endpointUrl = buildModelsListUrl(baseUrl) ?: run {
            showRawResult("Import models", null, "", "Invalid base URL")
            return
        }

        val requestBuilder = Request.Builder().url(endpointUrl).get()
        if (authMode == ProviderAuthMode.API_KEY) {
            val apiKey = resolveApiKeyForTests()
            if (apiKey.isBlank()) {
                showRawResult("Import models", null, "", "API key missing")
                return
            }
            if (type == ProviderType.GEMINI) {
                requestBuilder.addHeader("x-goog-api-key", apiKey)
            } else {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
        }

        val request = requestBuilder.build()
        val (status, body, error) = withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    Triple(response.code, response.body?.string().orEmpty(), null)
                }
            } catch (e: Exception) {
                Triple(null, "", e.message)
            }
        }

        if (status == null) {
            showRawResult("Import models", null, body, error ?: "Request failed")
            return
        }
        if (status / 100 != 2) {
            showRawResult("Import models", status, body, error)
            return
        }

        val json = runCatching { JSONObject(body) }.getOrNull()
        if (json == null) {
            showRawResult("Import models", status, body, "Invalid JSON response")
            return
        }

        val importedModels = if (type == ProviderType.GEMINI) {
            val modelsArray = json.optJSONArray("models")
            if (modelsArray == null) {
                emptyList()
            } else {
                (0 until modelsArray.length()).mapNotNull { i ->
                    val obj = modelsArray.optJSONObject(i) ?: return@mapNotNull null
                    val rawName = obj.optString("name").trim()
                    val id = rawName.removePrefix("models/").trimStart('/').trim()
                    if (id.isBlank()) {
                        return@mapNotNull null
                    }
                    val displayName = obj.optString("displayName").trim()
                    ModelConfig(
                        id = id,
                        name = displayName.ifBlank { id },
                        kind = ModelKind.MULTIMODAL,
                        streamingPartialsSupported = false,
                    )
                }
            }
        } else {
            val dataArray = json.optJSONArray("data")
            if (dataArray == null) {
                emptyList()
            } else {
                (0 until dataArray.length()).mapNotNull { i ->
                    val obj = dataArray.optJSONObject(i) ?: return@mapNotNull null
                    val id = obj.optString("id").trim()
                    if (id.isBlank()) {
                        return@mapNotNull null
                    }
                    ModelConfig(
                        id = id,
                        name = id,
                        kind = ModelKind.TEXT,
                        streamingPartialsSupported = false,
                    )
                }
            }
        }

        if (importedModels.isEmpty()) {
            showRawResult("Import models", status, body, "No models found")
            return
        }

        val existingIds = models.mapNotNull { it.id.trim().takeIf { id -> id.isNotBlank() } }.toSet()
        val dedupedImport = importedModels.filter { it.id.trim().isNotBlank() && !existingIds.contains(it.id.trim()) }
        val updatedModels = models + dedupedImport

        models = updatedModels
        repository.upsertProvider(providerFromForm(modelsOverride = updatedModels))
        lastImportSummary = "Imported ${dedupedImport.size} model${if (dedupedImport.size == 1) "" else "s"}"
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        sttAudioUri = uri
        if (uri != null && pendingSttTestAfterPick) {
            pendingSttTestAfterPick = false
            scope.launch {
                val modelId = if (useCustomSttTestModel || models.firstOrNull { it.kind == ModelKind.STT || it.kind == ModelKind.MULTIMODAL }?.id.isNullOrBlank()) {
                    customSttTestModelId.trim()
                } else {
                    models.firstOrNull { it.kind == ModelKind.STT || it.kind == ModelKind.MULTIMODAL }?.id.orEmpty()
                }
                if (modelId.isBlank()) {
                    showRawResult("STT test", null, "", "Model ID is required")
                    return@launch
                }
                isTestRunning = true
                try {
                    runSttTest(modelId, uri)
                } finally {
                    isTestRunning = false
                }
            }
        }
    }

    val normalized = remember(baseUrl) { normalizedBaseUrl(baseUrl) }
    val duplicateProvider = remember(providers, type, normalized, existingProviderId) {
        if (normalized == null) {
            null
        } else {
            providers.firstOrNull {
                it.id != existingProviderId && it.type == type && normalizedBaseUrl(it.endpoint) == normalized
            }
        }
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        when {
                            isEditingExisting -> "Edit Provider"
                            isCloning -> "Duplicate Provider"
                            else -> "New Provider"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.Backend.route)
                    if (isEditingExisting && existingProviderId != null) {
                        TextButton(onClick = {
                            navController.navigate(SettingsScreen.ProviderEdit.createRoute(id = null, cloneFromId = existingProviderId))
                        }) {
                            Text("Duplicate")
                        }
                    }
                    TextButton(
                        enabled = canSave,
                        onClick = {
                                val newProvider = ServiceProvider(
                                    id = effectiveProviderId,
                                    name = name.trim(),
                                    type = type,
                                    endpoint = baseUrl.trim(),
                                    authMode = authMode,
                                    apiKey = "",
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
                                 repository.upsertProvider(newProvider)

                                when (authMode) {
                                    ProviderAuthMode.NO_AUTH -> Unit
                                    ProviderAuthMode.API_KEY -> {
                                        if (apiKeyInput.isNotBlank()) {
                                            secretsStore.setProviderApiKey(newProvider.id, apiKeyInput.trim())
                                        } else if (isCloning && !cloneFromId.isNullOrBlank()) {
                                            val sourceKey = secretsStore.getProviderApiKey(cloneFromId.trim()).orEmpty()
                                            if (sourceKey.isNotBlank()) {
                                                secretsStore.setProviderApiKey(newProvider.id, sourceKey)
                                            }
                                        }
                                    }
                                }

                                 navController.popBackStack()
                             }
                         }
                     ) {
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
            Text(
                text = "Save stores this provider for selections and tests. Use Test or model import to verify the setup before leaving.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameTouched = true
                },
                label = { Text("Provider Name") },
                modifier = Modifier.fillMaxWidth()
            )

            if (isEditingExisting) {
                OutlinedTextField(
                    readOnly = true,
                    value = type.toString(),
                    onValueChange = {},
                    label = { Text("Type") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
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
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    baseUrlTouched = true
                },
                label = { Text("Base URL") },
                isError = baseUrl.isNotBlank() && !isBaseUrlValid,
                modifier = Modifier.fillMaxWidth()
            )

            if (baseUrl.isBlank()) {
                Text("Base URL is required", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!isBaseUrlValid) {
                Text("Use a valid https:// URL", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (duplicateProvider != null) {
                Text(
                    "Duplicate: ${duplicateProvider.name} already uses this type + base URL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Authentication", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = authMode == ProviderAuthMode.API_KEY,
                    onClick = {
                        authMode = ProviderAuthMode.API_KEY
                        authModeTouched = true
                    },
                    label = { Text("API Key") }
                )
                FilterChip(
                    selected = authMode == ProviderAuthMode.NO_AUTH,
                    onClick = {
                        authMode = ProviderAuthMode.NO_AUTH
                        authModeTouched = true
                        apiKeyInput = ""
                    },
                    label = { Text("No Auth") }
                )
            }
            
            if (authMode == ProviderAuthMode.API_KEY) {
                val keyStatusText = when {
                    hasStoredApiKey -> if (apiKeyLast4.isNullOrEmpty()) "API Key: Key set" else "API Key: Key set (ends with $apiKeyLast4)"
                    isCloning && hasCloneStoredApiKey -> if (cloneApiKeyLast4.isNullOrEmpty()) "API Key: Will reuse key" else "API Key: Will reuse key (ends with $cloneApiKeyLast4)"
                    else -> "API Key: Not set"
                }
                Text(keyStatusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text(if (hasStoredApiKey || (isCloning && hasCloneStoredApiKey)) "Replace API Key" else "Set API Key") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasStoredApiKey) {
                    OutlinedButton(
                        onClick = {
                            val secretsProviderId = existingProviderId ?: return@OutlinedButton
                            secretsStore.clearProviderApiKey(secretsProviderId)
                            apiKeyInput = ""
                            apiKeyLast4 = null
                            hasStoredApiKey = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Stored Key")
                    }
                }
            }

            if (isEditingExisting && existingProviderId != null) {
                HorizontalDivider()
                Text("Test", style = MaterialTheme.typography.titleMedium)

                val defaultTextModelId = models.firstOrNull {
                    it.kind == ModelKind.TEXT || it.kind == ModelKind.MULTIMODAL
                }?.id.orEmpty()
                val defaultSttModelId = models.firstOrNull {
                    it.kind == ModelKind.STT || it.kind == ModelKind.MULTIMODAL
                }?.id.orEmpty()

                val mustUseCustomTextModel = defaultTextModelId.isBlank()
                val mustUseCustomSttModel = defaultSttModelId.isBlank()
                val effectiveUseCustomTextModel = useCustomTextTestModel || mustUseCustomTextModel
                val effectiveUseCustomSttModel = useCustomSttTestModel || mustUseCustomSttModel

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            enabled = !isAnyActionRunning,
                            onClick = {
                                scope.launch {
                                    val modelId = if (effectiveUseCustomTextModel) {
                                        customTextTestModelId.trim()
                                    } else {
                                        defaultTextModelId
                                    }
                                    if (modelId.isBlank()) {
                                        showRawResult("Text test", null, "", "Model ID is required")
                                        return@launch
                                    }
                                    isTestRunning = true
                                    try {
                                        runTextTest(modelId)
                                    } finally {
                                        isTestRunning = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test Text")
                        }

                        OutlinedButton(
                            enabled = !isAnyActionRunning,
                            onClick = {
                                val audio = sttAudioUri
                                if (audio == null) {
                                    pendingSttTestAfterPick = true
                                    audioPickerLauncher.launch(arrayOf("audio/*"))
                                    return@OutlinedButton
                                }

                                scope.launch {
                                    val modelId = if (effectiveUseCustomSttModel) {
                                        customSttTestModelId.trim()
                                    } else {
                                        defaultSttModelId
                                    }
                                    if (modelId.isBlank()) {
                                        showRawResult("STT test", null, "", "Model ID is required")
                                        return@launch
                                    }
                                    isTestRunning = true
                                    try {
                                        runSttTest(modelId, audio)
                                    } finally {
                                        isTestRunning = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test STT")
                        }
                    }

                    OutlinedButton(
                        enabled = !isAnyActionRunning && type != ProviderType.WHISPER_ASR,
                        onClick = {
                            scope.launch {
                                isImportRunning = true
                                try {
                                    importModels()
                                } finally {
                                    isImportRunning = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import models")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = effectiveUseCustomTextModel,
                            enabled = !mustUseCustomTextModel,
                            onClick = { useCustomTextTestModel = !useCustomTextTestModel },
                            label = { Text("Test anyway") }
                        )
                        FilterChip(
                            selected = effectiveUseCustomSttModel,
                            enabled = !mustUseCustomSttModel,
                            onClick = { useCustomSttTestModel = !useCustomSttTestModel },
                            label = { Text("Force model") }
                        )
                        OutlinedButton(
                            enabled = !isAnyActionRunning,
                            onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                        ) {
                            Text(if (sttAudioUri == null) "Pick audio" else "Change audio")
                        }
                    }

                    if (effectiveUseCustomTextModel) {
                        OutlinedTextField(
                            value = customTextTestModelId,
                            onValueChange = { customTextTestModelId = it },
                            label = { Text("Text model id") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }

                    if (effectiveUseCustomSttModel) {
                        OutlinedTextField(
                            value = customSttTestModelId,
                            onValueChange = { customSttTestModelId = it },
                            label = { Text("STT model id") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }

                    if (isTestRunning) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Running...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    lastImportSummary?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
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
                          val refs = settingsState?.let { selectionReferencesModel(effectiveProviderId, model.id, it) }.orEmpty()
                          if (refs.isNotEmpty()) {
                              blockedDeleteModel = model
                              blockedDeleteModelRefs = refs
                          } else {
                              val newModels = models.toMutableList()
                              newModels.removeAt(index)
                              models = newModels
                          }
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

      if (showRawResponseDialog) {
          AlertDialog(
              onDismissRequest = {
                  showRawResponseDialog = false
                  rawResponseError = null
              },
              title = { Text(rawResponseTitle) },
              text = {
                  Column(
                      modifier = Modifier
                          .fillMaxWidth()
                          .verticalScroll(rememberScrollState()),
                      verticalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                      rawResponseStatus?.let { Text("HTTP $it", style = MaterialTheme.typography.labelMedium) }
                      rawResponseError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                      if (rawResponseBody.isNotBlank()) {
                          Text(rawResponseBody, style = MaterialTheme.typography.bodySmall)
                      } else if (rawResponseError == null) {
                          Text("(empty response)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                  }
              },
              confirmButton = {
                  TextButton(onClick = { showRawResponseDialog = false }) {
                      Text("Close")
                  }
              }
          )
      }

      if (blockedDeleteModel != null) {
         AlertDialog(
             onDismissRequest = {
                 blockedDeleteModel = null
                 blockedDeleteModelRefs = emptyList()
             },
             icon = { Icon(Icons.Default.Warning, null) },
             title = { Text("Can't delete selected model") },
             text = {
                 val modelName = blockedDeleteModel?.name.orEmpty()
                 val refsText = blockedDeleteModelRefs.joinToString(", ")
                 Text("$modelName is selected for: $refsText. Reassign or clear the selection first.")
             },
             confirmButton = {
                 TextButton(onClick = {
                     blockedDeleteModel = null
                     blockedDeleteModelRefs = emptyList()
                     navController.navigate(SettingsScreen.ProviderSelections.route)
                 }) {
                     Text("Go to selections")
                 }
             },
              dismissButton = {
                  TextButton(onClick = {
                      blockedDeleteModel = null
                      blockedDeleteModelRefs = emptyList()
                  }) {
                      Text("Cancel")
                  }
              }
          )
      }
}

private fun sanitizedBaseUrl(baseUrl: String): HttpUrl? {
    return baseUrl.trim().toHttpUrlOrNull()
        ?.newBuilder()
        ?.query(null)
        ?.fragment(null)
        ?.build()
}

private fun buildProviderTextTestUrl(providerType: ProviderType, baseUrl: String, modelId: String): HttpUrl? {
    return if (providerType == ProviderType.GEMINI) {
        buildGeminiGenerateContentUrl(baseUrl, modelId)
    } else {
        buildOpenAiChatCompletionsUrl(baseUrl)
    }
}

private fun buildProviderSttTestUrl(baseUrl: String): HttpUrl? {
    return buildOpenAiTranscriptionsUrl(baseUrl)
}

private fun buildModelsListUrl(baseUrl: String): HttpUrl? {
    val base = sanitizedBaseUrl(baseUrl) ?: return null
    val segments = base.pathSegments.filter { it.isNotBlank() }
    return if (segments.lastOrNull() == "models") {
        base
    } else {
        base.newBuilder().addPathSegment("models").build()
    }
}

private fun buildOpenAiChatCompletionsUrl(baseUrl: String): HttpUrl? {
    val base = sanitizedBaseUrl(baseUrl) ?: return null
    val segments = base.pathSegments.filter { it.isNotBlank() }
    val alreadyDerived = segments.size >= 2 && segments.takeLast(2) == listOf("chat", "completions")
    return if (alreadyDerived) base else base.newBuilder().addPathSegments("chat/completions").build()
}

private fun buildOpenAiTranscriptionsUrl(baseUrl: String): HttpUrl? {
    val base = sanitizedBaseUrl(baseUrl) ?: return null
    val segments = base.pathSegments.filter { it.isNotBlank() }
    val alreadyDerived = segments.size >= 2 && segments.takeLast(2) == listOf("audio", "transcriptions")
    return if (alreadyDerived) base else base.newBuilder().addPathSegments("audio/transcriptions").build()
}

private fun buildGeminiGenerateContentUrl(baseUrl: String, modelId: String): HttpUrl? {
    val base = sanitizedBaseUrl(baseUrl) ?: return null
    val normalizedPath = base.encodedPath.trim().trimEnd('/')
    if (normalizedPath.endsWith(":generateContent") && normalizedPath.contains("/models/")) {
        return base
    }
    val normalizedModel = modelId.trim().removePrefix("models/").trimStart('/')
    return base.newBuilder()
        .addPathSegments("models")
        .addPathSegment("${normalizedModel}:generateContent")
        .build()
}

private fun redactSecretsForUi(value: String): String {
    if (value.isBlank()) {
        return value
    }
    return value
        .replace(Regex("(?i)(Authorization\\s*:\\s*Bearer\\s+)([^\\s]+)"), "${'$'}1REDACTED")
        .replace(Regex("(?i)(x-goog-api-key\\s*:\\s*)([^\\s]+)"), "${'$'}1REDACTED")
        .replace(Regex("([?&]key=)[^&\\s]+"), "${'$'}1REDACTED")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ModelEditCard(model: ModelConfig, onUpdate: (ModelConfig) -> Unit, onDelete: () -> Unit) {
    var kindExpanded by remember { mutableStateOf(false) }

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

            ExposedDropdownMenuBox(
                expanded = kindExpanded,
                onExpandedChange = { kindExpanded = !kindExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = model.kind.name,
                    onValueChange = {},
                    label = { Text("Kind") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kindExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = kindExpanded,
                    onDismissRequest = { kindExpanded = false }
                ) {
                    ModelKind.values().forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.name) },
                            onClick = {
                                val updated = if (kind == ModelKind.TEXT) {
                                    model.copy(kind = kind, streamingPartialsSupported = false)
                                } else {
                                    model.copy(kind = kind)
                                }
                                onUpdate(updated)
                                kindExpanded = false
                            }
                        )
                    }
                }
            }

            if (model.kind == ModelKind.STT || model.kind == ModelKind.MULTIMODAL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = model.streamingPartialsSupported,
                        onCheckedChange = { onUpdate(model.copy(streamingPartialsSupported = it)) }
                    )
                    Text("Streaming partials supported")
                }
            }

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
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.PostProcessing.route)
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Changes apply immediately. Use a custom prompt only when the default cleanup behavior is not enough.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.Keyboard.route)
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Keyboard toggles apply immediately and affect the next dictation or command session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

    val currentChannel = settingsState?.get(UPDATE_CHANNEL) ?: "stable"

    LaunchedEffect(Unit) {
        updateManager.observeExistingDownload(scope)
        if (showUpdate) {
            val channel = UpdateChannel.fromString(currentChannel)
            updateManager.checkForUpdate(scope, channel)
        }
    }
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
