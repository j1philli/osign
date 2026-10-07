# RFC 0002: osign v0.2

- **Status:** proposed
- **Replaces:** osign 0.1.0 (a migration is defined in §11)
- **Reference implementation:** a Rust crate (codec, JSON Canvas validator, reconciliation, migration). It will be contributed to this repo in a follow-up PR.

## 1. Goals

1. **Every osign file is a valid [JSON Canvas 1.0](https://jsoncanvas.org/spec/1.0/) file.** This is a hard requirement, checked by a conformance validator.
2. **JSON Canvas tools get a faithful preview and can edit it safely.** osign readers pick up their edits instead of overwriting them.
3. **Git-native.** Serialization is canonical (two writers produce identical bytes), diffs stay readable, and a semantic merge driver exists.
4. **Collaboration-friendly.** Every property is an independently mergeable value: one parent pointer, sibling order given by array position, one value per property. This maps directly onto CRDTs.
5. **Lossless.** Unknown fields survive at every level.
6. **Self-contained.** Everything needed to render lives in the document, so it works offline and air-gapped.

### Why a new version

osign 0.1 is **not** valid JSON Canvas:
- Its node `type` values (`frame`, `shape`, `component`, …) aren't allowed.
- Text nodes lack `text`.
- Edges use `fromNodeId`/`toNodeId`.
- Coordinates may be fractional.

The bundled 0.1 examples fail validation with 25 and 152 violations.

## 2. Terminology

- **MUST**, **SHOULD** and **MAY** follow RFC 2119.
- A **document** is one design (§3). A **page** is one JSON Canvas file inside it.
- **Canvas layer:** the JSON Canvas fields of a node. **osign layer:** the node's `osign` object.

## 3. Documents and files

A document is either a **directory** or a **single file**.

### 3.1 Directory form (default for saved documents)

```
design.osign/
  document.json          document header and page list
  pages/
    <page-id>.canvas     one page; each is a complete, valid JSON Canvas file
  assets/
    <sha256>.<ext>       content-addressed binary assets (images, fonts)
  .osign/                tool-local cache (collaboration history, thumbnails); MUST NOT be committed
  .gitignore             SHOULD contain ".osign/"
```

`document.json`:

```json
{
  "osign": {
    "version": "0.2.0",
    "id": "doc-…",
    "name": "Checkout flow",
    "meta": { "created_at": "…" },
    "resources": { "tokens": [ … ], "fonts": [ … ], "components": [ … ] }
  },
  "pages": [ { "id": "page-1", "name": "Flows", "file": "pages/page-1.canvas" } ]
}
```

- `pages` order is page order.
- Every page file carries a minimal header so it can be identified on its own: `{ "osign": { "version": "0.2.0", "document": "doc-…", "page": "page-1" }, "nodes": […], "edges": […] }`.

### 3.2 Single-file form

A lone `.canvas` file whose top-level `osign` object carries the full header (`version`, `id`, `name`, `meta`, `resources`) is a one-page document. It's used for sharing, clipboard and export. Converting between the two forms is lossless.

### 3.3 Assets

- Binary assets are stored once, under `assets/`, named by the lowercase hex SHA-256 of their bytes plus an extension.
- Node `file` paths and image paints reference assets **relative to the document root** (`assets/9f86….png`).
- In the single-file form, assets MAY be inlined as `data:` URIs.
- *Known limitation:* Obsidian resolves `file` paths relative to the vault root, so image previews only appear when the document root is the vault root.

## 4. Two layers per node

```json
{
  "id": "card",
  "type": "group",
  "x": 120, "y": 140, "width": 321, "height": 132,
  "color": "#ffffff",
  "label": "Card",
  "osign": {
    "kind": "rect",
    "parent": "frame-1",
    "transform": { "x": 20, "y": 40, "width": 320.5, "height": 132 },
    "style": { "fills": [ { "type": "solid", "color": "#ffffff" } ], "cornerRadius": 12 }
  }
}
```

**Canvas-layer fields that are canonical** (stored only here):
- `id`
- `label`: the node's name, for every kind
- `text`: text content, escaped per §8.3
- `file`, `subpath`, `url`, `background`, `backgroundStyle`

**Canvas-layer fields that are derived** from the osign layer: `type`, `x`, `y`, `width`, `height`, `color`.

**osign-layer fields:**

| Key | Type | Notes |
|---|---|---|
| `kind` | string | §5 |
| `parent` | string | Omitted for roots. The only containment relation. |
| `transform` | object | §6 |
| `visible` | bool | Omitted when `true`. |
| `locked` | bool | Omitted when `false`. |
| `style` | object | §7 |
| `text` | object | Text style, §8 |
| `layout` | object | Auto-layout (to be specified in a future RFC) |
| `component` | object | Component and instance data (future RFC; §10) |
| `vector` | object | Paths for `vector`/`boolean` kinds, §5 |
| `data` | object | Extensions, namespaced by reverse-DNS key (`"com.acme.plugin": {…}`) |

*Known limitation:* hidden nodes (`visible: false`) still appear in JSON Canvas previews.

### 4.1 Derivation (normative)

**`type`:**
- `text` if the node has text content.
- Otherwise `file` if it has `file`.
- Otherwise `link` if it has `url`.
- Otherwise `group`.

**`x`, `y`, `width`, `height`:**
- The box is the smallest integer box enclosing the node's world-space axis-aligned bounds.
- Each bound is first snapped to the nearest integer when within 1e-6.
- Then `x = floor(minX)` and `width = ceil(maxX) − x`; the same for `y` and `height`.

**`color`:**
- If `style.canvasColor` is present, use it (a JSON Canvas preset `"1"`–`"6"`, or hex).
- Otherwise, if the first fill is `solid`, use its color, reduced to `#RGB`/`#RRGGBB` (alpha dropped).
- Otherwise omit `color`.

### 4.2 Reconciliation (normative)

When a derived field differs from the value derived from the osign layer, a JSON Canvas tool edited the file. Readers MUST apply the edit, processing nodes parents-first:

| Change | Reader action |
|---|---|
| Box moved, size unchanged | Translate `transform` by the delta, converted into the parent's space. A group moved together with its contents therefore moves only the group. |
| Box resized | Set the size. Rotated nodes scale proportionally. |
| `color` changed to hex | Replace the first solid fill, or insert one. |
| `color` changed to a preset | Set `style.canvasColor`. |
| `color` removed | Remove `canvasColor` if present, otherwise the first solid fill. |
| Node has no `osign` object | **Adopt** it: kind from `type`. A `file` node with an image extension becomes `image`. Parent is the smallest container (`frame`, `group`, `component`, `instance`) that encloses its box and appears earlier in the array. |
| `parent` refers to a missing node | Make the node a root at its current canvas position. |
| Parent cycle | Break it at the node where it's detected. |

## 5. Kinds

| `kind` | JSON Canvas `type` | Notes |
|---|---|---|
| `frame` | group | Clips content; may hold `layout`. |
| `group` | group | Bounds wrap its children. |
| `rect`, `ellipse` | group | |
| `vector` | group | `osign.vector.paths: [{ "d": "<SVG path data>", "windingRule": "nonzero" \| "evenodd" }]`, in node-local units |
| `boolean` | group | `osign.vector.operation`: `union` \| `subtract` \| `intersect` \| `exclude`; operands are its children |
| `text` | text | |
| `image` | file | Prefer an image fill on a `rect` for cropping; `image` is a placed asset. |
| `component`, `instance` | group | §10 |
| `link`, `file` | link / file | Embeds |

Unknown kinds MUST be preserved and are projected to `group`.

## 6. Structure and geometry

- **Order:** array order is z-order (JSON Canvas: first = bottom). The canonical order is a depth-first pre-order traversal with siblings back-to-front, so a parent always precedes its children.
- **Transform:** relative to the parent.
  - `x`/`y` is the top-left of the unrotated box.
  - `width`/`height` are ≥ 0.
  - `rotation` is in degrees, clockwise, about the box center. It's omitted when 0.
  - World matrix = parent world × translate(x, y) × translate(w/2, h/2) × rotate(r) × translate(−w/2, −h/2).

## 7. Style

Colors are hex strings, `#RRGGBB` or `#RRGGBBAA`, in sRGB. A document MAY declare `"colorSpace": "display-p3"` in `resources`.

| Key | Type |
|---|---|
| `fills` | `Paint[]`, bottom to top |
| `strokes` | `Paint[]` |
| `strokeWeight` | number, or `{ top, right, bottom, left }` |
| `strokeAlign` | `inside` \| `center` \| `outside` (default `center`) |
| `strokeCap` / `strokeJoin` / `strokeMiterLimit` | `none`\|`round`\|`square` / `miter`\|`round`\|`bevel` / number |
| `dashPattern` | number[] |
| `cornerRadius` | number, or `[tl, tr, br, bl]` |
| `cornerSmoothing` | 0–1 |
| `opacity` | 0–1, default 1 |
| `blendMode` | `normal`, `pass-through` (groups only), `multiply`, `screen`, `overlay`, `darken`, `lighten`, `color-dodge`, `color-burn`, `hard-light`, `soft-light`, `difference`, `exclusion`, `hue`, `saturation`, `color`, `luminosity` |
| `effects` | `Effect[]` |
| `canvasColor` | JSON Canvas color override (§4.1) |

**Paint:**

```jsonc
{ "type": "solid", "color": "#3366FF" }
{ "type": "linear" | "radial" | "angular" | "diamond",
  "stops": [ { "position": 0, "color": "#FFFFFF" }, { "position": 1, "color": "#000000" } ],
  "transform": [a, b, c, d, e, f] }          // gradient space → node unit square
{ "type": "image", "asset": "assets/<sha256>.png",
  "scaleMode": "fill" | "fit" | "crop" | "tile", "transform": [a, b, c, d, e, f], "scale": 1 }
// every paint also accepts: "opacity" (0–1), "visible" (bool), "blendMode"
```

**Effect:**

```jsonc
{ "type": "dropShadow" | "innerShadow", "color": "#00000040",
  "offset": { "x": 0, "y": 4 }, "radius": 8, "spread": 0 }
{ "type": "layerBlur" | "backgroundBlur", "radius": 12 }
// every effect also accepts: "visible", "blendMode"
```

This model mirrors Figma's paint, stroke and effect structure on purpose, to keep import lossless.

## 8. Text

### 8.1 Content

A text node's content is the JSON Canvas `text` field, escaped per §8.3. Content is plain Unicode; line breaks are `\n`.

### 8.2 Text style (`osign.text`)

| Key | Type |
|---|---|
| `fontFamily` | string; references `resources.fonts` |
| `fontWeight` | 1–1000 |
| `fontStyle` | `normal` \| `italic` |
| `fontSize` | number |
| `lineHeight` | `{ "unit": "px" \| "percent" \| "auto", "value": n }` |
| `letterSpacing` | `{ "unit": "px" \| "percent", "value": n }` |
| `textAlign` / `verticalAlign` | `left`\|`center`\|`right`\|`justify` / `top`\|`middle`\|`bottom` |
| `textDecoration` / `textCase` | `none`\|`underline`\|`strikethrough` / `none`\|`upper`\|`lower`\|`title` |
| `autoResize` | `none` \| `width` \| `height` |
| `openTypeFeatures` | `{ "liga": true, … }` |
| `runs` | **Reserved.** Mixed styles are a future addition: `[{ "start", "end", "style" }]`, with offsets in Unicode scalar values over the unescaped content. |

Fills on a text node color its glyphs.

### 8.3 Markdown escaping (normative)

JSON Canvas `text` is Markdown, but osign content is literal. **Writers MUST escape and readers MUST unescape**, so Markdown viewers show exactly what the designer typed.

**Escaping:** insert `\` before:
- Any of `` \ ` * _ [ ] < > # | ~ = $ % & ^ { } ``, wherever it appears.
- `-` or `+` as the first non-blank character of a line.
- `.` or `)` immediately after a run of digits that starts a line (after optional blanks).

**Unescaping:** a `\` followed by any ASCII punctuation character is removed. Every other character is kept.

**Guarantees:**
- `unescape(escape(s)) == s` for all strings.
- Text typed in a Markdown tool without escapes reads back literally; for example, `**bold**` stays as the literal characters `**bold**`.

*Known limitation:* Markdown viewers may collapse leading indentation and repeated spaces. The content itself is preserved byte-exact.

## 9. Edges

Edges are standard JSON Canvas edges (`fromNode`, `toNode`, sides, ends, `color`, `label`). osign uses them for prototype connections and annotations, with details under the edge's `osign` object (for example `{ "kind": "prototype", "trigger": "click", "action": "navigate", "destination": "frame-2" }`). Edges MUST NOT express containment.

## 10. Components (direction; details in a future RFC)

- Component definitions are `component` nodes, indexed in `resources.components`.
- Instances store **overrides** as the source of truth: a map from component-node path to property overrides.
- An instance's children are **written into the file** as ordinary nodes, so JSON Canvas previews look right. Each one is marked with `osign.instanceOf`. Like coordinates, they're derived: readers rebuild them from the component and the overrides, and reconcile edits made to them as overrides.

## 11. Canonical serialization (normative)

- UTF-8, 2-space indentation, LF line endings, one value per line, trailing newline.
- **Key order:**
  - **Top level:** `osign`, `nodes`, `edges`, then other keys.
  - **Node:** `id`, `type`, `x`, `y`, `width`, `height`, `color`, `label`, `text`, `file`, `subpath`, `url`, `background`, `backgroundStyle`, `osign`, then other keys.
  - **Node `osign` object:** `kind`, `parent`, `transform`, `style`, then other keys.
  - **Header:** `version`, `id`, `name`, `meta`, `resources`, then other keys.
  - **Edge:** `id`, `fromNode`, `fromSide`, `fromEnd`, `toNode`, `toSide`, `toEnd`, `color`, `label`, `osign`, then other keys.
  - **Everywhere else**, including unknown keys and everything nested in style, text and the rest: sorted lexicographically, recursively.
- **Numbers:**
  - Integral values are written as integers.
  - Other values use the shortest representation that round-trips.
  - `-0` is written as `0`.
- **Defaults are omitted:** `rotation: 0`, `visible: true`, `locked: false`, empty `style`.

## 12. Compatibility and versioning

- **Unknown keys MUST be preserved** at the top level, on nodes, inside `osign` objects and on edges.
- `version` follows semver. Readers MUST reject a higher major version and SHOULD read a higher minor version, preserving unknown fields.
- **Migration from 0.1:**
  - Node types become kinds (`shape` becomes `rect`).
  - A text node's `label` becomes its `text`.
  - `style.fill` and `style.stroke` become `fills` and `strokes`; `borderRadius` becomes `cornerRadius`.
  - Absolute geometry becomes parent-relative.
  - `children` and `"contains"` edges are dropped in favor of `osign.parent`.
  - `fromNodeId`/`toNodeId` become `fromNode`/`toNode`.
  - `layout`, `component` and `data` move under `osign`.

## 13. Git integration (informative)

- **Derived coordinates are noisy in diffs.** JSON Canvas coordinates are absolute, so moving a parent rewrites every descendant's `x`/`y` (101 changed lines for a frame with 99 children). The osign layer changes once.
- **Recommended merge setup:**
  - A semantic merge driver: `*.canvas merge=osign`. It replays both sides against the merge base, merges independent edits, and reports only true conflicts.
  - A `textconv` diff driver (`diff=osign`) that prints semantic changes ("frame-1 moved +37,0").
- **What the driver does in tested scenarios:**
  - It merged cleanly where git's line merge conflicted: a frame moved on one branch with a child moved on the other; both branches adding layers to the same parent; reorder plus resize of one layer; non-overlapping edits to the same text.
  - It reported true conflicts: the same property changed differently on both sides, and overlapping text edits.

## 14. Open items for later RFCs

- Auto-layout semantics (planned as a CSS flexbox/grid subset).
- Component overrides in detail.
- Variables and modes.
- Prototyping interactions.
- Mixed-style text runs.
