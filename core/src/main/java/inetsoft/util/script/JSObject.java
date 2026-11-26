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
package inetsoft.util.script;

import inetsoft.report.StyleFont;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.VariableTable;
import inetsoft.util.CoreTool;
import inetsoft.util.DecimalPatternUtil;
import inetsoft.util.Tool;
import inetsoft.util.script.graal.ScriptValueConverter;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.lang.reflect.*;
import java.text.*;
import java.util.*;

/**
 * Static helpers for converting script (GraalJS guest) values to host Java
 * types. Previously this class also wrapped native Java objects for Rhino
 * (extends NativeJavaObject); under GraalJS the HostAccess layer auto-wraps
 * host objects, so only the conversion helpers remain.
 */
public class JSObject {
   /**
    * Convert a javascript object to the specified type.
    */
   public static Object convert(Object val, Class type) {
      val = JavaScriptEngine.unwrap(val);

      if(val == null) {
         return null;
      }

      Class vtype = val.getClass();

      if(type.isAssignableFrom(vtype)) {
         if(isArray(val)) {
            return split(val);
         }

         return val;
      }

      try {
         if(type == String.class) {
            if(val.getClass().isArray()) {
               return CoreTool.arrayToString(val);
            }

            return val.toString();
         }
         else if(type == Color.class) {
            if((val instanceof String) && "".equals(((String) val).trim())) {
               return null;
            }

            return processColor(val);
         }
         else if(type == Color[].class) {
            Object[] arr = split(val);
            Color[] colors = new Color[arr.length];

            for(int i = 0; i < arr.length; i++) {
               Object obj = convert(arr[i], Color.class);

               if(obj instanceof Color) {
                  colors[i] = (Color) obj;
               }
               else if(obj != null) {
                  return obj;
               }
            }

            return colors;
         }
         else if(type == Font.class) {
            return StyleFont.decode(val.toString());
         }
         else if(type == Insets.class) {
            if(isObject(val)) {
               return new Insets(((Number) get(val, "top")).intValue(),
                                 ((Number) get(val, "left")).intValue(),
                                 ((Number) get(val, "bottom")).intValue(),
                                 ((Number) get(val, "right")).intValue());
            }

            double[] arr = splitN(val);

            return new Insets((int) arr[0], (int) arr[1], (int) arr[2],
               (int) arr[3]);
         }
         else if(type == Dimension.class) {
            if(isObject(val)) {
               return new Dimension(((Number) get(val, "width")).intValue(),
                                    ((Number) get(val, "height")).intValue());
            }

            double[] arr = splitN(val);

            return new Dimension((int) arr[0], (int) arr[1]);
         }
         else if(type == Point.class) {
            if(isObject(val)) {
               Number x = (Number) get(val, "x");
               Number y = (Number) get(val, "y");
               Number row = (Number) get(val, "row");
               Number column = (Number) get(val, "column");

               if(x != null && y != null) {
                  return new Point(x.intValue(), y.intValue());
               }
               else {
                  return new Point(column.intValue(), row.intValue());
               }
            }

            double[] arr = splitN(val);

            return new Point((int) arr[0], (int) arr[1]);
         }
         else if(type == NumberFormat.class) {
            return new DecimalFormat(DecimalPatternUtil.normalizeEmptyPattern(val.toString()));
         }
         else if(type == DateFormat.class) {
            return Tool.createDateFormat(val.toString());
         }
         else if(type == java.text.MessageFormat.class) {
            return new MessageFormat(val.toString());
         }
         else if(type == Format.class) {
            String[] arr = splitStr(val);

            if(arr.length == 2) {
               return TableFormat.getFormat(arr[0], arr[1],
                                            Locale.getDefault());
            }
            else {
               String str = val.toString();

               if(str.indexOf('#') >= 0 || str.indexOf('0') >= 0) {
                  return new DecimalFormat(val.toString());
               }
               else {
                  return Tool.createDateFormat(str);
               }
            }
         }
         else if(type == Number.class) {
            return Double.valueOf(val.toString());
         }
         else if(type == Shape.class) {
            if(isObject(val)) {
               String stype = (String) get(val, "type");
               Number x = (Number) get(val, "x");
               Number y = (Number) get(val, "y");
               Number width = (Number) get(val, "width");
               Number height = (Number) get(val, "height");

               if(stype != null && stype.equals("ellipse")) {
                  return new Ellipse2D.Double(x.intValue(), y.intValue(),
                                              width.intValue(),
                                              height.intValue());
               }
               else {
                  return new Rectangle(x.intValue(), y.intValue(),
                                       width.intValue(),
                                       height.intValue());
               }
            }

            double[] arr = splitN(val);

            // circle
            if(arr.length == 3) {
               return new Ellipse2D.Double(arr[0], arr[1], arr[2] * 2,
                                           arr[2] * 2);
            }
            // rectangle
            else if(arr.length == 4) {
               return new Rectangle((int) arr[0], (int) arr[1], (int) arr[2],
                                    (int) arr[3]);
            }
            else {
               Polygon poly = new Polygon();

               for(int i = 0; i < arr.length - 1; i += 2) {
                  poly.addPoint((int) arr[i], (int) arr[i + 1]);
               }

               return poly;
            }
         }
         else if(type == boolean.class || type == Boolean.class) {
            if(val instanceof Number) {
               // follow the JavaScript rule: 0 and NaN are false, any other number is true
               double num = ((Number) val).doubleValue();
               return !(num == 0 || Double.isNaN(num));
            }

            return Boolean.valueOf(val.toString());
         }
         else if(type == double[].class) {
            return splitN(val);
         }
         else if(type == String[].class) {
            return splitStr(val);
         }
         else if(type == Object[].class) {
            return split(val);
         }
         else if(type == int.class || type == Integer.class) {
            if(val instanceof Number) {
               return Integer.valueOf(((Number) val).intValue());
            }

            return Integer.valueOf(val.toString());
         }
         else if(type == float.class || type == Float.class) {
            if(val instanceof Number) {
               return Float.valueOf(((Number) val).floatValue());
            }

            return Float.valueOf(val.toString());
         }
         else if(type == double.class || type == Double.class) {
            if(val instanceof Number) {
               return Double.valueOf(((Number) val).doubleValue());
            }

            return Double.valueOf(val.toString());
         }
         else if(type == int[].class) {
            return splitI(val);
         }
         else if(type == java.sql.Date.class) {
            return new java.sql.Date(((Date) val).getTime());
         }
         else if(type == java.sql.Timestamp.class) {
            return new java.sql.Timestamp(((Date) val).getTime());
         }
         else if(type == java.sql.Time.class) {
            return new java.sql.Time(((Date) val).getTime());
         }
      }
      catch(Exception ex) {
         // the message is shown to the script author (VSAScriptable.reportPutFailure), so it
         // names the value and the type in script terms; the Java detail goes to the log only
         String message = getConvertFailureMessage(val, describeType(type));
         String detail = message + " (value type " + val.getClass().getName() +
            ", target type " + type.getName() + ", cause " + ex + ")";

         if(LOG.isDebugEnabled()) {
            LOG.warn(detail, ex);
         }
         else {
            LOG.warn(detail);
         }

         throw new RuntimeException(message, ex);
      }

      return val;
   }

