import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[4]
SOURCE = ROOT / 'TMessagesProj/src/main/java/app/nimarkogram/messenger/plugins/ui/PluginsActivity.java'


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


class PluginListResumeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_resume_refreshes_missed_plugin_update(self):
        implementation = method(SOURCE.read_text(encoding='utf-8'), 'public void onResume()')
        implementation = implementation.replace('app.nimarkogram.messenger.plugins.PluginsConstants', 'PluginsConstants')
        implementation = implementation.replace('app.nimarkogram.messenger.plugins.PythonPluginsEngine', 'PythonPluginsEngine')
        code = '''import java.util.*;
class Parent {public void onResume(){}}
class Main extends Parent {
 static class PluginsController {interface PluginsEngine {} static Map<String,PluginsEngine> engines=new HashMap<>(); static final PluginsController instance=new PluginsController(); Map<String,Object> plugins=new HashMap<>(); static PluginsController getInstance(){return instance;}}
 static class PluginsConstants {static final String PYTHON="python";}
 static class PythonPluginsEngine implements PluginsController.PluginsEngine {int scans; void rescanInstalledPluginFiles(){scans++;}}
 static class NimarkoConfig {static boolean pluginsEngine=true;}
 static class AndroidUtilities {static boolean searchVisible; static void updateViewVisibilityAnimated(Object view,boolean shown,float x,boolean animated){searchVisible=shown;}}
 static class Adapter {int rows; void update(boolean animated){rows=PluginsController.getInstance().plugins.size();}}
 static class ListView {Adapter adapter=new Adapter();}
 boolean uiLifecycleActive=true; ListView listView=new ListView(); Object searchItem=new Object(); int buttonUpdates;
 void updateAddButtonVisibility(boolean animated){buttonUpdates++;}
 ''' + implementation + '''
 public static void main(String[] args){Main m=new Main(); PythonPluginsEngine engine=new PythonPluginsEngine(); PluginsController.engines.put("python",engine); m.onResume(); if(m.listView.adapter.rows!=0 || engine.scans!=1)throw new AssertionError();
  PluginsController.getInstance().plugins.put("pet",new Object()); m.onResume();
  if(m.listView.adapter.rows!=1 || !AndroidUtilities.searchVisible)throw new AssertionError("missed update");
  if(engine.scans!=2)throw new AssertionError("missing rescan"); m.uiLifecycleActive=false; m.listView.adapter.rows=7; m.onResume(); if(m.listView.adapter.rows!=7 || engine.scans!=2)throw new AssertionError("destroyed screen");}
}'''
        with tempfile.TemporaryDirectory() as folder:
            folder = pathlib.Path(folder)
            path = folder / 'Main.java'
            path.write_text(code, encoding='utf-8')
            javac = pathlib.Path(shutil.which('javac')).resolve()
            java = javac.with_name('java.exe' if os.name == 'nt' else 'java')
            subprocess.run([str(javac), '-encoding', 'UTF-8', str(path)], check=True)
            subprocess.run([str(java), '-cp', str(folder), 'Main'], check=True)


if __name__ == '__main__':
    unittest.main()
