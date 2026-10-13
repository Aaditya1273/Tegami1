package com.tegami.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tegami.guardian.GuardianAction
import com.tegami.ui.components.BottomBar
import com.tegami.ui.components.Tab
import com.tegami.ui.guardian.GuardianScreen
import com.tegami.ui.guardian.GuardianViewModel
import com.tegami.ui.home.HomeScreen
import com.tegami.ui.letters.LetterDetailScreen
import com.tegami.ui.letters.LetterFilter
import com.tegami.ui.letters.LettersScreen
import com.tegami.ui.proofs.ProofsScreen
import com.tegami.ui.proofs.ProofsViewModel
import com.tegami.ui.proofs.VerifyScreen
import com.tegami.ui.proofs.VerifyViewModel
import com.tegami.ui.settings.SettingsScreen
import com.tegami.ui.theme.LocalReducedMotion
import com.tegami.ui.theme.Motion
import com.tegami.ui.theme.Tegami
import com.tegami.ui.welcome.WelcomeScreen

private object Routes {
    const val WELCOME = "welcome"
    const val LETTER = "letter/{sig}"
    const val LETTERS = "letters?filter={filter}"
    const val PROOFS = "proofs?amount={amount}&from={from}&to={to}&verifier={verifier}"
    const val VERIFY = "verify"
    const val GUARDIAN = "guardian?explain={explain}"
    fun letter(sig: String) = "letter/$sig"
    fun proofs(amount: String? = null, from: String? = null, to: String? = null, verifier: String? = null) = buildString {
        append("proofs?")
        append(listOfNotNull(amount?.let { "amount=${Uri.encode(it)}" }, from?.let { "from=$it" }, to?.let { "to=$it" },
            verifier?.let { "verifier=${Uri.encode(it)}" }).joinToString("&"))
    }
}

private fun routeOf(tab: Tab) = when (tab) {
    Tab.Home -> "home"; Tab.Letters -> "letters"; Tab.Proofs -> "proofs"; Tab.Guardian -> "guardian"; Tab.Settings -> "settings"
}

private fun tabOf(route: String?): Tab? = when (route?.substringBefore('?')) {
    "home" -> Tab.Home; "letters" -> Tab.Letters; "proofs" -> Tab.Proofs; "guardian" -> Tab.Guardian; "settings" -> Tab.Settings
    else -> null
}

