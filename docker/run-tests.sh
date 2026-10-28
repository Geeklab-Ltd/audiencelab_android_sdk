#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_TAG="audiencelab-android-sdk-test:local"
GRADLE_CACHE_VOLUME="${GRADLE_CACHE_VOLUME:-audiencelab-android-gradle-cache}"
DOCKER_PLATFORM="${DOCKER_PLATFORM:-}"
DOCKER_RUN_ARGS=(
  --rm
  -v "${GRADLE_CACHE_VOLUME}:/root/.gradle"
  -v "${ROOT_DIR}:/workspace"
  -w /workspace
)

if [[ -n "${DOCKER_PLATFORM}" ]]; then
  DOCKER_RUN_ARGS+=(--platform "${DOCKER_PLATFORM}")
fi

if ! docker image inspect "${IMAGE_TAG}" >/dev/null 2>&1; then
  echo "Docker image ${IMAGE_TAG} not found. Build it first with ./docker/build-image.sh" >&2
  exit 1
fi

docker volume create "${GRADLE_CACHE_VOLUME}" >/dev/null

docker run "${DOCKER_RUN_ARGS[@]}" \
  "${IMAGE_TAG}" \
  ./gradlew \
    --no-daemon \
    :audiencelab-sdk:testDebugUnitTest \
    :audiencelab-sdk:assembleRelease \
    :audiencelab-sdk:publishReleasePublicationToMavenLocal \
    :sample-app:assembleDebug
