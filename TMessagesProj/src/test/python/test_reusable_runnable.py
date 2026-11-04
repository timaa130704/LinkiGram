import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[4]
SOURCE = ROOT / 'TMessagesProj/src/main/java/app/nimarkogram/messenger/plugins/utils/PythonReusableRunnable.java'


class ReusableRunnableTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_repeat_defer_and_revoke(self):
        files = {
            'android/os/Looper.java': '''package android.os; public class Looper { public static Looper myLooper(){return null;} public static Looper getMainLooper(){return new Looper();} }''',
            'android/os/Handler.java': '''package android.os; public class Handler { public Handler(Looper l){} public boolean post(Runnable r){r.run();return true;} }''',
            'com/chaquo/python/PyObject.java': '''package com.chaquo.python; public class PyObject { public int calls; public void call(){calls++;} }''',
            'org/telegram/messenger/DispatchQueue.java': '''package org.telegram.messenger; public class DispatchQueue extends Thread { public boolean postRunnable(Runnable r){r.run();return true;} }''',
            'org/telegram/messenger/Utilities.java': '''package org.telegram.messenger; public class Utilities { public static DispatchQueue pluginsQueue = new DispatchQueue(); }''',
            'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger; public class FileLog { public static void e(String s, Throwable t){throw new AssertionError(t);} }''',
            'app/nimarkogram/messenger/plugins/ui/PluginUiRegistry.java': '''package app.nimarkogram.messenger.plugins.ui; import app.nimarkogram.messenger.plugins.PluginsController.PluginRuntimeToken; public class PluginUiRegistry { public static class DecorChildrenSnapshot {} public static DecorChildrenSnapshot captureDecorChildren(){return null;} public static void adoptNewDecorChildren(PluginRuntimeToken t,DecorChildrenSnapshot s){} }''',
            'app/nimarkogram/messenger/plugins/PluginsController.java': '''package app.nimarkogram.messenger.plugins; public class PluginsController {
                public interface RuntimeCallbackHolder {void onPluginRuntimeActive(); void revokePluginRuntime();}
                public static class PluginRuntimeToken {public String getPluginId(){return "test";}}
                public static final int RUNTIME_TASK_DROP=0, RUNTIME_TASK_WAIT=1, RUNTIME_TASK_RUN=2;
                public int decision=RUNTIME_TASK_RUN; public RuntimeCallbackHolder holder; public int calls;
                private static final PluginsController instance=new PluginsController(); public static PluginsController getInstance(){return instance;}
                public boolean registerRuntimeCallbackHolder(PluginRuntimeToken t,RuntimeCallbackHolder h){holder=h;return true;}
                public void unregisterRuntimeCallbackHolder(PluginRuntimeToken t,RuntimeCallbackHolder h){holder=null;}
                public int getPluginRuntimeTaskDecision(PluginRuntimeToken t){return decision;}
                public int deferRuntimeCallback(PluginRuntimeToken t,RuntimeCallbackHolder h){return decision;}
                public boolean enterPluginRuntime(PluginRuntimeToken t){calls++;return true;} public void exitPluginRuntime(PluginRuntimeToken t){calls--;}
                public Watchdog getWatchdog(){return new Watchdog();} public static class Watchdog {public void onPluginExecutionStarted(String s){} public void onPluginExecutionFinished(String s){} public void onPluginExecutionFailed(String s,Throwable t){}}
            }''',
            'Main.java': '''import app.nimarkogram.messenger.plugins.PluginsController; import app.nimarkogram.messenger.plugins.utils.PythonReusableRunnable; import com.chaquo.python.PyObject;
                public class Main {public static void main(String[] args){
                    PluginsController c=PluginsController.getInstance(); PyObject p=new PyObject();
                    PythonReusableRunnable r=new PythonReusableRunnable(p,new PluginsController.PluginRuntimeToken());
                    r.run(); r.run(); if(p.calls!=2 || c.calls!=0) throw new AssertionError("repeat");
                    c.decision=1; r.run(); if(p.calls!=2) throw new AssertionError("wait");
                    c.decision=2; r.onPluginRuntimeActive(); if(p.calls!=3) throw new AssertionError("resume");
                    r.revokePluginRuntime(); r.run(); if(p.calls!=3) throw new AssertionError("revoke");
                }}''',
        }
        with tempfile.TemporaryDirectory() as temp:
            temp = pathlib.Path(temp)
            for name, content in files.items():
                path = temp / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding='utf-8')
            command = ['javac', '-encoding', 'UTF-8', '-d', str(temp), str(SOURCE)]
            command.extend(str(temp / name) for name in files)
            subprocess.run(command, check=True, capture_output=True, text=True)
            java = pathlib.Path(shutil.which('javac')).resolve().with_name('java.exe' if __import__('os').name == 'nt' else 'java')
            subprocess.run([str(java), '-cp', str(temp), 'Main'], check=True)


if __name__ == '__main__':
    unittest.main()
