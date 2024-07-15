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
package inetsoft.util.xml;

import java.io.*;

/**
 * CounterOutputStream add counter to record the position of an output stream.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class CounterOutputStream extends OutputStream {
   /**
    * Constructor.
    */
   public CounterOutputStream(OutputStream out, FileOutputStream fo) {
      if(!(out instanceof BufferedOutputStream)) {
         out = new BufferedOutputStream(out);
      }

      this.out = out;
      this.fo = fo;
   }

   @Override
   public void write(int b) throws IOException {
      out.write(b);

      if(fo != null) {
         fo.write(b);
      }

      pos++;
   }
   
   @Override
   public void write(byte[] b, int off, int len) throws IOException {
      out.write(b, off, len);

      if(fo != null) {
         fo.write(b, off, len);
      }

      pos += len;
   }

   @Override
   public void write(byte[] b) throws IOException {
      write(b, 0, b.length);
   }
   
   @Override
   public void flush() throws IOException {
      super.flush();
      out.flush();

      if(fo != null) {
         fo.flush();
      }
   }

   /**
    * Get the count of bytes.
    */
   public long getPosition() {
      return pos;
   }

   private long pos = 0;
   private OutputStream out;
   private FileOutputStream fo;
}
