(ns cljdu.scan-test
  (:require [cljdu.scan :as scan]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]])
  (:import [java.nio.file Files]))

(defn- tmp-dir []
  (doto (io/file (System/getProperty "java.io.tmpdir")
                 (str "cljdu-scan-" (random-uuid)))
    (.mkdirs)))

(defn- spit-file [dir name contents]
  (let [f (io/file dir name)]
    (io/make-parents f)
    (spit f contents)
    f))

(defn- rm-rf [^java.io.File f]
  (when (.isDirectory f)
    (doseq [c (.listFiles f)]
      (rm-rf c)))
  (.delete f))

(deftest aggregates-and-sorts-largest-first
  (let [root (tmp-dir)]
    (try
      (spit-file root "small.txt" "ab")
      (spit-file root "big.txt" (apply str (repeat 4000 "x")))
      (spit-file (io/file root "sub") "inner.txt" (apply str (repeat 200 "y")))
      (let [tree (scan/scan-tree root)
            names (mapv :name (:children tree))]
        (is (= :dir (:kind tree)))
        (is (= ["big.txt" "sub" "small.txt"] names)
            "largest-first, then remaining")
        (is (= :file (:kind (first (:children tree)))))
        (is (= :dir (:kind (some #(when (= "sub" (:name %)) %) (:children tree)))))
        (is (= 1 (:dir-count tree)))
        (is (= 3 (:file-count tree)))
        (is (zero? (:skipped tree)))
        (is (= (:size tree)
               (reduce + (map :size (:children tree))))))
      (finally
        (rm-rf root)))))

(deftest does-not-follow-symlinks
  (let [root (tmp-dir)
        target (doto (io/file root "target") (.mkdirs))]
    (try
      (spit-file target "a.txt" (apply str (repeat 100 "z")))
      (Files/createSymbolicLink
       (.toPath (io/file root "linkdir"))
       (.toPath target)
       (into-array java.nio.file.attribute.FileAttribute []))
      (Files/createSymbolicLink
       (.toPath (io/file root "loop"))
       (.toPath root)
       (into-array java.nio.file.attribute.FileAttribute []))
      (let [tree (scan/scan-tree root)
            kinds (into {} (map (juxt :name :kind) (:children tree)))]
        (is (= :link (get kinds "linkdir")))
        (is (= :link (get kinds "loop")))
        (is (= 1 (:dir-count tree))
            "target/ counts as a dir; symlink dirs are not walked")
        (is (zero? (:skipped tree))))
      (finally
        (rm-rf root)))))

(deftest unreadable-directory-is-skipped
  (let [root (tmp-dir)
        secret (doto (io/file root "secret") (.mkdirs))]
    (try
      (spit-file secret "hidden.txt" "nope")
      (.setReadable secret false)
      (.setExecutable secret false)
      (let [tree (scan/scan-tree root)
            secret-node (some #(when (= "secret" (:name %)) %) (:children tree))]
        (if (nil? (.listFiles secret))
          (do
            (is (pos? (:skipped tree)))
            (is (= "unreadable" (:error secret-node)))
            (is (zero? (:size secret-node))))
          (is (some? secret-node)
              "process can still list the directory (root/owner); node is present")))
      (finally
        (.setReadable secret true)
        (.setExecutable secret true)
        (rm-rf root)))))

(deftest stale-generation-aborts-mid-walk
  (let [root (tmp-dir)
        gen (atom 1)
        started (promise)
        gate (promise)]
    (try
      (spit-file (io/file root "sub") "f.txt" "x")
      (let [stale (future
                    (scan/scan-tree root {:generation 1
                                          :current-gen gen
                                          :on-progress (fn [_]
                                                         (deliver started true)
                                                         (deref gate 5000 nil))}))]
        (is (deref started 5000 nil) "scan started")
        (reset! gen 2)
        (deliver gate true)
        (is (thrown-with-msg? Exception #"stale scan" @stale)))
      (finally
        (rm-rf root)))))

(deftest stale-generation-aborts
  (let [root (tmp-dir)
        gen (atom 1)]
    (try
      (spit-file root "a.txt" "hello")
      (reset! gen 2)
      (is (thrown-with-msg? Exception #"stale scan"
                            (scan/scan-tree root {:generation 1 :current-gen gen})))
      (finally
        (rm-rf root)))))

(deftest progress-reports-without-pre-scan
  (let [root (tmp-dir)
        seen (atom [])]
    (try
      (spit-file root "a.txt" "x")
      (scan/scan-tree root {:on-progress (fn [p] (swap! seen conj p))})
      (is (seq @seen))
      (is (some :done @seen))
      (is (some :path @seen))
      (finally
        (rm-rf root)))))
