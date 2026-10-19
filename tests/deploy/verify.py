#!/usr/bin/env python3
"""Isolated Docker + Caddy HTTPS acceptance; no real DNS, ACME or user database."""
import http.cookiejar
import json
import os
import pathlib
import re
import secrets
import ssl
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

image = os.environ.get("BLOG_TEST_IMAGE", "zhixing-blog:local")
caddy_image = os.environ.get("CADDY_TEST_IMAGE", "caddy:2-alpine")
prefix = "zhixing-test-" + secrets.token_hex(4)
app, proxy, network, volume = (prefix + "-" + part for part in ("app", "tls", "net", "data"))
port = int(os.environ.get("BLOG_TLS_TEST_PORT", "18443"))
base = "https://localhost:" + str(port)
password = secrets.token_urlsafe(32)
environment = dict(os.environ, BLOG_ADMIN_PASSWORD=password)
containers, volumes, networks = [], [], []


def docker(*args, check=True, env=None):
    return subprocess.run(["docker", *args], check=check, capture_output=True, text=True,
                          env=env, timeout=180)


def token(body):
    return re.search(r'name="_csrf" value="([^"]+)"', body).group(1)


with tempfile.TemporaryDirectory(prefix=prefix) as temporary:
    temp = pathlib.Path(temporary)
    try:
        configuration = prefix + "-config"
        docker("create", "--name", configuration, "--tmpfs", "/data", "--tmpfs", "/config",
               "-e", "BLOG_DOMAIN=blog.example.com", caddy_image,
               "caddy", "validate", "--config", "/etc/caddy/Caddyfile")
        containers.append(configuration)
        caddyfile = pathlib.Path(__file__).resolve().parents[2] / "deploy" / "Caddyfile"
        docker("cp", str(caddyfile), configuration + ":/etc/caddy/Caddyfile")
        docker("start", "-a", configuration)
        assert docker("inspect", "--format", "{{.State.ExitCode}}", configuration).stdout.strip() == "0"
        print("PASS: shipped Caddy configuration validation", flush=True)
        # A missing credential must fail startup, rather than leave an open admin UI.
        rejected = docker("run", "--rm", image, check=False)
        assert rejected.returncode != 0 and "BLOG_ADMIN_PASSWORD" in rejected.stdout + rejected.stderr
        print("PASS: public image refuses missing administrator credentials", flush=True)
        docker("network", "create", network)
        networks.append(network)
        docker("volume", "create", volume)
        volumes.append(volume)
        docker("run", "-d", "--name", app, "--network", network, "--network-alias", "app",
               "-e", "BLOG_ADMIN_PASSWORD", "-e", "BLOG_ADMIN_USERNAME=deploy-author",
               "--mount", f"type=volume,src={volume},dst=/app/data", "--read-only",
               "--tmpfs", "/tmp:size=128m,mode=1777", "--cap-drop", "ALL",
               "--security-opt", "no-new-privileges:true", image, env=environment)
        containers.append(app)
        assert docker("inspect", "--format", "{{.Config.User}}", app).stdout.strip() == "10001:10001"
        assert docker("inspect", "--format", "{{.HostConfig.ReadonlyRootfs}}", app).stdout.strip() == "true"
        config = temp / "Caddyfile"
        config.write_text("{\n admin off\n}\nhttps://localhost {\n tls internal\n reverse_proxy app:8080\n}\n")
        docker("create", "--name", proxy, "--network", network, "-p", f"127.0.0.1:{port}:443", "-e", "NO_PROXY=app,localhost,127.0.0.1",
               "-e", "no_proxy=app,localhost,127.0.0.1", "--tmpfs", "/data", "--tmpfs", "/config", caddy_image)
        containers.append(proxy)
        docker("cp", str(config), proxy + ":/etc/caddy/Caddyfile")
        docker("start", proxy)
        ca = temp / "root.crt"
        for _ in range(90):
            result = docker("cp", proxy + ":/data/caddy/pki/authorities/local/root.crt", str(ca), check=False)
            if result.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("Caddy did not issue its local test certificate")
        # Trust the exact generated local CA; certificate/hostname validation stays enabled.
        context = ssl.create_default_context(cafile=str(ca))
        jar = http.cookiejar.CookieJar()
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}),
            urllib.request.HTTPSHandler(context=context), urllib.request.HTTPCookieProcessor(jar))

        def get(path):
            response = opener.open(base + path, timeout=10)
            return response, response.read().decode()

        def post(path, fields):
            data = urllib.parse.urlencode(fields).encode()
            response = opener.open(urllib.request.Request(base + path, data=data), timeout=10)
            return response, response.read().decode()

        def ready():
            for _ in range(90):
                try:
                    response, body = get("/healthz")
                    if response.status == 200 and json.loads(body)["status"] == "UP":
                        return
                except (OSError, ValueError):
                    time.sleep(1)
            raise RuntimeError("HTTPS readiness failed")

        def login():
            response, body = get("/admin/posts")
            assert response.url.endswith("/login")
            response, _ = post("/login", {"_csrf": token(body), "username": "deploy-author", "password": password})
            assert response.url.endswith("/admin/posts")
            assert any(cookie.secure for cookie in jar), "Public sessions must use Secure cookies"

        ready()
        response, _ = get("/")
        assert "script-src 'self'" in response.headers["Content-Security-Policy"]
        login()
        print("PASS: verified HTTPS certificate, reverse proxy, login and Secure session cookies", flush=True)
        _, form = get("/admin/posts/new")
        response, listing = post("/admin/posts", {"_csrf": token(form), "title": "Container persistence check",
            "summary": "Isolated deployment acceptance", "content": "## saved in a named volume", "status": "DRAFT"})
        edit = re.search(r'href="(/admin/posts/\d+/edit)"', listing).group(1)
        post_id = re.search(r'/posts/(\d+)', edit).group(1)
        try:
            get("/posts/" + post_id)
            raise AssertionError("Draft is publicly visible")
        except urllib.error.HTTPError as error:
            assert error.code == 404
        docker("stop", app)
        backup = temp / "backup"
        docker("cp", app + ":/app/data", str(backup))
        assert (backup / "blog.mv.db").is_file()
        docker("start", app)
        jar.clear()
        ready()
        login()
        _, form = get(edit)
        assert "Container persistence check" in form and "saved in a named volume" in form
        _, history = get("/admin/posts/" + post_id + "/history")
        assert "版本 1" in history
        status = docker("inspect", "--format", "{{.State.Health.Status}}", app).stdout.strip()
        # Readiness was already verified by /healthz; wait for Docker's scheduled check too.
        for _ in range(45):
            if status == "healthy":
                break
            time.sleep(1)
            status = docker("inspect", "--format", "{{.State.Health.Status}}", app).stdout.strip()
        assert status == "healthy", status
        print("PASS: non-root/read-only container, named-volume persistence, stopped backup, revisions and health check", flush=True)
        print("All deployment checks passed. No real public URL was created.", flush=True)
    finally:
        for name in reversed(containers):
            docker("rm", "-f", "-v", name, check=False)
        for name in networks:
            docker("network", "rm", name, check=False)
        for name in volumes:
            docker("volume", "rm", name, check=False)
