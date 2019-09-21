(ns rksm-project-templates.graal-resource-indexer
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as s]))

(defn pattern-json-for-files [files]
  (pp/cl-format nil "{
  \"resources\": [
~{    {\"pattern\": ~s}~^,~%~}
  ]
}

" files))

(defn -main [& args]
  (let [resource-dir (io/file "resources/")
        config-file (io/file "./target/graal-resource-config.json")
        files (->> resource-dir
                   file-seq
                   (filter #(.isFile %))
                   (map #(.getName %))
                   #_(map #(s/escape % {\. "\\."})))
        json (pattern-json-for-files files)]
    (when-not (.exists (.getParentFile config-file))
      (.mkdir (.getParentFile config-file)))
    (spit (io/file config-file) json)
    (pp/cl-format true "~a written" config-file)))
