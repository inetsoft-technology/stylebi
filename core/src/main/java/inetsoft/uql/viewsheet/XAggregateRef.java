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
package inetsoft.uql.viewsheet;

import inetsoft.graph.data.CalcColumn;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.IAggregateRef;
import inetsoft.uql.erm.*;

/**
 * A XAggregateRef object represents a aggregate reference.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface XAggregateRef extends AssetObject, DataRefWrapper, 
                                       VSDataRef, IAggregateRef, CalculateAggregate
{
   /**
    * Set the secondary column to be used in the formula.
    * @param ref formula secondary column.
    */
   @Override
   public void setSecondaryColumn(DataRef ref);

   /**
    * Check if the aggregate ref is aggregated. It's aggregated if the aggregate
    * formula is not none, and the aggregate flag is on.
    */
   public boolean isAggregateEnabled();

   /**
    * Check if the aggregate flag is on.
    */
   public boolean isAggregated();

   /**
    * Set whether aggregation would be applied.
    */
   public void setAggregated(boolean aggregated);

   /**
    * Get the data type.
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType();

   /**
    * Get the original data type.
    */
   public String getOriginalDataType();

   /**
    * Get the full name.
    * @param applyCalc flag to apply calculation for full name.
    */
   public String getFullName(boolean applyCalc);

   /**
    * To view.
    * @param applyCalc flag to apply calculation for view.
    */
   public String toView(boolean applyCalc);

   /**
    * Create CalcColumn.
    */
   public CalcColumn createCalcColumn();
}
