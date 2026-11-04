import importlib
import json
import os
import sys
import types
import unittest
from unittest import mock

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'python'))


class GeneratedClassAdapterTest(unittest.TestCase):
    def test_concrete_subclass_dispatch_and_runtime_revocation(self):
        token = object()
        active = [True]
        fake_java = types.ModuleType('java')
        fake_java.jclass = lambda name: name
        runtime = types.ModuleType('plugin_runtime')
        runtime.capture_callback_owner = lambda *args: token
        runtime.make_interface_proxy = lambda *args, **kwargs: None
        runtime.run_owned_callback = lambda owner, fn, *args: fn(*args) if owner is token and active[0] else None
        sys.path.insert(0, ROOT)
        try:
            with mock.patch.dict(sys.modules, {'java': fake_java, 'plugin_runtime': runtime}):
                for key in ('extera_utils.classes', 'extera_utils._generated_classes'):
                    sys.modules.pop(key, None)
                classes = importlib.import_module('extera_utils.classes')
                generated = importlib.import_module('extera_utils._generated_classes')
                specs = []
                service = types.SimpleNamespace(
                    generateProxyClass=lambda owner, spec: specs.append((owner, json.loads(spec))) or 'sample:key',
                    newProxyInstance=lambda owner, key, signature, args, peer: object())
                with mock.patch.object(generated, 'PluginServices', service), \
                     mock.patch.object(generated, '_owner_plugin_id', lambda: 'sample'), \
                     mock.patch.object(generated, '_object_array', list):
                    @classes.java_subclass('android.view.View')
                    class View(classes.Base):
                        @classes.joverride
                        def getValue(self):
                            return 42
                    peer = View.new_instance()
                    self.assertEqual(specs[0][1]['superclass'], 'android.view.View')
                    self.assertEqual(peer.__extera_dispatch__('getValue|*', peer.java), 42)
                    active[0] = False
                    self.assertIsNone(peer.__extera_dispatch__('getValue|*', peer.java))
        finally:
            sys.path.remove(ROOT)
            for key in ('extera_utils.classes', 'extera_utils._generated_classes'):
                sys.modules.pop(key, None)


if __name__ == '__main__': unittest.main()
