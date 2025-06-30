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

import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;

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

   @Override
   public void list(String id, PrintWriter writer) throws IOException {
      Path storagePath = base.resolve(id);

      try (Stream<Path> paths = Files.walk(storagePath)) {
         paths.filter(Files::isRegularFile)
            .map(base::relativize)
            .forEach(writer::println);
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
