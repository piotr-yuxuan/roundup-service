(ns piotr-yuxuan.service-template.openapi-spec
  "Provide utilities to compare OpenAPI specifications, determine compatibility,
  and render human-readable diffs between API versions."
  (:import
   (java.io ByteArrayOutputStream OutputStreamWriter)
   (org.openapitools.openapidiff.core OpenApiCompare)
   (org.openapitools.openapidiff.core.model ChangedOpenApi DiffResult)
   (org.openapitools.openapidiff.core.output ConsoleRender)))

(defn diff
  "Compare two OpenAPI specification files or URLs and return a diff
  object representing all changes."
  ^ChangedOpenApi [^String left ^String right]
  (OpenApiCompare/fromLocations left right))

(defn compatible?
  "Determine whether an OpenAPI diff is backward-compatible, returning
  true if no breaking changes are present."
  [^ChangedOpenApi diff]
  (contains? #{DiffResult/NO_CHANGES DiffResult/COMPATIBLE} (.isCoreChanged diff)))

(defn changes
  "Return a human-readable string describing all changes in an OpenAPI
  diff object."
  ^String [^ChangedOpenApi diff]
  (with-open [baos (ByteArrayOutputStream.)]
    (.render (ConsoleRender.) diff (OutputStreamWriter. baos))
    (.toString baos "UTF-8")))
