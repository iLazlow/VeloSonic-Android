package de.ilazlow.velosonic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.deeplink.DeepLinkHolder
import de.ilazlow.velosonic.deeplink.DeepLinkParser
import de.ilazlow.velosonic.ui.AppRoute
import de.ilazlow.velosonic.ui.AppRootViewModel
import de.ilazlow.velosonic.ui.AppShell
import de.ilazlow.velosonic.ui.login.LoginScreen
import de.ilazlow.velosonic.ui.settings.AppearanceViewModel
import de.ilazlow.velosonic.ui.sync.SyncScreen
import de.ilazlow.velosonic.ui.theme.VeloSonicTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var deepLinkHolder: DeepLinkHolder
    @Inject lateinit var serverConfigDao: ServerConfigDao

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        requestNotificationPermissionIfNeeded()
        setContent {
            val appearanceViewModel: AppearanceViewModel = hiltViewModel()
            val appearance by appearanceViewModel.settings.collectAsStateWithLifecycle()
            VeloSonicTheme(dynamicColor = appearance.dynamicColorEnabled) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Android 13+ (API 33) treats POST_NOTIFICATIONS as a runtime permission — without this,
     *  the media session's playback notification (and every other notification) is silently
     *  suppressed by the OS even though the manifest declares the permission and the foreground
     *  service starts fine. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** `velosonic://host[:port]/type/id` links — see [DeepLinkParser]. Resolving which local
     *  server the link's host matches needs a DB read, hence the coroutine hop even though the
     *  rest of this is plain Intent plumbing. */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        lifecycleScope.launch {
            val configs = serverConfigDao.getAll()
            DeepLinkParser.parse(uri, configs)?.let { deepLinkHolder.set(it) }
        }
    }
}

/**
 * Mirrors MainView.swift's top-level branch: no server -> Login, server present but initial
 * sync not complete yet -> Syncing (SyncScreen), sync complete -> Ready (the main app shell).
 * Reactive to both Room tables it depends on, so login success and sync completion each
 * advance this automatically — no manual navigation call needed anywhere upstream.
 */
@Composable
fun AppRoot(viewModel: AppRootViewModel = hiltViewModel()) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    when (route) {
        is AppRoute.Loading -> Unit
        is AppRoute.Login -> LoginScreen()
        is AppRoute.Syncing -> SyncScreen(host = (route as AppRoute.Syncing).host)
        is AppRoute.Ready -> AppShell()
    }
}
