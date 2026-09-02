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
