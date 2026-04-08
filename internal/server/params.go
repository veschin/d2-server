package server

import "net/http"

func parseParams(r *http.Request) map[string]string {
	params := make(map[string]string)

	for k, v := range r.URL.Query() {
		if len(v) > 0 {
			params[k] = v[0]
		}
	}

	if err := r.ParseForm(); err == nil {
		for k, v := range r.PostForm {
			if len(v) > 0 {
				params[k] = v[0]
			}
		}
	}

	if err := r.ParseMultipartForm(32 << 20); err == nil && r.MultipartForm != nil {
		for k, v := range r.MultipartForm.Value {
			if len(v) > 0 {
				params[k] = v[0]
			}
		}
	}

	return params
}
