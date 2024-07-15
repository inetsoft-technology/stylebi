/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.storage;

import inetsoft.util.SingletonManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * {@code KeyValueStorage} is the client for a shared key-value store.
 *
 * @param <T> the value type.
 */
@SingletonManager.Singleton(KeyValueStorage.Reference.class)
public interface KeyValueStorage<T extends Serializable> extends AutoCloseable {
   /**
    * Creates a new instance of {@code KeyValueStorage}.
    *
    * @param id  the unique identifier of the key-value store.
    *
    * @param <T> the value type.
    *
    * @return a storage instance.
    */
   static <T extends Serializable> KeyValueStorage<T> newInstance(String id) {
      return newInstance(id, new LoadKeyValueTask<>(id));
   }

   /**
    * Creates a new instance of {@code KeyValueStorage}.
    *
    * @param id   the unique identifier of the key-value store.
    * @param load the specialized load task for the storage.
    *
    * @param <T> the value type.
    *
    * @return a storage instance.
    */
   static <T extends Serializable> KeyValueStorage<T> newInstance(String id,
                                                                  LoadKeyValueTask<T> load)
   {
      return new LocalKeyValueStorage<>(id, load);
   }

   /**
    * Determines if the store contains an entry with the specified key.
    *
    * @param key the key.
    *
    * @return {@code true} if the store contains the entry {@code false} if it does not.
    */
   boolean contains(String key);

   /**
    * Gets the value associated with the specified key.
    *
    * @param key the key.
    *
    * @return the value associated with the key or {@code null} if there is none.
    */
   T get(String key);

   /**
    * Puts a value into the store.
    *
    * @param key   the key.
    * @param value the value.
    *
    * @return the old value associated with the key or {@code null} if there was none.
    */
   Future<T> put(String key, T value);

   /**
    * Puts all values from the specified map in to the store.
    *
    * @param values the values to add.
    *
    * @return a future that can be used to determine the outcome of the operation.
    */
   Future<?> putAll(SortedMap<String, T> values);

   /**
    * Removes a value from the store.
    *
    * @param key the key.
    *
    * @return the value that was associated with the key or {@code null} if there was none.
    */
   Future<T> remove(String key);

   /**
    * Renames a key in the store atomically. This removes the value associated with <i>oldKey</i>
    * and then puts it back in the store using <i>newKey</i>.
    *
    * @param oldKey the old key.
    * @param newKey the new key.
    *
    * @return the value that was previously associated with the new key.
    */
   default Future<T> rename(String oldKey, String newKey) {
      return rename(oldKey, newKey, null);
   }

   /**
    * Renames a key in the store atomically. This removes the value associated with <i>oldKey</i>
    * and then puts it back in the store using <i>newKey</i>.
    *
    * @param oldKey the old key.
    * @param newKey the new key.
    * @param value  the value to use instead of the value associated with <i>newKey</i>. If
    *               {@code null}, the value associated with <i>newKey</i> will be used instead.
    *
    * @return the value that was previously associated with the new key.
    */
   Future<T> rename(String oldKey, String newKey, T value);

   /**
    * Replaces the contents of the key-value store with that of the supplied map.
    *
    * @param values the new values.
    *
    * @return a future that can be used to determine the outcome of the operation.
    */
   Future<?> replaceAll(SortedMap<String, T> values);

   /**
    * Delete this key value-store
    */
   Future<?> deleteStore();

   /**
    * Gets a stream of the entries in the store.
    *
    * @return the entry stream.
    */
   Stream<KeyValuePair<T>> stream();

   /**
    * Gets a stream of the keys in the store.
    *
    * @return the key stream.
    */
   Stream<String> keys();

   /**
    * Gets the number of entries in the store.
    *
    * @return the size.
    */
   int size();

   /**
    * Adds a listener that is notified when the store is changed.
    *
    * @param listener the listener to add.
    */
   void addListener(Listener<T> listener);

