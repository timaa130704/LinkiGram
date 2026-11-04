import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[4]
ENGINE = ROOT / 'TMessagesProj/src/main/java/app/nimarkogram/messenger/plugins/PythonPluginsEngine.java'


def extract_method(text, signature):
    start = text.index(signature)
    opening = text.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]


class QuarantinePersistenceTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_crash_marker_timestamps(self):
        source = ENGINE.read_text(encoding='utf-8')
        actual_method = extract_method(source, 'private boolean isQuarantineStale(')
        java = '''import java.util.*;
class Main {
 static class Controller { android.content.SharedPreferences preferences; }
 Controller controller = new Controller(); Controller getPluginsController() { return controller; }
 ''' + actual_method + '''
 static void check(boolean expected, boolean actual) { if (expected != actual) throw new AssertionError(); }
 public static void main(String[] args) {
  Main m = new Main(); android.content.SharedPreferences p = new android.content.SharedPreferences(); m.controller.preferences = p;
  check(false, m.isQuarantineStale("pet"));
  p.put("plugin_ok_at_pet", 100); p.put("plugin_crashed_at_pet", 200); check(false, m.isQuarantineStale("pet"));
  p.put("plugin_ok_at_pet", 300); check(true, m.isQuarantineStale("pet"));
  p.put("plugin_crashed_at_pet", 0); check(true, m.isQuarantineStale("pet"));
 }
}'''
        prefs = '''package android.content; import java.util.*; public class SharedPreferences {
 private final Map<String,Long> values = new HashMap<>(); public void put(String key,long value){ values.put(key,value); }
 public long getLong(String key,long fallback){ return values.getOrDefault(key,fallback); }
}'''
        with tempfile.TemporaryDirectory() as directory:
            directory = pathlib.Path(directory)
            (directory / 'android/content').mkdir(parents=True)
            (directory / 'Main.java').write_text(java, encoding='utf-8')
            (directory / 'android/content/SharedPreferences.java').write_text(prefs, encoding='utf-8')
            javac = pathlib.Path(shutil.which('javac')).resolve()
            java_exe = javac.with_name('java.exe' if os.name == 'nt' else 'java')
            subprocess.run([str(javac), '-d', str(directory), str(directory / 'Main.java'),
                            str(directory / 'android/content/SharedPreferences.java')], check=True)
            subprocess.run([str(java_exe), '-cp', str(directory), 'Main'], check=True)

    def test_recovery_preserves_preference_and_success_is_not_recorded_on_failure(self):
        source = ENGINE.read_text(encoding='utf-8')
        startup = source[source.index('boolean hasCrashMarker'):source.index('if (result == null || result.error != null')]
        stale = startup[startup.index('if (hasCrashMarker && isQuarantineStale'):startup.index('} else if (hasCrashMarker)')]
        self.assertIn('.remove("plugin_crashed_" + pluginId)', stale)
        self.assertNotIn('.putBoolean(', stale)
        self.assertIn('NM_PluginCrashedDisabled', startup[startup.index('} else if (hasCrashMarker)'):])
        load = source[source.index('private void loadPlugin(\n            String str'):source.index('private PyObject findPluginClass')]
        self.assertNotIn('plugin_ok_at_', load)
        enable = source[source.index('boolean committed = getPluginsController().commitPluginRuntime('):]
        self.assertLess(enable.index('if (!committed) throw'), enable.index('.putLong("plugin_ok_at_"'))


if __name__ == '__main__':
    unittest.main()
