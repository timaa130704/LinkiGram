import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'ci-apk-41906023326/telegram-13-reference/merge-previews'
for filename in sys.argv[1:]:
    lines = (root / filename).read_text(encoding='utf-8').splitlines()
    print('\nFILE', filename)
    index = 0
    while index < len(lines):
        if not lines[index].startswith('<<<<<<< '):
            index += 1
            continue
        start = max(0, index - 3)
        while index < len(lines) and not lines[index].startswith('>>>>>>> '):
            index += 1
        print('\n'.join(lines[start:min(len(lines), index + 4)]))
        index += 1
