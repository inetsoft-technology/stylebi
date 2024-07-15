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

import inetsoft.report.TableLens;
import inetsoft.report.composition.QueryTreeModel.QueryNode;
import inetsoft.report.internal.table.XTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;

/**
 * Data query retrieves table data from the table.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class DataQuery extends AssetQuery {
   /**
    * Create an asset query.
    */
   public DataQuery(int mode, AssetQuerySandbox box, DataTableAssembly table,
                    boolean stable, boolean metadata)
   {
      super(mode, box, stable, metadata);

      this.table = table;
      this.table.update();
   }

   /**
    * Get the post process base table lens.
    * @param vars the specified variable table.
    * @return the post process base table lens of the query.
    */
   @Override
   protected TableLens getPostBaseTableLens(VariableTable vars) throws Exception
   {
      XTable data = table.getData();
      return (data instanceof TableLens) ?
         (TableLens) data : new XTableLens(data);
   }

   /**
    * Get the table.
    * @param vars the specified variable table.
    * @return the table of the query.
    */
   @Override
   public TableLens getTableLens(VariableTable vars) throws Exception {
      TableLens data = getPostBaseTableLens(vars);
      data = new TableFilter2(data);
      data = getSortTableLens(data, vars);

      if((mode & AssetQuerySandbox.RUNTIME_MODE) != 0) {
         data = getVisibleTableLens(data, vars);
      }

      return data;
   }

   /**
    * Get the table assembly to be executed.
    * @return the table assembly.
    */
   @Override
   protected TableAssembly getTable() {
      return table;
   }

   /**
    * Merge from clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean mergeFrom(VariableTable vars) {
      // do nothing
      return true;
   }

   /**
    * If the merged columns are in a subquery, return the name
    * of the subquery (e.g. select col1 from (select ...) sub1).
    */
   @Override
   protected String getMergedTableName(ColumnRef column) {
      return null;
   }

   /**
    * Validate the column selection.
    */
   @Override
   public void validateColumnSelection() {
      // do nothing
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   protected ColumnSelection getDefaultColumnSelection0() {
      return new ColumnSelection();
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() {
      return false;
   }

   /**
    * Get the string representation of an attribute column.
    * @param column the specified attribute column.
    * @return the string representation of the attribute column.
    */
   @Override
   protected String getAttributeColumn(AttributeRef column) {
      return null;
   }

   /**
    * Get the alias of a column.
    * @param attr the specified column.
    * @return the alias of the column, <tt>null</tt> if not exists.
    */
   @Override
   protected String getAlias(ColumnRef attr) {
      return null;
   }

   /**
    * Get the target jdbc query to merge into.
    * @return the target jdbc query to merge into.
    */
   @Override
   public JDBCQuery getQuery0() {
      return null;
   }

   /**
    * Get the target sql to merge into.
    * @return the target sql to merge into.
    */
   @Override
   protected UniformSQL getUniformSQL() {
      return null;
   }

   /**
    * Get a text description of how the query will be executed.
    * @return the text description.
    */
   @Override
   public QueryNode getQueryPlan() throws Exception {
      StringBuilder sb = new StringBuilder();
      sb.append(catalog.getString("Data Query"));
      sb.append(".");

      return getQueryNode(sb.toString());
   }

   /**
    * Get the catalog.
    * @return the catalog part in sql statement if any.
    */
   @Override
   public String getCatalog() {
      return null;
   }

   private DataTableAssembly table;
}
