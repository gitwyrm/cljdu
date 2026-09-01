(ns cljdu.nav-test
  (:require [cljdu.nav :as nav]
            [clojure.test :refer [deftest is]]))

(def sample
  {:path "/r"
   :name "r"
   :kind :dir
   :size 30
   :children [{:path "/r/a"
               :name "a"
               :kind :dir
               :size 20
               :children [{:path "/r/a/b"
                           :name "b"
                           :kind :file
                           :size 20
                           :children []}]}
              {:path "/r/c"
               :name "c"
               :kind :file
               :size 10
               :children []}]})

(deftest find-node-walks-tree
  (is (= "r" (:name (nav/find-node sample "/r"))))
  (is (= "a" (:name (nav/find-node sample "/r/a"))))
  (is (= "b" (:name (nav/find-node sample "/r/a/b"))))
  (is (nil? (nav/find-node sample "/missing"))))

(deftest enter-only-directories
  (is (= "/r/a" (nav/enter "/r" (nav/find-node sample "/r/a"))))
  (is (= "/r" (nav/enter "/r" (nav/find-node sample "/r/c")))))

(deftest go-up-stops-at-root
  (is (= "/r/a" (nav/go-up "/r" "/r/a/b")))
  (is (= "/r" (nav/go-up "/r" "/r/a")))
  (is (= "/r" (nav/go-up "/r" "/r")))
  (is (nav/can-go-up? "/r" "/r/a"))
  (is (not (nav/can-go-up? "/r" "/r"))))

(deftest breadcrumbs-from-root-to-cwd
  (is (= ["r"] (mapv :name (nav/breadcrumbs "/r" "/r"))))
  (is (= ["r" "a" "b"] (mapv :name (nav/breadcrumbs "/r" "/r/a/b"))))
  (is (= ["/r" "/r/a" "/r/a/b"] (mapv :path (nav/breadcrumbs "/r" "/r/a/b")))))

(deftest tilde-path-uses-home
  (is (= "~" (nav/tilde-path "/home/me" "/home/me")))
  (is (= "~/.config" (nav/tilde-path "/home/me/.config" "/home/me")))
  (is (= "/var/log" (nav/tilde-path "/var/log" "/home/me")))
  (is (= "" (nav/tilde-path nil "/home/me"))))

(deftest crumb-caption-tildes-the-root
  (let [crumbs (nav/breadcrumbs "/home/me/.config" "/home/me/.config/cljdu")]
    (is (= "~/.config"
           (nav/crumb-caption (first crumbs) "/home/me/.config" "/home/me")))
    (is (= "cljdu"
           (nav/crumb-caption (second crumbs) "/home/me/.config" "/home/me")))))
