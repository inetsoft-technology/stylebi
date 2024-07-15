/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.lib.physical;

import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;

public interface PhysicalLibrary {
   /**
    * Creates a new entry in the library.
    *
    * @param path    the path to the entry.
    * @param properties the entry properties.
    *
    * @return a new entry.
    *
    * @throws IOException if an I/O error occurs.
    */
   PhysicalLibraryEntry createEntry(String path, Properties properties) throws IOException;

   /**
    * Gets the entries in the library.
    *
    * @return the entries.
    *
    * @throws IOException if an I/O error occurs.
    */
   Iterator<PhysicalLibraryEntry> getEntries() throws IOException;

   /**
    * Closes the library.
    *
    * @throws IOException if an I/O error occurs.
    */
   void close() throws IOException;
}
