#!/usr/bin/env python3
"""
Gate check script: Verifies that no unauthorized 'Xy' or 'XyDesk' identifiers
or UI strings remain in user-facing UI / app Kotlin files.
"""
import os
import re
import sys

ALLOWED_FILE_NAMES = {
    "WireNames.kt",
    "LegacyMigration.kt",
    "XyGamepadState.kt",
    "XyGamepadPacketV1.kt",
}

# Substrings allowed for backwards compatibility, protocol wire contracts, or external templates
ALLOWLIST_SUBSTRINGS = [
    "XyGamepadState",
    "XyGamepadPacketV1",
    "X-XyDesk-Client-ID",
    "xykal/XyRDP",
    "\"XyRDP\"",
    "\"XyDesk\"", # backwards-compatible username checks in login / session switch
    "xydesk_virtual_mic",
    "xykal",
]

def scan_files(root_dir):
    violations = []

    for root, dirs, files in os.walk(root_dir):
        # Exclude FreeRDP core / native C directories
        if "freeRDPCore" in root or "freerdp" in root.lower():
            continue
        for f in files:
            if not f.endswith(".kt") or f in ALLOWED_FILE_NAMES:
                continue
            path = os.path.join(root, f)
            rel_path = os.path.relpath(path, root_dir)
            with open(path, "r", encoding="utf-8", errors="ignore") as fp:
                for line_idx, line in enumerate(fp, 1):
                    stripped = line.strip()
                    # Skip pure comment lines
                    if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
                        continue
                    if "Xy" in line or "xy" in line:
                        # Check allowlist
                        if any(allow in line for allow in ALLOWLIST_SUBSTRINGS):
                            continue
                        # If "xy" appears as part of a normal English word (e.g. proxy, oxygen), exclude
                        # We only flag "Xy" (case-sensitive) or word-boundary "xy"
                        if "Xy" in line or re.search(r"\bxy\b", line, re.IGNORECASE):
                            violations.append((rel_path, line_idx, stripped))
    return violations

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    target_dir = os.path.join(repo_root, "client", "Android", "Studio", "app", "src", "main", "kotlin", "io", "seidesk", "rm")

    violations = scan_files(target_dir)
    if violations:
        print(f"FAILED: Found {len(violations)} unauthorized 'Xy' occurrence(s):")
        for file_path, line_no, line_src in violations:
            print(f"  {file_path}:{line_no}: {line_src}")
        sys.exit(1)
    else:
        print("PASSED: No unauthorized 'Xy' strings or identifiers detected.")
        sys.exit(0)

if __name__ == "__main__":
    main()
