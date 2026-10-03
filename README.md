# osign

Open, git-friendly design file format (built on JSON Canvas 1.0) plus a minimal viewer built with Kotlin Multiplatform + Compose Multiplatform. The viewer compiles to Kotlin/Wasm and runs in the browser. It's built with the [Kotlin Toolchain](https://kotlin-toolchain.org/) (formerly Amper).

## Layout
- `project.yaml`: Kotlin Toolchain project config (module list)
- `common.module-template.yaml` / `compose.module-template.yaml`: shared Kotlin and Compose settings
- `libs.versions.toml`: project library catalog (`$libs.*` dependencies)
- `kotlin` / `kotlin.bat`: Kotlin Toolchain wrapper scripts (pin the toolchain version)
- `shared/`: KMP library with osign models, `parseOsign`, and basic validation (`src/` code, `test/` tests)
- `shared-ui/`: Compose Multiplatform UI (file picking, canvas rendering with pan/zoom, inspector)
- `web-app/`: Kotlin/Wasm entry point (`src/main.kt`) and its `resources/index.html`
- `build-web.sh`: builds the web app and stages it in `web-app-dist/` as a static site
- `icons/icon-source.png`: source artwork for the app icon (`web-app/resources/favicon.png`)
- `docs/`: spec docs (`osign-overview.md`, `osign-file-format.md`)
- `schema/osign.schema.json`: minimal JSON Schema for basic shape validation
- `examples/hello-world.osign.json`: desktop marketing hero sample
- `examples/mobile-app.osign.json`: mobile app mock paired with assets under `examples/resources/mobile/`

## Prerequisites
- A JDK isn't needed up front: `./kotlin` provisions its own.
- [`just`](https://github.com/casey/just) for the recipes below.

## Common tasks
```bash
just build      # compile every module
just test       # run unit tests
just web        # serve the viewer at http://127.0.0.1:8080
```
Run `just` to list every recipe.

## Using the viewer
Click **Open files** and select an `.osign.json` document, or drag and drop it onto the window. The browser can't read files from disk by path, so select any local images the document references (for example `examples/mobile-app.osign.json` together with `examples/resources/mobile/*.png`) in the same dialog (or drop). Relative asset paths are matched by file name. Assets given as `data:` URIs or `http(s)` URLs load without extra files.

## Web viewer on GitHub Pages
`.github/workflows/pages.yml` tests, builds the release web app, and deploys `web-app-dist/` to GitHub Pages on every push to `main`. To turn it on, set **Settings → Pages → Source** to **GitHub Actions**.

## Testing notes
The Kotlin Toolchain can't run Kotlin/Wasm tests yet ([KTC-5576](https://youtrack.jetbrains.com/issue/KTC-5576)), so `shared` also targets the JVM, and that's where its unit tests run.

## Updating the toolchain
```bash
./kotlin update
```

## Docs
- `docs/osign-overview.md`
- `docs/osign-file-format.md`
- Schema: `schema/osign.schema.json`
- Sample: `examples/hello-world.osign.json`

## License
osign (the specification, schema, and code) is released under the [MIT License](LICENSE).

osign builds on [JSON Canvas](https://jsoncanvas.org/), an open format created by [Obsidian](https://obsidian.md/) and also published under the MIT License.
