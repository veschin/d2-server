# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

D2 diagram rendering microservice (Clojure) with Confluence user macro integration. Stateless HTTP server that shells out to the `d2` CLI binary for SVG/PNG rendering and code formatting.

## Commands

```bash
make run              # Dev server on :3000 (clj -M:run)
make test             # Run tests (clj -M:test)
make uberjar          # Build target/d2server.jar (clj -T:build uber)
make docker-build     # Build Docker image
make docker-run       # Build, start container, run httpie integration tests, stop
make compose-test     # Same via Docker Compose
```

Port override: `make DP_PORT=9090 docker-run`

## Architecture

Single-namespace server: `src/d2server/core.clj` -- all routing, parameter parsing, rendering, and response handling in one file.

**Rendering pipeline**: HTTP request -> `parse-params` (merges query/form/multipart) -> `decode-html-entities` -> optional `fetch-preset` (external D2 snippet URL) -> concatenate default-styles + preset + user D2 code -> write temp file -> shell out to `d2` CLI -> read output bytes -> respond with content-type and cache headers -> cleanup temp files.

**Key design decisions**:
- Font (`resources/Agave-Regular-slashed.ttf`) is lazily extracted from classpath to a temp file once per JVM via `delay`
- Default C4-like styles (`src/d2server/default-styles.d2`) are prepended to all renders unless `no-default-styles=true`
- PNG rendering requires Chromium + Node.js (Playwright) -- significant Docker setup
- Client-side caching in the Confluence macro (`macro.vtl`) via IndexedDB with SHA-256 content hashing
- All responses include `Access-Control-Allow-Origin: *` and 1-hour cache for images

**Endpoints**: `GET/POST /` (health), `/svg`, `/png`, `/render` (generic), `/format` -- documented in `swagger.yaml`.

**Render parameters**: `d2` (required), `format`, `theme`, `layout`, `sketch`, `scale`, `preset`, `no-default-styles`.

## Dependencies

Clojure 1.11.1, Reitit (routing), Ring/Jetty (HTTP), Cheshire (JSON), Commons Lang3 (HTML entity decoding). Runtime requires `d2` binary on PATH, Chromium and Node.js for PNG.

## Testing

No test framework -- integration tests only via `make docker-run` / `make compose-test` using httpie against running container (health, SVG, PNG, format endpoints).
