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

import inetsoft.report.internal.table.CalcCellContext;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.uql.XTable;
import inetsoft.util.script.FormulaContext;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;

/**
 * Select calc cells based on group spec and cell context.
 *
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public class CalcGroupSelector extends RangeSelector {
   public CalcGroupSelector(RuntimeCalcTableLens calc, Map groupspecs) {
      this.groupspecs = groupspecs;

      Point loc = FormulaContext.getCellLocation();
      CalcCellContext context = loc != null ? calc.getCellContext(loc.y, loc.x) : null;

      // add context to the group qualification
      if(context != null) {
         for(CalcCellContext.Group group : context.getGroups()) {
            if(group.getName() == null) {
               continue;
            }

            NamedCellRange.GroupSpec spec = (NamedCellRange.GroupSpec)
               groupspecs.get(group.getName());

            if(spec == null) {
               groupspecs.put(group.getName(), new NamedCellRange.GroupSpec(context, group));
            }
            else if(spec.isWildCard()) {
               groupspecs.remove(group.getName());
            }
         }
      }

      // remove the wildcards
      Iterator iter = groupspecs.entrySet().iterator();

      while(iter.hasNext()) {
         NamedCellRange.GroupSpec spec = (NamedCellRange.GroupSpec)
            ((Map.Entry) iter.next()).getValue();

         if(spec.isWildCard()) {
            iter.remove();
         }
      }
   }

   /**
    * Check if a cell matches selection criterias.
    * @return one of the flag defined in RowSelector.
    */
   @Override
   public int match(XTable lens, int row, int col) {
      RuntimeCalcTableLens calc = (RuntimeCalcTableLens) lens;
      Iterator iter = groupspecs.keySet().iterator();
      CalcCellContext context = calc.getCellContext(row, col);

      // for a cell that doesn't belong to any group, there is a single instance
      // so the group spec should be ignored. This is especially needed if the
      // reference is used in a group context but is not explicitly qualified
      // by user. A better solution may be to distinguish between implicit
      // group spec and explicit group spec, and only ignore implicit group
      // spec. But it should be safe doing it more liberally.
      if(context == null || context.getGroupCount() == 0) {
         return RangeProcessor.YES;
      }

      while(iter.hasNext()) {
         String gname = (String) iter.next();
         NamedCellRange.GroupSpec spec = (NamedCellRange.GroupSpec) groupspecs.get(gname);

         for(CalcCellContext.Group group : context.getGroups()) {
            if(gname.equals(group.getName())) {
               if(spec.isByPosition()) {
                  if(group.getPosition() != spec.getIndex()) {
                     return RangeProcessor.NO;
                  }
               }
               else if(spec.isByValue()) {
                  if(!equalsGroup(group.getValue(context), spec.getValue())) {
                     return RangeProcessor.NO;
                  }
               }
            }
         }
      }

      // if the group spec in the referencing cell doesn't have the same
      // groups in the referenced cell, return yes so unrelated cells can
      // reference each other. If some groups are found, the comparison has
      // failed and the cell should be rejected.
      return RangeProcessor.YES;
   }

   private Map groupspecs;
}
