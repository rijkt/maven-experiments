(ns maven-experiments.core
  (:require [clojure.java.shell :as shell]
            [clojure.set]
            [clojure.string :as str])
  (:gen-class))

(defn -main
  "Run a timing experiment for maven"
  [path]
  (defn enrich [output]
    (let [time-result (-> output
                          (:err) ; for some reason time writes to err
                          (str/split #"\n"))
          enriched (->>  time-result ; could have used as-> to change parameter position. Maybe there is a better way with the transducer?
                         (drop 1) ; there is a newline at the beginning of the output
                         (vec)
                         (map #(str/split % #"\t"))
                         (into output))
          trimmed (-> enriched
                      (clojure.set/rename-keys {:err :res "real" :real "user" :user "sys" :sys})
                      (dissoc :out))] 
      trimmed))
  (-> (shell/sh "/bin/sh" "-c" (str "cd " path " && time mvn clean install > /dev/null")) ; todo: pass -T argument
      (enrich)))
