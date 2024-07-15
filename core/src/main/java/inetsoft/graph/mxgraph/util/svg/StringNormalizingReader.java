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
package inetsoft.graph.mxgraph.util.svg;

import java.io.IOException;

/**
 * This class represents a NormalizingReader which handles Strings.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 */
public class StringNormalizingReader extends NormalizingReader {

   /**
    * The characters.
    */
   protected String string;

   /**
    * The length of the string.
    */
   protected int length;

   /**
    * The index of the next character.
    */
   protected int next;

   /**
    * The current line in the stream.
    */
   protected int line = 1;

   /**
    * The current column in the stream.
    */
   protected int column;

   /**
    * Creates a new StringNormalizingReader.
    *
    * @param s The string to read.
    */
   public StringNormalizingReader(String s)
   {
      string = s;
      length = s.length();
   }

   /**
    * Read a single character.  This method will block until a
    * character is available, an I/O error occurs, or the end of the
    * stream is reached.
    */
   public int read() throws IOException
   {
      int result = (length == next) ? -1 : string.charAt(next++);
      if(result <= 13) {
         switch(result) {
         case 13:
            column = 0;
            line++;
            int c = (length == next) ? -1 : string.charAt(next);
            if(c == 10) {
               next++;
            }
            return 10;

         case 10:
            column = 0;
            line++;
         }
      }
      return result;
   }

   /**
    * Returns the current line in the stream.
    */
   public int getLine()
   {
      return line;
   }

   /**
    * Returns the current column in the stream.
    */
   public int getColumn()
   {
      return column;
   }

   /**
    * Close the stream.
    */
   public void close() throws IOException
   {
      string = null;
   }
}