   /**
    * Get the text of a failed conversion of a script value, for the script author.
    *
    * @param val      the value that could not be converted.
    * @param typeDesc the expected type in words, e.g. from {@link #describeType(Class)}.
    */
   public static String getConvertFailureMessage(Object val, String typeDesc) {
      return "Failed to convert " + toScriptText(val) + " to " + typeDesc;
   }

   /**
    * Describe a conversion target type in words a script author understands, e.g.
    * "a size (two numbers: width and height, e.g. [100, 50])". A type without a
    * description is named by its simple class name.
    */
   public static String describeType(Class<?> type) {
      if(type == null) {
         return "a value";
      }

      if(type == String.class) {
         return "a text";
      }
      else if(type == Color.class) {
         return "a color (e.g. 'red', 0xFF0000 or [255, 0, 0])";
      }
      else if(type == Color[].class) {
         return "a list of colors (e.g. ['red', 0x00FF00])";
      }
      else if(type == Font.class) {
         return "a font (e.g. 'Arial-BOLD-12')";
      }
      else if(type == Insets.class) {
         return "four numbers (top, left, bottom and right, e.g. [1, 1, 1, 1])";
      }
      else if(type == Dimension.class) {
         return "a size (two numbers: width and height, e.g. [100, 50])";
      }
      else if(type == Point.class) {
         return "a position (two numbers: x and y, e.g. [10, 20])";
      }
      else if(type == Shape.class) {
         return "a shape (e.g. [x, y, width, height])";
      }
      else if(NumberFormat.class.isAssignableFrom(type)) {
         return "a number format pattern (e.g. '#,##0.00')";
      }
      else if(DateFormat.class.isAssignableFrom(type)) {
         return "a date format pattern (e.g. 'yyyy-MM-dd')";
      }
      else if(Format.class.isAssignableFrom(type)) {
         return "a format";
      }
      else if(type == int.class || type == Integer.class || type == long.class ||
         type == Long.class || type == short.class || type == Short.class ||
         type == byte.class || type == Byte.class || type == int[].class)
      {
         return type.isArray() ? "a list of integer numbers" : "an integer number";
      }
      else if(type == double.class || type == Double.class || type == float.class ||
         type == Float.class || Number.class.isAssignableFrom(type))
      {
         return "a number";
      }
      else if(type == double[].class) {
         return "a list of numbers";
      }
      else if(type == boolean.class || type == Boolean.class) {
         return "true or false";
      }
      else if(type == String[].class) {
         return "a list of texts";
      }
      else if(type == Object[].class) {
         return "a list";
      }
      else if(type == java.sql.Time.class) {
         return "a time";
      }
      else if(Date.class.isAssignableFrom(type)) {
         return "a date";
      }

      return type.getSimpleName();
   }

