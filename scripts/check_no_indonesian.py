#!/usr/bin/env python3
"""
Gate check script: Verifies that no untranslated Indonesian UI strings remain
in user-facing UI Kotlin files (io/seidesk/rm/ui/).
"""
import os
import re
import sys

INDONESIAN_WORDS = [
    "perangkat", "pengaturan", "beranda", "simpan", "batal", "memuat",
    "hubungkan", "durasi", "menit", "tutup", "hapus", "gagal",
    "unduh", "koneksi", "masuk", "keluar", "kembali", "lanjut", "kata sandi",
    "nama pengguna", "tidak dapat", "pilih", "salin", "bantuan"
]

# Allowlist for technical or protocol strings (e.g. enum names, internal error codes)
ALLOWLIST_SUBSTRINGS = [
    "PERANGKAT",  # enum SeiSection.PERANGKAT
    "PENGATURAN", # enum SeiSection.PENGATURAN
]

def scan_files(root_dir):
    violations = []
    pattern = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')

    for root, _, files in os.walk(root_dir):
        for f in files:
            if not f.endswith(".kt"):
                continue
            path = os.path.join(root, f)
            rel_path = os.path.relpath(path, root_dir)
            with open(path, "r", encoding="utf-8", errors="ignore") as fp:
                for line_idx, line in enumerate(fp, 1):
                    stripped = line.strip()
                    if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
                        continue
                    for match in pattern.finditer(line):
                        content = match.group(1)
                        if any(allow in content for allow in ALLOWLIST_SUBSTRINGS):
                            continue
                        content_lower = content.lower()
                        for word in INDONESIAN_WORDS:
                            if re.search(r"\b" + re.escape(word) + r"\b", content_lower):
                                violations.append((rel_path, line_idx, word, line.strip()))
                                break
    return violations

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    target_dir = os.path.join(repo_root, "client", "Android", "Studio", "app", "src", "main", "kotlin", "io", "seidesk", "rm")

    violations = scan_files(target_dir)
    if violations:
        print(f"FAILED: Found {len(violations)} possible Indonesian string(s) in UI:")
        for file_path, line_no, word, line_src in violations:
            print(f"  {file_path}:{line_no}: matched word '{word}' -> {line_src}")
        sys.exit(1)
    else:
        print("PASSED: No Indonesian UI strings detected.")
        sys.exit(0)

if __name__ == "__main__":
    main()
