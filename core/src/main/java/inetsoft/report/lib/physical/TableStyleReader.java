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
package inetsoft.report.lib.physical;

import inetsoft.report.style.XTableStyle;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

public class TableStyleReader implements LibraryAssetReader<XTableStyle> {
   @Override
   public XTableStyle read(String name, InputStream input) throws IOException {
      try {
         if(input == null) {
            return null;
         }

         return XTableStyle.getXTableStyle(null, input);
      }
      finally {
         IOUtils.closeQuietly(input);
      }
   }

   @Override
   public XTableStyle read(String name, PhysicalLibraryEntry entry) throws IOException {
      return read(name, entry.getInputStream());
   }
}
