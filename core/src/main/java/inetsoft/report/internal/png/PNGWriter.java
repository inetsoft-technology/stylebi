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
package inetsoft.report.internal.png;

import inetsoft.sree.SreeEnv;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;

public class PNGWriter implements PNGConstants {
   public PNGWriter(PNGImage image, int bufSize, PNGUtil util, boolean alpha) {
      this.image = image;
      this.util = util;
      this.alpha = alpha;

      int bytesPer = alpha ? 4 : 3;

      zstream = new Deflater();
      zbuf = new byte[bufSize];

      String propVal = SreeEnv.getProperty("image.filtered");

      if(propVal.equalsIgnoreCase("true")) {
         zstream.setStrategy(Deflater.FILTERED);
      }
      else {
         zstream.setStrategy(Deflater.DEFAULT_STRATEGY);
      }

      zstream.setLevel(Deflater.DEFAULT_COMPRESSION);

      int size = image.getWidth() * image.getBitDepth() / 8 * bytesPer;
      size = size < 0 ? 0 : size;
      scanLine = new byte[size];
      priorLine = new byte[size];

      for(int i = 0; i < scanLine.length; i++) {
         scanLine[i] = (byte) 0;
         priorLine[i] = (byte) 0;
      }
   }

   protected void writeImageData(DataOutputStream out) throws IOException {
      zoffset = 0;

      while(true) {
         priorLine = scanLine;
         scanLine = image.getScanLine();

         if(scanLine == null) {
            break;
         }

         writeFindFilter(out);
      }

      writeFinalize(out);
   }

   private void writeFindFilter(DataOutputStream out) throws IOException {
      filteredLine = util.applyFilter(scanLine, priorLine, image.getBitDepth());
      writeFilteredRow(out);
   }

   private void writeFilteredRow(DataOutputStream out) throws IOException {
      zstream.setInput(filteredLine, 0, filteredLine.length);

      do {
         int numBytes = zstream.deflate(zbuf, zoffset, zbuf.length - zoffset);

         zoffset += numBytes;

         if(zoffset == zbuf.length) {
            util.write(out, PNG_IDAT, zbuf, zoffset);
            zoffset = 0;
         }
      }
      while(!zstream.needsInput());
   }

   private void writeFinalize(DataOutputStream out) throws IOException {
      zstream.finish();

      do {
         int numBytes = zstream.deflate(zbuf, zoffset, zbuf.length - zoffset);

         zoffset += numBytes;

         if(zstream.finished() || zoffset == zbuf.length) {
            util.write(out, PNG_IDAT, zbuf, zoffset);
            zoffset = 0;
         }
      }
      while(!zstream.finished());
      zstream.reset();
   }

   protected void writeSignature(DataOutputStream out) throws IOException {
      out.write(PNG_SIGNATURE);
   }

   protected void writeIEND(DataOutputStream out) throws IOException {
      byte[] dummy = { 0 };

      util.write(out, PNG_IEND, dummy, 0);
   }

   protected void writeIHDR(DataOutputStream out) throws IOException {
      byte color_type = PNG_COLOR_TYPE_COLOR;

      if(alpha) {
         color_type |= PNG_COLOR_TYPE_ALPHA;
      }

      byte[] hdrData = { (byte) ((image.getWidth() >> 24) & 0xff),
         (byte) ((image.getWidth() >> 16) & 0xff),
         (byte) ((image.getWidth() >> 8) & 0xff),
         (byte) (image.getWidth() & 0xff),
         (byte) ((image.getHeight() >> 24) & 0xff),
         (byte) ((image.getHeight() >> 16) & 0xff),
         (byte) ((image.getHeight() >> 8) & 0xff),
         (byte) (image.getHeight() & 0xff), (byte) (image.getBitDepth()),
         color_type, PNG_COMPRESSION_TYPE_DEFAULT, PNG_FILTER_METHOD_DEFAULT,
         PNG_INTERLACE_NONE };

      util.write(out, PNG_IHDR, hdrData, 13);
   }

   protected void writePHYS(DataOutputStream out, int dpi) throws IOException {
      int dpm = (int) (dpi * 39.370079);

      byte[] hdrData = {
         (byte) ((dpm >> 24) & 0xff),
         (byte) ((dpm >> 16) & 0xff),
         (byte) ((dpm >> 8) & 0xff),
         (byte) (dpm & 0xff),
         (byte) ((dpm >> 24) & 0xff),
         (byte) ((dpm >> 16) & 0xff),
         (byte) ((dpm >> 8) & 0xff),
         (byte) (dpm & 0xff),
         (byte) 1};

      util.write(out, PNG_PHYS, hdrData, hdrData.length);
   }

   private PNGImage image;
   private PNGUtil util;
   private PNGEncoder encoder;
   private Deflater zstream;
   private byte[] zbuf;
   private byte[] scanLine;
   private byte[] priorLine;
   private byte[] filteredLine;
   private int zoffset;
   private boolean alpha = false;
}

