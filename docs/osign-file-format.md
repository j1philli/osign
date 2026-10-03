# osign file format

This document describes the osign file structure (built on top of JSON Canvas 1.0) and the minimal fields needed for interoperability.

## Hello world example
```json
{
  "id": "doc-1",
  "name": "Hello osign",
  "osign_version": "0.1.0",
  "canvas_version": "1.0",
  "meta": {
    "author": "example",
    "created_at": "2025-02-09T12:00:00Z"
  },
  "nodes": [
    {
      "id": "frame-1",
      "type": "frame",
      "x": 0,
      "y": 0,
      "width": 600,
      "height": 400,
      "label": "Frame",
      "style": { "fill": "#ffffff" }
    },
    {
      "id": "text-1",
      "type": "text",
      "x": 40,
      "y": 60,
      "width": 200,
      "height": 40,
      "label": "Hello world",
      "style": { "fill": "#222222", "typographyTokenId": "body/regular" }
    }
  ],
  "edges": [
    {
      "id": "edge-1",
      "fromNodeId": "frame-1",
      "toNodeId": "text-1",
      "label": "contains"
    }
  ],
  "resources": {
    "tokens": [
      { "id": "body/regular", "fontFamily": "Inter", "fontSize": 16, "lineHeight": 24 }
    ]
  }
}
```

## Top-level shape
- `osign_version: string`
- `canvas_version: string` (JSON Canvas version, e.g. "1.0")
- `meta: object` (author, timestamps, tools, etc.)
- `nodes: Node[]`
- `edges: Edge[]`
- Optional `resources: object` (images, fonts, tokens, etc.)

## Node
Fields (extend JSON Canvas with UI semantics):
- `id: string`
- `type: string` (e.g. `"frame" | "component" | "instance" | "text" | "shape" | "group" | "image"`)
- `x: number`, `y: number`
- `width: number`, `height: number`
- `file?: string` — required for node types that reference external resources (e.g. `image`, `asset`, JSON Canvas `file`). The value should be a path or URI to the resource, matching JSON Canvas' [`file` requirement](https://jsoncanvas.org/spec/1.0#file-type-nodes).
- `z_index?: number`
- `label?: string`
- `parentId?: string`
- `children?: string[]`
- `style?: object` (e.g. `fill`, `stroke`, `borderRadius`, `typographyTokenId`, etc.)
- `layout?: object` (e.g. `layoutMode`, `spacing`, `padding`, `alignment`, `constraints`)
- `component?: { key?: string; variantProps?: Record<string, string>; }`
- `data?: Record<string, any>` for future extensions.

## Edge
- `id: string`
- `fromNodeId: string`
- `toNodeId: string`
- `label?: string`
- Optional style fields (e.g. line type, color) under `style?: object`
- `data?: Record<string, any>` for future extensions.

## Extensibility
- Unknown fields under `data` (and any optional objects) are allowed and should be preserved by tools.
- An osign document must remain valid JSON Canvas; added fields must not break base compatibility.
- Tools should not strip or rewrite unrecognized keys; round-tripping must keep unknown fields intact.
