;;; Copyright © 2013 NeedMoreDesu desu@horishniy.org.ua
;;
;;; This program is free software. It comes without any warranty, to
;;; the extent permitted by applicable law. You can redistribute it
;;; and/or modify it under the terms of the Do What The Fuck You Want
;;; To Public License, Version 2, as published by Sam Hocevar. See
;;; http://www.wtfpl.net/ for more details.

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
