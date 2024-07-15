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
package inetsoft.web.admin.content.dataspace;

import org.apache.commons.compress.archivers.*;

import java.io.*;

public class DefaultArchiveAdapter implements ArchiveAdapter {
   DefaultArchiveAdapter(File file, String format)
      throws IOException, ArchiveException
   {
      archive = new ArchiveStreamFactory().createArchiveInputStream(format,
            ArchiveAdapter.gunzip(new FileInputStream(file)));
   }

   DefaultArchiveAdapter(InputStream inputStream, String format)
      throws IOException, ArchiveException
   {
      archive = new ArchiveStreamFactory().createArchiveInputStream(format,
         ArchiveAdapter.gunzip(inputStream));
   }

   @Override
   public ArchiveEntry getNextEntry() throws IOException {
      ArchiveEntry next = archive.getNextEntry();

      while(next != null && !archive.canReadEntryData(next)) {
         next = archive.getNextEntry();
      }

      return next;
   }

   @Override
   public InputStream getInputStream(ArchiveEntry entry) throws IOException {
      return archive;
   }

   @Override
   public void close() throws IOException {
      archive.close();
   }

   private final ArchiveInputStream archive;
}