   /**
    * Write a script value the way a script author would write it: a text in quotes, a number
    * without a trailing ".0", an array as [a, b], a viewsheet object by its name. A Java object
    * is never shown by its default toString (class name and identity hash). Long texts and
    * arrays are shortened.
    */
   public static String toScriptText(Object val) {
      StringBuilder buf = new StringBuilder();
      appendScriptText(buf, val, 0);

      if(buf.length() > MAX_TEXT_LENGTH) {
         buf.setLength(MAX_TEXT_LENGTH);
         buf.append("...");
      }

      return buf.toString();
   }

   private static void appendScriptText(StringBuilder buf, Object val, int depth) {
      val = JavaScriptEngine.unwrap(val);

      if(val instanceof Value value) {
         if(value.isHostObject()) {
            val = value.asHostObject();
         }
         else if(value.isProxyObject()) {
            val = value.asProxyObject();
         }
         else {
            String str = value.toString();
            buf.append(str.length() > MAX_ELEMENT_LENGTH ?
                          str.substring(0, MAX_ELEMENT_LENGTH) + "..." : str);
            return;
         }
      }

      if(val == null) {
         buf.append("null");
      }
      else if(val instanceof CharSequence) {
         String str = val.toString();

         if(str.length() > MAX_ELEMENT_LENGTH) {
            str = str.substring(0, MAX_ELEMENT_LENGTH) + "...";
         }

         buf.append('\'').append(str).append('\'');
      }
      else if(val instanceof Double || val instanceof Float) {
         double num = ((Number) val).doubleValue();

         if(num == Math.rint(num) && !Double.isInfinite(num) && Math.abs(num) < 1e15) {
            buf.append((long) num);
         }
         else {
            buf.append(val);
         }
      }
      else if(val instanceof Number || val instanceof Boolean || val instanceof Date ||
         val instanceof Character || val instanceof Enum)
      {
         buf.append(val);
      }
      else if(val instanceof Color color) {
         buf.append(String.format("0x%06X", color.getRGB() & 0xFFFFFF));
      }
      else if(val instanceof Dimension dim) {
         buf.append('[').append(dim.width).append(", ").append(dim.height).append(']');
      }
      else if(val instanceof Point point) {
         buf.append('[').append(point.x).append(", ").append(point.y).append(']');
      }
      else if(val instanceof inetsoft.report.script.viewsheet.VSAScriptable scriptable &&
         scriptable.getAssembly() != null)
      {
         buf.append(scriptable.getAssembly());
      }
      else if(val.getClass().isArray() || val instanceof Collection) {
         if(depth >= MAX_DEPTH) {
            buf.append("[...]");
            return;
         }

         Object[] arr = val instanceof Collection ? ((Collection<?>) val).toArray() : split(val);
         buf.append('[');

         for(int i = 0; i < arr.length; i++) {
            if(i > 0) {
               buf.append(", ");
            }

            if(i >= MAX_ELEMENTS || buf.length() > MAX_TEXT_LENGTH) {
               buf.append("...");
               break;
            }

            appendScriptText(buf, arr[i], depth + 1);
         }

         buf.append(']');
      }
      else if(val instanceof Map) {
         if(depth >= MAX_DEPTH) {
            buf.append("{...}");
            return;
         }

         buf.append('{');
         int i = 0;

         for(Map.Entry<?, ?> entry : ((Map<?, ?>) val).entrySet()) {
            if(i > 0) {
               buf.append(", ");
            }

            if(i++ >= MAX_ELEMENTS || buf.length() > MAX_TEXT_LENGTH) {
               buf.append("...");
               break;
            }

            if(entry.getKey() instanceof CharSequence) {
               buf.append(entry.getKey());
            }
            else {
               appendScriptText(buf, entry.getKey(), depth + 1);
            }

            buf.append(": ");
            appendScriptText(buf, entry.getValue(), depth + 1);
         }

         buf.append('}');
      }
      else {
         // any other Java object: its toString may be the class name and identity hash
         buf.append("an object");
      }
   }

