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
  (is (= [{:id "/r/big" :label "big" :value 60 :color "#89b4fa"}
          {:id "/r/mid" :label "mid.txt" :value 30 :color "#94e2d5"}
          {:id "/r/tiny" :label "tiny" :value 10 :color "#a6e3a1"}]
         (view/usage-slices (:children sample-node) 6)))
  (is (= [{:id "/r/big" :label "big" :value 60 :color "#89b4fa"}
          {:id :other :label "Other" :value 40 :color "#94e2d5"}]
         (view/usage-slices (:children sample-node) 1)))
  (is (empty? (view/usage-slices [])))
  (is (empty? (view/usage-slices [{:path "/z" :name "z" :size 0}]))))

(deftest usage-slices-fold-tiny-into-other
  (is (= [{:id "/a" :label "a" :value 100 :color "#89b4fa"}
          {:id :other :label "Other" :value 1 :color "#94e2d5"}]
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

(deftest chart-color-is-slice-index
  (is (= "#89b4fa" (view/chart-color 0)))
  (is (= "#94e2d5" (view/chart-color 1)))
  (is (= "#cba6f7" (view/chart-color 4)))
  (is (= "#f9e2af" (view/chart-color 5)))
  (is (= "#f38ba8" (view/chart-color 6)))
  (is (= 7 (count (set (map view/chart-color (range 7))))))
  (is (= "#89b4fa" (view/chart-color 7))))

(deftest legend-items-include-matching-swatch
  (is (= [{:id "/r/big" :label "big" :color "#89b4fa" :size "60 B" :pct "60%"}
          {:id "/r/mid" :label "mid.txt" :color "#94e2d5" :size "30 B" :pct "30%"}
          {:id "/r/tiny" :label "tiny" :color "#a6e3a1" :size "10 B" :pct "10%"}]
         (view/legend-items (view/usage-slices (:children sample-node) 6))))
  (let [six (view/legend-items
             (mapv (fn [i] {:label (str "n" i) :value 10}) (range 6)))]
    (is (= 6 (count (set (map :color six)))))
    (is (not= (:color (first six)) (:color (last six)))))
  ;; flutter + Other hashed to the same mauve on the old host
  (let [items (view/legend-items
               [{:label "flutter" :value 45}
                {:label "codex" :value 20}
                {:label "clojure" :value 15}
                {:label "Other" :value 8}])]
    (is (= "#89b4fa" (:color (first items))))
    (is (= "#fab387" (:color (last items))))
    (is (not= (:color (first items)) (:color (last items)))))
  (is (empty? (view/legend-items []))))

(deftest legend-detail-is-path-or-other-rule
  (is (= "/r/big" (view/legend-detail {:id "/r/big"} "/home/me")))
  (is (= "~/.config" (view/legend-detail {:id "/home/me/.config"} "/home/me")))
  (is (= "Entries under 2% of this folder, combined."
         (view/legend-detail {:id :other} "/home/me")))
  (is (nil? (view/legend-detail {:label "n0"}))))

(deftest bar-points-put-format-bytes-on-display
  (is (= [{:id "/r/big" :label "big" :value 60 :color "#89b4fa" :display "60 B"}
          {:id "/r/mid" :label "mid.txt" :value 30 :color "#94e2d5" :display "30 B"}
          {:id "/r/tiny" :label "tiny" :value 10 :color "#a6e3a1" :display "10 B"}]
         (view/bar-points (view/usage-slices (:children sample-node) 6))))
  (is (= "450.0 KB"
         (:display (first (view/bar-points
                           [{:id :flutter :label "flutter" :value 460800}])))))
  (is (empty? (view/bar-points []))))
