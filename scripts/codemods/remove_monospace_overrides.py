import os
import re

ROOT = r"client\Android\Studio\app\src\main\kotlin\io\seidesk\rm\ui"

for dirpath, _, filenames in os.walk(ROOT):
    for fn in filenames:
        if not fn.endswith(".kt"):
            continue
        fp = os.path.join(dirpath, fn)
        with open(fp, "r", encoding="utf-8") as f:
            content = f.read()

        orig = content
        # Pattern 1: .copy(fontFamily = FontFamily.Monospace)
        content = re.sub(r"\.copy\s*\(\s*fontFamily\s*=\s*FontFamily\.Monospace\s*\)", "", content)
        # Pattern 2: , fontFamily = FontFamily.Monospace
        content = re.sub(r",\s*fontFamily\s*=\s*FontFamily\.Monospace", "", content)
        # Pattern 3: fontFamily = FontFamily.Monospace,
        content = re.sub(r"fontFamily\s*=\s*FontFamily\.Monospace\s*,\s*\n", "", content)
        # Pattern 4: any standalone fontFamily = FontFamily.Monospace
        content = re.sub(r"\s*fontFamily\s*=\s*FontFamily\.Monospace\b", "", content)

        if content != orig:
            with open(fp, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"Updated {fp}")