   /**
    * Convert a scriptable to a VariableTable.
    */
   public static VariableTable convertToVariableTable(Object val, VariableTable vars,
                                                      boolean keepNull)
   {
      // in case user sends the actual object
      if(JavaScriptEngine.unwrap(val) instanceof VariableTable) {
         vars = (VariableTable) JavaScriptEngine.unwrap(val);
      }
      else if(isArray(val)) {
         // @by larryl, we should create a new VariableTable here to be the
         // same as passing in the VariableTable directly. keep it this way
         // in 12.2 for backward compatibility. Consider changing in 12.3 or later.
         Object[] arr = split(val);

         for(Object item : arr) {
            if(isArray(item)) {
               Object[] pair = split(item);

               if(pair.length >= 2) {
                  Object key = JavaScriptEngine.unwrap(pair[0]);
                  Object value = JavaScriptEngine.unwrap(pair[1]);

                  if(key != null && (keepNull || value != null)) {
                     vars.put(key.toString(), value);
                  }
               }
            }
            else {
               LOG.error("Parameter should be passed in as array of pairs");
            }
         }
      }

      return vars;
   }

   /**
    * Process color when the type is color.
    */
   private static Object processColor(Object val) throws Exception {
      if(val instanceof String) {
         try {
            return new Color(Integer.decode(val.toString()).intValue());
         }
         catch(Exception ex) {
            // try as constant
            Field field = Color.class.getField((String) val);

            if(field != null) {
               return field.get(null);
            }
         }
      }
      else if(val instanceof Number) {
         return new Color(((Number) val).intValue());
      }
      else if(isObject(val)) {
         return new Color(((Number) get(val, "r")).intValue(),
                          ((Number) get(val, "g")).intValue(),
                          ((Number) get(val, "b")).intValue());
      }

      double[] arr = splitN(val);

      if(arr.length == 3) {
         return new Color((int) arr[0], (int) arr[1], (int) arr[2]);
      }

      return null;
   }

