(ns cljdu.persist-test
  (:require [cljdu.persist :as persist]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]))

(deftest round-trip-root
  (let [f (io/file (System/getProperty "java.io.tmpdir")
                   (str "cljdu-state-" (random-uuid) ".edn"))]
    (try
      (is (= {} (persist/load-state f)))
      (persist/save-state! f {:root "/tmp/data" :extra :ignored})
      (is (= {:root "/tmp/data"} (persist/load-state f)))
      (finally
        (.delete f)))))

(deftest corrupt-file-is-empty
  (let [f (io/file (System/getProperty "java.io.tmpdir")
                   (str "cljdu-bad-" (random-uuid) ".edn"))]
    (try
      (spit f "{{{not edn")
      (is (= {} (persist/load-state f)))
      (finally
        (.delete f)))))
