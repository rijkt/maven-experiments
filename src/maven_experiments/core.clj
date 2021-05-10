(ns maven-experiments.core
  (:require [clojure.java.shell :as sh]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn])
  (:gen-class))

(defn -main
  "Run a timing experiment for maven"
  [path]
  (defn enrich [output thread-count]
    (let [time-result (-> output
                          :err ; for some reason time writes to err
                          (str/split #"\n"))
          enriched (->>  time-result ; could have used as-> to change parameter position. Maybe there is a better way with the transducer?
                         (drop 1) ; there is a newline at the beginning of the time command output
                         vec
                         (map #(str/split % #"\t"))
                         (into output))
          trimmed (-> enriched
                      (set/rename-keys {:err :res "real" :real "user" :user "sys" :sys})
                      (dissoc :out))] 
      (assoc trimmed :threads thread-count)))
  (defn run-experiment [path threads]
    (sh/sh "/bin/sh" "-c" (str "cd " path " && time mvn -T " threads " clean install > /dev/null")))
  (let [threads 100
        experiments 10]
    (loop [experiment-count 0
           thread-count 1]
      (cond
        (and (= thread-count (inc threads)) (= experiment-count experiments)) :done ; break out
        (= thread-count (inc threads)) (recur experiment-count 0)
        (= experiment-count experiments) (recur 0 thread-count)
        :else (let [run-result (run-experiment path thread-count)
                    record (enrich run-result thread-count)]
                (spit "/tmp/runs" (str record "\n") :append true) ; todo: output file and thread/experiment count arguments
                (recur (inc experiment-count) thread-count)))))
  (println "Done"))
