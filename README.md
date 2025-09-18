# D2 Server and Confluence Macro

A Clojure-based server for rendering D2 diagrams and a Confluence user macro for embedding them.

## Features

- [x] D2 diagram rendering to SVG/PNG
- [x] HTTP API endpoints (/svg, /png, /format)
- [x] HTML entity decoding for D2 code
- [x] Support for D2 parameters: theme, layout, sketch, scale
- [x] Preset D2 code merging
- [x] Multipart form data handling
- [x] Confluence user macro integration
- [x] Direction parameter for diagrams
- [x] Error handling and CORS support

## Setup

1. Install D2: https://d2lang.com/
2. Run `clj -M:run` to start the server on port 3001
3. Import `macro.vtl` as a Confluence user macro

## API

- `POST /svg` - Render SVG
- `POST /png` - Render PNG
- `POST /format` - Format D2 code
- Parameters: d2 (code), theme, layout, sketch, scale, preset

## Macro Parameters

- theme: D2 theme ID (int, default 1)
- layout: Layout engine (dagre/elk, default elk)
- server: Server URL (string, default http://localhost:3001)
- sketch: Render as sketch (boolean, default false)
- scale: Scale factor (string, default 0.5)
- format: Output format (svg/png, default svg)
- direction: Diagram direction (up/left/right/down)
- preset: Preset D2 code URL (string)