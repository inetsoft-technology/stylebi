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
package inetsoft.report.composition.region;

import inetsoft.util.DataSerializable;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.io.*;
import java.util.*;

/**
 * IndexedSet defines the values and indices mapping to avoid redundance.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class IndexedSet<T> implements DataSerializable {
   /**
    * Constructor.
    */
   public IndexedSet() {
      super();

      valueMap = new Object2ObjectOpenHashMap<>();
      values = new ArrayList();
   }

   /**
    * Put an item into the IndexedSet.
    * @param key the specified item key.
    * @return int of this key index.
    */
   public int put(T key) {
      if(key == null) {
         return -1;
      }

      int idx = valueMap.get(key) == null ? -1 : valueMap.get(key);

      if(idx < 0) {
         values.add(key);
         idx = values.size() - 1;
         valueMap.put(key, idx);
      }

      return idx;
   }

   /**
    * Clear palette.
    */
   public void clear() {
      valueMap.clear();
      values.clear();
   }

   /**
    * Get an item.
    * @param index the specified index.
    * @return item of the specified index.
    */
   public T get(int index) {
      return (index == -1) ? null : values.get(index);
   }

   /**
    * Get the number of items in the set.
    */
   public int size() {
      return values.size();
   }

   /**
    * Write data to a DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      int len = values.size();
      output.writeInt(len);

      for(int i = 0; i < len; i++) {
         writeValue(output, values.get(i));
      }
   }

   /**
    * Write value to output, default converts to string.
    */
   protected void writeValue(DataOutputStream output, Object val)
      throws IOException
   {
      output.writeUTF((String) val);
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }

   @Override
   public String toString() {
      return super.toString() + "(" + values + ")";
   }

   private Map<T, Integer> valueMap;
   private List<T> values;
}
