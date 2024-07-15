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

import inetsoft.uql.XTable;

import java.util.Map;

/**
 * An selector for selecting rows based on column value.
 * 
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
class ValueRowSelector extends AbstractGroupRowSelector {
   public ValueRowSelector(XTable table, Map groupspecs) {
      super(table, groupspecs);
   }
   
   @Override
   public int match(XTable table, int row, int col) {
      for(int i = 0; i < gcolumns.length; i++) {
         if(!equalsGroup(getValue(table, row, i), values[i])) {
            return RangeProcessor.NO;
         }
      }
      
      return RangeProcessor.YES;
   }
}
