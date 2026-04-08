package render

import (
	"context"
	"sync"

	"oss.terrastruct.com/d2/d2graph"
	"oss.terrastruct.com/d2/d2lib"
	"oss.terrastruct.com/d2/d2renderers/d2fonts"
	"oss.terrastruct.com/d2/d2renderers/d2svg"
	d2log "oss.terrastruct.com/d2/lib/log"
	"oss.terrastruct.com/d2/lib/textmeasure"
	"oss.terrastruct.com/util-go/go2"
)

type Params struct {
	Theme  int64
	Layout string
	Sketch bool
	Scale  float64
}

type Renderer struct {
	fontFamily     *d2fonts.FontFamily
	layoutResolver func(string) (d2graph.LayoutGraph, error)
	rulerPool      sync.Pool
}

func NewRenderer(fontFamily *d2fonts.FontFamily, layoutResolver func(string) (d2graph.LayoutGraph, error)) *Renderer {
	r := &Renderer{
		fontFamily:     fontFamily,
		layoutResolver: layoutResolver,
	}
	r.rulerPool.New = func() any {
		ruler, _ := textmeasure.NewRuler()
		return ruler
	}
	return r
}

func (r *Renderer) SVG(ctx context.Context, code string, p Params) ([]byte, error) {
	ctx = d2log.WithDefault(ctx)
	ruler := r.rulerPool.Get().(*textmeasure.Ruler)
	defer r.rulerPool.Put(ruler)

	compileOpts := &d2lib.CompileOptions{
		Ruler:          ruler,
		LayoutResolver: r.layoutResolver,
		Layout:         go2.Pointer(p.Layout),
		FontFamily:     r.fontFamily,
	}

	renderOpts := &d2svg.RenderOpts{
		ThemeID: go2.Pointer(p.Theme),
		Sketch:  go2.Pointer(p.Sketch),
	}

	if p.Scale != 1.0 {
		renderOpts.Scale = go2.Pointer(p.Scale)
	}

	diagram, _, err := d2lib.Compile(ctx, code, compileOpts, renderOpts)
	if err != nil {
		return nil, err
	}

	return d2svg.Render(diagram, renderOpts)
}
