import importlib.util
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("setup_ip", Path(__file__).resolve().parents[2] / "scripts/setup-ip.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SetupTests(unittest.TestCase):
    def directory(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        (root / "deploy").mkdir()
        (root / "deploy/compose.ip.override.yaml").write_text("services: {}\n")
        return root

    def test_creates_private_files_and_preserves_existing_configuration(self):
        root = self.directory()
        module.configure(root, "8.8.8.8", "Synthetic-test-password-2026")
        self.assertEqual(stat.S_IMODE((root / ".env").stat().st_mode), 0o600)
        original = (root / ".env").read_bytes()
        with self.assertRaises(FileExistsError):
            module.configure(root, "8.8.4.4", "Another-test-password-2026")
        self.assertEqual((root / ".env").read_bytes(), original)

    def test_rejects_local_ip_or_invalid_password_before_writing(self):
        for address, password in [("127.0.0.1", "Synthetic-test-password-2026"), ("8.8.8.8", "short"),
                                  ("8.8.8.8", "弱" * 30), ("8.8.8.8", "bad\npassword-long-enough")]:
            root = self.directory()
            with self.assertRaises(ValueError):
                module.configure(root, address, password)
            self.assertFalse((root / ".env").exists())

    @unittest.skipUnless(shutil.which("docker"), "Docker Compose is needed for quoting verification")
    def test_password_special_characters_round_trip_through_compose(self):
        root = self.directory()
        password = 'Synthetic-$HOME#"back\\slash\'2026'
        module.configure(root, "8.8.8.8", password)
        (root / "compose.yaml").write_text('services:\n  test:\n    image: eclipse-temurin:21-jre-alpine\n    environment:\n      VALUE: ${BLOG_ADMIN_PASSWORD}\n')
        environment = dict(os.environ)
        environment.pop("BLOG_ADMIN_PASSWORD", None)
        result = subprocess.run(["docker", "compose", "run", "--rm", "--no-deps", "test",
                                 "sh", "-c", 'printf "%s" "$VALUE"'], cwd=root,
                                env=environment, capture_output=True, text=True, check=True)
        self.addCleanup(lambda: subprocess.run(["docker", "compose", "down"], cwd=root,
            env=environment, capture_output=True, check=False))
        self.assertEqual(result.stdout, password)


if __name__ == "__main__":
    unittest.main()
