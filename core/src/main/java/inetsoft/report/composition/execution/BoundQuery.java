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
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.QueryTreeModel.QueryNode;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.XUtil;
import inetsoft.util.GroupedThread;
import inetsoft.util.log.LogContext;

import java.util.*;

/**
 * Bound query executes a bound table assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class BoundQuery extends AssetQuery {
   /**
    * Create an asset query.
    */
   public BoundQuery(int mode, AssetQuerySandbox box, boolean stable, boolean metadata) {
      super(mode, box, stable, metadata);
   }

   /**
    * Create an asset query.
    */
   public BoundQuery(int mode, AssetQuerySandbox box, BoundTableAssembly table, boolean stable,
                     boolean metadata)
      throws Exception
   {
      this(mode, box, stable, metadata);
      this.table = table;
      this.table.update();

      SourceInfo sinfo = table.getSourceInfo();
      String source = sinfo.getSource();
      xquery = getXQuery(source);
      oquery = (xquery instanceof JDBCQuery) ? (JDBCQuery) xquery.clone() : null;
      nquery = (oquery == null) ? null : (JDBCQuery) oquery.clone();

      if(nquery != null) {
         nquery.setName(box.getWSName() + "." + getTableDescription(table.getName()));
         SQLDefinition sql = nquery.getSQLDefinition();

         if(sql instanceof UniformSQL) {
            UniformSQL usql = (UniformSQL) sql;
            XJoin[] joins = usql.getJoins();
            List<XJoin> list = joins == null ? null : Arrays.asList(joins);
            usql.setOriginalJoins(list);
         }
      }

      smergeable = XUtil.isQueryMergeable(oquery);
      smergeable = super.isSourceMergeable() && smergeable;

      if(smergeable && nquery != null) {
         // set data source
         UniformSQL nsql = (UniformSQL) nquery.getSQLDefinition();

         if(!nsql.isParseSQL() || !nsql.isLossy()) {
            nsql.clearSQLString();
         }

         nsql.setDataSource((JDBCDataSource) nquery.getDataSource());
      }

      if(nquery != null) {
         nquery.setMaxRows(0);
      }
   }

   /**
    * Gets the log/audit record for this query.
    *
    * @return the record object or <tt>null</tt> if none.
    */
   protected Collection<?> getLogRecord() {
      if(xquery != null) {
         String name = getDataSourceLogRecord() + xquery.getName();
         return Collections.singleton(LogContext.QUERY.getRecord(name));
      }

      return Collections.emptySet();
   }

   protected String getDataSourceLogRecord() {
      if(xquery != null && xquery.getDataSource() != null) {
         StringBuilder name = new StringBuilder()
            .append(xquery.getDataSource().getFullName());

         if(xquery.getFolder() != null) {
            name.append('/').append(xquery.getFolder());
         }

         return name.append('.').toString();
      }

      return "";
   }

   /**
    * Get the query time out.
    */
   @Override
   protected int getQueryTimeOut() {
      SourceInfo sinfo = table.getSourceInfo();
      String source = sinfo.getSource();
      XQuery xquery = null;

      try {
         xquery = getXQuery(source);
      }
      catch(Exception ex) {
         // ignore it
      }

      if(xquery == null) {
         return 0;
      }

      int timeout = xquery.getTimeout();
      return timeout <= 0 || timeout == Integer.MAX_VALUE ? 0 : timeout;
   }

   /**
    * Get the catalog.
    * @return the catalog part in sql statement if any.
    */
   @Override
   public String getCatalog() {
      if(oquery == null) {
         return null;
      }

      UniformSQL sql = oquery.getSQLDefinition() instanceof UniformSQL ?
         (UniformSQL) oquery.getSQLDefinition() : null;

      for(int i = 0; sql != null && i < sql.getTableCount(); i++) {
         SelectTable stable = sql.getSelectTable(i);
         String catalog = stable.getCatalog();

         if(catalog != null && catalog.length() > 0) {
            return catalog;
         }

         Object tobj = stable.getName();

         if(!(tobj instanceof String)) {
            continue;
         }

         catalog = XUtil.getCatalog((String) tobj);

         if(catalog != null && catalog.length() > 0) {
            return catalog;
         }
      }

      return null;
   }

   /**
    * Get the user defined max rows.
    * @return the user defined max rows.
    */
   @Override
   protected int getDefinedMaxRows() throws Exception {
      int max = super.getDefinedMaxRows();

      if(oquery != null) {
         int qmax = oquery.getMaxRows();

         if(qmax > 0) {
            max = max > 0 ? Math.min(max, qmax) : qmax;
         }
      }

      return Math.max(0, max);
   }

   /**
    * Get the table assembly to be executed.
    * @return the table assembly.
    */
   @Override
   protected TableAssembly getTable() {
      return table;
   }

   @Override
   public TableLens getTableLens(VariableTable vars) throws Exception {
      return GroupedThread.runWithRecordContext(this::getLogRecord, () -> super.getTableLens(vars));
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
      xquery.setProperty("queryManager", box.getQueryManager());

      if(AssetQuerySandbox.isLiveMode(mode) && isDriversDataCached()) {
         int max = box.getWorksheet().getWorksheetInfo().getDesignMaxRows();

         // set property regardless whether it's 0 so the cache knows it's design time
         // let spark cache select samples from cached query
         vars.put("__HINT_CACHE_SAMPLES__", max + "");
      }

      XSessionManager manager = XSessionManager.getSessionManager();
      final AssetQuerySandbox oldBox = WSExecution.getAssetQuerySandbox();
      WSExecution.setAssetQuerySandbox(box);

      try {
         return manager.getXNodeTableLens(xquery, vars, box.getUser(),
                                          null, null, touchtime);
      }
      finally {
         WSExecution.setAssetQuerySandbox(oldBox);
      }
   }

   public void clearQueryCache(VariableTable vars) throws Exception {
      XSessionManager manager = XSessionManager.getSessionManager();
      WSExecution.setAssetQuerySandbox(box);
      this.merge(vars);

      try {
         if(xquery != null) {
            manager.removeQueryCacheData(xquery, vars, box.getUser(), XNodeTableLens.class);
         }
      }
      finally {
         WSExecution.setAssetQuerySandbox(null);
      }
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() throws Exception {
      if(this instanceof SQLBoundQuery) {
         return super.isSourceMergeable();
      }

      return smergeable;
   }

   @Override
   protected boolean isMergePreferred() {
      if(isDriversDataCached() && getAllSQLExpressions().isEmpty()) {
         return false;
      }

      return super.isMergePreferred();
   }

   /**
    * Merge the query.
    */
   @Override
   public void merge(VariableTable vars) throws Exception {
      super.merge(vars);

      if(nquery != null) {
         nquery.setVPMEnabled(box.isVPMEnabled());
         nquery = (JDBCQuery) VpmProcessor.getInstance()
            .applyConditions(nquery, vars, !isPlan(), box.getUser());
         // Bug #63895, make sure UniformSQL.vpmCondition is set as soon as possible to prevent
         // invalid queries from limit clauses
         UniformSQL uniformSQL = getUniformSQL();

         if(uniformSQL != null) {
            uniformSQL.setVPMCondition(nquery.getProperty("vpmCondition") != null);
         }

         nquery = (JDBCQuery) VpmProcessor.getInstance()
            .applyHiddenColumns(nquery, vars, box.getUser());
         nquery.setVPMEnabled(false);
      }
   }

   /**
    * Merge from clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean mergeFrom(VariableTable vars) throws Exception {
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
    * Check if is an qualified name.
    * @param name the specified name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isQualifiedName(String name) {
      if(super.isQualifiedName(name)) {
         return true;
      }

      UniformSQL osql = oquery  == null ? null : (UniformSQL) oquery.getSQLDefinition();
      return osql != null && osql.isTableColumn(name);
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   protected ColumnSelection getDefaultColumnSelection0() {
      return box.getDefaultColumnSelection(table.getSourceInfo(),
                                           table.getColumnSelection());
   }

   /**
    * Check if is a sql expression.
    * @param ref the specified base data ref.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSQLExpression(DataRef ref) {
      if(super.isSQLExpression(ref)) {
         return true;
      }

      if(!ref.isExpression() && oquery != null) {
         UniformSQL osql = (UniformSQL) oquery.getSQLDefinition();
         JDBCSelection oselection = (JDBCSelection) osql.getSelection();
         AttributeRef attr = (AttributeRef) ref;
         String column = getAttributeColumn(attr);

         if(column == null) {
            return false;
         }

         return oselection.getTable(column) == null;
      }

      return false;
   }

   /**
    * Get the index of an attribute column.
    * @param column the specified attribute column.
    * @return the index of the attribute column.
    */
   protected int indexOfAttributeColumn(AttributeRef column) {
      if(column == null) {
         return -1;
      }

      UniformSQL osql = (UniformSQL) oquery.getSQLDefinition();
      XSelection oselection = osql.getSelection();
      String name = getAttributeString(column);
      return oselection.indexOfColumn(name);
   }

   /**
    * Get the string representation of an attribute column.
    * @param column the specified attribute column.
    * @return the string representation of the attribute column.
    */
   @Override
   protected String getAttributeColumn(AttributeRef column) {
      if(column == null || !(oquery.getSQLDefinition() instanceof UniformSQL)) {
         return null;
      }

      UniformSQL osql = (UniformSQL) oquery.getSQLDefinition();

      XSelection oselection = osql.getSelection();
      String name = getAttributeString(column);
      int index = oselection.indexOfColumn(name, false, true);

      if(index == -1) {
         name = oselection.getAliasColumn(name);

         if(name != null) {
            index = oselection.indexOfColumn(name);
         }
      }

      return index == -1 ? null : oselection.getColumn(index);
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

      DataRef base = getBaseAttribute(column);

      if(base.isExpression()) {
         String name = base.getName();
         return requiresUpperCasedAlias(name) ? name.toUpperCase() : name;
      }

      UniformSQL osql = (UniformSQL) oquery.getSQLDefinition();
      XSelection oselection = osql.getSelection();
      String name = getAttributeString(base);
      SQLHelper helper = SQLHelper.getSQLHelper(osql);

      // @by larryl, use indexOfColumn would not work if a column is used
      // twice with different aliases
      for(int i = 0; i < oselection.getColumnCount(); i++) {
         if(name.equals(oselection.getAlias(i)) ||
            helper instanceof AccessSQLHelper && name.equalsIgnoreCase(oselection.getAlias(i)))
         {
            alias = name;
            break;
         }
      }

      if(alias == null) {
         int index = oselection.indexOfColumn(name);

         if(index == -1) {
            String name2 = oselection.getAliasColumn(name);

            if(name2 != null) {
               return requiresUpperCasedAlias(name) ? name.toUpperCase() : name;
            }
         }

         if(index == -1) {
            return null;
         }

         alias = oselection.getAlias(index);
      }

      return alias != null && requiresUpperCasedAlias(alias) ?
         alias.toUpperCase() : alias;
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
      return nquery == null ? null : nquery.getSQLDefinition() instanceof UniformSQL ?
         (UniformSQL) nquery.getSQLDefinition() : null;
   }

   /**
    * Get pre condition list.
    * @return the pre condition list.
    */
   @Override
   public ConditionList getPreConditionList() {
      if(preconds == null) {
         ConditionListWrapper wrapper = getTable().getPreConditionList();
         preconds = wrapper.getConditionList();

         // ignore filtering? just remove all the conditions
         if(preconds != null && box.isIgnoreFiltering()) {
            preconds.removeAllItems();
         }

         if(!box.isIgnoreFiltering()) {
            ConditionAssembly[] cassemblies = table.getConditionAssemblies();
            ColumnSelection columns = getTable().getColumnSelection();

            if(cassemblies.length != 0) {
               ConditionList conds = new ConditionList();
               boolean added = preconds.getSize() > 1;

               for(int i = 0; i < preconds.getSize(); i++) {
                  HierarchyItem item = preconds.getItem(i);

                  if(added) {
                     item.setLevel(item.getLevel() + 1);
                  }

                  conds.append(item);
               }

               for(ConditionAssembly cassembly : cassemblies) {
                  ConditionList temp = cassembly.getConditionList();

                  if(conds.getSize() > 0 &&
                     conds.isConditionItem(conds.getSize() - 1) &&
                     temp.getSize() > 0) {
                     conds.append(
                        new JunctionOperator(JunctionOperator.AND, 0));
                  }

                  added = temp.getSize() > 1;

                  for(int j = 0; j < temp.getSize(); j++) {
                     HierarchyItem item = temp.getItem(j);

                     if(added) {
                        item.setLevel(item.getLevel() + 1);
                     }

                     if(j % 2 == 0) {
                        ConditionItem citem = (ConditionItem) item;
                        DataRef attr = citem.getAttribute();

                        if(!columns.containsAttribute(attr)) {
                           ColumnRef column = findColumn(attr);
                           column.setVisible(false);
                           columns.addAttribute(column);
                        }
                     }

                     conds.append(item);
                  }
               }

               preconds = conds;
            }
         }

         ConditionListWrapper rwrapper =
            getTable().getPreRuntimeConditionList();
         List<ConditionList> list = new ArrayList<>();
         list.add(preconds);
         list.add(rwrapper == null ? null : rwrapper.getConditionList());
         preconds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
         preconds = preconds == null ? new ConditionList() : preconds;

         validateConditionList(preconds, false);
      }

      return preconds;
   }

   /**
    * Get the xquery object.
    * @param source the specified source.
    */
   protected XQuery getXQuery(String source) throws Exception {
      return getSourceQuery(source);
   }

   public static XQuery getSourceQuery(String source) throws Exception {
      return XUtil.getXQuery(source);
   }

   /**
    * Quote a column.
    * @param column the specified column.
    * @return the quoted column.
    */
   @Override
   protected String quoteColumn(String column) {
      if(column == null) {
         return column;
      }

      SQLHelper helper = getSQLHelper(getUniformSQL());
      return helper.quotePath(column);
   }

   /**
    * Check if is a table or table alias.
    * @param tname the specified table name.
    * @return <tt>true</tt> if is a table, <tt>false</tt> table alias.
    */
   protected boolean isTable(String tname) {
      UniformSQL sql = getUniformSQL();

      for(int i = 0; i < sql.getTableCount(); i++) {
         SelectTable stable = sql.getSelectTable(i);

         if(tname.equals(stable.getName())) {
            return true;
         }
         else if(tname.equals(stable.getAlias())) {
            return false;
         }
      }

      return true;
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

      XQuery xquery = this.xquery;

      if(xquery instanceof JDBCQuery) {
         JDBCQuery jquery = (JDBCQuery) xquery;
         jquery.setVPMEnabled(box.isVPMEnabled());
         jquery = (JDBCQuery) VpmProcessor.getInstance().applyConditions(jquery, vars, false, box.getUser());
         jquery = (JDBCQuery) VpmProcessor.getInstance().applyHiddenColumns(jquery, vars, box.getUser());
         jquery = (JDBCQuery) XUtil.clearComments(jquery);
         jquery.setVPMEnabled(false);

         xquery = jquery;
      }

      StringBuilder buffer = new StringBuilder();
      String desc = xquery.toString().trim();
      desc = catalog.getString("common.queryMerge", desc);
      buffer.append(getVariablesString(xquery, vars));
      buffer.append(desc);

      return getQueryNode(buffer.toString());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XMetaInfo getXMetaInfo(ColumnRef column, ColumnRef original) {
      DataRef attr = getRefForXMetaInfo(column);

      if(!(attr instanceof AttributeRef) || oquery == null ||
         !(oquery.getSQLDefinition() instanceof UniformSQL))
      {
         return null;
      }

      UniformSQL osql = (UniformSQL) oquery.getSQLDefinition();
      XSelection oselection = osql.getSelection();
      int index = indexOfAttributeColumn((AttributeRef) attr);
      XMetaInfo info = index < 0 ? null : oselection.getXMetaInfo(index);

      if(info != null) {
         info = info.clone();
         XDrillInfo dinfo = info.getXDrillInfo();

         if(dinfo != null) {
            dinfo.setColumn(attr);
         }
      }

      return fixMetaInfo(info, column, column);
   }

   /**
    * Check if the column should be added to the merged selection.
    * @param column the column to check for
    * @return <tt>true</tt> if exists, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isColumnExists(DataRef column) {
      return isColumnOnSelection(column, getUniformSQL().getBackupSelection());
   }

   protected BoundTableAssembly table; // bound table assembly
   protected JDBCQuery nquery; // new query
   protected JDBCQuery oquery; // original jdbc query
   protected XQuery xquery; // original jdbc query
   private boolean smergeable; // source mergeable
}
