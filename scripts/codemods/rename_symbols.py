#!/usr/bin/env python3
"""
scripts/codemods/rename_symbols.py

Performs mass refactoring for PR 2:
1. Strips existing Sei* = Xy* backward compatibility shims/wrappers.
2. Removes deprecated shims and files (XyBottomNav, XyFeedScreen, XyProfileScreen, XySessionService, XyApp.java).
3. Renames component, input, rdp, core, and test files to Sei*.
4. Renames all Xy* symbols to Sei* across Kotlin/Java/XML source files.
5. Updates theme colors and asset references.
"""

import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
STUDIO_DIR = REPO_ROOT / "client" / "Android" / "Studio"

def log(msg: str):
    print(f"[rename_symbols] {msg}")

def remove_line_blocks(file_path: Path, start_marker: str, end_marker: str = None):
    if not file_path.exists():
        return
    content = file_path.read_text(encoding="utf-8")
    if start_marker in content:
        if end_marker and end_marker in content:
            pattern = re.escape(start_marker) + r".*?" + re.escape(end_marker)
            new_content = re.sub(pattern, "", content, flags=re.DOTALL)
        else:
            # from start_marker to end of file
            idx = content.find(start_marker)
            new_content = content[:idx].rstrip() + "\n"
        file_path.write_text(new_content, encoding="utf-8")
        log(f"Removed block from {file_path.name}")

def step1_strip_shims():
    log("Step 1: Stripping shim/wrapper blocks...")
    ui_dir = STUDIO_DIR / "app" / "src" / "main" / "kotlin" / "io" / "seidesk" / "rm" / "ui"

    # XyComponents.kt shims at bottom
    remove_line_blocks(ui_dir / "components" / "XyComponents.kt", "// =========================================================================", None)
    # If not using section comments, look for first Sei* shim
    remove_line_blocks(ui_dir / "components" / "XyComponents.kt", "@Composable\nfun SeiToggleRow(", None)

    # XyControls.kt shims
    remove_line_blocks(ui_dir / "components" / "XyControls.kt", "// =========================================================================", None)
    remove_line_blocks(ui_dir / "components" / "XyControls.kt", "@Composable\nfun SeiSlider(", None)

    # XyGlass.kt shims
    remove_line_blocks(ui_dir / "components" / "XyGlass.kt", "// =========================================================================", None)
    remove_line_blocks(ui_dir / "components" / "XyGlass.kt", "@Composable\nfun SeiGlassTile(", None)

    # XyNotice.kt typealiases
    remove_line_blocks(ui_dir / "components" / "XyNotice.kt", "typealias SeiNoticeState = XyNoticeState", None)

    # XyIcons.kt typealias
    remove_line_blocks(ui_dir / "components" / "XyIcons.kt", "typealias SeiIcons = XyIcons", None)

    # XyGamepadActionMapper.kt typealiases
    remove_line_blocks(ui_dir / "input" / "XyGamepadActionMapper.kt", "typealias SeiPadKey = XyPadKey", None)

    # XyPadLayout.kt typealiases
    remove_line_blocks(ui_dir / "input" / "XyPadLayout.kt", "typealias SeiPadPos = XyPadPos", None)

    # XyRdpClient.kt typealiases
    remove_line_blocks(ui_dir / "rdp" / "XyRdpClient.kt", "internal typealias SeiRdpClient = XyRdpClient", None)

    # XyRdpConfig.kt typealias
    remove_line_blocks(ui_dir / "rdp" / "XyRdpConfig.kt", "internal typealias SeiRdpConfig = XyRdpConfig", None)

    # SeiBottomNav.kt typealias
    remove_line_blocks(ui_dir / "SeiBottomNav.kt", "internal typealias XyTab = SeiTab", None)

    # SessionRegistry.kt XySessionRegistry
    remove_line_blocks(ui_dir / "SessionRegistry.kt", "/** Backward compatibility alias for [SeiSessionRegistry]. */", None)
    remove_line_blocks(ui_dir / "SessionRegistry.kt", "object XySessionRegistry {", None)

    # SeiDeskHome.kt XyDeskHome compat
    remove_line_blocks(ui_dir / "SeiDeskHome.kt", "@Composable\nfun XyDeskHome(", None)

    # SeiDeskSession.kt XyDeskSessionScreen compat
    remove_line_blocks(ui_dir / "SeiDeskSession.kt", "@Composable\nfun XyDeskSessionScreen(", None)

    # SeiDeskHomeActivity.kt typealias
    home_act = ui_dir / "SeiDeskHomeActivity.kt"
    if home_act.exists():
        content = home_act.read_text(encoding="utf-8")
        content = re.sub(r"\ntypealias XyDeskHomeActivity = SeiDeskHomeActivity\n?", "\n", content)
        home_act.write_text(content, encoding="utf-8")

    # SeiDeskSessionActivity.kt typealias
    sess_act = ui_dir / "SeiDeskSessionActivity.kt"
    if sess_act.exists():
        content = sess_act.read_text(encoding="utf-8")
        content = re.sub(r"\ntypealias XyDeskSessionActivity = SeiDeskSessionActivity\n?", "\n", content)
        sess_act.write_text(content, encoding="utf-8")

