"""Prepare reviewed conflict resolutions without modifying project files."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / 'ci-apk-41906023326/telegram-13-reference'
pattern = re.compile(r'^<<<<<<< [^\n]*\n(.*?)^=======\n(.*?)^>>>>>>> [^\n]*\n', re.M | re.S)
def ours(a, b): return a
def theirs(a, b): return b
def both(a, b): return a + b
def story(a, b):
    marker = '        if (isStory) {'
    assert marker in b
    return a + b[b.index(marker):]
def upload(a, b):
    marker = '                // TDLib uploads premium files over 8 sessions.'
    assert marker in b
    return a + b[b.index(marker):]
def alert(a, b):
    assert 'backgroundColorKey = Theme.key_dialogBackground' in b
    return b + '        if (app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled()) dimAlpha = 0.2f;\n'
def bottom(a, b):
    assert 'FilterTrackingDrawable' in b and 'ClientTheme.surfaceDrawable' in a
    return ('        shadowDrawable = new FilterTrackingDrawable(app.nimarkogram.messenger.appearance.ClientTheme.surfaceDrawable(\n'
            '                context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate(), getThemedColor(Theme.key_dialogBackground), true));\n'
            '        shadowDrawable.setColorFilter(themedBackgroundFilter = new PorterDuffColorFilter(internalBackgroundColor = getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));\n')
def message(a, b):
    assert 'checkColorOsCache()' in a and 'isCompact()' in b
    return '        checkColorOsCache();\n' + b
def theme1(a, b):
    assert 'getCompactColor(key, provider)' in b
    return b.replace('return provider.getColor(key);', 'return app.nimarkogram.messenger.appearance.LinkiGramYouTheme.resolveColor(key, provider.getColor(key));')

rules = {
    'gradle.properties': [ours],
    'TMessagesProj/build.gradle': [ours],
    'TMessagesProj/src/main/java/androidx/recyclerview/widget/RecyclerView.java': [theirs, theirs],
    'TMessagesProj/src/main/java/org/telegram/messenger/FileLoadOperation.java': [story, both],
    'TMessagesProj/src/main/java/org/telegram/messenger/FileUploadOperation.java': [upload],
    'TMessagesProj/src/main/java/org/telegram/messenger/browser/Browser.java': [both],
    'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/AlertDialog.java': [alert],
    'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BottomSheet.java': [bottom],
    'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/MessageDrawable.java': [message],
    'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java': [theme1, both],
}
for name, resolvers in rules.items():
    content = (root / 'merge-previews' / name).read_text(encoding='utf-8')
    matches = list(pattern.finditer(content))
    if len(matches) != len(resolvers): raise RuntimeError(f'{name}: {len(matches)} conflicts, expected {len(resolvers)}')
    index = [0]
    def replace(match):
        resolve = resolvers[index[0]]
        index[0] += 1
        return resolve(match.group(1), match.group(2))
    result = pattern.sub(replace, content)
    output = root / 'resolved-previews' / name
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(result, encoding='utf-8', newline='\n')
    print('Prepared', name, len(matches))
