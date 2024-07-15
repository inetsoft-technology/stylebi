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
package inetsoft.report.filter;

import inetsoft.report.TableLens;

import java.awt.*;

/**
 * Crosstab condition filter.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class CrosstabConditionFilter extends ConditionFilter implements Crosstab
{
   /**
    * Create a CrosstabConditionFilter. The conditions are used for filter.
    * @param table table.
    * @param conditions the condition group used for filter.
    */
   public CrosstabConditionFilter(TableLens table, ConditionGroup conditions) {
      super(table, conditions);
   }

   /**
    * Get conditions.
    */
   public ConditionGroup getConditions() {
      return conditions;
   }

   /**
    * Return the spanning setting for the cell. If the specified cell
    * is not a spanning cell, it returns null. Otherwise it returns
    * a Dimension object with Dimension.width equals to the number
    * of columns and Dimension.height equals to the number of rows
    * of the spanning cell.
    * @param r row number.
    * @param c column number.
    * @return span cell dimension.
    */
   @Override
   public Dimension getSpan(int r, int c) {
      Dimension dim = super.getSpan(r, c);

      if(dim == null || dim.height <= 1) {
         return dim;
      }

      int verticalSpan = 1;
      int nextSpanStart = getBaseRowIndex(r) + dim.height;
      int rowCount = getRowCount();

      while(r < rowCount && getBaseRowIndex(++r) < nextSpanStart) {
         verticalSpan++;
      }

      return new Dimension(dim.width, verticalSpan);
   }
}
