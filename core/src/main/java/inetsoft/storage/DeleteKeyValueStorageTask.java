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
import java.util.Map;

/**
 * {@code LoadKeyValueTask} is a cluster singleton task that loads the content of a key-value store
 * into a distributed map.
 *
 * @param <T> the value type.
 */
public class DeleteKeyValueStorageTask<T extends Serializable>
   extends KeyValueTask<T> implements SingletonRunnableTask
{
   /**
    * Creates a new instance of {@code DeleteKeyValueTask}.
    *
    * @param id       the unique identifier of the key-value store.
    */
   public DeleteKeyValueStorageTask(String id) {
      super(id);
   }

   @Override
   public void run() {
      try {
         Map<String, T> map = getMap();

         if(!map.isEmpty()) {
            boolean blob = map.values().stream().anyMatch(v -> v instanceof Blob);

            if(blob) {
               removeBlobReferences();
            }
         }

         Cluster cluster = Cluster.getInstance();
         cluster.destroyReplicatedMap("inetsoft.storage.kv." + getId());

         getEngine().deleteStorage(getId());
      }
      catch(Exception e) {
         LoggerFactory.getLogger(getClass())
            .error("Failed to load key-value storage '{}'", getId(), e);
      }
   }

   private void removeBlobReferences() {
      Cluster cluster = Cluster.getInstance();
      cluster.destroyReplicatedMap("inetsoft.storage.kv." + getId() + "Refs");
   }
}
