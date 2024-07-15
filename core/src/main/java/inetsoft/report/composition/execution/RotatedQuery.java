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
import inetsoft.report.lens.RotatedTableLens;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rotated query executes a rotated table assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RotatedQuery extends AssetQuery {
   /**
    * Create an asset query.
    */
   public RotatedQuery(int mode, AssetQuerySandbox box, RotatedTableAssembly table,
                       boolean stable, boolean metadata, long ts)
      throws Exception
   {
      super(mode, box, stable, metadata);

      this.table = table;
      this.table.update();
      TableAssembly[] tables = table.getTableAssemblies(true);
      TableAssembly mirror = tables[0];
      this.query = AssetQuery.createAssetQuery(mirror, fixSubQueryMode(mode),
                                               box, true, ts, false, metadata);
      this.query.setSubQuery(true);
      this.query.plan = this.plan;
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
    * Get the post process base table.
    * @param vars the specified variable table.
    * @return the post process base table of the query.
    */
   @Override
   protected TableLens getPostBaseTableLens(VariableTable vars)
      throws Exception
   {
      query.setSubQuery(false);
      TableLens table = query.getTableLens(vars);
      // clear the alert message
      // Tool.getUserMessage();
      table = AssetQuery.shuckOffFormat(table);
      RotatedTableLens rotated = new RotatedTableLens(table, 1);
      // limit number of columns so we don't get a table with thousands of cols
      table = rotated;
      return table;
   }

   /**
    * Fixed the column selection if necessary.
    */
   @Override
   protected void fixColumnSelection(TableLens table, VariableTable vars) {
      // If it is a rotated table and it contains aggregate
      // We should use the rotated table but not the aggregate table to fix
      // the column selection, otherwise, columns will be lost.
      AggregateInfo ainfo = getTable().getAggregateInfo();

      if(ainfo != null && !ainfo.isEmpty()) {
         try {
            table = getPostBaseTableLens(vars);
         }
         catch(Exception e) {
            LOG.debug("Failed to get post base lens", e);
            return;
         }
      }

      ColumnSelection columns = getTable().getColumnSelection();
      ColumnSelection ncolumns = new ColumnSelection();
      boolean fnull = false;

      // fix column selection
      if(table.moreRows(0)) {
         for(int i = 0; i < table.getColCount() &&  ncolumns.getAttributeCount() < 250; i++) {
            if(table.getObject(0, i) == null) {
               if(fnull) {
                  continue;
               }
               else  {
                  fnull = true;
               }
            }

            String header = AssetUtil.format(XUtil.getHeader(table, i));
            DataRef ocolumn = columns.getAttribute(header);

            // keep alias column, avoid creating columnref use alias as attribute which may cause
            // alias column be removed when validate columnselection.
            if(ocolumn instanceof ColumnRef &&
               Tool.equals(header, ((ColumnRef) ocolumn).getAlias()))
            {
               ncolumns.addAttribute(((ColumnRef) ocolumn).clone());
            }
            else {
               AttributeRef attr = new AttributeRef(header);
               ColumnRef column = new ColumnRef(attr);
               String dtype = Tool.getDataType(table.getColType(i));
               column.setDataType(dtype);
               ncolumns.addAttribute(column);
            }
         }

         AssetUtil.fixAlias(ncolumns);
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) columns.getAttribute(i);

         if(!col.isVisible() && !ncolumns.containsAttribute(col)) {
            ncolumns.addAttribute(col);
         }
      }

      validateColumnSelection(ncolumns, columns, true, true, false, true);
      getTable().setColumnSelection(getTable().getColumnSelection());
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
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   protected ColumnSelection getDefaultColumnSelection0() {
      return new NullColumnSelection();
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
    * Set the query level.
    * @param level the specified query level.
    */
   @Override
   public void setLevel(int level) {
      super.setLevel(level);
      query.setLevel(level + 1);
   }

   /**
    * Get a text description of how the query will be executed.
    * @return the text description.
    */
   @Override
   public QueryNode getQueryPlan() throws Exception {
      QueryNode plan = super.getQueryPlan();
      StringBuilder buffer = new StringBuilder();
      String desc = catalog.getString("common.rotateMerge");
      buffer.append(getVariablesString(null));
      buffer.append(desc);
      QueryNode qnode = (plan != null) ? plan : getQueryNode(buffer.toString());
      qnode.setRelation("Rotate");
      qnode.addNode(query.getQueryPlan());

      return qnode;
   }

   /**
    * Set the plan flag.
    * @param plan <tt>true</tt> if for plan only, <tt>false</tt> otherwise.
    */
   @Override
   public void setPlan(boolean plan) {
      super.setPlan(plan);
      query.setPlan(plan);
   }

   /**
    * Get the number of child queries.
    */
   @Override
   public int getChildCount() {
      return 1;
   }

   /**
    * Get the specified child query.
    */
   @Override
   public AssetQuery getChild(int idx) {
      return query;
   }

   /**
    * Get the catalog.
    * @return the catalog part in sql statement if any.
    */
   @Override
   public String getCatalog() {
      return query.getCatalog();
   }

   /**
    * Get query icon.
    */
   @Override
   protected String getIconPath() {
      return "/inetsoft/report/gui/composition/images/rotate.png";
   }

   private RotatedTableAssembly table;
   private AssetQuery query; // base query
   private static final Logger LOG =
      LoggerFactory.getLogger(RotatedQuery.class);
}
