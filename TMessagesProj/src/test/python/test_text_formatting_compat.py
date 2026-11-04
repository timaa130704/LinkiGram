import importlib
import os
import sys
import types
import unittest
from unittest import mock

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'python'))


class Entity:
    pass


class FakeTLRPC:
    def __getattr__(self, name):
        return Entity


class TextFormattingCompatTest(unittest.TestCase):
    def test_html_formatting_uses_utf16_offsets(self):
        sys.path.insert(0, ROOT)
        try:
            with mock.patch.dict(sys.modules, {
                'org': types.ModuleType('org'),
                'org.telegram': types.ModuleType('org.telegram'),
                'org.telegram.tgnet': types.SimpleNamespace(TLRPC=FakeTLRPC()),
            }):
                sys.modules.pop('markdown_utils', None)
                sys.modules.pop('extera_utils.text_formatting', None)
                formatter = importlib.import_module('extera_utils.text_formatting')
                result = formatter.parse_text('😀<b>Hi</b>', 'HTML')
                self.assertEqual(result['message'], '😀Hi')
                self.assertEqual(result['entities'][0].offset, 2)
                self.assertEqual(result['entities'][0].length, 2)
                with self.assertRaises(ValueError):
                    formatter.parse_text('x', 'unknown')
        finally:
            sys.path.remove(ROOT)
            sys.modules.pop('markdown_utils', None)
            sys.modules.pop('extera_utils.text_formatting', None)


if __name__ == '__main__': unittest.main()
