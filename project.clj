(defproject eureka-client "0.3.0-SNAPSHOT"
  :description "A client for Netflix Eureka service discovery servers"
  :url "http://github.com/codebrickie/eureka-client"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-http "3.4.1"
                  ;;:exclusions [com.fasterxml.jackson.core/jackson-core]
                  ]
                 [cheshire "5.7.0"]
                 [org.clojure/core.cache "0.6.5"]]
  :profiles {:dev {:dependencies [[midje "1.8.3"]]
                   :plugins [[lein-midje "3.2.1"]
                             [codox "0.10.3"]]}})
