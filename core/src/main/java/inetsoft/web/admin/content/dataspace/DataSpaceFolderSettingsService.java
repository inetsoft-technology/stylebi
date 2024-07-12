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

import inetsoft.util.DataSpace;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import java.io.*;

@Service
public class DataSpaceFolderSettingsService {
   public void writeArchiveEntry(ArchiveEntry entry, InputStream input, String rootFolder)
      throws IOException
   {
      DataSpace space = DataSpace.getDataSpace();
      String name = entry.getName();
      String fullPath = rootFolder != null && !rootFolder.isEmpty() && !"/".equals(rootFolder) ?
         rootFolder + "/" + name : name;

      if(entry.isDirectory()) {
         space.makeDirectories(rootFolder + "/" + name);
      }
      else {
         int index = fullPath.lastIndexOf('/');
         String dir = index < 0 ? null : fullPath.substring(0, index);
         String file = index < 0 ? fullPath : fullPath.substring(index + 1);

         if(dir != null) {
            space.makeDirectories(dir);
         }

         space.withOutputStream(dir, file, out -> IOUtils.copy(input, out));
      }
   }

   public static String getArchiveFormat(File file) {
      try(InputStream input = new FileInputStream(file)) {
         return getArchiveFormat(ArchiveAdapter.gunzip(input));
      }
      catch(Exception ignore) {
         return null;
      }
   }

   public static String getArchiveFormat(InputStream input) {
      try {
         return ArchiveStreamFactory.detect(input);
      }
      catch(Exception ignore) {
         return null;
      }
   }
}
