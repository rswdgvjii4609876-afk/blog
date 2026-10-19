#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v java >/dev/null || ! command -v javac >/dev/null; then
    printf '%s\n' 'Java 21 JDK is required. Check java -version and javac -version.' >&2
    exit 1
fi
bash mvnw -B -ntp verify
exec bash scripts/run-dev.sh "$@"
