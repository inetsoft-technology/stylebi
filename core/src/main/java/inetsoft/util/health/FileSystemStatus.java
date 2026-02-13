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

package inetsoft.util.health;

import java.io.Serializable;
import java.util.Arrays;

public class FileSystemStatus implements Serializable {
   public FileSystemStatus(int fileSystemCount, FileSystemState[] fileSystems) {
      this.fileSystemCount = fileSystemCount;
      this.fileSystems = fileSystems;
   }

   public int getFileSystemCount() {
      return fileSystemCount;
   }

   public FileSystemState[] getFileSystems() {
      return fileSystems;
   }

   public boolean isFileSystemDown() {
      if(fileSystems == null || fileSystems.length == 0) {
         return false;
      }

      return Arrays.stream(fileSystems).anyMatch(p -> !p.isAvailable());
   }

   private final int fileSystemCount;
   private final FileSystemState[] fileSystems;
}