private fun NavHostController.toTab(tab: Tab) = navigate(routeOf(tab)) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TegamiRoot(session: SessionViewModel, incomingLinks: Int) {
    val nav = rememberNavController()
    val account by session.account.collectAsStateWithLifecycle()
    val letters by session.letters.collectAsStateWithLifecycle()
    val hide by session.hideAmounts.collectAsStateWithLifecycle()
    val connecting by session.connecting.collectAsStateWithLifecycle()
    val activity = LocalHostActivity.current
    val snackbar = remember { SnackbarHostState() }
    val reduced = LocalReducedMotion.current
    val c = Tegami.colors
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = tabOf(entry?.destination?.route)
    val config = session.config

    LaunchedEffect(Unit) { session.messages.collect { snackbar.showSnackbar(it) } }
    // Losing the account (disconnect) returns to the welcome screen; gaining one leaves it.
    LaunchedEffect(account?.address) {
        val onWelcome = nav.currentDestination?.route == Routes.WELCOME
        if (account == null && !onWelcome && nav.currentDestination?.route != Routes.VERIFY) {
            nav.navigate(Routes.WELCOME) { popUpTo(0) }
        } else if (account != null && onWelcome) {
            nav.navigate("home") { popUpTo(0) }
        }
    }
    LaunchedEffect(incomingLinks) { if (incomingLinks > 0) nav.navigate(Routes.VERIFY) { launchSingleTop = true } }

    val connect = { activity?.let { session.connect(it) }; Unit }
    val signIn = { activity?.let { session.connect(it, existing = true) }; Unit }
    val message = { m: String -> session.message(m) }

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize()) {
            NavHost(
                nav, startDestination = if (account == null) Routes.WELCOME else "home",
                modifier = Modifier.weight(1f).statusBarsPadding(),
                enterTransition = { if (reduced) fadeIn(tween(Motion.micro)) else fadeIn(tween(Motion.screen)) + slideInHorizontally(tween(Motion.screen, easing = Motion.Standard)) { it / 12 } },
                exitTransition = { fadeOut(tween(Motion.micro)) },
                popEnterTransition = { fadeIn(tween(if (reduced) Motion.micro else Motion.screen)) },
                popExitTransition = { if (reduced) fadeOut(tween(Motion.micro)) else fadeOut(tween(Motion.micro)) + slideOutHorizontally(tween(Motion.screen)) { it / 12 } },
            ) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(config, connecting, onConnect = connect, onSignIn = signIn, onMetaMask = { session.connectMetaMask() }, onWatch = { session.watch(it) },
                        onDemo = { session.watchDemo() }, onVerify = { nav.navigate(Routes.VERIFY) })
                }
                composable("home") {
                    val a = account ?: return@composable
                    HomeScreen(config, a, letters, hide,
                        onToggleHide = { session.setHideAmounts(!hide) }, onRefresh = session::refresh,
                        onOpenLetter = { nav.navigate(Routes.letter(it)) }, onSeeAll = { nav.toTab(Tab.Letters) },
                        onReview = { nav.navigate("letters?filter=${LetterFilter.Attention.name}") },
                        onPrepareStatement = { nav.toTab(Tab.Proofs) }, onConnectWallet = signIn,
                        onPay = { nav.navigate("pay") }, onMessage = message)
                }
                composable(Routes.LETTERS, arguments = listOf(navArgument("filter") { type = NavType.StringType; nullable = true })) { e ->
                    val initial = e.arguments?.getString("filter")?.let { f -> LetterFilter.entries.firstOrNull { it.name == f } } ?: LetterFilter.All
                    LettersScreen(config, letters, hide, initial, onRefresh = session::refresh, onLoadMore = { session.loadMore() },
                        onOpen = { nav.navigate(Routes.letter(it)) })
                }
                composable(Routes.LETTER, arguments = listOf(navArgument("sig") { type = NavType.StringType })) { e ->
                    val sig = e.arguments?.getString("sig").orEmpty()
                    LetterDetailScreen(config, letters.letters.firstOrNull { it.id == sig }, account?.canSign == true,
                        onBack = { nav.popBackStack() },
                        onAskGuardian = { nav.navigate("guardian?explain=$it") },
                        onUseInStatement = { nav.navigate(Routes.proofs()) { launchSingleTop = true } },
                        onMessage = message)
                }
                composable(Routes.PROOFS, arguments = listOf("amount", "from", "to", "verifier").map { n ->
                    navArgument(n) { type = NavType.StringType; nullable = true }
                }) {
                    val a = account ?: return@composable
                    val vm: ProofsViewModel = hiltViewModel()
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    val disclosures by session.disclosures.collectAsStateWithLifecycle()
                    ProofsScreen(config, a, ui, disclosures, letters.loading && letters.letters.isEmpty(),
                        onAmount = vm::setAmount, onVerifier = vm::setVerifier, onPreset = vm::setPreset, onValidity = vm::setValidity,
                        onReview = vm::review, onEdit = vm::backToEdit, onSign = { activity?.let(vm::sign) }, onShared = vm::recordShared,
                        onStartOver = vm::startOver,
                        onVerifyOwn = { link -> session.openStatement(link); nav.navigate(Routes.VERIFY) },
                        onOpenVerifier = { nav.navigate(Routes.VERIFY) }, onConnectWallet = connect, onMessage = message)
                }
                composable("pay") {
                    val a = account ?: return@composable
                    val vm: com.tegami.ui.pay.PayViewModel = hiltViewModel()
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    com.tegami.ui.pay.PayScreen(config, a.address, a.kind == com.tegami.data.Account.Kind.MetaMask, ui, onTo = vm::setTo, onAmount = vm::setAmount,
                        onInvoice = vm::setInvoice, onNote = vm::setNote, onSend = { activity?.let(vm::send) },
                        onLoad = vm::loadBalances, onAnother = vm::reset, onBack = { nav.popBackStack() })
                }
                composable(Routes.VERIFY) {
                    val vm: VerifyViewModel = hiltViewModel()
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    VerifyScreen(config, ui, onInput = vm::setInput, onVerify = vm::verify, onBack = {
                        if (!nav.popBackStack()) nav.navigate(if (account == null) Routes.WELCOME else "home")
                    })
                }
                composable(Routes.GUARDIAN, arguments = listOf(navArgument("explain") { type = NavType.StringType; nullable = true })) { e ->
                    val vm: GuardianViewModel = hiltViewModel()
                    val turns by vm.turns.collectAsStateWithLifecycle()
                    val explain = e.arguments?.getString("explain")
                    LaunchedEffect(explain) { if (explain != null) { vm.explain(explain); e.arguments?.remove("explain") } }
                    GuardianScreen(turns, onAsk = vm::ask, onResolve = vm::resolve,
                        onOpenStatementDraft = { a: GuardianAction.PrepareIncomeStatement ->
                            nav.navigate(Routes.proofs(a.threshold?.decimal()?.toPlainString(), a.from.toString(), a.to.toString(), a.verifier.ifEmpty { null }))
                        },
                        onEvidenceShared = vm::recordEvidenceShared)
                }
                composable("settings") {
                    val a = account ?: return@composable
                    val mint by session.mint.collectAsStateWithLifecycle()
                    val secure by session.secureScreen.collectAsStateWithLifecycle()
                    val motion by session.reducedMotion.collectAsStateWithLifecycle()
                    val disclosures by session.disclosures.collectAsStateWithLifecycle()
                    SettingsScreen(config, a, mint, secure, hide, motion, disclosures.size,
                        letters.letters.size, letters.letters.count { it.seal is com.tegami.domain.Seal.Sealed },
                        onLoadMint = { session.loadMint() }, onSecureScreen = session::setSecureScreen, onHideAmounts = session::setHideAmounts,
                        onReducedMotion = session::setReducedMotion, onClearDisclosures = session::clearDisclosures,
                        onConnectWallet = connect, onDisconnect = session::disconnect, onMessage = message)
                }
            }
            // Hidden while typing, so the keyboard never pushes the tab bar over the form's buttons.
            if (currentTab != null && account != null && !WindowInsets.isImeVisible) BottomBar(currentTab) { nav.toTab(it) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp)) {
            Snackbar(it, containerColor = c.ink, contentColor = c.canvas)
        }
    }
}
