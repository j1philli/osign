#!/usr/bin/env bash
# Builds the Kotlin/Wasm web app and stages it in web-app-dist/ as a static site (deployed by .github/workflows/pages.yml).
# Usage: ./build-web.sh [debug|release]   (default: debug)
set -euo pipefail

cd "$(dirname "$0")"

case "${1:-debug}" in
    debug) variant=Debug ;;
    release) variant=Release ;;
    *) echo "Usage: $0 [debug|release]" >&2; exit 1 ;;
esac

task=":web-app:buildWasmJsAppWasmJs${variant}"
package_dir="build/tasks/_web-app_buildWasmJsAppWasmJs${variant}"
dist_dir="web-app-dist"

echo "Building web-app ($variant)..."
./kotlin task "$task"

# The package contains index.html, the app wasm/mjs files, the Skiko runtime, and vendored JS dependencies.
rm -rf "$dist_dir"
cp -R "$package_dir" "$dist_dir"
echo "Staged $package_dir -> $dist_dir"
