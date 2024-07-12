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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * XReadBuffer, the blocked read channel of a connection.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public interface XReadBuffer {
   /**
    * Check if has remaining data to read.
    */
   public boolean hasRemaining();

   /**
    * Get the remaining bytes to read.
    */
   public int getRemaining();

   /**
    * Add one byte buffer.
    */
   public void add(ByteBuffer buf);

   /**
    * Read a byte buffer.
    */
   public ByteBuffer read(ByteBuffer buf) throws IOException;

   /**
    * Read a byte.
    */
   public byte readByte() throws IOException;

   /**
    * Read a short.
    */
   public short readShort() throws IOException;

   /**
    * Read an int.
    */
   public int readInt() throws IOException;

   /**
    * Read a long.
    */
   public long readLong() throws IOException;

   /**
    * Read a float.
    */
   public float readFloat() throws IOException;

   /**
    * Read a double.
    */
   public double readDouble() throws IOException;

   /**
    * Read a string.
    */
   public String readString() throws IOException;
}
