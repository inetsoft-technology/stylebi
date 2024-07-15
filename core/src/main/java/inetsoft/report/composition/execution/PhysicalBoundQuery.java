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

import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.report.TableLens;
import inetsoft.report.composition.QueryTreeModel.QueryNode;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.XUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Physical bound query executes a physical bound table assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class PhysicalBoundQuery extends BoundQuery {
   /**
    * Create an asset query.
    */
   public PhysicalBoundQuery(int mode, AssetQuerySandbox box, boolean stable, boolean metadata)
      throws Exception
   {
      super(mode, box, stable, metadata);

      venabled = true;
      colset = new HashSet();
   }

   /**
    * Create an asset query.
    */
   public PhysicalBoundQuery(int mode, AssetQuerySandbox box, BoundTableAssembly table,
                             boolean stable, boolean metadata)
      throws Exception
   {
      this(mode, box, stable, metadata);
      this.table = table;
      SourceInfo sinfo = table.getSourceInfo();

      nquery = new JDBCQuery();
      nquery.setUserQuery(true);
      XRepository repository = XFactory.getRepository();
      nquery.setDataSource(repository.getDataSource(sinfo.getPrefix()));
      nsql = new UniformSQL();
      nquery.setSQLDefinition(nsql);
      nquery.setName(box.getWSName() + "." + getTableDescription(table.getName()));

      // generate plain condition and column flags
      ConditionListWrapper rconds = table.getPreRuntimeConditionList();
      pcondition = table.getPreConditionList().getConditionSize() == 0 &&
         (rconds == null || rconds.getConditionSize() == 0);
      ColumnSelection columns = table.getColumnSelection();
      pcolumn = !table.isDistinct();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(column.isExpression()) {
            pcolumn = false;
            break;
         }

         if(column.getAlias() != null &&
            !column.getAlias().equals(getAttributeString(column)))
         {
            pcolumn = false;
            break;
         }
      }
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
    * Check if this physical bound query may be treated as a physical table
    * when being a sub query.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isPhysicalTable() {
      return getAggregateInfo().isEmpty() && pcondition && pcolumn;
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
      throw new Exception("Invalid caller found!");
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() throws Exception {
      return true;
   }

   @Override
   protected boolean isMergePreferred() {
      return true;
   }

   /**
    * Merge the query.
    */
   @Override
   public void merge(VariableTable vars) throws Exception {
      mergeFrom(vars); // merge from first
      super.merge(vars);

      if(nquery != null && venabled) {
         nquery.setVPMEnabled(box.isVPMEnabled());
         nquery = (JDBCQuery) VpmProcessor.getInstance().applyConditions(nquery, vars, false, box.getUser());
         nquery = (JDBCQuery) VpmProcessor.getInstance().applyHiddenColumns(nquery, vars, box.getUser());
         nquery.setVPMEnabled(false);
      }

      if(!isQueryMergeable(false)) {
         int max = getMaxRows(false);

         if(max > 0) {
            vars.put(XQuery.HINT_MAX_ROWS, max + "");
         }
      }
   }

   /**
    * set whether enable vpm when merge sql.
    * @param enabled <tt>true</tt> to enable vpm, <tt>false</tt> otherwise.
    */
   public void setVPMEnabled(boolean enabled) {
      this.venabled = enabled;
   }

   /**
    * check if enable vpm when merge sql.
    */
   public boolean isVPMEnabled() {
      return venabled;
   }

   /**
    * Merge from clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean mergeFrom(VariableTable vars) throws Exception {
      if(nsql.getTableCount() == 0) {
         SourceInfo sinfo = table.getSourceInfo();
         String table = sinfo.getSource();
         String schema = sinfo.getProperty(SourceInfo.SCHEMA);
         String catalog = sinfo.getProperty(SourceInfo.CATALOG);
         SelectTable stable = nsql.addTable(table, table);
         stable.setSchema(schema);
         stable.setCatalog(catalog);
      }

      return true;
   }

   /**
    * If the merged columns are in a subquery, return the name
    * of the subquery (e.g. select col1 from (select ...) sub1).
    */
   @Override
   protected String getMergedTableName(ColumnRef column) {
      return column.isExpression() ? null : table.getSourceInfo().getSource();
   }

   /**
    * Get the catalog.
    * @return the catalog part in sql statement if any.
    */
   @Override
   public String getCatalog() {
      SourceInfo sinfo = table.getSourceInfo();
      return sinfo.getProperty(SourceInfo.CATALOG);
   }

   /**
    * Check if is an qualified name.
    * @param name the specified name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isQualifiedName(String name) {
      return colset.contains(name);
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   protected ColumnSelection getDefaultColumnSelection0() {
      if(AssetQuerySandbox.isRuntimeMode(mode)) {
         return new NullColumnSelection();
      }

      return box.getDefaultColumnSelection(table.getSourceInfo(),
                                           table.getColumnSelection());
   }

   /**
    * Get the string representation of an attribute column.
    * @param column the specified attribute column.
    * @return the string representation of the attribute column.
    */
   @Override
   protected String getAttributeColumn(AttributeRef column) {
      if(column == null) {
         return null;
      }

      SourceInfo sinfo = table.getSourceInfo();
      String table = sinfo.getSource();
      String col = table + "." + getAttributeString(column);
      colset.add(col);

      return col;
   }

   /**
    * Get the alias of a column.
    * @param column the specified column.
    * @return the alias of the column, <tt>null</tt> if not exists.
    */
   @Override
   protected String getAlias(ColumnRef column) {
      String alias = column.getAlias();

      if(alias != null) {
         return requiresUpperCasedAlias(alias) ? alias.toUpperCase() : alias;
      }

      DataRef attr = getBaseAttribute(column);
      alias = attr.getAttribute();

      return requiresUpperCasedAlias(alias) ? alias.toUpperCase() : alias;
   }

   /**
    * Get the target jdbc query to merge into.
    * @return the target jdbc query to merge into.
    */
   @Override
   public JDBCQuery getQuery0() {
      return nquery;
   }

   /**
    * Get the target sql to merge into.
    * @return the target sql to merge into.
    */
   @Override
   protected UniformSQL getUniformSQL() {
      return nquery == null ? null : (UniformSQL) nquery.getSQLDefinition();
   }

   /**
    * Get a text description of how the query will be executed.
    * @return the text description.
    */
   @Override
   public QueryNode getQueryPlan() throws Exception {
      VariableTable vars = new VariableTable();
      QueryNode plan = super.getQueryPlan();

      if(plan != null) {
         return plan;
      }

      JDBCQuery jquery = this.nquery;
      jquery.setVPMEnabled(box.isVPMEnabled());
      jquery = (JDBCQuery) VpmProcessor.getInstance().applyConditions(jquery, vars, false, box.getUser());
      jquery = (JDBCQuery) VpmProcessor.getInstance().applyHiddenColumns(jquery, vars,
                                                    box.getUser());
      jquery = (JDBCQuery) XUtil.clearComments(jquery);
      jquery.setVPMEnabled(false);

      StringBuilder buffer = new StringBuilder();
      buffer.append(getVariablesString(jquery, vars));
      String desc = jquery.toString().trim();
      buffer.append(desc);

      return getQueryNode(buffer.toString());
   }

   /**
    * Get query icon.
    */
   @Override
   protected String getIconPath() {
      return "/inetsoft/report/gui/composition/images/physical.png";
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XMetaInfo getXMetaInfo(ColumnRef column, ColumnRef original) {
      return null;
   }

   /**
    * Check if the column should be added to the merged selection.
    * @param column the column to check for
    * @return <tt>true</tt> if exists, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isColumnExists(DataRef column) {
      return true;
   }

   private JDBCQuery nquery;
   private UniformSQL nsql;
   private boolean pcondition; // plain condition
   private boolean pcolumn; // plain column
   private boolean venabled; // vpm enabled
   private Set colset;  // plain columns used in the query
}
