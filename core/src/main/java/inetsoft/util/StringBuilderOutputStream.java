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
package inetsoft.util;

import inetsoft.util.swap.XSwapUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * <tt>OutputStream</tt> that places all output into a String buffer for later
 * use.
 *
 * @author InetSoft Technology
 */
public class StringBuilderOutputStream extends OutputStream {
   /**
    * Creates a new instance of <tt>StringBuilderOutputStream</tt>.
    */
   public StringBuilderOutputStream() {
   }

   @Override
   public void write(int b) throws IOException {
      buffer.clear();
      buffer.put((byte) b);
      XSwapUtil.flip(buffer);
      output.append(charset.decode(buffer));
   }

   @Override
   public void write(byte[] b) throws IOException {
      for(int i = 0; i < b.length; i += 1024) {
         write(b, i, Math.min(1024, b.length - i));
      }
   }

   @Override
   public void write(byte[] b, int off, int len) throws IOException {
      buffer.clear();
      buffer.put(b, off, len);
      XSwapUtil.flip(buffer);
      output.append(charset.decode(buffer));
   }

   @Override
   public String toString() {
      return output.toString();
   }

   private final StringBuilder output = new StringBuilder();
   private final ByteBuffer buffer = ByteBuffer.allocate(1024);
   private final Charset charset = Charset.defaultCharset();
}
