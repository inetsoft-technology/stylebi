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
package inetsoft.report.composition.execution;

import inetsoft.report.internal.Util;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.SelectionMap;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * This class holds a unique list of items of a column, and maintain the
 * association of each item to the items on other lists.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
class ColumnMetaData implements Cloneable, Serializable {
   /**
    * Get column distinct values.
    */
   public List<Object> getValues() {
      return items;
   }

   /**
    * Get the value at the specified position.
    */
   public Object getValue(int idx) {
      return items.get(idx);
   }

   /**
    * Get the number of values in this column.
    */
   public int getValueCount() {
      return items.size();
   }

   /**
    * Set the column type.
    * @param type a type defined in Tool.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Get the column type.
    */
   public String getType() {
      return type;
   }

   /**
    * Set the index of this column in the meta data tuple.
    */
   public void setIndex(int index) {
      this.index = index;
   }

   /**
    * Get the index of this column in the meta data tuple.
    */
   public int getIndex() {
      return index;
   }

   /**
    * Add an item to the list. If the item already exists, the item is not added
    * and the item index is returned. Null values are discarded and not added
    * to the list.
    * @param val item value.
    * @return item index or -1 if the item is not added.
    */
   public int addValue(Object val) {
      val = "".equals(val) ? null : val;

      if(val != null) {
         if(val.equals(pval)) { // optimization
            return pidx;
         }

         pval = val;

         if(mixedType) {
            // don't perform type conversion for mixed type
         }
         else if(fval != null && !val.getClass().equals(fval.getClass())) {
            String ntype = Tool.getDataType(val);

            // allow integer and boolean to be treated as same. this was the
            // logic before the mixedType support. only 0/1 integers are merged with
            // booleans, any other value would be changed (e.g. 23 to true, false to 0)
            if(dataType.equals(XSchema.BOOLEAN) && isZeroOrOne(val)) {
               val = (Integer) val != 0;
            }
            else if(dataType.equals(XSchema.INTEGER) && val instanceof Boolean &&
               isZeroOrOneColumn())
            {
               val = val == Boolean.TRUE ? 1 : 0;
            }
            else if(dataType.equals(XSchema.BOOLEAN) || val instanceof Boolean ||
               XSchema.isNumericType(dataType) && !XSchema.isNumericType(ntype) ||
               XSchema.isDateType(dataType) && !XSchema.isDateType(ntype) ||
               XSchema.STRING.equals(dataType) && !XSchema.STRING.equals(ntype))
            {
               mixedType = true;
            }
            else if(Util.needEnlargeNumberType(fval.getClass(), val.getClass())) {
               enlargeNumberType(val);
            }
            else {
               val = Tool.getData(dataType, val);
            }
         }
      }

      Integer idx = infomap.get(val);

      if(idx == null) {
         idx = items.size();
         infomap.put(val, idx);
         items.add(val);
         strmap = null;
      }

      if(fval == null && val != null) {
         fval = val;
         dataType = Tool.getDataType(fval);
      }

      int result = idx;

      if(val != null) {
         pidx = result;
      }

      return result;
   }

   /**
    * Check if the value is an integer 0 or 1, which may be merged with a boolean.
    */
   private static boolean isZeroOrOne(Object val) {
      return val instanceof Integer && ((Integer) val == 0 || (Integer) val == 1);
   }

   /**
    * Check if all the values added so far are integer 0 or 1.
    */
   private boolean isZeroOrOneColumn() {
      for(Object item : items) {
         if(item != null && !isZeroOrOne(item)) {
            return false;
         }
      }

      return true;
   }

   private void enlargeNumberType(Object val) {
      dataType = Tool.getDataType(val);
      fval = val;
      SelectionMap ninfomap = new SelectionMap();

      for(Object key : infomap.keySet()) {
         int value = infomap.getInt(key);
         Object nkey = Tool.getData(dataType, key);
         ninfomap.put(nkey, value);
         items.remove(value);
         items.add(value, nkey);
      }

      infomap = ninfomap;
      strmap = null;
   }

   /**
    * Get the index of the value in the column value list.
    */
   public int getValueIndex(Object val) {
      // a mixed type column keeps the values unconverted
      if(mixedType && val != null) {
         Integer info = infomap.get(val);

         if(info != null) {
            return info;
         }

         // a selected value usually arrives as its string (the selection list value), which
         // converting to the first value's type would miss or turn into another value
         info = getStringIndexMap().get(Tool.getDataString(val));

         if(info != null) {
            return info;
         }
      }

      if(val != null && fval != null && !val.getClass().equals(fval.getClass())) {
         val = Tool.getData(dataType, val);
      }

      Integer info = infomap.get(val);
      return (info == null) ? -1 : info;
   }

   /**
    * Get the map from the string of each value (as the selection list shows it) to its index.
    */
   private Map<String, Integer> getStringIndexMap() {
      Map<String, Integer> map = strmap;

      if(map == null) {
         map = new HashMap<>();

         for(int i = 0; i < items.size(); i++) {
            Object item = items.get(i);

            if(item != null) {
               map.putIfAbsent(Tool.getDataString(item), i);
            }
         }

         strmap = map;
      }

      return map;
   }

   /**
    * Dispose the column metadata.
    */
   public void dispose() {
      items.clear();
      infomap.clear();
      strmap = null;
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         ColumnMetaData obj = (ColumnMetaData) super.clone();

         obj.items = (ArrayList<Object>) items.clone();
         obj.infomap = (SelectionMap) infomap.clone();
         obj.strmap = null;

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone column meta data", ex);
      }

      return null;
   }

   private ArrayList<Object> items = new ArrayList<>(); // column distinct values
   private SelectionMap infomap = new SelectionMap(); // item value -> Integer (index)
   private String type = Tool.STRING;
   private int index = 0; // column index in the original table
   private transient Object fval = null; // current value on the list
   private transient String dataType = null; // current value type
   private transient Object pval = null; // previous value
   private transient int pidx = 0; // previous value index
   private transient boolean mixedType = false;
   // value string -> index, built on use for a mixed type column, reset when the items change
   private transient volatile Map<String, Integer> strmap = null;

   private static final Logger LOG = LoggerFactory.getLogger(ColumnMetaData.class);
}
