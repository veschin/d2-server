package main

import (
	_ "embed"
	"fmt"
	"log"
	"net/http"
	"os"
	"strconv"

	"d2server/internal/render"
	"d2server/internal/server"

	"oss.terrastruct.com/d2/d2graph"
	"oss.terrastruct.com/d2/d2layouts/d2dagrelayout"
	"oss.terrastruct.com/d2/d2layouts/d2elklayout"
	"oss.terrastruct.com/d2/d2renderers/d2fonts"
)

//go:embed resources/Agave-Regular-slashed.ttf
var agaveFont []byte

//go:embed resources/default-styles.d2
var defaultStyles string

func main() {
	port := 3000
	if len(os.Args) > 1 {
		p, err := strconv.Atoi(os.Args[1])
		if err != nil {
			log.Fatalf("Invalid port: %s", os.Args[1])
		}
		port = p
	}

	fontFamily, err := d2fonts.AddFontFamily("Agave", agaveFont, agaveFont, agaveFont, agaveFont)
	if err != nil {
		log.Fatalf("Failed to load font: %v", err)
	}

	layoutResolver := func(engine string) (d2graph.LayoutGraph, error) {
		switch engine {
		case "elk":
			return d2elklayout.DefaultLayout, nil
		default:
			return d2dagrelayout.DefaultLayout, nil
		}
	}

	renderer := render.NewRenderer(fontFamily, layoutResolver)
	srv := server.New(renderer, defaultStyles)

	addr := fmt.Sprintf(":%d", port)
	fmt.Printf("Starting D2 server on port %d\n", port)
	fmt.Printf("Endpoints:\n")
	fmt.Printf("  GET  /        - Health check\n")
	fmt.Printf("  */   /render  - Render D2 diagram (format param)\n")
	fmt.Printf("  */   /svg     - Render D2 as SVG\n")
	fmt.Printf("  */   /png     - Render D2 as PNG\n")
	fmt.Printf("  */   /format  - Format D2 code\n")
	fmt.Printf("  POST /extract - Extract D2 from PNG metadata\n")

	log.Fatal(http.ListenAndServe(addr, srv.Handler()))
}
