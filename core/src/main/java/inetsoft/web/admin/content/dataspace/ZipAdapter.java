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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.util.Enumeration;

public class ZipAdapter implements ArchiveAdapter {
   ZipAdapter(File file) throws IOException {
      SeekableByteChannel channel = FileChannel.open(file.toPath());
      archive = new ZipFile(channel);
      entries = archive.getEntries();
   }

   ZipAdapter(InputStream inputStream) throws IOException {
      SeekableInMemoryByteChannel channel =
         new SeekableInMemoryByteChannel(IOUtils.toByteArray(inputStream));
      archive = new ZipFile(channel);
      entries = archive.getEntries();
   }

   @Override
   public ArchiveEntry getNextEntry() throws IOException {
      ZipArchiveEntry next = entries.hasMoreElements() ? entries.nextElement() : null;

      while (next != null && !archive.canReadEntryData(next)) {
         next = entries.hasMoreElements() ? entries.nextElement() : null;
      }

      return next;
   }

   @Override
   public InputStream getInputStream(ArchiveEntry entry) throws IOException {
      return archive.getInputStream((ZipArchiveEntry) entry);
   }

   @Override
   public void close() throws IOException {
      archive.close();
   }

   private final ZipFile archive;
   private final Enumeration<ZipArchiveEntry> entries;
}
