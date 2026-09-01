(ns cljdu.test-runner
  (:require [clojure.test :as t]
            [cljdu.format-test]
            [cljdu.nav-test]
            [cljdu.persist-test]
            [cljdu.scan-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-all-tests #"cljdu\..*-test")]
    (System/exit (if (pos? (+ fail error)) 1 0))))
