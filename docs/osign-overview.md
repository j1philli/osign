# osign overview

## What is osign?
- An open, JSON-based, Git-friendly design file format.
- Built directly on JSON Canvas 1.0 (https://jsoncanvas.org/spec/1.0/); osign extends rather than replaces it.
- Adds UI/layout/style/component metadata while remaining human-readable, diffable, and merge-friendly.

## Design principles
- Open, documented, and stable over time.
- Git-first and offline-friendly.
- Minimal but extensible: a small core plus well-defined extension points.
- Viewer-first: everything needed to render the document should live in the file—no external fetches required.

## Relationship to JSON Canvas
osign uses JSON Canvas 1.0 as its base. It keeps the same concepts of `nodes`, `edges`, positions, sizes, and basic metadata, and adds extra fields for:
- UI node types (e.g. `frame`, `text`, `component`, `image`, `shape`, `instance`, `asset`).
- Layout metadata (constraints, auto-layout hints).
- Style metadata (fills, strokes, typography tokens).
- Component/variant metadata (component identifiers, variant props).
- Arbitrary `data` fields for extensions.

An osign file is valid JSON Canvas plus extra fields. Tools that understand JSON Canvas should ignore unknown osign fields without breaking.

## Versioning strategy
- Top-level `osign_version` string.
- Changes to the spec should be backward-compatible when possible.
- Breaking changes require a new major version and documented migration notes.
