import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[4]
ENGINE = ROOT / 'TMessagesProj/src/main/java/app/nimarkogram/messenger/plugins/PythonPluginsEngine.java'
CONTROLLER = ROOT / 'TMessagesProj/src/main/java/app/nimarkogram/messenger/plugins/PluginsController.java'


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


class PostCrashInventoryTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_bad_plugin_does_not_hide_other_installed_files(self):
        source = ENGINE.read_text(encoding='utf-8')
        actual = method(source, 'private void registerPluginsMetadataOnly(boolean forceDisabled)')
        harness = '''import java.io.*; import java.util.*;
class Main {
 static class Plugin {void setError(Exception e){} void setEnabled(boolean b){}}
 static class PluginsConstants {static class Settings{} } // replaced below by constant
 static class R {static class string {static final int NM_PluginCrashedDisabled=1;}}
 static class LocaleController {static String getString(int x){return "crashed";}}
 static class FileLog {static void e(String s,Throwable e){}}
 static class Preferences {boolean getBoolean(String s,boolean b){return b;}}
 static class PluginsController {static final String PREF_PLUGIN_ENABLED_KEY_PREFIX="enabled_"; File pluginsDir; Preferences preferences=new Preferences();
  Map<String,Plugin> plugins=new HashMap<>(); void clearPluginStartupActivations(){} void setPluginStartupActivationPending(String s,boolean b){}}
 static final Set<String> RECOVERY_BLOCKED_PLUGIN_IDS=new HashSet<>();
 PluginsController controller=new PluginsController(); PluginsController getPluginsController(){return controller;}
 Plugin createBoundedMetadataPlugin(File f,String id){if(id.equals("bad"))throw new IllegalStateException(); return new Plugin();}
 ''' + actual + '''
 public static void main(String[] args) {Main m=new Main(); m.controller.pluginsDir=new File(args[0]); m.registerPluginsMetadataOnly(true);
  if(!m.controller.plugins.containsKey("good")) throw new AssertionError("one bad plugin hid another"); }
}'''
        with tempfile.TemporaryDirectory() as folder:
            folder = pathlib.Path(folder)
            (folder / 'bad.py').touch()
            (folder / 'good.py').touch()
            file = folder / 'Main.java'
            file.write_text(harness, encoding='utf-8')
            javac = pathlib.Path(shutil.which('javac')).resolve()
            java = javac.with_name('java.exe' if os.name == 'nt' else 'java')
            subprocess.run([str(javac), '-encoding', 'UTF-8', str(file)], check=True)
            subprocess.run([str(java), '-cp', str(folder), 'Main', str(folder)], check=True)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_recovery_failure_keeps_inventory_and_enters_safe_mode(self):
        source = ENGINE.read_text(encoding='utf-8')
        actual = method(source, 'public void init(Runnable runnable)')
        actual = actual.replace('new org.telegram.messenger.DispatchQueue(', 'new DispatchQueue(')
        harness = '''import java.io.*; import java.util.*; import java.util.concurrent.atomic.*;
class Main {
 static class DispatchQueue {DispatchQueue(String n){} boolean isAlive(){return true;} void postRunnable(Runnable r){r.run();}}
 static class Utilities {static DispatchQueue pluginsQueue=new DispatchQueue("test");}
 static class AndroidUtilities {static void runOnUIThread(Runnable r){r.run();}}
 static class NimarkoConfig {static boolean pluginsSafeMode; static void setPluginsSafeMode(boolean value){pluginsSafeMode=value;}}
 static class FileLog {static void e(String s,Throwable e){} static void e(String s){} static void d(String s){} static void w(String s){}}
 static class SharedPreferences {Map<String,Object> getAll(){return new HashMap<>();} Editor edit(){return new Editor();} static class Editor {void remove(String s){} void apply(){}}}
 static class Controller {File pluginsDir=new File("."); SharedPreferences preferences=new SharedPreferences(); int notifications; void notifyPluginsChanged(){notifications++;} void clearPluginStartupActivations(){}}
 static class Obj {Obj getModule(String s){return this;} Obj callAttr(String s,Object... o){return this;} <T> T toJava(Class<T> cls){return cls.cast(new String[0]);}}
 static class PyException extends RuntimeException {}
 static class PipController {static PipController getInstance(){return new PipController();} void bootstrapRuntimeForPluginStartup(){}}
 static final AtomicBoolean PYTHON_RUNTIME_ABANDONED=new AtomicBoolean(false);
 Controller controller=new Controller(); boolean inventory; boolean failed; boolean callback; boolean loaded;
 Controller getPluginsController(){return controller;} Obj getPython(){return new Obj();}
 void registerPluginsMetadataOnly(boolean disabled){inventory=true;}
 void registerPluginsForSafeMode(){registerPluginsMetadataOnly(true);}
 void cleanupOrphanedHostInstallStages(){}
 void recoverInterruptedPluginUpdates(Controller c){if(failed)throw new IllegalStateException("simulated crash recovery failure");}
 void loadPlugins(Runnable r){loaded=true; if(r!=null)r.run();} void checkDevServer(){}
 ''' + actual + '''
 public static void main(String[] args){
  Main m=new Main(); m.failed=true; m.init(()->m.callback=true);
  if(!m.inventory || !NimarkoConfig.pluginsSafeMode || m.loaded || !m.callback || m.controller.notifications<2) throw new AssertionError("recovery fallback");
  NimarkoConfig.pluginsSafeMode=false; Main ok=new Main(); ok.init(()->ok.callback=true);
  if(!ok.inventory || !ok.loaded || !ok.callback || NimarkoConfig.pluginsSafeMode) throw new AssertionError("normal startup");
 }
}'''
        with tempfile.TemporaryDirectory() as folder:
            folder = pathlib.Path(folder)
            file = folder / 'Main.java'
            file.write_text(harness, encoding='utf-8')
            javac = pathlib.Path(shutil.which('javac')).resolve()
            java = javac.with_name('java.exe' if os.name == 'nt' else 'java')
            subprocess.run([str(javac), '-encoding', 'UTF-8', str(file)], check=True)
            subprocess.run([str(java), '-cp', str(folder), 'Main'], check=True)

    def test_recovery_is_guarded_before_crash_attribution(self):
        source = CONTROLLER.read_text(encoding='utf-8')
        start = source.index('PythonPluginsEngine.recoverInterruptedPluginUpdates(this);')
        end = source.index('String crashedPluginId =', start)
        self.assertIn('catch (Throwable recoveryFailure)', source[start:end])
        self.assertIn('NimarkoConfig.setPluginsSafeMode(true)', source[start:end])


if __name__ == '__main__':
    unittest.main()
