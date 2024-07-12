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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.data.CalcColumn;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.util.XMLSerializable;

import java.io.Serializable;
import java.util.List;

/**
 * Calculator interface.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public interface Calculator extends XMLSerializable, Serializable, Cloneable {
   /**
    * Type of percent.
    */
   int PERCENT = 1;
   /**
    * Type of change.
    */
   int CHANGE = 2;
   /**
    * Type of running total.
    */
   int RUNNINGTOTAL = 3;
   /**
    * Type of moving.
    */
   int MOVING = 4;
   /**
    * Type of custom.
    */
   int CUSTOM = 5;

   /**
    * Type of value of.
    */
   int VALUE = 6;

   /**
    * Type of compound growth.
    */
   int COMPOUNDGROWTH = 7;

   /**
    * create calculator column.
    * @param column column to be created.
    * @return the created column.
    */
   CalcColumn createCalcColumn(String column);

   /**
    * Update the calculator's dynamic values.
    *
    * @param oldRefs the old refs.
    * @param newRefs the new refs. The indices should match oldRefs.
    */
   void updateRefs(List<VSDimensionRef> oldRefs, List<VSDimensionRef> newRefs);

   /**
    * Get type.
    * @return type.
    */
   int getType();

   /**
    * Get alias.
    * @return alias.
    */
   String getAlias();

   /**
    * Set alias.
    * @param alias to be set.
    */
   void setAlias(String alias);

   /**
    * Get name.
    * @return name.
    */
   String getName();

   /**
    * If the calculation returns a percentage.
    */
   default boolean isPercent() {
      return false;
   }

   /**
    * Get prefix.
    */
   String getPrefix();

   /**
    * Get prefix.
    */
   String getPrefixView();

   /**
    * Get view.
    */
   String toView();

   /**
    * Whether support the sort by value.
    * @return
    */
   default boolean supportSortByValue() {
      return false;
   }

   /**
    * Clone.
    */
   Object clone();
}
