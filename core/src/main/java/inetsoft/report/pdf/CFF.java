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
package inetsoft.report.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.BitSet;

/**
 * Compact Font Format.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
class CFF {
   /**
    * Extract glyphs.
    */
   public static void extract(RandomAccessFile input, int offset, int len,
      ByteArrayOutputStream out, BitSet cidset)
      throws IOException {
      byte[] buf = new byte[len];

      input.seek(offset);
      input.readFully(buf);
      
      ByteArrayOutputStream cffBuf = new ByteArrayOutputStream();
      ByteArrayOutputStream cff2Buf = new ByteArrayOutputStream();
      // get the fixed five sections size
      int fixed = 4; // header

      // add the sizes of next 4 indexes
      for(int i = 0; i < 4; i++) {
         fixed += getIndexSize(buf, fixed);
      }

      fixed += 32; // extra bytes between the fixed portion and following

      // write header, default size is 4
      int idx = buf[2]; // get header size from header, 3rd card8

      cffBuf.write(buf, 0, idx);
      // copy name index
      idx += copyIndex(buf, idx, cffBuf);

      // process Top DICT INDEX
      byte[] topdict = buf;
      int[] topdictOff = {idx};
      int cnt = getIndexCount(topdict, topdictOff[0]);

      idx += getIndexSize(buf, idx);

      // iterate through the Top DICT in the Top DICT INDEX
      for(int i = 0; i < cnt; i++) {
         // get the Dict, each Dict is identified by topdict and offs
         int[] offs = getIndexOffsets(topdict, topdictOff[0], i);
         int charstringsOff = getDictInt(topdict, offs[0], offs[1],
                                         "CharStrings");
         int nglyphs = getIndexCount(buf, charstringsOff);
         int charsetOff = getDictInt(topdict, offs[0], offs[1], "charset");
         // CIDFont does not have encoding
         int[] fdarrayOff = {getDictInt(topdict, offs[0], offs[1], "FDArray")};
         int fdselectOff = getDictInt(topdict, offs[0], offs[1], "FDSelect");
         int[] privateSO = getDictIntArray(topdict, offs[0], offs[1],
                                           "Private");
         int subrsOff = 0;
         if(privateSO != null) {
            subrsOff = getDictInt(buf, privateSO[1], 
                                  privateSO[0] + privateSO[1], "Subrs");
         }

         // start writing to cff2Buf
         byte[] dict = topdict;
         
         // copy charset
         dict = setDictInt(dict, offs, "charset", cff2Buf.size() + fixed);
         copyCharsets(buf, charsetOff, nglyphs, cff2Buf);

         // copy FDSelect
         dict = setDictInt(dict, offs, "FDSelect", cff2Buf.size() + fixed);

         // fdset is the FD that is used in the glyph
         copyFDSelect(buf, fdselectOff, nglyphs, cff2Buf, cidset);
         
         // copy charstrings
         dict = setDictInt(dict, offs, "CharStrings", cff2Buf.size() + fixed);
         extractIndex(buf, charstringsOff, cidset, cff2Buf);
         // copyIndex(buf, charstringsOff, cff2Buf);

         // copy Private DICT in FDArray
         byte[] fdarray = buf;
         int fdcnt = getIndexCount(fdarray, fdarrayOff[0]);
         int fdsize = getIndexSize(fdarray, fdarrayOff[0]);
         
         // remember the size before fdarray
         int beforeFDArray = cff2Buf.size() + fixed + fdsize;
         
         ByteArrayOutputStream cff3Buf = new ByteArrayOutputStream();
         ByteArrayOutputStream cff4Buf = new ByteArrayOutputStream();
         
         // remember the staring point of subrs
         int[] lastfdoff = getIndexOffsets(fdarray, fdarrayOff[0], fdcnt - 1);
         int[] lastpri = getDictIntArray(fdarray, lastfdoff[0], lastfdoff[1],
                                           "Private");
         int subroffset = lastpri[1] + lastpri[0];
         
         // copy each Private DICT in FD
         for(int k = 0; k < fdcnt; k++) {
            byte[] fd = fdarray;
            int[] fdoffs = getIndexOffsets(fdarray, fdarrayOff[0], k);
            int[] fdprivSO = getDictIntArray(fd, fdoffs[0], fdoffs[1],
                                             "Private");

            if(fdprivSO != null) {
               if(fdprivSO.length != 2) {
                  throw new RuntimeException("Private OP wrong size: " +
                                             fdprivSO.length);
               }

               fd = setDictInt(fdarray, fdoffs, "Private",
                               cff3Buf.size() + beforeFDArray);

               byte[] privbuf = buf;
               int[] privoffs = {fdprivSO[1], fdprivSO[1] + fdprivSO[0]};
               int fdsubrsOff = getDictInt(buf, fdprivSO[1],
                                           fdprivSO[0] + fdprivSO[1], "Subrs");

               // local subrs is optional
               if(fdsubrsOff > 0) {
                  privbuf = setDictInt(privbuf, privoffs, "Subrs", 
                                       (subroffset - fdprivSO[1] + cff4Buf.size()));
               }

               cff3Buf.write(privbuf, privoffs[0], privoffs[1] - privoffs[0]);
               
               // local subrs offset in private DICT is relative to the 
               // beginning of the private DICT
               if(fdsubrsOff > 0) {
                  // pad space if the priv DICT shrank
                  if(privbuf.length < fdprivSO[0]) {
                     cff3Buf.write(new byte[fdprivSO[0] - privbuf.length]);
                  }

                  copyIndex(buf, fdsubrsOff + fdprivSO[1], cff4Buf);
               }

               if(fd != fdarray) {
                  fdarray = setIndexValue(fdarray, fdarrayOff, k, fd);
               }
            }
         }

         // copy FD
         int fdarrayOffset = cff2Buf.size() + fixed;
         dict = setDictInt(dict, offs, "FDArray", fdarrayOffset);
         copyIndex(fdarray, fdarrayOff[0], cff2Buf);
         
         // copy private Dict and local subrs
         cff2Buf.write(cff3Buf.toByteArray());
         cff2Buf.write(cff4Buf.toByteArray());         
         
         if(privateSO != null) {
            dict = setDictInt(dict, offs, "Private", cff2Buf.size() + fixed);
            cff2Buf.write(buf, privateSO[1], privateSO[0]);
            copyIndex(buf, privateSO[1] + subrsOff, cff2Buf);
         }

         if(dict != topdict) {
            topdict = setIndexValue(topdict, topdictOff, i, dict);
         }
      }

      // write top dict index
      copyIndex(topdict, topdictOff[0], cffBuf);
      
      // copy 2 fixed INDEX, String INDEX and Global Subr INDEX
      idx += copyIndex(buf, idx, cffBuf);
      idx += copyIndex(buf, idx, cffBuf);

      // pad the fixed sections to fill gaps 
      if(cffBuf.size() < fixed) {
         cffBuf.write(new byte[fixed - cffBuf.size()]);
      }
      else if(cffBuf.size() > fixed) {
         throw new RuntimeException("CFF sizes incorrect!");
      }

      // copy all other data
      cffBuf.write(cff2Buf.toByteArray());
      out.write(cffBuf.toByteArray());
   }

   static class Op {
      public static final int INT = 1;
      public static final int DOUBLE = 2;
      public static final int OP = 3;
      public Op(int number, int len) {
         inumber = number;
         this.len = len;
         type = INT;
      }

      public Op(double number, int len) {
         dnumber = number;
         this.len = len;
         type = DOUBLE;
      }

      public Op(String op, int len) {
         this.op = op;
         this.len = len;
         type = OP;
      }

      public int getType() {
         return type;
      }

      public int getInt() {
         return inumber;
      }

      public double getDouble() {
         return dnumber;
      }

      public String getOp() {
         return op;
      }

      // return the number of types this value occupies
      public int length() {
         return len;
      }

      public String toString() {
         switch(type) {
         case OP:
            return op;
         case INT:
            return inumber + " [" + len + "]";
         case DOUBLE:
            return dnumber + " [" + len + "]";
         }

         return "null";
      }

      int type;
      int inumber;
      double dnumber;
      String op;
      int len;
   }

   static final String[] ops = { "version", "Notice", "FullName", "FamilyName",
      "Weight", "FontBBox", "BlueValues", "OtherBlues", "FamilyBlues",
      "FamilyOtherBlues", "StdHW", "StdVW", "escape", "UniqueID",
      "XUID", "charset", "Encoding", "CharStrings", "Private", "Subrs",
      "defaultWidthX", "nominalWidthX"};
   static final String[] ops2 = { "Copyright", "isFixedPitch", "ItalicAngle",
      "UnderlinePosition", "UnderlineThickness", "PaintType", "CharstringType",
      "FontMatrix", "StrokeWidth", "BlueScale", "BlueShift", "BlueFuzz",
      "StemSnapH", "StemSnapV", "ForceBold", "15", "16", "LanguageGroup",
      "ExpansionFactor", "initialRandomSeed",
      "SyntheticBase", "PostScript", "BaseFontName", "BaseFontBlend",
      "MultipleMaster", "25", "BlendAxisType", "27", "28", "29", "ROS",
      "CIDFontVersion", "CIDFontRevision", "CIDFontType", "CIDCount",
      "UIDBase", "FDArray", "FDSelect", "FontName", "Chameleon" };

   static Op getOp(byte[] buf, int idx) {
      int b0 = (int) buf[idx] & 0xFF;

      // double byte operator
      if(b0 == 12) {
         int b1 = (int) buf[idx + 1] & 0xFF;
         return new Op((b1 < ops2.length) ? ops2[b1] : "", 2);
      } 
      // single byte operator
      else if(b0 >= 0 && b0 <= 27 || b0 == 31) {
         return new Op((b0 < ops.length) ? ops[b0] : "", 1);
      }
      else if(b0 >= 32 && b0 <= 246) {
         return new Op(b0 - 139, 1);
      }
      else if(b0 >= 247 && b0 <= 250) {
         int b1 = (int) buf[idx + 1] & 0xFF;

         return new Op((b0 - 247) * 256 + b1 + 108, 2);
      }
      else if(b0 >= 251 && b0 <= 254) {
         int b1 = (int) buf[idx + 1] & 0xFF;

         return new Op(-(b0 - 251) * 256 - b1 - 108, 2);
      }
      else if(b0 == 28) {
         int b1 = (int) buf[idx + 1] & 0xFF;
         int b2 = (int) buf[idx + 2] & 0xFF;

         return new Op((b1 << 8) | b2, 3);
      }
      else if(b0 == 29) {
         int b1 = (int) buf[idx + 1] & 0xFF;
         int b2 = (int) buf[idx + 2] & 0xFF;
         int b3 = (int) buf[idx + 3] & 0xFF;
         int b4 = (int) buf[idx + 4] & 0xFF;

         return new Op((b1 << 24) | (b2 << 16) | (b3 << 8) | b4, 5);
      }
      else if(b0 == 30) {
         /// incorrect value???
         for(int i = idx + 1; i < buf.length; i++) {
            if((buf[i] & 0xf) == 0xf) {
               return new Op(0.1, i - idx + 1);
            }
         }

         return new Op(0.1, buf.length - idx);
      }

      return null;
   }

   /**
    * Encode an integer in CFF DICT number format, 1-5 bytes.
    */
   static byte[] encodeDictInt(int val) {
      if(val >= -107 && val <= 107) {
         return new byte[] {(byte) (val + 139)};
      }
      else if(val >= 108 && val <= 1131) {
         int b1 = (val - 108) % 256;
         int b0 = (val - 108) / 256 + 247;

         return new byte[] {(byte) b0, (byte) b1};
      }
      else if(val >= -1131 && val <= -108) {
         /// - b1?
         int b1 = (108 - val) % 256;
         int b0 = (108 - val) / 256 + 251;

         return new byte[] {(byte) b0, (byte) b1};
      }
      else if(val >= -32768 && val <= 32767) {
         return new byte[] {(byte) 28, (byte) ((val >>> 8) & 0xFF),
            (byte) (val & 0xFF)};
      }

      return new byte[] {(byte) 29, (byte) ((val >>> 24) & 0xFF),
         (byte) ((val >>> 16) & 0xFF), (byte) ((val >>> 8) & 0xFF),
         (byte) (val & 0xFF)};
   }

   /**
    * Copy an entire Charsets section to output.
    * @return number of bytes copied.
    */
   static int copyCharsets(byte[] input, int offset, int nglyphs,
      ByteArrayOutputStream out) {
      int osize = out.size();

      // write format #
      out.write(input[offset]);

      switch(input[offset]) {
      // format 0
      case 0:
         out.write(input, offset + 1, 2 * (nglyphs - 1));
         break;
      // format 1, 2
      case 1:
      case 2:
         int nLeft = (input[offset] == 1) ? 1 : 2;

         nglyphs--; // excluding cid 0
         for(int i = offset + 1; nglyphs > 0; i += nLeft + 2) {
            out.write(input, i, nLeft + 2);
            nglyphs -= getNUMBER(input, i + 2, nLeft) + 1;
         }

         break;
      }

      return out.size() - osize;
   }

   /**
    * Copy an entire FDSelect section to output.
    * @return map of used FD.
    */
   static BitSet copyFDSelect(byte[] input, int offset, int nglyphs,
      ByteArrayOutputStream out, BitSet cidset) {
      BitSet fdset = new BitSet();

      // write format #
      out.write(input[offset]);

      switch(input[offset]) {
      // format 0
      case 0:
         // write the entire block
         out.write(input, offset + 1, nglyphs);

         // set fdset
         for(int i = 0; i < nglyphs; i++) {
            fdset.set(input[offset + i + 1]);
         }

         break;
      // format 3
      case 3:
         int nranges = getUSHORT(input, offset + 1);

         // write the entire block
         out.write(input, offset + 1, nranges * 3 + 4);

         offset += 3;

         // go through each range
         for(int i = 0; i < nranges; i++, offset += 3) {
            int first = getUSHORT(input, offset);
            int next = getUSHORT(input, offset + 3);
            int fd = input[offset + 2];

            for(int k = first; k < next; k++) {
               if(k == 0 || cidset.get(k)) {
                  fdset.set(fd);
                  break;
               }
            }
         }

         break;
      }

      return fdset;
   }

   /**
    * Copy an entire INDEX to output.
    * @return number of bytes copied.
    */
   static int copyIndex(byte[] input, int offset, ByteArrayOutputStream out)
      throws IOException {
      int cnt = getIndexSize(input, offset);

      out.write(input, offset, cnt);
      return cnt;
   }

   /**
    * Get the size of an index.
    * @return number of bytes.
    */
   static int getIndexSize(byte[] input, int offset) {
      int count = getUSHORT(input, offset);

      // empty index
      if(count == 0) {
         return 2;
      }

      int offsize = input[offset + 2];
      int lastoff = getNUMBER(input, offset + 3 + count * offsize, offsize);
      int cnt = lastoff + 2 + (count + 1) * offsize;

      return cnt;
   }

   /**
    * Get the number of objects in the INDEX.
    */
   static int getIndexCount(byte[] input, int offset) {
      return getUSHORT(input, offset);
   }

   /**
    * Get the starting offset and ending offset of the index item.
    * @return [0] is starting offset, [1] is ending offset.
    */
   static int[] getIndexOffsets(byte[] input, int offset, int n) {
      int count = getUSHORT(input, offset);

      // empty index
      if(count == 0) {
         return new int[] {0, 0};
      }

      int offsize = input[offset + 2];
      int off1 = getNUMBER(input, offset + 3 + n * offsize, offsize);
      int off2 = getNUMBER(input, offset + 3 + (n + 1) * offsize, offsize);
      int hdr = offset + 2 + (count + 1) * offsize;

      return new int[] {off1 + hdr, off2 + hdr};
   }

   /**
    * Update an item in an index. A new buffer is created if the index
    * size changes.
    */
   static byte[] setIndexValue(byte[] input, int[] off, int idx, byte[] val) {
      int[] itemoffs = getIndexOffsets(input, off[0], idx);

      if(itemoffs[1] - itemoffs[0] == val.length) {
         System.arraycopy(val, 0, input, itemoffs[0], val.length);
      }
      else {
         int size = getIndexSize(input, off[0]);
         int diff = val.length - (itemoffs[1] - itemoffs[0]);
         byte[] nbuf = new byte[size + diff];

         System.arraycopy(input, off[0], nbuf, 0, itemoffs[0] - off[0]);
         System.arraycopy(val, 0, nbuf, itemoffs[0] - off[0], val.length);
         System.arraycopy(input, itemoffs[1], nbuf,
            itemoffs[0] - off[0] + val.length, size + off[0] - itemoffs[1]);

         // update the offsets
         int cnt = getIndexCount(input, off[0]);
         int offsize = input[off[0] + 2];

         for(int i = 0, offi = 3; i < cnt + 1; i++, offi += offsize) {
            if(i > idx) {
               encodeNUMBER(
                  nbuf, offi, getNUMBER(input, off[0] + offi, offsize) + diff,
                  offsize);
            }
         }

         off[0] = 0;
         return nbuf;
      }

      return input;
   }

   /**
    * Return the next INDEX object with the specified elements.
    */
   static void extractIndex(byte[] index, int offset, BitSet cids,
      ByteArrayOutputStream outBuf) {
      try {
         DataOutputStream out = new DataOutputStream(outBuf);
         int count = getUSHORT(index, offset);

         if(count == 0) {
            out.writeShort(count);
            out.flush();
            return;
         }

         int offsize = index[offset + 2]; // offset size
         
         int offidx = offset + 3;
         int dataidx = offidx + offsize * (count + 1);
         ByteArrayOutputStream dataBuf = new ByteArrayOutputStream();

         // offset from byte preceding the data array
         ByteArrayOutputStream offstream = new ByteArrayOutputStream();
         DataOutputStream off = new DataOutputStream(offstream);
         
         writeOffset(off, 1, offsize);

         for(int i = 0; i < count; i++) {
            if(i == 0 || cids.get(i)) {
               int off1 = getNUMBER(index, offidx + i * offsize, offsize) - 1;
               int off2 =
                  getNUMBER(index, offidx + (i + 1) * offsize, offsize) - 1;

               dataBuf.write(index, dataidx + off1, off2 - off1);
            }
            else {
               // @by mikec, this code was added to work around a adobe8 bug
               // that will not read correct font info in multiple index
               // point to same glyph position.
               dataBuf.write(0x0F);
            }
            
            writeOffset(off, dataBuf.size() + 1, offsize);
         }
         
         off.flush();
            
         out.writeShort(count);
         out.writeByte(offsize); // offsize, use 2 byte offset
         out.write(offstream.toByteArray());
         out.write(dataBuf.toByteArray());
         out.flush();
      }
      catch(IOException e) {
         LOG.debug("Failed to extract index", e);
      }
   }

   static void writeOffset(DataOutputStream out, int value, int size) 
         throws IOException {
      for(int i = 0; i < size; i++) {
         int k = 8 * (size - i - 1);
         byte b = (byte) ((value >>> k) & 0xFF);
         
         out.writeByte(b);
      }
      
      out.flush();
   }
   
   /**
    * Encode number in bytes (big indian).
    * @param size number of bytes.
    */
   static void encodeNUMBER(byte[] buf, int idx, int val, int size) {
      for(int i = 0; i < size; i++) {
         buf[idx + size - i - 1] = (byte) ((val >>> (8*i)) & 0xFF);
      }
   }

   /**
    * Get an integer value of an operator in a DICT.
    * @return integer value.
    */
   static int getDictInt(byte[] input, int off1, int off2, String op) {
      int n = -1;

      for(int idx = off1; idx < off2;) {
         Op obj = getOp(input, idx);

         if(obj.getType() == Op.INT) {
            n = obj.getInt();
         }
         else if(obj.getType() == Op.OP && obj.getOp().equals(op)) {
            return n;
         }

         idx += obj.length();
      }

      return -1;
   }

   /**
    * Get an integer array value of an operator in a DICT.
    * @return integer array.
    */
   static int[] getDictIntArray(byte[] input, int off1, int off2, String op) {
      int[] n = new int[256];
      int cnt = 0;

      for(int idx = off1; idx < off2;) {
         Op obj = getOp(input, idx);

         if(obj.getType() == Op.INT) {
            n[cnt++] = obj.getInt();
         }
         else if(obj.getType() == Op.OP) {
            if(obj.getOp().equals(op)) {
               break;
            }

            // reset array
            cnt = 0;
         }

         idx += obj.length();
      }

      if(cnt > 0) {
         int[] rc = new int[cnt];

         System.arraycopy(n, 0, rc, 0, cnt);
         return rc;
      }

      return null;
   }

   /**
    * Update an integer value of an operator in a DICT.
    * @return input if the new value and old value has the same size (#bytes).
    * Otherwise it returns a new buffer with the updated value. If a new
    * buffer is returned, the offset values in the offs array are set to
    * [0 buf.length-1].
    */
   static byte[] setDictInt(byte[] input, int[] offs, String op, int val) {
      byte[] vbs = encodeDictInt(val);
      Op operand = null;

      for(int idx = offs[0]; idx < offs[1];) {
         Op obj = getOp(input, idx);

         if(obj.getType() == Op.INT) {
            operand = obj;
         } 
         // find place
         else if(obj.getType() == Op.OP && obj.getOp().equals(op)) {
            if(operand.length() == vbs.length) {
               for(int i = 0; i < vbs.length; i++) {
                  input[idx - vbs.length + i] = vbs[i];
               }

               return input;
            } 
            // allocate a new buffer if difference sizes
            else {
               idx -= operand.length();
               byte[] nbuf = new byte[offs[1] - offs[0] + vbs.length - 
                  operand.length()];

               System.arraycopy(input, offs[0], nbuf, 0, idx - offs[0]);
               System.arraycopy(vbs, 0, nbuf, idx - offs[0], vbs.length);
               System.arraycopy(input, idx + operand.length(), nbuf,
                  idx - offs[0] + vbs.length, offs[1] - idx - operand.length());
               offs[0] = 0;
               offs[1] = nbuf.length;
               return nbuf;
            }
         }

         idx += obj.length();
      }

      throw new RuntimeException("DICT operator not found: " + op);
   }

   private static int getUSHORT(byte[] buf, int idx) {
      return getNUMBER(buf, idx, 2);
   }

   /**
    * Get number from bytes.
    * @param size number of bytes to form a number, big-indian, <= 4.
    */
   private static int getNUMBER(byte[] buf, int idx, int size) {
      int val = 0;

      for(int i = 0; i < size; i++) {
         int b0 = (idx < buf.length) ? (buf[idx++] & 0xFF) : 0;

         val = (val << 8) | b0;
      }

      return val;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(CFF.class);
}

