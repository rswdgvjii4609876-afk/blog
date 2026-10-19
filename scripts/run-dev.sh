#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Work around IPv6-mapped sockets in some cloud runtimes; also works on local machines.
if [[ -n "${JAVA_HOME:-}" ]]; then java_cmd="$JAVA_HOME/bin/java"; else java_cmd=java; fi
if [[ ! -f target/blog-0.0.1-SNAPSHOT.jar ]]; then
    printf '%s\n' 'Build first: bash mvnw -B -ntp verify (Windows: .\mvnw.cmd verify)' >&2
    exit 1
fi
exec "$java_cmd" -Djava.net.preferIPv4Stack=true -jar target/blog-0.0.1-SNAPSHOT.jar "$@"
