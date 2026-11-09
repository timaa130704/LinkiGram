#!/usr/bin/env bash
#
# Overlay the LinkiGram desktop sources and patches onto an upstream
# Telegram Desktop checkout. The base checkout (default: desktop/tdesktop,
# cloned by CI and git-ignored here) is never vendored; this script and the
# tracked desktop/linkigram and desktop/patches directories are the source
# of truth.
#
# Usage: desktop/apply-linkigram.sh [path-to-tdesktop]

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tdesktop="${1:-$here/tdesktop}"

if [ ! -d "$tdesktop/Telegram/SourceFiles" ]; then
	echo "Not a Telegram Desktop checkout: $tdesktop" >&2
	echo "Usage: $0 [path-to-tdesktop]" >&2
	exit 1
fi

echo "Overlaying LinkiGram sources into $tdesktop"
cp -R "$here/linkigram/." "$tdesktop/"

for patch in "$here"/patches/*.patch; do
	name="$(basename "$patch")"
	if git -C "$tdesktop" apply --check "$patch" 2>/dev/null; then
		git -C "$tdesktop" apply "$patch"
		echo "Applied $name"
	elif git -C "$tdesktop" apply --reverse --check "$patch" 2>/dev/null; then
		echo "Already applied $name"
	else
		echo "Cannot apply $name cleanly; upstream may have moved." >&2
		exit 1
	fi
done

echo "LinkiGram desktop overlay is ready in $tdesktop"
