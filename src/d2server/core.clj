(ns d2server.core
  (:require [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [cheshire.core :as json]
            [ring.util.response :as response]
            [ring.middleware.params :as params]
            [ring.middleware.multipart-params :as multipart])
  (:gen-class)
  (:import [org.apache.commons.lang3 StringEscapeUtils]))

(defn fetch-preset
  "Fetch preset D2 code from URL"
  [url]
  (try
    (slurp url)
    (catch Exception e
      (println "Error fetching preset:" (.getMessage e))
      "")))

(defn format-d2-code
  "Format D2 code using d2 fmt"
  [d2-code]
  (let [temp-dir (System/getProperty "java.io.tmpdir")
        input-file (str temp-dir "/format-input-" (System/currentTimeMillis) ".d2")]
    (try
      ;; Write D2 code to temp file
      (spit input-file d2-code)

      ;; Execute d2 fmt command
      (let [result (shell/sh "d2" "fmt" input-file)]
        (if (= 0 (:exit result))
          ;; Success - return formatted code
          (slurp input-file)
          ;; Error - return original code
          d2-code))

      (catch Exception e
        (println "Error formatting D2 code:" (.getMessage e))
        d2-code)

      (finally
        ;; Cleanup temp file
        (try
          (io/delete-file input-file true)
          (catch Exception _))))))

(defn decode-html-entities
  "Decode HTML entities in string using Apache Commons"
  [s]
  (StringEscapeUtils/unescapeHtml4 s))

(defn render-d2
  "Renders D2 code to SVG or PNG using d2 binary"
  [d2-code format & {:keys [theme layout preset-url sketch scale]
                     :or {theme 0 layout "dagre" sketch false scale 1.0}}]
  (let [temp-dir (System/getProperty "java.io.tmpdir")
        input-file (str temp-dir "/input-" (System/currentTimeMillis) ".d2")
        output-file (str temp-dir "/output-" (System/currentTimeMillis) "." format)
        ;; Decode HTML entities and combine preset and user code
        decoded-d2-code (decode-html-entities d2-code)
        preset-code (if preset-url (fetch-preset preset-url) "")
        combined-code (if (empty? preset-code)
                        decoded-d2-code
                        (str preset-code "\n\n" decoded-d2-code))]
    (try
      ;; Write combined D2 code to temp file
      (spit input-file combined-code)

       ;; Execute d2 command
      (let [cmd (cond-> ["d2"
                         "--theme" (str theme)
                         "--layout" layout]
                  sketch (conj "--sketch")
                  (not= scale 1.0) (conj "--scale" (str scale))
                  true (conj input-file output-file))
            result (apply shell/sh cmd)]

        (if (= 0 (:exit result))
          ;; Success - read and return the output file
          (let [output-bytes (with-open [in (io/input-stream output-file)]
                               (let [buffer (byte-array (.available in))]
                                 (.read in buffer)
                                 buffer))]
            {:success true
             :data output-bytes
             :content-type (case format
                             "svg" "image/svg+xml"
                             "png" "image/png"
                             "application/octet-stream")})
          ;; Error - include both stderr and stdout
          {:success false
           :error (str "D2 rendering failed (exit code " (:exit result) "):\n"
                       "STDERR: " (:err result) "\n"
                       "STDOUT: " (:out result))}))

      (catch Exception e
        {:success false
         :error (.getMessage e)})

      (finally
        ;; Cleanup temp files
        (try
          (io/delete-file input-file true)
          (io/delete-file output-file true)
          (catch Exception _))))))

(defn parse-params
  "Parse parameters from query string or POST body"
  [request]
  (let [query-string (:query-string request)
        query-params (if query-string
                       (into {} (for [pair (clojure.string/split query-string #"&")]
                                  (let [[k v] (clojure.string/split pair #"=" 2)]
                                    [k (java.net.URLDecoder/decode (or v "") "UTF-8")])))
                       {})
        body-params (or (:params request) {})
        multipart-params-raw (or (:multipart-params request) {})
        ;; Extract values from multipart vectors [filename temp-file content-type]
        multipart-params (into {} (for [[k v] multipart-params-raw]
                                    [k (if (vector? v) (second v) v)]))
        ;; Merge query, body, and multipart params, multipart takes precedence
        all-params (merge query-params body-params multipart-params)]
    all-params))

(defn handle-render
  "Handle D2 rendering requests (GET and POST)"
  [request]
  (let [params (parse-params request)
        d2-code (get params "d2" "")
        format (get params "format" "svg")
        theme (Integer/parseInt (get params "theme" "1"))
        layout (get params "layout" "dagre")
        preset-url (get params "preset")
        sketch (= "true" (get params "sketch" "false"))
        scale (Double/parseDouble (get params "scale" "1.0"))]

    (if (empty? d2-code)
      (-> (response/response "Missing d2 parameter")
          (response/status 400)
          (response/content-type "text/plain")
          (response/header "Access-Control-Allow-Origin" "*"))

      (let [result (render-d2 d2-code format
                              :theme theme
                              :layout layout
                              :preset-url preset-url
                              :sketch sketch
                              :scale scale)]
        (if (:success result)
          (-> (response/response (java.io.ByteArrayInputStream. (:data result)))
              (response/content-type (:content-type result))
              (response/header "Access-Control-Allow-Origin" "*")
              (response/header "Cache-Control" "public, max-age=3600"))
          (-> (response/response (:error result))
              (response/status 500)
              (response/content-type "text/plain")
              (response/header "Access-Control-Allow-Origin" "*")))))))

(defn handle-health
  "Health check endpoint"
  [_]
  (-> (response/response (json/generate-string {:status "ok" :service "d2server"}))
      (response/content-type "application/json")))

(defn handle-format
  "Handle D2 code formatting requests (GET and POST)"
  [request]
  (let [params (parse-params request)
        d2-code (get params "d2" "")]

    (if (empty? d2-code)
      (-> (response/response "Missing d2 parameter")
          (response/status 400)
          (response/content-type "text/plain")
          (response/header "Access-Control-Allow-Origin" "*"))

      (let [decoded-code (decode-html-entities d2-code)
            formatted-code (format-d2-code decoded-code)]
        (-> (response/response formatted-code)
            (response/content-type "text/plain")
            (response/header "Access-Control-Allow-Origin" "*"))))))

(def app
  (-> (ring/ring-handler
       (ring/router
        [["/" {:get handle-health}]
         ["/render" {:get handle-render :post handle-render}]
         ["/svg" {:get #(handle-render (assoc-in % [:params "format"] "svg"))
                  :post #(handle-render (assoc-in % [:params "format"] "svg"))}]
         ["/png" {:get #(handle-render (assoc-in % [:params "format"] "png"))
                  :post #(handle-render (assoc-in % [:params "format"] "png"))}]
         ["/format" {:get handle-format :post handle-format}]])
       (ring/create-default-handler))
      multipart/wrap-multipart-params
      params/wrap-params))

(defn start-server
  "Start the HTTP server"
  [& {:keys [port] :or {port 3000}}]
  (println (str "Starting D2 server on port " port "..."))
  (println "Endpoints:")
  (println "  GET /           - Health check")
  (println "  GET /render     - Render D2 (format=svg|png)")
  (println "  GET /svg        - Render D2 as SVG")
  (println "  GET /png        - Render D2 as PNG")
  (println "  GET /format     - Format D2 code")
  (println "Parameters: d2, theme, layout, preset")
  (jetty/run-jetty app {:port port :join? false}))

(defn -main
  "Main entry point"
  [& args]
  (let [port (if-let [port-arg (first args)]
               (Integer/parseInt port-arg)
               3000)]
    (start-server :port port)
    (println (str "D2 Server running on http://localhost:" port))))