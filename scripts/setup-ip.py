#!/usr/bin/env python3
"""Create private deployment configuration without printing or accepting a password in argv."""
import argparse
import getpass
import ipaddress
import json
import os
from pathlib import Path
import shutil
import subprocess


def configure(root, address, password):
    ip = ipaddress.ip_address(address)
    if ip.version != 4 or not ip.is_global:
        raise ValueError("Please supply the server's public IPv4 address.")
    if not 16 <= len(password) <= 64 or len(password.encode("utf-8")) > 72:
        raise ValueError("Password must be 16-64 characters and at most 72 UTF-8 bytes.")
    if password.isspace() or any(ord(char) < 32 or ord(char) == 127 for char in password):
        raise ValueError("Password cannot contain control characters.")
    env = root / ".env"
    override = root / "compose.override.yaml"
    if env.exists() or override.exists():
        raise FileExistsError("Configuration already exists; nothing was overwritten. Keep it and check with docker compose ps.")
    template = root / "deploy" / "compose.ip.override.yaml"
    contents = template.read_text()
    # Compose performs interpolation even in .env double quotes: $$ preserves a literal dollar.
    quoted = json.dumps(password.replace("$", "$$"), ensure_ascii=False)
    descriptor = os.open(env, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as file:
        file.write(f"BLOG_DOMAIN={ip}\nBLOG_ADMIN_USERNAME=admin\nBLOG_ADMIN_PASSWORD={quoted}\n")
    descriptor = os.open(override, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as file:
        file.write(contents)


def main():
    parser = argparse.ArgumentParser(description="Initialize public IPv4 HTTPS deployment and start Compose.")
    parser.add_argument("address", help="Your server's public IPv4 address")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if (root / ".env").exists() or (root / "compose.override.yaml").exists():
        raise SystemExit("Existing deployment configuration found. Nothing changed. Run: docker compose ps")
    if not shutil.which("docker"):
        raise SystemExit("Docker is required.")
    print("Administrator account: admin. Choose and keep your own password (16-64 characters).")
    password = getpass.getpass("Set admin password (hidden): ")
    confirmation = getpass.getpass("Repeat password (hidden): ")
    if password != confirmation:
        raise SystemExit("Passwords did not match. No configuration was written; run this command again.")
    try:
        configure(root, args.address, password)
    except (ValueError, FileExistsError) as error:
        raise SystemExit(str(error)) from None
    print("Private configuration saved. Starting the application; password will not be printed.")
    command_environment = dict(os.environ)
    for name in ("BLOG_DOMAIN", "BLOG_ADMIN_USERNAME", "BLOG_ADMIN_PASSWORD"):
        command_environment.pop(name, None)
    subprocess.run(["docker", "compose", "config", "--quiet"], cwd=root, env=command_environment, check=True)
    subprocess.run(["docker", "compose", "up", "-d", "--build"], cwd=root, env=command_environment, check=True)
    subprocess.run(["docker", "compose", "ps"], cwd=root, env=command_environment, check=True)
    print(f"Services started. Check certificate issuance before opening https://{args.address}")
    print("Next check: docker compose logs --tail=80 caddy")


if __name__ == "__main__":
    main()
