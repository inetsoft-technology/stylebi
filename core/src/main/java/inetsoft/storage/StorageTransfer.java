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
package inetsoft.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * {@code StorageTransfer} handles importing the contents from and importing contents into key-value
 * and blob stores.
 */
public interface StorageTransfer {
   /**
    * Exports the contents of the key-value and blob stores.
    *
    * @param output the output stream to which the exported contents will be written.
    *
    * @throws IOException if an I/O error occurs.
    */
   void exportContents(OutputStream output) throws IOException;

   /**
    * Import the contents in a bundled zip file into the key-value and blob storage.
    *
    * @param file the zip file containing the contents to import.
    *
    * @throws IOException if an I/O error occurs.
    */
   void importContents(Path file) throws IOException;
}
