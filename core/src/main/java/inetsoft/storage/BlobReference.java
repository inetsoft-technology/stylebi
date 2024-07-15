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

import java.io.Serializable;

/**
 * {@code BlobReference} holds a reference to a blob and the count of the blobs that reference the
 * same file.
 */
public class BlobReference<T extends Serializable> implements Serializable {
   /**
    * Creates a new instance of {@code BlobReference}.
    *
    * @param blob  the blob.
    * @param count the number of blobs that reference the same file.
    */
   public BlobReference(Blob<T> blob, int count) {
      this.blob = blob;
      this.count = count;
   }

   /**
    * Gets the blob.
    *
    * @return the blob.
    */
   public Blob<T> getBlob() {
      return blob;
   }

   /**
    * Gets the number of blobs that reference the file after the operation has been performed.
    *
    * @return the reference count.
    */
   public int getCount() {
      return count;
   }

   private final Blob<T> blob;
   private final int count;
}