def step2_delete_deprecated_files():
    log("Step 2: Deleting deprecated shim files and old assets...")
    ui_dir = STUDIO_DIR / "app" / "src" / "main" / "kotlin" / "io" / "seidesk" / "rm" / "ui"
    files_to_delete = [
        ui_dir / "XyBottomNav.kt",
        ui_dir / "XyFeedScreen.kt",
        ui_dir / "XyProfileScreen.kt",
        STUDIO_DIR / "app" / "src" / "main" / "kotlin" / "io" / "seidesk" / "rm" / "XySessionService.kt",
        STUDIO_DIR / "app" / "src" / "main" / "java" / "io" / "seidesk" / "rm" / "XyApp.java",
        STUDIO_DIR / "app" / "src" / "main" / "res" / "drawable-nodpi" / "xy_logo_h_white.png",
        STUDIO_DIR / "app" / "src" / "main" / "res" / "drawable-nodpi" / "xy_logo_h_black.png",
        STUDIO_DIR / "app" / "src" / "main" / "res" / "drawable-nodpi" / "xy_mark_white.png",
    ]
    for f in files_to_delete:
        if f.exists():
            f.unlink()
            log(f"Deleted {f.name}")

def step3_update_manifest():
    log("Step 3: Updating AndroidManifest.xml...")
    manifest = STUDIO_DIR / "app" / "src" / "main" / "AndroidManifest.xml"
    content = manifest.read_text(encoding="utf-8")
    # Remove activity-alias elements for XyDeskHomeActivity and XyDeskSessionActivity
    content = re.sub(
        r'\s*<activity-alias\s+android:name="io\.seidesk\.rm\.ui\.XyDeskHomeActivity"[^>]*/>',
        "",
        content,
    )
    content = re.sub(
        r'\s*<activity-alias\s+android:name="io\.seidesk\.rm\.ui\.XyDeskSessionActivity"[^>]*/>',
        "",
        content,
    )
    manifest.write_text(content, encoding="utf-8")

