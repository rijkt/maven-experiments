(ns maven-experiments.core
  (:require [clojure.java.shell :as sh]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn])
  (:gen-class))

(defn- threads-done? [thread-count thread-max]
  (> thread-count  thread-max)) ; > since we want to finish the run for this number of threads

(defn- experiments-done? [experiment-count experiment-max]
  (>= experiment-count experiment-max))

(defn- time->miliseconds
  "Takes a time output (eg. 0m49.355s) and converts it to miliseconds"
  [s]
  (let [[*m* *s*] (str/split s  #"m")
        m (read-string *m*)
        s (read-string (first (str/split *s* #"s")))]
  (int (+ (* m 60000) (* s 1000)))))

(defn- enrich [output thread-count]
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
                    (dissoc :out)
                    (assoc :threads thread-count)
                    (update :real time->miliseconds)
                    (update :user time->miliseconds)
                    (update :sys time->miliseconds))]
    trimmed))

(defn- run-experiment! [path threads]
  (sh/sh "/bin/sh" "-c"
         (str "cd " path " && time mvn -T " threads " clean install > /dev/null")))

(defn- load-experiments! [path]
  (map edn/read-string (str/split (slurp path) #"\n")))

(defn -main
  "Run a timing experiment for maven"
  [project-path output-file thread-max experiment-max]
  (loop [thread-count 1 ; todo: replace with map on range pairs
         experiment-count 0]
    (cond
      (and (threads-done? thread-count thread-max) (experiments-done? experiment-count experiment-max))
      :done ; break out
      
      (experiments-done? experiment-count experiment-max)
      (recur (inc thread-count) 0) ; run experiments for the next number of threads

      :else
      (let [run-result (run-experiment! project-path thread-count)
            record (enrich run-result thread-count)]
        (spit output-file (str record "\n") :append true)
        (recur thread-count (inc experiment-count)))))
  (println "Done"))
