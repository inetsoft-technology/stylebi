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
import java.nio.file.*;
import java.util.Arrays;
import java.util.Iterator;

public class CacheDirectoryStream implements DirectoryStream<Path> {
   public CacheDirectoryStream(CachePath directory, CacheMetadata metadata,
                               Filter<? super Path> filter)
   {
      this.directory = directory;
      this.metadata = metadata;
      this.filter = filter;
   }

   @Override
   public Iterator<Path> iterator() {
      return Arrays.stream(metadata.getChildren())
         .map(directory::resolve)
         .filter(this::matches)
         .iterator();
   }

   private boolean matches(Path path) {
      try {
         return filter == null || filter.accept(path);
      }
      catch(IOException e) {
         throw new DirectoryIteratorException(e);
      }
   }

   @Override
   public void close() throws IOException {
      // no-op
   }

   private final CachePath directory;
   private final CacheMetadata metadata;
   private final DirectoryStream.Filter<? super Path> filter;
}
