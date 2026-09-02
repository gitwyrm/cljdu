(ns cljdu.view-test
  (:require [cljdu.view :as view]
            [clojure.test :refer [deftest is]]))

(def sample-node
  {:path "/r"
   :name "r"
   :kind :dir
   :size 100
   :children [{:path "/r/big" :name "big" :kind :dir :size 60}
              {:path "/r/mid" :name "mid.txt" :kind :file :size 30}
              {:path "/r/tiny" :name "tiny" :kind :link :size 10
               :error "unreadable"}]})

(deftest listing-rows-match-children
  (is (= [{:id "/r/big" :cells ["dir" "big" "60 B" "60%"]}
          {:id "/r/mid" :cells ["file" "mid.txt" "30 B" "30%"]}
          {:id "/r/tiny" :cells ["link" "tiny  (unreadable)" "10 B" "10%"]}]
         (view/listing-rows sample-node)))
  (is (empty? (view/listing-rows {:children []}))))

(deftest usage-slices-cap-and-other
  (is (= [{:id "/r/big" :label "big" :value 60}
          {:id "/r/mid" :label "mid.txt" :value 30}
          {:id "/r/tiny" :label "tiny" :value 10}]
         (view/usage-slices (:children sample-node) 6)))
  (is (= [{:id "/r/big" :label "big" :value 60}
          {:id :other :label "Other" :value 40}]
         (view/usage-slices (:children sample-node) 1)))
  (is (empty? (view/usage-slices [])))
  (is (empty? (view/usage-slices [{:path "/z" :name "z" :size 0}]))))

(deftest usage-slices-fold-tiny-into-other
  (is (= [{:id "/a" :label "a" :value 100}
          {:id :other :label "Other" :value 1}]
         (view/usage-slices [{:path "/a" :name "a" :size 100}
                             {:path "/b" :name "b" :size 1}] 6))))

(deftest breadcrumb-items-use-paths-as-ids
  (is (= [{:id "/home/me" :label "~"}
          {:id "/home/me/proj" :label "proj"}]
         (view/breadcrumb-items "/home/me" "/home/me/proj" "/home/me"))))

(deftest window-title-includes-folder
  (is (= "cljdu" (view/window-title nil nil "/home/me")))
  (is (= "cljdu — ~/.config" (view/window-title "/home/me/.config"
                                                "/home/me/.config"
                                                "/home/me"))))

(deftest chart-color-matches-host-utf8-hash
  ;; "a" bytes [97] → 97 % 5 = 2 → chart.3
  (is (= "#a6e3a1" (view/chart-color "a")))
  ;; "big" → 98+105+103 = 306 % 5 = 1 → chart.2
  (is (= "#94e2d5" (view/chart-color "big")))
  ;; "Other" → 514 % 5 = 4 → chart.5
  (is (= "#cba6f7" (view/chart-color "Other")))
  (is (= (view/chart-color "chunk.bin") (view/chart-color "chunk.bin")))
  (is (contains? (set view/chart-palette) (view/chart-color "…"))))

(deftest legend-items-include-matching-swatch
  (is (= [{:label "big" :color "#94e2d5" :size "60 B" :pct "60%"}
          {:label "mid.txt" :color (view/chart-color "mid.txt") :size "30 B" :pct "30%"}
          {:label "tiny" :color (view/chart-color "tiny") :size "10 B" :pct "10%"}]
         (view/legend-items (view/usage-slices (:children sample-node) 6))))
  (is (empty? (view/legend-items []))))
