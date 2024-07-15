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

import java.io.*;
import java.util.Properties;

public interface PhysicalLibraryEntry {
   /**
    * Gets the path to this entry.
    *
    * @return the path.
    */
   String getPath();

   /**
    * Determines if this entry represents a directory.
    *
    * @return <tt>true</tt> if a directory.
    */
   boolean isDirectory();

   /**
    * Gets the comment for this entry.
    */
   String getComment();

   /**
    * Gets the comment properties for this entry.
    */
   Properties getCommentProperties();

   /**
    * Gets the input stream for this entry.
    *
    * @return the input stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   InputStream getInputStream() throws IOException;

   /**
    * Gets the output stream for this entry.
    *
    * @return the output stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   OutputStream getOutputStream() throws IOException;

   /**
    * Get last modified time.
    * @return time in ms or -1 if the time is not known.
    */
   default long getLastModified() {
      return -1;
   }

   /**
    * Remove file for this entry.
    */
   default void remove() {
      // no-op
   }
}
