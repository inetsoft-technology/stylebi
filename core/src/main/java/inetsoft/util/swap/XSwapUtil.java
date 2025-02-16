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
package inetsoft.util.swap;

import inetsoft.util.Catalog;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import net.jpountz.lz4.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XSwapUtil, the utility class.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public final class XSwapUtil {
   /**
    * Get the bit value of an index.
    * @param i the specified index.
    * @return the bit value of the specified index.
    */
   private static final long bit(int i) {
      return 1L << (i & 0x3f);
   }

   /**
    * Get the unit index at an index.
    * @param i the specified index.
    * @return the unit index of the specified index.
    */
   private static final int unitIndex(int i) {
      return i >> 6;
   }

   /**
    * Get the byte length of a boolean array.
    * @param arr the specified boolean array.
    * @param size the specified size.
    * @return the byte length of this boolean array, <tt>-1</tt> unknown.
    */
   public static final long length(boolean[] arr, char size) {
      return 2 + (size == 0 ? 0 : (long) (unitIndex(size - 1) + 1) << 3);
   }

   /**
    * Write a boolean array to a byte buffer.
    * @param arr the specified boolean arr.
    * @param size the specified size.
    * @param buf the specified byte buffer.
    */
   public static final void writeBoolean(boolean[] arr, char size,
                                         ByteBuffer buf) {
      writeChar(buf, size);
      long t = 0;

      for(int i = 0; i < size; i++) {
         if(arr[i]) {
            t |=  bit(i);
         }
         else {
            t &= ~bit(i);
         }

         int i2 = i + 1;

         if(((i2 & 0x3f) == 0) || i2 == size) {
            writeLong(buf, t);
            t = 0;
         }
      }
   }

   /**
    * Read a boolean array from a byte buffer.
    * @param buf the specified byte buffer.
    * @return the boolean array.
    */
   public static final boolean[] readBoolean(ByteBuffer buf) {
      int size = readChar(buf);
      boolean[] arr = new boolean[size];
      long t = 0;

      for(int i = 0; i < size; i++) {
         if((i & 0x3f) == 0) {
            t = readLong(buf);
         }

         arr[i] = (t & bit(i)) != 0;
      }

      return arr;
   }

   /**
    * Write a double value.
    * @param buf the specified byte buffer.
    * @param dval the specified double value.
    */
   public static final void writeDouble(ByteBuffer buf, double dval) {
      long c = Double.doubleToRawLongBits(dval);
      buf.put((byte) (c >> 56));
      buf.put((byte) (c >> 48));
      buf.put((byte) (c >> 40));
      buf.put((byte) (c >> 32));
      buf.put((byte) (c >> 24));
      buf.put((byte) (c >> 16));
      buf.put((byte) (c >> 8));
      buf.put((byte) c);
   }

   /**
    * Read a double value.
    * @param buf the specified byte buffer.
    * @return the double value.
    */
   public static final double readDouble(ByteBuffer buf) {
      long i1 = buf.get() & 0xff;
      long i2 = buf.get() & 0xff;
      long i3 = buf.get() & 0xff;
      long i4 = buf.get() & 0xff;
      long i5 = buf.get() & 0xff;
      long i6 = buf.get() & 0xff;
      long i7 = buf.get() & 0xff;
      long i8 = buf.get() & 0xff;
      long c = (i1 << 56) | (i2 << 48) | (i3 << 40) | (i4 << 32) | (i5 << 24) |
         (i6 << 16) | (i7 << 8) | i8;

      return Double.longBitsToDouble(c);
   }

   /**
    * Write a long value.
    * @param buf the specified byte buffer.
    * @param c the specified long value.
    */
   public static final void writeLong(ByteBuffer buf, long c) {
      buf.put((byte) (c >> 56));
      buf.put((byte) (c >> 48));
      buf.put((byte) (c >> 40));
      buf.put((byte) (c >> 32));
      buf.put((byte) (c >> 24));
      buf.put((byte) (c >> 16));
      buf.put((byte) (c >> 8));
      buf.put((byte) c);
   }

   /**
    * Read a long value.
    * @param buf the specified byte buffer.
    * @return the long value.
    */
   public static final long readLong(ByteBuffer buf) {
      long i1 = buf.get() & 0xff;
      long i2 = buf.get() & 0xff;
      long i3 = buf.get() & 0xff;
      long i4 = buf.get() & 0xff;
      long i5 = buf.get() & 0xff;
      long i6 = buf.get() & 0xff;
      long i7 = buf.get() & 0xff;
      long i8 = buf.get() & 0xff;

      return (i1 << 56) | (i2 << 48) | (i3 << 40) | (i4 << 32) | (i5 << 24) |
         (i6 << 16) | (i7 << 8) | i8;
   }

   /**
    * Write a float value.
    * @param buf the specified byte buffer.
    * @param fval the specified float value.
    */
   public static final void writeFloat(ByteBuffer buf, float fval) {
      int c = Float.floatToRawIntBits(fval);
      buf.put((byte) (c >> 24));
      buf.put((byte) (c >> 16));
      buf.put((byte) (c >> 8));
      buf.put((byte) c);
   }

   /**
    * Read a float value.
    * @param buf the specified byte buffer.
    * @return the float value.
    */
   public static final float readFloat(ByteBuffer buf) {
      int i1 = buf.get() & 0xff;
      int i2 = buf.get() & 0xff;
      int i3 = buf.get() & 0xff;
      int i4 = buf.get() & 0xff;
      int c = (i1 << 24) | (i2 << 16) | (i3 << 8) | i4;

      return Float.intBitsToFloat(c);
   }

   /**
    * Write an int value.
    * @param buf the specified byte buffer.
    * @param c the specified int value.
    */
   public static final void writeInt(ByteBuffer buf, int c) {
      buf.put((byte) (c >> 24));
      buf.put((byte) (c >> 16));
      buf.put((byte) (c >> 8));
      buf.put((byte) c);
   }

   /**
    * Read an int value.
    * @param buf the specified byte buffer.
    * @return the int value.
    */
   public static final int readInt(ByteBuffer buf) {
      int i1 = buf.get() & 0xff;
      int i2 = buf.get() & 0xff;
      int i3 = buf.get() & 0xff;
      int i4 = buf.get() & 0xff;

      return (i1 << 24) | (i2 << 16) | (i3 << 8) | i4;
   }

   /**
    * Get the byte length of a string.
    * @param str the specified string value.
    * @return the byte length of a string.
    */
   public static final int length(String str) {
      return str == null ? 2 : (str.length() * 3) + 2;
   }

   /**
    * Write a string value.
    * @param buf the specified byte buf.
    * @param str the specified string value.
    */
   public static final void writeString(ByteBuffer buf, String str) {
      if(str == null) {
         writeChar(buf, MAX_CHAR);
         return;
      }

      byte[] chars = str.getBytes(StandardCharsets.UTF_8);
      boolean exceedsMaxLen = chars.length > MAX_CHAR - 1;

      if(exceedsMaxLen) {
         byte[] newChars = new byte[MAX_CHAR - 1];
         System.arraycopy(chars, 0, newChars, 0, MAX_CHAR - 1);
         chars = newChars;
      }

      writeChar(buf, (char) chars.length);
      buf.put(chars);

      if(exceedsMaxLen) {
         LOG.warn(Catalog.getCatalog().getString("common.swap.string.subString", str, new String(chars)));
      }
   }

   /*
    * Read a string value.
    * @param buf the specified byte buffer.
    * @return the string value.
    */
   public static final String readString(ByteBuffer buf) {
      char len = readChar(buf);

      if(len == MAX_CHAR) {
         return null;
      }

      final String str;

      if(buf.hasArray()) {
         str = new String(buf.array(), buf.position(), len, StandardCharsets.UTF_8);
         XSwapUtil.position(buf, buf.position() + len);
      }
      else {
         byte[] arr = new byte[len];
         buf.get(arr);

         str = new String(arr, StandardCharsets.UTF_8);
      }

      // XStringColumn has a cache to share strings based on hit ratio. no need to intern here.
      //return str.intern();
      return str;
   }

   /**
    * Write a char value.
    * @param buf the specified byte buffer.
    * @param c the specified char value.
    */
   public static final void writeChar(ByteBuffer buf, char c) {
      buf.put((byte) (c >> 8));
      buf.put((byte) c);
   }

   /**
    * Read a char value.
    * @param buf the specified byte buffer.
    * @return the char value.
    */
   public static final char readChar(ByteBuffer buf) {
      int c1 = (int) buf.get();
      int c2 = (int) buf.get();
      return (char) ((c1 << 8) | (c2 & 0xff));
   }

   /**
    * Write a short value.
    * @param buf the specified byte buffer.
    * @param c the specified short value.
    */
   public static final void writeShort(ByteBuffer buf, short c) {
      buf.put((byte) (c >> 8));
      buf.put((byte) c);
   }

   /**
    * Read a short value.
    * @param buf the specified byte buffer.
    * @return the short value.
    */
   public static final short readShort(ByteBuffer buf) {
      int c1 = (int) buf.get();
      int c2 = (int) buf.get();
      return (short) ((c1 << 8) | (c2 & 0xff));
   }

   /**
    * Get the fastest factory.
    */
   private static LZ4Factory getLZ4Factory() {
      if(lz4Factory != null) {
         return lz4Factory;
      }

      synchronized(XSwapUtil.class) {
         try {
            lz4Factory = LZ4Factory.nativeInstance();
         }
         catch(Throwable ex) {
            lz4Factory = LZ4Factory.fastestInstance();
         }
      }

      return lz4Factory;
   }

   /**
    * Compress byte buffer.
    */
   public static ByteBuffer compressByteBuffer(ByteBuffer buf) {
      LZ4Compressor compressor = getLZ4Factory().fastCompressor();
      int offset = 0;
      int len = buf.remaining();
      byte[] output = null;
      int outputLen = 0;

      try {
         byte[] arr;

         if(buf.hasArray()) {
            arr = buf.array();
            offset = buf.arrayOffset();
         }
         else {
            arr = new byte[len];
            buf.get(arr);
         }

         int maxCompressedLength = compressor.maxCompressedLength(len);
         output = new byte[maxCompressedLength + 4];

         // magic number
         output[0] = 'l';
         output[1] = 'z';
         output[2] = '4';
         output[3] = 'j';

         // store uncompressed length
         output[4] = (byte) ((len >>> 24) & 0xFF);
         output[5] = (byte) ((len >>> 16) & 0xFF);
         output[6] = (byte) ((len >>> 8) & 0xFF);
         output[7] = (byte) (len & 0xFF);

         outputLen = compressor.compress(arr, offset, len, output, 8) + 8;
      }
      catch(Exception ex) {
         LOG.error("Failed to compress buffer", ex);
      }

      // should be able to reuse the buffer for compression
      if(outputLen < buf.capacity()) {
         XSwapUtil.clear(buf);
         buf.put(output, 0, outputLen);
         flip(buf);

         return buf;
      }

      return ByteBuffer.wrap(output, 0, outputLen);
   }

   /**
    * Uncompress byte buffer.
    */
   public static ByteBuffer uncompressByteBuffer(ByteBuffer buf) throws Exception {
      LZ4FastDecompressor decompressor = getLZ4Factory().fastDecompressor();

      byte[] arr = null;
      int offset = 0;

      if(buf.hasArray()) {
         arr = buf.array();
         offset = buf.arrayOffset();
      }
      else {
         arr = new byte[buf.remaining()];
         buf.get(arr);
      }

      // check magic number
      if(offset + 4 >= arr.length ||
         arr[offset] != 'l' || arr[offset + 1] != 'z' ||
         arr[offset + 2] != '4' || arr[offset + 3] != 'j')
      {
         return buf;
      }

      offset += 4;
      // uncompressed length
      int n1 = arr[offset];
      int n2 = arr[offset + 1];
      int n3 = arr[offset + 2];
      int n4 = arr[offset + 3];

      n1 = n1 & 0xff;
      n2 = n2 & 0xff;
      n3 = n3 & 0xff;
      n4 = n4 & 0xff;

      int len = (n1 << 24) | (n2 << 16) | (n3 << 8) | n4;
      byte[] output = new byte[len];
      decompressor.decompress(arr, offset + 4, output, 0, len);

      return ByteBuffer.wrap(output);
   }

   /**
    * Check if GC running in parallel.
    */
   public static boolean isParallelGC() {
      return parallelGC;
   }

   /**
    * Check if using concurrent mark sweep GC.
    */
   public static boolean isCmsGC() {
      return cmsGC;
   }

   /**
    * Get a shared Kryo object.
    */
   public static com.esotericsoftware.kryo.kryo5.Kryo getKryo() {
      com.esotericsoftware.kryo.kryo5.Kryo kryo = kryos.get();
      kryo.setRegistrationRequired(false);
      return kryo;
   }

   /**
    * Flip a buffer. Avoid problem with ByteBuffer.flip signature change.
    */
   public static Buffer flip(Buffer buf) {
      return buf.flip();
   }

   /**
    * Seek a buffer. Avoid problem with ByteBuffer.position signature change.
    */
   public static Buffer position(Buffer buf, int pos) {
      return buf.position(pos);
   }

   /**
    * Clear a buffer. Avoid problem with ByteBuffer.clear signature change.
    */
   public static Buffer clear(Buffer buf) {
      return buf.clear();
   }

   /**
    * Set a buffer's limit. Avoid problem with ByteBuffer.limit signature change.
    */
   public static Buffer limit(Buffer buf, int size) {
      return buf.limit(size);
   }

   /**
    * Rewinds a buffer. Avoid problem with ByteBuffer.rewind signature change.
    */
   public static Buffer rewind(Buffer buf) {
      return buf.rewind();
   }

   private static boolean cmsGC;
   private static boolean parallelGC;
   private static LZ4Factory lz4Factory = null;

   static {
      RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
      List<String> args = bean.getInputArguments();
      cmsGC = args.indexOf("-XX:+UseConcMarkSweepGC") >= 0;
      parallelGC = cmsGC || args.indexOf("-XX:+ExplicitGCInvokesConcurrent") >= 0;
   }

   private static ThreadLocal<com.esotericsoftware.kryo.kryo5.Kryo> kryos = new ThreadLocal<com.esotericsoftware.kryo.kryo5.Kryo>() {
      @Override
      protected com.esotericsoftware.kryo.kryo5.Kryo initialValue() {
         com.esotericsoftware.kryo.kryo5.Kryo kryo = new com.esotericsoftware.kryo.kryo5.Kryo();
         kryo.setClassLoader(Thread.currentThread().getContextClassLoader());
         return kryo;
      }
   };

   private static final char MAX_CHAR = '\uffff';
   private static final Logger LOG = LoggerFactory.getLogger(XSwapUtil.class);
}
