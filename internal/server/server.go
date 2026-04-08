package server

import (
	"net/http"
	"time"

	"d2server/internal/render"
)

type Server struct {
	renderer      *render.Renderer
	defaultStyles string
	httpClient    *http.Client
}

func New(renderer *render.Renderer, defaultStyles string) *Server {
	return &Server{
		renderer:      renderer,
		defaultStyles: defaultStyles,
		httpClient:    &http.Client{Timeout: 10 * time.Second},
	}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /{$}", s.handleHealth)
	mux.HandleFunc("GET /render", s.handleRender)
	mux.HandleFunc("POST /render", s.handleRender)
	mux.HandleFunc("GET /svg", s.handleSVG)
	mux.HandleFunc("POST /svg", s.handleSVG)
	mux.HandleFunc("GET /png", s.handlePNG)
	mux.HandleFunc("POST /png", s.handlePNG)
	mux.HandleFunc("GET /format", s.handleFormat)
	mux.HandleFunc("POST /format", s.handleFormat)
	mux.HandleFunc("POST /extract", s.handleExtract)
	return mux
}
