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
package inetsoft.util;

import inetsoft.storage.KeyValueStorage;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LayerProperties as a properties tries to keep the performance of properties,
 * and it divides the key-value pairs into three groups, namely BASE, LOADED
 * and SET. If reload properties from an input stream, the LOAD key-value
 * pairs will be cleared.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
public class LayerProperties extends Properties {
   /**
    * Constructor.
    */
   public LayerProperties() {
      super();
   }

   /**
    * Constructor.
    */
   public LayerProperties(Properties prop) {
      super(prop);
   }

   /**
    * Load properties from an input stream.
    */
   @Override
   public synchronized void load(InputStream in) throws IOException {
      Properties prop = new Properties();
      prop.load(in);
      load(prop);
   }

   /**
    * Load properties from a key-value storage.
    */
   public synchronized void load(KeyValueStorage<String> storage) {
      Properties prop = new Properties();
      storage.stream().forEach(p -> prop.setProperty(p.getKey(), p.getValue()));
      load(prop);
   }

   private void load(Properties prop) {
      Set<String> removed = typeMap.entrySet().stream()
         .filter(e -> e.getValue() == EntryType.LOADED)
         .map(Map.Entry::getKey)
         .collect(Collectors.toSet());

      removed.forEach(this::remove);

      for(String key : prop.stringPropertyNames()) {
         typeMap.put(key, EntryType.LOADED);
         super.setProperty(key, prop.getProperty(key));
      }
   }

   /**
    * Get property.
    */
   @Override
   public synchronized String getProperty(String key, String defaultValue) {
      Object oval = get(key);

      // get value from self hashtable
      if(oval instanceof String) {
         return (String) oval;
      }

      // get value from base properties
      if(defaults != null) {
         String val = defaults.getProperty(key, defaultValue);

         if(val != null) {
            return val;
         }
      }

      // return default value
      return defaultValue;
   }

   /**
    * Set property.
    */
   @Override
   public synchronized Object setProperty(String key, String value) {
      typeMap.put(key, EntryType.SET);
      return super.setProperty(key, value);
   }

   /**
    * Remove property.
    */
   @Override
   public synchronized Object remove(Object key) {
      typeMap.remove(key);
      return super.remove(key);
   }

   /**
    * Clear properties.
    */
   @Override
   public synchronized void clear() {
      typeMap.clear();
      super.clear();
   }

   private final Map<String, EntryType> typeMap = new HashMap<>();

   enum EntryType {
      BASE, LOADED, SET
   }
}
