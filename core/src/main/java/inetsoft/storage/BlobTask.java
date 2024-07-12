/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.storage;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedLong;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * {@code BlobTask} is a specialization of {@link KeyValueTask} that performs operations on the
 * key-value store that backs a blob store.
 *
 * @param <T>
 */
public abstract class BlobTask<T extends Serializable> extends KeyValueTask<Blob<T>> {
   /**
    * Creates a new instance of {@code BlobTask}.
    *
    * @param id the unique identifier of the blob store.
    */
   public BlobTask(String id) {
      super(id);
   }

   /**
    * Gets the map that contains the references to the file digests.
    *
    * @return the reference map.
    */
   protected Map<String, Set<String>> getReferenceMap() {
      Cluster cluster = Cluster.getInstance();
      return cluster.getReplicatedMap("inetsoft.storage.kv." + getId() + "Refs");
   }

   /**
    * Gets the distributed long that contains the cached last modified time of the blob store.
    *
    * @return the last modified timestamp.
    */
   protected DistributedLong getLastModified() {
      Cluster cluster = Cluster.getInstance();
      return cluster.getLong("inetsoft.storage.blob.ts." + getId());
   }
}
