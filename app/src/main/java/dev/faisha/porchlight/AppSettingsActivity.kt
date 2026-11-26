package dev.faisha.porchlight

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import dev.faisha.porchlight.data.ThemeMode
import dev.faisha.porchlight.data.ThemeStore
import dev.faisha.porchlight.widget.WidgetRefresher

/** The Settings page, opened from the gear on the main screen. For now it holds Appearance > Theme. */
class AppSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)

        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val buttons = mapOf(
            ThemeMode.SYSTEM to R.id.theme_system,
            ThemeMode.LIGHT to R.id.theme_light,
            ThemeMode.DARK to R.id.theme_dark,
        )
        val group = findViewById<RadioGroup>(R.id.theme)
        group.check(buttons.getValue(ThemeStore.get(this)))
        group.setOnCheckedChangeListener { _, checkedId ->
            val mode = buttons.entries.firstOrNull { it.value == checkedId }?.key ?: return@setOnCheckedChangeListener
            if (mode != ThemeStore.get(this)) {
                ThemeStore.set(this, mode)
                WidgetRefresher.redrawForTheme(this, mode)
            }
        }

        val version = packageManager.getPackageInfo(packageName, 0).versionName
        findViewById<TextView>(R.id.version).text = "Porchlight $version · MIT licence"
    }
}
