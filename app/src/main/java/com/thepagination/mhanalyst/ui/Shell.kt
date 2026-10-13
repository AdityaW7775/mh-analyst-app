package com.thepagination.mhanalyst.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thepagination.mhanalyst.BuildConfig
import com.thepagination.mhanalyst.R
import com.thepagination.mhanalyst.data.ApiException
import com.thepagination.mhanalyst.data.AppViewModel
import com.thepagination.mhanalyst.data.LoginStep
import com.thepagination.mhanalyst.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MhTheme { App() } }
    }
}

/** Reads from the phone's database off the main thread; keeps the last result while reloading. */
@Composable
fun <T> load(vararg keys: Any?, block: () -> T): T? {
    val state = produceState<T?>(initialValue = null, *keys) { value = withContext(Dispatchers.IO) { block() } }
    return state.value
}

fun NavHostController.open(route: String, arg: String) = navigate(route + "/" + Uri.encode(arg))

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home),
    Tab("daybook", "Day Book", Icons.Outlined.CalendarMonth),
    Tab("vouchers", "Vouchers", Icons.Outlined.Receipt),
    Tab("ledgers", "Ledgers", Icons.Outlined.People),
    Tab("stock", "Stock", Icons.Outlined.Inventory2),
)

@Composable
fun App(vm: AppViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    Scaffold(containerColor = mh.bg, snackbarHost = { SnackbarHost(vm.snackbar) }) { pad ->
        Box(Modifier.padding(pad)) {
            if (vm.profile == null) LoginScreen(vm) else MainShell(vm)
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val onTab = tabs.any { it.route == route }
    val activity = LocalContext.current as? Activity
    // Back on any main tab closes the app; inside a voucher, ledger or item it returns to the list.
    BackHandler(enabled = onTab) { activity?.finish() }

    Scaffold(
        containerColor = mh.bg,
        bottomBar = {
            if (onTab) NavigationBar(containerColor = mh.panel, tonalElevation = 0.dp) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = mh.ink, selectedIconColor = mh.bg, selectedTextColor = mh.ink,
                            unselectedIconColor = mh.muted, unselectedTextColor = mh.muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(vm, nav) }
            composable("daybook") { DayBookScreen(vm, nav) }
            composable("vouchers") { VouchersScreen(vm, nav) }
            composable("ledgers") { LedgersScreen(vm, nav) }
            composable("stock") { StockScreen(vm, nav) }
            composable("voucher/{g}") { VoucherScreen(vm, nav, it.arguments?.getString("g") ?: "") }
            composable("ledger/{g}") { LedgerScreen(vm, nav, it.arguments?.getString("g") ?: "") }
            composable("item/{g}") { ItemScreen(vm, nav, it.arguments?.getString("g") ?: "") }
            composable("web") { WebVouchersScreen(vm, nav) }
            composable("new") { NewVoucherScreen(vm, nav) }
            composable("account") { AccountScreen(vm, nav) }
        }
    }
}

/** Page header: title, company, sync icon, new voucher and account buttons. */
@Composable
fun Header(title: String, vm: AppViewModel, nav: NavHostController, back: Boolean = false, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(2.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = if (back) 22.sp else 28.sp, fontWeight = FontWeight.Bold, color = mh.ink, maxLines = 2, letterSpacing = (-0.5).sp)
            Text(subtitle ?: vm.companyName, fontSize = 13.sp, color = mh.muted, maxLines = 1)
        }
        val busy = vm.syncing || vm.refreshing
        val angle = if (busy) {
            val t = rememberInfiniteTransition(label = "sync")
            t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle").value
        } else 0f
        RoundIcon(Icons.Outlined.Sync, "Get the latest data from Tally", Modifier.rotate(angle)) { vm.syncNow() }
        if (!back && vm.profile?.canCreate == true) RoundIcon(Icons.Outlined.Add, "New voucher", filled = true) { nav.navigate("new") }
        if (!back) RoundIcon(Icons.Outlined.Person, "Account") { nav.navigate("account") }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, iconModifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 6.dp).size(42.dp).clip(RoundedCornerShape(14.dp))
            .background(if (filled) mh.accent else mh.panel).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (filled) androidx.compose.ui.graphics.Color.White else mh.ink, modifier = iconModifier) }
}

