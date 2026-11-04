import importlib.util
import json
import os
import sys
import tempfile
import types
import unittest
from unittest import mock


SOURCES = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'python'))


class ElyxSettingsTest(unittest.TestCase):
    def test_persist_and_clear_using_host_store(self):
        sys.path.insert(0, SOURCES)
        with mock.patch.dict(sys.modules, {'android_utils': types.SimpleNamespace(log=lambda *args: None)}):
            import plugin_settings
            spec = importlib.util.spec_from_file_location(
                'elyx_settings_test', os.path.join(SOURCES, 'elyx_runtime', 'settings.py'))
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            with tempfile.TemporaryDirectory() as directory:
                class EmptyPrefs:
                    def entrySet(self):
                        return types.SimpleNamespace(iterator=lambda: types.SimpleNamespace(hasNext=lambda: False))
                # Android can fsync a directory; Windows cannot open one with
                # os.open. Keep the real settings lifecycle and use a portable
                # snapshot writer for this host-side integration test.
                def portable_write(snapshot):
                    with open(snapshot.path, 'w', encoding='utf-8') as stream:
                        json.dump(snapshot.values, stream)
                writer_patch = mock.patch.object(plugin_settings, '_write_snapshot', portable_write)
                writer_patch.start()
                plugin_settings.init(directory, EmptyPrefs())
                try:
                    with mock.patch.object(module, '_reload') as reload_screen:
                        settings = module.SettingsController('example')
                        settings.set('value', {'x': 1}, reload_settings=True)
                        self.assertEqual(plugin_settings.get_setting('example', 'value', None), {'x': 1})
                        self.assertEqual(settings.get_settings(), {'value': {'x': 1}})
                        plugin_settings.flush_settings(timeout=2)
                        plugin_settings.shutdown(timeout=2)
                        plugin_settings.init(directory, EmptyPrefs())
                        self.assertEqual(settings['value'], {'x': 1})
                        settings.clear_settings()
                        self.assertEqual(settings.get_settings(), {})
                        self.assertEqual(reload_screen.call_count, 2)
                finally:
                    plugin_settings.shutdown(timeout=2)
                    writer_patch.stop()
        sys.path.remove(SOURCES)


if __name__ == '__main__':
    unittest.main()
