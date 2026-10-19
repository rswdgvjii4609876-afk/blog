#!/usr/bin/env python3
"""Regression: verified IP HTTPS without SNI, with a proxy behind NAT. No ACME order."""
import os
from pathlib import Path
import re
import secrets
import socket
import ssl
import subprocess
import tempfile
import time

name = "zhixing-ip-tls-" + secrets.token_hex(4)
port = int(os.environ.get("BLOG_IP_TLS_TEST_PORT", "18444"))
image = os.environ.get("CADDY_TEST_IMAGE", "caddy:2-alpine")


def docker(*args, check=True):
    return subprocess.run(["docker", *args], capture_output=True, text=True, check=check, timeout=120)


with tempfile.TemporaryDirectory(prefix=name) as directory:
    root = Path(directory)
    configuration = (Path(__file__).resolve().parents[2] / "deploy/Caddyfile.ip").read_text()
    # Use the shipped selection policy, but replace only certificate issuance and the upstream.
    configuration = re.sub(r"tls\s*\{\s*issuer acme\s*\{.*?\}\s*\}", "tls internal", configuration, flags=re.S)
    configuration = configuration.replace("reverse_proxy app:8080", 'respond "ip-https-ok"')
    file = root / "Caddyfile"
    file.write_text(configuration)
    try:
        docker("create", "--name", name, "-p", f"127.0.0.1:{port}:443", "--tmpfs", "/data", "--tmpfs", "/config",
               "-e", "BLOG_DOMAIN=203.0.113.10", image)
        docker("cp", str(file), name + ":/etc/caddy/Caddyfile")
        docker("start", name)
        failure = None
        for _ in range(15):
            certificate = docker("exec", name, "cat", "/data/caddy/pki/authorities/local/root.crt", check=False)
            if certificate.returncode != 0:
                time.sleep(1)
                continue
            ca = root / "ca.crt"
            ca.write_text(certificate.stdout)
            context = ssl.create_default_context(cafile=str(ca))
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=3) as connection:
                    # OpenSSL omits SNI for numeric IPs, while still validating IP subjectAltName.
                    with context.wrap_socket(connection, server_hostname="203.0.113.10") as secure:
                        secure.sendall(b"GET / HTTP/1.1\r\nHost: 203.0.113.10\r\nConnection: close\r\n\r\n")
                        body = b""
                        while data := secure.recv(4096):
                            body += data
                        assert b"200 OK" in body and b"ip-https-ok" in body
                        print("PASS: no-SNI IP HTTPS behind NAT; certificate/IP validation enabled")
                        break
            except (OSError, AssertionError) as error:
                failure = error
                time.sleep(1)
        else:
            raise RuntimeError("No-SNI IP TLS failed") from failure
    finally:
        docker("rm", "-f", "-v", name, check=False)
