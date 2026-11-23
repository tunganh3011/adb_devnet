package com.devnet.adbcompanion.data

import android.content.Context
import androidx.core.content.edit
import com.devnet.adbcompanion.domain.model.ThemeMode

class PreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var targetHost: String
        get() = prefs.getString(KEY_TARGET_HOST, DEFAULT_TARGET_HOST).orEmpty()
        set(value) = prefs.edit { putString(KEY_TARGET_HOST, value.trim()) }

    var persistentNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_NOTIFICATION, false)
        set(value) = prefs.edit { putBoolean(KEY_PERSISTENT_NOTIFICATION, value) }

    var floatingHudEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_HUD, false)
        set(value) = prefs.edit { putBoolean(KEY_FLOATING_HUD, value) }

    var themeMode: ThemeMode
        get() {
            val savedMode = prefs.getString(KEY_THEME_MODE, null)
            return ThemeMode.entries.firstOrNull { it.name == savedMode } ?: ThemeMode.DARK
        }
        set(value) = prefs.edit { putString(KEY_THEME_MODE, value.name) }

    var savedPrivateDnsHost: String
        get() = prefs.getString(KEY_PRIVATE_DNS_HOST, DEFAULT_PRIVATE_DNS_HOST)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_PRIVATE_DNS_HOST
        set(value) {
            val clean = value.trim()
            if (clean.isNotEmpty()) {
                prefs.edit { putString(KEY_PRIVATE_DNS_HOST, clean) }
            }
        }

    companion object {
        private const val PREFS_NAME = "devnet_prefs"
        private const val KEY_TARGET_HOST = "target_host"
        private const val KEY_PERSISTENT_NOTIFICATION = "persistent_notification"
        private const val KEY_FLOATING_HUD = "floating_hud"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PRIVATE_DNS_HOST = "private_dns_host"
        private const val DEFAULT_TARGET_HOST = ""
        private const val DEFAULT_PRIVATE_DNS_HOST = "dns.adguard-dns.com"
    }
}
