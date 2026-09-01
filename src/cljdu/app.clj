(ns cljdu.app
  "cljdu — a small native disk-usage browser."
  (:require [cljdu.format :as fmt]
            [cljdu.nav :as nav]
            [cljdu.persist :as persist]
            [cljdu.scan :as scan]
            [cljdu.theme]
            [clojure.string :as str]
            [gpui.platform :as platform]
            [gpui.ratom :as r]
            [gpui.ui :as ui]))

(defonce !gen (atom 0))

(defonce !state
  (r/atom {:root nil
           :cwd nil
           :tree nil
           :scanning? false
           :progress nil
           :fatal nil
           :path-draft ""}))

(defn- home
  []
  (System/getProperty "user.home"))

(defn- tilde
  [path]
  (let [h (home)]
    (cond
      (nil? path) ""
      (= path h) "~"
      (str/starts-with? path (str h "/")) (str "~" (subs path (count h)))
      :else path)))

(defn- apply-root
  [state root]
  (assoc state
         :root root
         :cwd root
         :tree nil
         :scanning? true
         :progress {:path root :bytes 0 :files 0 :dirs 0 :skipped 0}
         :fatal nil
         :path-draft (tilde root)))

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
    (swap! !state assoc :cwd path)))

(defn- go-up!
  []
  (swap! !state (fn [s]
                  (assoc s :cwd (nav/go-up (:root s) (:cwd s))))))

(defn- reveal-cwd!
  []
  (when-let [cwd (:cwd @!state)]
    (platform/reveal-path! cwd)))

(defn- kind-label
  [kind]
  (case kind
    :dir "dir"
    :link "link"
    "file"))

(defn- row
  [parent-size idx {:keys [path name kind size error]}]
  (let [dir? (= :dir kind)
        muted "#a6adc8"
        enter! #(when dir? (go-to! path))]
    (ui/hstack
     {:gap 10
      :padding 6
      :align :center
      :on-click enter!
      :bg (when (odd? idx) "#181825")}
     (ui/label (kind-label kind)
               {:width 42
                :font-size 12
                :color muted})
     (ui/label (str name (when error (str "  (" error ")")))
               {:flex 1
                :font-weight (when dir? :medium)
                :on-click enter!})
     (ui/label (fmt/format-bytes size)
               {:width 88
                :font-size 13})
     (ui/label (fmt/percent size parent-size)
               {:width 44
                :font-size 13
                :color muted}))))

(defn- toolbar
  [{:keys [scanning?]}]
  (ui/hstack
   {:gap 8 :align :center}
   (ui/button "Open…" choose-directory! {:primary true :compact true})
   (ui/button "Refresh" refresh! {:compact true})
   (ui/button "Show" reveal-cwd! {:variant :ghost :compact true})
   (ui/spacer)
   (when scanning?
     (ui/label "Scanning…" {:font-size 13 :color "#cba6f7"}))))

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
      (ui/label (tilde (or cwd root "No folder selected"))
                {:font-size 18 :font-weight :semibold :flex 1})
      (ui/label (fmt/format-bytes size)
                {:font-size 18 :font-weight :semibold}))
     (ui/label
      (str files " files · " dirs " dirs"
           (when (pos? skipped)
             (str " · " skipped " unreadable path"
                  (when (not= 1 skipped) "s")
                  " skipped")))
      {:font-size 12 :color "#a6adc8"})
     (when (and scanning? (:path progress))
       (ui/label (tilde (:path progress))
                 {:font-size 12 :color "#cba6f7"})))))

(defn- crumb-bar
  [{:keys [root cwd]}]
  (when root
    (ui/hstack
     {:gap 4 :align :center}
     (ui/button "Back" go-up! {:variant :ghost :compact true})
     (map (fn [{:keys [name path]}]
            (ui/button name #(go-to! path) {:variant :text :compact true}))
          (nav/breadcrumbs root cwd)))))

(defn- listing
  [{:keys [tree cwd scanning? root]}]
  (let [node (or (nav/find-node tree cwd) tree)
        kids (:children node)]
    (cond
      (and (nil? tree) (not scanning?))
      (ui/label (if root
                  "Refresh to scan this folder."
                  "Open a folder to scan disk usage.")
                {:padding 16 :color "#a6adc8"})

      (and scanning? (nil? tree))
      (ui/label "Walking the filesystem…"
                {:padding 16 :color "#a6adc8"})

      (empty? kids)
      (ui/label "Empty directory"
                {:padding 16 :color "#a6adc8"})

      :else
      (ui/scroll
       {:flex 1}
       (map-indexed #(row (:size node) %1 %2) kids)))))

(defn- path-field
  [{:keys [path-draft]}]
  (ui/text-field
   path-draft
   {:id "path"
    :placeholder "Folder path — Enter to scan"
    :flex 1
    :on-change #(swap! !state assoc :path-draft %)
    :on-submit submit-path!}))

(defn app []
  (let [s @!state]
    (ui/window
     {:title "cljdu"
      :chrome :app
      :width 720
      :height 640
      :theme "Catppuccin Violet"}
     (ui/vstack
      {:flex 1 :padding 14 :gap 10}
      (toolbar s)
      (path-field s)
      (header s)
      (when (:fatal s)
        (ui/label (:fatal s) {:color "#f38ba8" :font-size 13}))
      (crumb-bar s)
      (listing s)))))

(defn- maybe-restore!
  []
  (when (nil? (:root @!state))
    (when-let [root (:root (persist/load-state))]
      (let [f (java.io.File. (str root))]
        (when (.isDirectory f)
          (swap! !state assoc :root root :cwd root :path-draft (tilde root)))))))

(maybe-restore!)
