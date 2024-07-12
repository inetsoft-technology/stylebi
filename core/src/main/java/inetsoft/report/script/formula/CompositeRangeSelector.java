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

import inetsoft.uql.XTable;

/**
 * A row selector that contains multiple other row selectors. The first
 * non-YES value of a selector is returned.
 */
public class CompositeRangeSelector extends RangeSelector {
   public CompositeRangeSelector(RangeSelector sel1, RangeSelector sel2) {
      this(new RangeSelector[] {sel1, sel2});
   }
   
   public CompositeRangeSelector(RangeSelector[] selectors) {
      this.selectors = selectors;
   }
   
   @Override
   public int match(XTable table, int row, int col) {
      for(int i = 0; i < selectors.length; i++) {
         int rc = selectors[i].match(table, row, col);
         
         if(rc != RangeProcessor.YES) {
            return rc;
         }
      }
      
      return RangeProcessor.YES;
   }

   private RangeSelector[] selectors;
}
