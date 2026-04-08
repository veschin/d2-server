# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

D2 diagram rendering microservice (Go) with Confluence user macro integration. Stateless HTTP server that renders D2 diagrams in-process via the d2 library. SVG output is native; PNG output shells out to `rsvg-convert`.

## Commands

```bash
make build            # Compile binary: ./d2server
make run              # Dev server on :3000 (go run . <port>)
make test             # Run tests (go test ./...)
make docker-build     # Build Docker image
make docker-run       # Build, start container, run httpie integration tests, stop
make compose-test     # Same via Docker Compose
```

Port override: `make DP_PORT=9090 docker-run`

## Architecture

Entry point: `main.go` -- loads embedded font and default styles, wires layout resolvers, constructs renderer and server.

Package layout:
- `internal/render` -- D2 rendering: SVG via d2 library in-process, PNG via `rsvg-convert` subprocess
- `internal/server` -- HTTP routing and request handling
- `internal/format` -- D2 code formatting
- `internal/pngmeta` -- PNG metadata extraction (embedded D2 source)

**Rendering pipeline**: HTTP request -> parameter parsing (query/form/multipart) -> HTML entity decoding -> optional preset fetch (external D2 snippet URL) -> concatenate default-styles + preset + user D2 code -> `render.Renderer` -> SVG bytes from d2 library -> (PNG path) pipe through `rsvg-convert` -> respond with content-type and cache headers.

**Key design decisions**:
- Font (`resources/Agave-Regular-slashed.ttf`) and default styles (`resources/default-styles.d2`) are embedded at compile time via `//go:embed`
- Default C4-like styles are prepended to all renders unless `no-default-styles=true`
- PNG rendering requires `rsvg-convert` on PATH (provided by `librsvg` in Docker)
- Client-side caching in the Confluence macro (`macro.vtl`) via IndexedDB with SHA-256 content hashing
- All responses include `Access-Control-Allow-Origin: *` and 1-hour cache for images

**Endpoints**: `GET/POST /` (health), `/svg`, `/png`, `/render` (generic), `/format`, `POST /extract` -- documented in `swagger.yaml`.

**Render parameters**: `d2` (required), `format`, `theme`, `layout`, `sketch`, `scale`, `preset`, `no-default-styles`.

## Dependencies

Go stdlib, `oss.terrastruct.com/d2` (in-process rendering + layout engines: dagre, elk). Runtime requires `rsvg-convert` on PATH for PNG output.

## Testing

No unit test framework -- integration tests only via `make docker-run` / `make compose-test` using httpie against running container (health, SVG, PNG, format endpoints).
