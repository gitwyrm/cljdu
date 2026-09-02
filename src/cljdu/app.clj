(ns cljdu.app
  "cljdu — a small native disk-usage browser."
  (:require [cljdu.format :as fmt]
            [cljdu.nav :as nav]
            [cljdu.persist :as persist]
            [cljdu.scan :as scan]
            [cljdu.theme]
            [cljdu.view :as view]
            [clojure.string :as str]
            [gpui.platform :as platform]
            [gpui.ratom :as r]
            [gpui.ui :as ui]))

(defonce !gen (atom 0))

(defonce !state
  (r/atom {:root nil
           :cwd nil
           :tree nil
           :selected nil
           :scanning? false
           :progress nil
           :fatal nil
           :path-draft ""}))

(defn- home
  []
  (System/getProperty "user.home"))

(defn- apply-root
  [state root]
  (assoc state
         :root root
         :cwd root
         :tree nil
         :selected nil
         :scanning? true
         :progress {:path root :bytes 0 :files 0 :dirs 0 :skipped 0}
         :fatal nil
         :path-draft (nav/tilde-path root)))

(defn start-scan!
  [root]
  (let [root (.getCanonicalPath (java.io.File. (str root)))
        gen (swap! !gen inc)]
    (swap! !state apply-root root)
    (persist/save-state! {:root root})
    (future
      (try
        (let [tree (scan/scan-tree
                    root
                    {:generation gen
                     :current-gen !gen
                     :on-progress (fn [p]
                                    (when (= gen @!gen)
                                      (swap! !state assoc :progress p)))})]
          (when (= gen @!gen)
            (swap! !state assoc
                   :tree tree
                   :scanning? false
                   :progress (assoc (or (:progress @!state) {}) :done true)
                   :fatal nil)))
        (catch Exception e
          (when (= gen @!gen)
            (if (:stale (ex-data e))
              nil
              (swap! !state assoc
                     :scanning? false
                     :fatal (or (.getMessage e) (.getName (class e)))))))))))

(defn- choose-directory!
  []
  (platform/pick-directory
   {:title "Choose a folder to scan"}
   (fn [{:keys [path error]}]
     (cond
       path (start-scan! path)
       error (swap! !state assoc :fatal (str "Folder picker: " error))))))

(defn- refresh!
  []
  (when-let [root (:root @!state)]
    (start-scan! root)))

(defn- submit-path!
  [raw]
  (let [raw (str/trim (str raw))
        expanded (cond
                   (str/blank? raw) nil
                   (= raw "~") (home)
                   (str/starts-with? raw "~/") (str (home) (subs raw 1))
                   :else raw)]
    (when expanded
      (let [f (java.io.File. expanded)]
        (if (.isDirectory f)
          (start-scan! f)
          (swap! !state assoc :fatal (str "Not a directory: " expanded)))))))

(defn- go-to!
  [path]
  (when path
    (swap! !state assoc :cwd path :selected nil)))

(defn- go-up!
  []
  (swap! !state (fn [s]
                  (assoc s
                         :cwd (nav/go-up (:root s) (:cwd s))
                         :selected nil))))

(defn- reveal-path
  []
  (or (:selected @!state) (:cwd @!state)))

(defn- reveal!
  []
  (when-let [path (reveal-path)]
    (platform/reveal-path! path)))

(defn- enter-id!
  [path]
  (when-let [node (nav/find-node (:tree @!state) path)]
    (when (= :dir (:kind node))
      (go-to! path))))

(defn- dir-selected?
  [{:keys [tree selected]}]
  (boolean
   (and selected
        (= :dir (:kind (nav/find-node tree selected))))))

(defn- listing-menu
  [state]
  (into []
        (concat
         (when (dir-selected? state)
           [{:id :open :label "Open folder" :icon :folder-open}])
         [{:id :show :label "Show in file manager" :icon :external-link}])))

(defn- on-listing-menu
  [id]
  (case id
    :open (enter-id! (:selected @!state))
    :show (reveal!)
    nil))

(defn- toolbar
  [{:keys [scanning?]}]
  (ui/hstack
   {:gap 8 :align :center}
   (ui/button "Open…" choose-directory!
              {:primary true :compact true :tooltip "Choose a folder to scan"})
   (ui/button "Refresh" refresh!
              {:compact true :tooltip "Scan this folder again"})
   (ui/button "Show" reveal!
              {:variant :ghost :compact true :tooltip "Reveal in the file manager"})
   (ui/spacer)
   (when scanning?
     (ui/hstack
      {:gap 6 :align :center}
      (ui/spinner {:size :small})
      (ui/label "Scanning…" {:font-size 13 :color "#cba6f7"})))))

