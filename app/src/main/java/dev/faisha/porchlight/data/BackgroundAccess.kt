package dev.faisha.porchlight.data

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import java.io.IOException

/**
 * The app's Battery setting (Settings → Apps → Porchlight → Battery). One UI uses the same
 * three options as stock Android 12+, which map onto two flags: Unrestricted is
 * PowerManager.isIgnoringBatteryOptimizations, Restricted is ActivityManager.isBackgroundRestricted,
 * and Optimised (the default) is neither.
 */
enum class BatteryMode(val label: String) {
    UNRESTRICTED("Unrestricted"),
    OPTIMIZED("Optimised"),
    RESTRICTED("Restricted"),
}

/** A setting that can stop Android letting Porchlight use the network in the background. */
enum class BackgroundBlock { BATTERY, DATA_SAVER }

/**
 * Whether Android lets Porchlight reach Home Assistant in the background. When it doesn't, the
 * network looks absent to the app, so a widget refresh fails with [NoNetworkException].
 */
object BackgroundAccess {

    fun batteryMode(context: Context): BatteryMode {
        val activity = context.getSystemService(ActivityManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        return when {
            activity?.isBackgroundRestricted == true -> BatteryMode.RESTRICTED
            power?.isIgnoringBatteryOptimizations(context.packageName) == true -> BatteryMode.UNRESTRICTED
            else -> BatteryMode.OPTIMIZED
        }
    }

    /** Data Saver is on and Porchlight isn't on its "unrestricted data" list. Only bites on metered networks. */
    fun dataSaverBlocking(context: Context): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.restrictBackgroundStatus ==
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED

    /** The setting to change first, or null when nothing is in the way. Battery comes first. */
    fun block(mode: BatteryMode, dataSaver: Boolean): BackgroundBlock? = when {
        mode != BatteryMode.UNRESTRICTED -> BackgroundBlock.BATTERY
        dataSaver -> BackgroundBlock.DATA_SAVER
        else -> null
    }

    /**
     * The setting that explains failure [e], or null when something else does. A blocked network
     * looks like no network at all ([NoNetworkException]). Restricted also stops requests partway,
     * so it explains any network failure, as it did before.
     */
    fun explains(e: Throwable, mode: BatteryMode, dataSaver: Boolean): BackgroundBlock? {
        if (e !is IOException || e is HaHttpException || e is InsecureUrlException) return null
        return when {
            mode == BatteryMode.RESTRICTED -> BackgroundBlock.BATTERY
            e !is NoNetworkException -> null
            mode == BatteryMode.OPTIMIZED -> BackgroundBlock.BATTERY
            dataSaver -> BackgroundBlock.DATA_SAVER
            else -> null
        }
    }

    /**
     * Opens the setting that lifts [block]. For Optimised, the system "let app always run in the
     * background?" dialog, which sets Unrestricted in one tap. It isn't documented to lift
     * Restricted, so that opens App info, where Battery is one tap away.
     */
    @SuppressLint("BatteryLife") // Fine for a sideloaded app; Play limits this intent to some app types.
    fun fixIntent(context: Context, block: BackgroundBlock, mode: BatteryMode): Intent {
        val pkg = Uri.fromParts("package", context.packageName, null)
        val action = when {
            block == BackgroundBlock.DATA_SAVER -> Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS
            mode == BatteryMode.OPTIMIZED -> Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            else -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
        return Intent(action, pkg)
    }

    /** App info: the fallback when a phone has no screen for [fixIntent]. */
    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}
