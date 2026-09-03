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

(def chart-palette
  "Pie colors in slice order (largest first, then Other).

  Matches clj-gpui `pie_palette`: theme `chart.1`–`chart.5`, then
  `warning` and `danger`. Indexing (not a label hash) keeps six-plus
  slices unique — the old 5-color hash made flutter and Other the
  same mauve."
  ["#89b4fa" "#94e2d5" "#a6e3a1" "#fab387" "#cba6f7"
   "#f9e2af" "#f38ba8"])

(defn chart-color
  "Hex for pie slice `i` (0-based, largest-first). Same order as the host."
  [i]
  (let [n (count chart-palette)
        ix (if (zero? n) 0 (mod (long i) n))]
    (nth chart-palette ix)))

(defn legend-items
  "Pie legend rows: swatch color, name, size, percent of chart total."
  [slices]
  (let [total (max 1 (reduce + 0 (map #(long (:value % 0)) slices)))]
    (into []
          (map-indexed
           (fn [i {:keys [label value]}]
             {:label (str label)
              :color (chart-color i)
              :size (fmt/format-bytes value)
              :pct (fmt/percent value total)}))
          slices)))

(defn usage-slices
  "Largest-first chart points for `children`, capped at `n` plus Other.

  Slices under 2% of the folder are folded into Other so the chart
  is not a row of unreadable labels."
  ([children]
   (usage-slices children chart-limit))
  ([children n]
   (let [kids (filterv #(pos? (long (:size % 0))) (or children []))
         n (max 0 (long n))
         total (max 1 (reduce + 0 (map #(long (:size % 0)) kids)))
         min-size (long (Math/ceil (* (double total) 0.02)))]
     (if (or (empty? kids) (zero? n))
       []
       (let [[big small] (split-with #(>= (long (:size % 0)) min-size) kids)
             top (vec (take n big))
             rest (concat (drop n big) small)
             other (reduce + 0 (map #(long (:size % 0)) rest))]
         (cond-> (mapv slice top)
           (pos? other) (conj {:id :other :label "Other" :value other})))))))

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
