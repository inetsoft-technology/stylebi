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
package inetsoft.util.script;

import inetsoft.report.StyleFont;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.VariableTable;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.lang.reflect.*;
import java.text.*;
import java.util.*;

/**
 * This is the wrapper for native java objects.
 */
public class JSObject extends NativeJavaObject {
   public JSObject(Scriptable scope, Object javaObject, Class staticType) {
      super(scope, javaObject, staticType);

      if(javaObject != null) {
         cls = javaObject.getClass();
      }
   }

   /**
    * Replace the java object in wrapper. This is used for optimization and should
    * not be used unless from a cache.
    */
   void setJavaObject(Object javaObject) {
      this.javaObject = javaObject;
   }

   /**
    * Remember the properties and type.
    */
   private void initProperties() {
      typemap = new HashMap<>();

      try {
         Method[] funcs = cls.getMethods();

         for(int i = 0; i < funcs.length; i++) {
            String name = funcs[i].getName();

            if(name.startsWith("set")) {
               Class[] args = funcs[i].getParameterTypes();
               name = name.substring(3);

               if(args.length == 1) {
                  typemap.put(name.toLowerCase(), args[0]);
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to init properties: " + cls, ex);
      }
   }

   @Override
   public Object get(String name, Scriptable start) {
      Object value = super.get(name, start);

      // fix for CategoricalShapeFrame.init() ambiguity
      if(value instanceof NativeJavaMethod && "init".equals(name)) {
         return new JSMethod((NativeJavaMethod) value);
      }

      return value;
   }

   @Override
   public void put(String name, Scriptable start, Object value) {
      if(typemap == null && cls != null) {
         initProperties();
      }

      Class type = typemap.get(name.toLowerCase());

      if(type != null) {
         value = convert(value, type);
      }

      super.put(name, start, value);
   }

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
         if(val instanceof NativeArray) {
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
            if((val instanceof String) && "".equals(((String) val).trim()) ||
               val instanceof UniqueTag)
            {
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
            if(val instanceof NativeObject) {
               Scriptable obj = (Scriptable) val;
               return new Insets(((Number) get(obj, "top")).intValue(),
                                 ((Number) get(obj, "left")).intValue(),
                                 ((Number) get(obj, "bottom")).intValue(),
                                 ((Number) get(obj, "right")).intValue());
            }

            double[] arr = splitN(val);

            return new Insets((int) arr[0], (int) arr[1], (int) arr[2],
               (int) arr[3]);
         }
         else if(type == Dimension.class) {
            if(val instanceof NativeObject) {
               Scriptable obj = (Scriptable) val;
               return new Dimension(((Number) get(obj, "width")).intValue(),
                                    ((Number) get(obj, "height")).intValue());
            }

            double[] arr = splitN(val);

            return new Dimension((int) arr[0], (int) arr[1]);
         }
         else if(type == Point.class) {
            if(val instanceof NativeObject) {
               Scriptable obj = (Scriptable) val;
               Number x = (Number) get(obj, "x");
               Number y = (Number) get(obj, "y");
               Number row = (Number) get(obj, "row");
               Number column = (Number) get(obj, "column");

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
            if(val instanceof NativeObject) {
               Scriptable obj = (Scriptable) val;
               String stype = (String) get(obj, "type");
               Number x = (Number) get(obj, "x");
               Number y = (Number) get(obj, "y");
               Number width = (Number) get(obj, "width");
               Number height = (Number) get(obj, "x");

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
         else if(type == double.class || type == Double.class) {
            return Double.valueOf(val.toString());
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
      else if(val instanceof Scriptable) {
         // @by larryl, we should create a new VariableTable here to be the
         // same as passing in the VariableTable directly. keep it this way
         // in 12.2 for backward compatibility. Consider changing in 12.3 or later.
         Scriptable arr = (Scriptable) val;

         for(int i = 0; arr.has(i, arr); i++) {
            Object item = arr.get(i, arr);

            if(item instanceof Scriptable) {
               Scriptable pair = (Scriptable) item;

               if(pair.has(1, pair)) {
                  Object key = JavaScriptEngine.unwrap(pair.get(0, pair));
                  Object value = JavaScriptEngine.unwrap(pair.get(1, pair));

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
      else if(val instanceof NativeObject) {
         Scriptable obj = (Scriptable) val;
         return new Color(((Number) get(obj, "r")).intValue(),
                          ((Number) get(obj, "g")).intValue(),
                          ((Number) get(obj, "b")).intValue());
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
      Object[] arr = null;

      if(str instanceof NativeArray) {
         NativeArray narr = (NativeArray) str;
         Object[] arr2 = new Object[(int) narr.jsGet_length()];

         for(int i = 0; i < arr2.length; i++) {
            arr2[i] = JavaScriptEngine.unwrap(narr.get(i, narr));
         }

         arr = arr2;
      }
      else if(str.getClass().isArray()) {
         arr = (Object[]) str;
      }
      else {
         arr = Tool.split(str.toString(), ',');
      }

      return arr;
   }

   /**
    * Get the value of a javascript object property.
    */
   public static Object get(Scriptable jobj, String name) {
      Object val = jobj.get(name, jobj);

      if(val == Undefined.instance || val == Scriptable.NOT_FOUND) {
         return null;
      }

      return JavaScriptEngine.unwrap(val);
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
      Object arr = null;

      if(str instanceof NativeArray) {
         NativeArray narr = (NativeArray) str;
         Object[] arr2 = new Object[(int) narr.jsGet_length()];

         for(int i = 0; i < arr2.length; i++) {
            arr2[i] = narr.get(i, narr);
         }

         arr = arr2;
      }
      else if(str.getClass().isArray()) {
         arr = str;
      }
      else {
         arr = Tool.split(str.toString(), ',');
      }

      double[] ns = new double[Array.getLength(arr)];

      for(int i = 0; i < ns.length; i++) {
         Object item = Array.get(arr, i);

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
      Object arr = split(str);
      String[] ns = new String[Array.getLength(arr)];

      for(int i = 0; i < ns.length; i++) {
         Object item = Array.get(arr, i);

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
      return (val instanceof NativeArray) ||
         val != null && val.getClass().isArray();
   }

   private Map<String, Class> typemap = null;
   private Class cls; // java object class

   private static final Logger LOG = LoggerFactory.getLogger(JSObject.class);
}
