import collections
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import argparse

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--write-previews', action='store_true')
args = parser.parse_args()
ref = root / 'ci-apk-41906023326/telegram-13-reference'
before = '1266006d887e75b70674b6b085822c8a315091ba'
after = '7be672ead283d022812d75c729d3a04dd4c2a2c6'
def git(*args):
    return subprocess.check_output(['git', '-C', str(ref), *args])
def tree(commit):
    result = {}
    for entry in git('ls-tree', '-rz', '--full-tree', commit).split(b'\0'):
        if not entry: continue
        info, path = entry.split(b'\t', 1)
        mode, kind, oid = info.decode().split()
        result[os.fsdecode(path)] = (mode, kind, oid)
    return result
old, new = tree(before), tree(after)
stats = collections.Counter()
categories = collections.Counter()
conflicts = []
diverged = []
for name in sorted(old.keys() | new.keys()):
    a, b = old.get(name), new.get(name)
    if a == b: continue
    path = root / name
    categories[name.split('/')[1] if name.startswith('TMessagesProj/') and '/' in name else name.split('/')[0]] += 1
    if a and a[1] != 'blob' or b and b[1] != 'blob':
        stats['submodule_or_other'] += 1
    elif not path.exists():
        stats['absent_locally'] += 1
        stats['absent_added' if not a else 'absent_deleted' if not b else 'absent_modified'] += 1
    elif not path.is_file():
        stats['non_file'] += 1
    else:
        data = path.read_bytes()
        oid = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
        if a and oid == a[2]: stats['matches_old'] += 1
        elif b and oid == b[2]: stats['already_new'] += 1
        else:
            stats['local_diverged'] += 1
            if a and b and a[1] == b[1] == 'blob': diverged.append((name, a[2], b[2]))
            if len(conflicts) < 20: conflicts.append(name)
print('Changed paths:', sum(stats[k] for k in ('submodule_or_other','absent_locally','non_file','matches_old','already_new','local_diverged')))
print('State:', dict(stats))
print('Areas:', dict(categories.most_common(15)))
print('Examples of diverged paths:', *conflicts, sep='\n')
print('Diverged paths with both versions:', len(diverged))
for name, _, _ in diverged:
    if name.endswith(('TLRPC.java', 'ChatActivity.java', 'MessagesController.java', 'MediaController.java', 'AndroidManifest.xml', 'build.gradle')):
        print('Integration-sensitive:', name)

proc = subprocess.Popen(['git', '-C', str(ref), 'cat-file', '--batch'], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
def blob(oid):
    proc.stdin.write((oid + '\n').encode()); proc.stdin.flush()
    header = proc.stdout.readline().decode().strip().split()
    if len(header) != 3: raise RuntimeError('Cannot read upstream blob')
    data = proc.stdout.read(int(header[2])); proc.stdout.read(1)
    return data
merged = collections.Counter()
failed = []
with tempfile.TemporaryDirectory(prefix='linki-upgrade-audit-') as temp:
    paths = [Path(temp) / n for n in ('ours', 'base', 'theirs')]
    for name, old_oid, new_oid in diverged:
        local = (root / name).read_bytes()
        base, incoming = blob(old_oid), blob(new_oid)
        if any(b'\0' in data[:8192] for data in (local, base, incoming)):
            merged['binary'] += 1
            continue
        for path, data in zip(paths, (local, base, incoming)): path.write_bytes(data.replace(b'\r\n', b'\n'))
        result = subprocess.run(['git', 'merge-file', '-p', *map(str, paths)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode == 0: merged['clean'] += 1
        else:
            merged['conflict'] += 1
            failed.append(name)
            print('Conflict blocks:', result.stdout.count(b'<<<<<<< '), name)
            if args.write_previews:
                preview = ref / 'merge-previews' / name
                preview.parent.mkdir(parents=True, exist_ok=True)
                preview.write_bytes(result.stdout)
proc.stdin.close(); proc.wait()
print('Three-way text merge audit:', dict(merged))
print('Files requiring manual integration:', *failed, sep='\n')
