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

import inetsoft.util.ConfigurationContext;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * {@code BlobEngine} is an interface for classes that handle loading and saving blobs to some
 * persistent storage.
 */
public interface BlobEngine extends AutoCloseable {
   /**
    * Determines if the blob with the specified digest exists in storage.
    *
    * @param id     the unique identifier of the storage.
    * @param digest the MD5 digest of the blob as a hexadecimal string.
    *
    * @return {@code true} if the blob exists or {@code false} if it doesn't.
    */
   boolean exists(String id, String digest);

   /**
    * Reads a blob from storage.
    *
    * @param id     the unique identifier of the storage.
    * @param digest the MD5 digest of the blob as a hexadecimal string.
    * @param target the path to the local file where the blob will be written.
    *
    * @throws IOException if an I/O error occurs.
    */
   void read(String id, String digest, Path target) throws IOException;

   /**
    * Writes a blob into storage.
    *
    * @param id     the unique identifier of the storage.
    * @param digest the MD5 digest of the blob as a hexadecimal string.
    * @param source the path to the local file from where the blob will be read.
    *
    * @throws IOException if an I/O error occurs.
    */
   void write(String id, String digest, Path source) throws IOException;

   /**
    * Deletes a blob from storage.
    *
    * @param id     the unique identifier of the storage.
    * @param digest the MD5 digest of the blob as a hexadecimal string.
    *
    * @throws IOException if an I/O error occurs.
    */
   void delete(String id, String digest) throws IOException;

   /**
    * Deletes multiple blobs from storage.
    *
    * @param id      the unique identifier of the storage.
    * @param digests the MD5 digests of the blobs as hexadecimal strings.
    *
    * @throws IOException if an I/O error occurs.
    */
   default void deleteAll(String id, Set<String> digests) throws IOException {
      for(String digest : digests) {
         delete(id, digest);
      }
   }

   @Override
   default void close() throws Exception {
   }

   static BlobEngine getInstance() {
      return ConfigurationContext.getContext().getSpringBean(BlobEngine.class);
   }

}
