import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE = Path(__file__).with_name("cache_inventory.py")
spec = importlib.util.spec_from_file_location("cache_inventory", MODULE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class CacheInventoryTest(unittest.TestCase):
    def test_only_cache_names_and_no_contents(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "main_file_cache.dat2").write_bytes(b"not a real cache")
            (root / "main_file_cache.idx0").write_bytes(b"idx")
            (root / "credentials.txt").write_text("TOP SECRET", encoding="utf-8")
            result = module.inspect(root)
            self.assertEqual(result["recognizedCount"], 2)
            self.assertFalse(result["cacheRevisionVerified"])
            self.assertNotIn("TOP SECRET", str(result))
            self.assertNotIn("credentials", str(result))

    def test_rejects_non_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / "sample"
            file.write_bytes(b"x")
            with self.assertRaises(ValueError):
                module.inspect(file)


if __name__ == "__main__":
    unittest.main()
