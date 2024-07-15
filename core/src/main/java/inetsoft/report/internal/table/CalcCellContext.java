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
package inetsoft.report.internal.table;

import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.*;

/**
 * This class captures the CalcTable cell context. It records how a cell is
 * generated and the relation to the group that generated the cell.
 */
public class CalcCellContext {
   /**
    * Create a context by merging the groups in the two contexts.
    */
   public static CalcCellContext merge(CalcCellContext context1, CalcCellContext context2) {
      CalcCellContext context = new CalcCellContext();

      context.groups.putAll(context1.groups);
      context.groups.putAll(context2.groups);
      context.valueidx.putAll(context1.valueidx);
      context.valueidx.putAll(context2.valueidx);

      return context;
   }

   /**
    * Add a new group to the context.
    */
   public void addGroup(String name, int position, ValueList values) {
      groups.put(name, new Group(name, position, values));
   }

   /**
    * Set the index in the value array.
    */
   public void setValueIndex(String name, int idx) {
      valueidx.put(name, idx);
   }

   /**
    * Get the index of the value in the value array. If the index is unknown,
    * it returns -1.
    */
   public int getValueIndex(String name) {
      Integer iobj = valueidx.get(name);
      return (iobj == null) ? -1 : iobj;
   }

   /**
    * Get the groups in the context.
    */
   public Collection<String> getGroupNames() {
      return groups.keySet();
   }

   /**
    * Get the groups in the context.
    */
   public Collection<Group> getGroups() {
      return groups.values();
   }

   /**
    * Get a group by name.
    */
   public Group getGroup(String name) {
      return groups.get(name);
   }

   /**
    * Get the number of groups in the context.
    */
   public int getGroupCount() {
      return groups.size();
   }

   public Map<String,Integer> getValueidx() {
      return Collections.unmodifiableMap(valueidx);
   }

   /**
    * Get the context hashcode.
    */
   public int hashCode() {
      return groups.hashCode();
   }

   /**
    * Check if the context contains the same groups.
    */
   public boolean equals(Object obj) {
      try {
         CalcCellContext context = (CalcCellContext) obj;

         return groups.equals(context.groups);
      }
      catch(Exception ex) {
         return false;
      }
   }

   /**
    * Check if the context contains the same groups.
    */
   public boolean equalsOneGroup(Object obj) {
      CalcCellContext context = (CalcCellContext) obj;
      Map g1 = context.groups;

      for(Object key : g1.keySet()) {
         Object val1 = g1.get(key);
         Object val2 = groups.get(key);

         if(val1 != null && val1.equals(val2) ||
            val2 != null && val2.equals(val1))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Make a copy of the context.
    */
   @Override
   public Object clone() {
      CalcCellContext obj = new CalcCellContext();

      obj.valueidx = valueidx.clone();
      obj.groups = groups.clone();

      return obj;
   }

   /**
    * Get a string that can uniquely identify this group spec.
    */
   public String getIdentifier() {
      StringBuilder buf = new StringBuilder();

      for(Group group : groups.values()) {
         buf.append(group.getIdentifier());
         buf.append(",");
      }

      return buf.toString();
   }

   public String toString() {
      return "CalcCellContext(" + groups + ";" + valueidx + ")";
   }

   /**
    * Context within a group.
    */
   public static class Group {
      Group(String name, int position, ValueList values) {
         this.name = name;
         this.position = position;
         this.values = values;
      }

      /**
       * Copy constructor.
       */
      Group(Group group) {
         this(group.name, group.position, group.values);
      }

      /**
       * Get the group name.
       */
      public String getName() {
         return name;
      }

      /**
       * Get the position within the group.
       */
      public int getPosition() {
         return position;
      }

      /**
       * Get parent group value.
       */
      public Object getValue(CalcCellContext context) {
         return getValue(context, position);
      }

      /**
       * Get the group value at the specified position.
       */
      public Object getValue(CalcCellContext context, int idx) {
         Object val = idx >= 0 && idx < values.size() ? values.get(idx) : null;

         if(val instanceof Object[]) {
            int vi = context.getValueIndex(name);

            if(vi >= 0) {
               return ((Object[]) val)[vi];
            }
         }

         return val;
      }

      /**
       * Get all values of this group.
       */
      public Object[] getValues() {
         return values.getValues();
      }

      /**
       * The group values are in either of one forms:
       * 1. If all groups have same value list, it's a one dimensional array, e.g. [a, b, c]
       * 2. If the groups have different value list, it's stored in a two dimensional array, e.g.
       *    [[g1v1, g2v1], [g1v2, g2v2]]
       * This method fetches the correct value list.
       * @param valueIndex the value index in the two dimensional array.
       */
      public Object[] getGroupValues(int valueIndex) {
         Object[] arr = getValues();

         if(arr instanceof Object[][]) {
            Object[] arr2 = new Object[arr.length];

            for(int i = 0; i < arr2.length; i++) {
               arr2[i] = ((Object[]) arr[i])[valueIndex];
            }

            return arr2;
         }

         return arr;
      }

      /**
       * Get the size of the value list.
       */
      public int getValueCount() {
         return values.size();
      }

      /**
       * Get the 'field' scope for this group.
       */
      public Object getScope() {
         return values.getScope(position);
      }

      /**
       * get the hashcode of the group.
       */
      public int hashCode() {
         return (name != null) ? name.hashCode() : super.hashCode() + position;
      }

      public boolean equals(Object obj) {
         try {
            Group group = (Group) obj;

            if(position != group.position || !Tool.equals(name, group.name)) {
               return false;
            }

            // @by larryl, optimization. Comparing object reference is more
            // strict but is ok in the calctable context since the value list
            // are not cloned or copied.
            //return values.equals(group.values);
            return values == group.values;
         }
         catch(Exception ex) {
            return false;
         }
      }

      /**
       * Get a string that can uniquely identify this group spec.
       */
      public String getIdentifier() {
         return name + ":" + position;
      }

      public String toString() {
         return "Group[" + name + "," + position + "," + values + "]";
      }

      private String name; // group name if any
      private int position; // position inside the group
      private ValueList values; // group value
   }

   // name -> Group
   private Object2ObjectOpenHashMap<String, Group> groups = new Object2ObjectOpenHashMap<>();
   private Object2IntOpenHashMap<String> valueidx = new Object2IntOpenHashMap<>(); // name -> index in value arr
}
