package app.cursor.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.cursor.android.streaming.StreamingNotifications
import app.cursor.android.ui.AgentScreen
import app.cursor.android.ui.InboxScreen
import app.cursor.android.ui.NewAgentScreen
import app.cursor.android.ui.SettingsScreen
import app.cursor.android.ui.theme.CursorTheme
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {
    private val viewModel: CursorAppViewModel by viewModels()
    private val agentNavigationRequests = AgentNavigationRequests()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enqueueAgentNavigation(intent)
        splash.setKeepOnScreenCondition { false }
        enableEdgeToEdge()
        setContent {
            val inbox by viewModel.inbox.collectAsStateWithLifecycle()
            CursorTheme(
                themeMode = inbox.settings.themeMode,
                dynamicColor = inbox.settings.dynamicColor,
                spinnerStyle = inbox.settings.spinnerStyle,
                textScaleId = inbox.settings.textScale,
                fontWeightId = inbox.settings.fontWeight,
                haptics = inbox.settings.haptics,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    CursorApp(viewModel, agentNavigationRequests.requests)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enqueueAgentNavigation(intent)
    }

    private fun enqueueAgentNavigation(intent: Intent) {
        agentNavigationRequests.offer(
            intent.getStringExtra(StreamingNotifications.EXTRA_AGENT_ID),
        )
        intent.removeExtra(StreamingNotifications.EXTRA_AGENT_ID)
    }
}

@Composable
private fun CursorApp(
    viewModel: CursorAppViewModel,
    agentNavigationRequests: Flow<String>,
) {
    val navController = rememberNavController()

    // Android-default feel: a short fade with a slight horizontal slide, nothing fancier.
    NavHost(
        navController = navController,
        startDestination = "inbox",
        enterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 }
        },
        exitTransition = {
            fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -it / 16 }
        },
        popEnterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 12 }
        },
        popExitTransition = {
            fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 16 }
        },
    ) {
        composable("inbox") {
            InboxScreen(
                viewModel = viewModel,
                onOpenAgent = { id -> navController.navigateToAgent(id) },
                onNewAgent = { navController.navigate("new") },
                onSettings = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable("new") {
            NewAgentScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCreated = { id -> navController.navigateToAgent(id) },
            )
        }
        composable(
            route = "agent/{agentId}",
            arguments = listOf(navArgument("agentId") { type = NavType.StringType }),
        ) { entry ->
            val agentId = entry.arguments?.getString("agentId").orEmpty()
            AgentScreen(
                agentId = agentId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }

    LaunchedEffect(navController, agentNavigationRequests) {
        agentNavigationRequests.collect { agentId ->
            navController.navigateToAgent(agentId)
        }
    }
}

private fun NavHostController.navigateToAgent(agentId: String) {
    val normalized = normalizeAgentId(agentId) ?: return
    val currentAgentId = currentBackStackEntry?.arguments?.getString("agentId")
    if (!shouldNavigateToAgent(currentDestination?.route, currentAgentId, normalized)) return

    navigate(agentRoute(normalized)) {
        popUpTo("inbox")
        launchSingleTop = true
    }
}

private fun agentRoute(agentId: String): String = "agent/${Uri.encode(agentId)}"

internal fun shouldNavigateToAgent(
    currentRoute: String?,
    currentAgentId: String?,
    targetAgentId: String,
): Boolean = currentRoute != AGENT_ROUTE || currentAgentId != targetAgentId

internal const val AGENT_ROUTE = "agent/{agentId}"
