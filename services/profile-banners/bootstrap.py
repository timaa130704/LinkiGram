#!/usr/bin/env python3
"""Run the Node.js banner service inside a Linux Python 3.12+ hosting image."""
import fcntl
import hashlib
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parent
RUNTIME = ROOT / '.node-runtime'
BASE_URL = 'https://nodejs.org/dist/latest-v24.x/'
MAX_ARCHIVE = 100 * 1024 * 1024


def download(url, destination):
    digest = hashlib.sha256()
    with urllib.request.urlopen(url, timeout=90) as response, destination.open('wb') as output:
        total = 0
        while chunk := response.read(256 * 1024):
            total += len(chunk)
            if total > MAX_ARCHIVE:
                raise RuntimeError('Архив Node.js превысил допустимый размер')
            digest.update(chunk)
            output.write(chunk)
    return digest.hexdigest()


def install_runtime():
    node = RUNTIME / 'bin' / 'node'
    if node.is_file():
        version = subprocess.check_output([str(node), '--version'], text=True).strip()
        if version.startswith('v24.'):
            return node
        raise RuntimeError('В .node-runtime уже лежит другая версия. Остановите сервер и удалите эту папку, затем запустите снова.')
    if RUNTIME.exists():
        raise RuntimeError('Папка .node-runtime неполная. Остановите сервер и удалите её перед повторным запуском.')
    if platform.system() != 'Linux':
        raise RuntimeError('Этот запускатель предназначен для Linux-контейнера хостинга')
    arch = {'x86_64': 'x64', 'aarch64': 'arm64'}.get(platform.machine())
    if not arch:
        raise RuntimeError('Не поддерживается архитектура: ' + platform.machine())
    if not hasattr(tarfile, 'data_filter'):
        raise RuntimeError('Выберите образ Python 3.12 или новее')
    print('Устанавливаю Node.js 24 локально. Права администратора не нужны.', flush=True)
    with urllib.request.urlopen(BASE_URL + 'SHASUMS256.txt', timeout=60) as response:
        manifest = response.read(1024 * 1024).decode('ascii')
    pattern = re.compile(r'([a-f0-9]{64})\s+(node-v24\.\d+\.\d+-linux-' + arch + r'\.tar\.xz)')
    selected = next((pattern.fullmatch(line.strip()) for line in manifest.splitlines() if pattern.fullmatch(line.strip())), None)
    if selected is None:
        raise RuntimeError('На nodejs.org не найден официальный архив Node.js 24 для этой системы')
    expected, name = selected.groups()
    with tempfile.TemporaryDirectory(prefix='.node-install-', dir=ROOT) as temporary:
        temporary = Path(temporary)
        archive_path = temporary / name
        actual = download(BASE_URL + name, archive_path)
        if actual != expected:
            raise RuntimeError('Контрольная сумма Node.js не совпала. Повторите запуск позже.')
        prefix = name.removesuffix('.tar.xz')
        with tarfile.open(archive_path, 'r:xz') as archive:
            entries = archive.getmembers()
            if sum(entry.size for entry in entries) > 450 * 1024 * 1024:
                raise RuntimeError('Распакованный архив слишком большой')
            if any(entry.name.split('/')[0] != prefix for entry in entries):
                raise RuntimeError('Неожиданная структура архива Node.js')
            archive.extractall(temporary, filter='data')
        extracted = temporary / prefix
        version = subprocess.check_output([str(extracted / 'bin' / 'node'), '--version'], text=True).strip()
        if not version.startswith('v24.'):
            raise RuntimeError('Скачанный Node.js имеет неожиданную версию')
        extracted.rename(RUNTIME)
        print('Установлен ' + version, flush=True)
    return node


def main():
    os.chdir(ROOT)
    if not (ROOT / 'server.js').is_file() or not (ROOT / 'package.json').is_file():
        raise RuntimeError('Положите bootstrap.py рядом с server.js и package.json')
    # Prevent two console starts from installing into the same directory at once.
    with (ROOT / '.node-install.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        node = install_runtime()
        env = dict(os.environ)
        env['PATH'] = str(RUNTIME / 'bin') + os.pathsep + env.get('PATH', '')
        digest = hashlib.sha256((ROOT / 'package.json').read_bytes())
        if (ROOT / 'pnpm-lock.yaml').is_file():
            digest.update((ROOT / 'pnpm-lock.yaml').read_bytes())
        fingerprint = digest.hexdigest()
        marker = ROOT / 'node_modules' / '.linkigram-deps'
        installed = marker.is_file() and marker.read_text().strip() == fingerprint
        if installed:
            installed = subprocess.run([str(node), '-e', "require('sharp');require('busboy');require('node:sqlite')"],
                                       env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
        if not installed:
            print('Устанавливаю зависимости сервера…', flush=True)
            npm = RUNTIME / 'lib' / 'node_modules' / 'npm' / 'bin' / 'npm-cli.js'
            subprocess.run([str(node), str(npm), 'install', '--omit=dev', '--no-audit', '--no-fund'], env=env, check=True)
            marker.write_text(fingerprint + '\n')
    print('Запускаю API баннеров и Telegram-бота…', flush=True)
    sys.stdout.flush()
    os.execve(str(node), [str(node), str(ROOT / 'server.js')], env)


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError, ValueError, subprocess.CalledProcessError, tarfile.TarError) as error:
        # This launcher does not read, interpolate or print BOT_TOKEN.
        print('Ошибка запуска: ' + str(error), file=sys.stderr)
        sys.exit(1)
