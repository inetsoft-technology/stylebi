/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.sree.internal.cluster.*;

import java.io.Serializable;
import java.util.*;

/**
 * {@code DeleteAllBlobTask} deletes multiple blobs in a single cluster operation.
 * Returns a set of digests that have zero references and should be deleted from storage.
 *
 * @param <T> the metadata type.
 */
public class DeleteAllBlobTask<T extends Serializable> extends BlobTask<T>
   implements SingletonCallableTask<TreeSet<String>>
{
   /**
    * Creates a new instance of {@code DeleteAllBlobTask}.
    *
    * @param id   the unique identifier of the blob store.
    * @param keys the paths to the blobs to delete.
    */
   public DeleteAllBlobTask(String id, Set<String> keys) {
      super(id);
      this.keys = keys;
   }

   @Override
   public TreeSet<String> call() throws Exception {
      TreeSet<String> digestsToDelete = new TreeSet<>();
      Map<String, Set<String>> refMap = getReferenceMap();
      DistributedMap<String, Blob<T>> map = getMap();

      // First, collect all blobs to get their digests before removal
      Map<String, Blob<T>> blobsToRemove = new HashMap<>();

      for(String key : keys) {
         Blob<T> blob = map.get(key);

         if(blob != null) {
            blobsToRemove.put(key, blob);
         }
      }

      // Bulk remove from engine and map - use TreeSet to avoid distributed deadlock
      TreeSet<String> sortedKeys = new TreeSet<>(keys);
      getEngine().removeAll(getId(), sortedKeys);
      map.removeAll(sortedKeys);

      // Process reference counting for each removed blob
      for(Map.Entry<String, Blob<T>> entry : blobsToRemove.entrySet()) {
         String key = entry.getKey();
         Blob<T> blob = entry.getValue();

         if(blob.getDigest() != null) {
            String digest = blob.getDigest();

            if(refMap.containsKey(digest)) {
               Set<String> refs = refMap.get(digest);
               boolean exists = refs.remove(key);

               if(refs.isEmpty()) {
                  refMap.remove(digest);

                  if(exists) {
                     digestsToDelete.add(digest);
                  }
               }
               else {
                  refMap.put(digest, refs);
               }
            }
            else {
               digestsToDelete.add(digest);
            }
         }
      }

      // Bulk delete from blob engine
      if(!digestsToDelete.isEmpty()) {
         BlobEngine.getInstance().deleteAll(getId(), digestsToDelete);
      }

      getLastModified().set(System.currentTimeMillis());

      // Send a single cache clear message for all deleted digests
      if(!digestsToDelete.isEmpty()) {
         Cluster.getInstance().sendMessage(new ClearAllBlobCacheMessage(getId(), digestsToDelete));
      }

      return digestsToDelete;
   }

   private final Set<String> keys;
}
