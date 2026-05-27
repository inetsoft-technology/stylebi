/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.util.cachefs;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

class CacheFileAttributes implements BasicFileAttributes {
   public CacheFileAttributes(String path, boolean directory, long creationTime,
                              Instant lastModifiedTime, long size)
   {
      this.path = path;
      this.directory = directory;
      this.creationTime = FileTime.fromMillis(creationTime);
      this.lastModifiedTime = FileTime.from(lastModifiedTime);
      this.size = size;
   }

   @Override
   public FileTime lastModifiedTime() {
      return lastModifiedTime;
   }

   @Override
   public FileTime lastAccessTime() {
      return lastModifiedTime;
   }

   @Override
   public FileTime creationTime() {
      return creationTime;
   }

   @Override
   public boolean isRegularFile() {
      return !directory;
   }

   @Override
   public boolean isDirectory() {
      return directory;
   }

   @Override
   public boolean isSymbolicLink() {
      return false;
   }

   @Override
   public boolean isOther() {
      return false;
   }

   @Override
   public long size() {
      return size;
   }

   @Override
   public Object fileKey() {
      return path;
   }

   @Override
   public String toString() {
      return "CacheFileAttributes{" +
         "lastModifiedTime=" + lastModifiedTime +
         ", creationTime=" + creationTime +
         ", directory=" + directory +
         ", size=" + size +
         ", path='" + path + '\'' +
         '}';
   }

   private final FileTime lastModifiedTime;
   private final FileTime creationTime;
   private final boolean directory;
   private final long size;
   private final String path;
}
