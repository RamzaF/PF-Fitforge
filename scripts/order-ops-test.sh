#!/bin/sh
# Always own build tag, guarded disposable database, and sources from this worktree.
set -eu
export FITFORGE_IMAGE_TAG="${FITFORGE_IMAGE_TAG:-fitforge-order-ops-baseline-20261116}"
exec docker compose -f compose.yaml -f compose.order-ops.yaml -p "${FITFORGE_ORDER_TEST_PROJECT:-fitforge-orders-tests-orderops-20261116-v2}" run --rm -v "$PWD/InitializrSpringbootProject/src:/workspace/src:ro" test mvn -o -B -ntp "$@"
