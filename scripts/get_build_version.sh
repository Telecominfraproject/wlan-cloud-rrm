#!/bin/sh
# Print the "build version" to be appended to the package version

if [ -d .git ] && [ -x "$(command -v git)" ]; then
  BUILD_NUM="$(git rev-list --count --first-parent HEAD)"
  HASH="$(git rev-parse --short HEAD)"
  echo "-$HASH($BUILD_NUM)"
fi
