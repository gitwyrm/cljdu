(ns cljdu.scan
  "Recursive disk-usage scanner. Runs on ordinary JVM filesystem APIs.

  Does not follow symbolic links. Permission and vanishing-file errors are
  counted as skips rather than aborting the walk."
  (:require [clojure.string :as str])
  (:import [java.nio.file
            AccessDeniedException
            DirectoryStream
            Files
            LinkOption
            NoSuchFileException
            Path
            Paths]
           [java.nio.file.attribute BasicFileAttributes]
           [java.io IOException]))

(set! *warn-on-reflection* true)

(def ^:private ^"[Ljava.nio.file.LinkOption;" no-follow
  (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))

(defn- as-path
  ^Path [x]
  (cond
    (instance? Path x) x
    (instance? java.io.File x) (.toPath ^java.io.File x)
    :else (Paths/get (str x) (into-array String []))))

(defn stale?
  [generation current-gen]
  (and (some? current-gen)
       (not= generation @current-gen)))

(defn- stale!
  [generation current-gen]
  (when (stale? generation current-gen)
    (throw (ex-info "stale scan" {:stale true :generation generation}))))

(defn- attrs
  ^BasicFileAttributes [^Path path]
  (Files/readAttributes path BasicFileAttributes no-follow))

(defn- dir-name
  [^Path path]
  (let [n (.getFileName path)]
    (if n
      (str n)
      (str path))))

(defn- empty-node
  [^Path path kind error]
  {:path (str path)
   :name (dir-name path)
   :kind kind
   :size 0
   :file-count 0
   :dir-count 0
   :skipped (if error 1 0)
   :children []
   :error error})

(defn- sort-children
  [children]
  (vec (sort (fn [a b]
               (let [c (compare (long (:size b 0)) (long (:size a 0)))]
                 (if (zero? c)
                   (compare (str/lower-case (str (:name a)))
                            (str/lower-case (str (:name b))))
                   c)))
             children)))

(defn- list-children
  [^Path dir]
  (try
    (with-open [^DirectoryStream stream (Files/newDirectoryStream dir)]
      (vec (iterator-seq (.iterator stream))))
    (catch AccessDeniedException _
      ::denied)
    (catch NoSuchFileException _
      ::missing)
    (catch IOException _
      ::denied)))

(defn- leaf-node
  [^Path path kind size]
  {:path (str path)
   :name (dir-name path)
   :kind kind
   :size size
   :file-count 1
   :dir-count 0
   :skipped 0
   :children []
   :error nil})

(defn scan-tree
  "Walk `root` and return an aggregated tree.

  Options:

    :generation   token for this scan
    :current-gen  atom of the latest generation; a mismatch aborts with
                  `ex-info` `{:stale true}`
    :on-progress  fn of {:path :bytes :files :dirs :skipped}
    :visited      atom of canonical directory paths already entered"
  ([root]
   (scan-tree root nil))
  ([root {:keys [generation current-gen on-progress visited]}]
   (let [root (as-path root)
         visited (or visited (atom #{}))
         last-progress (atom 0)
         totals (atom {:bytes 0 :files 0 :dirs 0 :skipped 0})]
     (letfn [(progress! [path]
               (when on-progress
                 (let [now (System/currentTimeMillis)]
                   (when (>= (- now @last-progress) 80)
                     (reset! last-progress now)
                     (on-progress (assoc @totals :path (str path)))))))
             (bump-file [size]
               (swap! totals (fn [t]
                               (-> t
                                   (update :files inc)
                                   (update :bytes + size)))))
             (walk [^Path path]
               (stale! generation current-gen)
               (progress! path)
               (let [^BasicFileAttributes a (try
                                              (attrs path)
                                              (catch AccessDeniedException _ nil)
                                              (catch NoSuchFileException _ nil)
                                              (catch IOException _ nil))]
                 (cond
                   (nil? a)
                   (do (swap! totals update :skipped inc)
                       (empty-node path :file "unreadable"))

                   (.isSymbolicLink a)
                   (let [size (try (.size a) (catch Exception _ 0))]
                     (bump-file size)
                     (leaf-node path :link size))

                   (.isRegularFile a)
                   (let [size (try (.size a) (catch Exception _ 0))]
                     (bump-file size)
                     (leaf-node path :file size))

                   (.isDirectory a)
                   (let [real (try
                                (str (.toRealPath path (into-array LinkOption [])))
                                (catch Exception _
                                  (str (.toAbsolutePath path))))]
                     (if (contains? @visited real)
                       (do (swap! totals update :skipped inc)
                           (empty-node path :dir "loop"))
                       (do
                         (swap! visited conj real)
                         (swap! totals update :dirs inc)
                         (let [listed (list-children path)]
                           (if (keyword? listed)
                             (do (swap! totals update :skipped inc)
                                 (empty-node path :dir
                                             (if (= listed ::missing)
                                               "missing"
                                               "unreadable")))
                             (let [children (mapv walk listed)
                                   size (reduce + 0 (map :size children))
                                   files (reduce + 0 (map :file-count children))
                                   dirs (reduce + 0 (map (fn [c]
                                                           (cond-> (long (:dir-count c 0))
                                                             (= :dir (:kind c)) inc))
                                                         children))
                                   skipped (reduce + 0 (map :skipped children))]
                               {:path (str path)
                                :name (dir-name path)
                                :kind :dir
                                :size size
                                :file-count files
                                :dir-count dirs
                                :skipped skipped
                                :children (sort-children children)
                                :error nil}))))))

                   :else
                   (let [size (try (.size a) (catch Exception _ 0))]
                     (bump-file size)
                     (leaf-node path :file size)))))]
       (let [tree (walk root)]
         (when on-progress
           (on-progress (assoc @totals :path (str root) :done true)))
         tree)))))
