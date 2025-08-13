(ns piotr-yuxuan.service-template.openapi-spec
  (:import
   (java.io ByteArrayOutputStream OutputStreamWriter)
   (org.openapitools.openapidiff.core OpenApiCompare)
   (org.openapitools.openapidiff.core.model ChangedOpenApi DiffResult)
   (org.openapitools.openapidiff.core.output ConsoleRender)))

(defn diff
  "Compare two OpenAPI specs, return a diff object."
  ^ChangedOpenApi [^String left ^String right]
  (OpenApiCompare/fromLocations left right))

(defn compatible?
  [^ChangedOpenApi diff]
  (contains? #{DiffResult/NO_CHANGES DiffResult/COMPATIBLE} (.isCoreChanged diff)))

(defn changes
  ^String [^ChangedOpenApi diff]
  (with-open [baos (ByteArrayOutputStream.)]
    (.render (ConsoleRender.) diff (OutputStreamWriter. baos))
    (.toString baos "UTF-8")))
