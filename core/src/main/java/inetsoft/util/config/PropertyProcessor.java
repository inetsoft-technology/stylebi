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
package inetsoft.util.config;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.propertyeditors.StringArrayPropertyEditor;

import java.beans.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PropertyProcessor {
   public void applyProperties(InetsoftConfig config) {
      try {
         applyProperties(config, "inetsoftConfig.", "INETSOFTCONFIG_");
      }
      catch(Exception e) {
         LOG.warn("Failed to set configuration from system property or environment variable", e);
      }
   }

   private boolean applyProperties(Object bean, String propertyPrefix, String environmentPrefix)
      throws Exception
   {
      boolean modified = false;
      BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());

      for(PropertyDescriptor desc : beanInfo.getPropertyDescriptors()) {
         if(desc.getPropertyType().isAnnotationPresent(InetsoftConfigBean.class)) {
            Object child = desc.getReadMethod().invoke(bean);
            boolean newChild = false;

            if(child == null) {
               child = desc.getPropertyType().getConstructor().newInstance();
               newChild = true;
            }

            boolean childModified = applyProperties(
               child, propertyPrefix + desc.getName() + ".",
               environmentPrefix + desc.getName().toUpperCase() + "_");

            if(childModified && newChild) {
               desc.getWriteMethod().invoke(bean, child);
            }

            modified = modified || childModified;
         }
         else {
            String property = getPropertyValue(desc.getName(), propertyPrefix, environmentPrefix);

            if(property != null) {
               PropertyEditor editor = getPropertyEditor(desc);

               if(editor != null) {
                  editor.setAsText(property);
                  desc.getWriteMethod().invoke(bean, editor.getValue());
                  modified = true;
               }
               else {
                  LOG.warn(
                     "Failed to set configuration property {}{}, could not find property editor for type {}",
                     propertyPrefix, desc.getName(), desc.getPropertyType());
               }
            }
         }
      }

      return modified;
   }

   private String getPropertyValue(String name, String propertyPrefix, String environmentPrefix) {
      String value = System.getenv(environmentPrefix + name.toUpperCase());

      if(!StringUtils.isEmpty(value)) {
         return value;
      }

      return System.getProperty(propertyPrefix + name);
   }

   private PropertyEditor getPropertyEditor(PropertyDescriptor desc) {
      PropertyEditor editor = desc.createPropertyEditor(null);

      if(editor == null) {
         editor = PropertyEditorManager.findEditor(desc.getPropertyType());
      }

      if(editor == null && desc.getPropertyType().isArray() &&
         String.class.isAssignableFrom(desc.getPropertyType().getComponentType()))
      {
         editor = new StringArrayPropertyEditor(",", true, true);
      }

      if(editor == null && Map.class.isAssignableFrom(desc.getPropertyType())) {
         editor = new MapPropertyEditor();
      }

      return editor;
   }

   private static final Logger LOG = LoggerFactory.getLogger(PropertyProcessor.class);

   public static final class MapPropertyEditor extends PropertyEditorSupport {
      @Override
      public String getAsText() {
         Map<?, ?> map = (Map<?, ?>) getValue();
         return map.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(","));
      }

      @Override
      public void setAsText(String text) throws IllegalArgumentException {
         Map<String, String> map = new HashMap<>();

         if(text != null) {
            for(String entry : text.split(",")) {
               if(entry != null && !entry.isEmpty()) {
                  int idx = entry.indexOf('=');

                  if(idx > -1) {
                     String key = entry.substring(0, idx);
                     String value = entry.substring(idx + 1);
                     map.put(key, value);
                  }
               }
            }
         }

         setValue(map);
      }
   }
}
