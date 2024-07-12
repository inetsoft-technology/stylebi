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

import org.apache.commons.compress.archivers.*;

import java.io.*;
import java.util.zip.GZIPInputStream;

public interface ArchiveAdapter extends Closeable {
   ArchiveEntry getNextEntry() throws IOException;

   InputStream getInputStream(ArchiveEntry entry) throws IOException;

   static ArchiveAdapter newInstance(File file, String format)
      throws IOException, ArchiveException
   {
      if(ArchiveStreamFactory.ZIP.equalsIgnoreCase(format)) {
         return new ZipAdapter(file);
      }
      else if(ArchiveStreamFactory.SEVEN_Z.equalsIgnoreCase(format)) {
         return new SevenZAdapter(file);
      }
      else {
         return new DefaultArchiveAdapter(file, format);
      }
   }

   static ArchiveAdapter newInstance(InputStream inputStream, String format)
      throws IOException, ArchiveException
   {
      if(ArchiveStreamFactory.ZIP.equalsIgnoreCase(format)) {
         return new ZipAdapter(inputStream);
      }
      else if(ArchiveStreamFactory.SEVEN_Z.equalsIgnoreCase(format)) {
         return new SevenZAdapter(inputStream);
      }
      else {
         return new DefaultArchiveAdapter(inputStream, format);
      }
   }

   static InputStream gunzip(InputStream input) throws IOException {
      InputStream result = input;

      if(!result.markSupported()) {
         result = new BufferedInputStream(result);
      }

      result.mark(2);
      int sig1 = result.read();

      if(sig1 < 0) {
         result.reset();
         return result;
      }

      int sig2 = result.read();

      if(sig2 < 0) {
         result.reset();
         return result;
      }

      result.reset();
      return sig1 == 0x1f && sig2 == 0x8b ? new GZIPInputStream(result) : result;
   }
}
