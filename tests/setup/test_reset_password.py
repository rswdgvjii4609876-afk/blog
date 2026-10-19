import importlib.util
import os
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("reset_password", Path(__file__).resolve().parents[2] / "scripts/reset-admin-password.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ResetPasswordTests(unittest.TestCase):
    def directory(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        (root / ".env").write_text('BLOG_DOMAIN=203.0.113.10\nBLOG_ADMIN_USERNAME=author\nBLOG_ADMIN_PASSWORD="Old-synthetic-password"\nEXTRA=unchanged\n')
        (root / "compose.override.yaml").write_text("existing config")
        (root / "data").mkdir()
        (root / "data/article.txt").write_text("keep articles")
        return root

    def test_changes_only_password_preserving_files_and_private_permissions(self):
        root = self.directory()
        module.reset_password(root, "New-synthetic-password-2026")
        value = (root / ".env").read_text()
        self.assertIn("BLOG_DOMAIN=203.0.113.10\nBLOG_ADMIN_USERNAME=author", value)
        self.assertIn('BLOG_ADMIN_PASSWORD="New-synthetic-password-2026"', value)
        self.assertTrue(value.endswith("EXTRA=unchanged\n"))
        self.assertEqual(stat.S_IMODE((root / ".env").stat().st_mode), 0o600)
        self.assertEqual((root / "compose.override.yaml").read_text(), "existing config")
        self.assertEqual((root / "data/article.txt").read_text(), "keep articles")

    def test_rejects_invalid_or_ambiguous_settings_without_changes(self):
        root = self.directory()
        old = (root / ".env").read_bytes()
        with self.assertRaises(ValueError):
            module.reset_password(root, "short")
        self.assertEqual((root / ".env").read_bytes(), old)
        (root / ".env").write_text("BLOG_DOMAIN=203.0.113.10\n")
        with self.assertRaises(ValueError):
            module.reset_password(root, "New-synthetic-password-2026")
        self.assertEqual((root / ".env").read_text(), "BLOG_DOMAIN=203.0.113.10\n")

    def test_failed_atomic_replace_preserves_old_configuration_and_removes_temp_file(self):
        root = self.directory()
        old = (root / ".env").read_bytes()
        with patch.object(module.os, "replace", side_effect=OSError("synthetic failure")):
            with self.assertRaises(OSError):
                module.reset_password(root, "New-synthetic-password-2026")
        self.assertEqual((root / ".env").read_bytes(), old)
        self.assertEqual(list(root.glob(".env.reset-*")), [])


if __name__ == "__main__":
    unittest.main()
