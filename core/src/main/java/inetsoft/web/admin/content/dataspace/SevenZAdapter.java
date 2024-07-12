/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.content.dataspace;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;

public class SevenZAdapter implements ArchiveAdapter{
   SevenZAdapter(File file) throws IOException {
      SeekableByteChannel channel = FileChannel.open(file.toPath());
      archive = new SevenZFile(channel);
   }

   SevenZAdapter(InputStream inputStream) throws IOException {
      SeekableInMemoryByteChannel channel =
         new SeekableInMemoryByteChannel(IOUtils.toByteArray(inputStream));
      archive = new SevenZFile(channel);
   }

   @Override
   public ArchiveEntry getNextEntry() throws IOException {
      return archive.getNextEntry();
   }

   @Override
   public InputStream getInputStream(ArchiveEntry entry) throws IOException {
      return archive.getInputStream((SevenZArchiveEntry) entry);
   }

   @Override
   public void close() throws IOException {
      archive.close();
   }

   private final SevenZFile archive;
}
