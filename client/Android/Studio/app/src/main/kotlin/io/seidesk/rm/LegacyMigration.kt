package io.seidesk.rm

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import io.seidesk.rm.security.CredentialVault
import java.io.File

/**
 * Migration from legacy 'xydesk.*' preferences, databases, vault IDs, and files
 * to modern 'seidesk.*' namespace.
 *
 * Safe & idempotent: runs once on first startup after update. Never wipes saved data.
 */
object LegacyMigration {
    private const val TAG = "LegacyMigration"
    const val MIGRATION_FLAG_KEY = "seidesk.migrated.v1"
    private const val NEW_APP_PREFS = "seidesk.app"
    private const val OLD_APP_PREFS = "xydesk.app"

    val PREFS_MAP = mapOf(
        "xydesk.app" to "seidesk.app",
        "xydesk.lang" to "seidesk.lang",
        "xydesk.input" to "seidesk.input",
        "xydesk.session" to "seidesk.session",
        "xydesk.remote.display" to "seidesk.remote.display",
        "xydesk.rdp.options" to "seidesk.rdp.options",
        "xydesk.clipboard" to "seidesk.clipboard",
        "xydesk.community.jokes" to "seidesk.community.jokes",
        "xydesk.splash" to "seidesk.splash",
        "xydesk.rdpfree" to "seidesk.rdpfree",
        "xydesk.certtrust" to "seidesk.certtrust",
        "xydesk_credential_vault" to "seidesk_credential_vault"
    )

    fun run(context: Context) {
        val appContext = context.applicationContext ?: context
        val appPrefs = appContext.getSharedPreferences(NEW_APP_PREFS, Context.MODE_PRIVATE)
        if (appPrefs.getBoolean(MIGRATION_FLAG_KEY, false)) {
            return
        }

        try {
            // 1. SharedPreferences files migration
            migratePrefs(appContext)

            // 2. Room database migration (xydesk.db + -wal + -shm)
            migrateDatabase(appContext)

            // 3. Vault ID migration
            migrateVault(appContext)

            // 4. Notification channels
            migrateNotificationChannels(appContext)

            // 5. Files (avatar & boot log)
            migrateFiles(appContext)

            // Mark migration complete
            appPrefs.edit().putBoolean(MIGRATION_FLAG_KEY, true).commit()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Legacy migration encountered error", e)
        }
    }

    fun migratePrefs(context: Context) {
        PREFS_MAP.forEach { (oldName, newName) ->
            val oldPrefs = context.getSharedPreferences(oldName, Context.MODE_PRIVATE)
            val newPrefs = context.getSharedPreferences(newName, Context.MODE_PRIVATE)

            val oldEntries = oldPrefs.all
            if (oldEntries.isNotEmpty() && newPrefs.all.isEmpty()) {
                val editor = newPrefs.edit()
                for ((key, value) in oldEntries) {
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>)
                        }
                    }
                    if (oldName == "xydesk.splash" && key == "xydesk_remote_voice_played" && value is Boolean) {
                        editor.putBoolean("seidesk_remote_voice_played", value)
                    }
                }
                editor.commit()

                try {
                    val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                    val oldFile = File(sharedPrefsDir, "$oldName.xml")
                    if (oldFile.exists()) oldFile.delete()
                    oldPrefs.edit().clear().commit()
                } catch (_: Throwable) {}
            }
        }
    }

    fun migrateDatabase(context: Context) {
        try {
            val oldDb = context.getDatabasePath("xydesk.db")
            val newDb = context.getDatabasePath("seidesk.db")
            if (oldDb.exists() && !newDb.exists()) {
                oldDb.renameTo(newDb)
                val oldWal = File(oldDb.path + "-wal")
                if (oldWal.exists()) oldWal.renameTo(File(newDb.path + "-wal"))
                val oldShm = File(oldDb.path + "-shm")
                if (oldShm.exists()) oldShm.renameTo(File(newDb.path + "-shm"))
            }
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "Database migration failed: ${e.message}")
        }
    }

    fun migrateVault(context: Context) {
        try {
            val vault = CredentialVault(context)
            val oldPat = vault.get("xyrdp-github-pat")
            if (oldPat != null && vault.get("seirdp-github-pat") == null) {
                vault.put("seirdp-github-pat", oldPat)
                vault.remove("xyrdp-github-pat")
            }
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "Vault migration failed: ${e.message}")
        }
    }

    fun migrateNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.deleteNotificationChannel("xy_online_music")
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Notification channel migration failed: ${e.message}")
            }
        }
    }

    fun migrateFiles(context: Context) {
        try {
            val filesDir = context.filesDir ?: return
            val legacyAvatar = File(filesDir, "xy_avatar.webp")
            val currentAvatar = File(filesDir, "sei_avatar.webp")
            if (legacyAvatar.exists() && !currentAvatar.exists()) {
                legacyAvatar.copyTo(currentAvatar, overwrite = false)
                legacyAvatar.delete()
            }

            val legacyBootLog = File(filesDir, "xydesk-boot.log")
            val currentBootLog = File(filesDir, "seidesk-boot.log")
            if (legacyBootLog.exists() && !currentBootLog.exists()) {
                legacyBootLog.copyTo(currentBootLog, overwrite = false)
                legacyBootLog.delete()
            }
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "Files migration failed: ${e.message}")
        }
    }
}
