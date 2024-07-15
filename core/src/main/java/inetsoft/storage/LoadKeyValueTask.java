/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.storage;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.SingletonRunnableTask;
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
    * Creates a new instance of {@code DeleteKeyValueTask}.
    *
    * @param id the unique identifier of the key-value store.
    */
   public LoadKeyValueTask(String id) {
      this(id, false);
   }

   /**
    * Creates a new instance of {@code DeleteKeyValueTask}.
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
         Map<String, T> map = getMap();

         if(map.isEmpty() || external) {
            Map<String, T> temp = new TreeMap<>();
            getEngine().<T>stream(getId())
               .forEach(p -> temp.put(p.getKey(), p.getValue()));

            if(temp.isEmpty()) {
               Class<T> valueClass = initialize(temp);

               if(valueClass != null) {
                  for(Map.Entry<String, T> e : temp.entrySet()) {
                     getEngine().put(getId(), e.getKey(), e.getValue());
                  }
               }
            }

            validate(temp);

            boolean blob = temp.values().stream().anyMatch(v -> v instanceof Blob);

            if(blob) {
               loadBlobReferences(temp);
            }

            map.clear();
            map.putAll(temp);
         }
      }
      catch(Exception e) {
         LoggerFactory.getLogger(getClass())
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

      Cluster cluster = Cluster.getInstance();
      Map<String, Set<String>> refMap =
         cluster.getReplicatedMap("inetsoft.storage.kv." + getId() + "Refs");
      refMap.clear();
      refMap.putAll(temp);
   }

   private final boolean external;
}
