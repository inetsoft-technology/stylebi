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

import inetsoft.util.Tool;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Objects;

/**
 * {@code LocalBlobStorage} is an implementation of {@link BlobStorage} that stores blobs directly
 * in the local file system.
 *
 * @param <T> the extended metadata type.
 */
public final class LocalBlobStorage<T extends Serializable> extends BlobStorage<T> {
   /**
    * Creates a new instance of {@code LocalBlobStorage}.
    *
    * @param id      the unique identifier of the blob storage.
    * @param base    the base directory of the blob storage.
    * @param storage the key-value store for the blob metadata.
    *
    * @throws IOException if the local storage directories could not be created.
    */
   public LocalBlobStorage(String id, Path base, KeyValueStorage<Blob<T>> storage)
      throws IOException
   {
      super(id, storage);
      Objects.requireNonNull(id, "The storage identifier cannot be null");
      Objects.requireNonNull(base, "The blob storage directory cannot be null");
      this.base = base.resolve(id);
      this.tempDir = this.base.resolve("temp");
      Files.createDirectories(this.tempDir);
   }

   @Override
   protected InputStream getInputStream(Blob<T> blob) throws IOException {
      Path path = getPath(blob, base);
      return Files.newInputStream(path);
   }

   @Override
   protected BlobChannel getReadChannel(Blob<T> blob) throws IOException {
      Path path = getPath(blob, base);
      return new BlobReadChannel(FileChannel.open(path, StandardOpenOption.READ));
   }

   @Override
   protected void commit(Blob<T> blob, Path tempFile) throws IOException {
      Path path = getPath(blob, base);

      if(path.toFile().exists()) {
         Tool.deleteFile(tempFile.toFile());
      }
      else {
         Files.createDirectories(path.getParent());
         Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE);
      }
   }

   @Override
   protected void delete(Blob<T> blob) throws IOException {
      Path path = getPath(blob, base);

      if(path.toFile().exists()) {
         Files.delete(path);
      }
   }

   @Override
   protected Path copyToTemp(Blob<T> blob) throws IOException {
      Path path = getPath(blob, base);
      Path tempFile = createTempFile("blob", ".dat");
      Files.copy(path, tempFile, StandardCopyOption.REPLACE_EXISTING);
      return tempFile;
   }

   @Override
   protected Path createTempFile(String prefix, String suffix) throws IOException {
      return Files.createTempFile(tempDir, prefix, suffix);
   }

   @Override
   protected boolean isLocal() {
      return false;
   }

   private final Path base;
   private final Path tempDir;
}
