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
package inetsoft.uql.util;

import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.UserVariable;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * Named group info interface.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface XNamedGroupInfo extends Serializable, Cloneable {
   /**
    * Expert named group info.
    */
   public static final int EXPERT_NAMEDGROUP_INFO = 1;
   /**
    * Simple named group info.
    */
   public static final int SIMPLE_NAMEDGROUP_INFO = 2;
   /**
    * Asset named group info.
    */
   public static final int ASSET_NAMEDGROUP_INFO = 3;
   /**
    * Asset named group info reference.
    */
   public static final int ASSET_NAMEDGROUP_INFO_REF = 4;

   /**
    * Get type of the named group info.
    *
    * @return the type of the named group info
    */
   public abstract int getType();

   /**
    * Get the condition list of a group.
    *
    * @param name the group name
    * @return the condition list of the group
    */
   public abstract ConditionList getGroupCondition(String name);

   /**
    * Reset the named group info.
    */
   public abstract void clear();

   /**
    * Get all the group names.
    *
    * @return all the group names
    */
   default String[] getGroups() {
      return getGroups(false);
   }

   String[] getGroups(boolean sort);

   /**
    * Remove a group name.
    *
    * @param name the specified group name
    */
   public abstract void removeGroup(String name);

   /**
    * Check if the named group info is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   public boolean isEmpty();

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   public void replaceVariables(VariableTable vars);

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   public UserVariable[] getAllVariables();

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   public boolean printKey(PrintWriter writer) throws Exception;

   /**
    * Clone it.
    */
   public abstract Object clone();
}
