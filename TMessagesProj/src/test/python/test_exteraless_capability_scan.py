"""Host-side checks for the exteraless plugin capability scanner.

The scanner is advisory; these checks do not establish runtime isolation.
"""

import importlib.util
from pathlib import Path
import tempfile
import unittest


SCANNER = (Path(__file__).resolve().parents[2] / "main" / "python" /
           "extera_utils" / "capability_scan.py")
spec = importlib.util.spec_from_file_location("exteraless_capability_scan", SCANNER)
scanner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scanner)


class CapabilityScanTest(unittest.TestCase):
    def scan(self, source):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.plugin"
            path.write_text(source, encoding="utf-8")
            return scanner.scan(str(path))

    def test_request_capabilities(self):
        result = self.scan("from client_utils import send_request\nsend_request(request, done)\n")
        self.assertIn("network", result)
        self.assertIn("messages.read", result)

    def test_ui_only(self):
        result = self.scan("from ui.bulletin import BulletinHelper\nBulletinHelper.show_info('hi')\n")
        self.assertEqual({}, result)

    def test_files_capability(self):
        result = self.scan("import os\nfrom file_utils import get_files_dir\nos.path.join(get_files_dir(), 'cache')\n")
        self.assertIn("files", result)


if __name__ == "__main__":
    unittest.main()
