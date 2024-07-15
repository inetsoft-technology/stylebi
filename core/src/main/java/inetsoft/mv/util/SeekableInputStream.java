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
package inetsoft.mv.util;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface SeekableInputStream extends SeekableChannel {
   /**
    * Map the file region into a byte buffer in memory.
    */
   ByteBuffer map(long pos, long size) throws IOException;

   /**
    * Unmap the buffer created by map().
    */
   void unmap(ByteBuffer buf) throws IOException;

   /**
    * Open the input in a new stream.
    */
   SeekableInputStream reopen() throws IOException;

   long getModificationTime() throws IOException;

   /**
    * Get the file path on the file system.
    */
   Object getFilePath();

   default boolean readFully(ByteBuffer buf) throws IOException {
      while(buf.remaining() > 0) {
         int len = read(buf);

         if(len < 0) {
            break;
         }
      }

      return buf.remaining() == 0;
   }
}
