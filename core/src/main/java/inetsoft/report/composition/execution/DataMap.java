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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;

import java.util.*;

/**
 * A hashmap for holding resultsets.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class DataMap {
   /**
    * Detail data (without aggregation).
    */
   public static final int DETAIL = 1;
   /**
    * Data with brushing condition.
    */
   public static final int BRUSH = 2;
   /**
    * Data with zoom condition.
    */
   public static final int ZOOM = 4;
   /**
    * Normal result.
    */
   public static final int NORMAL = BRUSH | ZOOM;
   /**
    * Result as VSTableLens.
    */
   public static final int VSTABLE = 16;
   /**
    * Result with no runtime filtering conditions.
    */
   public static final int NO_FILTER = 32 | BRUSH;
   /**
    * Result with drill condition.
    */
   public static final int DRILL_FILTER = 64;

   /**
    * Put a result into the map.
    * @param type a type constant.
    */
   public void put(String name, Object data, int type) {
      map.put(name + "." + type, data);
   }

   /**
    * Get a result from the map.
    * @param type a type constant.
    */
   public Object get(String name, int type) {
      return map.get(name + "." + type);
   }

   /**
    * Remove results for an entry.
    */
   public void remove(String name, int type) {
      map.remove(createKey(name, type));
   }

   /**
    * Remove all results for an entry.
    */
   public void removeAll(String name) {
      removeAll(name, true);
   }

   /**
    * Remove all results for an entry.
    * @param nofilter true to remove data without filter.
    */
   public void removeAll(String name, boolean nofilter) {
      for(int type : types) {
         if(nofilter || (type != NO_FILTER)) {
            map.remove(createKey(name, type));
         }
      }
   }

   /**
    * Rename the entry.
    */
   public void rename(String oname, String nname) {
      for(int type : types) {
         Object val = map.remove(createKey(oname, type));

         if(val != null) {
            map.put(createKey(nname, type), val);
         }
      }
   }

   /**
    * Get a set of all entry names.
    */
   public Collection<String> keys() {
      Set<String> names = new HashSet<>();

      for(String name : new ArrayList<>(map.keySet())) {
         int idx = name.lastIndexOf('.');
         names.add(name.substring(0, idx));
      }

      return names;
   }

   /**
    * Get types a name responds to.
    * @param name the specified name.
    * @return types of this name.
    */
   public Collection<Integer> getTypes(String name) {
      Collection<Integer> c = new ArrayList<>();

      for(String name0 : map.keySet()) {
         if(name0.startsWith(name)) {
            int idx = name0.lastIndexOf('.');
            c.add(Integer.valueOf(name0.substring(idx + 1)));
         }
      }

      return c;
   }

   /**
    * Dispose a table cache map.
    */
   public void dispose() {
      ArrayList<String> list = new ArrayList<>(map.keySet());

      for(String name : list) {
         Object obj = map.remove(name);

         if(obj instanceof TableLens) {
            TableLens table = (TableLens) obj;
            table.dispose();
         }
      }

      map.clear();
   }

   private String createKey(String name, int type) {
      return name + "." + type;
   }

   private final Map<String, Object> map = new Hashtable<>();

   private static final int[] types = {DETAIL, BRUSH, ZOOM, NORMAL, VSTABLE, NO_FILTER};
}
