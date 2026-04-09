# syntax=docker/dockerfile:1
FROM golang:alpine AS builder

WORKDIR /app
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download

COPY . .
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 go build -ldflags="-s -w" -o d2server .

FROM alpine:3.21

WORKDIR /app

COPY resources/rsvg-convert.tar.gz /tmp/
RUN tar xzf /tmp/rsvg-convert.tar.gz -C / \
    && rm /tmp/rsvg-convert.tar.gz

COPY --from=builder /app/d2server .
COPY --from=builder /app/resources/Agave-Regular-slashed.ttf /usr/share/fonts/
RUN /bin/fc-cache -f

EXPOSE 3000

ENTRYPOINT ["./d2server"]
