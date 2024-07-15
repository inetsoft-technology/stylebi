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
import inetsoft.report.filter.SummaryFilter;
import inetsoft.uql.XTable;

/**
 * A selector for selecting the detailed rows from a grouped table.
 * 
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public class DetailRowSelector extends RangeSelector {
   /**
    * Create a detail row selector.
    * @param singleGroup true if only select the detail rows in a single
    * group. It should terminate the loop when hitting a non-detail row.
    * @param level the group level to get the detail rows.
    */
   public DetailRowSelector(boolean singleGroup, int level) {
      this.singleGroup = singleGroup;
      this.level = level;
   }

   @Override
   public int match(XTable table, int row, int col) {
      GroupedTable filter = (GroupedTable) table;

      int glevel = filter.getGroupLevel(row);
      int rc = RangeProcessor.YES;

      if(glevel >= 0 && glevel < level) {
         if(filter.isGroupHeaderRow(row)) {
            return singleGroup ? RangeProcessor.BREAK : RangeProcessor.NO;
         }
         // @by larryl, if find the group header cell, which is not on
         // a separate row, process it then break
         else {
            rc = singleGroup ? RangeProcessor.BREAK_AFTER : RangeProcessor.YES;
         }
      }
      
      if(rc == RangeProcessor.YES && !(filter instanceof SummaryFilter)) {
         if(filter.isSummaryRow(row) || filter.isGroupHeaderRow(row)) {
            return RangeProcessor.NO;
         }
      }

      return rc;
   }
      
   private boolean singleGroup;
   private int level;
}
