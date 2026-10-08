import pathlib
import tempfile
import unittest
from release_version import update

class ReleaseVersionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        (self.root / "esp32/clockinator").mkdir(parents=True)
        (self.root / "version.properties").write_text("versionName=1.2.3\nversionCode=42\n")
        (self.root / "esp32/clockinator/Version.h").write_text("old header")
    def test_updates_both_targets_and_increases_android_code(self):
        update("2.0.0", self.root)
        self.assertEqual((self.root / "version.properties").read_text(), "versionName=2.0.0\nversionCode=43\n")
        self.assertIn('#define CLOCKINATOR_VERSION "2.0.0"', (self.root / "esp32/clockinator/Version.h").read_text())
    def test_rejected_input_does_not_change_files(self):
        for value in ["1.2.3", "1.2.2", "1.0", "v2.0.0", "02.0.0", "2.0.0; touch pwned", "2.0.0\n"]:
            with self.subTest(value=value):
                with self.assertRaises(ValueError): update(value, self.root)
                self.assertIn("versionCode=42", (self.root / "version.properties").read_text())
                self.assertEqual((self.root / "esp32/clockinator/Version.h").read_text(), "old header")

if __name__ == "__main__": unittest.main()