def step4_rename_files():
    log("Step 4: Renaming files on disk...")
    renames = [
        # ui/components
        ("app/src/main/kotlin/io/seidesk/rm/ui/components/XyComponents.kt",
         "app/src/main/kotlin/io/seidesk/rm/ui/components/SeiComponents.kt"),
        ("app/src/main/kotlin/io/seidesk/rm/ui/components/XyControls.kt",
         "app/src/main/kotlin/io/seidesk/rm/ui/components/SeiControls.kt"),
        ("app/src/main/kotlin/io/seidesk/rm/ui/components/XyGlass.kt",
         "app/src/main/kotlin/io/seidesk/rm/ui/components/SeiGlass.kt"),
        ("app/src/main/kotlin/io/seidesk/rm/ui/components/XyIcons.kt",
         "app/src/main/kotlin/io/seidesk/rm/ui/components/SeiIcons.kt"),
        ("app/src/main/kotlin/io/seidesk/rm/ui/components/XyNotice.kt",
         "app/src/main/kotlin/io/seidesk/rm/ui/components/SeiNotice.kt"),
        # ui/input
        ("app/src/main/kotlin/io/seidesk/rm/ui/input/XyGamepadActionMapper.kt",
         "app/src/main/kotlin/io/seidesk/rm/ui/input/SeiGamepadActionMapper.kt"),
        ("app/src/main/kotlin/io/seidesk/rm/ui/input/XyPadLayout.kt",
         "app/src/main/kotlin/io/seidesk/rm/ui/input/SeiPadLayout.kt"),
        # ui/rdp
        ("app/src/main/kotlin/io/seidesk/rm/ui/rdp/XyRdpClient.kt",
         "app/src/main/kotlin/io/seidesk/rm/ui/rdp/SeiRdpClient.kt"),
        ("app/src/main/kotlin/io/seidesk/rm/ui/rdp/XyRdpConfig.kt",
         "app/src/main/kotlin/io/seidesk/rm/ui/rdp/SeiRdpConfig.kt"),
        ("app/src/main/kotlin/io/seidesk/rm/ui/rdp/XyRdpUnlock.kt",
         "app/src/main/kotlin/io/seidesk/rm/ui/rdp/SeiRdpUnlock.kt"),
        # core-pc-stream
        ("core-pc-stream/src/main/kotlin/io/seidesk/rm/pcstream/XyGamepadPacket.kt",
         "core-pc-stream/src/main/kotlin/io/seidesk/rm/pcstream/SeiGamepadPacket.kt"),
        ("core-pc-stream/src/main/kotlin/io/seidesk/rm/pcstream/XyPcStreamGamepadSink.kt",
         "core-pc-stream/src/main/kotlin/io/seidesk/rm/pcstream/SeiPcStreamGamepadSink.kt"),
        ("core-pc-stream/src/main/kotlin/io/seidesk/rm/pcstream/XyVirtualPad.kt",
         "core-pc-stream/src/main/kotlin/io/seidesk/rm/pcstream/SeiVirtualPad.kt"),
        ("core-pc-stream/src/test/kotlin/io/seidesk/rm/pcstream/XyGamepadPacketTest.kt",
         "core-pc-stream/src/test/kotlin/io/seidesk/rm/pcstream/SeiGamepadPacketTest.kt"),
        ("core-pc-stream/src/test/kotlin/io/seidesk/rm/pcstream/XyVirtualPadTest.kt",
         "core-pc-stream/src/test/kotlin/io/seidesk/rm/pcstream/SeiVirtualPadTest.kt"),
        # app tests
        ("app/src/test/kotlin/io/seidesk/rm/ui/components/XySliderMathTest.kt",
         "app/src/test/kotlin/io/seidesk/rm/ui/components/SeiSliderMathTest.kt"),
        ("app/src/test/kotlin/io/seidesk/rm/ui/input/XyGamepadActionMapperTest.kt",
         "app/src/test/kotlin/io/seidesk/rm/ui/input/SeiGamepadActionMapperTest.kt"),
        ("app/src/test/kotlin/io/seidesk/rm/ui/input/XyPadLayoutTest.kt",
         "app/src/test/kotlin/io/seidesk/rm/ui/input/SeiPadLayoutTest.kt"),
        ("app/src/test/kotlin/io/seidesk/rm/ui/rdp/XyRdpParseTest.kt",
         "app/src/test/kotlin/io/seidesk/rm/ui/rdp/SeiRdpParseTest.kt"),
        # assets
        ("app/src/main/res/drawable-nodpi/xy_win11_wall.jpg",
         "app/src/main/res/drawable-nodpi/sei_win11_wall.jpg"),
    ]
    for src_rel, dst_rel in renames:
        src = STUDIO_DIR / src_rel
        dst = STUDIO_DIR / dst_rel
        if src.exists():
            dst.parent.mkdir(parents=True, exist_ok=True)
            src.rename(dst)
            log(f"Renamed {src.name} -> {dst.name}")

