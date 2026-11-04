import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[4]
SOURCE = ROOT / 'TMessagesProj/src/main/java/app/nimarkogram/messenger/plugins/PythonPluginsEngine.java'


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


class PluginRescanTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_rescan_preserves_live_plugins_and_user_preferences(self):
        actual = method(SOURCE.read_text(encoding='utf-8'), 'public void rescanInstalledPluginFiles()')
        code = '''import java.io.*; import java.util.*; import java.util.concurrent.*;
class Main {
 static class Queue {void postRunnable(Runnable r){r.run();}} static class Utilities {static Queue pluginsQueue=new Queue();}
 static class Plugin {boolean enabled; Exception error; void setEnabled(boolean b){enabled=b;} void setError(Exception e){error=e;}}
 static class Preferences {Map<String,Boolean> values=new HashMap<>(); boolean getBoolean(String k,boolean d){return values.getOrDefault(k,d);}}
 static class PluginsController {File directory; Preferences preferences=new Preferences(); Map<String,Plugin> plugins=new ConcurrentHashMap<>(); int notifications; File getPluginsDir(){return directory;} void notifyPluginsChanged(){notifications++;}}
 static class R {static class string {static final int NM_PluginCrashedDisabled=1;}}
 static class LocaleController {static String getString(int i){return "crashed";}}
 static class FileLog {static void e(String s,Throwable t){}}
 static Set<String> RECOVERY_BLOCKED_PLUGIN_IDS=new HashSet<>(); PluginsController controller=new PluginsController();
 PluginsController getPluginsController(){return controller;} static File pluginDeleteMarker(File d,String id){return new File(d,id+".delete");}
 Plugin createBoundedMetadataPlugin(File f,String id){return new Plugin();}
 ''' + actual + '''
 public static void main(String[] args){Main m=new Main(); m.controller.directory=new File(args[0]);
  Plugin active=new Plugin(); active.enabled=true; m.controller.plugins.put("active",active); m.controller.preferences.values.put("plugin_enabled_pet",true);
  m.rescanInstalledPluginFiles();
  if(m.controller.plugins.get("active")!=active || !active.enabled)throw new AssertionError("live plugin replaced");
  if(!m.controller.plugins.containsKey("pet") || m.controller.plugins.containsKey("deleted"))throw new AssertionError("scan result");
  if(!m.controller.preferences.values.get("plugin_enabled_pet") || m.controller.notifications!=1)throw new AssertionError("preference changed");
  m.rescanInstalledPluginFiles(); if(m.controller.notifications!=1)throw new AssertionError("duplicate update");
 }
}'''
        with tempfile.TemporaryDirectory() as folder:
            folder = pathlib.Path(folder)
            for name in ('active.py', 'pet.py', 'deleted.py', 'deleted.delete'):
                (folder / name).touch()
            file = folder / 'Main.java'
            file.write_text(code, encoding='utf-8')
            javac = pathlib.Path(shutil.which('javac')).resolve()
            java = javac.with_name('java.exe' if os.name == 'nt' else 'java')
            subprocess.run([str(javac), '-encoding', 'UTF-8', str(file)], check=True)
            subprocess.run([str(java), '-cp', str(folder), 'Main', str(folder)], check=True)


if __name__ == '__main__':
    unittest.main()
