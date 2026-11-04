import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[4]
SOURCE = ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java'


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


class PluginAppStartupTest(unittest.TestCase):
    def test_application_startup_calls_engine_after_account_initialization(self):
        source = SOURCE.read_text(encoding='utf-8')
        app_init = method(source, 'public static void postInitApplication()')
        self.assertIn('initPluginEngine();', app_init)
        self.assertLess(app_init.index('UserConfig.getInstance(a).loadConfig();'),
                        app_init.index('initPluginEngine();'))

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_main_process_initializes_but_restart_process_does_not(self):
        actual = method(SOURCE.read_text(encoding='utf-8'), 'private static void initPluginEngine()')
        actual = actual.replace('android.os.Build', 'Build').replace('android.app.Application', 'Application')
        actual = actual.replace('app.nimarkogram.messenger.plugins.PluginsController', 'PluginsController')
        actual = actual.replace('android.util.Log', 'Log')
        code = '''class Main {
 static class Build {static class VERSION {static int SDK_INT=30;}}
 static class Application {static String process="app"; static String getProcessName(){return process;}}
 static class Context {String getPackageName(){return "app";}} static Context applicationContext=new Context();
 static class PluginsController {static PluginsController instance=new PluginsController(); int calls; static PluginsController getInstance(){return instance;} void init(){calls++;}}
 static class Log {static void e(String tag,String message,Throwable failure){}}
 ''' + actual + '''
 public static void main(String[] args){
  initPluginEngine(); if(PluginsController.instance.calls!=1)throw new AssertionError("main not started");
  Application.process="app:nimarko_restart"; initPluginEngine(); if(PluginsController.instance.calls!=1)throw new AssertionError("secondary started");
  Build.VERSION.SDK_INT=27; initPluginEngine(); if(PluginsController.instance.calls!=2)throw new AssertionError("older Android");
 }
}'''
        with tempfile.TemporaryDirectory() as folder:
            folder = pathlib.Path(folder)
            file = folder / 'Main.java'
            file.write_text(code, encoding='utf-8')
            javac = pathlib.Path(shutil.which('javac')).resolve()
            java = javac.with_name('java.exe' if os.name == 'nt' else 'java')
            subprocess.run([str(javac), '-encoding', 'UTF-8', str(file)], check=True)
            subprocess.run([str(java), '-cp', str(folder), 'Main'], check=True)


if __name__ == '__main__':
    unittest.main()