def step5_symbol_replacements():
    log("Step 5: Replacing symbol names across source code...")
    # List of exact token replacements. Ordered by length descending so longer identifiers match first.
    replacements = [
        # Session & Services
        ("XySessionService", "SeiSessionService"),
        ("XySessionRegistry", "SeiSessionRegistry"),
        ("XyDeskHomeActivity", "SeiDeskHomeActivity"),
        ("XyDeskSessionActivity", "SeiDeskSessionActivity"),
        ("XyDeskHome", "SeiDeskHome"),
        ("XyDeskSessionScreen", "SeiDeskSessionScreen"),
        # Enums & Models
        ("XyAudioMode", "SeiAudioMode"),
        ("XyStreamProfile", "SeiStreamProfile"),
        ("XySecurityProtocol", "SeiSecurityProtocol"),
        ("XyGpuProfile", "SeiGpuProfile"),
        ("XyPcStreamEngine", "SeiPcStreamEngine"),
        ("XyGateway", "SeiGateway"),
        ("XySection", "SeiSection"),
        ("XyRoute", "SeiRoute"),
        ("XyTab", "SeiTab"),
        ("XyWall", "SeiWall"),
        ("XyWallpaper", "SeiWallpaper"),
        ("XyMouseButton", "SeiMouseButton"),
        ("XySplashScreen", "SeiSplashScreen"),
        # Components & UI
        ("XyButtonVariant", "SeiButtonVariant"),
        ("XyCardVariant", "SeiCardVariant"),
        ("XyFieldState", "SeiFieldState"),
        ("XyGlassShape", "SeiGlassShape"),
        ("XyGlassCard", "SeiGlassCard"),
        ("XyGlassTile", "SeiGlassTile"),
        ("XyGlassRow", "SeiGlassRow"),
        ("XyGlassSectionLabel", "SeiGlassSectionLabel"),
        ("XyNoticeState", "SeiNoticeState"),
        ("XyNoticeBus", "SeiNoticeBus"),
        ("XyNoticeCard", "SeiNoticeCard"),
        ("XyNoticeHost", "SeiNoticeHost"),
        ("XyTileGrid", "SeiTileGrid"),
        ("XyCard", "SeiCard"),
        ("XyPillButton", "SeiPillButton"),
        ("XyIconPill", "SeiIconPill"),
        ("XyRow", "SeiRow"),
        ("XySwitch", "SeiSwitch"),
        ("XySegmented", "SeiSegmented"),
        ("XyField", "SeiField"),
        ("XyTopBar", "SeiTopBar"),
        ("XyStatusDot", "SeiStatusDot"),
        ("XyWordmark", "SeiWordmark"),
        ("XyLogo", "SeiLogo"),
        ("XyOverlay", "SeiOverlay"),
        ("XyDialog", "SeiDialog"),
        ("XyIcons", "SeiIcons"),
        ("XyIcon", "SeiIcon"),
        ("xyIcon", "seiIcon"),
        ("XySlider", "SeiSlider"),
        ("XySpinner", "SeiSpinner"),
        ("XyDivider", "SeiDivider"),
        ("XyDot", "SeiDot"),
        ("XyTag", "SeiTag"),
        ("XyInset", "SeiInset"),
        ("XyToggleRow", "SeiToggleRow"),
        # Modifiers
        ("Modifier.xyGlass", "Modifier.seiSurface"),
        (".xyGlass(", ".seiSurface("),
        ("Modifier.xyPurpleGlass", "Modifier.seiMusicGlass"),
        (".xyPurpleGlass(", ".seiMusicGlass("),
        ("xyGlass(", "seiSurface("),
        ("xyPurpleGlass(", "seiMusicGlass("),
        # Input & RDP
        ("XyGamepadActionMapperTest", "SeiGamepadActionMapperTest"),
        ("XyGamepadActionMapper", "SeiGamepadActionMapper"),
        ("XyGamepadPacketTest", "SeiGamepadPacketTest"),
        ("XyGamepadPacket", "SeiGamepadPacket"),
        ("XyPcStreamGamepadSink", "SeiPcStreamGamepadSink"),
        ("XyVirtualPadTest", "SeiVirtualPadTest"),
        ("XyVirtualPad", "SeiVirtualPad"),
        ("XyPadButton", "SeiPadButton"),
        ("XyPadLayoutTest", "SeiPadLayoutTest"),
        ("XyPadLayout", "SeiPadLayout"),
        ("XyPadKey", "SeiPadKey"),
        ("XyStickMode", "SeiStickMode"),
        ("XyStickDirection", "SeiStickDirection"),
        ("XyGamepadOutput", "SeiGamepadOutput"),
        ("XyPadPos", "SeiPadPos"),
        ("XyPadCluster", "SeiPadCluster"),
        ("XyRdpClient", "SeiRdpClient"),
        ("XyRdpAccess", "SeiRdpAccess"),
        ("XyRdpRun", "SeiRdpRun"),
        ("XyRdpConfig", "SeiRdpConfig"),
        ("XyRdpUnlock", "SeiRdpUnlock"),
        ("XySliderMathTest", "SeiSliderMathTest"),
        ("XyRdpParseTest", "SeiRdpParseTest"),
        # Theme Tokens
        ("XyThemeState", "SeiThemeState"),
        ("xyDark()", "seiDark()"),
        ("xyDark", "seiDark"),
        ("XyShapes", "SeiShapes"),
        ("XyPill", "SeiPill"),
        ("XyDisplay", "SeiDisplay"),
        ("XyBody", "SeiBody"),
        ("XyPrimaryContainer", "SeiPrimaryContainer"),
        ("XyAccentGlow", "SeiAccentGlow"),
        ("XyAccentSoft", "SeiAccentSoft"),
        ("XyAccentMuted", "SeiAccentMuted"),
        ("XyAccent", "SeiAccent"),
        ("XySurfaceRaised", "SeiSurfaceRaised"),
        ("XySurfaceAlt", "SeiSurfaceAlt"),
        ("XySurface", "SeiSurface"),
        ("XyTextFaint", "SeiTextFaint"),
        ("XyTextDim", "SeiTextDim"),
        ("XyText", "SeiText"),
        ("XyLineStrong", "SeiLineStrong"),
        ("XyLine", "SeiLine"),
        ("XyBgAlt", "SeiBgAlt"),
        ("XyBg", "SeiBg"),
        ("XyOkGlow", "SeiOkGlow"),
        ("XyOk", "SeiOk"),
        ("XyWarn", "SeiWarn"),
        ("XyDanger", "SeiDanger"),
        # Res resources
        ("xy_win11_wall", "sei_win11_wall"),
        ("xy_launcher_bg", "sei_launcher_bg"),
        ("xy_window_bg", "sei_window_bg"),
        # Threads & Tags & Labels
        ('"XyDesk-PC-Recorder"', '"SeiDesk-PC-Recorder"'),
        ('"XyDeskSessionAct"', '"SeiDeskSessionAct"'),
        ('"XyDeskSession"', '"SeiDeskSession"'),
        ('"XyDeskOnlineMusic"', '"SeiDeskOnlineMusic"'),
        ('"XyDeskClipboardReader"', '"SeiDeskClipboardReader"'),
        ('"XyDesk-PC-AudioRec"', '"SeiDesk-PC-AudioRec"'),
        ('"xydesk-profiles-backup"', '"seidesk-profiles-backup"'),
        ('"xydesk-host-cmd"', '"seidesk-host-cmd"'),
        ('"xy-spinner"', '"sei-spinner"'),
        # Intent extras in SeiDeskSessionActivity
        ('"xydesk.host"', '"seidesk.host"'),
        ('"xydesk.port"', '"seidesk.port"'),
        ('"xydesk.user"', '"seidesk.user"'),
        ('"xydesk.pass"', '"seidesk.pass"'),
        ('"xydesk.domain"', '"seidesk.domain"'),
        ('"xydesk.label"', '"seidesk.label"'),
        ('"xydesk.key"', '"seidesk.key"'),
    ]

    target_extensions = {".kt", ".java", ".xml"}
    modules = ["app", "core-rdp", "core-pc-stream", "features-sessions", "freeRDPCore"]

    count = 0
    for mod in modules:
        mod_src = STUDIO_DIR / mod / "src"
        if not mod_src.exists():
            continue
        for path in mod_src.rglob("*"):
            if path.suffix in target_extensions:
                # Do not modify native cpp/c in freeRDPCore
                if "cpp" in path.parts:
                    continue
                content = path.read_text(encoding="utf-8")
                orig = content
                for old_val, new_val in replacements:
                    if old_val in content:
                        # Use word boundary if it's an alphanumeric identifier
                        if re.match(r"^[A-Za-z0-9_]+$", old_val):
                            content = re.sub(r"\b" + re.escape(old_val) + r"\b", new_val, content)
                        else:
                            content = content.replace(old_val, new_val)
                if content != orig:
                    path.write_text(content, encoding="utf-8")
                    count += 1
    log(f"Replaced symbols in {count} source files.")