(defn- path-crumbs
  [{:keys [root cwd]}]
  (if-not root
    (ui/label "No folder selected"
              {:font-size 18 :font-weight :semibold :flex 1})
    (ui/hstack
     {:gap 6 :align :center :flex 1}
     (when (nav/can-go-up? root cwd)
       (ui/button "Back" go-up!
                  {:variant :ghost :compact true :tooltip "Parent folder"}))
     (ui/breadcrumb (view/breadcrumb-items root cwd)
                    {:flex 1 :on-change go-to!})
     (ui/clipboard cwd {:tooltip "Copy this folder's path"}))))

(defn- header
  [{:keys [root cwd tree scanning? progress]}]
  (let [node (or (nav/find-node tree cwd) tree)
        size (or (:size node) (:bytes progress) 0)
        files (or (:file-count node) (:files progress) 0)
        dirs (or (:dir-count node) (:dirs progress) 0)
        skipped (or (:skipped node) (:skipped progress) 0)]
    (ui/vstack
     {:gap 4}
     (ui/hstack
      {:align :center :gap 8}
      (path-crumbs {:root root :cwd cwd})
      (ui/label (fmt/format-bytes size)
                {:font-size 18 :font-weight :semibold}))
     (ui/hstack
      {:gap 8 :align :center}
      (ui/label
       (str files " files · " dirs " dirs")
       {:font-size 12 :color "#6c6f85"})
      (when (pos? skipped)
        (ui/tag (str skipped " skipped") {:variant :warning :size :small})))
     (when (and scanning? (:path progress))
       (ui/label (nav/tilde-path (:path progress))
                 {:font-size 12 :color "#cba6f7"})))))

(defn- empty-listing
  [message]
  (ui/label message {:padding 16 :color "#6c6f85"}))

(defn- scanning-placeholder
  []
  (ui/vstack
   {:gap 8 :padding 16}
   (ui/hstack
    {:gap 8 :align :center}
    (ui/spinner {:size :small})
    (ui/label "Walking the filesystem…" {:color "#6c6f85"}))
   (ui/skeleton {:width 420 :height 14})
   (ui/skeleton {:width 360 :height 14})
   (ui/skeleton {:width 280 :height 14})))

(defn- listing
  [{:keys [tree cwd scanning? root selected] :as state}]
  (let [node (or (nav/find-node tree cwd) tree)
        kids (:children node)
        kid-ids (set (map :path kids))
        selected (when (contains? kid-ids selected) selected)
        slices (view/usage-slices kids)]
    (ui/vstack
     {:flex 1}
     (cond
       (and (nil? tree) (not scanning?))
       (empty-listing (if root
                        "Refresh to scan this folder."
                        "Open a folder to scan disk usage."))

       (and scanning? (nil? tree))
       (scanning-placeholder)

       (empty? kids)
       (empty-listing "Empty directory")

       :else
       (ui/vstack
        {:flex 1 :gap 8}
        (when (>= (count slices) 2)
          (ui/bar-chart slices {:height 128}))
        (ui/context-menu
         (listing-menu (assoc state :selected selected))
         {:flex 1 :on-change on-listing-menu}
         (ui/table {:columns view/listing-columns
                    :rows (view/listing-rows node)
                    :selected selected
                    :flex 1
                    :on-change #(swap! !state assoc :selected %)
                    :on-confirm enter-id!})))))))

(defn- path-field
  [{:keys [path-draft]}]
  (ui/text-field
   path-draft
   {:id "path"
    :placeholder "Folder path — Enter to scan"
    :height 36
    :on-change #(swap! !state assoc :path-draft %)
    :on-submit submit-path!}))

(defn app []
  (let [s @!state]
    (ui/window
     {:title (view/window-title (:root s) (:cwd s))
      :chrome :app
      :width 760
      :height 700
      :theme "Catppuccin Violet Dark"}
     (ui/vstack
      {:flex 1 :padding 14 :gap 10}
      (toolbar s)
      (path-field s)
      (header s)
      (when-let [msg (:fatal s)]
        (ui/alert msg {:variant :error
                       :title "Scan failed"
                       :on-close #(swap! !state assoc :fatal nil)}))
      (ui/divider)
      (listing s)))))

(defn- maybe-restore!
  []
  (when (nil? (:root @!state))
    (when-let [root (:root (persist/load-state))]
      (let [f (java.io.File. (str root))]
        (when (.isDirectory f)
          (swap! !state assoc :root root :cwd root :path-draft (nav/tilde-path root)))))))

(maybe-restore!)
