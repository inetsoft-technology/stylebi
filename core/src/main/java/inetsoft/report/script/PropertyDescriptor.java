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
package inetsoft.report.script;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.*;
import inetsoft.report.internal.BaseElement;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.XTable;
import inetsoft.uql.util.XTableDataSet;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.GradientColor;
import inetsoft.util.script.*;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Describe a property as a pair of getter and setter methods.
 */
public class PropertyDescriptor {
   /**
    * Create a property descriptor.
    * @param getter the method to call on the element to get property value.
    * @param setter the method to call on the element to set property value.
    * @param type the type of the property.
    */
   public PropertyDescriptor(Class cls, String getter, String setter, Class type) {
      this.getter = methods.getMethod(cls, getter);
      this.setter = methods.getMethod(cls, setter, type);
      this.type = type;
   }

   /**
    * Create a property descriptor.
    * @param getter the method to call on the element to get property value.
    * @param setter the method to call on the element to set property value.
    * @param type the type of the property.
    * @param object the object to use to call getter and setter
    * instead of element.
    */
   public PropertyDescriptor(Class cls, String getter, String setter, Class type, Object object) {
      this(cls, getter, setter, type);
      this.object = object;
   }

   /**
    * Create a property descriptor.
    * @param setter the method to call on the element to set property value.
    * @param type the type of the property.
    * @param value the property value.
    */
   public PropertyDescriptor(Class cls, String setter, Class type, Object value) {
      this(cls, null, setter, type);
      this.value = value;
   }

   /**
    * Create a property descriptor.
    * @param getter the method to call on the element to get property value.
    * @param setter the method to call on the element to set property value.
    * @param type the type of the property.
    * @param params additional parameter to use in getter and setter.
    */
   public PropertyDescriptor(Class cls, String getter, String setter, Class type, Object[] params)
      throws NoSuchMethodException
   {
      Class[] getPs = new Class[params.length];
      Class[] setPs = new Class[params.length + 1];

      for(int i = 0; i < params.length; i++) {
         getPs[i] = params[i].getClass();
         setPs[i] = params[i].getClass();
      }

      setPs[params.length] = type;

      this.getter = methods.getMethod(cls, getter, getPs);
      this.setter = methods.getMethod(cls, setter, setPs);
      this.type = type;
      this.params = params;
   }

   /**
    * Get property value.
    */
   public Object get(Object element) throws Exception {
      if(value != null) {
         return value;
      }
      else if(params != null) {
         return getter.invoke(element, params);
      }
      else if(object == null) {
         return getter.invoke(element, new Object[] {});
      }
      else {
         return getter.invoke(object, new Object[] {});
      }
   }

   /**
    * Set property value.
    * @return the value of the property.
    */
   public Object set(Object element, Object value) throws Exception {
      if(setter == null) {
         LOG.warn("No setter found for [" + element + "] = " + value);
         return value;
      }

      return set(setter, element, value);
   }

   /**
    * Checks whether or not the value requires conversion.
    *
    * @param value the value to check
    * @param setter the setter of the value
    * @return true if the value needs conversion, false otherwise
    */
   protected boolean isConvertValue(Object value, Method setter) {
      return true;
   }

   /**
    * Set the value by calling the setter method with the value.
    */
   protected Object set(Method setter, Object element, Object value) throws Exception {
      // @by larryl, one common mistake in scripting is running a script that
      // changes an element that has already been printed, in which case the
      // change would not be seen in the output. warn user here.
      if(element instanceof BaseElement &&
         ((BaseElement) element).getElementInfo().isPrinted())
      {
         LOG.warn("Element already printed, " +
                  "change will not take effect until next refresh: " +
                  setter.getName() + " [" + element + "] = " + value);
      }

      Object value2 = isConvertValue(value, setter) ? convert(value) : value;

      if(params != null) {
         Object[] params2 = new Object[params.length + 1];

         System.arraycopy(params, 0, params2, 0, params.length);
         params2[params.length] = value2;
         setter.invoke(element, params2);
      }
      else if(object == null) {
         setter.invoke(element, value2);
      }
      else {
         setter.invoke(object, value2);
      }

      return value2;
   }

   /**
    * Get the type of this property.
    */
   public Class getType() {
      return type;
   }

   /**
    * Convert a javascript object to this property type.
    */
   public Object convert(Object val) {
      return convert(val, type);
   }

