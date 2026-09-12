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

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;

public final class CacheFS {
   private CacheFS() {
   }

   public static Path getPath(String storeId, String first, String... more) {
      StringBuilder uri = new StringBuilder()
         .append("cachefs://")
         .append(storeId);

      if(!first.startsWith("/")) {
         uri.append("/");
      }

      if(first.length() > 1 && first.endsWith("/")) {
         uri.append(first, 0, first.length() - 1);
      }
      else {
         uri.append(first);
      }

      for(String m : more) {
         if(m.startsWith("/")) {
            uri.append(m);
         }
         else {
            uri.append('/').append(m);
         }
      }

      return Paths.get(URI.create(uri.toString()));
   }

   public static long size(Path path) {
      try {
         if(Files.exists(path)) {
            return Files.size(path);
         }
      }
      catch(IOException ignore) {
      }

      return 0L;
   }

   public static long lastModified(Path path) {
      try {
         if(Files.exists(path)) {
            return Files.getLastModifiedTime(path).toMillis();
         }
      }
      catch(IOException ignore) {
      }

      return 0L;
   }
}
