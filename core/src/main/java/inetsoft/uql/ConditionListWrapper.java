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
package inetsoft.uql;

import inetsoft.uql.schema.UserVariable;
import inetsoft.util.XMLSerializable;

import java.io.Serializable;

/**
 * ConditionListWrapper wraps a <tt>ConditionList<tt>.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface ConditionListWrapper extends Cloneable, Serializable, XMLSerializable {
   /**
    * Get the size.
    * @return size of the ConditionList.
    */
   int getConditionSize();

   /**
    * Get the HierarchyItem at the specified index.
    * @param index the specified index.
    * @return the HierarchyItem at the specified index.
    */
   HierarchyItem getItem(int index);

   /**
    * Check if the item at the specified index is a ConditionItem.
    * @param index the specified index.
    * @return true if is a ConditionItem.
    */
   boolean isConditionItem(int index);

   /**
    * Check if the item at the specified index is a JunctionOperator.
    * @param index the specified index.
    * @return true if is a JunctionOperator.
    */
   boolean isJunctionOperator(int index);

   /**
    * Get the ConditionItem at the specified index.
    * @param index the specified index.
    * @return the ConditionItem at the specified index.
    */
   ConditionItem getConditionItem(int index);

   /**
    * Get the JunctionOperator at the specified index.
    * @param index the specified index.
    * @return the JunctionOperator at the specified index.
    */
   JunctionOperator getJunctionOperator(int index);

   /**
    * Get the contained ConditionList.
    * @return the contained CondtiionList.
    */
   ConditionList getConditionList();

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   void replaceVariables(VariableTable vars);

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   UserVariable[] getAllVariables();

   /**
    * Check if this list is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   boolean isEmpty();

   /**
    * Clone the object.
    * @return the cloned object.
    */
   Object clone();
}
