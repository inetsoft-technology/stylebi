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
            return new DecimalFormat(val.toString());
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
         String message = "Failed to convert (" + val + ") to [" + type + "]";

         if(LOG.isDebugEnabled()) {
            LOG.warn(message, ex);
         }
         else {
            LOG.warn(message);
         }

         throw new RuntimeException(message, ex);
      }

      return val;
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
    * Split an object into an array.
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

   private static final Logger LOG = LoggerFactory.getLogger(JSObject.class);
}
