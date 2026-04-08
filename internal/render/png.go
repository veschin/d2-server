package render

import (
	"bytes"
	"fmt"
	"os/exec"
	"regexp"
)

var fontRegex = regexp.MustCompile(`d2-\d+-font-(?:regular|bold|italic|semibold)`)

func ReplaceFonts(svg []byte) []byte {
	return fontRegex.ReplaceAll(svg, []byte("Agave"))
}

func ConvertSVGToPNG(svg []byte) ([]byte, error) {
	modifiedSVG := ReplaceFonts(svg)

	cmd := exec.Command("rsvg-convert")
	cmd.Stdin = bytes.NewReader(modifiedSVG)

	var out bytes.Buffer
	cmd.Stdout = &out

	var stderr bytes.Buffer
	cmd.Stderr = &stderr

	if err := cmd.Run(); err != nil {
		return nil, fmt.Errorf("rsvg-convert failed: %s", stderr.String())
	}

	return out.Bytes(), nil
}
