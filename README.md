# D2 Server and Confluence Macro

A Clojure-based server for rendering D2 diagrams and a Confluence user macro for embedding them.

## Roadmap

### D2 Parameters Support
- [x] theme (int, default 1)
- [x] layout (enum: dagre, elk, default elk)
- [x] server (string, default http://localhost:3001)
- [x] sketch (boolean, default false)
- [x] scale (float, default 0.5)
- [x] format (enum: svg, png, default svg)
- [x] direction (enum: up, left, right, down)
- [x] preset (string, URL to preset D2 code)

### API Endpoints
- [x] POST /svg - Render SVG
- [x] POST /png - Render PNG
- [x] POST /format - Format D2 code

### Other Features
- [x] HTML entity decoding for D2 code
- [x] Multipart form data handling
- [x] Confluence user macro integration
- [x] Error handling and CORS support
- [x] D2 binary integration
- [ ] Swagger API docs (TODO)

## API Docs

API documentation available at `/swagger` when server is running.

## Macro Parameters

- theme: D2 theme ID (int, default 1)
- layout: Layout engine (dagre/elk, default elk)
- server: Server URL (string, default http://localhost:3001)
- sketch: Render as sketch (boolean, default false)
- scale: Scale factor (string, default 0.5)
- format: Output format (svg/png, default svg)
- direction: Diagram direction (up/left/right/down)
- preset: Preset D2 code URL (string)