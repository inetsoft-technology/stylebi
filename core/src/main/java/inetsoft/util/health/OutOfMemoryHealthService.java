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
package inetsoft.util.health;

import inetsoft.sree.SreeEnv;
import inetsoft.util.FileSystemService;
import inetsoft.util.SingletonManager;

import java.io.File;
import java.time.Instant;

public class OutOfMemoryHealthService {
   public OutOfMemoryHealthService() {
      String path = SreeEnv.getProperty("health.outOfMemory.file", "./oom");
      oomFile = FileSystemService.getInstance().getFile(path);
   }

   public static OutOfMemoryHealthService getInstance() {
      return SingletonManager.getInstance(OutOfMemoryHealthService.class);
   }

   public OutOfMemoryStatus getStatus() {
      if(oomFile.exists()) {
         return new OutOfMemoryStatus(
            true, Instant.ofEpochMilli(oomFile.lastModified()).toString());
      }

      return new OutOfMemoryStatus();
   }

   private final File oomFile;

}
