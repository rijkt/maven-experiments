(ns maven-experiments.core
  (:require [clojure.java.shell :as shell]
            [clojure.set]
            [clojure.string :as str])
  (:gen-class))

(defn -main
  "Run a timing experiment for maven"
  [path]
  (defn enrich [output]
    (let [x (-> output
                (:err)
                (str/split #"\n"))
          y (->>  x ; could have used as-> to change parameter position. Maybe there is a better way with the transducer?
                  (drop 1) 
                  (vec)
                  (map #(str/split % #"\t"))
                  (into output))] 
      y)) ; todo: make all keys keywords
  (-> (shell/sh "/bin/sh" "-c" (str "cd " path " && time mvn clean install > /dev/null")) ; todo: pass -T argument
      (enrich)
      (clojure.set/rename-keys {:err :res "real" :real "user" :user "sys" :sys}))) ; for some reason time writes to err
