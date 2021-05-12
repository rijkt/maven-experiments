(ns maven-experiments.core
  (:require [clojure.java.shell :as sh]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn])
  (:gen-class))

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

(defn- experiment-runner
  "Returns a fn that takes a single param - the number of threads to use"
  [project-path output-file]
  #(let [run-result (run-experiment! project-path %)
         record (enrich run-result %)]
     (spit output-file (str record "\n") :append true)))

(defn- load-experiments! [path]
  (map edn/read-string (str/split (slurp path) #"\n")))

(defn -main
  "Run a timing experiment for maven"
  [project-path output-file threads experiments]
  (let [iterations (mapcat #(repeat experiments %) (range 1 (inc threads)))
        experiment-runner  (experiment-runner project-path output-file)]
    (dorun ; force evaluation
     (map experiment-runner iterations)))
  (println "Done"))
