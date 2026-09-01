(ns cljdu.format
  "Human-readable sizes and percentages."
  (:import [java.util Locale]))

(defn- fmt1
  [n unit]
  (str (String/format Locale/US "%.1f" (to-array [(double n)])) " " unit))

(defn format-bytes
  "Format a byte count with 1024-based units (B, KB, MB, GB, TB)."
  [n]
  (let [n (long (or n 0))
        abs (Math/abs n)]
    (cond
      (< abs 1024) (str n " B")
      (< abs 1048576) (fmt1 (/ n 1024.0) "KB")
      (< abs 1073741824) (fmt1 (/ n 1048576.0) "MB")
      (< abs 1099511627776) (fmt1 (/ n 1073741824.0) "GB")
      :else (fmt1 (/ n 1099511627776.0) "TB"))))

(defn percent
  "Integer percent of `part` relative to `total`, or a placeholder."
  [part total]
  (let [part (long (or part 0))
        total (long (or total 0))]
    (cond
      (<= total 0) "—"
      (zero? part) "0%"
      :else (let [p (* 100.0 (/ (double part) (double total)))]
              (cond
                (< p 0.5) "<1%"
                (>= p 99.5) (if (< part total) "99%" "100%")
                :else (str (int (Math/round p)) "%"))))))