   /**
    * Removes a listener from the notification list.
    *
    * @param listener the listener to remove.
    */
   void removeListener(Listener<T> listener);

   /**
    * Checks if the storage has been closed.
    */
   boolean isClosed();

   /**
    * {@code Event} signals that an entry has changed in a key-value store.
    *
    * @param <T> the value type.
    */
   final class Event<T> extends EventObject {
      /**
       * Creates a new instance of {@code Event}.
       *
       * @param source   the source of the event.
       * @param key      the key for the modified entry.
       * @param oldValue the old value of the entry, if any.
       * @param newValue the new value of the entry, if any.
       */
      Event(Object source, String key, String mapName, T oldValue, T newValue) {
         super(source);
         Objects.requireNonNull(key, "The key cannot be null");
         this.key = key;
         this.mapName = mapName;
         this.oldValue = oldValue;
         this.newValue = newValue;
      }

      /**
       * Gets the key of the modified entry.
       *
       * @return the key.
       */
      public String getKey() {
         return key;
      }

      /**
       * Gets the map name of the modified store
       *
       * @return the key.
       */
      public String getMapName() {
         return mapName;
      }

      /**
       * Gets the old value of the entry.
       *
       * @return the old value or {@code null} if none.
       */
      public T getOldValue() {
         return oldValue;
      }

      /**
       * Gets the new value of the entry.
       *
       * @return the new value or {@code null} if none.
       */
      public T getNewValue() {
         return newValue;
      }

      @Override
      public String toString() {
         return "Event{" +
            "key='" + key + '\'' +
            ", mapName='" + mapName + '\'' +
            ", oldValue=" + oldValue +
            ", newValue=" + newValue +
            "} " + super.toString();
      }

      private final String key;
      private final String mapName;
      private final T oldValue;
      private final T newValue;
   }

   /**
    * {@code Listener} is an interface for classes that are notified when a key-value store is
    * modified.
    *
    * @param <T> the value type.
    */
   interface Listener<T> extends EventListener {
      /**
       * Called when a new entry is added to the key-value store.
       *
       * @param event the event object.
       */
      void entryAdded(Event<T> event);

      /**
       * Called when an existing entry is updated in the key-value store.
       *
       * @param event the event object.
       */
      void entryUpdated(Event<T> event);

      /**
       * Called when an existing entry is removed from the key-value store.
       *
       * @param event the event object.
       */
      void entryRemoved(Event<T> event);
   }

   class Reference extends SingletonManager.Reference<KeyValueStorage<?>> {
      @Override
      public  KeyValueStorage<?> get(Object... parameters) {
         if(parameters.length < 1 || parameters.length > 2) {
            return null;
         }

         if(storages == null) {
            storages = new HashMap<>();
         }

         String storeID = (String) parameters[0];
         KeyValueStorage<?> storage = storages.get(storeID);

         if(storage == null || storage.isClosed()) {
            Supplier<LoadKeyValueTask<?>> createTask = parameters.length == 2 ?
               (Supplier<LoadKeyValueTask<?>>) parameters[1] : null;

            if(createTask == null) {
               storage = KeyValueStorage.newInstance(storeID);
            }
            else {
               storage = KeyValueStorage
                  .newInstance(storeID, createTask.get());
            }

            storages.put(storeID, storage);
         }

         return storage;
      }

      @Override
      public void dispose() {
         if(storages != null) {
            for(String storeID : storages.keySet()) {
               try {
                  KeyValueStorage<?> storage = storages.get(storeID);

                  if(!storage.isClosed()) {
                     storage.close();
                  }
               }
               catch(Exception e) {
                  LOG.error("Failed to close storage with storeID " + storeID, e);
               }
            }

            storages = null;
         }
      }

      private HashMap<String, KeyValueStorage<?>> storages;
      private static final Logger LOG = LoggerFactory.getLogger(Reference.class);
   }
}