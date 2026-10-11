#!/bin/sh

# Lightweight Gradle bootstrapper for GitHub Actions / local builds.
# Downloads the pinned Gradle distribution when it is not cached.
set -e

GRADLE_VERSION="8.10.2"
DIST_NAME="gradle-${GRADLE_VERSION}-bin.zip"
BASE_URL="https://services.gradle.org/distributions"
CACHE_DIR="${HOME}/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}"
ZIP_PATH="${CACHE_DIR}/${DIST_NAME}"
INSTALL_DIR="${CACHE_DIR}/gradle-${GRADLE_VERSION}"

if [ -x "${INSTALL_DIR}/bin/gradle" ]; then
  exec "${INSTALL_DIR}/bin/gradle" "$@"
fi

mkdir -p "${CACHE_DIR}"

if [ ! -f "${ZIP_PATH}" ]; then
  echo "Downloading Gradle ${GRADLE_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 -o "${ZIP_PATH}" "${BASE_URL}/${DIST_NAME}"
  else
    wget -O "${ZIP_PATH}" "${BASE_URL}/${DIST_NAME}"
  fi
fi

rm -rf "${INSTALL_DIR}"
if command -v unzip >/dev/null 2>&1; then
  unzip -q "${ZIP_PATH}" -d "${CACHE_DIR}"
else
  echo "unzip is required."
  exit 1
fi

exec "${INSTALL_DIR}/bin/gradle" "$@"
