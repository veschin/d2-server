# D2 Server

Microservice for rendering D2 diagrams. Confluence user macro included.

## Quick Start

```bash
make docker-run  # build + start on localhost:3333
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | / | Health check |
| GET, POST | /svg | Render SVG |
| GET, POST | /png | Render PNG |
| GET, POST | /render | Render (format via param) |
| GET, POST | /format | Format D2 code |
| POST | /extract | Extract D2 source from PNG metadata |

## Render Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| d2 | string | required | D2 diagram source |
| format | enum | svg | Output format: `svg`, `png` |
| theme | int | 1 | D2 theme ID |
| layout | enum | dagre | Layout engine: `dagre`, `elk` |
| sketch | bool | false | Sketch rendering mode |
| scale | float | 1.0 | Scale factor |
| preset | string | - | URL to preset D2 snippet |
| no-default-styles | bool | false | Disable C4-like default styles |

## Confluence Macro

`macro.vtl` provides a Confluence user macro for embedding rendered diagrams. Parameters: `theme`, `layout`, `server`, `sketch`, `scale`, `format`, `direction`, `preset`, `no-default-styles`. Diagram source is entered as macro body. Client-side caching via IndexedDB with SHA-256 content hashing.

## Docker

```bash
make docker-build    # Build image (~76MB, Alpine-based)
make docker-run      # Build + start container as daemon
make docker-test     # Build, start, run integration tests, stop
make compose-test    # Same via Docker Compose
```

`docker-compose.yml` exposes port 3333 with host networking. Memory usage under load: ~50MB.

## Development

```bash
make run     # Dev server on :3333
make test    # go test ./...
make build   # Compile binary
```

Startup time: <50ms.

## Architecture

| Package | Responsibility |
|---------|---------------|
| `main.go` | Entrypoint, embedded resources |
| `internal/render` | D2 library (in-process) + rsvg-convert for PNG |
| `internal/server` | HTTP handlers, parameter parsing, CORS |
| `internal/format` | D2 code formatting |
| `internal/pngmeta` | PNG metadata read/write |

SVG rendering uses the D2 Go library directly - no CLI subprocess. PNG output pipes SVG through `rsvg-convert`.

## Requirements

- Go 1.22+
- `rsvg-convert` (librsvg) - required for PNG output in local development (bundled in Docker image)
