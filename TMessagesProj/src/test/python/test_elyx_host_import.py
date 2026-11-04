import os
import sys
import tempfile
import types
import unittest
import zipfile
from unittest import mock

SOURCES = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'python'))
sys.path.insert(0, SOURCES)


class Token:
    def getPluginId(self): return 'Sample'
    def getGeneration(self): return 3
    def getInstanceId(self): return 7


class ElyxHostImportTest(unittest.TestCase):
    def test_archive_import_unload_and_reload(self):
        import plugin_imports
        from elyx_runtime import get_state
        token = Token()
        controller = types.SimpleNamespace(
            RUNTIME_TASK_DROP=-1,
            isPluginRuntimeCurrent=lambda value: value is token,
            getPluginRuntimeTaskDecision=lambda value: 0,
            captureCurrentPluginRuntime=lambda: token)
        bridge = types.SimpleNamespace(PluginsController=types.SimpleNamespace(getInstance=lambda: controller))
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, 'Sample.py')
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('refmap.json', '{"metainfo":"meta.json","main":"main.py","assets":"assets"}')
                archive.writestr('meta.json', '{"id":"Sample","name":"Sample"}')
                archive.writestr('main.py', 'from elyx import assets\nimport helper\nvalue = helper.value\nasset = assets.get("code.dex").path\n')
                archive.writestr('helper.py', 'value = 42\n')
                archive.writestr('assets/code.dex', b'dex\n035\0')
            with mock.patch.dict(sys.modules, {'app': types.ModuleType('app'),
                    'app.nimarkogram': types.ModuleType('app.nimarkogram'),
                    'app.nimarkogram.messenger': types.ModuleType('app.nimarkogram.messenger'),
                    'app.nimarkogram.messenger.plugins': bridge}):
                module = plugin_imports.load_plugin('Sample', path, token)
                self.assertEqual(module.value, 42)
                self.assertEqual(module.__nimarko_runtime_token__, token)
                self.assertTrue(os.path.isfile(str(module.asset)))
                self.assertIsNotNone(get_state('Sample'))
                plugin_imports.remove_plugin('Sample', token)
                self.assertIsNone(get_state('Sample'))
                self.assertNotIn(module.__name__, sys.modules)
                module = plugin_imports.load_plugin('Sample', path, token)
                self.assertEqual(module.value, 42)
                plugin_imports.remove_plugin('Sample', token)


if __name__ == '__main__': unittest.main()