   /**
    * Split an object into an array. A text is split at commas. A single value that is not a
    * text (a Date, a Number, a Boolean, ...) is kept, with its type, as the one element.
    */
   public static Object[] split(Object str) {
      str = JavaScriptEngine.unwrap(str);

      if(str == null) {
         return new Object[0];
      }

      if(str instanceof Object[]) {
         return (Object[]) str;
      }
      else if(str.getClass().isArray()) {
         int len = Array.getLength(str);
         Object[] arr = new Object[len];

         for(int i = 0; i < len; i++) {
            arr[i] = Array.get(str, i);
         }

         return arr;
      }
      else if(str instanceof java.util.List) {
         return ((java.util.List<?>) str).toArray();
      }

      if(!(str instanceof CharSequence)) {
         return new Object[] { str };
      }

      return Tool.split(str.toString(), ',');
   }

   /**
    * Get the value of a javascript object property.
    */
   public static Object get(Object jobj, String name) {
      jobj = JavaScriptEngine.unwrap(jobj);

      if(jobj == null) {
         return null;
      }

      if(jobj instanceof Value) {
         Value v = (Value) jobj;

         if(v.hasMember(name)) {
            return ScriptValueConverter.toHost(v.getMember(name));
         }

         return null;
      }

      if(jobj instanceof Map) {
         return ((Map<?, ?>) jobj).get(name);
      }

      return null;
   }

   /**
    * Split string into integer array.
    */
   public static int[] splitI(Object str) {
      double[] arr = splitN(str);
      int[] iarr = new int[arr.length];

      for(int i = 0; i < arr.length; i++) {
         iarr[i] = (int) arr[i];
      }

      return iarr;
   }

   /**
    * Split string into number array.
    */
   public static double[] splitN(Object str) {
      Object[] arr = split(str);
      double[] ns = new double[arr.length];

      for(int i = 0; i < ns.length; i++) {
         Object item = arr[i];

         try {
            ns[i] = (item instanceof Number) ?
               ((Number) item).doubleValue() :
               Double.valueOf(item.toString()).doubleValue();
         }
         catch(Throwable e) {// ignored, defaults to 0
         }
      }

      return ns;
   }

   /**
    * Split string into string array.
    */
   public static String[] splitStr(Object str) {
      Object[] arr = split(str);
      String[] ns = new String[arr.length];

      for(int i = 0; i < ns.length; i++) {
         Object item = arr[i];

         if(item != null) {
            try {
               ns[i] = item.toString();
            }
            catch(Throwable e) {
               // ignored, defaults to 0
            }
         }
      }

      return ns;
   }

   /**
    * Check if a value is an array.
    */
   public static boolean isArray(Object val) {
      val = unwrapValue(val);

      if(val instanceof Value) {
         return ((Value) val).hasArrayElements();
      }

      return (val instanceof java.util.List) ||
         val != null && val.getClass().isArray();
   }

   /**
    * Check if a value is a script object with named members (a JS object
    * literal), as opposed to an array or primitive.
    */
   public static boolean isObject(Object val) {
      if(val instanceof Value) {
         Value v = (Value) val;
         return v.hasMembers() && !v.hasArrayElements();
      }

      return val instanceof Map;
   }

   /**
    * Get the member names of a script object.
    */
   public static Object[] getIds(Object jobj) {
      jobj = JavaScriptEngine.unwrap(jobj);

      if(jobj instanceof Value) {
         return ((Value) jobj).getMemberKeys().toArray();
      }

      if(jobj instanceof Map) {
         return ((Map<?, ?>) jobj).keySet().toArray();
      }

      return new Object[0];
   }

   private static Object unwrapValue(Object val) {
      if(val instanceof Value) {
         Value v = (Value) val;

         if(v.isHostObject()) {
            return v.asHostObject();
         }
      }

      return val;
   }

   // limits of toScriptText: whole text, one text value, array/map entries, nesting
   private static final int MAX_TEXT_LENGTH = 200;
   private static final int MAX_ELEMENT_LENGTH = 60;
   private static final int MAX_ELEMENTS = 10;
   private static final int MAX_DEPTH = 3;
   private static final Logger LOG = LoggerFactory.getLogger(JSObject.class);
}
