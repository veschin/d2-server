package format

import (
	"strings"

	"oss.terrastruct.com/d2/d2format"
	"oss.terrastruct.com/d2/d2parser"
)

func Format(code string) string {
	ast, err := d2parser.Parse("", strings.NewReader(code), nil)
	if err != nil {
		return code
	}
	return d2format.Format(ast)
}
