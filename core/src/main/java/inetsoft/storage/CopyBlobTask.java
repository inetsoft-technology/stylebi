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

import inetsoft.sree.internal.cluster.SingletonCallableTask;

import java.io.FileNotFoundException;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * {@code CopyBlobTask} copies a blob to another path.
 *
 * @param <T> the metadata type.
 */
public class CopyBlobTask<T extends Serializable>
   extends BlobTask<T> implements SingletonCallableTask<BlobReference<T>>
{
   /**
    * Creates a new instance of {@code CopyBlobTask}.
    *
    * @param id      the unique identifier of the blob store.
    * @param oldPath the path to the source blob.
    * @param newPath the path to the new blob.
    */
   public CopyBlobTask(String id, String oldPath, String newPath) {
      super(id);
      this.oldPath = oldPath;
      this.newPath = newPath;
   }

   @Override
   public BlobReference<T> call() throws Exception {
      Blob<T> oldBlob = getEngine().get(getId(), oldPath);

      if(oldBlob == null) {
         throw new FileNotFoundException(oldPath);
      }

      Blob<T> newBlob = new Blob<>(newPath, oldBlob.getDigest(), oldBlob.getLength(), Instant.now(), oldBlob.getMetadata());
      getEngine().put(getId(), newPath, newBlob);
      getMap().put(newPath, newBlob);
      getLastModified().set(newBlob.getLastModified().toEpochMilli());

      int refCount = 0;

      if(newBlob.getDigest() != null) {
         Map<String, Set<String>> refMap = getReferenceMap();
         Set<String> refs = refMap.get(newBlob.getDigest());

         if(refs == null) {
            refs = new HashSet<>();
         }

         refs.add(newPath);
         refMap.put(newBlob.getDigest(), refs);
         refCount = refs.size();
      }

      return new BlobReference<>(newBlob, refCount);
   }

   private final String oldPath;
   private final String newPath;
}
