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
package inetsoft.mv.comm;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * XWriteBuffer, the write channel of a connection.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public interface XWriteBuffer {
   /**
    * Check if has remaining data to write.
    */
   public boolean hasRemaining();

   /**
    * Get the remaining bytes to write.
    */
   public int getRemaining();

   /**
    * Write a byte buffer.
    */
   public void write(ByteBuffer buf) throws IOException;

   /**
    * Write a byte.
    */
   public void writeByte(byte val) throws IOException;

   /**
    * Write a short.
    */
   public void writeShort(short val) throws IOException;

   /**
    * Write an int.
    */
   public void writeInt(int val) throws IOException;

   /**
    * Write a long.
    */
   public void writeLong(long val) throws IOException;

   /**
    * Write a float.
    */
   public void writeFloat(float val) throws IOException;

   /**
    * Write a double.
    */
   public void writeDouble(double val) throws IOException;

   /**
    * Write a string. If the string is longer than SHORT.MAX_VALUE,
    * BufferOverflowException will be thrown.
    */
   public void writeString(String val) throws IOException;

   /**
    * Fetch one byte buffer.
    */
   public ByteBuffer fetch();

   /**
    * Flush the write buffer.
    * @param closing true for closing connection.
    * @return true if there is data to write.
    */
   public boolean flush(boolean closing);
}
