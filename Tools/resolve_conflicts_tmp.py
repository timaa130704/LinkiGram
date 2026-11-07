#!/usr/bin/env python3
"""Resolve conflict markers in a merge preview using a per-hunk choice list.

Usage:
    python3 Tools/resolve_conflicts_tmp.py <preview-rel-path> <choice[,choice...]> [--write]
    python3 Tools/resolve_conflicts_tmp.py <preview-rel-path> --auto-empty-ours [--write]

Choices are applied in file order to successive conflict hunks:
    ours   keep only our side
    theirs keep only their side
    both   keep our side then their side
    none   drop the whole block

--auto-empty-ours picks 'theirs' for hunks whose our-side is empty, otherwise
mirrors the default choice given in the choice list (used as a fallback).
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PREVIEWS = ROOT / 'ci-apk-41906023326/telegram-13-reference/merge-previews'


def split_hunks(text):
    lines = text.splitlines(keepends=True)
    out, i = [], 0
    while i < len(lines):
        if lines[i].startswith('<<<<<<< '):
            j = i + 1
            while not lines[j].startswith('======='):
                j += 1
            k = j + 1
            while not lines[k].startswith('>>>>>>> '):
                k += 1
            out.append(('conflict', lines[i + 1:j], lines[j + 1:k]))
            i = k + 1
        else:
            out.append(('text', [lines[i]], None))
            i += 1
    return out


def resolve(hunks, choices, auto_empty_ours):
    result, conflicts = [], 0
    for kind, ours, theirs in hunks:
        if kind == 'text':
            result.extend(ours)
            continue
        conflicts += 1
        if auto_empty_ours and not ours:
            result.extend(theirs)
            continue
        choice = choices[conflicts - 1] if conflicts - 1 < len(choices) else (
            choices[-1] if choices else None)
        if choice == 'ours':
            result.extend(ours)
        elif choice == 'theirs':
            result.extend(theirs)
        elif choice == 'both':
            result.extend(ours)
            result.extend(theirs)
        elif choice == 'none':
            pass
        else:
            raise SystemExit('missing choice for conflict #%d' % conflicts)
    return result, conflicts


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    flags = {a for a in sys.argv[1:] if a.startswith('--')}
    if len(args) < 2:
        raise SystemExit(__doc__)
    rel = args[0]
    if '--auto-empty-ours' in flags:
        choices = args[1].split(',') if len(args) > 1 else ['theirs']
        auto = True
    else:
        auto = False
        choices = args[1].split(',')
    preview = PREVIEWS / rel
    text = preview.read_text(encoding='utf-8')
    hunks = split_hunks(text)
    result, conflicts = resolve(hunks, choices, auto)
    target = ROOT / rel
    if '--write' in flags:
        target.write_text(''.join(result), encoding='utf-8')
        print('wrote %s (resolved %d conflicts)' % (rel, conflicts))
    else:
        sys.stdout.write(''.join(result))
        print('--- would resolve %d conflicts in %s' % (conflicts, rel), file=sys.stderr)


if __name__ == '__main__':
    main()