   /**
    * Convert a javascript object to the specified type.
    */
   public static Object convert(Object val, Class type) {
      val = JSObject.convert(val, type);

      if(val == null) {
         // passing null to boolean causes an exception (47206).
         if(type == boolean.class) {
            val = false;
         }

         return val;
      }

      Class vtype = val.getClass();

      if(type.isAssignableFrom(vtype)) {
         return val;
      }

      try {
         if(type == Position.class) {
            if(val instanceof NativeObject) {
               Scriptable obj = (Scriptable) val;
               return new Position(
                  ((Number) JSObject.get(obj, "x")).doubleValue(),
                  ((Number) JSObject.get(obj, "y")).doubleValue());
            }

            double[] arr = JSObject.splitN(val);

            return new Position(arr[0], arr[1]);
         }
         else if(type == Size.class) {
            if(val instanceof NativeObject) {
               Scriptable obj = (Scriptable) val;
               return new Size(
                  ((Number) JSObject.get(obj, "width")).doubleValue(),
                  ((Number) JSObject.get(obj, "height")).doubleValue());
            }

            double[] arr = JSObject.splitN(val);

            return new Size(arr[0], arr[1]);
         }
         else if(type == Hyperlink.class) {
            if(JSObject.isArray(val)) {
               Object[] arr = JSObject.split(val);

               if(arr.length > 0) {
                  Hyperlink link = new Hyperlink(arr[0].toString(), true);

                  // second is parameter
                  if(arr.length > 1) {
                     Object[] pairs = JSObject.split(arr[1]);

                     // [link, [name, value]]
                     if(pairs.length == 2 && !JSObject.isArray(pairs[0])) {
                        link.setParameterField(pairs[0].toString(),
                           pairs[1].toString());
                     }
                     // [link, [[name, value], [name, value]]]
                     else {
                        for(int i = 0; i < pairs.length; i++) {
                           Object[] pair = JSObject.split(pairs[i]);

                           if(pair.length > 1) {
                              link.setParameterField(pair[0].toString(),
                                 pair[1].toString());
                           }
                        }
                     }

                     if(arr.length > 2) {
                        if(!Boolean.valueOf(arr[2].toString()).booleanValue()) {
                           link.setLinkType(Hyperlink.WEB_LINK);
                        }
                     }

                     if(arr.length > 3) {
                        link.setTargetFrame(arr[3].toString());
                     }
                  }

                  return link;
               }
            }
            else if(val instanceof NativeObject) {
               Scriptable obj = (Scriptable) val;
               String link = (String) JSObject.get(obj, "link");
               String target = (String) JSObject.get(obj, "target");
               String tooltip = (String) JSObject.get(obj, "tooltip");
               // report, web, archive
               String ltype = (String) JSObject.get(obj, "type");
               Boolean sendParams = (Boolean)
                  JSObject.get(obj, "sendReportParameters");
               Object[] ids = obj.getIds();
               Hyperlink hyper = new Hyperlink(link, true);

               for(int k = 0; k < ids.length; k++) {
                  if(ids[k] instanceof String) {
                     String name = (String) ids[k];
                     hyper.setParameterField(name,
                                             (String) JSObject.get(obj, name));
                  }
               }

               if(target != null) {
                  hyper.setTargetFrame(target);
               }

               if(tooltip != null) {
                  hyper.setToolTip(tooltip);
               }

               if(ltype != null) {
                  if(ltype.equals("web")) {
                     hyper.setLinkType(Hyperlink.WEB_LINK);
                  }
               }

               if(sendParams != null) {
                  hyper.setSendReportParameters(sendParams.booleanValue());
               }

               return hyper;
            }

            return new Hyperlink(val.toString(), true, true);
         }
         else if(type == Hyperlink.Ref.class) {
            if(JSObject.isArray(val)) {
               Object[] arr = JSObject.split(val);

               if(arr.length > 0) {
                  Hyperlink.Ref link =
                     new Hyperlink.Ref(arr[0].toString(), true);

                  // second is parameter
                  if(arr.length > 1) {
                     Object[] pairs = JSObject.split(arr[1]);

                     if(pairs.length == 2 && !JSObject.isArray(pairs[0])) {
                        link.setParameter(pairs[0].toString(),
                           JavaScriptEngine.unwrap(pairs[1]));
                     }
                     // [link, [[name, value], [name, value]]]
                     else {
                        for(int i = 0; i < pairs.length; i++) {
                           Object[] pair = JSObject.split(pairs[i]);

                           if(pair.length > 1) {
                              link.setParameter(pair[0].toString(),
                                 JavaScriptEngine.unwrap(pair[1]));
                           }
                        }
                     }

                     if(arr.length > 2) {
                        if(!Boolean.valueOf(arr[2].toString()).booleanValue()) {
                           link.setLinkType(Hyperlink.WEB_LINK);
                        }
                     }

                     if(arr.length > 3) {
                        link.setTargetFrame(arr[3].toString());
                     }
                  }

                  return link;
               }
            }
            else if(val instanceof NativeObject) {
               Scriptable obj = (Scriptable) val;
               String link = (String) JSObject.get(obj, "link");
               String target = (String) JSObject.get(obj, "target");
               String tooltip = (String) JSObject.get(obj, "tooltip");
               // report, web, archive
               String ltype = (String) JSObject.get(obj, "type");
               Boolean sendParams = (Boolean)
                  JSObject.get(obj, "sendReportParameters");
               Object[] ids = obj.getIds();
               Hyperlink.Ref hyper = new Hyperlink.Ref(link, true);

               for(int k = 0; k < ids.length; k++) {
                  if(ids[k] instanceof String) {
                     String name = (String) ids[k];
                     hyper.setParameter(name, JSObject.get(obj, name));
                  }
               }

               if(target != null) {
                  hyper.setTargetFrame(target);
               }

               if(tooltip != null) {
                  hyper.setToolTip(tooltip);
               }

               if(ltype != null) {
                  if(ltype.equals("web")) {
                     hyper.setLinkType(Hyperlink.WEB_LINK);
                  }
               }

               if(sendParams != null) {
                  hyper.setSendReportParameters(sendParams.booleanValue());
               }

               return hyper;
            }
            else if(val instanceof Hyperlink) {
               return new Hyperlink.Ref((Hyperlink) val);
            }

            return new Hyperlink.Ref(val.toString(), true);
         }
         else if(type == TableLens.class) {
            if(val instanceof TableArray) {
               return ((TableArray) val).getElementTable();
            }
            else if(val instanceof Object[]) {
               Object value = JavaScriptEngine.unwrap(val);
               Object[][] array = to2DArray(value);

               if(array != null) {
                  return new DefaultTableLens(array);
               }
            }

            return null;
         }
         else if(type == PresenterRef.class) {
            PresenterRef ref = new PresenterRef();

            if(JSObject.isArray(val)) {
               Object[] arr = JSObject.split(val);

               if(arr.length > 0) {
                  ref.setName(arr[0].toString());

                  // second is parameter
                  if(arr.length > 1) {
                     Object[] pairs = JSObject.split(arr[1]);

                     if(pairs.length == 2 && !JSObject.isArray(pairs[0])) {
                        ref.setParameter(pairs[0].toString(),
                                         JavaScriptEngine.unwrap(pairs[1]));
                     }
                     // [link, [[name, value], [name, value]]]
                     else {
                        for(int i = 0; i < pairs.length; i++) {
                           Object[] pair = JSObject.split(pairs[i]);

                           ref.setParameter(pair[0].toString(),
                                            JavaScriptEngine.unwrap(pair[1]));
                        }
                     }
                  }
               }
            }
            else if(val instanceof String) {
               ref.setName((String) val);
            }

            return ref;
         }
         else if(type == Presenter.class) {
            PresenterRef ref = (PresenterRef) convert(val, PresenterRef.class);

            try {
               return ref.createPresenter();
            }
            catch(Exception ex2) {
               LOG.error("Failed to create presenter for: " + val, ex2);
            }

            return null;
         }
         else if(type == Image.class) {
            return ReportJavaScriptEngine.getImage(val);
         }
         else if(type == BorderColors.class) {
            if(JSObject.isArray(val)) {
               Object[] arr = JSObject.split(val);
               Color[] color = new Color[arr.length];
               BorderColors borderColor = new BorderColors();

               for(int i = 0; i < arr.length; i++) {
                  color[i] = (Color) JSObject.convert(arr[i], Color.class);
                  borderColor.setBorderColor(i, color[i]);
               }

               return borderColor;
            }
         }
         else if(type == DataSet.class) {
            return createDataSet(val);
         }
         else if(type == XFormatInfo.class) {
            if(JSObject.isArray(val)) {
               Object[] objs = JSObject.split(val);
               XFormatInfo info = new XFormatInfo();

               if(objs.length >= 1) {
                  info.setFormat(objs[0].toString());
               }

               if(objs.length >= 2) {
                  info.setFormatSpec(objs[1].toString());
               }

               return info;
            }
         }
         else if(type == GradientColor.class) {
            if(JSObject.isArray(val)) {
               Object[] objs = JSObject.split(val);

               GradientColor gradientColor = new GradientColor();
               List<GradientColor.ColorStop> colors = new ArrayList<>();

               if(objs.length >= 1 && !StringUtils.isEmpty(objs[0])) {
                  gradientColor.setApply(Boolean.parseBoolean(objs[0].toString()));
               }

               if(objs.length >= 2 && !StringUtils.isEmpty(objs[1])) {
                  gradientColor.setDirection(objs[1].toString());
               }

               if(objs.length >= 3 && !StringUtils.isEmpty(objs[2])) {
                  if(gradientColor.isLinear()) {
                     gradientColor.setAngle(parseObjToInteger(objs[2]));
                  }
                  else {
                     addColorStop(colors, buildColorStop(objs[2].toString()));
                  }
               }

               if(objs.length >= 4) {
                  for(int i = 3; i < objs.length; i++) {
                     addColorStop(colors, buildColorStop(objs[i].toString()));
                  }
               }

               gradientColor.setColors(colors.toArray(new GradientColor.ColorStop[colors.size()]));

               return gradientColor;
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to covert " + val + " to type " + type, ex);
         throw new RuntimeException("Can't convert (" + val + ") to [" + type +
                                    "]");
      }

      return val;
   }

   private static Integer parseObjToInteger(Object obj) {
      if(StringUtils.isEmpty(obj)) {
         return 0;
      }
      else if(obj instanceof Number) {
         return ((Number) obj).intValue();
      }
      else if(obj instanceof Character) {
         return (int) ((Character) obj);
      }
      else {
         return Integer.parseInt(obj.toString());
      }
   }

   private static void addColorStop(List<GradientColor.ColorStop> list,
                                    GradientColor.ColorStop colorStop)
   {
      if(list != null && colorStop != null) {
         list.add(colorStop);
      }
   }

   private static GradientColor.ColorStop buildColorStop(String colorStopStr) {
      GradientColor.ColorStop result = null;

      if(!StringUtils.isEmpty(colorStopStr)) {
         String[] colorStop = colorStopStr.split(" ");

         if(colorStop != null && colorStop.length > 1) {
            result = new GradientColor.ColorStop(colorStop[0], Integer.parseInt(colorStop[1]));
         }
      }

      return result;
   }

   /**
    * Create a dataset from a javascript dataset/tablelens/array.
    */
   public static DataSet createDataSet(Object ndata) {
      DataSet nset = null;

      if(ndata instanceof TableArray) {
         ndata = ((TableArray) ndata).getTable();
      }

      if(ndata instanceof DataSet) {
         nset = (DataSet) ndata;
      }
      else if(ndata instanceof XTable) {
         nset = new XTableDataSet((XTable) ndata);
      }
      else if(ndata instanceof Object[]) {
         Object[][] arr2 = to2DArray(ndata);

         if(arr2 != null) {
            nset = new DefaultDataSet(arr2);
         }
      }

      return nset;
   }

   /**
    * Convert an object value to a 2D array.
    */
   private static Object[][] to2DArray(Object ndata) {
      Object[] arr = (Object[]) ndata;
      Object[][] arr2 = new Object[arr.length][];

      for(int k = 0; k < arr2.length; k++) {
         // if this is not really a 2d array, don't return a 2d array
         // or the none([val]) in calc table would be directed to
         // table summarize in ReportJavaScriptEngine.summarize()
         if(!JSObject.isArray(arr[k])) {
            return null;
         }

         arr2[k] = JavaScriptEngine.split(arr[k]);
      }

      return arr2;
   }

   private Method getter; // property getter
   protected Method setter; // property setter
   protected Class type; // property type
   private Object object; // property host object
   private Object value; // property value
   private Object[] params = null; // parameter to call getter and setter

   private static MethodCache methods = new MethodCache();
   private static final Logger LOG =
      LoggerFactory.getLogger(PropertyDescriptor.class);
}
