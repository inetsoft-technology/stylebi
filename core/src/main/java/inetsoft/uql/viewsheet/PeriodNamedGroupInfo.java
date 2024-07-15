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
package inetsoft.uql.viewsheet;

import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.xml.VersionControlComparators;

import java.io.PrintWriter;
import java.util.*;

/**
 * Period Named group info.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class PeriodNamedGroupInfo implements XNamedGroupInfo {
   /**
    * Create an instance of PeriodNamedGroupInfo.
    */
   public PeriodNamedGroupInfo() {
      super();
   }

   /**
    * Get type of the named group info.
    * @return the type of the named group info.
    */
   @Override
   public int getType() {
      return ASSET_NAMEDGROUP_INFO;
   }

   /**
    * Get the condition list of a group.
    * @param name the group name.
    * @return the condition list of the group.
    */
   @Override
   public ConditionList getGroupCondition(String name) {
      return map.get(name);
   }

   /**
    * Set the condition list of a group.
    * @param name the group name.
    * @param conds the condition list of the group.
    */
   public void setGroupCondition(String name, ConditionList conds) {
      if(conds == null || conds.getSize() == 0) {
         map.remove(name);
      }
      else {
         map.put(name, conds);
      }
   }

   /**
    * Reset the named group info.
    */
   @Override
   public void clear() {
      map.clear();
   }

   /**
    * Get all the group names.
    * @return all the group names.
    */
   @Override
   public String[] getGroups(boolean sort) {
      Set<String> keys = map.keySet();
      String[] arr = new String[keys.size()];
      arr = keys.toArray(arr);

      if(sort) {
         Arrays.sort(arr, VersionControlComparators.string);
      }

      return arr;
   }

   /**
    * Remove a group name.
    * @param name the specified group name.
    */
   @Override
   public void removeGroup(String name) {
      map.remove(name);
   }

   /**
    * Check if the named group info is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmpty() {
      return map.size() == 0;
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      Iterator<ConditionList> it = map.values().iterator();

      while(it.hasNext()) {
         ConditionList conds = it.next();
         conds.replaceVariables(vars);
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      List<UserVariable> vars = new ArrayList<>();
      Iterator<ConditionList> it = map.values().iterator();

      while(it.hasNext()) {
         ConditionList conds = it.next();
         UserVariable[] arr = conds.getAllVariables();

         for(int i = 0; arr != null && i < arr.length; i++) {
            vars.add(arr[i]);
         }
      }

      return vars.toArray(new UserVariable[0]);
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Clone this PeriodNamedGroupInfo object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         // ignore it for impossible
      }

      return null;
   }

   private Map<String, ConditionList> map = new HashMap<>();
}
