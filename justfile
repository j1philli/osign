# osign — Kotlin Multiplatform viewer (Kotlin/Wasm UI served in the browser)

# List available recipes
default:
    @just --list

# Compile every module
build:
    ./kotlin build

# Run unit tests (shared model tests run on the JVM; wasm tests aren't supported by the toolchain yet)
test:
    ./kotlin test

# Build the wasm web app and stage it in web-app-dist/ (debug or release)
build-web profile='debug':
    ./build-web.sh {{profile}}

# Serve the web app in a browser (http://127.0.0.1:8080)
web:
    ./kotlin run -m web-app

# Remove build outputs
clean:
    ./kotlin clean
    rm -rf web-app-dist
