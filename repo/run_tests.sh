#!/usr/bin/env bash
set -euo pipefail

# Run tests inside Docker — no local JDK or Maven required.
# Uses the same base image as the production Dockerfile.

echo "Building test image and running tests in Docker..."
docker build -f Dockerfile.test -t reclaim-tests . && \
docker run --rm reclaim-tests
