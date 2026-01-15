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
package inetsoft.storage.fs;

import inetsoft.storage.BlobEngine;
import inetsoft.util.Tool;
import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

/**
 * {@code FilesystemBlobEngine} is an implementation of {@link BlobEngine} that stores blobs to a
 * directory on the filesystem. This is intended to be used with a network filesystem.
 */
public class FilesystemBlobEngine implements BlobEngine {
   /**
    * Creates a new instance of {@code FilesystemBlobEngine}.
    *
    * @param base the base directory where blobs will be stored.
    */
   public FilesystemBlobEngine(Path base) {
      this.base = base;
      this.temp = base.resolve("temp");

      try {
         Files.createDirectories(temp);
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to create storage directories", e);
      }
   }

   @Override
   public boolean exists(String id, String digest) {
      return getPath(id, digest).toFile().exists();
   }

   @Override
   public void read(String id, String digest, Path target) throws IOException {
      Path path = getPath(id, digest);
      Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
   }

   @Override
   public void write(String id, String digest, Path source) throws IOException {
      if(exists(id, digest)) {
         return;
      }

      Path tempFile = Files.createTempFile(temp, "blob", ".dat");

      try {
         Files.copy(source, tempFile, StandardCopyOption.REPLACE_EXISTING);
         Path path = getPath(id, digest);
         Files.createDirectories(path.getParent());
         Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE);
      }
      finally {
         Tool.deleteFile(tempFile.toFile());
      }
   }

   @Override
   public void delete(String id, String digest) throws IOException {
      Files.delete(getPath(id, digest));
   }

   public void deleteStore(String id) {
      try {
         if(id == null || id.isBlank()) {
            return;
         }

         Path root = base.resolve(id).normalize();
         Path baseNorm = base.normalize();

         if(!root.startsWith(baseNorm)) {
            throw new IOException("Can not delete outside blob base: " + root);
         }

         if(!Files.exists(root)) {
            return;
         }

        FileUtils.deleteDirectory(root.toFile());
      }
      catch(Exception e) {
         LoggerFactory.getLogger(FilesystemBlobEngine.class)
            .error("Failed to delete BlobEngine Storage folders: ", e);
      }
   }

   private Path getPath(String id, String digest) {
      String dir = digest.substring(0, 2);
      String name = digest.substring(2);
      return base.resolve(id).resolve(dir).resolve(name);
   }

   private final Path base;
   private final Path temp;
}
