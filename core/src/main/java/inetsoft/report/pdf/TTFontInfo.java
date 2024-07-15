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
package inetsoft.report.pdf;

import inetsoft.util.FileSystemService;
import inetsoft.util.swap.XSwapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.*;

/**
 * TrueType FontInfo class. Font information is extracted from truetype
 * font files (.ttf).
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
final class TTFontInfo extends FontInfo {
   static final int TAG_CMAP = 0x636d6170;
   static final int TAG_HEAD = 0x68656164;
   static final int TAG_NAME = 0x6e616d65;
   static final int TAG_GLYF = 0x676c7966;
   static final int TAG_MAXP = 0x6d617870;
   static final int TAG_PREP = 0x70726570;
   static final int TAG_HMTX = 0x686d7478;
   static final int TAG_KERN = 0x6b65726e;
   static final int TAG_HDMX = 0x68646d78;
   static final int TAG_LOCA = 0x6c6f6361;
   static final int TAG_POST = 0x706f7374;
   static final int TAG_OS2  = 0x4f532f32;
   static final int TAG_CVT  = 0x63767420;
   static final int TAG_GASP = 0x67617370;
   static final int TAG_VDMX = 0x56444d58;
   static final int TAG_VMTX = 0x766d7478;
   static final int TAG_VHEA = 0x76686561;
   static final int TAG_HHEA = 0x68686561;
   static final int TAG_TYP1 = 0x74797031;
   static final int TAG_BSLN = 0x62736c6e;
   static final int TAG_GSUB = 0x47535542;
   static final int TAG_DSIG = 0x44534947;
   static final int TAG_FPGM = 0x6670676d;
   static final int TAG_FVAR = 0x66766172;
   static final int TAG_GVAR = 0x67766172;
   static final int TAG_CFF  = 0x43464620;
   static final int TAG_MMSD = 0x4d4d5344;
   static final int TAG_MMFX = 0x4d4d4658;
   static final int TAG_BASE = 0x42415345;
   static final int TAG_GDEF = 0x47444546;
   static final int TAG_GPOS = 0x47504f53;
   static final int TAG_JSTF = 0x4a535446;
   static final int TAG_EBDT = 0x45424454;
   static final int TAG_EBLC = 0x45424c43;
   static final int TAG_EBSC = 0x45425343;
   static final int TAG_LTSH = 0x4c545348;
   static final int TAG_PCLT = 0x50434c54;
   static final int TAG_ACNT = 0x61636e74;
   static final int TAG_AVAR = 0x61766172;
   static final int TAG_BDAT = 0x62646174;
   static final int TAG_BLOC = 0x626c6f63;
   static final int TAG_CVAR = 0x63766172;
   static final int TAG_FEAT = 0x66656174;
   static final int TAG_FDSC = 0x66647363;
   static final int TAG_FMTX = 0x666d7478;
   static final int TAG_JUST = 0x6a757374;
   static final int TAG_LCAR = 0x6c636172;
   static final int TAG_MORT = 0x6d6f7274;
   static final int TAG_OPBD = 0x6d6f7274;
   static final int TAG_PROP = 0x70726f70;
   static final int TAG_TRAK = 0x7472616b;
   static final int TAG_TTCF = 0x74746366;
   // glyph flag constants
   static final int ARG_1_AND_2_ARE_WORDS = 0x1;
   static final int ARGS_ARE_XY_VALUES = 0x2;
   static final int ROUND_XY_TO_GRID = 0x4;
   static final int WE_HAVE_A_SCALE = 0x8;
   static final int MORE_COMPONENTS = 0x20;
   static final int WE_HAVE_AN_X_AND_Y_SCALE = 0x40;
   static final int WE_HAVE_A_TWO_BY_TWO = 0x80;
   static final int WE_HAVE_INSTRUCTIONS = 0x100;
   static final int USE_MY_METRICS = 0x200;
   /**
    * Get the truetype font file.
    */
   public File getFontFile() {
      return file;
   }

   /**
    * Get the postscript font name.
    */
   public String getPSName() {
      return psName;
   }

   /**
    * Get the first character in the font.
    */
   public int getFirstChar() {
      return firstChar;
   }

   /**
    * Get the last character in the font.
    */
   public int getLastChar() {
      return lastChar;
   }

   /**
    * Check if this font is cjk font.
    */
   public boolean isCJKFont() {
      return cjk != null;
   }

   /**
    * Return the cmap for a CID font.
    */
   public CMap getCMap() {
      return cjkmap;
   }

   /**
    * Check if this font contains advanced typographic tables. Currently
    * we don't support extracting a subset of glyphs for these font files
    * because the glyph substitution that is beyond single character.
    */
   public boolean isAdvancedTypographic() {
      return advanced;
   }

   /**
    * Map a character to glyph index.
    */
   public int getGlyphIndex(char c) {
      if(cjkmap != null) {
         return cjkmap.map(c);
      }
      else if(cmap != null && c < cmap.length) {
         return cmap[c] >= 0 ? cmap[c] : c;
      }

      return c;
   }

   /**
    * Get the character width.
    */
   @Override
   public int getWidth(int idx) {
      int sidx = getGlyphIndex((char) idx);
      return super.getWidth(sidx);
   }

   /**
    * Check if this is a CFF font (CFF table in the file).
    */
   public boolean isCFFont() {
      return findTagOffLen(TAG_CFF) != null;
   }

   /**
    * Get the glyph widths in CID order. For CID (CJK) fonts only.
    */
   public short[] getCIDWidths() {
      return cidwidths;
   }

   /**
    * Parse a truetype font file.
    */
   public void parse(File file) throws IOException {
      this.file = file;

      RandomAccessFile input = new RandomAccessFile(file, "r");
      readTTC(file, input);
      parse(input);
      input.close();
   }

   private void readTTC(File file, RandomAccessFile input) throws IOException {
      // @by larryl, if it's a ttc file, we only read the first font
      // this may not be accurate
      if(file.getName().toLowerCase().endsWith(".ttc")) {
         FileChannel channel = input.getChannel();

         ByteBuffer buffer = ByteBuffer.allocate(12);
         buffer.order(ByteOrder.BIG_ENDIAN);
         channel.read(buffer);
         XSwapUtil.flip(buffer);

         int numFonts = buffer.getInt(8);

         buffer = ByteBuffer.allocate(numFonts * 4);
         buffer.order(ByteOrder.BIG_ENDIAN);
         channel.read(buffer);
         XSwapUtil.flip(buffer);

         channel.position(buffer.getInt());
      }
   }

   /**
    * Parses the offset table of the font file.
    *
    * @param input the font file.
    *
    * @return the number of tables in the font file.
    *
    * @throws IOException if an I/O error occurs.
    */
   private int parseOffsetTable(RandomAccessFile input) throws IOException {
      FileChannel channel = input.getChannel();

      ByteBuffer buffer = ByteBuffer.allocate(12);
      buffer.order(ByteOrder.BIG_ENDIAN);

      channel.read(buffer);
      XSwapUtil.flip(buffer);

      int numtbls = ((int) buffer.getShort(4)) & 0xffff;

      tagofflen = new int[numtbls][4];

      buffer = ByteBuffer.allocate(16);
      buffer.order(ByteOrder.BIG_ENDIAN);

      for(int i = 0; i < numtbls; i++) {
         XSwapUtil.rewind(buffer);
         channel.read(buffer);
         XSwapUtil.flip(buffer);

         tagofflen[i][0] = buffer.getInt();
         tagofflen[i][3] = buffer.getInt();
         tagofflen[i][1] = buffer.getInt();
         tagofflen[i][2] = buffer.getInt();
      }

      return numtbls;
   }

   /**
    * Parse the file and get the offset table at the current position.
    */
   @SuppressWarnings("unchecked")
   private void parse(RandomAccessFile input) throws IOException {
      int unitPerEm = 16;
      int numOfHMetrics = 1;

      int numtbls = parseOffsetTable(input);

      FileChannel channel = input.getChannel();

      for(int i = 0; i < numtbls; i++) {
         int tag = tagofflen[i][0];
         int offset = tagofflen[i][1];
         int len = tagofflen[i][2];

         channel.position(offset);

         if(tag == TAG_HEAD) {
            ByteBuffer buffer = ByteBuffer.allocate(len);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            XSwapUtil.flip(buffer);

            unitPerEm = (int) buffer.getShort(18);

            buffer.position(36);
            bbox = new Rectangle(
               (int) buffer.getShort(), (int) buffer.getShort(),
               (int) buffer.getShort(), (int) buffer.getShort());

            indexToLocFormat = (int) buffer.getShort(50);
         }
         else if(tag == TAG_HHEA) {
            ByteBuffer buffer = ByteBuffer.allocate(len);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            XSwapUtil.flip(buffer);

            buffer.position(4);
            ascender = (int) buffer.getShort();
            descender = (int) buffer.getShort();
            lineGap = (int) buffer.getShort();
            advance = (int) buffer.getShort();
            numOfHMetrics = ((int) buffer.getShort(34)) & 0xffff;

            // if cap height is not availabe (PCLT is missing), use ascent
            if(capHeight == 0) {
               capHeight = ascender;
            }
         }
         else if(tag == TAG_POST) {
            ByteBuffer buffer = ByteBuffer.allocate(len);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            XSwapUtil.flip(buffer);

            buffer.position(4);
            italicAngle =
               buffer.getShort() + buffer.getShort() / (double) 0xffff;

            fixedPitch = (buffer.getInt(12) != 0);
         }
         else if(tag == TAG_PCLT) {
            ByteBuffer buffer = ByteBuffer.allocate(len);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            XSwapUtil.flip(buffer);

            capHeight = (int) buffer.getShort(16);
         }
         else if(tag == TAG_MAXP) {
            ByteBuffer buffer = ByteBuffer.allocate(len);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            XSwapUtil.flip(buffer);

            numGlyphs = (int) buffer.getShort(4);
            numGlyphs = numGlyphs < 0 ? (char) numGlyphs : numGlyphs;
         }
         else if(tag == TAG_NAME) {
            ByteBuffer buffer = ByteBuffer.allocate(len);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            XSwapUtil.flip(buffer);

            buffer.position(2);
            int nrec = (int) buffer.getShort();
            int storeoff = (int) buffer.getShort();
            int i2 = 6; // start of name records

            for(int j = 0; j < nrec; j++, i2 += 12) {
               buffer.position(i2 + 6);
               int nameID = (int) buffer.getShort();
               int strlen = (int) buffer.getShort();
               int stroff = (int) buffer.getShort();

               buffer.position(storeoff + stroff);
               byte[] bytes = new byte[strlen];
               buffer.get(bytes);

               String str = new String(bytes, "UnicodeBig");

               switch(nameID) {
               case 1: // family name
                  familyName = str;
                  fontName = str;
                  break;
               case 2: // sub family name (style and weight)
                  styleName = str;
                  break;
               case 4: // full font name
                  fullName = str;
                  break;
               case 6: // postscript name
                  psName = str;
                  break;
               }
            }
         }
         else if(tag == TAG_CMAP) {
            ByteBuffer buffer = ByteBuffer.allocate(len);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            XSwapUtil.flip(buffer);

            int ntbl = (int) buffer.getShort(2);

            for(int k = 0; k < ntbl; k++) {
               int sub = buffer.getInt(k * 8 + 8);
               int fmt = ((int) buffer.getShort(sub)) & 0xffff;

               switch(fmt) {
               case 0:  // direct mapping
                  cmap = new int[256];

                  for(int m = 0; m < cmap.length; m++) {
                     cmap[m] = buffer.get(sub + 6 + m) & 0xff;
                  }

                  break;
               // unicode mapping
               case 4:
                  int nseg = (((int) buffer.getShort(sub + 6)) & 0xffff) / 2;
                  int[] endCount = new int[nseg];
                  int[] startCount = new int[nseg];
                  int[] idDelta = new int[nseg];
                  int[] idRangeOffset = new int[nseg];

                  for(int n = 0; n < nseg; n++) {
                     endCount[n] =
                        ((int) buffer.getShort(sub + 14 + n * 2)) & 0xffff;
                     startCount[n] =
                        ((int) buffer.getShort(sub + 16 + nseg * 2 + n * 2)) &
                        0xffff;

                     // @by larryl, according to the ttspec, this is a USHORT
                     // but apparently the delta can be negative, so we call
                     // getSHORT instead
                     idDelta[n] =
                        (int) buffer.getShort(sub + 16 + nseg * 4 + n * 2);

                     idRangeOffset[n] =
                        ((int) buffer.getShort(sub + 16 + nseg * 6 + n * 2)) &
                        0xffff;
                  }

                  // @by mikec, if a font file contains both direct cmap
                  // and unicode cmap like WingDing font do,
                  // we must keep direct map first, otherwise the mapping
                  // will lost.
                  int[] ocmap = new int[endCount[endCount.length - 2] + 1];
                  int olen = 0;

                  if(cmap != null) {
                     olen = cmap.length;
                     
                     // @by stephenwebster, Fix bug1398964134250
                     // Ensure capacity of array to avoid AIOB when 
                     // loading font info
                     if(olen > ocmap.length) {
                        ocmap = new int[olen];
                     }
                     
                     System.arraycopy(cmap, 0, ocmap, 0, olen);
                  }

                  cmap = ocmap;

                  for(int ci = 0; ci < cmap.length; ci++) {
                     if(ci >= olen) {
                        cmap[ci] = -1;
                     }

                     for(int n = 0; n < endCount.length; n++) {
                        if(ci <= endCount[n]) {
                           if(ci >= startCount[n]) {
                              if(idRangeOffset[n] == 0) {
                                 cmap[ci] = (ci + idDelta[n]) & 0xffff;
                              }
                              else {
                                 cmap[ci] = ((int) buffer.getShort(
                                    idRangeOffset[n] + (ci - startCount[n]) *
                                    2 + sub + 16 + nseg * 6 + n * 2)) & 0xffff;
                              }
                           }

                           continue;
                        }
                     }
                  }
               }
            }
         }
         else if(tag == TAG_HMTX) {
            ByteBuffer buffer = ByteBuffer.allocate(len);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            XSwapUtil.flip(buffer);

            widths = new short[Math.max(numOfHMetrics, MAX_GLYPH)];
            int cnt;

            for(cnt = 0; cnt < numOfHMetrics; cnt++) {
               widths[cnt] = buffer.getShort(cnt * 4);
            }

            cnt = (cnt == 0) ? 1 : cnt;
            for(int j = cnt; j < widths.length; j++) {
               widths[j] = widths[cnt - 1];
            }
         }
         else if(tag == TAG_KERN) {
            ByteBuffer buffer = ByteBuffer.allocate(len);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            XSwapUtil.flip(buffer);

            int nTables = ((int) buffer.getShort(2)) & 0xffff;
            int idx = 4;

            // read subtables
            for(int ti = 0; ti < nTables; ti++) {
               int sublen = ((int) buffer.getShort(idx + 2)) & 0xffff;
               int coverage = ((int) buffer.getShort(idx + 4)) & 0xffff;

               // horizontal, kern, format 0
               if((coverage & 0x01) != 0 && (coverage & 0x02) == 0 &&
                  (coverage & 0xFF00) == 0) {
                  int pairOff = idx + 2 * 3;
                  int nPairs = ((int) buffer.getShort(pairOff)) & 0xffff;

                  pairOff += 2 * 4;
                  buffer.position(pairOff);

                  for(int j = 0; j < nPairs; j++) {
                     int left = ((int) buffer.getShort()) & 0xffff;
                     int right = ((int) buffer.getShort()) & 0xffff;
                     int value = ((int) buffer.getShort()) * 1000 / unitPerEm;

                     pairLeft.set(left);
                     pairKern.put("" + ((char) left) + ((char) right), (short) value);
                  }
               }

               idx += sublen;
            }
         }
         // glyph position table
         else if(tag == TAG_GPOS) {
            advanced = true;
         }
         // glyph substitution table
         else if(tag == TAG_GSUB) {
            advanced = true;
         }
      }

      // convert funit to 1/1000 pt
      ascender = ascender * 1000 / unitPerEm;
      descender = descender * 1000 / unitPerEm;
      lineGap = lineGap * 1000 / unitPerEm;
      advance = advance * 1000 / unitPerEm;
      capHeight = capHeight * 1000 / unitPerEm;
      bbox.x = bbox.x * 1000 / unitPerEm;
      bbox.y = bbox.y * 1000 / unitPerEm;
      bbox.width = bbox.width * 1000 / unitPerEm;
      bbox.height = bbox.height * 1000 / unitPerEm;
      cjk = FontManager.getFontManager().getCJKInfo(getFullName());

      // convert units to 1/1000 of a point
      for(int i = 0; i < widths.length; i++) {
         widths[i] = (short) (widths[i] * 1000 / unitPerEm);
      }

      // cmap is retrieved from a CMap resource file in the pdf/cmap
      // directory
      if(isCJKFont()) {
         cidwidths = widths;

         try {
            cjkmap = new CMap(cjk[1]);
         }
         catch(Exception e) {
            LOG.error("Failed to create CJK font map: " + cjk[1], e);
         }
      }
   }

   private File dropTables(RandomAccessFile rafile) throws IOException {
      File tfile = FileSystemService.getInstance().getCacheTempFile("font", "ttf");
      tfile.deleteOnExit();

      FileChannel channel = rafile.getChannel();

      ByteBuffer buffer = ByteBuffer.allocate(12);
      buffer.order(ByteOrder.BIG_ENDIAN);
      channel.read(buffer);
      XSwapUtil.flip(buffer);

      int numTables = ((int) buffer.getShort(4)) & 0xffff;

      buffer = ByteBuffer.allocate(16 * numTables);
      buffer.order(ByteOrder.BIG_ENDIAN);
      channel.read(buffer);
      XSwapUtil.flip(buffer);

      Map<Integer, int[]> index = new LinkedHashMap<>();
      Set<Integer> reqd = new HashSet<>();

      reqd.add(TAG_HEAD);
      reqd.add(TAG_HHEA);
      reqd.add(TAG_LOCA);
      reqd.add(TAG_MAXP);
      reqd.add(TAG_CVT);
      reqd.add(TAG_PREP);
      reqd.add(TAG_GLYF);
      reqd.add(TAG_HMTX);
      reqd.add(TAG_FPGM);
      reqd.add(TAG_CMAP); // required if present

      for(int i = 0; i < numTables; i++) {
         int tag = buffer.getInt();
         int checkSum = buffer.getInt();
         int offset = buffer.getInt();
         int length = buffer.getInt();

         if(reqd.contains(tag)) {
            index.put(tag, new int[] { checkSum, offset, length });
         }
      }

      RandomAccessFile trafile = new RandomAccessFile(tfile, "rw");

      try {
         FileChannel tchannel = trafile.getChannel();

         try {
            numTables = index.size();

            int pow2size = (int) Math.floor(Math.log(numTables) / Math.log(2));
            int searchRange = pow2size * 16;
            int entrySelector =
               (int) Math.round(Math.log(pow2size) / Math.log(2));
            int rangeShift = numTables * 16 - searchRange;

            buffer = ByteBuffer.allocate(12);
            buffer.order(ByteOrder.BIG_ENDIAN);

            buffer.putInt(0x00010000);
            buffer.putShort((short) numTables);
            buffer.putShort((short) searchRange);
            buffer.putShort((short) entrySelector);
            buffer.putShort((short) rangeShift);

            XSwapUtil.flip(buffer);
            tchannel.write(buffer);

            int offset = 12 + 16 * numTables;

            for(Map.Entry<Integer, int[]> e : index.entrySet()) {
               buffer = ByteBuffer.allocate(16);
               buffer.order(ByteOrder.BIG_ENDIAN);

               buffer.putInt(e.getKey());
               buffer.putInt(e.getValue()[0]);
               buffer.putInt(offset);
               buffer.putInt(e.getValue()[2]);

               XSwapUtil.flip(buffer);
               tchannel.write(buffer);

               offset += e.getValue()[2];
            }

            for(int[] bounds : index.values()) {
               buffer = ByteBuffer.allocate(bounds[2]);
               channel.position(bounds[1]);
               channel.read(buffer);
               XSwapUtil.flip(buffer);
               tchannel.write(buffer);
            }
         }
         finally {
            tchannel.close();
         }
      }
      finally {
         trafile.close();
      }

      return tfile;
   }

   /**
    * Retrieves the font file data.
    */
   byte[] getFontData() throws IOException {
      File fontFile = getFontFile();
      ByteArrayOutputStream buf =
         new ByteArrayOutputStream((int) fontFile.length());

      try(FileInputStream input = new FileInputStream(fontFile)) {
         byte[] b2 = new byte[4096];
         int cnt;

         // read in font file contents
         while((cnt = input.read(b2)) > 0) {
            buf.write(b2, 0, cnt);
         }
      }

      return buf.toByteArray();
   }

   /**
    * Retrieves a font file with a subset of glyph.
    */
   byte[] getFontData(BitSet cidset) throws IOException {
      RandomAccessFile input = new RandomAccessFile(file, "r");

      // CID font required tables:
      // head, hhea, loca, maxp, cvt_, prep, glyf, hmtx, fpgm
      if(findTagOffLen(TAG_LOCA) != null) {
         readTTC(file, input);
         File tfile = dropTables(input);
         input.close();
         input = new RandomAccessFile(tfile, "r");

         try {
            parseOffsetTable(input);

            ByteArrayOutputStream locaBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream glyfBuf = new ByteArrayOutputStream();
            DataOutputStream locaOut = new DataOutputStream(locaBuf);
            int[] loca = findTagOffLen(TAG_LOCA);
            int[] glyf = findTagOffLen(TAG_GLYF);

            if(glyf == null) {
               throw new IOException("GLYF table missing: " + file);
            }

            // find composite glyph
            BitSet composite = new BitSet();

            input.seek(loca[1]);
            int bi = (indexToLocFormat == 0) ?
               input.readUnsignedShort() * 2 :
               input.readInt();

            // create a new glyf table with the glyphs that are in the cidset
            for(int i = 0; i < numGlyphs; i++) {
               int ei = (indexToLocFormat == 0) ?
                  input.readUnsignedShort() * 2 :
                  input.readInt();

               if(cidset.get(i) && ei > bi) {
                  long off = input.getFilePointer();

                  input.seek(glyf[1] + bi);
                  byte[] buf = new byte[ei - bi];

                  input.readFully(buf);

                  ByteBuffer buffer = ByteBuffer.wrap(buf);
                  buffer.order(ByteOrder.BIG_ENDIAN);

                  int idx = 0;
                  int numberOfContours = buffer.getShort();

                  if(numberOfContours >= 0) {
                     bi = ei;
                     input.seek(off);
                     continue;
                  }

                  idx += 10;

                  int flags = 0;

                  do {
                     flags = ((int) buffer.getShort(idx)) & 0xffff;
                     int glyph = ((int) buffer.getShort(idx + 2)) & 0xffff;

                     composite.set(glyph);

                     idx += 4;

                     if((flags & ARG_1_AND_2_ARE_WORDS) != 0) {
                        idx += 4;
                     }
                     else {
                        idx += 2;
                     }

                     if((flags & WE_HAVE_A_SCALE) != 0) {
                        idx += 2;
                     }
                     else if((flags & WE_HAVE_AN_X_AND_Y_SCALE) != 0) {
                        idx += 4;
                     }
                     else if((flags & WE_HAVE_A_TWO_BY_TWO) != 0) {
                        idx += 8;
                     }
                  }
                  while((flags & MORE_COMPONENTS) != 0);
                  input.seek(off);
               }

               bi = ei;
            }

            // copy glyf and loca tables
            input.seek(loca[1]);
            bi = (indexToLocFormat == 0) ?
               input.readUnsignedShort() * 2 :
               input.readInt();

            // create a new glyf table with the glyphs that are in the cidset
            for(int i = 0; i < numGlyphs; i++) {
               int ei = (indexToLocFormat == 0) ?
                  input.readUnsignedShort() * 2 :
                  input.readInt();

               if(indexToLocFormat == 0) {
                  locaOut.writeShort(glyfBuf.size() / 2);
               }
               else {
                  locaOut.writeInt(glyfBuf.size());
               }

               if((cidset.get(i) || composite.get(i)) && ei > bi) {
                  long off = input.getFilePointer();

                  input.seek(glyf[1] + bi);
                  byte[] buf = new byte[ei - bi];

                  input.readFully(buf);
                  glyfBuf.write(buf);
                  input.seek(off);
               }

               bi = ei;
            }

            // write the last index
            if(indexToLocFormat == 0) {
               locaOut.writeShort(glyfBuf.size() / 2);
            }
            else {
               locaOut.writeInt(glyfBuf.size());
            }

            locaOut.flush();

            // start creating the file
            ByteArrayOutputStream dataBuf = new ByteArrayOutputStream();
            // table directory
            ByteArrayOutputStream dirBuf = new ByteArrayOutputStream();
            DataOutputStream dirOut = new DataOutputStream(dirBuf);

            // Offset table
            copy(input, 0, 12, dataBuf);
            // copy table directory, we will replace the table directory
            // after the table is created, but copy it here so the offset
            // of subsequent tables are correct
            copy(input, 12, 16 * tagofflen.length, dataBuf);

            // copy each table
            for(int i = 0; i < tagofflen.length; i++) {
               long off = dataBuf.size();
               int checksum = 0;

               if(tagofflen[i][0] == TAG_LOCA) {
                  checksum = copy(locaBuf, dataBuf);
               }
               else if(tagofflen[i][0] == TAG_GLYF) {
                  checksum = copy(glyfBuf, dataBuf);
               }
               else {
                  copy(input, tagofflen[i][1], tagofflen[i][2], dataBuf);
                  checksum = tagofflen[i][3];
               }

               dirOut.writeInt(tagofflen[i][0]);
               dirOut.writeInt(checksum);
               dirOut.writeInt((int) off);
               dirOut.writeInt((int) (dataBuf.size() - off));
               // pad the output to 4 bytes boundary so the next table is
               // aligned
               pad4(dataBuf);
            }

            // get the output array
            byte[] outarr = dataBuf.toByteArray();

            // copy the directory array
            dirOut.flush();
            System.arraycopy(dirBuf.toByteArray(), 0, outarr, 12,
               tagofflen.length * 16);
            return outarr;
         }
         finally {
            input.close();
            tfile.delete();

            input = new RandomAccessFile(file, "r");
            readTTC(file, input);
            parseOffsetTable(input);
            input.close();
         }
      }
      // CFF file, must be handled differently (Compact Font File)
      else if(findTagOffLen(TAG_CFF) != null) {
         int[] vars = findTagOffLen(TAG_CFF);
         ByteArrayOutputStream cffBuf = new ByteArrayOutputStream();

         CFF.extract(input, vars[1], vars[2], cffBuf, cidset);
         return cffBuf.toByteArray();
      }

      return null;
   }

   /**
    * Find the tag, offset, lengh array, null if not found.
    */
   int[] findTagOffLen(int tag) {
      for(int i = 0; i < tagofflen.length; i++) {
         if(tagofflen[i][0] == tag) {
            return tagofflen[i];
         }
      }

      return null;
   }

   /**
    * Pad the buffer to 4 bytes boundary.
    */
   static void pad4(ByteArrayOutputStream stream) throws IOException {
      int pad = (4 - stream.size() % 4) % 4;

      stream.write(new byte[pad]);
   }

   /**
    * Copy a section from one file to the other.
    * @return checksum of the copied data.
    */
   static int copy(RandomAccessFile input, int off, int len,
                   OutputStream output) throws IOException
   {
      byte[] buf = new byte[len];

      input.seek(off);
      input.readFully(buf);
      output.write(buf);
      return getChecksum(buf);
   }

   /**
    * Copy a file to the other.
    * @return checksum of the copied data.
    */
   static int copy(ByteArrayOutputStream input, OutputStream output)
      throws IOException
   {
      byte[] buf = input.toByteArray();

      output.write(buf);

      return getChecksum(buf);
   }

   // check sum is the sum of all longs. assume the buf is padded at
   // 4 bytes boundary
   static int getChecksum(byte[] buf) {
      long sum = 0;
      ByteBuffer buffer = ByteBuffer.wrap(buf);
      buffer.order(ByteOrder.BIG_ENDIAN);

      for(int i = 0; i <= buf.length - 4; i += 4) {
         sum += ((long) buffer.getInt()) & 0xffffffffL;
         sum &= 0xffffffffL;
      }

      return ((int) sum) & 0xffffffff;
   }

   /**
    * Return a name, fullname, psname tuple or tuples (for ttc).
    */
   public static String[] getFontNames(File file) throws IOException {
      RandomAccessFile input = new RandomAccessFile(file, "r");

      try {
         FileChannel channel = input.getChannel();

         ByteBuffer buffer = ByteBuffer.allocate(12);
         buffer.order(ByteOrder.BIG_ENDIAN);
         channel.read(buffer);
         XSwapUtil.flip(buffer);

         int tag = buffer.getInt();

         if(tag == TAG_TTCF) {
            int numfonts = buffer.getInt(8);
            List<String> tuples = new ArrayList<>();

            // read offset table
            buffer = ByteBuffer.allocate(4 * numfonts);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            XSwapUtil.flip(buffer);

            for(int i = 0; i < numfonts; i++) {
               int offset = buffer.getInt(i * 4);

               channel.position(offset);
               String[] names = getFontNames0(channel);

               for(int k = 0; k < names.length; k++) {
                  tuples.add(names[k]);
               }
            }

            return tuples.toArray(new String[tuples.size()]);
         }
         else {
            channel.position(0);
            return getFontNames0(channel);
         }
      }
      finally {
         input.close();
      }
   }

   /**
    * Read the font name, full name, and psname.
    */
   private static String[] getFontNames0(FileChannel channel) throws IOException
   {
      List<String> names = new ArrayList<>();
      List<String> fullnames = new ArrayList<>();
      List<String> psnames = new ArrayList<>();

      ByteBuffer buffer = ByteBuffer.allocate(12);
      buffer.order(ByteOrder.BIG_ENDIAN);
      channel.read(buffer);
      XSwapUtil.flip(buffer);

      int numtbls = (int) buffer.getShort(4);

      buffer = ByteBuffer.allocate(16);
      buffer.order(ByteOrder.BIG_ENDIAN);

      for(int i = 0; i < numtbls; i++) {
         XSwapUtil.rewind(buffer);
         channel.read(buffer);
         XSwapUtil.flip(buffer);

         int tag = buffer.getInt();

         if(tag == TAG_NAME) {
            int offset = buffer.getInt(8);
            int len = buffer.getInt(12);

            channel.position(offset);

            ByteBuffer buffer2 = ByteBuffer.allocate(len);
            buffer2.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer2);
            XSwapUtil.flip(buffer2);

            buffer2.position(2);
            int nrec = (int) buffer2.getShort();
            int storeoff = (int) buffer2.getShort();
            int i2 = 6; // start of name records

            for(int j = 0; j < nrec; j++, i2 += 12) {
               buffer2.position(i2 + 6);
               int nameID = (int) buffer2.getShort();
               int strlen = (int) buffer2.getShort();
               int stroff = (int) buffer2.getShort();

               // internal error, font format not correct
               if(storeoff + stroff + strlen > buffer2.capacity()) {
                  continue;
               }

               byte[] bytes = new byte[strlen];
               buffer2.position(storeoff + stroff);
               buffer2.get(bytes);
               String str = new String(bytes, "UnicodeBig");

               if(nameID == 1) {
                  names.add(0, str);
               }
               else if(nameID == 4) {
                  fullnames.add(0, str);
               }
               else if(nameID == 6) {
                  psnames.add(0, str);
               }
            }

            break;
         }
      }

      int n = names.size();

      if(n == 0) {
         n = fullnames.size();
      }
      else if(fullnames.size() != 0) {
         n = Math.min(n, fullnames.size());
      }

      if(n == 0) {
         n = psnames.size();
      }
      else if(psnames.size() != 0) {
         n = Math.min(n, psnames.size());
      }

      String[] arr = new String[n * 3];

      for(int i = 0, idx = 0; i < n; i++) {
         arr[idx++] = names.size() > i ? names.get(i) : null;
         arr[idx++] = fullnames.size() > i ? fullnames.get(i) : null;
         arr[idx++] = psnames.size() > i ? psnames.get(i) : null;
      }

      return arr;
   }

   // @by louis, pass the security scanning
   /*public static void main(String args[]) {
      PrintStream err = System.out;

      for(int n = 0; n < args.length; n++) {
         try {
            String[] fnames = TTFontInfo.getFontNames(new File(args[n]));

            err.println("Getting font names:[" + args[n] + "]");

            for(int k = 0; k < fnames.length; k++) {
               err.println("fontnames[" + k + "]=" + fnames[k]);
            }

            TTFontInfo fi = new TTFontInfo();

            fi.parse(new File(args[n]));

            err.println("Parsing font file:[" + args[n] + "]");
            err.println("k: " + fi.getKern().size());
            err.println("getFontName = " + fi.getFontName());
            err.println("getFullName = " + fi.getFullName());
            err.println("getFamilyName = " + fi.getFamilyName());
            err.println("getPSName = " + fi.getPSName());
            err.println("[style name] = " + fi.styleName);
            err.println("getWeight = " + fi.getWeight());
            err.println("isFixedPitch = " + fi.isFixedPitch());
            err.println("getAscent = " + fi.getAscent());
            err.println("getDescent = " + fi.getDescent());
            err.println("getLeading = " + fi.getLeading());
            err.println("getMaxAdvance = " + fi.getMaxAdvance());
            err.println("getItalicAngle = " + fi.getItalicAngle());
            err.println("getFontBBox = " + fi.getFontBBox());
            err.println("getEncoding = " + fi.getEncoding());
            err.println("getCapHeight = " + fi.getCapHeight());

            short[] widths = fi.getWidths();

            err.print("width[");
            for(int i = 0; i < widths.length; i++) {
               if((i % 15) == 0) {
                  err.print("\n" + i + ": ");
               }

               err.print(widths[i] + " ");
            }

            err.println("]");
         }
         catch(Exception e) {
            LOG.error(e.getMessage(), e);
         }
      }
   }*/

   private static final int MAX_GLYPH = 0x2027;

   private String psName = null;
   private String styleName = null;
   private int firstChar = 32;
   private int lastChar = 1169;
   private File file;
   private String[] cjk = null; // cjk ordering and encoding
   private CMap cjkmap = null; // cmap for cjk font
   private int[] cmap = null;
   private int indexToLocFormat = 0; // 0 for short offset, 1 for long offset
   private int numGlyphs = 1169; // number of glyphs
   // each row is (tag, offset, length, checksum)
   private int[][] tagofflen = null;
   private short[] cidwidths = null; // glyph widths in cid order
   private boolean advanced = false; // Advanced typographic font file

   private static final Logger LOG =
      LoggerFactory.getLogger(TTFontInfo.class);
}
