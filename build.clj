(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file "target/d2server.jar")

(defn clean [_]
  (println "Cleaning target directory...")
  (b/delete {:path "target"}))

(defn uber [_]
  (println "Cleaning...")
  (clean nil)
  (println "Writing POM...")
  (b/write-pom {:class-dir class-dir
                :lib 'd2server/d2server
                :version "1.0.0"
                :basis basis
                :src-dirs ["src"]})
  (println "Copying sources and resources...")
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (println "Compiling Clojure code...")
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (println "Building uberjar...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'd2server.core})
  (println "Uberjar built successfully:" uber-file))
