package server

import (
	"context"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"html"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"d2server/internal/format"
	"d2server/internal/pngmeta"
	"d2server/internal/render"
)

func contentHash(s string) string {
	h := sha256.Sum256([]byte(s))
	return fmt.Sprintf("%x", h[:6])
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok", "service": "d2server"})
}

func (s *Server) handleRender(w http.ResponseWriter, r *http.Request) {
	s.renderWithFormat(w, r, "")
}

func (s *Server) handleSVG(w http.ResponseWriter, r *http.Request) {
	s.renderWithFormat(w, r, "svg")
}

func (s *Server) handlePNG(w http.ResponseWriter, r *http.Request) {
	s.renderWithFormat(w, r, "png")
}

func (s *Server) renderWithFormat(w http.ResponseWriter, r *http.Request, forceFormat string) {
	params := parseParams(r)

	rawD2 := params["d2"]
	if rawD2 == "" {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Content-Type", "text/plain")
		http.Error(w, "Missing d2 parameter", http.StatusBadRequest)
		return
	}

	hash := contentHash(rawD2)

	outputFormat := forceFormat
	if outputFormat == "" {
		outputFormat = params["format"]
	}
	if outputFormat == "" {
		outputFormat = "svg"
	}

	theme := int64(1)
	if v, err := strconv.ParseInt(params["theme"], 10, 64); err == nil {
		theme = v
	}

	layout := params["layout"]
	if layout == "" {
		layout = "dagre"
	}

	sketch := params["sketch"] == "true"

	scale := 1.0
	if v, err := strconv.ParseFloat(params["scale"], 64); err == nil {
		scale = v
	}

	noDefaultStyles := params["no-default-styles"] == "true"
	preset := params["preset"]

	d2Code := html.UnescapeString(rawD2)

	var presetCode string
	if preset != "" {
		presetCode = s.fetchPreset(r.Context(), preset)
	}

	var parts []string
	if !noDefaultStyles {
		parts = append(parts, s.defaultStyles)
	}
	if presetCode != "" {
		parts = append(parts, presetCode)
	}
	parts = append(parts, d2Code)
	combined := strings.Join(parts, "\n\n")

	ctx, cancel := context.WithTimeout(r.Context(), 30*time.Second)
	defer cancel()

	svgBytes, err := s.renderer.SVG(ctx, combined, render.Params{
		Theme:  theme,
		Layout: layout,
		Sketch: sketch,
		Scale:  scale,
	})
	if err != nil {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	switch outputFormat {
	case "png":
		pngBytes, err := render.ConvertSVGToPNG(svgBytes)
		if err != nil {
			w.Header().Set("Access-Control-Allow-Origin", "*")
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		pngBytes, _ = pngmeta.Embed(pngBytes, rawD2)
		w.Header().Set("Content-Type", "image/png")
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Content-Disposition", fmt.Sprintf(`inline; filename="d2-%s.png"`, hash))
		w.Header().Set("Cache-Control", "public, max-age=3600")
		w.Write(pngBytes)
	default:
		w.Header().Set("Content-Type", "image/svg+xml")
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Content-Disposition", fmt.Sprintf(`inline; filename="d2-%s.svg"`, hash))
		w.Header().Set("Cache-Control", "public, max-age=3600")
		w.Write(svgBytes)
	}
}

func (s *Server) fetchPreset(ctx context.Context, url string) string {
	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return ""
	}
	resp, err := s.httpClient.Do(req)
	if err != nil {
		return ""
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return ""
	}
	return string(body)
}

func (s *Server) handleFormat(w http.ResponseWriter, r *http.Request) {
	params := parseParams(r)
	d2Code := params["d2"]
	if d2Code == "" {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Content-Type", "text/plain")
		http.Error(w, "Missing d2 parameter", http.StatusBadRequest)
		return
	}
	decoded := html.UnescapeString(d2Code)
	formatted := format.Format(decoded)
	w.Header().Set("Content-Type", "text/plain")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Write([]byte(formatted))
}

func (s *Server) handleExtract(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseMultipartForm(32 << 20); err != nil {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		http.Error(w, "Missing file parameter (multipart upload required)", http.StatusBadRequest)
		return
	}
	file, _, err := r.FormFile("file")
	if err != nil {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		http.Error(w, "Missing file parameter (multipart upload required)", http.StatusBadRequest)
		return
	}
	defer file.Close()

	data, err := io.ReadAll(file)
	if err != nil {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	source, ok := pngmeta.Extract(data)
	if !ok {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		http.Error(w, "No D2 source found in PNG metadata", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "text/plain")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Write([]byte(source))
}
