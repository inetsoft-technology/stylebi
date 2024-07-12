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
import inetsoft.sree.internal.cluster.SingletonCallableTask;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * {@code DeleteBlobTask} deletes a blob. If the returned reference count is zero, the file should
 * be deleted locally.
 *
 * @param <T> the metadata type.
 */
public class DeleteBlobTask<T extends Serializable> extends BlobTask<T>
   implements SingletonCallableTask<BlobReference<T>>
{
   /**
    * Creates a new instance of {@code DeleteBlobTask}.
    *
    * @param id    the unique identifier of the blob store.
    * @param key   the path to the file to delete.
    * @param local if the local engine is being used.
    */
   public DeleteBlobTask(String id, String key, boolean local) {
      super(id);
      this.key = key;
      this.local = local;
   }

   @Override
   public BlobReference<T> call() throws Exception {
      Blob<T> blob = getEngine().remove(getId(), key);
      Blob<T> blob2 = getMap().remove(key);
      blob = blob == null ? blob2 : blob;
      getLastModified().set(System.currentTimeMillis());

      if(blob != null) {
         Map<String, Set<String>> refMap = getReferenceMap();

         if(blob.getDigest() != null && refMap.containsKey(blob.getDigest())) {
            Set<String> refs = refMap.get(blob.getDigest());
            boolean exists = refs.remove(key);

            if(refs.isEmpty()) {
               refMap.remove(key);

               if(exists && !local) {
                  BlobEngine.getInstance().delete(getId(), blob.getDigest());
                  Cluster.getInstance()
                     .sendMessage(new ClearBlobCacheMessage(getId(), blob.getDigest()));
               }

               return new BlobReference<>(blob, 0);
            }
            else {
               refMap.put(blob.getDigest(), refs);
               return new BlobReference<>(blob, refs.size());
            }
         }

         return new BlobReference<>(blob, 0);
      }

      return new BlobReference<>(null, 0);
   }

   private final String key;
   private final boolean local;
}
