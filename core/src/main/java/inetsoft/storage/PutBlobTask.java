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
import inetsoft.sree.internal.cluster.SingletonCallableTask;

import java.io.Serializable;
import java.util.*;

/**
 * {@code PutBlobTask} puts a blob into the shared metadata map.
 *
 * @param <T> the metadata type.
 */
public class PutBlobTask<T extends Serializable>
   extends BlobTask<T> implements SingletonCallableTask<BlobReference<T>>
{
   /**
    * Creates a new instance of {@code PutBlobTask}.
    *
    * @param id    the unique identifier of the blob store.
    * @param blob  the blob being added.
    * @param local if the local engine is being used.
    */
   public PutBlobTask(String id, Blob<T> blob, boolean local) {
      super(id);
      this.data = serializeValue(blob);
      this.local = local;
   }

   @Override
   public BlobReference<T> call() throws Exception {
      Blob<T> blob = deserializeValue(data);
      Blob<T> oldBlob = getEngine().put(getId(), blob.getPath(), blob);
      getMap().put(blob.getPath(), blob);
      getLastModified().set(blob.getLastModified().toEpochMilli());

      Map<String, Set<String>> refMap = getReferenceMap();
      Set<String> refs = refMap.get(blob.getDigest());

      if(refs == null) {
         refs = new HashSet<>();
      }

      refs.add(blob.getPath());
      refMap.put(blob.getDigest(), refs);
      int count = 0;

      if(oldBlob != null) {
         refs = refMap.get(oldBlob.getDigest());

         if(refs != null) {
            if(oldBlob.getDigest().equals(blob.getDigest())) {
               count = 1;
            }
            else {
               boolean exists = refs.remove(oldBlob.getPath());

               if(refs.isEmpty()) {
                  if(exists && !local) {
                     BlobEngine.getInstance().delete(getId(), oldBlob.getDigest());
                     Cluster.getInstance()
                        .sendMessage(new ClearBlobCacheMessage(getId(), oldBlob.getDigest()));
                  }

                  refMap.remove(oldBlob.getDigest());
               }
               else {
                  refMap.put(oldBlob.getDigest(), refs);
                  count = refs.size();
               }
            }
         }
      }

      return new BlobReference<>(oldBlob, count);
   }

   private final byte[] data;
   private final boolean local;
}