def step6_colors_and_splash():
    log("Step 6: Updating colors.xml, themes.xml, Splash.kt, and SeiSessionService...")
    colors_xml = STUDIO_DIR / "app" / "src" / "main" / "res" / "values" / "colors.xml"
    if colors_xml.exists():
        c = colors_xml.read_text(encoding="utf-8")
        c = c.replace('<color name="xy_launcher_bg">#10041A</color>', '<color name="sei_launcher_bg">#0A0C10</color>')
        c = c.replace('<color name="xy_window_bg">#14161A</color>', '<color name="sei_window_bg">#0A0C10</color>')
        colors_xml.write_text(c, encoding="utf-8")

    # Update AppBrand.kt PUBLISHER
    app_brand = STUDIO_DIR / "app" / "src" / "main" / "kotlin" / "io" / "seidesk" / "rm" / "ui" / "AppBrand.kt"
    if app_brand.exists():
        c = app_brand.read_text(encoding="utf-8")
        c = c.replace('"XyVerse Technology Global"', '"Seijixinghe888"')
        app_brand.write_text(c, encoding="utf-8")

    # Update Splash.kt
    splash_kt = STUDIO_DIR / "app" / "src" / "main" / "kotlin" / "io" / "seidesk" / "rm" / "ui" / "Splash.kt"
    if splash_kt.exists():
        c = splash_kt.read_text(encoding="utf-8")
        # Replace background Color(0xFF14161A) with Color(0xFF0A0C10)
        c = c.replace("Color(0xFF14161A)", "Color(0xFF0A0C10)")
        c = c.replace("Color(0xFFF6F7F9)", "Color(0xFFF8FAFC)")
        # Replace Image(painterResource(if (dark) R.drawable.xy_logo_h_white ...)) with Compose SeiLogo + SeiWordmark
        old_image_pattern = r'Image\(\s*painter\s*=\s*painterResource\(\s*if\s*\(dark\)\s*R\.drawable\.xy_logo_h_white\s*else\s*R\.drawable\.xy_logo_h_black,?\s*\),.*?modifier\s*=\s*Modifier\s*\.size\(width\s*=\s*252\.dp,\s*height\s*=\s*86\.dp\)\s*\.alpha\(alpha\)\s*\.scale\(scale\),?\s*\)'
        new_logo_block = (
            'androidx.compose.foundation.layout.Column(\n'
            '            modifier = Modifier\n'
            '                .alpha(alpha)\n'
            '                .scale(scale),\n'
            '            horizontalAlignment = Alignment.CenterHorizontally,\n'
            '        ) {\n'
            '            SeiLogo(\n'
            '                modifier = Modifier.size(64.dp),\n'
            '                tint = if (dark) Color.White else Color(0xFF0A0C10),\n'
            '            )\n'
            '            androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))\n'
            '            SeiWordmark(\n'
            '                fontSize = 24.sp,\n'
            '            )\n'
            '        }'
        )
        c = re.sub(old_image_pattern, new_logo_block, c, flags=re.DOTALL)
        splash_kt.write_text(c, encoding="utf-8")

    # Update SeiSessionService.kt
    sess_svc = STUDIO_DIR / "app" / "src" / "main" / "kotlin" / "io" / "seidesk" / "rm" / "SeiSessionService.kt"
    if sess_svc.exists():
        c = sess_svc.read_text(encoding="utf-8")
        c = c.replace(
            "BitmapFactory.decodeResource(resources, io.seidesk.rm.R.drawable.xy_mark_white)",
            "BitmapFactory.decodeResource(resources, io.seidesk.rm.R.drawable.ic_launcher_monochrome)"
        )
        sess_svc.write_text(c, encoding="utf-8")

def main():
    log("Starting PR 2 codemods...")
    step1_strip_shims()
    step2_delete_deprecated_files()
    step3_update_manifest()
    step4_rename_files()
    step5_symbol_replacements()
    step6_colors_and_splash()
    log("PR 2 codemods completed.")

if __name__ == "__main__":
    main()
