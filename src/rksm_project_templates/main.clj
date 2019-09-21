(ns rksm-project-templates.main
  (:gen-class)
  (:refer-clojure :exclude [replace])
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.stacktrace :as stacktrace]
            [clojure.string :refer [includes? lower-case replace split trim]])
  (:import java.io.File))

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
  ([opts]
   (let [project-templates
         #_(-> "clj-deps-project.edn" io/file slurp read-string)
         (-> "clj-deps-project.edn" io/resource slurp read-string)]
     (realize-project! opts project-templates)))
  ([{:keys [directory project-name namespace] :as _opts}
    {template-files :files :as _project-templates}]
   (let [project-name (or project-name
                          (-> directory io/file .getName (replace #"_" "-")))
         namespace-name (or namespace project-name)
         replacements {:main-ns (str namespace-name ".main")
                       :nrepl-ns (str namespace-name ".nrepl")
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
                 ;; (println (stacktrace/print-stack-trace e))
                 (System/exit 3)))))

(def summary "Robert's project creator.")

(defn parse-args [args]
  (loop [opts {}
         errors []
         [next & rest] args
         opt nil]
    (case next
      nil {:errors errors
           :options opts
           :summary summary}
      ("-h" "--help") (recur (assoc opts :help true) errors rest nil)
      ("-d" "--directory") (recur opts errors rest :directory)
      ("-p" "--project-name") (recur opts errors rest :project-name)
      "--ns" (recur opts errors rest :namespace)
      (let [opts (if opt (assoc opts opt next) opts)]
        (recur opts errors rest nil)))))

(defn -main [& args]
  (let [{:keys [errors options summary]} (parse-args args)]
    (cond
      (not-empty errors) (do
                           (println (first errors))
                           (System/exit 1))
      (:help options) (do
                        (println summary)
                        (System/exit 0))
      :else (realize-project-safe! options))))



(comment

  (pprint (cli/parse-opts ["-d" "./src"] cli-options))
  
  (cli/parse-opts ["-h"] cli-options)
  (def args (cli/parse-opts ["-d" "./test-project"] cli-options))
  (def args (cli/parse-opts ["-d" "./test-project" "--ns" "rksm.test-project"] cli-options))
  (def directory (-> args :options :directory))
  (def project-templates (-> "clj-deps-project.edn" io/resource slurp read-string))

  (realize-project! (-> args :options) project-templates)

  (delete-all directory)
  )



;; let [{:keys [errors options summary]} (cli/parse-opts args cli-options)]

;; (def cli-options
;;   [["-d" "--directory DIR" "Project directory"
;;     :validate [#(not (.exists (io/file %))) "Project directory already exists"]]
;;    [nil "--ns NAMESPACE" "Main namespace to use (optional)"
;;     :validate [#(not (includes? % " ")) "Invalid namespace name"]]
;;    ["-p" "--project-name NAME" "Project name (optional)"
;;     :validate [#(not (includes? % " ")) "Invalid project name"]]
;;    ["-h" "--help"]])
