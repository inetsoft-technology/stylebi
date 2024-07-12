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
package inetsoft.report.script.formula;

import inetsoft.report.filter.GroupedTable;
import inetsoft.uql.XTable;

import java.util.Map;

/**
 * An selector for selecting rows within specified groups.
 * 
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
class GroupRowSelector extends AbstractGroupRowSelector {
   public GroupRowSelector(XTable table, Map groupspecs) {
      super(table, groupspecs);

      // sort the cols to be descending order
      for(int i = 1; i < gcolumns.length; i++) {
         for(int j = i; j > 0; j--) {
            if(gcolumns[j] <= gcolumns[j - 1]) {
               break;
            }

            int tempi = gcolumns[j - 1];
            Object tempv = values[j - 1];
               
            gcolumns[j - 1] = gcolumns[j];
            values[j - 1] = values[j];
            gcolumns[j] = tempi;
            values[j] = tempv;
         }
      }

      GroupedTable filter = (GroupedTable) table;

      // check if the group is fully qualified so only one group will be
      // selected
      if(gcolumns.length == filter.getGroupColCount() && 
         !hasGroupExpression()) 
      {
         singleGroup = true;
            
         for(int i = 0; i < values.length; i++) {
            if(values[i].equals("*")) {
               singleGroup = false;
               break;
            }
         }
      }
   }
      
   /**
    * Set whether to match summary row. Only match group header row if false.
    */
   public void setSummary(boolean summary) {
      this.summary = summary;
   }

   /**
    * Check whether to match summary row. Only match group header row if false.
    */
   public boolean isSummary() {
      return summary;
   }
   
   @Override
   public int match(XTable table, int row, int col) {
      if(row < toRow || gcolumns.length == 0) {
         return RangeProcessor.YES;
      }

      // if a group was already found and only group is selected, terminate
      if(toRow > 0 && singleGroup) {
         return RangeProcessor.BREAK;
      }
         
      GroupedTable filter = (GroupedTable) table;
      int orow = row;

      for(int i = 0; i < gcolumns.length; i++) {
         int gcolumn = (gcolumns[i] < 0) ? 0 : gcolumns[i];
         
         if((summary && filter.isSummaryRow(row) ||
            !summary && filter.isGroupHeaderCell(row, gcolumn)) &&
            (values[i].equals("*") || 
             equalsGroup(values[i], getValue(table, row, i))))
         {
            if(filter.isAddGroupHeader()) {
               row--;
            }
         }
         else {
            return RangeProcessor.NO;
         }
      }

      toRow = filter.getGroupLastRow(orow) + 1;
      return RangeProcessor.YES;
   }

   /**
    * Get the inner most group level.
    */
   public int getInnerLevel() {
      // since the index is sorted desc, the first index is the inner most
      return gcolumns[0];
   }

   /**
    * Check if this group selector will select all groups.
    */
   public boolean isWildCard() {
      for(int i = 0; i < values.length; i++) {
         if(!"*".equals(values[i])) {
            return false;
         }
      }

      return true;
   }
   
   private int toRow = -1; // all rows < toRow are in the group
   private boolean singleGroup = false;
   private boolean summary = false;
}
