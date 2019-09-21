(ns rksm-project-templates.main
  (:gen-class)
  (:refer-clojure :exclude [replace])
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [cl-format pprint]]
            [clojure.string :refer [includes? lower-case replace split trim]]
            [clojure.tools.cli :as cli])
  (:import java.io.File))

(def templates {:cljs "cljs-deps.edn"
                :clj "clj-deps.edn"})

(defn replace-in-str
  "Replace ${XXX} with (:xxx replacements}."
  [^String string replacements]
  (replace string
           #"\$\{([^\}]+)\}"
           (fn [[match var]]
             (get replacements (keyword (lower-case var)) match))))

(defn file-list
  ([file-map replacements]
   (file-list file-map replacements []))
  ([file-map replacements base-dir]
   (for [[name content] file-map
         :let [dirs (split (replace-in-str name replacements) #"/")
               dirs (concat base-dir dirs)
               files (if (string? content)
                       [[dirs (-> content trim (replace-in-str replacements))]]
                       (file-list content replacements dirs))]
         f files]
     f)))

(defn write-files!
  [file-list base-dir]
  (doseq [[path content] file-list
          :let [^File file (apply io/file base-dir path)]]
    (.mkdirs (.getParentFile file))
    (spit file content)))

(defn delete-all [dir]
  (doseq [^File f (reverse (file-seq (io/file dir)))] (.delete f)))

(defn realize-project!
  ([{:keys [template] :as opts}]
   (let [project-templates (->> template keyword (get templates) io/resource slurp read-string)]
     (realize-project! opts project-templates)))
  ([{:keys [directory project-name namespace] :as _opts}
    {template-files :files :as _project-templates}]
   (let [project-name (or project-name
                          (-> directory io/file .getName (replace #"_" "-")))
         namespace-name (or namespace project-name)
         replacements {:ns namespace-name
                       :project-file-name (-> namespace-name (replace #"-" "_") (replace #"\." "/"))}
         project-files (file-list template-files replacements)]
     (write-files! project-files directory)
     project-files)))


(defn realize-project-safe! [{:keys [directory] :as opts}]
  (cond
    (nil? (seq directory)) (do (println "No project directory specified (-d).")
                               (System/exit 2))
    :else (try (realize-project! opts)
               (catch Exception ^Exception e
                 (println e)
                 ;; Hmm doesn't work with graal?
                 ;; (println (stacktrace/print-stack-trace e))
                 (System/exit 3)))))

(def summary "Robert's project creator.")

(def cli-options
  [["-l" "--list" "List project templates"]
   ["-t" "--template NAME" "Project template to use"
    :validate [#(contains? templates (keyword %)) "Unknown template"]
    :default :clj]
   ["-d" "--directory DIR" "Project directory"
    :validate [#(not (.exists (io/file %))) "Project directory already exists"]]
   ["-n" "--namespace NAMESPACE" "Main namespace to use (optional)"
    :validate [#(not (includes? % " ")) "Invalid namespace name"]]
   ["-p" "--project-name NAME" "Project name (optional)"
    :validate [#(not (includes? % " ")) "Invalid project name"]]
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [errors options summary]} (cli/parse-opts args cli-options)]
    (cond
      (not-empty errors) (do
                           (println (first errors))
                           (System/exit 1))
      (:help options) (do
                        (println summary)
                        (System/exit 0))
      (:list options) (do
                        (cl-format true
                                   "The following templates are available:~%~{  ~a~^~%~}~%"
                                   (map name (keys templates)))
                        (System/exit 0))
      :else (realize-project-safe! options))))




(comment

  (pprint (cli/parse-opts ["-d" "./src" "-t" "2cljs"] cli-options))

  (cli/parse-opts ["-h"] cli-options)
  (def args (cli/parse-opts ["-d" "./test-project"] cli-options))
  (def args (cli/parse-opts ["-d" "./test-project" "--ns" "rksm.test-project"] cli-options))
  (def directory (-> args :options :directory))
  (def project-templates (-> "clj-deps-project.edn" io/resource slurp read-string))

  (realize-project! (-> args :options) project-templates)

  (delete-all directory)
  )
