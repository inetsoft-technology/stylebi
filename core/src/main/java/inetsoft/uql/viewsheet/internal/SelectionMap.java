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
package inetsoft.uql.viewsheet.internal;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Map;

/**
 * SelectionMap, compares objects using value instead of equals when required.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionMap extends Object2IntOpenHashMap<Object> {
   /**
    * Constructor.
    */
   public SelectionMap() {
      super();
   }

   /**
    * Check if contains a key.
    * @param key the specified key.
    * @return <tt>true</tt> if contains the object, <tt>false</tt> otherwise.
    */
   @Override
   public boolean containsKey(Object key) {
      return super.containsKey(SelectionSet.normalize(key));
   }

   /**
    * Get the associated object.
    * @param key the specified key.
    * @return the associated object.
    */
   @Override
   public Integer get(Object key) {
      return super.get(SelectionSet.normalize(key));
   }

   @Override
   public int getInt(Object key) {
      return super.getInt(SelectionSet.normalize(key));
   }

   /**
    * Put the key-value pair.
    * @param key the specified key.
    * @param value the specified value.
    * @return the old value if any, <tt>null</tt> otherwise.
    */
   @Override
   public Integer put(Object key, Integer value) {
      return super.put(SelectionSet.normalize(key), value);
   }

   @Override
   public int put(Object key, int value) {
      return super.put(SelectionSet.normalize(key), value);
   }

   /**
    * Put data in batch.
    * @param map the specified map.
    */
   @Override
   public void putAll(Map<?, ? extends Integer> map) {
      for(Map.Entry<?, ? extends Integer> entry : map.entrySet()) {
         put(entry.getKey(), entry.getValue());
      }
   }

   /**
    * Remove a key.
    * @param key the specified key to remove.
    * @return the old value if any, <tt>null</tt> otherwise.
    */
   @Override
   public Integer remove(Object key) {
      return super.remove(SelectionSet.normalize(key));
   }
}
