(ns build
  "Build tasks:
  - `uberjar` packaged service for deployment
  - `clean` remove all build assets and jar files"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.build.api :as build-api]))

(defn dependency-mrjar?
  [^String jar-file]
  (when (and jar-file (.exists (io/file jar-file)))
    (with-open [r (java.util.jar.JarFile. jar-file)]
      (some-> (.getManifest r)
              (.getMainAttributes)
              (.getValue "Multi-Release")
              (= "true")))))

(defn find-mrjar-deps
  "Today I learnt that some dependencies ship bytecodes for different
  JVM versions in what is called an MRJAR, or Multi-Release jar."
  []
  (->> (build-api/create-basis)
       :libs
       vals
       (map (comp first :paths))
       (filter dependency-mrjar?)))

(def service-name
  "starling-roundup-service")

(defn version
  []
  (some->> (io/resource (str service-name ".version"))
           slurp
           str/trim))

(defn project-config
  "Project configuration to support all tasks"
  [version]
  {:class-directory "target/classes"
   :main-namespace 'piotr-yuxuan/service-template.main
   :project-basis (build-api/create-basis)
   :uberjar-file (format "target/service-template-standalone-%s.jar" version)})

(defn clean
  "Remove a directory
  - `:path '\"directory-name\"'` for a specific directory
  - `nil` (or no command line arguments) to delete `target` directory
    `target` is the default directory for build artefacts
  Checks that `.` and `/` directories are not deleted"
  [directory]
  (when-not (contains? #{"." "/"} directory)
    (build-api/delete {:path (or (:path directory) "target")})))

(defn uberjar
  "Create an archive containing Clojure and the build of the project
  Merge command line configuration to the default project config"
  [options]
  (let [{:keys [class-directory main-namespace project-basis uberjar-file]} (merge (project-config (version)) options)]
    (clean "target")
    (build-api/copy-dir {:src-dirs ["src" "resources"]
                         :target-dir class-directory})

    (build-api/compile-clj {:basis project-basis
                            :class-dir class-directory
                            :src-dirs ["src"]
                            :compile-opts {:direct-linking true}
                            :javac-opts [;; No debug info such as line
                                         ;; numbers or local vars.
                                         "-g:none"
                                         "-source" "24"
                                         "-target" "24"]})

    (build-api/uber {:basis project-basis
                     :class-dir class-directory
                     :main main-namespace
                     :uber-file uberjar-file})
    (println uberjar-file)))
