(ns cljdu.format-test
  (:require [cljdu.format :as fmt]
            [clojure.test :refer [deftest is]]))

(deftest format-bytes-units
  (is (= "0 B" (fmt/format-bytes 0)))
  (is (= "512 B" (fmt/format-bytes 512)))
  (is (= "1.0 KB" (fmt/format-bytes 1024)))
  (is (= "1.5 KB" (fmt/format-bytes 1536)))
  (is (= "1.0 MB" (fmt/format-bytes (* 1024 1024))))
  (is (= "1.0 GB" (fmt/format-bytes (* 1024 1024 1024))))
  (is (= "1.0 TB" (fmt/format-bytes (* 1024 1024 1024 1024)))))

(deftest percent-rounding
  (is (= "—" (fmt/percent 1 0)))
  (is (= "0%" (fmt/percent 0 100)))
  (is (= "<1%" (fmt/percent 1 1000)))
  (is (= "43%" (fmt/percent 43 100)))
  (is (= "50%" (fmt/percent 1 2)))
  (is (= "100%" (fmt/percent 10 10)))
  (is (= "99%" (fmt/percent 999 1000))))

(deftest share-is-0-to-100
  (is (= 0 (fmt/share 1 0)))
  (is (= 0 (fmt/share 0 100)))
  (is (= 0 (fmt/share 1 1000)))
  (is (= 43 (fmt/share 43 100)))
  (is (= 50 (fmt/share 1 2)))
  (is (= 100 (fmt/share 10 10)))
  (is (= 100 (fmt/share 999 1000)))
  (is (= 100 (fmt/share 200 100))))
