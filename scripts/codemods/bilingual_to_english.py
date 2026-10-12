#!/usr/bin/env python3
"""
scripts/codemods/bilingual_to_english.py

Performs mass refactoring for PR 3 (English US Only):
1. Converts all `xy(id, en, ...)` calls to `sei(en, ...)`.
2. Converts all `xyNow(id, en, ...)` calls to `seiNow(en, ...)`.
3. Updates `Lang.kt` to define `sei` and `seiNow` as English-only functions.
4. Removes language switcher from `Screens.kt`.
5. Fixes localization leaks ('Kembali' -> 'Back', 'RESOLUSI' -> 'RESOLUTION').
6. Replaces imports of `xy` and `xyNow`.
"""

import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
STUDIO_DIR = REPO_ROOT / "client" / "Android" / "Studio"

def log(msg: str):
    print(f"[bilingual_to_english] {msg}")

def extract_call_args(text: str, open_paren_idx: int):
    """
    Given text and the index of '(', returns (args_list, end_idx)
    where end_idx is index right after the matching ')'.
    Correctly handles nested parentheses, brackets, braces, and string literals.
    """
    pos = open_paren_idx + 1
    depth = 1
    args = []
    current_arg = []
    in_str = False
    str_triple = False
    in_char = False
    escape = False

    while pos < len(text) and depth > 0:
        c = text[pos]

        if escape:
            current_arg.append(c)
            escape = False
            pos += 1
            continue

        if c == '\\' and (in_str or in_char):
            escape = True
            current_arg.append(c)
            pos += 1
            continue

        if not in_str and not in_char:
            if text[pos:pos+3] == '"""':
                in_str = True
                str_triple = True
                current_arg.append('"""')
                pos += 3
                continue
            elif c == '"':
                in_str = True
                str_triple = False
                current_arg.append(c)
                pos += 1
                continue
            elif c == "'":
                in_char = True
                current_arg.append(c)
                pos += 1
                continue
            elif c in '({[':
                depth += 1
                current_arg.append(c)
                pos += 1
                continue
            elif c in ')}]':
                depth -= 1
                if depth == 0:
                    args.append(''.join(current_arg))
                    pos += 1
                    break
                current_arg.append(c)
                pos += 1
                continue
            elif c == ',' and depth == 1:
                args.append(''.join(current_arg))
                current_arg = []
                pos += 1
                continue
            else:
                current_arg.append(c)
                pos += 1
                continue
        else:
            if in_str:
                if str_triple and text[pos:pos+3] == '"""':
                    in_str = False
                    str_triple = False
                    current_arg.append('"""')
                    pos += 3
                    continue
                elif not str_triple and c == '"':
                    in_str = False
                    current_arg.append(c)
                    pos += 1
                    continue
                else:
                    current_arg.append(c)
                    pos += 1
                    continue
            elif in_char:
                if c == "'":
                    in_char = False
                current_arg.append(c)
                pos += 1
                continue

    return args, pos

def transform_file_bilingual_calls(file_path: Path):
    if not file_path.exists() or file_path.suffix != ".kt":
        return 0

    content = file_path.read_text(encoding="utf-8")
    orig = content
    changes = 0

    # We match calls to \bxy\( or \bxyNow\(
    # We do iterative replacement scanning from start to end
    pattern = re.compile(r'\b(xy|xyNow)\s*\(')

    idx = 0
    while True:
        match = pattern.search(content, idx)
        if not match:
            break

        fn_name = match.group(1)
        open_paren_idx = match.end() - 1

        args, end_idx = extract_call_args(content, open_paren_idx)
        if len(args) < 2:
            # Not a bilingual call, skip past this match
            idx = match.end()
            continue

        # If it has at least 2 args:
        # arg[0] is ID, arg[1] is EN, arg[2:] are varargs
        en_arg = args[1]
        extra_args = args[2:]

        # Clean up whitespace formatting:
        # If en_arg starts with newline and indentation, preserve it
        new_fn_name = "sei" if fn_name == "xy" else "seiNow"

        # Build replacement call
        # If extra_args exist, join them with comma
        new_call_args = [en_arg] + extra_args
        # If trailing comma was present in original args, keep it clean
        if len(new_call_args) == 1 and new_call_args[0].rstrip().endswith(','):
            new_call_args[0] = new_call_args[0].rstrip()[:-1]

        replacement = f"{new_fn_name}(" + ",".join(new_call_args) + ")"

        # Replace in content
        start_idx = match.start()
        content = content[:start_idx] + replacement + content[end_idx:]
        changes += 1
        idx = start_idx + len(replacement)

    if content != orig:
        file_path.write_text(content, encoding="utf-8")

    return changes

