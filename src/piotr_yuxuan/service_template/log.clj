(ns piotr-yuxuan.service-template.log
  (:require
   [com.brunobonacci.mulog :as u]
   [piotr-yuxuan.closeable-map :as closeable-map :refer [closeable-map*
                                                         with-tag]]))

(def default-publishers
  [{:type :console-json
    :pretty? true}])

(defn start
  [{::keys [publishers] :as config}]
  (u/log ::start)
  (closeable-map*
   (assoc config ::stop (with-tag ::closeable-map/fn
                          (u/start-publisher! {:type :multi :publishers publishers})))))
