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

import inetsoft.util.graphics.ImageWrapper;

import java.awt.*;
import java.io.*;
import java.util.Date;

/**
 * Class for reading and writing data on ObjectOutputStream.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class DataRW {
   /**
    * Write one string value.
    * @param out the specified output stream.
    * @param val the specified string value.
    */
   public static final void writeString(OutputStream out, String val)
      throws Exception
   {
      writeChar(out, (char) val.length());

      for(int i = 0; i < val.length(); i++) {
         char c = val.charAt(i);
         writeChar(out, c);
      }
   }

   /**
    * Read one string value.
    * @param in the specified input stream.
    * @return the string value.
    */
   public static final String readString(InputStream in) throws Exception {
      int count = readChar(in);
      char[] chars = new char[count];

      for(int i = 0; i < chars.length; i++) {
         chars[i] = readChar(in);
      }

      return new String(chars);
   }

   /**
    * Write one long value.
    * @param out the specified output stream.
    * @param val the specified long value.
    */
   public static final void writeLong(OutputStream out, long val)
      throws Exception
   {
      out.write((int)((val >> 56) & 0xff));
      out.write((int)((val >> 48) & 0xff));
      out.write((int)((val >> 40) & 0xff));
      out.write((int)((val >> 32) & 0xff));
      out.write((int)((val >> 24) & 0xff));
      out.write((int)((val >> 16) & 0xff));
      out.write((int)((val >> 8) & 0xff));
      out.write((int)(val & 0xff));
   }

   /**
    * Read one long value.
    * @param in the specified input stream.
    * @return the long value.
    */
   public static final long readLong(InputStream in) throws Exception {
      long val = in.read();
      val = (val << 8) | in.read();
      val = (val << 8) | in.read();
      val = (val << 8) | in.read();
      val = (val << 8) | in.read();
      val = (val << 8) | in.read();
      val = (val << 8) | in.read();
      val = (val << 8) | in.read();

      return val;
   }

   /**
    * Write one int value.
    * @param out the specified output stream.
    * @param val the specified int value.
    */
   public static final void writeInt(OutputStream out, int val)
      throws Exception
   {
      out.write((int)((val >> 24) & 0xff));
      out.write((int)((val >> 16) & 0xff));
      out.write((int)((val >> 8) & 0xff));
      out.write((int)(val & 0xff));
   }

   /**
    * Read one int value.
    * @param in the specified input stream.
    * @return the in value.
    */
   public static final int readInt(InputStream in) throws Exception {
      int val = in.read();
      val = (val << 8) | in.read();
      val = (val << 8) | in.read();
      val = (val << 8) | in.read();

      return val;
   }

   /**
    * Write one char value.
    * @param out the specified output stream.
    * @param val the specified char value.
    */
   public static final void writeChar(OutputStream out, char val)
      throws Exception
   {
      out.write((val >> 8) & 0xff);
      out.write(val & 0xff);
   }

   /**
    * Read one char value.
    * @param in the specified input stream.
    * @return the char value.
    */
   public static final char readChar(InputStream in) throws Exception {
      int val = in.read();
      val = (val << 8) | in.read();

      return (char) val;
   }

   /**
    * Write one byte value.
    * @param out the specified output stream.
    * @param val the specified byte value.
    */
   public static final void writeByte(OutputStream out, byte val)
      throws Exception
   {
      out.write(val);
   }

   /**
    * Read one byte value.
    * @param in the specified input stream.
    * @return the byte value.
    */
   public static final byte readByte(InputStream in) throws Exception {
      return (byte) in.read();
   }

   /**
    * Write a data to output.
    */
   public abstract void write(ObjectOutputStream output, Object data)
      throws Exception;

   /**
    * Read a data from output.
    */
   public abstract Object read(ObjectInputStream inp)
      throws Exception;

   /**
    * Create DataRW for the type.
    */
   public static DataRW create(Class type) {
      if(type.equals(String.class)) {
         return STRING_RW;
      }
      else if(Number.class.isAssignableFrom(type)) {
         if(type == java.math.BigDecimal.class) {
            return BIGDECIMAL_RW;
         }
         else if(type == Double.class) {
            return DOUBLE_RW;
         }
         else if(type == Float.class) {
            return FLOAT_RW;
         }
         else if(type == Long.class) {
            return LONG_RW;
         }
         else if(type == Short.class) {
            return SHORT_RW;
         }
         else { //Integer or Byte or BigInteger
            return INT_RW;
         }
      }
      else if(java.sql.Date.class.isAssignableFrom(type)) {
         return DATE_RW;
      }
      else if(java.sql.Time.class.isAssignableFrom(type)) {
         return TIME_RW;
      }
      else if(java.sql.Timestamp.class.isAssignableFrom(type)) {
         return TIME_INSTANT_RW;
      }
      else if(java.util.Date.class.isAssignableFrom(type)) {
         return TIME_INSTANT_RW;
      }

      return DEFAULT_RW;
   }

   // big decimal reader & writer
   static final DataRW BIGDECIMAL_RW = new DataRW() {
      @Override
      public void write(ObjectOutputStream out, Object val) throws Exception {
         out.writeObject(val);
      }

      @Override
      public Object read(ObjectInputStream inp) throws Exception {
         return inp.readObject();
      }
   };

   // double reader & writer
   static final DataRW DOUBLE_RW = new DataRW() {
      @Override
      public void write(ObjectOutputStream out, Object val) throws Exception {
         if(val == null) {
            out.writeDouble(Tool.NULL_DOUBLE);
         }
         else {
            out.writeDouble(((Number) val).doubleValue());
         }
      }

      @Override
      public Object read(ObjectInputStream inp) throws Exception {
         double val = inp.readDouble();
         return (val == Tool.NULL_DOUBLE) ? null : Double.valueOf(val);
      }
   };

   // float reader & writer
   static final DataRW FLOAT_RW = new DataRW() {
      @Override
      public void write(ObjectOutputStream out, Object val) throws Exception {
         if(val == null) {
            out.writeFloat(Tool.NULL_FLOAT);
         }
         else {
            out.writeFloat(((Number) val).floatValue());
         }
      }

      @Override
      public Object read(ObjectInputStream inp) throws Exception {
         float val = inp.readFloat();
         return (val == Tool.NULL_FLOAT) ? null : Float.valueOf(val);
      }
   };

   // long reader & writer
   static final DataRW LONG_RW = new DataRW() {
      @Override
      public void write(ObjectOutputStream out, Object val) throws Exception {
         if(val == null) {
            out.writeLong(Tool.NULL_LONG);
         }
         else {
            out.writeLong(((Number) val).longValue());
         }
      }

      @Override
      public Object read(ObjectInputStream inp) throws Exception {
         long val = inp.readLong();
         return (val == Tool.NULL_LONG) ? null : Long.valueOf(val);
      }
   };

   // short reader & writer
   static final DataRW SHORT_RW = new DataRW() {
      @Override
      public void write(ObjectOutputStream out, Object val) throws Exception {
         if(val == null) {
            out.writeShort(Tool.NULL_SHORT);
         }
         else {
            out.writeShort(((Number) val).shortValue());
         }
      }

      @Override
      public Object read(ObjectInputStream inp) throws Exception {
         short val = inp.readShort();
         return (val == Tool.NULL_SHORT) ? null : Short.valueOf(val);
      }
   };

   // int reader & writer
   static final DataRW INT_RW = new DataRW() {
      @Override
      public void write(ObjectOutputStream out, Object val) throws Exception {
         if(val == null) {
            out.writeInt(Tool.NULL_INTEGER);
         }
         else {
            out.writeInt(((Number) val).intValue());
         }
      }

      @Override
      public Object read(ObjectInputStream inp) throws Exception {
         int val = inp.readInt();
         return (val == Tool.NULL_INTEGER) ? null : Integer.valueOf(val);
      }
   };

   // java.sql.Date reader & writer
   static final DataRW DATE_RW = new DataRW() {
      @Override
      public void write(ObjectOutputStream out, Object val) throws Exception {
         if(val == null) {
            out.writeLong(Tool.NULL_LONG);
         }
         else {
            long t = ((Date) val).getTime();
            t = t - (t % 1000);
            out.writeLong(t);
         }
      }

      @Override
      public Object read(ObjectInputStream inp) throws Exception {
         long val = inp.readLong();
         return (val == Tool.NULL_LONG) ? null : new java.sql.Date(val);
      }
   };

   // java.sql.Timestamp reader & writer
   static final DataRW TIME_INSTANT_RW = new DataRW() {
      @Override
      public void write(ObjectOutputStream out, Object val) throws Exception {
         if(val == null) {
            out.writeLong(Tool.NULL_LONG);
         }
         else {
            long t = ((Date) val).getTime();
            t = t - (t % 1000);
            out.writeLong(t);
         }
      }

      @Override
      public Object read(ObjectInputStream inp) throws Exception {
         long val = inp.readLong();
         return (val == Tool.NULL_LONG) ? null : new java.sql.Timestamp(val);
      }
   };

   // java.sql.Time reader & writer
   static final DataRW TIME_RW = new DataRW() {
      @Override
      public void write(ObjectOutputStream out, Object val) throws Exception {
         if(val == null) {
            out.writeLong(Tool.NULL_LONG);
         }
         else {
            long t = ((Date) val).getTime();
            t = t - (t % 1000);
            out.writeLong(t);
         }
      }

      @Override
      public Object read(ObjectInputStream inp) throws Exception {
         long val = inp.readLong();
         return (val == Tool.NULL_LONG) ? null : new java.sql.Time(val);
      }
   };

   // string reader & writer
   static final DataRW STRING_RW = new DataRW() {
      @Override
      public void write(ObjectOutputStream out, Object val) throws Exception {
         try {
            out.writeObject((String) val);
         }
         catch(ClassCastException ex) {
            if(val instanceof Image) {
               val = new ImageWrapper((Image) val);
               out.writeObject(val);
            }
            else {
               out.writeObject(val);
            }
         }
      }

      @Override
      public Object read(ObjectInputStream inp) throws Exception {
         Object obj = inp.readObject();

         try {
            return (String) obj;
         }
         catch(ClassCastException ex) {
            if(obj instanceof ImageWrapper) {
               return ((ImageWrapper) obj).unwrap();
            }
            else {
               return obj;
            }
         }
      }
   };

   // default reader & writer
   static final DataRW DEFAULT_RW = new DataRW() {
      @Override
      public void write(ObjectOutputStream out, Object val) throws Exception {
         if(val instanceof Image) {
            val = new ImageWrapper((Image) val);
         }

         out.writeObject(val);
      }

      @Override
      public Object read(ObjectInputStream inp) throws Exception {
         Object val = inp.readObject();

         if(val instanceof ImageWrapper) {
            val = ((ImageWrapper) val).unwrap();
         }

         return val;
      }
   };
}
