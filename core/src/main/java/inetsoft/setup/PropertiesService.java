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
package inetsoft.setup;

import inetsoft.storage.KeyValueEngine;

/**
 * {@code PropertiesService} handles setting properties in a configured storage. The
 * {@link #close()} method must be called after instances are no longer needed.
 */
public class PropertiesService extends AbstractStorageService {
   public PropertiesService(String directory) {
      super(directory);
      keyValueEngine = createKeyValueEngine();
   }

   /**
    * Gets the value of a property.
    *
    * @param name the property name.
    *
    * @return the property value.
    */
   public String get(String name) {
      if(keyValueEngine.contains("sreeProperties", name)) {
         return keyValueEngine.get("sreeProperties", name);
      }

      return null;
   }

   /**
    * Sets the value of a property.
    *
    * @param name  the property name.
    * @param value the property value.
    */
   public void put(String name, String value) {
      keyValueEngine.put("sreeProperties", name, value);
   }

   /**
    * Sets the value of a property if it is not already set.
    *
    * @param name  the property name.
    * @param value the property value.
    */
   public void putIfAbsent(String name, String value) {
      if(!keyValueEngine.contains("sreeProperties", name) ||
         keyValueEngine.<String>get("sreeProperties", name).isEmpty())
      {
         put(name, value);
      }
   }

   /**
    * Sets the value of a property if it is not already set and <i>value</i> is not null or empty.
    *
    * @param name  the property name.
    * @param value the property value.
    */
   public void putNonnullIfAbsent(String name, String value) {
      if(value != null && !value.isEmpty()) {
         putIfAbsent(name, value);
      }
   }

   /**
    * Removes the value for a property.
    *
    * @param name the property name.
    */
   public void remove(String name) {
      if(keyValueEngine.contains("sreeProperties", name)) {
         keyValueEngine.remove("sreeProperties", name);
      }
   }

   @Override
   public void close() throws Exception {
      keyValueEngine.close();
   }

   private final KeyValueEngine keyValueEngine;
}
