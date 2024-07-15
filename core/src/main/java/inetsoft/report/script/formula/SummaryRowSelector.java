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
package inetsoft.report.script.formula;

import inetsoft.report.filter.GroupedTable;
import inetsoft.uql.XTable;

/**
 * An selector for selecting the summary rows from a grouped table.
 * 
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public class SummaryRowSelector extends RangeSelector {
   /**
    * Create a summary row selector.
    * @param level the group level to get the detail rows.
    */
   public SummaryRowSelector(int level) {
      this.level = level;
   }

   /**
    * Set the summary level to include in the selection.
    */
   public void setLevel(int level) {
      this.level = level;
   }
      
   @Override
   public int match(XTable table, int row, int col) {
      GroupedTable filter = (GroupedTable) table;

      if(!filter.isSummaryRow(row)) {
         return RangeProcessor.NO;
      }
         
      if(level < 0 || filter.getSummaryLevel(row) == level) {
         return RangeProcessor.YES;
      }

      return RangeProcessor.NO;
   }
      
   private int level;
}
