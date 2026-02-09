# D2 Server and Confluence Macro

A Clojure-based server for rendering D2 diagrams and a Confluence user macro for embedding them.

## Roadmap

### D2 Parameters Support
- [x] theme (int, default 1)
- [x] layout (enum: dagre, elk, default elk)
- [x] server (string, default http://localhost:3000)
- [x] sketch (boolean, default false)
- [x] scale (float, default 0.8)
- [x] format (enum: svg, png, default svg)
- [x] direction (enum: up, left, right, down, default down)
- [x] preset (string, URL to preset D2 code)
- [x] no-default-styles (boolean, default false - disables C4-like default styles)

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
- [x] Zoom panel with pan for SVG diagrams
- [x] Download buttons for SVG/PNG formats
- [x] Status display of current parameters
- [x] Improved error handling with network error detection

## Macro Parameters

- theme: D2 theme ID (int, default 1)
- layout: Layout engine (dagre/elk, default elk)
- server: Server URL (string, default http://localhost:3000)
- sketch: Render as sketch (boolean, default false)
- scale: Scale factor (string, default 0.8)
- format: Output format (svg/png, default svg)
- direction: Diagram direction (up/left/right/down, default down)
- preset: Preset D2 code URL (string)
- no-default-styles: Disable C4-like default styles (boolean, default false)

## Build Instructions

### Development

Run the server locally:
```
make run
```

### Production Build

Build uberjar:
```
make uberjar
```

### Docker

Build Docker image:
```
make docker-build
```

Run and test Docker container:
```
make docker-run
```

## Known Issues

- GraalVM native image build is currently not working
