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
import inetsoft.report.composition.QueryTreeModel.QueryNode;
import inetsoft.report.internal.table.XTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.util.ArrayList;
import java.util.List;

/**
 * Un-pivot query executes a un-pivot table assembly.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class UnpivotQuery extends AssetQuery {
   /**
    * Create an asset query.
    */
   public UnpivotQuery(int mode, AssetQuerySandbox box, UnpivotTableAssembly table,
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
   protected TableLens getPostBaseTableLens(VariableTable vars) throws Exception {
      query.setSubQuery(false);
      TableLens lens = query.getTableLens(vars);
      lens = AssetQuery.shuckOffFormat(lens);
      int hcol = table.getHeaderColumns();
      XSwappableTable data = AssetUtil.unpivot(lens, hcol);
      XEmbeddedTable embedded = new XEmbeddedTable(data);
      applyChangedColumnsType(data, embedded);
      lens = new XTableLens(embedded);
      return lens;
   }

   private void applyChangedColumnsType(XSwappableTable data, XEmbeddedTable embedded)
      throws Exception
   {
      TableAssembly tableAssembly = getTable();

      if(tableAssembly instanceof UnpivotTableAssembly) {
         ColumnSelection columnSelection = tableAssembly.getColumnSelection(false);
         UnpivotTableAssemblyInfo tableInfo = (UnpivotTableAssemblyInfo) tableAssembly.getInfo();

         if(columnSelection == null) {
            return;
         }

         ColumnIndexMap columnIndexMap = new ColumnIndexMap(embedded);
         List<Integer> changeCols = new ArrayList<>();
         List<String> changeTypes = new ArrayList<>();
         List<Format> changeFormats = new ArrayList<>();
         List<Boolean> changeForces = new ArrayList<>();

         for(int i = 0; i < columnSelection.getAttributeCount(); i++) {
            if(!(columnSelection.getAttribute(i) instanceof ColumnRef)) {
               continue;
            }

            ColumnRef column = (ColumnRef) columnSelection.getAttribute(i);

            if(!tableInfo.columnTypeChanged(columnSelection.getAttribute(i))) {
               continue;
            }

            int col = AssetUtil.findColumn(embedded, column, columnIndexMap);
            Format format = tableInfo.getChangedTypeColumnFormat(column);

            if(col >= 0) {
               changeCols.add(col);
               changeTypes.add(column.getDataType());
               changeFormats.add(format);
               changeForces.add(tableInfo.forceParseDataByFormat(column));
            }
         }

         if(changeTypes.size() > 0) {
            embedded.setDataTypes(changeCols, changeTypes, changeFormats, data, changeForces);
         }
      }
   }

   /**
    * Fixed the column selection if necessary.
    */
   @Override
   protected void fixColumnSelection(TableLens table, VariableTable vars) {
      // @by yanie: bug1426616128222
      // If it is a unpivot table and it contains aggregate
      // We should use the unpivot table but not the aggregate table to fix
      // the column selection, otherwise, columns will be lost.
      AggregateInfo ainfo = getTable().getAggregateInfo();
      TableLens originalTable = table;

      if(ainfo != null && !ainfo.isEmpty()) {
         try {
            table = getPostBaseTableLens(vars);
         }
         catch(Exception e) {
            LOG.debug("Failed to get post base lens", e);
            return;
         }
      }

      fixColumnSelection0(table, false);

      if(ainfo != null && !ainfo.isEmpty() && ainfo.isCrosstab()) {
         fixColumnSelection0(originalTable, true);
      }
   }

   private void fixColumnSelection0(TableLens table, boolean isPublic) {
      ColumnSelection ncolumns = new ColumnSelection();
      boolean fnull = false;

      // fix column selection
      if(table.moreRows(0)) {
         final ColumnSelection ocolumns = getTable().getColumnSelection();

         for(int i = 0; i < table.getColCount(); i++) {
            if(table.getObject(0, i) == null) {
               if(fnull) {
                  continue;
               }
               else  {
                  fnull = true;
               }
            }

            final ColumnRef matchingCol = AssetUtil.findColumn(table, i, ocolumns);
            final String entity;

            if(matchingCol != null) {
               entity = matchingCol.getEntity();
            }
            else if(i < table.getColCount() - 2) {
               entity = this.table.getTableAssemblyName();
            }
            else {
               entity = null;
            }

            String id = table.getColumnIdentifier(i);
            String header = AssetUtil.format(XUtil.getHeader(table, i));
            AttributeRef attr = id != null && !id.equals(entity + "." + header) ?
               new AttributeRef(entity, id) : new AttributeRef(entity, header);
            ColumnRef column = new ColumnRef(attr);
            String dtype = Tool.getDataType(table.getColType(i));
            column.setDataType(dtype);
            ncolumns.addAttribute(column);
         }
      }

      // fix column type according to data value
      if(table.moreRows(1)) {
         for(int i = 0; i < table.getColCount(); i++) {
            Object obj = table.getObject(1, i);

            if(obj != null) {
               ColumnRef col = (ColumnRef) ncolumns.getAttribute(i);
               String type = Tool.getDataType(obj.getClass());

               if(!type.equals(col.getDataType())) {
                  col.setDataType(type);
               }
            }
         }
      }

      boolean bc = "true".equalsIgnoreCase(getTable().getProperty("BC_VALIDATE"));

      if((!(getTable() instanceof EmbeddedTableAssembly) || bc) && (box.isFixingAlias() || bc)) {
         AssetUtil.fixAlias(ncolumns);
      }

      ColumnSelection columns = getTable().getColumnSelection(isPublic);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) columns.getAttribute(i);

         if(!col.isVisible() && !ncolumns.containsAttribute(col)) {
            //ncolumns.addAttribute(col);
         }

         // update type to column selection
         int idx = ncolumns.indexOfAttribute(col);

         if(idx >= 0) {
            String ntype = ncolumns.getAttribute(idx).getDataType();
            col.setDataType(ntype);
         }
      }

      ncolumns.setProperty("public", "true");
      validateColumnSelection(ncolumns, columns, true, true, false, true);
   }

   @Override
   protected boolean isValidDateRange(ColumnRef column, ColumnSelection ncolumns,
                                      AggregateInfo aggregateInfo,
                                      boolean mv)
   {
      if(column.getDataRef() instanceof DateRangeRef && !mv) {
         DateRangeRef dateRangeRef = (DateRangeRef) column.getDataRef();
         boolean groupRef = aggregateInfo != null ? aggregateInfo.getGroup(column) != null
            : false;

         if(dateRangeRef.getDataRef() != null && groupRef) {
            DataRef baseRef = dateRangeRef.getDataRef();
            boolean invalidDateRange = !AssetUtil.isDateRangeValid(dateRangeRef, ncolumns) &&
               getTable() instanceof ComposedTableAssembly;

            if(!(baseRef instanceof ExpressionRef) && !columnSelectionContainsRef(baseRef, ncolumns) || invalidDateRange) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Validate data types.
    * @param base the specified table.
    * @param columns the specified column selection.
    */
   @Override
   protected void validateDataTypes(XTable base, ColumnSelection columns,
                                    ColumnIndexMap columnIndexMap)
   {
      // do nothing
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
      String desc = catalog.getString("common.unpivotMerge");
      buffer.append(getVariablesString(null));
      buffer.append(desc);
      QueryNode qnode = (plan != null) ? plan : getQueryNode(buffer.toString());
      qnode.setRelation("Un-pivot");
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
      return "/inetsoft/report/gui/composition/images/unpivot.png";
   }

   private UnpivotTableAssembly table;
   private AssetQuery query; // base query

   private static final Logger LOG =
      LoggerFactory.getLogger(UnpivotQuery.class);
}
