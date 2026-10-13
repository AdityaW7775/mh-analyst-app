package com.thepagination.mhanalyst.data

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thepagination.mhanalyst.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class LoginStep {
    data class NeedCode(val ticket: String) : LoginStep()
    data class Done(val profile: Profile) : LoginStep()
}

/** Everything that talks to the website or the local database. */
class Repo(context: Context) {
    val db = LocalDb(context)
    private val tokens = TokenStore(context)
    private val prefs = context.getSharedPreferences("mh_state", Context.MODE_PRIVATE)
    private val api = Api(BuildConfig.BASE_URL) { tokens.token }

    fun savedProfile(): Profile? = prefs.getString("profile", null)?.let { runCatching { Profile.from(JSONObject(it)) }.getOrNull() }
        ?.takeIf { tokens.token != null }

    var company: String?
        get() = prefs.getString("company", null)
        set(v) { prefs.edit().putString("company", v).apply() }

    fun lastRefreshed(company: String): Long = prefs.getLong("synced_$company", 0L)

    private fun signedIn(j: JSONObject): Profile {
        tokens.token = j.getString("token")
        prefs.edit().putString("profile", j.toString()).apply()
        return Profile.from(j)
    }

    suspend fun login(username: String, password: String): LoginStep = withContext(Dispatchers.IO) {
        val j = api.call("login", body = JSONObject().put("username", username).put("password", password).put("device", Build.MANUFACTURER + " " + Build.MODEL))
        if (j.optBoolean("need_code")) LoginStep.NeedCode(j.getString("ticket")) else LoginStep.Done(signedIn(j))
    }

    suspend fun verify(ticket: String, code: String): Profile = withContext(Dispatchers.IO) {
        signedIn(api.call("verify", body = JSONObject().put("ticket", ticket).put("code", code)))
    }

    suspend fun refreshProfile(): Profile = withContext(Dispatchers.IO) {
        val j = api.call("me")
        prefs.edit().putString("profile", j.toString()).apply()
        Profile.from(j)
    }

    /** Downloads only what changed since the last download (everything the first time). Returns rows changed. */
    suspend fun refresh(company: String): Int = withContext(Dispatchers.IO) {
        var since = prefs.getString("cursor_$company", null)
        var after: String? = null
        var cursor: String? = null
        var wipe = since == null
        var changed = 0
        while (true) {
            val q = HashMap<String, String>()
            q["company"] = company
            since?.let { q["since"] = it }
            after?.let { q["after"] = it }
            val j = api.call("data", q)
            if (j.optBoolean("reset")) {
                since = null
                after = null
                wipe = true
                continue
            }
            if (cursor == null) cursor = j.getString("now")
            db.apply(company, j, wipe && after == null)
            for (k in listOf("vouchers", "ledgers", "items", "deleted")) changed += j.optJSONArray(k)?.length() ?: 0
            if (!j.optBoolean("more")) break
            after = j.getString("next")
        }
        prefs.edit().putString("cursor_$company", cursor).putLong("synced_$company", System.currentTimeMillis()).apply()
        changed
    }

    suspend fun sync(company: String, ask: Boolean): JSONObject = withContext(Dispatchers.IO) {
        api.call("sync", mapOf("company" to company), if (ask) JSONObject() else null)
    }

    suspend fun createVoucher(company: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        api.call("voucher", mapOf("company" to company), body)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { api.call("logout", body = JSONObject()) }
        tokens.token = null
        prefs.edit().clear().apply()
        db.wipeAll()
    }
}

/** App-wide state shared by all screens. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    val snackbar = SnackbarHostState()

    var profile by mutableStateOf(repo.savedProfile())
        private set
    var company by mutableStateOf(repo.company ?: profile?.companies?.firstOrNull()?.key ?: "")
        private set
    /** Bumped whenever new data lands, so screens re-read the local database. */
    var version by mutableIntStateOf(0)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var syncing by mutableStateOf(false)
        private set
    var hasData by mutableStateOf(company.isNotEmpty() && repo.db.hasData(company))
        private set
    private var lastRefresh = 0L

    val companyName: String get() = profile?.companies?.firstOrNull { it.key == company }?.name ?: ""

    fun message(text: String) {
        viewModelScope.launch { snackbar.showSnackbar(text) }
    }

    fun signedIn(p: Profile) {
        profile = p
        if (p.companies.none { it.key == company }) company = p.companies.firstOrNull()?.key ?: ""
        repo.company = company
        refresh(force = true)
    }

    fun switchCompany(key: String) {
        if (key == company) return
        company = key
        repo.company = key
        hasData = repo.db.hasData(key)
        version++
        refresh(force = true)
    }

    /** Quietly fetches what changed. Called on start, when the app comes back, and after a sync. */
    fun refresh(force: Boolean = false) {
        if (profile == null || company.isEmpty() || refreshing) return
        if (!force && System.currentTimeMillis() - lastRefresh < 30_000) return
        refreshing = true
        viewModelScope.launch {
            try {
                repo.refresh(company)
                lastRefresh = System.currentTimeMillis()
                hasData = repo.db.hasData(company)
                version++
                runCatching { profile = repo.refreshProfile() }
            } catch (e: ApiException) {
                if (e.code == 401) forceSignOut(e.message ?: "Please sign in again.")
                else if (force || !hasData) message(e.message ?: "Couldn't update.")
            } finally {
                refreshing = false
            }
        }
    }

    /** Sync button: ask the Tally PC for fresh data, wait for it, then download the changes. */
    fun syncNow() {
        if (syncing || company.isEmpty()) return
        syncing = true
        viewModelScope.launch {
            try {
                val first = repo.sync(company, ask = true)
                if (!first.optBoolean("online")) {
                    message("The Tally PC is offline. Showing the latest data on the website.")
                } else {
                    val baseline = first.optString("last_sync")
                    message("Asked the Tally PC for the latest data…")
                    val started = System.currentTimeMillis()
                    while (System.currentTimeMillis() - started < 180_000) {
                        delay(3000)
                        val st = runCatching { repo.sync(company, ask = false) }.getOrNull() ?: continue
                        if (st.optString("last_sync").isNotEmpty() && st.optString("last_sync") != baseline) break
                    }
                }
                repo.refresh(company)
                lastRefresh = System.currentTimeMillis()
                hasData = repo.db.hasData(company)
                version++
                message("Up to date.")
            } catch (e: ApiException) {
                if (e.code == 401) forceSignOut(e.message ?: "Please sign in again.") else message(e.message ?: "Couldn't sync.")
            } finally {
                syncing = false
            }
        }
    }

    fun afterVoucherSaved() {
        refresh(force = true)
    }

    private fun forceSignOut(reason: String) {
        viewModelScope.launch {
            repo.signOut()
            profile = null
            hasData = false
            version++
            message(reason)
        }
    }

    fun signOut() = forceSignOut("Signed out. Data saved on this phone was removed.")
}