/** A scrolling page with the header on top. */
@Composable
fun Page(title: String, vm: AppViewModel, nav: NavHostController, back: Boolean = false, subtitle: String? = null, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(title, vm, nav, back, subtitle) }
        if (!vm.hasData && !back) item {
            DCard {
                Text(if (vm.refreshing) "Downloading your Tally data to this phone…" else "No data on this phone yet. Tap the sync icon to download it.", color = mh.muted)
                if (vm.refreshing) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        content()
    }
}

// ---------- sign in ----------

@Composable
private fun LoginScreen(vm: AppViewModel) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var ticket by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(mh.darkCard).verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        Image(painterResource(R.mipmap.ic_launcher), contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("MH Analyst", color = androidx.compose.ui.graphics.Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Your Tally data, on your phone.", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
        Spacer(Modifier.height(28.dp))
        DCard {
            if (ticket == null) {
                Text("Sign in", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = mh.ink)
                OutlinedTextField(username, { username = it.trim() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            } else {
                Text("Authenticator code", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = mh.ink)
                Sub("Enter the 6-digit code from Google or Microsoft Authenticator, or one of your recovery codes.")
                OutlinedTextField(code, { code = it.take(11) }, label = { Text("Code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            error?.let { Text(it, color = mh.orange, fontSize = 14.sp) }
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val t = ticket
                            if (t == null) {
                                when (val step = vm.repo.login(username, password)) {
                                    is LoginStep.NeedCode -> ticket = step.ticket
                                    is LoginStep.Done -> vm.signedIn(step.profile)
                                }
                            } else {
                                vm.signedIn(vm.repo.verify(t, code))
                            }
                        } catch (e: ApiException) {
                            error = e.message
                            if (ticket != null && e.code == 401 && (e.message ?: "").contains("too long")) ticket = null
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && (if (ticket == null) username.isNotBlank() && password.isNotEmpty() else code.length >= 6),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mh.accent),
            ) { Text(if (busy) "Please wait…" else if (ticket == null) "Continue" else "Sign in") }
            if (ticket != null) TextButton(onClick = { ticket = null; code = ""; error = null }) { Text("Use a different account") }
        }
        Spacer(Modifier.height(16.dp))
        Text("First time? Sign in on the website once to set up your authenticator.", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}

// ---------- account ----------

@Composable
private fun AccountScreen(vm: AppViewModel, nav: NavHostController) {
    val p = vm.profile ?: return
    val context = LocalContext.current
    Page("Account", vm, nav, back = true, subtitle = p.name + " · " + p.role.replaceFirstChar { it.uppercase() }) {
        item {
            DCard {
                CardTitle("Company")
                p.companies.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (c.key == vm.company) mh.panel2 else mh.panel)
                            .clickable { vm.switchCompany(c.key); nav.popBackStack() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.name, color = mh.ink, fontWeight = if (c.key == vm.company) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        if (c.key == vm.company) StatusTag("Showing", mh.ink, mh.bg)
                    }
                }
            }
        }
        item {
            DCard(onClick = { nav.navigate("web") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = mh.ink)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Website vouchers", color = mh.ink, fontWeight = FontWeight.SemiBold)
                        Sub("Vouchers made on the website or this app, and whether they reached Tally")
                    }
                }
            }
        }
        item {
            DCard {
                CardTitle("This phone")
                val last = vm.repo.lastRefreshed(vm.company)
                Sub(if (last > 0) "Data last updated " + Fmt.date(java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()) +
                    " at " + java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0).toString().take(5) else "Not downloaded yet")
                Sub("Colours follow your phone's light or dark setting. Users, settings and the audit log are on the website.")
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.BASE_URL))) }, shape = RoundedCornerShape(12.dp)) { Text("Open the website") }
            }
        }
        item {
            Button(
                onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mh.panel, contentColor = mh.orange),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign out and remove data from this phone")
            }
        }
        item { Text("MH Analyst " + BuildConfig.VERSION_NAME, color = mh.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}
