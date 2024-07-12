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
package inetsoft.uql.tabular;

import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * This class stores information about a bean property.
 *
 * @author InetSoft Technology
 * @since 12.0
 */
public class PropertyMeta implements Comparable {
   public PropertyMeta(PropertyDescriptor desc) {
      this.desc = desc;

      Method getter = desc.getReadMethod();
      Method setter = desc.getWriteMethod();

      if(getter != null) {
         property = getter.getAnnotation(Property.class);
         editor = getter.getAnnotation(PropertyEditor.class);
      }

      if(property == null && setter != null) {
         property = setter.getAnnotation(Property.class);
         editor = setter.getAnnotation(PropertyEditor.class);
      }
   }

   /**
    * Check if the @Property is added to the property.
    */
   public boolean isAnnotated() {
      return property != null;
   }

   /**
    * Get property name.
    */
   public String getName() {
      return desc.getName();
   }

   /**
    * Check if the editor is a multiline text area.
    */
   public boolean isMultiline() {
      return editor != null && editor.rows() > 1;
   }

   /**
    * Return the properties this editor (tagsMethod) depends on.
    */
   public String[] getDependsOn() {
      if(editor != null) {
         return editor.dependsOn();
      }

      return new String[0];
   }

   /**
    * Get the property value from the bean.
    */
   public Object getValue(Object bean) {
      try {
         Object val = desc.getReadMethod().invoke(bean);

         if(val instanceof Enum) {
            val = ((Enum) val).name();
         }

         return val;
      }
      catch(Exception ex) {
         LOG.error("Failed to get property value: " + desc.getName(), ex);
      }

      return null;
   }

   /**
    * Set the property value in the bean.
    */
   public void setValue(Object bean, Object val) {
      try {
         Class[] ptypes = desc.getWriteMethod().getParameterTypes();

         if(ptypes.length > 0 && val != null) {
            if(ptypes[0].isEnum()) {
               val = Enum.valueOf(ptypes[0], val.toString());
            }
            else if(ptypes[0] == char.class) {
               if("".equals(val)) {
                  val = ' ';
               }
               else {
                  val = val.toString().charAt(0);
               }
            }
            // having surrounding space seems never desirable. if needed, we can
            // add an attribute in Property annotation to keep space in the future.
            else if(ptypes[0] == String.class) {
               val = val.toString().trim();
            }
         }

         // passing null to primitive parameter causing an exception. use 0 as default
         // for numbers so a value can be cleared if the field is cleared on gui.
         if(val == null) {
            Parameter param = desc.getWriteMethod().getParameters()[0];

            if(param.getType().equals(int.class)) {
               val = 0;
            }
            else if(param.getType().equals(long.class)) {
               val = 0l;
            }
            else if(param.getType().equals(short.class)) {
               val = (short) 0;
            }
            else if(param.getType().equals(double.class)) {
               val = 0.0;
            }
            else if(param.getType().equals(float.class)) {
               val = 0f;
            }
         }

         desc.getWriteMethod().invoke(bean, val);
      }
      catch(Exception ex) {
         LOG.error("Failed to set property value: " + desc.getName() + " " + val, ex);
      }
   }

   public String getDisplayLabel() {
      if(property.label().length() > 0) {
         return catalog.getString(property.label());
      }

      return catalog.getString(desc.getDisplayName());
   }

   @Override
   public int compareTo(Object obj) {
      return getName().compareTo(((PropertyMeta) obj).getName());
   }

   public Property getProperty() {
      return property;
   }

   public PropertyEditor getEditor() {
      return editor;
   }

   public PropertyDescriptor getDescriptor() {
      return desc;
   }

   private PropertyDescriptor desc;
   private Property property;
   private PropertyEditor editor;
   private Catalog catalog = Catalog.getCatalog();

   private static final Logger LOG =
      LoggerFactory.getLogger(PropertyMeta.class);
}
