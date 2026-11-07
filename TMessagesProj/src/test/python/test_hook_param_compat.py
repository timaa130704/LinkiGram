import ast
import os
import types
import unittest
from typing import Optional, Callable

SOURCE = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'python', 'base_plugin.py'))


class HookParamCompatTest(unittest.TestCase):
    def test_filter_metadata_survives_functional_hook_adapter(self):
        with open(SOURCE, encoding='utf-8') as stream:
            tree = ast.parse(stream.read(), filename=SOURCE)
        nodes = [node for node in tree.body if isinstance(node, ast.FunctionDef)
                 and node.name in ('hook_filters', '_make_method_hook')]
        class Filter:
            def __init__(self, value): self.value = value
            def to_java_filter(self): return self.value
        constants = types.SimpleNamespace(Xposed=types.SimpleNamespace(
            BEFORE_HOOKED_METHOD='before_hooked_method', AFTER_HOOKED_METHOD='after_hooked_method'))
        scope = {'HookFilterData': Filter, 'MethodHook': type('MethodHook', (), {}),
                 'PluginsConstants': constants, 'Optional': Optional, 'Callable': Callable}
        exec(compile(ast.Module(body=nodes, type_ignores=[]), SOURCE, 'exec'), scope)
        @scope['hook_filters'](Filter('b'), after=[Filter('a')])
        def callback(param): pass
        handler = scope['_make_method_hook'](before=callback)
        self.assertEqual(handler.before_hooked_method.__hook_filters__,
                         {'before': ['b'], 'after': ['a']})

    def test_property_access_preserves_xposed_setters(self):
        with open(SOURCE, encoding='utf-8') as stream:
            tree = ast.parse(stream.read(), filename=SOURCE)
        nodes = [node for node in tree.body if isinstance(node, (ast.ClassDef, ast.FunctionDef))
                 and node.name in ('HookParam', '_wrap_hook_param')]
        code = compile(ast.Module(body=nodes, type_ignores=[]), SOURCE, 'exec')
        scope = {'XC_MethodHook': types.SimpleNamespace(MethodHookParam=object),
                 '_hook_param_accessors': None}
        exec(code, scope)

        class Param:
            def __init__(self):
                self.value = None
                self.error = None
                self.args = [1]
                self.returnEarly = False
            def getResult(self): return self.value
            def getThrowable(self): return self.error
            def setResult(self, value):
                self.value = value
                self.returnEarly = True
            def setThrowable(self, value):
                self.error = value
                self.returnEarly = True

        raw = Param()
        wrapped = scope['_wrap_hook_param'](raw)
        self.assertIs(wrapped.java, raw)
        wrapped.result = 42
        self.assertEqual(wrapped.result, 42)
        self.assertTrue(raw.returnEarly)
        wrapped.args = [2]
        self.assertEqual(raw.args, [2])
        wrapped.throwable = ValueError('x')
        self.assertIsInstance(wrapped.throwable, ValueError)


if __name__ == '__main__': unittest.main()
