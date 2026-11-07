import importlib.util
import os
import sys
import types
import unittest
from unittest import mock

SOURCE = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'python', 'ui', 'settings.py'))


class SettingsCompatTest(unittest.TestCase):
    def test_exteraless_factory_and_custom_constructors(self):
        class Settings:
            def __getattr__(self, name): return name
        modules = {
            'android': types.ModuleType('android'),
            'android.view': types.SimpleNamespace(View=type('View', (), {})),
            'app': types.ModuleType('app'),
            'app.nimarkogram': types.ModuleType('app.nimarkogram'),
            'app.nimarkogram.messenger': types.ModuleType('app.nimarkogram.messenger'),
            'app.nimarkogram.messenger.plugins': types.SimpleNamespace(
                PluginsConstants=types.SimpleNamespace(Settings=Settings())),
        }
        with mock.patch.dict(sys.modules, modules):
            spec = importlib.util.spec_from_file_location('settings_compat_test', SOURCE)
            module = importlib.util.module_from_spec(spec)
            sys.modules[spec.name] = module
            try:
                spec.loader.exec_module(module)
                factory = module.SimpleSettingFactory(create_view=lambda context: ('view', context))
                row = module.Custom(factory=factory, factory_args=(), link_alias='item')
                self.assertEqual(row.create_view('context'), ('view', 'context'))
                self.assertEqual(row.link_alias, 'item')
                positional = module.SimpleSettingFactory(lambda context: ('positional', context))
                self.assertEqual(positional().create_view('ctx'), ('positional', 'ctx'))
                self.assertEqual(module.Selector('id', 'Title', 0, ['a'], subtext='Help').subtext, 'Help')
                slider = module.Slider('level', 'Level', default=5, min=0, max=10, step=2)
                self.assertEqual(slider.normalize(5), 6)
                self.assertEqual(slider.normalize(99), 10)
                with self.assertRaises(ValueError):
                    module.Slider('bad', 'Bad', min=1, max=1)
            finally:
                sys.modules.pop(spec.name, None)


if __name__ == '__main__': unittest.main()
