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
package inetsoft.report.internal.png;

import inetsoft.sree.SreeEnv;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

/**
 * Class providing methods useful to the encoding and writing of PNG
 * images, including the calculation of 32-bit CRC checksums.
 */

public class PNGUtil implements PNGConstants {
   public PNGUtil(boolean alpha) {
      this.alpha = alpha;
      imageFiltered = SreeEnv.getProperty("image.filtered").
         equalsIgnoreCase("true");
   }

   /**
    * Returns the current checksum.
    */
   public int getCRC() {
      return (int) crc.getValue();
   }

   /**
    * Resets the checksum.
    */
   public void resetCRC() {
      crc.reset();
   }

   /**
    * Updates the checksum to include the specified integer.
    */
   public void updateCRC(int d) {
      crc.update((d & 0xff000000) >> 24);
      crc.update((d & 0x00ff0000) >> 16);
      crc.update((d & 0x0000ff00) >> 8);
      crc.update(d & 0xff);
   }

   /**
    * Updates the checksum to include the specified short integer.
    */
   public void updateCRC(short d) {
      crc.update((d & 0xff00) >> 8);
      crc.update(d & 0xff);
   }

   /**
    * Updates the checksum to include the specified byte.
    */
   public void updateCRC(byte d) {
      crc.update(d);
   }

   /**
    * Updates the checksum to include the specified bytes.
    */
   public void updateCRC(byte[] d) {
      crc.update(d, 0, d.length);
   }

   /**
    * Updates the checksum to include first <i>len</i> bytes of
    * the specified array of bytes.
    */
   public void updateCRC(byte[] d, int len) {
      crc.update(d, 0, len);
   }

   /**
    * Writes a chunk to the output stream.
    */
   public void write(DataOutputStream out, byte[] id, byte[] data,
                     int len) throws IOException {
      out.writeInt(len);
      out.write(id);
      out.write(data, 0, len);
      resetCRC();
      updateCRC(id);
      updateCRC(data, len);
      out.writeInt(getCRC());
   }

   /**
    * Applies the most efficient filter to the specified
    * scanline. The filter is determined using the heuristic
    * described in section 9.6 of the PNG specification.
    */
   public byte[] applyFilter(byte[] scanLine, byte[] priorLine,
                             int bitDepth) {
      int bytesPer = alpha ? 4 : 3;
      int bpp = bitDepth / 8 * bytesPer;
      byte[] buff1 = new byte[scanLine.length + 1];

      applyNullFilter(scanLine, buff1);

      if(!imageFiltered) {
         return buff1;
      }

      byte[] buff2 = new byte[scanLine.length + 1];
      int sum1 = sumFilter(buff1);
      int sum2 = applySubFilter(scanLine, buff2, bpp);

      if(sum2 < sum1) {
         buff1 = buff2;
         sum1 = sum2;
      }

      sum2 = applyUpFilter(scanLine, priorLine, buff2);

      if(sum2 < sum1) {
         buff1 = buff2;
         sum1 = sum2;
      }

      sum2 = applyAverageFilter(scanLine, priorLine, buff2, bpp);

      if(sum2 < sum1) {
         buff1 = buff2;
         sum1 = sum2;
      }

      sum2 = applyPaethFilter(scanLine, priorLine, buff2, bpp);

      if(sum2 < sum1) {
         buff1 = buff2;
      }

      return buff1;
   }

   /**
    * Applies a null filter (no filtering of the bytes) to the
    * specified scanline.
    */
   public void applyNullFilter(byte[] scanLine, byte[] buff) {
      buff[0] = PNG_FILTER_NULL;
      System.arraycopy(scanLine, 0, buff, 1, buff.length - 1);
   }

   /**
    * Applies a null filter (no filtering of the bytes) to the
    * specified scanline.
    */
   public int sumFilter(byte[] buff) {
      int sum = 0;

      for(int i = 1; i < buff.length; i++) {
         sum += ((int) buff[i]) & 0xFF;
      }

      return sum;
   }

   /**
    * Applies a sub filter to the specified scanline.
    */
   public int applySubFilter(byte[] scanLine, byte[] buff, int bpp) {
      int sum = buff[0];

      buff[0] = PNG_FILTER_SUB;

      for(int i = 1; i < buff.length; i++) {
         byte a = scanLine[i - 1];
         byte b = i - 1 - bpp >= 0 ? scanLine[i - 1 - bpp] : 0;

         buff[i] = subtractBytes(a, b);
         sum += Math.abs((int) buff[i]);
      }

      return sum;
   }

   /**
    * Applies an up filter to the specified scanline.
    */
   public int applyUpFilter(byte[] scanLine, byte[] priorLine,
                            byte[] buff) {
      int sum = 0;

      buff[0] = PNG_FILTER_UP;

      for(int i = 1; i < buff.length; i++) {
         buff[i] = subtractBytes(scanLine[i - 1], priorLine[i - 1]);
         sum += Math.abs((int) buff[i]);
      }

      return sum;
   }

   /**
    * Applies an average filter to the specified scanline.
    */
   public int applyAverageFilter(byte[] scanLine, byte[] priorLine,
                                 byte[] buff, int bpp) {
      int sum = 0;

      buff[0] = PNG_FILTER_AVERAGE;

      for(int i = 1; i < buff.length; i++) {
         byte a = scanLine[i - 1];
         byte b = i - 1 - bpp >= 0 ? scanLine[i - 1 - bpp] : 0;
         byte c = priorLine[i - 1];

         buff[i] = (byte) (unsignedCast(a) -
            ((unsignedCast(b) + unsignedCast(c)) / 2));
         sum += Math.abs((int) buff[i]);
      }

      return sum;
   }

   /**
    * Applies a Paeth filter to the specified scanline.
    */
   public int applyPaethFilter(byte[] scanLine, byte[] priorLine,
                               byte[] buff, int bpp) {
      int sum = 0;

      buff[0] = PNG_FILTER_PAETH;

      for(int i = 1; i < buff.length; i++) {
         byte a = scanLine[i - 1];
         byte b = i - 1 - bpp >= 0 ? scanLine[i - 1 - bpp] : 0;
         byte c = priorLine[i - 1];
         byte d = i - 1 - bpp >= 0 ? priorLine[i - 1 - bpp] : 0;

         buff[i] = subtractBytes(a, getPaethPredictor(b, c, d));
         sum += Math.abs((int) buff[i]);
      }

      return sum;
   }

   /**
    * Returns the Paeth predictor for the specified bytes.
    */
   private byte getPaethPredictor(byte left, byte up, byte upLeft) {
      int p = unsignedCast(left) + unsignedCast(up) - unsignedCast(upLeft);
      int pleft = Math.abs(p - unsignedCast(left));
      int pup = Math.abs(p - unsignedCast(up));
      int pupLeft = Math.abs(p - unsignedCast(upLeft));

      if(pleft <= pup && pleft <= pupLeft) {
         return left;
      }

      if(pup <= pupLeft) {
         return up;
      }

      return upLeft;
   }

   private byte addBytes(byte a, byte b) {
      return (byte) (unsignedCast(a) + unsignedCast(b));
   }

   private byte subtractBytes(byte a, byte b) {
      return (byte) (unsignedCast(a) - unsignedCast(b));
   }

   private final int unsignedCast(byte b) {
      return ((int) b) & 0xFF;
   }

   private CRC32 crc = new CRC32();
   private boolean alpha = false;
   private boolean imageFiltered = false;
}

