(ns orcpub.entity
  (:require [clojure.spec :as spec]
            [orcpub.modifiers :as modifiers]
            [orcpub.dnd.e5.modifiers :as dnd5-mods]
            [orcpub.dnd.e5.character :as dnd5-char]
            [orcpub.template :as t]))

(spec/def ::key keyword?)
(spec/def ::option (spec/keys :req [::key]
                              :opt [::options]))
(spec/def ::option-vec (spec/+ ::option))
(spec/def ::options (spec/map-of keyword? (spec/or :single ::option
                                                   :multiple ::option-vec)))
(spec/def ::raw-entity (spec/keys :opt [::options]))

(spec/def ::flat-option (spec/keys :req [::t/path]
                                   :opt [::value]))
(spec/def ::flat-options (spec/+ ::flat-option))

(declare build-options-paths)

(defn build-option-paths [path option]
  (let [new-path (conj path (::key option))
        child-options (::options option)
        option-value (::value option)
        result (cond-> {::t/path new-path}
                 option-value (assoc ::value option-value))]
    (if (seq child-options)
      (conj (build-options-paths new-path child-options)
            result)
      result)))

(defn build-options-entry-value-paths [path value]
  (if (sequential? value)
    (map (partial build-option-paths path) value)
    [(build-option-paths path value)]))

(defn build-options-entry-paths [path [option-key value]]
  (let [new-path (conj path option-key)]
    (build-options-entry-value-paths new-path value)))

(defn build-options-paths [path options]
  {:pre [(spec/valid? ::options options)]}
  (map (partial build-options-entry-paths path) options))

(defn flatten-options [options]
  {:pre [(spec/valid? ::options options)]
   :post [(spec/valid? ::flat-options %)]}
  (flatten (build-options-paths [] options)))

(defn collect-modifiers [flat-options modifier-map]
  {:pre [(spec/valid? ::flat-options flat-options)
         (spec/valid? ::t/modifier-map modifier-map)]
   :post [(spec/valid? ::modifiers/modifiers %)]}
  (mapcat
   (fn [{path ::t/path
         option-value ::value
         :as option}]
     (let [modifiers (::t/modifiers (get-in modifier-map path))]
       (if option-value
         (map
          #(assoc % ::modifiers/value option-value)
          modifiers)
         modifiers)))
   flat-options))

(defn build [raw-entity modifier-map]
  {:pre [(spec/valid? ::raw-entity raw-entity)
         (spec/valid? ::t/modifier-map modifier-map)]}
  (let [options (flatten-options (::options raw-entity))
        modifiers (collect-modifiers options modifier-map)]
    (reduce
     (fn [current-entity modifier]
       (update-in current-entity
                  (let [path (::modifiers/path modifier)]
                    (if (keyword? path)
                      [path]
                      path))
                  (partial modifiers/modify modifier)))
     raw-entity
     modifiers)))

(defn name-to-kw [name]
  (-> name
      clojure.string/lower-case
      (clojure.string/replace #"\W" "-")))

(defn selection [name options]
  {::t/name name
   ::t/key (name-to-kw name)
   ::t/options options})

(defn option [name & [selections modifiers]]
  (cond-> {::t/name name
           ::t/key (name-to-kw name)}
    selections (assoc ::t/selections selections)
    modifiers (assoc ::t/modifiers modifiers)))