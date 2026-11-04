import importlib.util
import os
import sys
import tempfile
import types
import unittest
from unittest import mock


ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'python', 'elyx_runtime'))


class ElyxMetadataSafetyTest(unittest.TestCase):
    def test_python_metadata_does_not_execute(self):
        package = types.ModuleType('_elyx_metadata_test')
        package.__path__ = [ROOT]
        with mock.patch.dict(sys.modules, {'_elyx_metadata_test': package, 'yaml': types.ModuleType('yaml')}):
            spec = importlib.util.spec_from_file_location(
                '_elyx_metadata_test.metadata', os.path.join(ROOT, 'metadata.py'))
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            with tempfile.TemporaryDirectory() as directory:
                marker = os.path.join(directory, 'executed')
                source = ("id = 'example'\nname: str = 'Example'\n"
                          "requirements = ['requests']\n"
                          f"open({marker!r}, 'w').write('bad')\n")
                result = module.parse_mapping_file('metadata.py', source.encode())
                self.assertEqual(result['id'], 'example')
                self.assertEqual(result['name'], 'Example')
                self.assertEqual(result['requirements'], ['requests'])
                self.assertFalse(os.path.exists(marker))


if __name__ == '__main__':
    unittest.main()
