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
package inetsoft.uql;

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Objects;

public class CompositeValue<T> implements Cloneable, Serializable {
   public CompositeValue(Class<T> cls, T defaultValue, boolean saveDefault) {
      this.cls = cls;
      this.defaultValue = defaultValue;
      this.saveDefault = saveDefault;
   }

   public CompositeValue(Class<T> cls, T defaultValue) {
      this(cls, defaultValue, false);
   }

   public T get() {
      return userDefined ? userValue : (cssDefined ? cssValue : defaultValue);
   }

   public T get(Type type) {
      if(type == Type.CSS) {
         return cssValue;
      }
      else if(type == Type.USER) {
         return userValue;
      }
      else {
         return defaultValue;
      }
   }

   public void setValue(T value, Type type) {
      if(type == Type.CSS) {
         setCssValue(value);
      }
      else if(type == Type.USER) {
         setUserValue(value);
      }
      else {
         setDefaultValue(value);
      }
   }

   private void setCssValue(T cssValue) {
      this.cssValue = cssValue;
      this.cssDefined = true;
   }

   private void setUserValue(T userValue) {
      this.userValue = userValue;
      this.userDefined = true;
   }

   private void setDefaultValue(T defaultValue) {
      this.defaultValue = defaultValue;
   }

   public boolean hasUserValue() {
      return userDefined;
   }

   public void resetValue(Type type) {
      if(type == Type.CSS) {
         resetCssValue();
      }
      else {
         resetUserValue();
      }
   }

   public void resetUserValue() {
      userValue = null;
      userDefined = false;
   }

   public void resetCssValue() {
      cssValue = null;
      cssDefined = false;
   }

   public void parse(String str) {
      parse(str, false);
   }

   public void parse(String str, boolean readAsDefault) {
      if(str == null || str.isEmpty()) {
         return;
      }

      if(str.contains(DEFAULT_PREFIX)) {
         int index = str.indexOf(DEFAULT_PREFIX);
         String userStr = str.substring(0, index);

         if(!userStr.isEmpty()) {
            setUserValue(parseDataString(userStr));
         }

         String defStr = str.substring(index + DEFAULT_PREFIX.length(), str.length());

         if(!defStr.isEmpty()) {
            setDefaultValue(parseDataString(defStr));
         }
      }
      else {
         // Bug #55730, reading from an earlier version so use readAsDefault to
         // determine whether this value should be read as a default or a user value
         if(saveDefault && readAsDefault) {
            setDefaultValue(parseDataString(str));
         }
         else {
            setUserValue(parseDataString(str));
         }
      }
   }

   private T parseDataString(String str) {
      if(NULL_VALUE.equals(str)) {
         return null;
      }
      else if(Color.class.isAssignableFrom(cls)) {
         return (T) new Color(Integer.parseInt(str));
      }
      else if(Boolean.class.isAssignableFrom(cls)) {
         return (T) Boolean.valueOf(str);
      }
      else if(Double.class.isAssignableFrom(cls)) {
         return (T) Double.valueOf(str);
      }
      else if(Integer.class.isAssignableFrom(cls)) {
         return (T) Integer.valueOf(str);
      }
      else if(Insets.class.isAssignableFrom(cls)) {
         String[] insets = Tool.split(str, ',');
         return (T) new Insets(Integer.parseInt(insets[0]), Integer.parseInt(insets[1]),
                               Integer.parseInt(insets[2]), Integer.parseInt(insets[3]));
      }

      return null;
   }

   @Override
   public String toString() {
      StringBuilder buf = new StringBuilder();

      if(userDefined) {
         buf.append(toDataString(userValue));
      }

      if(saveDefault) {
         buf.append(DEFAULT_PREFIX);
         buf.append(toDataString(defaultValue));
      }

      return buf.toString();
   }

   private String toDataString(T value) {
      if(value == null) {
         return NULL_VALUE;
      }
      else if(value instanceof Color) {
         return ((Color) value).getRGB() + "";
      }
      else if(value instanceof Insets) {
         Insets insets = (Insets) value;
         return insets.top + "," + insets.left + "," + insets.bottom + "," + insets.right;
      }

      return value.toString();
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      CompositeValue<?> that = (CompositeValue<?>) o;
      return Objects.equals(defaultValue, that.defaultValue) &&
         Objects.equals(cssValue, that.cssValue) &&
         Objects.equals(userValue, that.userValue);
   }

   @Override
   public int hashCode() {
      return Objects.hash(defaultValue, cssValue, userValue);
   }

   /**
    * Clone the object.
    *
    * @return the cloned object.
    */
   @Override
   public CompositeValue<T> clone() {
      try {
         CompositeValue<T> compositeValue = (CompositeValue<T>) super.clone();
         compositeValue.defaultValue = cloneValue(defaultValue);
         compositeValue.cssValue = cloneValue(cssValue);
         compositeValue.userValue = cloneValue(userValue);

         return compositeValue;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private T cloneValue(T value) {
      try {
         if(value instanceof Cloneable) {
            Method m = value.getClass().getMethod("clone", new Class[0]);

            if(m != null) {
               value = (T) m.invoke(value, new Object[0]);
            }
         }
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      return value;
   }

   private T defaultValue;
   private T cssValue;
   private T userValue;
   private boolean cssDefined = false;
   private boolean userDefined = false;
   private Class<T> cls;
   private boolean saveDefault = false;
   private static final String NULL_VALUE = "__NULL__";
   private static final String DEFAULT_PREFIX = "^^DEF^^";
   private static final Logger LOG = LoggerFactory.getLogger(CompositeValue.class);

   public static enum Type {
      DEFAULT, CSS, USER
   }
}
