package com.tegami

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tegami.proof.StatementCodec
import com.tegami.ui.IncomingLinks
import com.tegami.ui.LocalHostActivity
import com.tegami.ui.SessionViewModel
import com.tegami.ui.TegamiRoot
import com.tegami.ui.theme.TegamiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var links: IncomingLinks
    @Inject lateinit var metaMask: com.tegami.data.MetaMaskRepository
    private val session: SessionViewModel by viewModels()
    private val linkCount = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // never held open: data loads inside the app
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)

        // Screenshot / recents protection follows the user's setting (on by default).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                session.secureScreen.collect { on ->
                    if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            val reduced by session.reducedMotion.collectAsStateWithLifecycle()
            val links by linkCount
            TegamiTheme(reducedMotion = reduced) {
                CompositionLocalProvider(LocalHostActivity provides this) {
                    TegamiRoot(session, incomingLinks = links)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /**
     * Accepts only tegami://verify?s=<token> (well-formed, size-capped) and tegami://metamask replies to the open
     * MetaMask request; anything else is ignored.
     */
    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != "tegami") return
        if (uri.host == "metamask") { metaMask.deliver(uri); return }
        if (uri.host != "verify") return
        val token = uri.getQueryParameter("s") ?: return
        if (StatementCodec.decode(token).isFailure) return
        links.pending.value = token
        linkCount.intValue++
    }
}
