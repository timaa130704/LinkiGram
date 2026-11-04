import importlib.util
import os
import sys
import threading
import types
import unittest
from unittest import mock

SOURCE = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'python', 'account_client.py'))


class AccountClientCompatTest(unittest.TestCase):
    def test_scoped_accounts_and_callback_propagation(self):
        spec = importlib.util.spec_from_file_location('account_client_test', SOURCE)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        seen = []
        token = object()
        client = types.SimpleNamespace(
            get_selected_account=lambda: 1,
            get_messages_controller=lambda account: ('controller', account),
            send_request=lambda request, callback, account: (seen.append(account), callback()),
            send_message=lambda params, account, parse_mode=None: (params, account, parse_mode))
        runtime = types.SimpleNamespace(capture_callback_owner=lambda callback: token)
        with mock.patch.dict(sys.modules, {'client_utils': client, 'plugin_runtime': runtime}):
            facade = module.get_client()
            self.assertEqual(facade.account, 1)
            with module.hook_scope(3):
                self.assertEqual(facade.get_messages_controller(), ('controller', 3))
                self.assertEqual(facade.send_message({'peer': 1}, 'HTML'), ({'peer': 1}, 3, 'HTML'))
                self.assertEqual(facade(2).account, 2)
                worker_values = []
                callback = module._scope_callback(lambda: worker_values.append(module.get_hook_account()), 3)
                self.assertIs(callback._nimarko_runtime_token, token)
                worker = threading.Thread(target=callback)
                worker.start()
                worker.join()
                self.assertEqual(worker_values, [3])
                with module.hook_scope(4):
                    self.assertEqual(facade.account, 4)
                self.assertEqual(facade.account, 3)
            self.assertEqual(facade.account, 1)


if __name__ == '__main__': unittest.main()
