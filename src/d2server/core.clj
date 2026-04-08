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
  (:import [org.apache.commons.lang3 StringEscapeUtils]
           [java.nio.file Files]
           [java.security MessageDigest]
           [javax.imageio ImageIO IIOImage]
           [javax.imageio.metadata IIOMetadataNode]
           [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn fetch-preset
  "Fetch preset D2 code from URL"
  [url]
  (try
    (slurp url)
    (catch Exception e
      (println "Error fetching preset:" (.getMessage e))
      "")))

(defn content-hash
  "Generate a short hash from string content for unique filenames"
  [s]
  (let [digest (MessageDigest/getInstance "SHA-256")
        hash-bytes (.digest digest (.getBytes s "UTF-8"))]
    (subs (apply str (map #(format "%02x" %) hash-bytes)) 0 12)))

(defn unique-suffix []
  (str (System/currentTimeMillis) "-" (rand-int 100000)))

(defn extract-resource-to-file
  "Extract a classpath resource to a persistent temp file, returns path string"
  [resource-path]
  (when-let [res (io/resource resource-path)]
    (let [temp-file (java.io.File/createTempFile "d2-font-" ".ttf")]
      (.deleteOnExit temp-file)
      (with-open [in (io/input-stream res)]
        (io/copy in temp-file))
      (.getAbsolutePath temp-file))))

(def font-path
  (delay (extract-resource-to-file "Agave-Regular-slashed.ttf")))

(defn format-d2-code
  "Format D2 code using d2 fmt"
  [d2-code]
  (let [temp-dir (System/getProperty "java.io.tmpdir")
        input-file (str temp-dir "/format-input-" (unique-suffix) ".d2")]
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

(defn embed-png-metadata
  "Embed D2 source code into PNG tEXt metadata chunk"
  [^bytes png-bytes d2-source]
  (let [reader (.next (ImageIO/getImageReadersByFormatName "png"))
        _ (.setInput reader (ImageIO/createImageInputStream (ByteArrayInputStream. png-bytes)))
        image (.read reader 0)
        metadata (.getImageMetadata reader 0)
        text-entry (doto (IIOMetadataNode. "tEXtEntry")
                     (.setAttribute "keyword" "d2-source")
                     (.setAttribute "value" d2-source))
        text-node (doto (IIOMetadataNode. "tEXt")
                    (.appendChild text-entry))
        root (doto (IIOMetadataNode. "javax_imageio_png_1.0")
               (.appendChild text-node))
        _ (.mergeTree metadata "javax_imageio_png_1.0" root)
        baos (ByteArrayOutputStream.)
        ios (ImageIO/createImageOutputStream baos)
        writer (.next (ImageIO/getImageWritersByFormatName "png"))]
    (.setOutput writer ios)
    (.write writer nil (IIOImage. image nil metadata) nil)
    (.close ios)
    (.dispose writer)
    (.dispose reader)
    (.toByteArray baos)))

(defn extract-png-metadata
  "Extract D2 source code from PNG tEXt metadata chunk"
  [^bytes png-bytes]
  (let [reader (.next (ImageIO/getImageReadersByFormatName "png"))
        _ (.setInput reader (ImageIO/createImageInputStream (ByteArrayInputStream. png-bytes)))
        metadata (.getImageMetadata reader 0)
        tree (.getAsTree metadata "javax_imageio_png_1.0")
        text-node (let [nodes (.getElementsByTagName tree "tEXtEntry")
                        len (.getLength nodes)]
                    (first (for [i (range len)
                                 :let [node (.item nodes i)]
                                 :when (= "d2-source" (.getAttribute node "keyword"))]
                             (.getAttribute node "value"))))]
    (.dispose reader)
    text-node))

(defn render-d2
  "Renders D2 code to SVG or PNG using d2 binary"
  [d2-code format & {:keys [theme layout preset-url sketch scale no-default-styles]
                     :or {theme 0 layout "dagre" sketch false scale 1.0 no-default-styles false}}]
  (let [temp-dir (System/getProperty "java.io.tmpdir")
        suffix (unique-suffix)
        input-file (str temp-dir "/input-" suffix ".d2")
        svg-file (str temp-dir "/output-" suffix ".svg")
        png-file (when (= format "png") (str temp-dir "/output-" suffix ".png"))
        ;; Decode HTML entities and combine default-styles, preset and user code
        decoded-d2-code (decode-html-entities d2-code)
        preset-code (if preset-url (fetch-preset preset-url) "")
        default-styles-resource (when-not no-default-styles
                                  (io/resource "d2server/default-styles.d2"))
        default-styles (if default-styles-resource (slurp default-styles-resource) "")
        combined-code (str default-styles
                           (when-not (empty? default-styles) "\n\n")
                           (when-not (empty? preset-code) preset-code)
                           (when-not (empty? preset-code) "\n\n")
                           decoded-d2-code)
        file-hash (content-hash d2-code)]
    (try
      ;; Write combined D2 code to temp file
      (spit input-file combined-code)

      ;; Execute d2 command
      (let [font @font-path
            cmd (cond-> ["d2"
                         "--theme" (str theme)
                         "--layout" layout]
                  font (into ["--font-regular" font
                              "--font-bold" font
                              "--font-italic" font
                              "--font-semibold" font])
                  sketch (conj "--sketch")
                  (not= scale 1.0) (conj "--scale" (str scale))
                  true (conj input-file svg-file))
            result (apply shell/sh cmd)]

        (if (= 0 (:exit result))
          (if png-file
            ;; PNG: convert SVG -> PNG via rsvg-convert
            (let [convert-result (shell/sh "rsvg-convert" "-o" png-file svg-file)]
              (if (= 0 (:exit convert-result))
                (let [raw-bytes (Files/readAllBytes (.toPath (io/file png-file)))
                      output-bytes (embed-png-metadata raw-bytes d2-code)]
                  {:success true
                   :data output-bytes
                   :filename (str "d2-" file-hash ".png")
                   :content-type "image/png"})
                {:success false
                 :error (str "PNG conversion failed:\n" (:err convert-result))}))
            ;; SVG: read d2 output directly
            (let [output-bytes (Files/readAllBytes (.toPath (io/file svg-file)))]
              {:success true
               :data output-bytes
               :filename (str "d2-" file-hash ".svg")
               :content-type "image/svg+xml"}))
          ;; Error - include both stderr and stdout
          {:success false
           :error (str "D2 rendering failed (exit code " (:exit result) "):\n"
                       "STDERR: " (:err result) "\n"
                       "STDOUT: " (:out result))}))

      (catch Exception e
        {:success false
         :error (.getMessage e)})

      (finally
        (try
          (io/delete-file input-file true)
          (io/delete-file svg-file true)
          (when png-file (io/delete-file png-file true))
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
        scale (Double/parseDouble (get params "scale" "1.0"))
        no-default-styles (= "true" (get params "no-default-styles" "false"))]

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
                              :scale scale
                              :no-default-styles no-default-styles)]
        (if (:success result)
          (-> (response/response (java.io.ByteArrayInputStream. (:data result)))
              (response/content-type (:content-type result))
              (response/header "Access-Control-Allow-Origin" "*")
              (response/header "Content-Disposition"
                               (str "inline; filename=\"" (:filename result) "\""))
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

(defn handle-extract
  "Extract D2 source code from PNG metadata"
  [request]
  (let [multipart-params (or (:multipart-params request) {})
        file-entry (get multipart-params "file")]
    (if (and file-entry (map? file-entry))
      (let [png-bytes (Files/readAllBytes (.toPath (:tempfile file-entry)))
            d2-source (try
                        (extract-png-metadata png-bytes)
                        (catch Exception _ nil))]
        (if d2-source
          (-> (response/response d2-source)
              (response/content-type "text/plain; charset=utf-8")
              (response/header "Access-Control-Allow-Origin" "*"))
          (-> (response/response "No D2 source found in PNG metadata")
              (response/status 404)
              (response/content-type "text/plain")
              (response/header "Access-Control-Allow-Origin" "*"))))
      (-> (response/response "Missing file parameter (multipart upload required)")
          (response/status 400)
          (response/content-type "text/plain")
          (response/header "Access-Control-Allow-Origin" "*")))))

(def app
  (-> (ring/ring-handler
       (ring/router
        [["/" {:get handle-health}]
         ["/render" {:get handle-render :post handle-render}]
         ["/svg" {:get #(handle-render (assoc-in % [:params "format"] "svg"))
                  :post #(handle-render (assoc-in % [:params "format"] "svg"))}]
         ["/png" {:get #(handle-render (assoc-in % [:params "format"] "png"))
                  :post #(handle-render (assoc-in % [:params "format"] "png"))}]
         ["/format" {:get handle-format :post handle-format}]
         ["/extract" {:post handle-extract}]])
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
  (println "  POST /extract   - Extract D2 source from PNG")
  (println "Parameters: d2, theme, layout, preset, sketch, scale, no-default-styles")
  (jetty/run-jetty app {:port port :join? false}))

(defn -main
  "Main entry point"
  [& args]
  (let [port (if-let [port-arg (first args)]
               (Integer/parseInt port-arg)
               3000)]
    (start-server :port port)
    (println (str "D2 Server running on http://localhost:" port))))