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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.util.zip.*;

/**
 * This class contains methods for encoding or compressing data.
 *
 * @version 5.1, 9/20/2003
 * @author Inetsoft Technology
 */
public class Encoder implements java.io.Serializable {
   /**
    * Encode image into an array of bytes. This encoding order is from
    * top of the image to bottom, and from left to right at each scan
    * line. This is different from the PDF and PS image encoding order.
    */
   public static byte[] encodeImage(Image img) {
      PixelConsumer pc = new PixelConsumer(img);
      pc.produce();

      // temp space
      ByteArrayOutputStream buf = new ByteArrayOutputStream();

      for(int i = 0; i < pc.height; i++) {
         for(int j = 0; j < pc.width; j++) {
            int n = pc.pix[j][i];

            // save 4 alpha an rgb information in template..
            // order in R,G,B,A..
            // Do not in the order A,R,G,B,
            // if do so , will create the string ']]>' in the CDDATA body
            // when add a .jpg image without metadata
            // and parse the template failly.
            buf.write((byte) ((n & 0xFF0000) >> 16));
            buf.write((byte) ((n & 0xFF00) >> 8));
            buf.write((byte) (n & 0xFF));
            buf.write((byte) ((n & 0xFF000000) >> 24));
         }
      }

      return buf.toByteArray();
   }

   /**
    * Convert RGB to Grayscale.
    */
   public static int toGray(int r, int g, int b) {
      return (int) (0.299 * r + 0.587 * g + 0.114 * b);
   }

   /**
    * Decode an image byte stream to create an image instance.
    */
   public static Image decodeImage(int w, int h, byte[] buf) {
      return Tool.createImage(buf, w, h);
   }

   /**
    * Encode data using ASCII base 85 algorithm.
    */
   public static byte[] encodeAscii85(byte[] data) {
      return encodeAscii85(data, 0, data.length);
   }

   /**
    * Encode data using ASCII base 85 algorithm.
    */
   public static byte[] encodeAscii85(byte[] data, int offset, int length) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      Ascii85OutputStream output = new Ascii85OutputStream(buffer, false);

      try {
         output.write(data, offset, length);
         output.close();
      }
      catch(IOException exc) {
         LOG.error("Failed to encode data", exc);
      }

