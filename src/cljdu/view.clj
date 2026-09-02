(ns cljdu.view
  "Pure helpers that turn scan nodes into gpui.ui widget data."
  (:require [cljdu.format :as fmt]
            [cljdu.nav :as nav]))

(def listing-columns
  [{:id :kind :label "Kind" :width 56}
   {:id :name :label "Name"}
   {:id :size :label "Size" :width 92}
   {:id :pct :label "%" :width 52}])

(def chart-limit 6)

(defn kind-label
  [kind]
  (case kind
    :dir "dir"
    :link "link"
    "file"))

(defn- display-name
  [{:keys [name error]}]
  (str name (when error (str "  (" error ")"))))

(defn listing-rows
  "Table rows for a folder node's immediate children."
  [node]
  (let [parent-size (long (:size node 0))]
    (mapv (fn [{:keys [path kind size] :as child}]
            {:id path
             :cells [(kind-label kind)
                     (display-name child)
                     (fmt/format-bytes size)
                     (fmt/percent size parent-size)]})
          (or (:children node) []))))

(defn- short-label
  [s]
  (let [s (str s)]
    (if (<= (count s) 18)
      s
      (str (subs s 0 16) "…"))))

(defn- slice
  [{:keys [path name size]}]
  {:id path
   :label (short-label name)
   :value (long (or size 0))})

(defn usage-slices
  "Largest-first chart points for `children`, capped at `n` plus Other."
  ([children]
   (usage-slices children chart-limit))
  ([children n]
   (let [kids (vec (or children []))
         n (max 0 (long n))]
     (if (or (empty? kids) (zero? n))
       []
       (let [points (if (<= (count kids) n)
                      (mapv slice kids)
                      (let [top (subvec kids 0 n)
                            rest-size (reduce + 0 (map #(long (:size % 0))
                                                       (subvec kids n)))]
                        (cond-> (mapv slice top)
                          (pos? rest-size)
                          (conj {:id :other :label "Other" :value rest-size}))))]
         (filterv #(pos? (:value %)) points))))))

(defn breadcrumb-items
  "Breadcrumb widget items from the scan root to `cwd`."
  ([root cwd]
   (breadcrumb-items root cwd (System/getProperty "user.home")))
  ([root cwd home]
   (mapv (fn [crumb]
           {:id (:path crumb)
            :label (nav/crumb-caption crumb root home)})
         (nav/breadcrumbs root cwd))))

(defn window-title
  "OS window title, including the current folder when one is open."
  ([root cwd]
   (window-title root cwd (System/getProperty "user.home")))
  ([root cwd home]
   (if root
     (str "cljdu — " (nav/tilde-path (or cwd root) home))
     "cljdu")))
