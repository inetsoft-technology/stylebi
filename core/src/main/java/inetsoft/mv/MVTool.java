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
package inetsoft.mv;

import inetsoft.mv.data.CompactString;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.uql.*;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Tool for MV.
 *
 * @author InetSoft Technology
 * @since  11.4.
 */
public class MVTool {
   /**
    * Constant indicates the data type - string.
    */
   public static final int STRING_TYPE = 0;
   /**
    * Constant indicates the data type - boolean.
    */
   public static final int BOOLEAN_TYPE = 1;
   /**
    * Constant indicates the data type - float.
    */
   public static final int FLOAT_TYPE = 2;
   /**
    * Constant indicates the data type - double.
    */
   public static final int DOUBLE_TYPE = 3;
   /**
    * Constant indicates the data type - char.
    */
   public static final int CHAR_TYPE = 4;
   /**
    * Constant indicates the data type - character.
    */
   public static final int CHARACTER_TYPE = 5;
   /**
    * Constant indicates the data type - byte.
    */
   public static final int BYTE_TYPE = 6;
   /**
    * Constant indicates the data type - short.
    */
   public static final int SHORT_TYPE = 7;
   /**
    * Constant indicates the data type - integer.
    */
   public static final int INTEGER_TYPE = 8;
   /**
    * Constant indicates the data type - long.
    */
   public static final int LONG_TYPE = 9;
   /**
    * Constant indicates the data type - timestamp.
    */
   public static final int TIME_INSTANT_TYPE = 10;
   /**
    * Constant indicates the data type - date.
    */
   public static final int DATE_TYPE = 11;
   /**
    * Constant indicates the data type - time.
    */
   public static final int TIME_TYPE = 12;

   /**
    * Creates a new <tt>MVCreator</tt> instance.
    *
    * @param def the view definition.
    *
    * @return a new creator.
    */
   public static MVCreator newMVCreator(MVDef def) {
      MVFactory factory = getMVFactory();

      if(factory == null) {
         throw new RuntimeException("MV plugin missing. " +
                                       "Check if the plugins directory is corrupt.");
      }

      MVCreator creator = factory.newCreator(def);
      return creator;
   }

   /**
    * Creates a new <tt>MVExecutor</tt> instance.
    *
    * @param table  the bound table assembly.
    * @param mvName the name of the materialized view.
    * @param vars   the query parameters.
    * @param user   a principal that identifies the user that is executing the query.
    *
    * @return a new executor.
    */
   public static MVExecutor newMVExecutor(TableAssembly table, String mvName,
                                          VariableTable vars, XPrincipal user)
   {
      MVFactory factory = getMVFactory();

      if(factory == null) {
         throw new RuntimeException("MV plugin missing. " +
                                       "Check if the plugins directory is corrupt.");
      }

      MVExecutor executor = factory.newExecutor(table, mvName, vars, user);
      return executor;
   }

   /**
    * Create a new MV session.
    */
   public static MVSession newMVSession() {
      MVFactory factory = getMVFactory();
      return (factory != null) ? factory.newSession() : null;
   }

   /**
    * Create a new MV message handler.
    */
   public static MessageListener newMVMessageHandler() {
      MVFactory factory = getMVFactory();
      return factory == null ? null : factory.newMessageHandler();
   }

   /**
    * Gets the MV factory instance.
    *
    * @return the MV factory.
    */
   private static MVFactory getMVFactory() {
      MVFactory factory;

      synchronized(LOCAL_MV_FACTORY) {
         factory = LOCAL_MV_FACTORY.get();

         if(factory == null) {
            factory = ServiceLoader.load(MVFactory.class).findFirst().orElse(null);
            LOCAL_MV_FACTORY.set(factory);
         }
      }

      return factory;
   }

   private static Method cleanerMethod;
   private static Method cleanMethod;
   private static Method invokeCleanerMethod;
   private static Object unsafe;
   private static final AtomicReference<MVFactory> LOCAL_MV_FACTORY =
      new AtomicReference<>(null);

   static {
      try {
         Class<?> directBufferClass = Class.forName("sun.nio.ch.DirectBuffer");
         cleanerMethod = directBufferClass.getMethod("cleaner");
         Class<?> cleanerClass = Class.forName("sun.misc.Cleaner");
         cleanMethod = cleanerClass.getMethod("clean");
      }
      catch(Exception ex) {
         // ignore
      }

      // java 9+
      try {
         Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
         Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
         unsafeField.setAccessible(true);
         unsafe = unsafeField.get(null);
         invokeCleanerMethod = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
      }
      catch(Exception ex) {
         // ignore
      }
   }

