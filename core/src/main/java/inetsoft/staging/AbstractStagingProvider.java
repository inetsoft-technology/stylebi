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
package inetsoft.staging;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.examples.Expander;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;

public abstract class AbstractStagingProvider implements StagingProvider {
   protected void copyZip(InputStream input, Path stagingDirectory)
      throws IOException, ArchiveException
   {
      Path temp = Files.createTempFile("config", ".dat");

      try {
         input.mark(2);
         int sig1 = input.read();

         if(sig1 < 0) {
            throw new IOException("Invalid archive file at config URL");
         }

         int sig2 = input.read();

         if(sig2 < 0) {
            throw new IOException("Invalid archive file at config URL");
         }

         input.reset();
         InputStream in = sig1 == 0x1f && sig2 == 0x8b ? new GZIPInputStream(input) : input;
         Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
         new Expander().expand(temp.toFile(), stagingDirectory.toFile());
      }
      finally {
         temp.toFile().delete();
      }
   }
}
