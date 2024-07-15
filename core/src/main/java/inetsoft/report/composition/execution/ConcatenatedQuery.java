/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.composition.QueryTreeModel.QueryNode;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.SetTableLens;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;

/**
 * Concatenated query executes a concatenated table assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class ConcatenatedQuery extends AssetQuery {
   /**
    * Create an asset query.
    */
   public ConcatenatedQuery(int mode, AssetQuerySandbox box, ConcatenatedTableAssembly table,
                            boolean stable, boolean metadata, long ts)
      throws Exception
   {
      super(mode, box, stable, metadata);

      this.table = table;
      this.table.update();
      tables = table.getTableAssemblies(true);
      this.queries = new AssetQuery[tables.length];
      List<String> sqlCols = getSQLExpressions();

      for(int i = 0; i < queries.length; i++) {
         boolean root = "true".equals(tables[i].getProperty("MVRoot"));

         queries[i] = AssetQuery.createAssetQuery(
            tables[i], fixSubQueryMode(mode), box, true, ts, root, metadata);
         queries[i].setSubQuery(true);
         queries[i].plan = this.plan;
         queries[i].parentSQLExpressions.addAll(sqlCols);
      }

      colset = new HashSet<>();
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
    * get the table assemblies of the query.
    * @return table assemblies
    */
   private TableAssembly[] getTableAssemblies() {
      if(this.queries == null) {
         return this.table.getTableAssemblies(true);
      }

      TableAssembly[] assemblies = new TableAssembly[this.queries.length];

      for(int i = 0; i < this.queries.length; i++) {
         assemblies[i] = this.queries[i].getTable();
      }

      return assemblies;
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
      ThreadPoolExecutor executor =
         (ThreadPoolExecutor) Executors.newFixedThreadPool(5, new GroupedThreadFactory());

      try {
         TableAssemblyOperator lastOp = null;
         List<Future<TableLens>> results = new ArrayList<>();
         final Thread currentThread = Thread.currentThread();
         final List<String> infos = XUtil.QUERY_INFOS.get();
         final List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

         for(AssetQuery query : queries) {
            final AssetQuery q = query;
            results.add(executor.submit(() -> {
               q.setSubQuery(false);
               q.setTimeLimited(isTimeLimited());
               q.setQueryManager(getQueryManager());

               try {
                  WSExecution.setAssetQuerySandbox(box);
                  ThreadContext.inheritSession(currentThread);
                  XUtil.QUERY_INFOS.set(infos);
                  WorksheetService.ASSET_EXCEPTIONS.set(exs);
                  TableLens lens = q.getTableLens(vars);

                  if(lens == null) {
                     UserMessage userMessage = Tool.getUserMessage();

                     if(userMessage != null) {
                        final String msg = userMessage.getMessage();
                        final int level = userMessage.getLevel();
                        final LogLevel logLevel = userMessage.getLogLevel();
                        throw new MessageException(msg, logLevel, true, level);
                     }
                     else {
                        final String errorMessage =
                           Catalog.getCatalog().getString("common.table.getDataFailed");
                        throw new MessageException(errorMessage);
                     }
                  }

                  return AssetQuery.shuckOffFormat(lens);
               }
               finally {
                  WSExecution.setAssetQuerySandbox(null);
               }
            }));
         }

         TableLens table = results.get(0).get();
         String name = tables[0].getName();
         List<String> ids = new ArrayList<>();

         if(table == null) {
            return null;
         }

         for(int j = 0; j < table.getColCount(); j++) {
            String header = (String) Util.getHeader(table, j);
            String originalIdentifier = table.getColumnIdentifier(j);

            if(Tool.equals(XUtil.getDefaultColumnName(j), header) &&
               (StringUtils.isEmpty(originalIdentifier) || originalIdentifier.endsWith(".")))
            {
               header = "";
            }

            ids.add(name + "." + header);
         }

         for(int i = 1; i < tables.length; i++) {
            TableLens table2;

            try {
               table2 = results.get(i).get();
            }
            catch(ExecutionException ex) {
               MessageException mex = new MessageException(ex.getCause().getMessage());

               // need to log in case this is run from scheduler
               if(ex.getCause() instanceof MessageException) {
                  mex = (MessageException) ex.getCause();
                  LOG.warn("Subquery execution error: " + mex.getMessage());
               }
               else {
                  LOG.warn("Subquery execution error: " + ex.getCause(), ex);
               }

               throw mex;
            }

            String ltable = tables[i - 1].getName();
            String rtable = tables[i].getName();
            TableAssemblyOperator top = this.table.getOperator(ltable, rtable);
            int operation = top.getOperator(0).getOperation();
            boolean distinct = top.getOperator(0).isDistinct();

            // repeated operation?
            if(lastOp != null && table instanceof SetTableLens &&
               lastOp.getOperator(0).getOperation() == operation &&
               lastOp.getOperator(0).isDistinct() == distinct)
            {
               ((SetTableLens) table).setTable(
                  ((SetTableLens) table).getTableCount(), table2);
            }
            // union?
            else if(operation == TableAssemblyOperator.UNION) {
               table = PostProcessor.union(table, table2, distinct);
            }
            // intersect?
            else if(operation == TableAssemblyOperator.INTERSECT) {
               table = PostProcessor.intersect(table, table2);
            }
            // minus?
            else if(operation == TableAssemblyOperator.MINUS) {
               table = PostProcessor.minus(table, table2);
            }
            else {
               throw new RuntimeException("Unsupported operation found: " + operation);
            }

            lastOp = top;
         }

         for(int i = 0; i < table.getColCount(); i++) {
            table.setColumnIdentifier(i, ids.get(i));
         }

         return table;
      }
      finally {
         if(!executor.isShutdown()) {
            executor.shutdown();
         }
      }
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() throws Exception {
      if(!super.isSourceMergeable()) {
         return false;
      }

      String source = table.getSource();

      if(source == null) {
         return false;
      }

      Object datasource0 = null;
      String info = null;

      if(tables.length == 2) {
         String ltable = tables[0].getName();
         String rtable = tables[1].getName();
         TableAssemblyOperator top = table.getOperator(ltable, rtable);
         TableAssemblyOperator.Operator op = top.getOperator(0);
         int operation = op.getOperation();

         switch(operation) {
         case TableAssemblyOperator.UNION:
            info = SQLHelper.UNION_TABLE;
            break;
         case TableAssemblyOperator.INTERSECT:
            info = SQLHelper.INTERSECT_TABLE;
            break;
         case TableAssemblyOperator.MINUS:
            info = SQLHelper.MINUS_TABLE;
            break;
         default:
            throw new RuntimeException("Unsupported operation found: " +
                                       operation);
         }
      }

      SQLHelper helper = null;
      JDBCQuery query = null;

      for(int i = 0; i < queries.length; i++) {
         if(!queries[i].isQueryMergeable(true)) {
            return false;
         }

	      query = queries[i].getQuery();

         if(i == 0) {
            datasource0 = query.getDataSource();
         }
         else if(!datasource0.equals(query.getDataSource())) {
            return false;
         }

         UniformSQL sql = queries[0].getUniformSQL();
         helper = helper == null ? getSQLHelper(sql) : helper;

         if(!helper.supportsOperation(SQLHelper.CONCATENATION_TABLE, info)) {
            return false;
         }

         final boolean maxRowsSupported = helper.supportsOperation(SQLHelper.MAXROWS, info);

         if(!maxRowsSupported && queries[i].getDefinedMaxRows(false) > 0) {
            return false;
         }
      }

      String product = query == null ?
         null : SQLHelper.getProductName(query.getDataSource());

      // is db ? same column name is required
      if("db2".equals(product)) {
         String[] names = null;

         for(int i = 0; i < queries.length; i++) {
            ColumnSelection columns = tables[i].getColumnSelection(true);
            names = i > 0 ? names : new String[columns.getAttributeCount()];

            for(int j = 0; j < columns.getAttributeCount(); j++) {
               ColumnRef column = (ColumnRef) columns.getAttribute(j);
               String name = getName(column).toLowerCase();

               if(i == 0) {
                  names[j] = name;
               }
               else if(j >= names.length || !names[j].equals(name)) {
                  return false;
               }
            }
         }
      }

      return true;
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

      String col = getAttributeString(column);
      colset.add(col);
      return col;
   }

   /**
    * Get the name of a column.
    * @param column the specified column.
    * @return the name of the column.
    */
   private String getName(ColumnRef column) {
      String alias = column.getAlias();
      return alias != null && alias.length() > 0 ?
         alias : column.getAttribute();
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

      if(attr.isExpression()) {
         String name = attr.getName();
         return requiresUpperCasedAlias(name) ? name.toUpperCase() : name;
      }

      return null;
   }

   /**
    * Get the target jdbc query to merge into.
    * @return the target jdbc query to merge into.
    */
   @Override
   public JDBCQuery getQuery0() throws Exception {
      if(!isSourceMergeable()) {
         return null;
      }

      if(nquery == null) {
         JDBCQuery query0 = queries[0].getQuery();
         nquery = (JDBCQuery) query0.clone();
         nquery.setSQLDefinition(new UniformSQL());
         nquery.setMaxRows(0);
         nquery.setName(box.getWSName() + "." +
                        getTableDescription(table.getName()));
      }

      return nquery;
   }

   /**
    * Get the target sql to merge into.
    * @return the target sql to merge into.
    */
   @Override
   protected UniformSQL getUniformSQL() {
      try {
         getQuery();
      }
      catch(Exception ex) {
         LOG.warn("Failed to get query for uniform SQL", ex);
      }

      return nquery == null ? null : (UniformSQL) nquery.getSQLDefinition();
   }

   /**
    * Merge the query.
    */
   @Override
   public void merge(VariableTable vars) throws Exception {
      if(!isSourceMergeable() || !isMergePreferred()) {
         ColumnSelection columns = getTable().getColumnSelection();

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) columns.getAttribute(i);
            column.setProcessed(false);
         }

         this.ginfo = (AggregateInfo) getAggregateInfo().clone();
         mergeOrderBy();
         return;
      }

      for(AssetQuery query : queries) {
         query.merge(vars);
      }

      super.merge(vars);
   }

   /**
    * Merge from clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean mergeFrom(VariableTable vars) throws Exception {
      StringBuilder sb = new StringBuilder();
      UniformSQL nsql = getUniformSQL();
      SQLHelper helper = getSQLHelper(nsql);
      boolean maxrows = helper.supportsOperation(SQLHelper.MAXROWS);
      String product = SQLHelper.getProductName(getQuery().getDataSource());
      boolean minus = "oracle".equals(product);
      UniformSQL fsql = null;
      Principal user = box.getUser();

      String[] roles = XUtil.getUserRoleNames(user);
      String[] groups = XUtil.getUserGroups(user);

      // same as JDBCHandler
      if(user != null && vars != null) {
         vars = (VariableTable) vars.clone();

         if(!vars.contains("_USER_")) {
            vars.put("_USER_", XUtil.getUserName(user));
         }

         if(roles != null && !vars.contains("_ROLES_")) {
            vars.put("_ROLES_", roles);
         }

         if(groups != null && !vars.contains("_GROUPS_")) {
            vars.put("_GROUPS_", groups);
         }
      }

      for(int i = 0; i < queries.length; i++) {
         JDBCQuery query = queries[i].getQuery();
         UniformSQL sql = queries[i].getUniformSQL();
         sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);
         sql.clearSQLString();
         query.validateConditions(vars);
         sql.setSubQuery(true);
         sql.removeAllOrderByFields();

         if(i == 0) {
            fsql = sql;
         }

         if(i > 1) {
            sb.insert(0, '(');
            sb.append(')');
         }

         if(i > 0) {
            String ltable = tables[i - 1].getName();
            String rtable = tables[i].getName();
            TableAssemblyOperator top = table.getOperator(ltable, rtable);
            TableAssemblyOperator.Operator op = top.getOperator(0);
            int operation = op.getOperation();

            switch(operation) {
               case TableAssemblyOperator.UNION:
                  sb.append(" UNION ");
                  break;
               case TableAssemblyOperator.INTERSECT:
                  sb.append(" INTERSECT ");
                  break;
               case TableAssemblyOperator.MINUS:
                  if(!minus) {
                     sb.append(" EXCEPT ");
                  }
                  else {
                     sb.append(" MINUS ");
                  }

                  break;
               default:
                  throw new RuntimeException("Unsupported operation found: " +
                                             operation);
            }

            if(!op.isDistinct()) {
               sb.append("ALL ");
            }
         }

         sb.append('(');

         // set the max rows hint to this sub sql directly, for
         // concatenation sql is not well supported in uniform sql
         if(mode == AssetQuerySandbox.LIVE_MODE && maxrows) {
            int max = box.getWorksheet().getWorksheetInfo().getDesignMaxRows();

            if(max > 0) {
               sql.setHint(UniformSQL.HINT_INPUT_MAXROWS, max + "");
            }
         }

         sb.append(sql.toString());
         sb.append(')');
      }

      UniformSQL ssql = new UniformSQL(sb.toString(), false);

      if(fsql != null) {
         ssql.setBackupSelection((XSelection) fsql.getSelection().clone());
         ssql.setSelection((XSelection) fsql.getSelection().clone());
      }

      nsql.addTable(tables[0].getName(), ssql);
      return true;
   }

   /**
    * If the merged columns are in a subquery, return the name
    * of the subquery (e.g. select col1 from (select ...) sub1).
    */
   @Override
   protected String getMergedTableName(ColumnRef column) {
      return tables[0].getName();
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

      return colset.contains(name);
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   protected ColumnSelection getDefaultColumnSelection0() {
      TableAssembly[] tables = getTableAssemblies();

      if(tables == null || tables.length == 0) {
         return null;
      }

      queries[0].validateColumnSelection();
      return ConcatenatedTableAssembly.getDefaultColumnSelection(tables);
   }

   /**
    * Set the query level.
    * @param level the specified query level.
    */
   @Override
   public void setLevel(int level) {
      super.setLevel(level);

      for(AssetQuery query : queries) {
         query.setLevel(level + 1);
      }
   }

   /**
    * Get a text description of how the query will be executed.
    * @return the text description.
    */
   @Override
   public QueryNode getQueryPlan() throws Exception {
      QueryNode plan = super.getQueryPlan();
      StringBuilder buffer = new StringBuilder();
      String desc = catalog.getString("common.concatenateMerge");
      buffer.append(getVariablesString(null));
      buffer.append(desc);
      QueryNode qnode = (plan != null) ? plan : getQueryNode(buffer.toString());
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < queries.length; i++) {
         QueryNode node = queries[i].getQueryPlan();
         qnode.addNode(node);

         sb.append(queries[i].getTable().getName());

         if(i < queries.length - 1) {
            sb.append(" ");
            sb.append(table.getOperator(i).getOperatorString());
            sb.append(" ");
         }
      }

      qnode.setRelation(sb.toString());

      return qnode;
   }

   /**
    * Set the plan flag.
    * @param plan <tt>true</tt> if for plan only, <tt>false</tt> otherwise.
    */
   @Override
   public void setPlan(boolean plan) {
      super.setPlan(plan);

      for(AssetQuery query : queries) {
         query.setPlan(plan);
      }
   }

   /**
    * Get the number of child queries.
    */
   @Override
   public int getChildCount() {
      return queries.length;
   }

   /**
    * Get the specified child query.
    */
   @Override
   public AssetQuery getChild(int idx) {
      return queries[idx];
   }

   /**
    * Get the catalog.
    * @return the catalog part in sql statement if any.
    */
   @Override
   public String getCatalog() {
      for(AssetQuery query : queries) {
         String catalog = query.getCatalog();

         if(catalog != null && catalog.length() > 0) {
            return catalog;
         }
      }

      return null;
   }

   /**
    * Get query icon.
    */
   @Override
   protected String getIconPath() {
      return "/inetsoft/report/gui/composition/images/concanate.png";
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XMetaInfo getXMetaInfo(ColumnRef column, ColumnRef original) {
      DataRef ref = getRefForXMetaInfo(column);

      if(!(ref instanceof AttributeRef)) {
         return null;
      }

      AttributeRef attr = (AttributeRef) ref;
      ColumnSelection cols = tables[0].getColumnSelection(true);
      ColumnRef column1 = null;
      ColumnRef column2 = null;

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef tcolumn = (ColumnRef) cols.getAttribute(i);

         if(attr.getAttribute().equals(tcolumn.getAlias())) {
            column1 = tcolumn;
            break;
         }

         if(attr.getAttribute().equals(tcolumn.getAttribute())) {
            column2 = tcolumn;
         }
      }

      ColumnRef tcolumn = column1 != null ?  column1 : column2;
      XMetaInfo info = tcolumn == null ? null : queries[0].getXMetaInfo(tcolumn, original);
      info = fixMetaInfo(info, tcolumn, column,
         queries == null || queries.length <= 0 ? null : queries[0]);

      // for concatenated table, and is auto created format info, ignore it
      // because it is meaningless, such as Month date range merge with
      // timeinstant, the month format is meaningless
      if(info != null && Util.isXFormatInfoReplaceable(info)) {
         info.setXFormatInfo(null);
      }

      return info;
   }

   private static final class GroupedThreadFactory implements ThreadFactory {
      @Override
      public Thread newThread(Runnable r) {
         GroupedThread thread = new GroupedThread(r);
         thread.setDaemon(true);
         return thread;
      }
   }

   @Override
   protected Object getQueryProperty(String name) {
      Object val = super.getQueryProperty(name);

      if(val == null) {
         for(AssetQuery query : queries) {
            val = query.getQueryProperty(name);

            if(val != null) {
               break;
            }
         }
      }

      return val;
   }

   private ConcatenatedTableAssembly table; // concatenated table assembly
   private TableAssembly[] tables; // base tables
   private AssetQuery[] queries; // base queries
   private JDBCQuery nquery; // new query
   protected Set<String> colset; // columns

   private static final Logger LOG =
      LoggerFactory.getLogger(ConcatenatedQuery.class);
}
