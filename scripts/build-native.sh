#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_NAME="${DOPPIO_NATIVE_IMAGE_NAME:-doppio-native-builder}"
OUTPUT_DIR="${ROOT_DIR}/dist/native"
OUTPUT_FILE="${OUTPUT_DIR}/doppio"

cd "${ROOT_DIR}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to build the native executable." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not running. Start Docker and rerun this script." >&2
  exit 1
fi

mvn -DskipTests clean package

JAR_FILE="$(find "${ROOT_DIR}/target" -maxdepth 1 -type f -name 'doppio-*.jar' ! -name 'original-*' | sort | sed -n '1p')"
if [[ -z "${JAR_FILE}" ]]; then
  echo "No packaged Doppio jar found under target/." >&2
  exit 1
fi

JAR_ARG="${JAR_FILE#${ROOT_DIR}/}"

docker build \
  --build-arg "JAR_FILE=${JAR_ARG}" \
  -f docker/native/Dockerfile \
  -t "${IMAGE_NAME}" \
  .

CONTAINER_ID="$(docker create "${IMAGE_NAME}")"
cleanup() {
  docker rm "${CONTAINER_ID}" >/dev/null
}
trap cleanup EXIT

mkdir -p "${OUTPUT_DIR}"
docker cp "${CONTAINER_ID}:/workspace/doppio" "${OUTPUT_FILE}"
chmod +x "${OUTPUT_FILE}"

echo "Native executable written to ${OUTPUT_FILE}"
