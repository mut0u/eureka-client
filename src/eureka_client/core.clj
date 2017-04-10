(ns eureka-client.core
  (:require [clj-http.client :as http]
            [clojure.set :refer [rename-keys]]
            [cheshire.core :refer [parse-string]]
            [clojure.core.cache :as cache]))

(def ^:private request-opts {:content-type :json
                             :socket-timeout 1000
                             :conn-timeout 1000
                             :accept :json})

(defn parse-json [s]
  (parse-string s (fn [k] (keyword k))))


(def ^:private instances-cache (atom (cache/ttl-cache-factory {} :ttl 30000)))

(defn- tick [ms f & args]
  (future
    (loop []
      (apply f args)
      (Thread/sleep ms)
      (recur))))

(defn- eureka-path [host port]
  (str "http://" host ":" port "/eureka/apps"))

(defn- app-path [host port app-id]
  (str (eureka-path host port) "/" app-id))

(defn- instance-path [host port app-id instance-id]
  (str (app-path host port app-id) "/" instance-id))

(defn- send-heartbeat [host port app-id host-id]
  (http/put (instance-path host port app-id host-id)
            request-opts))

(defn- fetch-instances-from-server
  [eureka-host eureka-port app-id]
  (->> (http/get (app-path eureka-host eureka-port  app-id) (assoc request-opts :as :json))
       :body
       :application
       :instance
       (map #(select-keys % [:ipAddr :port]))
       (map #(rename-keys % {:ipAddr :ip}))
       (map #(assoc % :port (read-string (:$ (:port %)))))))

(defn- fetch-all-instances-from-server
  [eureka-host eureka-port]
  (->> (http/get (app-path eureka-host eureka-port "") (assoc request-opts :as :json))
       :body
       :applications
       :application
       ))

(defn- find-instances-cached
  [eureka-host eureka-port app-id]
  (if (cache/has? @instances-cache app-id)
    (swap! instances-cache #(cache/hit % app-id))
    (swap! instances-cache
           #(cache/miss %
                        app-id
                        (fetch-instances-from-server eureka-host eureka-port app-id)))))

(defn fetch-hostname []
  (str (.getHostAddress (java.net.InetAddress/getLocalHost))))

(defn register
  "Registers the app-id at the Eureka server and sends a heartbeat every 30
   seconds."
  [eureka-host eureka-port app-id own-port]
  (let [host-id (fetch-hostname)  ;;(str (.getHostAddress (java.net.InetAddress/getLocalHost)) "-" (rand-int 10000))
        app-url (app-path eureka-host eureka-port app-id)]
    (http/post (app-path eureka-host eureka-port app-id)
               (assoc
                request-opts
                :form-params
                {:instance {:hostName host-id
                            :app app-id
                            :ipAddr (.getHostAddress (java.net.InetAddress/getLocalHost))
                            :vipAddress app-id
                            :secureVipAddress app-id
                            :status "UP"
                            :port {:$ own-port (keyword "@enabled")  true}
                            :securePort {:$ (str "8" own-port) (keyword "@enabled")  true}
                            :healthCheckUrl (str app-url "/healthcheck"),
                            :statusPageUrl (str app-url "/status"),
                            :homePageUrl (str app-url "/home")
                            :dataCenterInfo {(keyword "@class") "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo" :name "MyOwn"}}}
                ))
    (tick 30000 send-heartbeat eureka-host eureka-port app-id host-id)))

(defn remove-instance
  "remvoe the instance by instanceid"
  [eureka-host eureka-port app-id host-id]
  (let [v (str (app-path eureka-host eureka-port app-id) "/" host-id)]
    (->> (http/delete v)
         :status
         (= 200))))

(defn find-instances
  "Finds all instances for a given app id registered with the Eureka server.
  Results are cached for 30 seconds."
  [eureka-host eureka-port app-id]
  (get (find-instances-cached eureka-host eureka-port app-id) app-id))


(defn query-all-instances
  [eureka-host eureka-port]
  (->> (http/get (eureka-path eureka-host eureka-port) {:accept :json})
       :body
       parse-json
       :applications
       :application))
