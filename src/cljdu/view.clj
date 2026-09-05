(ns cljdu.view
  "Pure helpers that turn scan nodes into gpui.ui widget data."
  (:require [cljdu.format :as fmt]
            [cljdu.nav :as nav]))

(def listing-columns
  [{:id :kind :label "Kind" :width 40 :align :center}
   {:id :name :label "Name"}
   {:id :size :label "Size" :width 92}
   {:id :share :label "" :width 88 :selectable false}
   {:id :pct :label "%" :width 52}])

(def chart-limit 6)

(defn kind-label
  [kind]
  (case kind
    :dir "dir"
    :link "link"
    "file"))

(defn kind-icon
  "DataTable Kind cell: Kit folder, file, or external-link icon.

  `:text` is dump / `cell_text` (dir, file, link). There is no symlink
  glyph in the Kit catalog, so links use `:external-link`."
  [kind]
  {:type :icon
   :icon (case kind
           :dir "folder"
           :link "external-link"
           "file")
   :text (kind-label kind)})

(defn- display-name
  [{:keys [name error]}]
  (str name (when error (str "  (" error ")"))))

(defn listing-rows
  "Table rows for a folder node's immediate children."
  [node]
  (let [parent-size (long (:size node 0))]
    (mapv (fn [{:keys [path kind size] :as child}]
            {:id path
             :cells [(kind-icon kind)
                     (display-name child)
                     (fmt/format-bytes size)
                     {:type :progress
                      :value (fmt/share size parent-size)
                      :width 72}
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
  "Pie / bar colors in slice order (largest first, then Other).

  Sent as per-point `:color` — Kit paints `chart.2` when a slice omits
  it. Tokens are Catppuccin Violet Dark `chart.1`–`chart.5`, then
  `warning` and `danger`."
  ["#89b4fa" "#94e2d5" "#a6e3a1" "#fab387" "#cba6f7"
   "#f9e2af" "#f38ba8"])

(defn chart-color
  "Hex for chart slice `i` (0-based, largest-first)."
  [i]
  (let [n (count chart-palette)
        ix (if (zero? n) 0 (mod (long i) n))]
    (nth chart-palette ix)))

(defn- with-slice-colors
  [slices]
  (into []
        (map-indexed (fn [i s] (assoc s :color (chart-color i))))
        slices))

(defn legend-items
  "Pie legend rows: swatch color, name, size, percent of chart total."
  [slices]
  (let [total (max 1 (reduce + 0 (map #(long (:value % 0)) slices)))]
    (into []
          (map-indexed
           (fn [i {:keys [id label value color]}]
             (cond-> {:label (str label)
                      :color (or color (chart-color i))
                      :size (fmt/format-bytes value)
                      :pct (fmt/percent value total)}
               (some? id) (assoc :id id))))
          slices)))

(defn legend-detail
  "Hover-card copy for a legend row: full path, or Other's fold rule."
  ([item]
   (legend-detail item (System/getProperty "user.home")))
  ([{:keys [id]} home]
   (cond
     (nil? id) nil
     (= id :other) "Entries under 2% of this folder, combined."
     :else (nav/tilde-path (str id) home))))

(defn bar-points
  "Bar-tab chart points: same sizes as `usage-slices`, with formatted
  byte counts on `:display` (Kit `BarChart::label` at the bar tip)."
  [slices]
  (mapv (fn [{:keys [value] :as s}]
          (assoc s :display (fmt/format-bytes value)))
        (or slices [])))

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
         (with-slice-colors
           (cond-> (mapv slice top)
             (pos? other) (conj {:id :other :label "Other" :value other}))))))))

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
