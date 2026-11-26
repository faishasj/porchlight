package dev.faisha.porchlight.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class HaConfig(
    val localUrl: String,
    val remoteUrl: String?,   // optional fallback, e.g. Nabu Casa
    val token: String,
)

/**
 * Stores URLs in plain SharedPreferences and the long-lived token encrypted with an
 * AES-GCM key held in the Android Keystore (the key never leaves secure hardware).
 */
class ConfigStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ha_config", Context.MODE_PRIVATE)

    fun load(): HaConfig? {
        val local = prefs.getString(KEY_LOCAL, null) ?: return null
        val encToken = prefs.getString(KEY_TOKEN, null) ?: return null
        val token = runCatching { decrypt(encToken) }.getOrNull() ?: return null
        return HaConfig(
            localUrl = local,
            remoteUrl = prefs.getString(KEY_REMOTE, null)?.takeIf { it.isNotBlank() },
            token = token,
        )
    }

    fun save(config: HaConfig) {
        prefs.edit()
            .putString(KEY_LOCAL, config.localUrl)
            .putString(KEY_REMOTE, config.remoteUrl.orEmpty())
            .putString(KEY_TOKEN, encrypt(config.token))
            .apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
        val ct = bytes.copyOfRange(GCM_IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "porchlight_token_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val KEY_LOCAL = "local_url"
        const val KEY_REMOTE = "remote_url"
        const val KEY_TOKEN = "token"
    }
}
