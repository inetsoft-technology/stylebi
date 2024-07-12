/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.script.viewsheet;

import inetsoft.report.script.PropertyDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Describe a property as a pair of getter and setter methods.
 */
public class VSPropertyDescriptor extends PropertyDescriptor {
   public VSPropertyDescriptor(Class cls, String getter, String setter, Class type, Object object) {
      super(cls, getter, setter, type, object);
   }

   public VSPropertyDescriptor(Class cls, String setter, Class type, Object value) {
      super(cls, setter, type, value);
   }

   @Override
   public Object set(Object element, Object value) throws Exception {
      if(SET_VALUE.get()) {
         // when setDValue(), also clear out RValue. (62865)
         try {
            super.set(setter, element, value);
         }
         catch(Exception ex) {
            // ignore
         }

         Method setter2 = getValueSetter(setter, type, value);

         if(setter2 != null) {
            // getValueSetter returns string value setter if values are string/boolean,
            // so we make the corresponding conversion here. (62865)
            if(!(value instanceof String) && value != null && setter2.getParameterCount() == 1 &&
               setter2.getParameterTypes()[0].equals(String.class))
            {
               value = value.toString();
            }

            return super.set(setter2, element, value);
         }
      }

      return super.set(element, value);
   }

   /**
    * Set whether the change DValue in put() (default is RValue).
    */
   public static void setUseDValue(boolean useDvalue) {
      SET_VALUE.set(useDvalue);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected boolean isConvertValue(Object value, Method setter) {
      // Should not happen as method is expected to be a setter
      if(setter.getParameterCount() < 1) {
         return !SET_VALUE.get();
      }

      return !(SET_VALUE.get() && value instanceof String &&
         setter.getParameterTypes()[0].equals(String.class));
   }

   /**
    * Get a setter for setting the value of a property. If the value is a string, it may
    * need to call the setXXXValue() method instead of the setXXX() method.
    */
   private Method getValueSetter(Method setter, Class type, Object value) {
      if(setter == null) {
         return null;
      }

      // this method causes the setXXXValue() to be instead at all time instead of setXXX.
      // this is not correct. we should use setXXX by default, and only use setXXXValue() if
      // the parameter is string (and the property type is not string).
      // keep the same for now but change later (13.3).

      if(!setterInited) {
         setterInited = true;

         if(!String.class.equals(type)) {
            try {
               setterValueObj = setter.getDeclaringClass().
                  getMethod(setter.getName() + "Value", new Class[]{ type });
            }
            catch(Exception ex) {
               // ignored
            }
         }

         try {
            setterValueStr = setter.getDeclaringClass().
               getMethod(setter.getName() + "Value", new Class[]{ String.class });
         }
         catch(Exception ex) {
            // ignored
         }
      }

      if(value == null) {
         return setter;
      }
      // access boolean for setVisibleValue (visible = true). (62747)
      else if(value instanceof String || value instanceof Boolean) {
         if(setterValueStr != null) {
            return setterValueStr;
         }
      }
      else if(setterValueObj != null) {
         return setterValueObj;
      }

      return setter;
   }

   private boolean setterInited = false;
   private Method setterValueObj;
   private Method setterValueStr;

   private static ThreadLocal<Boolean> SET_VALUE = new ThreadLocal<Boolean>() {
      @Override
      protected Boolean initialValue() {
         return Boolean.FALSE;
      }
   };
   private static final Logger LOG =
      LoggerFactory.getLogger(VSPropertyDescriptor.class);
}
