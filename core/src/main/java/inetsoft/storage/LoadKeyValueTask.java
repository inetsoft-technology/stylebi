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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.internal.cluster.SingletonRunnableTask;
import inetsoft.util.ShutdownException;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * {@code LoadKeyValueTask} is a cluster singleton task that loads the content of a key-value store
 * into a distributed map.
 *
 * @param <T> the value type.
 */
public class LoadKeyValueTask<T extends Serializable>
   extends KeyValueTask<T> implements SingletonRunnableTask
{
   /**
    * Creates a new instance of {@code LoadKeyValueTask}.
    *
    * @param id the unique identifier of the key-value store.
    */
   public LoadKeyValueTask(String id) {
      this(id, false);
   }

   /**
    * Creates a new instance of {@code LoadKeyValueTask}.
    *
    * @param id       the unique identifier of the key-value store.
    * @param external a flag indicating if this load was triggered by an external change.
    */
   public LoadKeyValueTask(String id, boolean external) {
      super(id);
      this.external = external;
   }

   @Override
   public void run() {
      try {
         DistributedMap<String, T> map = getMap();

         if(map.isEmpty() || external || map.size() != getEngine().size(getId())) {
            TreeMap<String, T> temp = new TreeMap<>();
            getEngine().<T>stream(getId())
               .forEach(p -> temp.put(p.getKey(), p.getValue()));

            if(temp.isEmpty()) {
               Class<T> valueClass = initialize(temp);

               if(valueClass != null) {
                  getEngine().putAll(getId(), temp);
               }
            }

            validate(temp);

            boolean blob = temp.values().stream().anyMatch(v -> v instanceof Blob);

            if(blob) {
               loadBlobReferences(temp);
            }

            if(external) {
               // an external change replaced the stored content, so the listeners on every node
               // are told that every entry was added again (e.g. Plugins loads a plugin whose
               // version changed only from an added event)
               map.clear();
               map.putAll(temp);
            }
            else {
               replaceContents(map, temp);
            }
         }
      }
      catch(ShutdownException e) {
         LoggerFactory.getLogger(LoadKeyValueTask.class)
            .debug("Tried to load KV store during shutdown", e);
      }
      catch(RuntimeException e) {
         throw e;
      }
      catch(Exception e) {
         LoggerFactory.getLogger(LoadKeyValueTask.class)
            .error("Failed to load key-value storage '{}'", getId(), e);
      }
   }

   /**
    * Perform custom initialization when the key-value storage is empty.
    *
    * @param map the map into which to put the default values.
    */
   protected Class<T> initialize(Map<String, T> map) {
      return null;
   }

   /**
    * Validate the initial contents prior to putting them into the distributed map.
    *
    * @param map the contents to validate.
    *
    * @throws Exception if the contents are invalid.
    */
   protected void validate(Map<String, T> map) throws Exception {
      // no-op
   }

   /**
    * Checks if a value already in the distributed map is the same as the stored value, so that the
    * reload does not write it again.
    *
    * @param current the value in the distributed map.
    * @param stored  the value loaded from the key-value store.
    *
    * @return {@code true} if the values are the same.
    */
   protected boolean isSameValue(T current, T stored) {
      return Objects.equals(current, stored);
   }

   /**
    * Makes the distributed map hold the loaded content without ever removing a key that is
    * still stored. Readers on every node read the map directly while this runs, so clearing the
    * map first made every stored key look absent to them until the content was put back, and a
    * reader that creates a missing entry, such as the data source registry root, then replaced
    * the stored entry with an empty one (Bug #78532).
    */
   private void replaceContents(DistributedMap<String, T> map, SortedMap<String, T> temp) {
      Map<String, T> current = new HashMap<>();

      for(Map.Entry<String, T> e : map.entrySet()) {
         current.put(e.getKey(), e.getValue());
      }

      SortedMap<String, T> changed = new TreeMap<>();

      for(Map.Entry<String, T> e : temp.entrySet()) {
         T value = current.get(e.getKey());

         if(value == null || !isSameValue(value, e.getValue())) {
            changed.put(e.getKey(), e.getValue());
         }
      }

      if(!changed.isEmpty()) {
         map.putAll(changed);
      }

      Set<String> removed = new HashSet<>(current.keySet());
      removed.removeAll(temp.keySet());

      if(!removed.isEmpty()) {
         map.removeAll(removed);
      }
   }

   private void loadBlobReferences(Map<String, ?> map) {
      Map<String, Set<String>> temp = new TreeMap<>();

      for(Map.Entry<String, ?> e : map.entrySet()) {
         if(e.getValue() instanceof Blob) {
            String digest = ((Blob<?>) e.getValue()).getDigest();

            if(digest != null) {
               temp.computeIfAbsent(digest, k -> new HashSet<>()).add(e.getKey());
            }
         }
      }

      Cluster cluster = getCluster();
      Map<String, Set<String>> refMap =
         cluster.getReplicatedMap("inetsoft.storage.kv." + getId() + "Refs");
      refMap.clear();
      refMap.putAll(temp);
   }

   private final boolean external;
}
