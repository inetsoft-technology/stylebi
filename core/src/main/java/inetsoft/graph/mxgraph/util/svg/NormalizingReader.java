/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.graph.mxgraph.util.svg;

import java.io.IOException;
import java.io.Reader;

/**
 * This class represents a reader which normalizes the line break: \n,
 * \r, \r\n are replaced by \n.  The methods of this reader are not
 * synchronized.  The input is buffered.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 */
public abstract class NormalizingReader extends Reader {

   /**
    * Read characters into a portion of an array.
    *
    * @param cbuf Destination buffer
    * @param off  Offset at which to start writing characters
    * @param len  Maximum number of characters to read
    *
    * @return The number of characters read, or -1 if the end of the
    * stream has been reached
    */
   public int read(char[] cbuf, int off, int len) throws IOException
   {
      if(len == 0) {
         return 0;
      }

      int c = read();
      if(c == -1) {
         return -1;
      }
      int result = 0;
      do {
         cbuf[result + off] = (char) c;
         result++;
         c = read();
      }
      while(c != -1 && result < len);
      return result;
   }

   /**
    * Returns the current line in the stream.
    */
   public abstract int getLine();

   /**
    * Returns the current column in the stream.
    */
   public abstract int getColumn();

}