      return buffer.toByteArray();
   }

   /**
    * Decode data using ASCII base 85 algorithm.
    */
   public static byte[] decodeAscii85(byte[] data) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();

      for(int i = 0; i < data.length; i++) {
         byte[] cs = new byte[5];

         if(data[i] == 122) { // 'z'
            out.write((byte) 0);
            out.write((byte) 0);
            out.write((byte) 0);
            out.write((byte) 0);
            continue;
         }

         // read in 5 bytes
         int n = 0;

         for(; n < cs.length && i < data.length; n++, i++) {
            cs[n] = (byte) (data[i] - 33);
         }

         i--; // will be inc again at loop

         // construct value (4 bytes)
         long v = 0;

         for(int j = 0; j < cs.length; j++) {
            v = v * 85 + ((j < n) ? cs[j] : 85);
         }

         if(--n <= 0) {
            break;
         }

         out.write((byte) ((v >> 24) & 0xFF));

         if(--n <= 0) {
            break;
         }

         out.write((byte) ((v >> 16) & 0xFF));

         if(--n <= 0) {
            break;
         }

         out.write((byte) ((v >> 8) & 0xFF));

         if(--n <= 0) {
            break;
         }

         out.write((byte) (v & 0xFF));
      }

      return out.toByteArray();
   }

   /**
    * Decode data using ASCII base 85 algorithm.
    */
   public static byte[] decodeAscii85(String val) {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();

      for(int i = 0; i < val.length(); i++) {
         char c = val.charAt(i);

         if(!Character.isWhitespace(c)) {
            buf.write((byte) c);
         }
      }

      return decodeAscii85(buf.toByteArray());
   }

   /**
    * Write data using ASCII Hex encoding algorithm.
    */
   public static void writeAsciiHex(PrintWriter writer, byte[] data) {
      int length = data.length;
      char[] chars = new char[4096];
      int counter = 0;

      for(int i = 0; i < length; i++) {
         byte a = (byte) ((data[i] & 0xF0) >>> 4);
         byte b = (byte) (data[i] & 0xF);

         if(a < 10) {
            chars[counter++] = (char) (a + '0');
         }
         else {
            chars[counter++] = (char) (a - 10 + 'A');
         }

         if(b < 10) {
            chars[counter++] = (char) (b + '0');
         }
         else {
            chars[counter++] = (char) (b - 10 + 'A');
         }

         if(counter == 4096) {
            writer.write(chars);
            counter = 0;
         }
      }

      if(counter > 0) {
         writer.write(chars, 0, counter);
      }
   }

   public static char[] writeAsciiHex(byte[] data) {
      int length = data.length;
      char[] chars = new char[4096];
      int counter = 0;

      for(int i = 0; i < length; i++) {
         byte a = (byte) ((data[i] & 0xF0) >>> 4);
         byte b = (byte) (data[i] & 0xF);

         if(a < 10) {
            chars[counter++] = (char) (a + '0');
         }
         else {
            chars[counter++] = (char) (a - 10 + 'A');
         }

         if(b < 10) {
            chars[counter++] = (char) (b + '0');
         }
         else {
            chars[counter++] = (char) (b - 10 + 'A');
         }
      }

      return chars;
   }

   private static final char[] HEXCHARS = {
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
      'A', 'B', 'C', 'D', 'E', 'F'};

   /**
    * Encode data using ASCII Hex algorithm.
    */
   public static String encodeAsciiHex(byte[] data) {
      int length = data.length;
      StringBuilder buf = new StringBuilder(length * 2);

      for(int i = 0; i < length; i++) {
         byte a = (byte) ((data[i] & 0xF0) >>> 4);
         byte b = (byte) (data[i] & 0xF);

         buf.append(HEXCHARS[a]);
         buf.append(HEXCHARS[b]);
      }

      return buf.toString();
   }

   /**
    * Decode data using ASCII Hex algorithm.
    */
   public static byte[] decodeAsciiHex(String data) {
      ByteArrayOutputStream out = new ByteArrayOutputStream(data.length() / 2);
      data = data.toUpperCase();
      // @by larryl, optimization
      // @by yuz, iterate by toCharArray costs twice more than String.charAt
      //char[] arr = data.toCharArray();
      int len = data.length() - 1;

      for(int i = 0; i < len; i += 2) {
         char hi = data.charAt(i);
         char lo = data.charAt(i + 1);
         byte b = 0;

         if(hi >= '0' && hi <= 'F' && lo >= '0' && lo <= 'F') {
            if(hi <= '9') {
               b = (byte) (hi - '0');
            }
            else {
               b = (byte) (hi - 'A' + 10);
            }

            b = (byte) (b << 4);

            if(lo <= '9') {
               b |= (byte) (lo - '0');
            }
            else {
               b |= (byte) (lo - 'A' + 10);
            }

            out.write(b);
         }
      }

      return out.toByteArray();
   }

   /**
    * Compress data using zlib (rfc 1950 - 1952) algorithm.
    */
   public static byte[] deflate(byte[] data) {
      Deflater def = new Deflater(Deflater.BEST_COMPRESSION);
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      DeflaterOutputStream out = new DeflaterOutputStream(buf, def);

      try {
         out.write(data, 0, data.length);
         out.close();
      }
      catch(Exception e) {
         LOG.warn("Failed to deflate data", e);
      }
      finally {
         def.end();
      }

      return buf.toByteArray();
   }

   /**
    * Uncompress data using zlib (rfc 1950 - 1952) algorithm.
    */
   public static byte[] inflate(byte[] data) {
      Inflater def = new Inflater();
      ByteArrayInputStream buf = new ByteArrayInputStream(data);
      InflaterInputStream inp = new InflaterInputStream(buf, def);
      ByteArrayOutputStream res = new ByteArrayOutputStream();
      byte[] result = data;

      try {
         byte[] ba = new byte[256];
         int cnt;

         while((cnt = inp.read(ba, 0, ba.length)) >= 0) {
            res.write(ba, 0, cnt);
         }

         result = res.toByteArray();

         res.close();
         inp.close();
         buf.close();
      }
      catch(Exception e) {
         LOG.warn("Failed to inflate data" , e);
      }
      finally {
         def.end();
      }

      return result;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(Encoder.class);

   /**
    * Filter output stream that writes data in ASCII85 encoding.
    *
    * @author InetSoft Technology
    * @since  10.3
    */
   public static final class Ascii85OutputStream extends FilterOutputStream {
      public Ascii85OutputStream(OutputStream output) {
         this(output, true);
      }

      public Ascii85OutputStream(OutputStream output, boolean splitLines) {
         super(output);
         this.splitLines = splitLines;
      }

      @Override
      public void write(int b) throws IOException {
         if(pos == 0) {
            buffer += ((long) b << 24) & 0xff000000L;
         }
         else if(pos == 1) {
            buffer += ((long) b << 16) & 0xff0000L;
         }
         else if(pos == 2) {
            buffer += ((long) b << 8) & 0xff00L;
         }
         else {
            buffer += b & 0xffL;
         }

         pos++;

         if(pos > 3) {
            checkedWrite(convertWord(buffer));
            buffer = 0;
            pos = 0;
         }
      }

      @Override
      public void close() throws IOException {
         finalizeStream();
      }

      private void checkedWrite(byte[] buf) throws IOException {
         checkedWrite(buf, buf.length, false);
      }

      private void checkedWrite(byte[] buf , int len) throws IOException {
         checkedWrite(buf, len, false);
      }

      private void checkedWrite(byte[] buf , int len, boolean nosplit)
         throws IOException
      {
         if(splitLines && posinline + len > 80) {
            int firstpart = (nosplit ? 0 : len - (posinline + len - 80));

            if(firstpart > 0) {
               out.write(buf, 0, firstpart);
            }

            out.write(EOL); bw++;
            int rest = len - firstpart;

            if(rest > 0) {
               out.write(buf, firstpart, rest);
            }

            posinline = rest;
         }
         else {
            out.write(buf, 0, len);
            posinline += len;
         }

         bw += len;
      }

      /**
       * This converts a 32 bit value (4 bytes) into 5 bytes using base 85.
       * each byte in the result starts with zero at the '!' character so
       * the resulting base85 number fits into printable ascii chars
       *
       * @param word the 32 bit unsigned (hence the long datatype) word
       * @return 5 bytes (or a single byte of the 'z' character for word
       * values of 0)
       */
      private byte[] convertWord(long word) {
         word = word & 0xffffffff;

         if(word == 0) {
            return ZERO_ARRAY;
         }
         else {
            if(word < 0) {
               word = -word;
            }

            byte c1 = (byte) ((word / POW85[0]) & 0xFF);
            byte c2 = (byte) (((word - (c1 * POW85[0])) / POW85[1]) & 0xFF);
            byte c3 = (byte) (((word - (c1 * POW85[0]) -
               (c2 * POW85[1])) / POW85[2]) & 0xFF);
            byte c4 = (byte) (((word - (c1 * POW85[0]) -
               (c2 * POW85[1]) - (c3 * POW85[2])) / POW85[3]) & 0xFF);
            byte c5 = (byte) (((word - (c1 * POW85[0]) -
               (c2 * POW85[1]) - (c3 * POW85[2]) - (c4 * POW85[3]))) & 0xFF);

            byte[] ret = {
               (byte)(c1 + START), (byte)(c2 + START),
               (byte)(c3 + START), (byte)(c4 + START),
               (byte)(c5 + START)
            };

            return ret;
         }
      }

      public void finalizeStream() throws IOException {
         // now take care of the trailing few bytes.
         // with n leftover bytes, we append 0 bytes to make a full group of 4
         // then convert like normal (except not applying the special zero rule)
         // and write out the first n+1 bytes from the result
         if(pos > 0) {
            int rest = pos;
            byte[] conv;

            // special rule for handling zeros at the end
            if(buffer != 0) {
               conv = convertWord(buffer);
            }
            else {
               conv = new byte[5];

               for(int j = 0; j < 5; j++) {
                  conv[j] = (byte) '!';
               }
            }

            // assert rest+1 <= 5
            checkedWrite(conv, rest + 1);
         }

         flush();
      }

      private final boolean splitLines;
      private int pos = 0;
      private long buffer = 0;
      private int posinline = 0;
      private int bw = 0;

      //Special character "z" stands for four NULL bytes (short-cut for !!!!!)
      private static final int ZERO = 0x7A; //"z"
      // ZERO as a byte array
      private static final byte[] ZERO_ARRAY = { (byte) ZERO };
      // The start index for ASCII85 characters (!)
      private static final int START = 0x21; //"!"
      // The end index for ASCII85 characters (u)
      @SuppressWarnings("unused")
      private static final int END = 0x75; //"u"
      // The EOL indicator (LF)
      private static final byte[] EOL =
         System.getProperty("line.separator").getBytes();

      // Array of powers of 85 (4, 3, 2, 1, 0)
      private static final long[] POW85 = new long[] {
         85 * 85 * 85 * 85, 85 * 85 * 85, 85 * 85, 85, 1
      };
   }
}
