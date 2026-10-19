#!/usr/bin/env python3
"""Rotate only the administrator password; keep credentials out of argv and stdout."""
import getpass
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def reset_password(root, password):
    if not 16 <= len(password) <= 64 or len(password.encode("utf-8")) > 72:
        raise ValueError("Password must be 16-64 characters and at most 72 UTF-8 bytes.")
    if password.isspace() or any(ord(char) < 32 or ord(char) == 127 for char in password):
        raise ValueError("Password cannot be blank or contain control characters.")
    env = root / ".env"
    if env.is_symlink() or not env.is_file():
        raise ValueError("A regular existing .env configuration is required; no files changed.")
    contents = env.read_text()
    quoted = json.dumps(password.replace("$", "$$"), ensure_ascii=False)
    updated, count = re.subn(r"(?m)^BLOG_ADMIN_PASSWORD=[^\r\n]*", lambda _: "BLOG_ADMIN_PASSWORD=" + quoted, contents)
    if count != 1:
        raise ValueError("Expected exactly one BLOG_ADMIN_PASSWORD setting; no files changed.")
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", dir=root, prefix=".env.reset-", delete=False) as file:
            temporary = Path(file.name)
            os.fchmod(file.fileno(), 0o600)
            file.write(updated)
            file.flush()
            os.fsync(file.fileno())
        os.replace(temporary, env)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main():
    root = Path(__file__).resolve().parents[1]
    if not (root / ".env").is_file():
        raise SystemExit("No existing .env found. This command is for an initialized deployment.")
    password = getpass.getpass("New admin password (hidden, 16-64 characters): ")
    repeated = getpass.getpass("Repeat new password (hidden): ")
    if password != repeated:
        raise SystemExit("Passwords did not match. Nothing changed; run this command again.")
    try:
        reset_password(root, password)
    except (ValueError, OSError) as error:
        raise SystemExit(str(error)) from None
    print("New password saved privately. Recreating the application; data and certificates are retained.")
    environment = dict(os.environ)
    for name in ("BLOG_DOMAIN", "BLOG_ADMIN_USERNAME", "BLOG_ADMIN_PASSWORD"):
        environment.pop(name, None)
    try:
        subprocess.run(["docker", "compose", "config", "--quiet"], cwd=root, env=environment, check=True)
        subprocess.run(["docker", "compose", "up", "-d", "--no-deps", "--force-recreate", "--wait", "app"],
                       cwd=root, env=environment, check=True)
    except subprocess.CalledProcessError:
        raise SystemExit("Password file was updated, but startup did not finish. Check docker compose ps and app logs; do not delete data.") from None
    print("Password reset complete. Log in with your existing administrator username and the new password.")


if __name__ == "__main__":
    main()
