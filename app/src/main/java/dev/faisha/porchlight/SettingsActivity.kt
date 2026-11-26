package dev.faisha.porchlight

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import dev.faisha.porchlight.data.BackgroundAccess
import dev.faisha.porchlight.data.BackgroundBlock
import dev.faisha.porchlight.data.BatteryMode
import dev.faisha.porchlight.data.ConfigStore
import dev.faisha.porchlight.data.HaClient
import dev.faisha.porchlight.data.HaConfig
import dev.faisha.porchlight.data.UrlPolicy
import dev.faisha.porchlight.widget.RefreshScheduler
import dev.faisha.porchlight.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The main screen: the Home Assistant connection. The gear opens [AppSettingsActivity]. */
class SettingsActivity : Activity() {

    private enum class Tone { PLAIN, OK, ERROR }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var store: ConfigStore

    /** Refresh the widget once the background banner clears, since its last refresh likely failed. */
    private var refreshWhenUnblocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = ConfigStore(this)

        val local = findViewById<EditText>(R.id.local_url)
        val remote = findViewById<EditText>(R.id.remote_url)
        val token = findViewById<EditText>(R.id.token)
        val status = findViewById<TextView>(R.id.status)
        val save = findViewById<Button>(R.id.save)
        val httpWarning = findViewById<TextView>(R.id.http_warning)

        findViewById<View>(R.id.open_settings).setOnClickListener {
            startActivity(Intent(this, AppSettingsActivity::class.java))
        }

        // Labels plain http as the risk it is, whenever either URL uses it.
        val updateHttpWarning = {
            val http = listOf(local, remote).any { UrlPolicy.isPlainHttp(it.text.toString()) }
            httpWarning.visibility = if (http) View.VISIBLE else View.GONE
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updateHttpWarning()
        }
        local.addTextChangedListener(watcher)
        remote.addTextChangedListener(watcher)

        var existing = store.load()
        existing?.let {
            local.setText(it.localUrl)
            remote.setText(it.remoteUrl.orEmpty())
            token.hint = "Saved · leave blank to keep it"
        }
        updateHttpWarning()

        save.setOnClickListener {
            val localUrl = local.text.toString().trim()
            val remoteUrl = remote.text.toString().trim().ifBlank { null }
            // The saved token is only reused for the saved URLs, so nobody with the phone in hand
            // can point the app at their own server and have it send them the token.
            val saved = existing
            val urlsChanged = saved != null &&
                (localUrl != saved.localUrl || remoteUrl != saved.remoteUrl)
            val tokenValue = token.text.toString().trim()
                .ifBlank { if (urlsChanged) "" else saved?.token.orEmpty() }

            val problem = UrlPolicy.problem(localUrl, "Local")
                ?: remoteUrl?.let { UrlPolicy.problem(it, "Remote") }
                ?: when {
                    tokenValue.isNotBlank() -> null
                    urlsChanged -> "A URL changed, so enter the token again"
                    else -> "Token is required"
                }
            if (problem != null) {
                showStatus(status, problem, Tone.ERROR)
                return@setOnClickListener
            }

            val config = HaConfig(localUrl, remoteUrl, tokenValue)
            save.isEnabled = false
            showStatus(status, "Testing…", Tone.PLAIN)
            scope.launch {
                try {
                    val message = HaClient(this@SettingsActivity, config).ping()
                    store.save(config)
                    existing = config
                    token.setText("")
                    token.hint = "Saved · leave blank to keep it"
                    showStatus(status, "Saved. Home Assistant says: $message", Tone.OK)
                    RefreshScheduler.schedulePeriodic(this@SettingsActivity)
                    WidgetRefresher.refreshAll(this@SettingsActivity)
                } catch (e: Exception) {
                    showStatus(status, "Couldn't connect (${WidgetRefresher.describe(e)}): ${e.message}", Tone.ERROR)
                } finally {
                    save.isEnabled = true
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra(EXTRA_FIX_BACKGROUND, false)) {
            intent.removeExtra(EXTRA_FIX_BACKGROUND)
            refreshWhenUnblocked = true
        }
        val blocked = showBackgroundBanner()
        if (blocked) {
            refreshWhenUnblocked = true
        } else if (refreshWhenUnblocked) {
            refreshWhenUnblocked = false
            if (store.load() != null) RefreshScheduler.refreshNow(this)
        }
    }

    /**
     * Shows the banner while the Battery setting or Data Saver stops the widget refreshing in the
     * background. Checked on every resume, so it clears after a trip to Settings. True if shown.
     */
    private fun showBackgroundBanner(): Boolean {
        val banner = findViewById<View>(R.id.background_banner)
        val mode = BackgroundAccess.batteryMode(this)
        val block = BackgroundAccess.block(mode, BackgroundAccess.dataSaverBlocking(this))
        if (block == null) {
            banner.visibility = View.GONE
            return false
        }
        val (text, button) = when {
            block == BackgroundBlock.DATA_SAVER ->
                "Data Saver is blocking Porchlight's background data, so the widget can't update on mobile data. " +
                    "Allow Porchlight unrestricted data." to "Open Data Saver setting"
            mode == BatteryMode.OPTIMIZED ->
                "Battery is set to ${mode.label}, so Android can cut off Porchlight's network in the background " +
                    "and the widget stops updating. Set it to Unrestricted." to "Set battery to Unrestricted"
            else ->
                "Battery is set to ${mode.label}, so the widget can't update in the background. " +
                    "In App info, tap Battery and choose Unrestricted." to "Open App info"
        }
        findViewById<TextView>(R.id.background_banner_text).text = text
        findViewById<Button>(R.id.background_fix).apply {
            this.text = button
            setOnClickListener { openSetting(BackgroundAccess.fixIntent(this@SettingsActivity, block, mode)) }
        }
        banner.visibility = View.VISIBLE
        return true
    }

    /** Some phones lack a given settings screen; App info is always there. */
    private fun openSetting(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(BackgroundAccess.appInfoIntent(this))
        }
    }

    /** Plain grey text while testing; a green or red banner for the result. */
    private fun showStatus(view: TextView, text: String, tone: Tone) {
        val (background, color, icon) = when (tone) {
            Tone.PLAIN -> Triple(0, R.color.app_sub, 0)
            Tone.OK -> Triple(R.drawable.bg_banner_ok, R.color.app_ok, R.drawable.ic_check)
            Tone.ERROR -> Triple(R.drawable.bg_banner_error, R.color.app_error, R.drawable.ic_warning)
        }
        val tint = getColor(color)
        val padH = if (tone == Tone.PLAIN) 0 else dp(12)
        val padV = if (tone == Tone.PLAIN) 0 else dp(10)
        view.apply {
            this.text = text
            visibility = View.VISIBLE
            setBackgroundResource(background)
            setTextColor(tint)
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(tint)
            setPaddingRelative(padH, padV, padH, padV)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Set when the widget's "Battery restricted" or "Data Saver on" error opened this screen. */
        const val EXTRA_FIX_BACKGROUND = "dev.faisha.porchlight.FIX_BACKGROUND"
    }
}