   /**
    * Unmap (close) a mapped buffer. The buffer can't be used after the call.
    */
   public static void unmap(MappedByteBuffer bb) {
      try {
         if(cleanMethod != null) {
            Object cleaner = cleanerMethod.invoke(bb);

            if(cleaner != null) {
               cleanMethod.invoke(cleaner);
            }
         }
         else if(unsafe != null && invokeCleanerMethod != null) {
            invokeCleanerMethod.invoke(unsafe, bb);
         }
      }
      catch(Exception ex) {
         // ignore
      }
   }

   /**
    * Copy all data from src to dest.
    */
   public static void copyChannel(ReadableByteChannel src, WritableByteChannel dest)
      throws IOException
   {
      ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);

      while(src.read(buffer) != -1) {
         // prepare the buffer to be drained
         XSwapUtil.flip(buffer);
         // write to the channel, may block
         dest.write(buffer);
         // If partial transfer, shift remainder down
         // If buffer is empty, same as doing clear()
         buffer.compact();
      }

      // EOF will leave buffer in fill state
      XSwapUtil.flip(buffer);

      // make sure the buffer is fully drained.
      while(buffer.hasRemaining()) {
         dest.write(buffer);
      }
   }

   /**
    * Put objects to new ByteBuffer,and return the buf.
    *
    * @param compactStr true to use CompactString for strings
    */
   public static ByteBuffer getObjectsByteBuffer(Object[] objs, boolean compactStr)
      throws IOException
   {
      int dataType = getDataType(objs);
      byte[] arr = getObjectsByteBuffer(objs, objs.length, dataType, compactStr);
      int len = arr.length;
      // direct byte buffers are less efficient than managed buffers when
      // dealing mostly with bytes or byte arrays
      ByteBuffer buf = ByteBuffer.allocate(13 + len);
      int size = objs.length;
      buf.put((byte) 0xff);
      buf.putInt(dataType);
      buf.putInt(size);
      buf.putInt(len);

      if(len != 0) {
         buf.put(arr);
      }

      XSwapUtil.flip(buf);
      return buf;
   }

   /**
    * Get the serialized objects in a compressed buffer.
    */
   private static byte[] getObjectsByteBuffer(Object[] objs, int length,
                                              int dataType, boolean compactStr)
         throws IOException
   {
      if(dataType == -1 || length == 0) {
         return new byte[0];
      }

      ByteArrayOutputStream buf = new ByteArrayOutputStream(1024);
      final OutputStream compress = Tool.createCompressOutputStream(buf);

      try(DataOutputStream output = new DataOutputStream(compress)) {
         for(int i = 0; i < length; i++) {
            Object obj = objs[i];
            boolean isnull = obj == null;

            switch(dataType) {
            case STRING_TYPE:
               String str = obj != null ? obj.toString() : null;

               if(compactStr && str != null) {
                  byte[] arr = str.getBytes(StandardCharsets.UTF_8);
                  output.writeInt(arr.length);
                  output.write(arr);
               }
               else {
                  Tool.writeUTF(output, str);
               }
               break;
            case BOOLEAN_TYPE:
               output.writeByte((byte) (isnull ? 3 : ((Boolean) obj ? 1 : 0)));
               break;
            case FLOAT_TYPE:
               output.writeInt(isnull ? Tool.NULL_INTEGER :
                                  Float.floatToIntBits(((Number) obj).floatValue()));
               break;
            case DOUBLE_TYPE:
               output.writeLong(isnull ? Tool.NULL_LONG :
                                   Double.doubleToLongBits(((Number) obj).doubleValue()));
               break;
            case CHAR_TYPE:
            case CHARACTER_TYPE:
               output.writeShort(isnull ? Tool.NULL_SHORT :
                                    (short) ((Character) obj).charValue());
               break;
            case BYTE_TYPE:
               output.writeByte(isnull ? Tool.NULL_BYTE : ((Number) obj).byteValue());
               break;
            case SHORT_TYPE:
               output.writeShort(isnull ? Tool.NULL_SHORT :
                                    ((Number) obj).shortValue());
               break;
            case INTEGER_TYPE:
               output.writeInt(isnull ? Tool.NULL_INTEGER :
                                  ((Number) obj).intValue());
               break;
            case LONG_TYPE:
               output.writeLong(isnull ? Tool.NULL_LONG : ((Number) obj).longValue());
               break;
            case TIME_INSTANT_TYPE:
               output.writeLong(isnull ? Tool.NULL_LONG : ((Date) obj).getTime());
               break;
            case DATE_TYPE:
               output.writeInt(isnull ? Tool.NULL_INTEGER :
                                  ((int) (((Date) obj).getTime() / 3600000L)));
               break;
            case TIME_TYPE:
               output.writeInt(isnull ? Tool.NULL_INTEGER :
                                  ((int) ((((Date) obj).getTime() % 86400000L) / 1000L)));
               break;
            default:
               throw new RuntimeException("Unsupport data type: " + dataType +
                                             " found!");
            }
         }
      }

      return buf.toByteArray();
   }

   /**
    * Read objects from file.
    * @param compactStr true to use CompactString for strings
    */
   public static Object[] readObjects(ReadableByteChannel channel,
                                      boolean compactStr)
      throws IOException
   {
      ByteBuffer buf = ByteBuffer.allocate(13);
      channel.read(buf);
      XSwapUtil.flip(buf);
      int chk = buf.get();
      int dataType = buf.getInt();
      int size = buf.getInt();
      int len = buf.getInt();
      // direct byte buffers are less efficient than managed buffers when
      // dealing mostly with bytes or byte arrays
      buf = ByteBuffer.allocate(len);
      channel.read(buf);
      XSwapUtil.flip(buf);

      return readObjects(buf, size, dataType, compactStr);
   }

   /**
    * Read objects from buffer.
    * @param size the number of objects to read.
    */
   public static Object[] readObjects(ByteBuffer buf, int size,
                                      int dataType, boolean compactStr)
   {
      Object[] values = new Object[size];

      if(buf.remaining() == 0) {
         return values;
      }

      try {
         byte[] arr = buf.array();
         ByteArrayInputStream binput = new ByteArrayInputStream(arr);
         InputStream inflater = Tool.createUncompressInputStream(binput);
         DataInputStream input = new DataInputStream(inflater);

         for(int i = 0; i < size; i++) {
            Object obj = null;
            byte b; int ival; long lval; short sval;

            switch(dataType) {
            case STRING_TYPE:
               if(compactStr) {
                  int strlen = input.readInt();

                  if(strlen >= 0) {
                     byte[] bytes = new byte[strlen];
                     input.readFully(bytes);
                     obj = new CompactString(bytes);
                  }
               }
               else {
                  obj = Tool.readUTF(input);
               }
               break;
            case BOOLEAN_TYPE:
               b = input.readByte();
               switch(b) {
               case 3:
                  obj = null;
                  break;
               case 1:
                  obj = Boolean.TRUE;
                  break;
               default:
                  obj = Boolean.FALSE;
               }
               break;
            case FLOAT_TYPE:
               ival = input.readInt();
               obj = ival == Tool.NULL_INTEGER ? null : Float.intBitsToFloat(ival);
               break;
            case DOUBLE_TYPE:
               lval = input.readLong();
               obj = lval == Tool.NULL_LONG ? null : Double.longBitsToDouble(lval);
               break;
            case CHAR_TYPE:
            case CHARACTER_TYPE:
               sval = input.readShort();
               obj = sval == Tool.NULL_SHORT ? null : (char) sval;
               break;
            case BYTE_TYPE:
               b = input.readByte();
               obj = b == Tool.NULL_BYTE ? null : b;
               break;
            case SHORT_TYPE:
               sval = input.readShort();
               obj = sval == Tool.NULL_SHORT ? null : sval;
               break;
            case INTEGER_TYPE:
               ival = input.readInt();
               obj = ival == Tool.NULL_INTEGER ? null : ival;
               break;
            case LONG_TYPE:
               lval = input.readLong();
               obj = lval == Tool.NULL_LONG ? null : lval;
               break;
            case TIME_INSTANT_TYPE:
               lval = input.readLong();
               obj = lval == Tool.NULL_LONG ? null : new Timestamp(lval);
               break;
            case DATE_TYPE:
               ival = input.readInt();
               obj = ival == Tool.NULL_INTEGER ? null :
                  new java.sql.Date(ival * 3600000L);
               break;
            case TIME_TYPE:
               ival = input.readInt();
               obj = ival == Tool.NULL_INTEGER ? null : new Time(ival * 1000L);
               break;
            default:
               throw new RuntimeException("Unsupport data type: " + dataType  +
                  " found!");
            }

            values[i] = obj;
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to read objects", ex);
      }

      return values;
   }

   /**
    * Get data type int value of a class.
    */
   public static int getDataTypeIndex(Class cls) {
      String type = Tool.getDataType(cls);

      switch(type) {
      case CoreTool.STRING:
         return STRING_TYPE;
      case CoreTool.BOOLEAN:
         return BOOLEAN_TYPE;
      case CoreTool.FLOAT:
         return FLOAT_TYPE;
      case CoreTool.DOUBLE:
         return DOUBLE_TYPE;
      case CoreTool.CHAR:
         return CHAR_TYPE;
      case CoreTool.CHARACTER:
         return CHARACTER_TYPE;
      case CoreTool.BYTE:
         return BYTE_TYPE;
      case CoreTool.SHORT:
         return SHORT_TYPE;
      case CoreTool.INTEGER:
         return INTEGER_TYPE;
      case CoreTool.LONG:
         return LONG_TYPE;
      case CoreTool.TIME_INSTANT:
         return TIME_INSTANT_TYPE;
      case CoreTool.DATE:
         return DATE_TYPE;
      case CoreTool.TIME:
         return TIME_TYPE;
      }

      throw new RuntimeException("Unsupported data type: " + cls + " found!");
   }

   private static int getDataType(Object[] objs) {
      for(Object obj : objs) {
         if(obj != null) {
            return getDataTypeIndex(obj.getClass());
         }
      }

      return -1;
   }

   /**
    * Check if the MV update condition on the table has been moved to
    * the parent.
    */
   public static boolean isMVConditionMoved(TableAssembly tbl) {
      return "true".equals(tbl.getProperty("mv.cond.moved"));
   }

   /**
    * Set whether the MV update condition on the table has been moved to
    * the parent.
    */
   public static void setMVConditionMoved(TableAssembly tbl, boolean moved) {
      tbl.setProperty("mv.cond.moved", moved + "");
   }

   /**
    * Check if the MV update condition is moved to this table from
    * the child in the transformation.
    */
   public static boolean isMVConditionParent(TableAssembly tbl) {
      return "true".equals(tbl.getProperty("mv.cond.parent"));
   }

   /**
    * Set whether the MV update condition is moved to this table from
    * the child in the transformation.
    */
   public static void setMVConditionParent(TableAssembly tbl, boolean moved) {
      tbl.setProperty("mv.cond.parent", moved + "");
   }

   /**
    * Check if the expression can be materialized.
    */
   public static boolean isExpressionMVCompatible(String script) {
      if(script != null && (script.contains("@nomv") || script.contains("parameter."))) {
         if(NO_MV_PATTERN.matcher(script).find() || PARAMTER_PATTERN.matcher(script).find()) {
            return false;
         }
      }
      return true;
   }

   /**
    * Check if expression contains reference to aggregate field.
    */
   public static boolean containsAggregateField(String script) {
      if(script.contains("field['")) {
         return Arrays.stream(AGGREGATE_FIELDS).anyMatch(f -> script.contains(f));
      }

      return false;
   }

   private static final String[] AGGREGATE_FIELDS = new String[] {
      "field['" + XConstants.AVERAGE_FORMULA + "(",
      "field['" + XConstants.COUNT_FORMULA + "(",
      "field['" + XConstants.DISTINCTCOUNT_FORMULA + "(",
      "field['" + XConstants.MAX_FORMULA + "(",
      "field['" + XConstants.MIN_FORMULA + "(",
      "field['" + XConstants.PRODUCT_FORMULA + "(",
      "field['" + XConstants.SUM_FORMULA + "(",
      "field['" + XConstants.CONCAT_FORMULA + "(",
      "field['" + XConstants.STANDARDDEVIATION_FORMULA + "(",
      "field['" + XConstants.VARIANCE_FORMULA + "(",
      "field['" + XConstants.POPULATIONSTANDARDDEVIATION_FORMULA + "(",
      "field['" + XConstants.POPULATIONVARIANCE_FORMULA + "(",
      "field['" + XConstants.CORRELATION_FORMULA + "(",
      "field['" + XConstants.COVARIANCE_FORMULA + "(",
      "field['" + XConstants.MEDIAN_FORMULA + "(",
      "field['" + XConstants.NTHLARGEST_FORMULA + "(",
      "field['" + XConstants.NTHMOSTFREQUENT_FORMULA + "(",
      "field['" + XConstants.NTHSMALLEST_FORMULA + "(",
      "field['" + XConstants.PTHPERCENTILE_FORMULA + "(",
      "field['" + XConstants.WEIGHTEDAVERAGE_FORMULA + "(",
      "field['" + XConstants.SUMWT_FORMULA + "(",
      "field['" + XConstants.SUMSQ_FORMULA + "(",
      "field['" + XConstants.FIRST_FORMULA + "(",
      "field['" + XConstants.LAST_FORMULA + "(",
      "field['" + XConstants.MODE_FORMULA + "("};

   private static final Logger LOG = LoggerFactory.getLogger(MVTool.class);
   private static final Pattern NO_MV_PATTERN = Pattern.compile("\\/\\*\\s+@nomv ");
   private static final Pattern PARAMTER_PATTERN = Pattern.compile("\\bparameter\\.");
}
