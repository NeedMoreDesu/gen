(ns gen.linker-storage)

(declare to-string)

(deftype GenLinkerStorage [processes]
 Object
 (toString [self]
  (to-string self)))

(defn storage-create []
 (GenLinkerStorage. (ref {})))
(defn storage? [arg]
 (= (type arg) GenLinkerStorage))
(defn storage-get-processes [storage]
 @(.processes storage))
(defn storage-get-process-by-thread [storage thread]
 (get (storage-get-processes storage) thread))
(defn storage-add [storage thread process]
 (dosync (alter (.processes storage) assoc thread process)))
(defn storage-remove [storage thread]
 (dosync (alter (.processes storage) dissoc thread)))

(defn to-string [storage]
 (str
  "count: " (count (storage-get-processes storage))))

(defonce ^:dynamic *linker* (storage-create))
