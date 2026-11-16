package com.example.data.templates

import com.example.data.generator.JsonProjectExtractor
import com.example.model.GeneratedFile
import com.example.model.GeneratedProjectPayload

enum class StarterTemplateCategory(val label: String) {
    ALL("All"),
    NAVIGATION("Navigation"),
    MASTER_DETAIL("Master-Detail"),
    AUTH_FORMS("Auth & Forms"),
    PRODUCTIVITY("Productivity")
}

enum class TemplatePreviewStyle {
    BOTTOM_NAV,
    MASTER_DETAIL,
    LOGIN_FORM,
    TASK_DASHBOARD,
    SETTINGS_HUB
}

data class StarterTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val category: StarterTemplateCategory,
    val previewStyle: TemplatePreviewStyle,
    val defaultAppName: String,
    val defaultPackageName: String,
    val complexityLabel: String,
    val includedFeatures: List<String>,
    val sampleFileNames: List<String>,
    val buildSourceFiles: (appName: String, packageName: String, pkgDir: String) -> List<GeneratedFile>
)

object StarterTemplateCatalog {

    val templates: List<StarterTemplate> = listOf(
        StarterTemplate(
            id = "bottom_navigation",
            title = "Bottom Navigation App",
            subtitle = "3-tab Material 3 NavigationBar architecture",
            description = "Complete multi-tab Compose scaffold with Home, Explore, and Profile destinations, shared TopAppBar, interactive counters, and profile switches.",
            category = StarterTemplateCategory.NAVIGATION,
            previewStyle = TemplatePreviewStyle.BOTTOM_NAV,
            defaultAppName = "Pulse Nav",
            defaultPackageName = "com.starter.bottomnav",
            complexityLabel = "3 Tabs • M3",
            includedFeatures = listOf("NavigationBar", "Scaffold", "3 Tab Screens", "M3 Theme"),
            sampleFileNames = listOf(
                "MainActivity.kt",
                "ui/screens/HomeTabScreen.kt",
                "ui/screens/ExploreTabScreen.kt",
                "ui/screens/ProfileTabScreen.kt",
                "ui/theme/Theme.kt",
                "res/values/strings.xml",
                "app/build.gradle.kts"
            ),
            buildSourceFiles = { appName, pkg, pkgDir ->
                buildBottomNavFiles(appName, pkg, pkgDir)
            }
        ),
        StarterTemplate(
            id = "master_detail_list",
            title = "Master-Detail List",
            subtitle = "Searchable catalog list with detail navigation",
            description = "Browseable master list with search bar, category chips, bookmark toggles, and a dedicated Detail screen with back navigation.",
            category = StarterTemplateCategory.MASTER_DETAIL,
            previewStyle = TemplatePreviewStyle.MASTER_DETAIL,
            defaultAppName = "Catalog Pro",
            defaultPackageName = "com.starter.masterdetail",
            complexityLabel = "2 Screens • List",
            includedFeatures = listOf("LazyColumn", "Search Filter", "Detail Flow", "Bookmarks"),
            sampleFileNames = listOf(
                "MainActivity.kt",
                "model/CatalogItem.kt",
                "ui/screens/MasterListScreen.kt",
                "ui/screens/ItemDetailScreen.kt",
                "ui/theme/Theme.kt",
                "res/values/strings.xml",
                "app/build.gradle.kts"
            ),
            buildSourceFiles = { appName, pkg, pkgDir ->
                buildMasterDetailFiles(appName, pkg, pkgDir)
            }
        ),
        StarterTemplate(
            id = "login_form",
            title = "Login Form",
            subtitle = "Validated sign-in & sign-up flow with visibility toggle",
            description = "Clean Material 3 authentication skeleton featuring email & password validation, password masking toggle, mode switch (Sign In / Sign Up), and welcome dashboard.",
            category = StarterTemplateCategory.AUTH_FORMS,
            previewStyle = TemplatePreviewStyle.LOGIN_FORM,
            defaultAppName = "Auth Flow",
            defaultPackageName = "com.starter.loginform",
            complexityLabel = "Validated Form",
            includedFeatures = listOf("OutlinedTextField", "Validation", "Password Toggle", "Welcome View"),
            sampleFileNames = listOf(
                "MainActivity.kt",
                "ui/screens/LoginScreen.kt",
                "ui/screens/WelcomeHomeScreen.kt",
                "ui/theme/Theme.kt",
                "res/values/strings.xml",
                "app/build.gradle.kts"
            ),
            buildSourceFiles = { appName, pkg, pkgDir ->
                buildLoginFormFiles(appName, pkg, pkgDir)
            }
        ),
        StarterTemplate(
            id = "task_dashboard",
            title = "Task & Habit Dashboard",
            subtitle = "Progress tracker with quick-add & interactive checklist",
            description = "Productivity starter featuring a live completion progress bar, quick-add task input, interactive checkboxes, and priority tags.",
            category = StarterTemplateCategory.PRODUCTIVITY,
            previewStyle = TemplatePreviewStyle.TASK_DASHBOARD,
            defaultAppName = "Focus Tasks",
            defaultPackageName = "com.starter.tasks",
            complexityLabel = "Interactive CRUD",
            includedFeatures = listOf("Progress Bar", "Quick Add", "Checklist State", "Filter Chips"),
            sampleFileNames = listOf(
                "MainActivity.kt",
                "model/TaskItem.kt",
                "ui/screens/TaskDashboardScreen.kt",
                "ui/theme/Theme.kt",
                "res/values/strings.xml",
                "app/build.gradle.kts"
            ),
            buildSourceFiles = { appName, pkg, pkgDir ->
                buildTaskDashboardFiles(appName, pkg, pkgDir)
            }
        ),
        StarterTemplate(
            id = "settings_preferences",
            title = "Settings & Preferences Hub",
            subtitle = "Grouped Material 3 settings cards & toggles",
            description = "Structured preferences screen with grouped ElevatedCards, notification and sync switches, accent theme selector chips, and account status card.",
            category = StarterTemplateCategory.NAVIGATION,
            previewStyle = TemplatePreviewStyle.SETTINGS_HUB,
            defaultAppName = "Prefs Studio",
            defaultPackageName = "com.starter.settingshub",
            complexityLabel = "Preferences UI",
            includedFeatures = listOf("ElevatedCard", "Switch Controls", "Accent Chips", "Account Card"),
            sampleFileNames = listOf(
                "MainActivity.kt",
                "ui/screens/SettingsHubScreen.kt",
                "ui/theme/Theme.kt",
                "res/values/strings.xml",
                "app/build.gradle.kts"
            ),
            buildSourceFiles = { appName, pkg, pkgDir ->
                buildSettingsHubFiles(appName, pkg, pkgDir)
            }
        )
    )

