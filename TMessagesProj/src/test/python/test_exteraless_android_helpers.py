import ast
import importlib.util
import pathlib
import sys
import types
import unittest
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parents[4]
PATH = ROOT / 'TMessagesProj/src/main/python/android_utils.py'


class AndroidHelperCompatibilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.owner = object()
        cls.active = True
        cls.executing = False
        cls.errors = []
        cls.proxies = []
        android = types.ModuleType('android')
        os_module = types.ModuleType('android.os')
        os_module.Looper = object
        telegram = types.ModuleType('org.telegram.messenger')
        telegram.AndroidUtilities = object
        app_utils = types.ModuleType('app.nimarkogram.messenger.utils')
        app_utils.AppUtils = object
        ui = types.ModuleType('app.nimarkogram.messenger.plugins.ui')
        ui.PluginViewListener = object
        plugins = types.ModuleType('app.nimarkogram.messenger.plugins')
        controller = types.SimpleNamespace(isPluginRuntimeExecuting=lambda owner: cls.executing)
        plugins.PluginsController = types.SimpleNamespace(getInstance=lambda: controller)
        runtime = types.ModuleType('plugin_runtime')
        runtime.capture_callback_owner = lambda fn=None: cls.owner
        runtime.make_runnable = lambda fn: fn
        def owned(owner, fn, args, kwargs, default):
            return fn(*args, **kwargs) if cls.active and owner is cls.owner else default
        runtime._run_owned_callback = owned
        def proxy(target, interfaces, owner):
            if owner is None:
                raise RuntimeError('missing owner')
            cls.proxies.append((target, interfaces, owner))
            return target
        runtime.make_interface_proxy = proxy
        java = types.ModuleType('java')
        java.jclass = lambda name: name
        modules = {'android': android, 'android.os': os_module,
                   'org': types.ModuleType('org'), 'org.telegram': types.ModuleType('org.telegram'),
                   'org.telegram.messenger': telegram,
                   'app': types.ModuleType('app'), 'app.nimarkogram': types.ModuleType('app.nimarkogram'),
                   'app.nimarkogram.messenger': types.ModuleType('app.nimarkogram.messenger'),
                   'app.nimarkogram.messenger.utils': app_utils,
                   'app.nimarkogram.messenger.plugins': plugins,
                   'app.nimarkogram.messenger.plugins.ui': ui,
                   'plugin_runtime': runtime, 'java': java}
        cls.patch = mock.patch.dict(sys.modules, modules)
        cls.patch.start()
        spec = importlib.util.spec_from_file_location('_sdk_android_test', PATH)
        cls.sdk = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.sdk)
        cls.sdk.log = cls.errors.append

    @classmethod
    def tearDownClass(cls):
        cls.patch.stop()

    def setUp(self):
        type(self).active = True
        type(self).executing = False
        self.errors.clear()
        self.proxies.clear()

    def test_safe_call_preserves_keyword_arguments_and_swallows_errors(self):
        self.assertEqual(self.sdk.safe_call(lambda **kw: kw['default'], default=3), 3)
        self.assertIsNone(self.sdk.safe_call(lambda: 1 / 0))
        self.assertIn('ZeroDivisionError', self.errors[-1])
        self.assertIsNone(self.sdk.safe_call(None))

    def test_safe_call_runs_in_current_initialization_scope(self):
        type(self).active = False
        type(self).executing = True
        self.assertEqual(self.sdk.safe_call(lambda: 'initializing'), 'initializing')

    def test_listener_callbacks_and_unloaded_owner(self):
        events = []
        listener = self.sdk.OnSeekBarChangeListener(
            lambda view, progress, user: events.append((view, progress, user)),
            lambda view: events.append(('start', view)))
        listener.onStartTrackingTouch('bar')
        listener.onProgressChanged('bar', 7, True)
        listener.onStopTrackingTouch('bar')
        self.assertEqual(events, [('start', 'bar'), ('bar', 7, True)])
        self.assertIs(self.proxies[0][2], self.owner)
        type(self).active = False
        listener.onProgressChanged('bar', 9, True)
        self.assertEqual(len(events), 2)

    def test_touch_and_key_boolean_results(self):
        touch = self.sdk.OnTouchListener(lambda view, event: event == 'tap')
        key = self.sdk.OnKeyListener(lambda view, code, event: code == 42)
        self.assertTrue(touch.onTouch('view', 'tap'))
        self.assertFalse(touch.onTouch('view', 'swipe'))
        self.assertTrue(key.onKey('view', 42, 'down'))
        type(self).active = False
        self.assertFalse(key.onKey('view', 42, 'down'))

if __name__ == '__main__':
    unittest.main()
