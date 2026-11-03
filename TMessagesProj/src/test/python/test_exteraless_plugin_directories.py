import ast
import os
import pathlib
import re
import tempfile
import unittest
from typing import Optional

ROOT = pathlib.Path(__file__).resolve().parents[4]
SOURCE = ROOT / 'TMessagesProj/src/main/python/file_utils.py'


class PluginDirectoryCompatibilityTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        owner = type('Token', (), {'getPluginId': lambda self: 'example_plugin'})()
        tree = ast.parse(SOURCE.read_text())
        selected = {'_plugin_directory', 'get_plugin_dir', 'get_plugin_cache_dir'}
        functions = [node for node in tree.body
                     if isinstance(node, ast.FunctionDef) and node.name in selected]
        namespace = {'os': os, 're': re, 'Optional': Optional,
                     'capture_callback_owner': lambda: owner,
                     'get_plugins_dir': lambda: os.path.join(self.tmp.name, 'plugins'),
                     'get_cache_dir': lambda: os.path.join(self.tmp.name, 'cache')}
        exec(compile(ast.Module(body=functions, type_ignores=[]), str(SOURCE), 'exec'), namespace)
        self.sdk = namespace

    def test_owner_directories_are_created_and_stable(self):
        data = self.sdk['get_plugin_dir']()
        cache = self.sdk['get_plugin_cache_dir']()
        self.assertEqual(data, os.path.join(self.tmp.name, 'plugins', '.data', 'example_plugin'))
        self.assertEqual(cache, os.path.join(self.tmp.name, 'cache', 'plugins', 'example_plugin'))
        self.assertTrue(os.path.isdir(data))
        self.assertTrue(os.path.isdir(cache))
        self.assertEqual(data, self.sdk['get_plugin_dir']())

    def test_invalid_ids_cannot_escape_the_root(self):
        for plugin_id in ('../escape', '/absolute', 'a/b', '', 'a', 'x' * 33):
            self.assertIsNone(self.sdk['get_plugin_dir'](plugin_id))
            self.assertIsNone(self.sdk['get_plugin_cache_dir'](plugin_id))
        self.assertEqual(self.sdk['get_plugin_dir']('plugin2'),
                         os.path.join(self.tmp.name, 'plugins', '.data', 'plugin2'))

    def test_no_owner_returns_none(self):
        self.sdk['capture_callback_owner'] = lambda: None
        self.assertIsNone(self.sdk['get_plugin_dir']())
        self.assertIsNone(self.sdk['get_plugin_cache_dir']())

if __name__ == '__main__':
    unittest.main()
