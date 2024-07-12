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
package inetsoft.mv.comm;

import java.nio.ByteBuffer;

/**
 * Utility methods for the communication service.
 *
 * @author InetSoft Technology
 * @since  10.3
 */
public class CommUtil {
   /**
    * Create a byte buffer for reading block file.
    */
   public static ByteBuffer createBlockBuffer() {
      int bsize = 32 * 1024;

      try {
         XConnectionConfig config = CommService.getConfig();
         bsize = config.getBlockSize();
      }
      catch(Exception ex) {
         // ignore it
      }

      return ByteBuffer.allocate(bsize);
   }
}
