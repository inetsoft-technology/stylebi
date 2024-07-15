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
package inetsoft.report.composition.execution;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TabularTableAssembly;
import inetsoft.uql.asset.internal.TabularTableAssemblyInfo;
import inetsoft.uql.tabular.TabularQuery;

/**
 * Tabular Bound query executes a tabular table assembly.
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public class TabularBoundQuery extends BoundQuery {
   /**
    * Create an asset query.
    */
   public TabularBoundQuery(int mode, AssetQuerySandbox box, boolean stable, boolean metadata) {
      super(mode, box, stable, metadata);
   }

   /**
    * Create an asset query.
    */
   public TabularBoundQuery(int mode, AssetQuerySandbox box, TabularTableAssembly table,
                            boolean stable, boolean metadata) throws Exception
   {
      this(mode, box, stable, metadata);
      this.table = table;
      this.table.update();
      this.xquery = ((TabularTableAssemblyInfo) table.getTableInfo()).getQuery();

      if(xquery != null) {
         xquery.setName(box.getWSName() + "." + getTableDescription(table.getName()));

         if(xquery.getTimeout() == 0) {
            xquery.setTimeout(getTimeout());
         }
      }
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() {
      return false;
   }

   @Override
   public void merge(VariableTable vars) throws Exception {
      super.merge(vars);

      if(xquery != null) {
         xquery.setMaxRows(getMaxRows(false));
      }
   }

   /**
    * Validate the column selection.
    */
   @Override
   public void validateColumnSelection() {
      super.validateColumnSelection();
      ColumnSelection columns = getTable().getColumnSelection();

      if(xquery instanceof TabularQuery && columns != null) {
         TabularQuery tabularQuery = (TabularQuery) xquery;

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef col = (ColumnRef) columns.getAttribute(i);

            if(tabularQuery.getColumnType(col.getName()) != null) {
               col.setDataType(tabularQuery.getColumnType(col.getName()));
            }
         }
      }
   }
}
