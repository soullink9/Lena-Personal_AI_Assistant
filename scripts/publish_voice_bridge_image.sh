#!/usr/bin/env bash
# Builds the voice bridge's Docker image from voice/bridge/Dockerfile and pushes it to a container registry, so
# voice/bridge/compose.yaml can pull it instead of building it. Used by both release workflows: Gitea's pushes to
# Gitea's own registry, GitHub's to ghcr.io.
#
#   IMAGE=ghcr.io/soullink9/lena-voice-bridge TAG=v0.6.48 LATEST=true \
#   REGISTRY_USER=... REGISTRY_PASSWORD=... SOURCE_URL=https://github.com/soullink9/Lena-Personal_AI_Assistant \
#   [PLATFORMS=linux/amd64,linux/arm64] scripts/publish_voice_bridge_image.sh
#
# IMAGE      the image's name without a tag; it starts with the registry's host
# TAG        the release's tag; the image is tagged with its version (0.6.48)
# LATEST     true also tags it latest. Only the newest of Lena's versions gets it (the workflows ask
#            scripts/missing_releases.py), so releases run together in any order leave the newest as latest.
# PLATFORMS  optional; without it, the image is for the runner's own platform. Building for another one needs
#            QEMU on the Docker host (GitHub's workflow installs it first).
# SOURCE_URL the public repository, written into the image's labels (ghcr.io links the package to it)
#
# Run it from the top of the code to build: it builds that folder's voice/bridge (GitHub's workflow runs main's copy
# of this script on an older tag's code). Needs the docker CLI with buildx and a Docker daemon it can reach. The bridge's version must match the tag, so
# an image is always the code of the release it's named after.
set -euo pipefail
: "${IMAGE:?Set IMAGE, the image name without a tag}"
: "${TAG:?Set TAG, the release tag}"
: "${REGISTRY_USER:?Set REGISTRY_USER}"
: "${REGISTRY_PASSWORD:?Set REGISTRY_PASSWORD}"
: "${SOURCE_URL:?Set SOURCE_URL, the public repository}"
LATEST="${LATEST:-false}"
PLATFORMS="${PLATFORMS:-}"
repo="$(pwd)"
version="${TAG#[vV]}"
registry="${IMAGE%%/*}"

# The image carries bridge.py as it is in this commit; refuse to name it after another version.
bridge_version=$(sed -n 's/.*"LenaVoiceBridge\/\([0-9.]*\)".*/\1/p' "$repo/voice/bridge/bridge.py")
if [ "$bridge_version" != "$version" ]; then
  echo "::error file=voice/bridge/bridge.py::The voice bridge is at ${bridge_version:-no version}, not $version: no image is published for $TAG"
  exit 1
fi

if ! docker info > /dev/null 2>&1; then
  echo "::error::This job can't reach a Docker daemon. On Gitea, the job mounts the runner's /var/run/docker.sock, which act_runner allows only when its config.yaml lists it under container.valid_volumes. See CONTRIBUTING.md#releasing."
  exit 1
fi

tags=(--tag "$IMAGE:$version")
if [ "$LATEST" = true ]; then
  tags+=(--tag "$IMAGE:latest")
fi

# A login only for this build, in its own folder, removed afterwards: nothing is left in the runner's Docker setup.
# (buildx keeps its builders there too, so this comes first.)
DOCKER_CONFIG="$(mktemp -d)"
export DOCKER_CONFIG
trap 'docker buildx rm lena-voice-bridge > /dev/null 2>&1 || true; rm -rf "$DOCKER_CONFIG"' EXIT
printf '%s' "$REGISTRY_PASSWORD" | docker login "$registry" --username "$REGISTRY_USER" --password-stdin

builder=()
platform=()
if [ -n "$PLATFORMS" ]; then
  # Docker's own builder makes an image for one platform only; a builder in a container makes several at once.
  docker buildx create --name lena-voice-bridge --driver docker-container > /dev/null
  builder=(--builder lena-voice-bridge)
  platform=(--platform "$PLATFORMS")
fi

docker buildx build "${builder[@]}" "${platform[@]}" "${tags[@]}" \
  --label "org.opencontainers.image.title=Lena voice bridge" \
  --label "org.opencontainers.image.description=Lets Open WebUI speak and listen through Home Assistant's Wyoming Piper and Whisper" \
  --label "org.opencontainers.image.source=$SOURCE_URL" \
  --label "org.opencontainers.image.version=$version" \
  --label "org.opencontainers.image.revision=$(git -C "$repo" rev-parse HEAD)" \
  --label "org.opencontainers.image.licenses=GPL-3.0" \
  --push "$repo/voice/bridge"

echo "Published $IMAGE:$version$([ "$LATEST" = true ] && echo " and $IMAGE:latest")"