    fun derivePackageName(appName: String, fallback: String): String {
        val slug = appName.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(18)
        return if (slug.length >= 2 && slug.first().isLetter()) {
            "com.starter.$slug"
        } else {
            fallback
        }
    }

    fun buildProjectPayload(
        template: StarterTemplate,
        customAppName: String = template.defaultAppName,
        customPackageName: String = template.defaultPackageName
    ): GeneratedProjectPayload {
        val resolvedAppName = customAppName.trim().ifBlank { template.defaultAppName }
        val sanitizedPkg = JsonProjectExtractor.sanitizePackageName(
            customPackageName.trim().ifBlank { template.defaultPackageName }
        )
        val pkgDir = sanitizedPkg.replace('.', '/')

        val sourceFiles = template.buildSourceFiles(resolvedAppName, sanitizedPkg, pkgDir)
        val completeFiles = JsonProjectExtractor.ensureEssentialGradleFiles(
            appName = resolvedAppName,
            packageName = sanitizedPkg,
            files = sourceFiles
        )

        return GeneratedProjectPayload(
            appName = resolvedAppName,
            packageName = sanitizedPkg,
            files = completeFiles.sortedBy { it.path }
        )
    }

    private fun commonThemeFile(appName: String, packageName: String, pkgDir: String): GeneratedFile {
        val safeThemePrefix = appName.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "StarterApp" }
        return GeneratedFile(
            path = "app/src/main/java/$pkgDir/ui/theme/Theme.kt",
            content = """
                package $packageName.ui.theme

                import androidx.compose.foundation.isSystemInDarkTheme
                import androidx.compose.material3.MaterialTheme
                import androidx.compose.material3.darkColorScheme
                import androidx.compose.material3.lightColorScheme
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.graphics.Color

                private val LightColors = lightColorScheme(
                    primary = Color(0xFF0284C7),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFE0F2FE),
                    onPrimaryContainer = Color(0xFF082F49),
                    secondary = Color(0xFF059669),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFFD1FAE5),
                    onSecondaryContainer = Color(0xFF022C22),
                    background = Color(0xFFF8FAFC),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFE2E8F0)
                )

                private val DarkColors = darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    onPrimary = Color(0xFF070B14),
                    primaryContainer = Color(0xFF0B3B5B),
                    onPrimaryContainer = Color(0xFFBAE6FD),
                    secondary = Color(0xFF34D399),
                    onSecondary = Color(0xFF070B14),
                    secondaryContainer = Color(0xFF0A3B2E),
                    onSecondaryContainer = Color(0xFFA7F3D0),
                    background = Color(0xFF0B1120),
                    surface = Color(0xFF111827),
                    surfaceVariant = Color(0xFF1E293B)
                )

                @Composable
                fun ${safeThemePrefix}Theme(
                    darkTheme: Boolean = isSystemInDarkTheme(),
                    content: @Composable () -> Unit
                ) {
                    MaterialTheme(
                        colorScheme = if (darkTheme) DarkColors else LightColors,
                        content = content
                    )
                }
            """.trimIndent()
        )
    }

    private fun commonStringsXml(appName: String): GeneratedFile {
        val escaped = appName
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return GeneratedFile(
            path = "app/src/main/res/values/strings.xml",
            content = """
                <resources>
                    <string name="app_name">$escaped</string>
                </resources>
            """.trimIndent()
        )
    }

    private fun buildBottomNavFiles(
        appName: String,
        pkg: String,
        pkgDir: String
    ): List<GeneratedFile> {
        val themeFn = appName.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "StarterApp" } + "Theme"
        return listOf(
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/MainActivity.kt",
                content = """
                    package $pkg

                    import android.os.Bundle
                    import androidx.activity.ComponentActivity
                    import androidx.activity.compose.setContent
                    import androidx.activity.enableEdgeToEdge
                    import androidx.compose.foundation.layout.Box
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.foundation.layout.padding
                    import androidx.compose.material.icons.Icons
                    import androidx.compose.material.icons.filled.Explore
                    import androidx.compose.material.icons.filled.Home
                    import androidx.compose.material.icons.filled.Person
                    import androidx.compose.material3.ExperimentalMaterial3Api
                    import androidx.compose.material3.Icon
                    import androidx.compose.material3.NavigationBar
                    import androidx.compose.material3.NavigationBarItem
                    import androidx.compose.material3.Scaffold
                    import androidx.compose.material3.Text
                    import androidx.compose.material3.TopAppBar
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableIntStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import androidx.compose.ui.Modifier
                    import $pkg.ui.screens.ExploreTabScreen
                    import $pkg.ui.screens.HomeTabScreen
                    import $pkg.ui.screens.ProfileTabScreen
                    import $pkg.ui.theme.$themeFn

                    class MainActivity : ComponentActivity() {
                        override fun onCreate(savedInstanceState: Bundle?) {
                            super.onCreate(savedInstanceState)
                            enableEdgeToEdge()
                            setContent {
                                $themeFn {
                                    BottomNavApp()
                                }
                            }
                        }
                    }

                    @OptIn(ExperimentalMaterial3Api::class)
                    @Composable
                    fun BottomNavApp() {
                        var selectedTab by remember { mutableIntStateOf(0) }
                        val tabs = listOf("Home", "Explore", "Profile")

                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("$appName — ${'$'}{tabs[selectedTab]}") }
                                )
                            },
                            bottomBar = {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                        label = { Text("Home") }
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
                                        label = { Text("Explore") }
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 2,
                                        onClick = { selectedTab = 2 },
                                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                        label = { Text("Profile") }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (selectedTab) {
                                    0 -> HomeTabScreen(onExploreClick = { selectedTab = 1 })
                                    1 -> ExploreTabScreen()
                                    else -> ProfileTabScreen()
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/ui/screens/HomeTabScreen.kt",
                content = """
                    package $pkg.ui.screens

                    import androidx.compose.foundation.layout.Arrangement
                    import androidx.compose.foundation.layout.Column
                    import androidx.compose.foundation.layout.Row
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.foundation.layout.fillMaxWidth
                    import androidx.compose.foundation.layout.padding
                    import androidx.compose.material3.Button
                    import androidx.compose.material3.Card
                    import androidx.compose.material3.ElevatedCard
                    import androidx.compose.material3.MaterialTheme
                    import androidx.compose.material3.OutlinedButton
                    import androidx.compose.material3.Text
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableIntStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import androidx.compose.ui.Modifier
                    import androidx.compose.ui.unit.dp

                    @Composable
                    fun HomeTabScreen(onExploreClick: () -> Unit = {}) {
                        var checkIns by remember { mutableIntStateOf(3) }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Welcome back",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = "Your Material 3 bottom navigation skeleton is ready. Today check-ins: ${'$'}checkIns",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { checkIns++ }) {
                                            Text("Check In (+1)")
                                        }
                                        OutlinedButton(onClick = onExploreClick) {
                                            Text("Open Explore")
                                        }
                                    }
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Architecture Ready",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Each tab has its own screen composable inside ui/screens/.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/ui/screens/ExploreTabScreen.kt",
                content = """
                    package $pkg.ui.screens

                    import androidx.compose.foundation.layout.Arrangement
                    import androidx.compose.foundation.layout.Column
                    import androidx.compose.foundation.layout.PaddingValues
                    import androidx.compose.foundation.layout.Row
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.foundation.layout.fillMaxWidth
                    import androidx.compose.foundation.layout.padding
                    import androidx.compose.foundation.lazy.LazyColumn
                    import androidx.compose.foundation.lazy.items
                    import androidx.compose.material3.Card
                    import androidx.compose.material3.FilterChip
                    import androidx.compose.material3.MaterialTheme
                    import androidx.compose.material3.Text
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import androidx.compose.ui.Modifier
                    import androidx.compose.ui.unit.dp

                    @Composable
                    fun ExploreTabScreen() {
                        val categories = listOf("All", "Design", "Compose", "Tools")
                        var activeCategory by remember { mutableStateOf("All") }
                        val sampleFeed = listOf(
                            "Material 3 Dynamic Color" to "Design",
                            "Adaptive NavigationBar" to "Compose",
                            "Kotlin Coroutines & Flow" to "Tools",
                            "Edge-to-Edge WindowInsets" to "Compose"
                        ).filter { activeCategory == "All" || it.second == activeCategory }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                categories.forEach { category ->
                                    FilterChip(
                                        selected = activeCategory == category,
                                        onClick = { activeCategory = category },
                                        label = { Text(category) }
                                    )
                                }
                            }

                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(sampleFeed) { (title, tag) ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = "Category: ${'$'}tag",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/ui/screens/ProfileTabScreen.kt",
                content = """
                    package $pkg.ui.screens

                    import androidx.compose.foundation.layout.Arrangement
                    import androidx.compose.foundation.layout.Column
                    import androidx.compose.foundation.layout.Row
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.foundation.layout.fillMaxWidth
                    import androidx.compose.foundation.layout.padding
                    import androidx.compose.material3.Card
                    import androidx.compose.material3.MaterialTheme
                    import androidx.compose.material3.Switch
                    import androidx.compose.material3.Text
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import androidx.compose.ui.Alignment
                    import androidx.compose.ui.Modifier
                    import androidx.compose.ui.unit.dp

                    @Composable
                    fun ProfileTabScreen() {
                        var notificationsEnabled by remember { mutableStateOf(true) }
                        var offlineCacheEnabled by remember { mutableStateOf(true) }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Developer Profile", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Compose Starter Member • Active Workspace",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Push Notifications", style = MaterialTheme.typography.bodyLarge)
                                        Switch(
                                            checked = notificationsEnabled,
                                            onCheckedChange = { notificationsEnabled = it }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Offline Sync Cache", style = MaterialTheme.typography.bodyLarge)
                                        Switch(
                                            checked = offlineCacheEnabled,
                                            onCheckedChange = { offlineCacheEnabled = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            commonThemeFile(appName, pkg, pkgDir),
            commonStringsXml(appName)
        )
    }

    private fun buildMasterDetailFiles(
        appName: String,
        pkg: String,
        pkgDir: String
    ): List<GeneratedFile> {
        val themeFn = appName.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "StarterApp" } + "Theme"
        return listOf(
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/MainActivity.kt",
                content = """
                    package $pkg

                    import android.os.Bundle
                    import androidx.activity.ComponentActivity
                    import androidx.activity.compose.BackHandler
                    import androidx.activity.compose.setContent
                    import androidx.activity.enableEdgeToEdge
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import $pkg.model.CatalogItem
                    import $pkg.model.SampleCatalogData
                    import $pkg.ui.screens.ItemDetailScreen
                    import $pkg.ui.screens.MasterListScreen
                    import $pkg.ui.theme.$themeFn

                    class MainActivity : ComponentActivity() {
                        override fun onCreate(savedInstanceState: Bundle?) {
                            super.onCreate(savedInstanceState)
                            enableEdgeToEdge()
                            setContent {
                                $themeFn {
                                    MasterDetailApp()
                                }
                            }
                        }
                    }

                    @Composable
                    fun MasterDetailApp() {
                        var items by remember { mutableStateOf(SampleCatalogData.items) }
                        var selectedItemId by remember { mutableStateOf<String?>(null) }

                        val selectedItem: CatalogItem? = items.firstOrNull { it.id == selectedItemId }

                        if (selectedItem != null) {
                            BackHandler { selectedItemId = null }
                            ItemDetailScreen(
                                item = selectedItem,
                                onBack = { selectedItemId = null },
                                onToggleFavorite = { targetId ->
                                    items = items.map { entry ->
                                        if (entry.id == targetId) entry.copy(isFavorite = !entry.isFavorite) else entry
                                    }
                                }
                            )
                        } else {
                            MasterListScreen(
                                appTitle = "$appName",
                                items = items,
                                onSelectItem = { item -> selectedItemId = item.id },
                                onToggleFavorite = { targetId ->
                                    items = items.map { entry ->
                                        if (entry.id == targetId) entry.copy(isFavorite = !entry.isFavorite) else entry
                                    }
                                }
                            )
                        }
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/model/CatalogItem.kt",
                content = """
                    package $pkg.model

                    data class CatalogItem(
                        val id: String,
                        val title: String,
                        val category: String,
                        val summary: String,
                        val details: String,
                        val isFavorite: Boolean = false
                    )

                    object SampleCatalogData {
                        val items = listOf(
                            CatalogItem(
                                id = "1",
                                title = "Jetpack Compose Architecture",
                                category = "UI",
                                summary = "Declarative UI state management and unidirectional data flow.",
                                details = "Organize screens into stateless composables paired with ViewModel StateFlows for predictable UI updates and testability."
                            ),
                            CatalogItem(
                                id = "2",
                                title = "Room Local Persistence",
                                category = "Data",
                                summary = "Type-safe SQLite database with Flow queries.",
                                details = "Expose reactive Flow<List<Entity>> streams from your DAO so list and detail views stay synchronized automatically."
                            ),
                            CatalogItem(
                                id = "3",
                                title = "Material 3 Adaptive Layouts",
                                category = "Design",
                                summary = "Responsive panes across phones, foldables, and tablets.",
                                details = "Support compact single-pane navigation and side-by-side list-detail panes cleanly."
                            ),
                            CatalogItem(
                                id = "4",
                                title = "Coroutines & Structured Concurrency",
                                category = "Async",
                                summary = "Safe background execution with lifecycle awareness.",
                                details = "Dispatch network and disk I/O onto Dispatchers.IO while keeping UI recompositions smooth at 60/120fps."
                            )
                        )
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/ui/screens/MasterListScreen.kt",
                content = """
                    package $pkg.ui.screens

                    import androidx.compose.foundation.clickable
                    import androidx.compose.foundation.layout.Arrangement
                    import androidx.compose.foundation.layout.Column
                    import androidx.compose.foundation.layout.PaddingValues
                    import androidx.compose.foundation.layout.Row
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.foundation.layout.fillMaxWidth
                    import androidx.compose.foundation.layout.padding
                    import androidx.compose.foundation.lazy.LazyColumn
                    import androidx.compose.foundation.lazy.items
                    import androidx.compose.material.icons.Icons
                    import androidx.compose.material.icons.filled.Favorite
                    import androidx.compose.material.icons.filled.FavoriteBorder
                    import androidx.compose.material3.ElevatedCard
                    import androidx.compose.material3.ExperimentalMaterial3Api
                    import androidx.compose.material3.FilterChip
                    import androidx.compose.material3.Icon
                    import androidx.compose.material3.IconButton
                    import androidx.compose.material3.MaterialTheme
                    import androidx.compose.material3.OutlinedTextField
                    import androidx.compose.material3.Scaffold
                    import androidx.compose.material3.Text
                    import androidx.compose.material3.TopAppBar
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import androidx.compose.ui.Alignment
                    import androidx.compose.ui.Modifier
                    import androidx.compose.ui.unit.dp
                    import $pkg.model.CatalogItem

                    @OptIn(ExperimentalMaterial3Api::class)
                    @Composable
                    fun MasterListScreen(
                        appTitle: String = "Catalog",
                        items: List<CatalogItem> = emptyList(),
                        onSelectItem: (CatalogItem) -> Unit = {},
                        onToggleFavorite: (String) -> Unit = {}
                    ) {
                        var query by remember { mutableStateOf("") }
                        var selectedCategory by remember { mutableStateOf("All") }

                        val categories = listOf("All", "UI", "Data", "Design", "Async")
                        val filtered = items.filter { item ->
                            val matchesCat = selectedCategory == "All" || item.category == selectedCategory
                            val matchesQuery = query.isBlank() ||
                                item.title.contains(query, ignoreCase = true) ||
                                item.summary.contains(query, ignoreCase = true)
                            matchesCat && matchesQuery
                        }

                        Scaffold(
                            topBar = {
                                TopAppBar(title = { Text(appTitle) })
                            }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    label = { Text("Search items") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    categories.forEach { cat ->
                                        FilterChip(
                                            selected = selectedCategory == cat,
                                            onClick = { selectedCategory = cat },
                                            label = { Text(cat) }
                                        )
                                    }
                                }

                                LazyColumn(
                                    contentPadding = PaddingValues(bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filtered, key = { it.id }) { item ->
                                        ElevatedCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onSelectItem(item) }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        text = item.summary,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(onClick = { onToggleFavorite(item.id) }) {
                                                    Icon(
                                                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                        contentDescription = "Toggle favorite",
                                                        tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/ui/screens/ItemDetailScreen.kt",
                content = """
                    package $pkg.ui.screens

                    import androidx.compose.foundation.layout.Arrangement
                    import androidx.compose.foundation.layout.Column
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.foundation.layout.fillMaxWidth
                    import androidx.compose.foundation.layout.padding
                    import androidx.compose.material.icons.Icons
                    import androidx.compose.material.icons.automirrored.filled.ArrowBack
                    import androidx.compose.material3.Button
                    import androidx.compose.material3.Card
                    import androidx.compose.material3.ExperimentalMaterial3Api
                    import androidx.compose.material3.Icon
                    import androidx.compose.material3.IconButton
                    import androidx.compose.material3.MaterialTheme
                    import androidx.compose.material3.Scaffold
                    import androidx.compose.material3.Text
                    import androidx.compose.material3.TopAppBar
                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.Modifier
                    import androidx.compose.ui.unit.dp
                    import $pkg.model.CatalogItem

                    @OptIn(ExperimentalMaterial3Api::class)
                    @Composable
                    fun ItemDetailScreen(
                        item: CatalogItem,
                        onBack: () -> Unit = {},
                        onToggleFavorite: (String) -> Unit = {}
                    ) {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text(item.title) },
                                    navigationIcon = {
                                        IconButton(onClick = onBack) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back to list"
                                            )
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Category: ${'$'}{item.category}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = item.summary,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = item.details,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onToggleFavorite(item.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (item.isFavorite) "Remove from Favorites" else "Save to Favorites")
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            commonThemeFile(appName, pkg, pkgDir),
            commonStringsXml(appName)
        )
    }

    private fun buildLoginFormFiles(
        appName: String,
        pkg: String,
        pkgDir: String
    ): List<GeneratedFile> {
        val themeFn = appName.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "StarterApp" } + "Theme"
        return listOf(
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/MainActivity.kt",
                content = """
                    package $pkg

                    import android.os.Bundle
                    import androidx.activity.ComponentActivity
                    import androidx.activity.compose.setContent
                    import androidx.activity.enableEdgeToEdge
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import $pkg.ui.screens.LoginScreen
                    import $pkg.ui.screens.WelcomeHomeScreen
                    import $pkg.ui.theme.$themeFn

                    class MainActivity : ComponentActivity() {
                        override fun onCreate(savedInstanceState: Bundle?) {
                            super.onCreate(savedInstanceState)
                            enableEdgeToEdge()
                            setContent {
                                $themeFn {
                                    AuthFlowApp()
                                }
                            }
                        }
                    }

                    @Composable
                    fun AuthFlowApp() {
                        var signedInEmail by remember { mutableStateOf<String?>(null) }

                        if (signedInEmail == null) {
                            LoginScreen(
                                appTitle = "$appName",
                                onAuthenticated = { email -> signedInEmail = email }
                            )
                        } else {
                            WelcomeHomeScreen(
                                userEmail = signedInEmail.orEmpty(),
                                onSignOut = { signedInEmail = null }
                            )
                        }
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/ui/screens/LoginScreen.kt",
                content = """
                    package $pkg.ui.screens

                    import androidx.compose.foundation.layout.Arrangement
                    import androidx.compose.foundation.layout.Column
                    import androidx.compose.foundation.layout.Row
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.foundation.layout.fillMaxWidth
                    import androidx.compose.foundation.layout.padding
                    import androidx.compose.material.icons.Icons
                    import androidx.compose.material.icons.filled.Lock
                    import androidx.compose.material.icons.filled.Visibility
                    import androidx.compose.material.icons.filled.VisibilityOff
                    import androidx.compose.material3.Button
                    import androidx.compose.material3.ElevatedCard
                    import androidx.compose.material3.ExperimentalMaterial3Api
                    import androidx.compose.material3.Icon
                    import androidx.compose.material3.IconButton
                    import androidx.compose.material3.MaterialTheme
                    import androidx.compose.material3.OutlinedTextField
                    import androidx.compose.material3.Scaffold
                    import androidx.compose.material3.Switch
                    import androidx.compose.material3.Text
                    import androidx.compose.material3.TextButton
                    import androidx.compose.material3.TopAppBar
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import androidx.compose.ui.Alignment
                    import androidx.compose.ui.Modifier
                    import androidx.compose.ui.text.input.PasswordVisualTransformation
                    import androidx.compose.ui.text.input.VisualTransformation
                    import androidx.compose.ui.unit.dp

                    @OptIn(ExperimentalMaterial3Api::class)
                    @Composable
                    fun LoginScreen(
                        appTitle: String = "Welcome",
                        onAuthenticated: (String) -> Unit = {}
                    ) {
                        var email by remember { mutableStateOf("") }
                        var password by remember { mutableStateOf("") }
                        var showPassword by remember { mutableStateOf(false) }
                        var rememberMe by remember { mutableStateOf(true) }
                        var isSignUpMode by remember { mutableStateOf(false) }
                        var validationError by remember { mutableStateOf<String?>(null) }

                        Scaffold(
                            topBar = { TopAppBar(title = { Text(appTitle) }) }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.Lock, contentDescription = null)
                                            Text(
                                                text = if (isSignUpMode) "Create Account" else "Sign In",
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                        }

                                        OutlinedTextField(
                                            value = email,
                                            onValueChange = {
                                                email = it
                                                validationError = null
                                            },
                                            label = { Text("Email Address") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = {
                                                password = it
                                                validationError = null
                                            },
                                            label = { Text("Password") },
                                            singleLine = true,
                                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showPassword = !showPassword }) {
                                                    Icon(
                                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = "Toggle password visibility"
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        if (validationError != null) {
                                            Text(
                                                text = validationError.orEmpty(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Keep me signed in", style = MaterialTheme.typography.bodyMedium)
                                            Switch(
                                                checked = rememberMe,
                                                onCheckedChange = { rememberMe = it }
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                val trimmed = email.trim()
                                                when {
                                                    !trimmed.contains("@") ->
                                                        validationError = "Enter a valid email address."
                                                    password.length < 6 ->
                                                        validationError = "Password must be at least 6 characters."
                                                    else -> onAuthenticated(trimmed)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(if (isSignUpMode) "Create Account" else "Sign In")
                                        }

                                        TextButton(
                                            onClick = { isSignUpMode = !isSignUpMode },
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        ) {
                                            Text(
                                                if (isSignUpMode) "Already have an account? Sign In"
                                                else "New here? Create an account"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/ui/screens/WelcomeHomeScreen.kt",
                content = """
                    package $pkg.ui.screens

                    import androidx.compose.foundation.layout.Arrangement
                    import androidx.compose.foundation.layout.Column
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.foundation.layout.fillMaxWidth
                    import androidx.compose.foundation.layout.padding
                    import androidx.compose.material3.Card
                    import androidx.compose.material3.MaterialTheme
                    import androidx.compose.material3.OutlinedButton
                    import androidx.compose.material3.Text
                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.Modifier
                    import androidx.compose.ui.unit.dp

                    @Composable
                    fun WelcomeHomeScreen(
                        userEmail: String = "user@example.com",
                        onSignOut: () -> Unit = {}
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Signed In", style = MaterialTheme.typography.headlineSmall)
                                    Text(
                                        text = "Authenticated as ${'$'}userEmail",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = onSignOut,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Sign Out")
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            commonThemeFile(appName, pkg, pkgDir),
            commonStringsXml(appName)
        )
    }

    private fun buildTaskDashboardFiles(
        appName: String,
        pkg: String,
        pkgDir: String
    ): List<GeneratedFile> {
        val themeFn = appName.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "StarterApp" } + "Theme"
        return listOf(
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/MainActivity.kt",
                content = """
                    package $pkg

                    import android.os.Bundle
                    import androidx.activity.ComponentActivity
                    import androidx.activity.compose.setContent
                    import androidx.activity.enableEdgeToEdge
                    import $pkg.ui.screens.TaskDashboardScreen
                    import $pkg.ui.theme.$themeFn

                    class MainActivity : ComponentActivity() {
                        override fun onCreate(savedInstanceState: Bundle?) {
                            super.onCreate(savedInstanceState)
                            enableEdgeToEdge()
                            setContent {
                                $themeFn {
                                    TaskDashboardScreen(appTitle = "$appName")
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/model/TaskItem.kt",
                content = """
                    package $pkg.model

                    data class TaskItem(
                        val id: Int,
                        val title: String,
                        val category: String,
                        val isDone: Boolean = false
                    )
                """.trimIndent()
            ),
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/ui/screens/TaskDashboardScreen.kt",
                content = """
                    package $pkg.ui.screens

                    import androidx.compose.foundation.layout.Arrangement
                    import androidx.compose.foundation.layout.Column
                    import androidx.compose.foundation.layout.PaddingValues
                    import androidx.compose.foundation.layout.Row
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.foundation.layout.fillMaxWidth
                    import androidx.compose.foundation.layout.padding
                    import androidx.compose.foundation.lazy.LazyColumn
                    import androidx.compose.foundation.lazy.items
                    import androidx.compose.material3.Button
                    import androidx.compose.material3.Card
                    import androidx.compose.material3.Checkbox
                    import androidx.compose.material3.ElevatedCard
                    import androidx.compose.material3.ExperimentalMaterial3Api
                    import androidx.compose.material3.LinearProgressIndicator
                    import androidx.compose.material3.MaterialTheme
                    import androidx.compose.material3.OutlinedTextField
                    import androidx.compose.material3.Scaffold
                    import androidx.compose.material3.Text
                    import androidx.compose.material3.TopAppBar
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import androidx.compose.ui.Alignment
                    import androidx.compose.ui.Modifier
                    import androidx.compose.ui.unit.dp
                    import $pkg.model.TaskItem

                    @OptIn(ExperimentalMaterial3Api::class)
                    @Composable
                    fun TaskDashboardScreen(appTitle: String = "Tasks") {
                        var tasks by remember {
                            mutableStateOf(
                                listOf(
                                    TaskItem(1, "Design Material 3 cards", "Design", true),
                                    TaskItem(2, "Wire ViewModel StateFlow", "Code", false),
                                    TaskItem(3, "Test APK build workflow", "Release", false)
                                )
                            )
                        }
                        var newTaskTitle by remember { mutableStateOf("") }
                        val completedCount = tasks.count { it.isDone }
                        val progress = if (tasks.isEmpty()) 0f else completedCount.toFloat() / tasks.size.toFloat()

                        Scaffold(
                            topBar = { TopAppBar(title = { Text(appTitle) }) }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Daily Progress: ${'$'}completedCount / ${'$'}{tasks.size} completed",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newTaskTitle,
                                        onValueChange = { newTaskTitle = it },
                                        label = { Text("New task") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            val clean = newTaskTitle.trim()
                                            if (clean.isNotEmpty()) {
                                                val nextId = (tasks.maxOfOrNull { it.id } ?: 0) + 1
                                                tasks = tasks + TaskItem(nextId, clean, "General", false)
                                                newTaskTitle = ""
                                            }
                                        }
                                    ) {
                                        Text("Add")
                                    }
                                }

                                LazyColumn(
                                    contentPadding = PaddingValues(bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(tasks, key = { it.id }) { task ->
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Checkbox(
                                                    checked = task.isDone,
                                                    onCheckedChange = { checked ->
                                                        tasks = tasks.map {
                                                            if (it.id == task.id) it.copy(isDone = checked) else it
                                                        }
                                                    }
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                                                    Text(
                                                        text = task.category,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            commonThemeFile(appName, pkg, pkgDir),
            commonStringsXml(appName)
        )
    }

    private fun buildSettingsHubFiles(
        appName: String,
        pkg: String,
        pkgDir: String
    ): List<GeneratedFile> {
        val themeFn = appName.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "StarterApp" } + "Theme"
        return listOf(
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/MainActivity.kt",
                content = """
                    package $pkg

                    import android.os.Bundle
                    import androidx.activity.ComponentActivity
                    import androidx.activity.compose.setContent
                    import androidx.activity.enableEdgeToEdge
                    import $pkg.ui.screens.SettingsHubScreen
                    import $pkg.ui.theme.$themeFn

                    class MainActivity : ComponentActivity() {
                        override fun onCreate(savedInstanceState: Bundle?) {
                            super.onCreate(savedInstanceState)
                            enableEdgeToEdge()
                            setContent {
                                $themeFn {
                                    SettingsHubScreen(appTitle = "$appName")
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            GeneratedFile(
                path = "app/src/main/java/$pkgDir/ui/screens/SettingsHubScreen.kt",
                content = """
                    package $pkg.ui.screens

                    import androidx.compose.foundation.layout.Arrangement
                    import androidx.compose.foundation.layout.Column
                    import androidx.compose.foundation.layout.Row
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.foundation.layout.fillMaxWidth
                    import androidx.compose.foundation.layout.padding
                    import androidx.compose.material3.ElevatedCard
                    import androidx.compose.material3.ExperimentalMaterial3Api
                    import androidx.compose.material3.FilterChip
                    import androidx.compose.material3.MaterialTheme
                    import androidx.compose.material3.Scaffold
                    import androidx.compose.material3.Switch
                    import androidx.compose.material3.Text
                    import androidx.compose.material3.TopAppBar
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import androidx.compose.ui.Alignment
                    import androidx.compose.ui.Modifier
                    import androidx.compose.ui.unit.dp

                    @OptIn(ExperimentalMaterial3Api::class)
                    @Composable
                    fun SettingsHubScreen(appTitle: String = "Settings") {
                        var notifications by remember { mutableStateOf(true) }
                        var autoBackup by remember { mutableStateOf(true) }
                        var selectedSyncInterval by remember { mutableStateOf("15m") }

                        Scaffold(
                            topBar = { TopAppBar(title = { Text(appTitle) }) }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("General Preferences", style = MaterialTheme.typography.titleMedium)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Notifications", style = MaterialTheme.typography.bodyLarge)
                                            Switch(checked = notifications, onCheckedChange = { notifications = it })
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Auto Cloud Backup", style = MaterialTheme.typography.bodyLarge)
                                            Switch(checked = autoBackup, onCheckedChange = { autoBackup = it })
                                        }
                                    }
                                }

                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("Background Sync Interval", style = MaterialTheme.typography.titleMedium)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("15m", "1h", "Daily").forEach { option ->
                                                FilterChip(
                                                    selected = selectedSyncInterval == option,
                                                    onClick = { selectedSyncInterval = option },
                                                    label = { Text(option) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
            ),
            commonThemeFile(appName, pkg, pkgDir),
            commonStringsXml(appName)
        )
    }
}
