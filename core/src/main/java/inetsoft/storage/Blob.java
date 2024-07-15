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

import com.fasterxml.jackson.annotation.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * {@code Blob} contains the metadata for an entry in a blob store.
 *
 * @param <T> the type of the extended metadata.
 */
public final class Blob<T extends Serializable> implements Serializable {
   /**
    * Creates a new instance of {@code Blob}.
    *
    * @param path     the path to the blob.
    * @param digest   the MD5 digest of the blob as a hexadecimal string.
    * @param length   the length of the blob in bytes.
    * @param metadata the extended metadata for the blob.
    */
   @JsonCreator
   public Blob(@JsonProperty("path") String path, @JsonProperty("digest") String digest,
               @JsonProperty("length") long length,
               @JsonProperty("lastModified") Instant lastModified,
               @JsonProperty("metadata") T metadata)
   {
      Objects.requireNonNull(path, "The path must not be null");
      Objects.requireNonNull(lastModified, "The last modified time cannot be null");
      this.path = path;
      this.digest = digest;
      this.length = length;
      this.lastModified = lastModified;
      this.metadata = metadata;
   }

   /**
    * Gets the path to the blob.
    *
    * @return the path.
    */
   public String getPath() {
      return path;
   }

   /**
    * Gets the MD5 digest of the blob as a hexadecimal string. This may be {@code null} if this is a
    * metadata-only entry, like a folder.
    *
    * @return the digest.
    */
   public String getDigest() {
      return digest;
   }

   /**
    * Gets the length of the blob data in bytes.
    *
    * @return the length.
    */
   public long getLength() {
      return length;
   }

   /**
    * Gets the date and time at which the blob was last modified.
    *
    * @return the last modified time.
    */
   @JsonFormat(shape = JsonFormat.Shape.STRING)
   public Instant getLastModified() {
      return lastModified;
   }

   /**
    * Gets the extended metadata for the blob.
    *
    * @return the metadata, may be {@code null}.
    */
   @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
   public T getMetadata() {
      return metadata;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      Blob<?> blob = (Blob<?>) o;
      return length == blob.length &&
         Objects.equals(path, blob.path) &&
         Objects.equals(digest, blob.digest) &&
         Objects.equals(lastModified, blob.lastModified) &&
         Objects.equals(metadata, blob.metadata);
   }

   @Override
   public int hashCode() {
      return Objects.hash(path, digest, length, lastModified, metadata);
   }

   @Override
   public String toString() {
      return "Blob{" +
         "path='" + path + '\'' +
         ", digest='" + digest + '\'' +
         ", length=" + length +
         ", lastModified=" + lastModified +
         ", metadata=" + metadata +
         '}';
   }

   private final String path;
   private final String digest;
   private final long length;
   private final Instant lastModified;
   private final T metadata;
}
