(ns community.components.index
  (:require [community.state :as state]
            [community.util :refer-macros [<?]]
            [community.api :as api]
            [community.partials :refer [link-to]]
            [community.routes :refer [routes]]
            [om.core :as om]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn subforum-group [{:keys [name subforums id]}]
  (html
   [:div {:key id}
    [:h2 name]
    (if (not (empty? subforums))
      [:ul.block-grid-4
       (for [{:keys [id slug ui-color] :as subforum} subforums]
         [:li.block-grid-item {:key id :className (if (:unread subforum) "unread")}
          [:div {:style {:height "150px"
                         :width "150px"
                         :background-color ui-color}}
           [:a (link-to (routes :subforum {:id id :slug slug})
                        (:name subforum))]]])]) ]))

(defcomponent index [{:as app :keys [current-user subforum-groups]}
                     owner]
  (display-name [_] "Index")

  (did-mount [this]
    (go
      (try
        (om/update! app :subforum-groups (<? (api/subforum-groups)))
        (state/remove-errors! :ajax)

        (catch ExceptionInfo e
          (let [e-data (ex-data e)]
            (state/add-error! (:error-info e-data)))))))

  (render [this]
    (html
      (if (not (empty? subforum-groups))
        [:div.row (map subforum-group subforum-groups)]
        [:div]))))
