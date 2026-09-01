(ns cljdu.nav
  "Navigation over a scanned tree without walking the filesystem again."
  (:require [clojure.string :as str])
  (:import [java.io File]
           [java.util.regex Pattern]))

(defn find-node
  "Find the node whose `:path` equals `path`, or nil."
  [tree path]
  (when (and tree path)
    (if (= (:path tree) path)
      tree
      (some #(find-node % path) (:children tree)))))

(defn parent-path
  "Parent of `path`, or nil at filesystem root."
  [path]
  (when path
    (.getParent (File. (str path)))))

(defn can-go-up?
  "True when `cwd` is a descendant of `root`."
  [root cwd]
  (boolean
   (and root cwd
        (not= root cwd)
        (str/starts-with? (str cwd) (str root File/separator)))))

(defn go-up
  "Move cwd to its parent, but not above `root`."
  [root cwd]
  (if (can-go-up? root cwd)
    (or (parent-path cwd) root)
    root))

(defn- display-name
  [path]
  (let [n (.getName (File. (str path)))]
    (if (seq n) n (str path))))

(defn breadcrumbs
  "Path segments from `root` to `cwd` as [{:name :path} ...]."
  [root cwd]
  (let [root (str root)
        cwd (str (or cwd root))
        sep File/separator]
    (if (or (= root cwd)
            (not (str/starts-with? cwd (str root sep))))
      [{:name (display-name root) :path root}]
      (let [rel (subs cwd (inc (count root)))
            parts (remove empty? (str/split rel (re-pattern (Pattern/quote sep))))]
        (reduce (fn [acc part]
                  (let [parent (:path (peek acc))
                        path (str parent sep part)]
                    (conj acc {:name part :path path})))
                [{:name (display-name root) :path root}]
                parts)))))

(defn enter
  "If `node` is a directory, return its path; otherwise return cwd."
  [cwd node]
  (if (and node (= :dir (:kind node)))
    (:path node)
    cwd))

(defn children-sorted
  "Immediate children already sorted largest-first."
  [node]
  (vec (:children node)))
