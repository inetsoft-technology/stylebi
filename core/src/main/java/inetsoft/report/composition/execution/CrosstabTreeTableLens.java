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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.uql.viewsheet.internal.CrosstabTree;

/**
 * Change the base table so collapsed members are treated as a single value so
 * the resulting crosstab would match the CrosstabTree. This is used as the base
 * class for CrossTabFilter to produced the crosstab with expanded/collapsed cells.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public class CrosstabTreeTableLens extends DefaultTableFilter {
   /**
    * Create a filter.
    */
   public CrosstabTreeTableLens(TableLens table, CrosstabTree ctree) {
      super(table);

      this.ctree = ctree;
      this.table = table;
   }

   @Override
   public final Object getObject(int r, int c) {
      return r == 0 || ctree.isParentsExpanded(table, r, c) ? table.getObject(r, c)
         : CrosstabTree.COLLAPSED;
   }

   private CrosstabTree ctree;
   private TableLens table;
}
