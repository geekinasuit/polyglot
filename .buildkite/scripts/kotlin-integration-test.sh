#!/usr/bin/env bash
set -euo pipefail

# Kotlin gRPC integration test
# Runs on both Linux (Docker container) and macOS (native agent).
#
# On Linux containers, bazelisk is not pre-installed — this script bootstraps it.
# On macOS, `bazel` resolves to bazelisk already in PATH; the bootstrap is a no-op.

##############################################################################
# Bootstrap bazelisk if not present (Linux Docker containers)
##############################################################################

if ! command -v bazel &> /dev/null; then
  echo "--- Installing bazelisk"
  apt-get update -qq && apt-get install -y curl python3 git build-essential > /dev/null
  ARCH=$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')
  curl -fsSL -o /usr/local/bin/bazel \
    https://github.com/bazelbuild/bazelisk/releases/download/v1.20.0/bazelisk-linux-${ARCH}
  chmod +x /usr/local/bin/bazel
fi

##############################################################################
# Build
##############################################################################

SERVICE_PORT=19999

echo "--- Building service and client"
bazel build --config=ci //kotlin:brackets_client //kotlin:brackets_service

##############################################################################
# Start service with guaranteed cleanup
##############################################################################

echo "--- Starting service on port ${SERVICE_PORT}"
bazel-bin/kotlin/brackets_service --port="${SERVICE_PORT}" &
SERVICE_PID=$!

cleanup() {
  echo "--- Stopping service (pid ${SERVICE_PID})"
  kill "${SERVICE_PID}" 2>/dev/null || true
}
trap cleanup EXIT

##############################################################################
# Wait for service to be ready
##############################################################################

echo "--- Waiting for service to accept connections"
timeout=30
while ! (echo > /dev/tcp/localhost/${SERVICE_PORT}) &>/dev/null; do
  ((timeout--)) || {
    echo "ERROR: Service did not start on port ${SERVICE_PORT} within 30s"
    exit 1
  }
  sleep 1
done
echo "Service is ready on port ${SERVICE_PORT} (pid ${SERVICE_PID})"

##############################################################################
# Integration tests
##############################################################################

echo "--- Running integration tests"

echo "foo ( bar ( baz ) { blah } [ what!!? ] )" > /tmp/kt-test1.txt
echo "foo ( bar ( baz ] { blah } [ what!!? ] )" > /tmp/kt-test2.txt

echo "  Test 1: balanced input"
if bazel-bin/kotlin/brackets_client --port="${SERVICE_PORT}" /tmp/kt-test1.txt \
    | grep -q "Brackets are balanced"; then
  echo "  PASSED"
else
  echo "  FAILED — expected 'Brackets are balanced'"
  exit 1
fi

echo "  Test 2: unbalanced input"
if bazel-bin/kotlin/brackets_client --port="${SERVICE_PORT}" /tmp/kt-test2.txt \
    | grep -q "Brackets are NOT balanced"; then
  echo "  PASSED"
else
  echo "  FAILED — expected 'Brackets are NOT balanced'"
  exit 1
fi

echo "--- All integration tests passed"
