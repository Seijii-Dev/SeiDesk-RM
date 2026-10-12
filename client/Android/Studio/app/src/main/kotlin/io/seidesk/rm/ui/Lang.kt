package io.seidesk.rm.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

/**
 * App language configuration: English (US) only.
 */
enum class SeiLang(val code: String, val label: String) {
    EN("en", "English"),
    ;

    companion object {
        fun fromSystem(context: Context): SeiLang = EN
    }
}

typealias XyLang = SeiLang

/**
 * String lookup table for official key-based UI strings (US English).
 */
object SeiText {
    private val langState = mutableStateOf(SeiLang.EN)

    fun setLang(value: SeiLang) {
        langState.value = value
    }

    fun current(): SeiLang = langState.value

    fun t(key: String): String = table[key] ?: key

    fun t(key: String, vararg args: Any?): String {
        var out = t(key)
        args.forEachIndexed { i, a -> out = out.replace("{" + i + "}", a.toString()) }
        return out
    }

    private val table: Map<String, String> = mapOf(
        // ---- shell / home ----
        "app.tagline" to "Android RDP client for Windows and Windows Server.",
        "home.devices" to "Devices",
        "home.display" to "Display",
        "home.keyboard" to "Keyboard & HUD",
        "home.transport" to "Default transport",
        "home.security" to "Security",
        "home.about" to "About",
        "home.language" to "Language",
        "home.language.sub" to "App language; applies immediately",
        "home.new" to "New device",
        "home.connect" to "Connect",
        "home.edit" to "Edit",
        "home.delete" to "Delete",
        "home.empty.title" to "No devices yet",
        "home.empty.body" to "Add a Windows or Windows Server address to start.",

        // ---- about + support ----
        "about.version" to "App version",
        "about.engine" to "RDP engine",
        "about.developer" to "Developer",
        "about.license" to "View license",
        "about.license.title" to "License",
        "about.license.body" to "FreeRDP — Apache License 2.0\n" +
            "Copyright (C) 2012-2026 FreeRDP contributors.\n\n" +
            "SeiDesk RM — © SeiDesk.\n" +
            "Fonts: JetBrains Mono (SIL Open Font License 1.1).",
        "about.support.title" to "Support me",
        "about.support.body" to "This app is built without ads and without " +
            "tracking. If it helps you, any support goes straight to build " +
            "costs, test servers, and coding time.",
        "about.support.saweria" to "Support via Saweria",
        "about.support.github" to "GitHub Sponsors",
        "about.support.star" to "Star on GitHub",
        "about.support.starred" to "Thanks",
        "about.thanks" to "Thanks for using SeiDesk RM.",

        // ---- session panel ----
        "panel.input" to "Input",
        "panel.pointer" to "Pointer",
        "panel.buttons" to "Buttons",
        "panel.keyboard" to "Keyboard",
        "panel.screen" to "Screen",
        "panel.session" to "Session",
        "panel.tips" to "Tips",
        "panel.close" to "Close",
        "panel.addKey" to "Add button",
        "panel.editDone" to "Done editing",
        "panel.mapMode" to "Edit layout",
        "panel.resetKeys" to "Restore default buttons",
        "panel.disconnect" to "Disconnect",
        "panel.screenshot" to "Screenshot",
        "pointer.show" to "Show pointer",
        "keys.hold" to "Hold",
        "keys.tap" to "Single tap",
        "keys.toggle" to "Toggle",
        "keys.inToolbar" to "In toolbar",
        "keys.onlyScreen" to "Screen only",
        "keys.editTitle" to "EDIT BUTTON",
        "keys.addTitle" to "ADD BUTTON",
        "input.switchHint" to "Switch to direct touch",
    )
}

/** Preferences storage for app language (no-op in US English-only mode). */
object LangPrefs {
    @Volatile private var loaded = false

    fun install(context: Context): SeiLang {
        loaded = true
        return SeiLang.EN
    }

    fun current(): SeiLang = SeiLang.EN

    fun set(context: Context, lang: SeiLang) {
        // No-op in US English-only mode
    }

    fun isLoaded(): Boolean = loaded
}

/** Active language: always English. */
@Composable
fun seiLang(): SeiLang = SeiLang.EN

@Composable
fun xyLang(): SeiLang = SeiLang.EN

/** Shortcut for [SeiText.t]. */
@Composable
fun t(key: String): String = SeiText.t(key)

/**
 * Standard Composable string helper (US English only).
 */
@Composable
fun sei(text: String, vararg args: Any?): String = fill(text, args)

/**
 * Non-composable string helper (US English only).
 */
fun seiNow(text: String, vararg args: Any?): String = fill(text, args)


private fun fill(template: String, args: Array<out Any?>): String {
    if (args.isEmpty()) return template
    var out = template
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a.toString()) }
    return out
}
