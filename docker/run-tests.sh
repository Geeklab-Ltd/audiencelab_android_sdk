#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_TAG="audiencelab-android-sdk-test:local"
DOCKER_PLATFORM="${DOCKER_PLATFORM:-}"
if [[ -n "${DOCKER_PLATFORM}" ]]; then
  docker build \
    --platform "${DOCKER_PLATFORM}" \
    -f "${ROOT_DIR}/docker/Dockerfile" \
    -t "${IMAGE_TAG}" \
    "${ROOT_DIR}"

  docker run --rm \
    --platform "${DOCKER_PLATFORM}" \
    -v "${ROOT_DIR}/..:/workspace" \
    -w /workspace/audiencelab_android_sdk \
    "${IMAGE_TAG}" \
    ./gradlew \
      :audiencelab-sdk:testDebugUnitTest \
      :audiencelab-sdk:assembleRelease \
      :sample-app:assembleDebug
else
  docker build \
    -f "${ROOT_DIR}/docker/Dockerfile" \
    -t "${IMAGE_TAG}" \
    "${ROOT_DIR}"

  docker run --rm \
    -v "${ROOT_DIR}/..:/workspace" \
    -w /workspace/audiencelab_android_sdk \
    "${IMAGE_TAG}" \
    ./gradlew \
      :audiencelab-sdk:testDebugUnitTest \
      :audiencelab-sdk:assembleRelease \
      :sample-app:assembleDebug
fi
