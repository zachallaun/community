(ns community.components.subforum
  (:require [community.util :as util :refer-macros [<? p]]
            [community.api :as api]
            [community.models :as models]
            [community.location :refer [redirect-to]]
            [community.partials :refer [link-to]]
            [community.routes :refer [routes]]
            [community.components.shared :as shared]
            [om.core :as om]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defcomponent new-thread [subforum owner]
  (display-name [_] "NewThread")

  (init-state [this]
    {:c-draft (async/chan 1)
     :form-disabled? false
     :errors #{}})

  (will-mount [this]
    (let [{:keys [c-draft]} (om/get-state owner)]
      (go-loop []
        (when-let [draft (<! c-draft)]
          (try
            (let [{:keys [id autocomplete-users]} @subforum
                  new-thread (<? (api/new-thread id (models/with-mentions draft autocomplete-users)))]
              (redirect-to (routes :thread new-thread)))

            (catch ExceptionInfo e
              (om/set-state! owner :form-disabled? false)
              (let [e-data (ex-data e)]
                (om/update-state! owner :errors #(conj % (:message e-data))))))

          (recur)))))

  (will-unmount [this]
    (async/close! (:c-draft (om/get-state owner))))

  (render [this]
    (let [{:keys [form-disabled? c-draft errors]} (om/get-state owner)]
      (html
        [:div.panel {:class (if (empty? errors) "panel-default" "panel-danger")}
         [:div.panel-heading
          [:h4 "New thread"]
          (when (not (empty? errors))
            (map (fn [e] [:div e]) errors))]
         [:div.panel-body
          [:form {:onSubmit (fn [e]
                              (.preventDefault e)
                              (when-not form-disabled?
                                (async/put! c-draft (:new-thread @subforum))
                                (om/set-state! owner :form-disabled? true)))}
           [:div.form-group
            [:label {:for "thread-title"} "Title"]
            [:input#thread-title.form-control
             {:type "text"
              :name "thread[title]"
              :onChange (fn [e]
                          (om/update! subforum [:new-thread :title]
                                      (-> e .-target .-value)))}]]
           [:div.form-group
            [:label {:for "post-body"} "Body"]
            (shared/->autocompleting-textarea
             {:value (get-in subforum [:new-thread :body])
              :autocomplete-list (mapv :name (:autocomplete-users subforum))}
             {:opts {:on-change #(om/update! subforum [:new-thread :body] %)
                     :passthrough {:id "post-body"
                                   :class "form-control"}}})]
           [:button.btn.btn-default {:type "submit"
                                     :disabled form-disabled?}
            "Create thread"]]]]))))

(defn update-subforum! [app]
  (go
    (try
      (let [subforum (<? (api/subforum (-> @app :route-data :id)))]
        (om/update! app :subforum subforum)
        (om/transact! app :errors #(reduce disj % (vals (:ajax api/errors)))))

      (catch ExceptionInfo e
        (let [e-data (ex-data e)]
          (if (== 404 (:status e-data))
            (om/update! app [:route-data :route] :page-not-found)
            (om/transact! app :errors #(conj % (:message e-data)))))))))

(defcomponent subforum [{:keys [route-data subforum] :as app}
                        owner]
  (display-name [_] "Subforum")

  (did-mount [_]
    (update-subforum! app))

  (will-receive-props [_ next-props]
    (let [last-props (om/get-props owner)]
      (when (not= (:route-data next-props) (:route-data last-props))
        (update-subforum! app))))

  (render [this]
    (html
      (if subforum
        [:div
         [:h1 (:name subforum)]
         [:table.table.table-striped
          [:thead
           [:tr [:th "Topic"] [:th "Created by"] [:th "Last updated"]]]
          [:tbody
           (for [{:keys [id slug title created-by] :as thread} (:threads subforum)]
             [:tr {:key id :class (if (:unread thread) "unread")}
              [:td (link-to (routes :thread thread) title)]
              [:td created-by]
              [:td (util/human-format-time (:marked-unread-at thread))]])]]
         (->new-thread subforum)]
        [:div]))))
