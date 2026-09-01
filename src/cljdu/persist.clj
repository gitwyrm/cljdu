(ns cljdu.persist
  "Remember the last scanned directory."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn state-file
  ^java.io.File []
  (io/file (System/getProperty "user.home") ".config" "cljdu" "state.edn"))

(defn load-state
  "Map with optional `:root`, or {}."
  ([]
   (load-state (state-file)))
  ([^java.io.File file]
   (try
     (if (.isFile file)
       (let [data (edn/read-string (slurp file))]
         (if (map? data)
           (select-keys data [:root])
           {}))
       {})
     (catch Exception _
       {}))))

(defn save-state!
  ([m]
   (save-state! (state-file) m))
  ([^java.io.File file m]
   (try
     (when-let [parent (.getParentFile file)]
       (.mkdirs parent))
     (spit file (pr-str (select-keys m [:root])))
     file
     (catch Exception _
       nil))))
