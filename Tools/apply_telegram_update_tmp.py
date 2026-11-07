"""Apply the DrKLO/Telegram 13.1.1 -> 13.2.1 base update to the LinkiGram tree.

Bases are read from the reference checkout at
ci-apk-41906023326/telegram-13-reference (a blobless clone of DrKLO/Telegram).
Run with --apply to modify the project. Without it, only report.

Rules:
  * local == old upstream  -> take new upstream (or delete if upstream deleted)
  * local == new upstream  -> nothing
  * local path absent      -> add new upstream file
  * local diverged         -> git merge-file(local, old, new); write if clean,
                              otherwise leave the local file untouched and list it
  * non-blob entries       -> report only (submodules)
"""
import argparse
import collections
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
REF = ROOT / 'ci-apk-41906023326/telegram-13-reference'
BACKUP = ROOT / 'ci-apk-41906023326/upgrade-backup'
BEFORE = '1266006d887e75b70674b6b085822c8a315091ba'
AFTER = '7be672ead283d022812d75c729d3a04dd4c2a2c6'


def git(*args):
    return subprocess.check_output(['git', '-C', str(REF), *args])


def tree(commit):
    result = {}
    for entry in git('ls-tree', '-rz', '--full-tree', commit).split(b'\0'):
        if not entry:
            continue
        info, path = entry.split(b'\t', 1)
        mode, kind, oid = info.decode().split()
        result[os.fsdecode(path)] = (mode, kind, oid)
    return result


def blob(oid):
    return git('cat-file', 'blob', oid)


def write(path: Path, data: bytes, mode: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    if mode == '100755':
        os.chmod(path, 0o755)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--backup', action='store_true',
                        help='save original files below ci-apk-41906023326/upgrade-backup')
    args = parser.parse_args()

    old, new = tree(BEFORE), tree(AFTER)
    stats = collections.Counter()
    conflicts = []
    deleted_diverged = []
    submodules = []
    changes = []  # (action, name, old_mode, new_mode, new_oid)

    for name in sorted(old.keys() | new.keys()):
        a, b = old.get(name), new.get(name)
        if a == b:
            continue
        if (a and a[1] != 'blob') or (b and b[1] != 'blob'):
            submodules.append((name, a, b))
            continue
        path = ROOT / name
        local = path.read_bytes() if path.is_file() else None
        if local is None:
            stats['absent_locally'] += 1
            if b:
                changes.append(('add', name, None, b[0], b[2]))
            continue
        oid = hashlib.sha1(b'blob ' + str(len(local)).encode() + b'\0' + local).hexdigest()
        if a and oid == a[2]:
            stats['take_new'] += 1
            changes.append(('delete' if not b else 'replace', name, a[0], b and b[0], b and b[2]))
        elif b and oid == b[2]:
            stats['already_new'] += 1
        else:
            stats['diverged'] += 1
            if not b:
                deleted_diverged.append(name)
                continue
            if not a:
                # Upstream added a path that already exists locally under a different name;
                # cannot merge, keep local and report.
                deleted_diverged.append(name)
                continue
            if b'\0' in local[:8192] or b'\0' in blob(b[2])[:8192]:
                stats['binary_diverged'] += 1
                conflicts.append(name)
                continue
            with tempfile.TemporaryDirectory(prefix='linki-apply-') as temp:
                paths = [Path(temp) / n for n in ('ours', 'base', 'theirs')]
                for p, data in zip(paths, (local, blob(a[2]), blob(b[2]))):
                    p.write_bytes(data.replace(b'\r\n', b'\n'))
                result = subprocess.run(['git', 'merge-file', '-p', *map(str, paths)],
                                        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if result.returncode == 0:
                stats['merged_clean'] += 1
                changes.append(('merge_clean', name, a[0], b[0], b[2]))
            else:
                stats['merge_conflict'] += 1
                conflicts.append(name)

    print('State:', dict(stats))
    print('Changes to apply:', len(changes))
    print('Conflicts left for manual resolution:', len(conflicts))
    print('Upstream-deleted but locally modified:', len(deleted_diverged))
    print('Non-blob entries:', len(submodules))
    for name in conflicts:
        print('CONFLICT', name)
    for name in deleted_diverged:
        print('DELETED-UPSTREAM', name)
    for name, a, b in submodules:
        print('SUBMODULE', name, a, b)

    if not args.apply:
        return 0

    if args.backup:
        if BACKUP.exists():
            raise SystemExit(f'Backup already exists: {BACKUP}')
        manifest = BACKUP / 'manifest-before.txt'
        BACKUP.mkdir(parents=True)
        with open(manifest, 'w', encoding='utf-8') as fh:
            for path in sorted(ROOT.rglob('*')):
                if not path.is_file():
                    continue
                rel = path.relative_to(ROOT).as_posix()
                if rel.startswith('ci-apk-41906023326/') or rel.startswith('upgrade-backup/'):
                    continue
                fh.write(rel + '\n')
        for action, name, old_mode, new_mode, new_oid in changes:
            if action == 'add':
                continue
            src = ROOT / name
            if src.is_file():
                dst = BACKUP / 'files' / name
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)

    for action, name, old_mode, new_mode, new_oid in changes:
        path = ROOT / name
        if action == 'delete':
            path.unlink(missing_ok=True)
        elif action == 'add' or action == 'replace':
            write(path, blob(new_oid), new_mode)
        elif action == 'merge_clean':
            # Recompute; deterministic and avoids storing bytes in memory.
            base = blob(old[name][2])
            incoming = blob(new_oid)
            with tempfile.TemporaryDirectory(prefix='linki-apply-') as temp:
                paths = [Path(temp) / n for n in ('ours', 'base', 'theirs')]
                paths[0].write_bytes(path.read_bytes().replace(b'\r\n', b'\n'))
                paths[1].write_bytes(base.replace(b'\r\n', b'\n'))
                paths[2].write_bytes(incoming.replace(b'\r\n', b'\n'))
                result = subprocess.run(['git', 'merge-file', '-p', *map(str, paths)],
                                        stdout=subprocess.PIPE, check=True)
            write(path, result.stdout, new_mode)
        else:
            raise SystemExit(f'Unknown action {action}')
    print('Applied', len(changes), 'changes.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