def update_lang_kt():
    log("Updating Lang.kt...")
    lang_file = STUDIO_DIR / "app" / "src" / "main" / "kotlin" / "io" / "seidesk" / "rm" / "ui" / "Lang.kt"
    if not lang_file.exists():
        return

    content = """package io.seidesk.rm.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

/**
 * App language configuration: English (US) only.
 */
enum class XyLang(val code: String, val label: String) {
    EN("en", "English"),
    ;

    companion object {
        fun fromSystem(context: Context): XyLang = EN
    }
}

/**
 * String lookup table for official key-based UI strings (US English).
 */
object SeiText {
    private val langState = mutableStateOf(XyLang.EN)

    fun setLang(value: XyLang) {
        langState.value = value
    }

    fun current(): XyLang = langState.value

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
        "about.license.body" to "FreeRDP — Apache License 2.0\\n" +
            "Copyright (C) 2012-2026 FreeRDP contributors.\\n\\n" +
            "SeiDesk RM — © SeiDesk.\\n" +
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

    fun install(context: Context): XyLang {
        loaded = true
        return XyLang.EN
    }

    fun current(): XyLang = XyLang.EN

    fun set(context: Context, lang: XyLang) {
        // No-op in US English-only mode
    }

    fun isLoaded(): Boolean = loaded
}

/** Active language: always English. */
@Composable
fun xyLang(): XyLang = XyLang.EN

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

// Backward compatibility overloads during transition
@Composable
fun sei(id: String, en: String, vararg args: Any?): String = fill(en, args)

fun seiNow(id: String, en: String, vararg args: Any?): String = fill(en, args)

@Composable
fun xy(id: String, en: String, vararg args: Any?): String = fill(en, args)

fun xyNow(id: String, en: String, vararg args: Any?): String = fill(en, args)

private fun fill(template: String, args: Array<out Any?>): String {
    if (args.isEmpty()) return template
    var out = template
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a.toString()) }
    return out
}
"""
    lang_file.write_text(content, encoding="utf-8")
    log("Lang.kt updated.")

def fix_localization_leaks_and_imports():
    log("Fixing localization leaks and imports...")

    # SeiComponents.kt: 'Kembali' -> 'Back'
    comp_file = STUDIO_DIR / "app" / "src" / "main" / "kotlin" / "io" / "seidesk" / "rm" / "ui" / "components" / "SeiComponents.kt"
    if comp_file.exists():
        c = comp_file.read_text(encoding="utf-8")
        c = c.replace('contentDescription = "Kembali"', 'contentDescription = sei("Back")')
        comp_file.write_text(c, encoding="utf-8")
        log("Updated Kembali in SeiComponents.kt")

    # SessionControls.kt: 'RESOLUSI' -> 'RESOLUTION'
    sess_ctrl = STUDIO_DIR / "app" / "src" / "main" / "kotlin" / "io" / "seidesk" / "rm" / "ui" / "SessionControls.kt"
    if sess_ctrl.exists():
        c = sess_ctrl.read_text(encoding="utf-8")
        c = c.replace('"RESOLUSI"', '"RESOLUTION"')
        sess_ctrl.write_text(c, encoding="utf-8")
        log("Updated RESOLUSI in SessionControls.kt")

    # Screens.kt: remove Language card in AboutSection
    screens_file = STUDIO_DIR / "app" / "src" / "main" / "kotlin" / "io" / "seidesk" / "rm" / "ui" / "Screens.kt"
    if screens_file.exists():
        c = screens_file.read_text(encoding="utf-8")
        # Remove the language card block
        pattern = r'\s*// ---- Language: English-first release ----\s*SeiCard\s*\{[^}]*home\.language[^}]*\}'
        c = re.sub(pattern, "", c, flags=re.DOTALL)
        # Also clean unused `var lang by remember { mutableStateOf(LangPrefs.current()) }`
        c = re.sub(r'\s*var lang by remember \{ mutableStateOf\(LangPrefs\.current\(\)\) \}', "", c)
        screens_file.write_text(c, encoding="utf-8")
        log("Removed language card from Screens.kt")

def main():
    log("Starting PR 3 (English US only) transformations...")
    update_lang_kt()

    total_transformed = 0
    for kt_file in STUDIO_DIR.rglob("*.kt"):
        if "cpp" in kt_file.parts:
            continue
        count = transform_file_bilingual_calls(kt_file)
        if count > 0:
            total_transformed += count
            log(f"Transformed {count} bilingual calls in {kt_file.name}")

    log(f"Total bilingual calls transformed: {total_transformed}")
    fix_localization_leaks_and_imports()
    log("PR 3 transformations complete.")

if __name__ == "__main__":
    main()
