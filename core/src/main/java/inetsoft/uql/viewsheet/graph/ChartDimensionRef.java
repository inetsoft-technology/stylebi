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
package inetsoft.uql.viewsheet.graph;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.XDimensionRef;

/**
 * @version 10.3
 * @author InetSoft Technology Corp.
 */
public interface ChartDimensionRef extends XDimensionRef, ChartRef {
   /**
    * Set the aggregate column of this reference.
    */
   @Override
   public void setDataRef(DataRef ref);

   /**
    * Get the column to sort by.
    */
   @Override
   public String getSortByCol();

   /**
    * Get the runtime ranking column value of this dimension reference.
    */
   @Override
   public String getRankingCol();

   /**
    * Set the ranking column of this dimension reference.
    * @param col the ranking column of this dimension reference.
    */
   @Override
   public void setRankingCol(String col);

   /**
    * Get the runtime ranking n value of this dimension reference.
    */
   @Override
   public int getRankingN();

   /**
    * Get the runtime ranking option value of this dimension reference.
    */
   @Override
   public int getRankingOption();

   /**
    * Check if others should be grouped or discarded.
    */
   @Override
   public boolean isGroupOthers();

   void initDefaultFormat();
}
