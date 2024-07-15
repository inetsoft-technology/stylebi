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
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.*;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
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

import java.util.*;
import java.util.concurrent.*;

/**
 * Join query executes a join table assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class JoinQuery extends AssetQuery {
   /**
    * Create an asset query.
    */
   public JoinQuery(int mode, AssetQuerySandbox box, AbstractJoinTableAssembly table,
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
         queries[i] = AssetQuery.createAssetQuery(
            tables[i], fixSubQueryMode(mode), box, true, ts, false, metadata);
         queries[i].setSubQuery(true);
         queries[i].setJoinSubTable(true);
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
    * {@inheritDoc}
    */
   @Override
   public XMetaInfo getXMetaInfo(ColumnRef column, ColumnRef original) {
      DataRef ref = getRefForXMetaInfo(column);

      if(!(ref instanceof AttributeRef)) {
         return null;
      }

      AttributeRef attr = (AttributeRef) ref;
      String tname = attr.getEntity();

      for(int i = 0; i < tables.length; i++) {
         if(tables[i].getName().equals(tname)) {
            ColumnSelection cols = tables[i].getColumnSelection(true);
            ColumnRef column1 = null;
            ColumnRef column2 = null;

            for(int j = 0; j < cols.getAttributeCount(); j++) {
               ColumnRef tcolumn = (ColumnRef) cols.getAttribute(j);

               if(attr.getAttribute().equals(tcolumn.getAlias())) {
                  column1 = tcolumn;
                  break;
               }

               if(attr.getAttribute().equals(tcolumn.getAttribute())) {
                  column2 = tcolumn;
               }
            }

            ColumnRef tcolumn = column1 != null ?  column1 : column2;
            XMetaInfo info = tcolumn == null ? null : queries[i].getXMetaInfo(tcolumn, original);
            return fixMetaInfo(info, tcolumn, column, queries[i]);
         }
      }

      return null;
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

      // @by stephenwebster, For bug1434053039968
      // Obtain the SQLHelper without the use of the Uniform SQL, which seemed
      // to cause an infinite loop.  Instead do the needful work to obtain the
      // the SQLHelper through the current query's datasource.
      JDBCQuery q1 = nquery == null ? queries[0].getQuery() : nquery;
      SQLHelper helper =
         q1 == null ? new SQLHelper() :
            SQLHelper.getSQLHelper(q1.getDataSource());
      boolean noMaxRows = !helper.supportsOperation(SQLHelper.MAXROWS);
      boolean noFullOuter = !helper.supportsOperation(SQLHelper.FULL_OUTERJOIN);
      int joinMaxrows = Integer.parseInt(SreeEnv.getProperty("join.table.maxrows", "0"));

      // fix Bug #56358, for unity mongo driver,
      // join support has issue when join query left and right table name contains quote.
      if("mongo".equals(helper.getSQLHelperType()) && queries != null) {
         for(int i = queries.length - 1; i >= 0; i--) {
            AssetQuery query = queries[i];

            if(query == null || query.getTable() == null) {
               continue;
            }

            String tableName = query.getTable().getName();

            if(!Tool.equals(tableName, XUtil.quoteName(tableName, helper))) {
               return false;
            }
         }
      }

      if(!helper.supportsOperation(SQLHelper.FROM_SUBQUERY)) {
         return false;
      }

      for(int i = 0; i < queries.length; i++) {
         if(!queries[i].isQueryMergeable(true)) {
            return false;
         }

         JDBCQuery query = queries[i].getQuery();

         if(i == 0) {
            datasource0 = query.getDataSource();
         }
         else if(!Tool.equals(datasource0, query.getDataSource())) {
            return false;
         }

         if(noMaxRows && (queries[i].getDefinedMaxRows(false) > 0 || joinMaxrows > 0)) {
            return false;
         }

         String ltable = tables[i].getName();

         for(int k = i + 1; k < tables.length; k++) {
            String rtable = tables[k].getName();
            TableAssemblyOperator top = table.getOperator(ltable, rtable);

            if(top == null) {
               continue;
            }

            if(top.isMergeJoin()) {
               return false;
            }
            else if(top.isFullJoin() && noFullOuter) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Get the post process base table.
    * @param vars the specified variable table.
    * @return the post process base table of the query.
    */
   @Override
   protected TableLens getPostBaseTableLens(final VariableTable vars)
      throws Exception
   {
      TableLens lens = getPostBaseTableLens0(vars);
      boolean metaView = AssetQuerySandbox.isDesignMode(mode);
      boolean detailView = !getAggregateInfo().isEmpty() && !getTable().isAggregate();

      if(metaView || detailView) {
         return lens;
      }

      ColumnSelection privateCols = getTable().getColumnSelection(false);
      ColumnSelection publicCols = getTable().getColumnSelection(true);
      ColumnSelection cols = lens.getColCount() == publicCols.getAttributeCount() ?
         publicCols : privateCols;
      Set<Integer> colMap = new LinkedHashSet<>();
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(lens);

      // since column orders of the join table are desided by the sub tables,
      // so arrange the columns as the public columnselection here to make sure columns orders
      // can be applied.
      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);
         int idx = AssetUtil.findColumn(lens, ref, false, true, columnIndexMap);

         if(idx < 0 && ref instanceof ColumnRef && ((ColumnRef) ref).getAlias() != null) {
            ref = ((ColumnRef) ref).clone();
            ((ColumnRef) ref).setAlias(null);
            idx = AssetUtil.findColumn(lens, ref, false, true, columnIndexMap);
         }

         if(idx < 0 && ref instanceof ColumnRef && ((ColumnRef) ref).getDataRef() instanceof DateRangeRef) {
            ref = new ColumnRef(((DateRangeRef) ((ColumnRef) ref).getDataRef()).getDataRef());
            idx = AssetUtil.findColumn(lens, ref, false, true, columnIndexMap);
         }

         if(idx >= 0) {
            colMap.add(idx);
         }
      }

      for(int i = 0; i < lens.getColCount(); i++) {
         if(!colMap.contains(i)) {
            colMap.add(i);
         }
      }

      // The filter will hide and also rearrange the columns back to the original order
      lens = new ColumnMapFilter(lens, colMap.stream()
         .mapToInt(Integer::intValue).toArray());

      return lens;
   }

   protected TableLens getPostBaseTableLens0(final VariableTable vars)
      throws Exception
   {
      ThreadPoolExecutor executor =
         (ThreadPoolExecutor) Executors.newFixedThreadPool(5, new GroupedThreadFactory());

      try {
         List<Future<TableLens>> results = new ArrayList<>();
         final List<String> infos = XUtil.QUERY_INFOS.get();
         final List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();
         final Thread currentThread = Thread.currentThread();

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
         List<String> ids = new ArrayList<>();
         String name = this.tables[0].getName();

         for(int j = 0; j < table.getColCount(); j++) {
            String header = Util.getHeader(table, j).toString();
            String originalIdentifier = table.getColumnIdentifier(j);

            if(Tool.equals(XUtil.getDefaultColumnName(j), header) &&
               (StringUtils.isEmpty(originalIdentifier) || originalIdentifier.endsWith(".")))
            {
               header = "";
            }

            ids.add(name + "." + header);
         }

         TableLens[] lenses = new TableLens[tables.length];
         lenses[0] = table;

         join1:
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

            name = this.tables[i].getName();

            for(int j = 0; j < table2.getColCount(); j++) {
               String header = Util.getHeader(table2, j).toString();
               String originalIdentifier = table2.getColumnIdentifier(j);

               if(Tool.equals(XUtil.getDefaultColumnName(j), header) &&
                  (StringUtils.isEmpty(originalIdentifier) || originalIdentifier.endsWith(".")))
               {
                  header = "";
               }

               ids.add(name + "." + header);
            }

            // at this point, table is the join from 0 to (i-1), and table2 is
            // table at i. Next we find all the join operators from {0..(i-1)} to
            // i.
            String rtable = tables[i].getName();
            Vector<Integer> lcolumns = new Vector<>(); // left column index in table
            Vector<Integer> rcolumns = new Vector<>(); // right column index in table2
            Vector<Integer> joins = new Vector<>(); // join operators
            boolean eqJoin = true; // true if regular equal joins
            int eqOp = XConstants.INNER_JOIN; // join operator for equal joins
            int colOffset = 0; // columns in table 0 - (k - 1)
            lenses[i] = table2;
            // @by davyc, if right table has any operation which is not cross join,
            // we should discard the cross join option, same as DB
            // fix bug1331802914432
            boolean shouldProcess = false;
            boolean crossJoin = true;

            for(int k = 0; k < i; colOffset += lenses[k].getColCount(), k++) {
               String ltable = tables[k].getName();
               TableAssemblyOperator top = this.table.getOperator(ltable, rtable);

               if(top == null) {
                  if(i == 1) {
                     eqJoin = false;
                  }

                  continue;
               }

               shouldProcess = true;

               // merge join? create a merged join table
               if(top.isMergeJoin()) {
                  table = new MergedJoinTableLens(table, table2);
                  continue join1;
               }
               // cross join? create a cross join table
               else if(top.isCrossJoin()) {
                  continue;
                  // table = new CrossJoinTableLens(table, table2);
                  // continue join1;
               }
               // ignore filtering? just create a cross join table
               else if(box.isIgnoreFiltering()) {
                  table = PostProcessor.crossJoin(table, table2);

                  // make sure the table doesn't get too big
                  table = PostProcessor.maxrows(table, 5000);
                  continue join1;
               }

               crossJoin = false;

               // create a join table
               TableAssemblyOperator.Operator[] ops = top.getOperators();

               for(TableAssemblyOperator.Operator op : ops) {
                  int type = op.getOperation();
                  DataRef lcolumn = op.getLeftAttribute();
                  DataRef rcolumn = op.getRightAttribute();
                  int lindex = AssetUtil.findColumn(lenses[k], lcolumn);
                  int rindex = AssetUtil.findColumn(table2, rcolumn);

                  if(lindex < 0) {
                     LOG.warn("Join column not found in left-hand table: " + lcolumn);
                     continue;
                  }

                  if(rindex < 0) {
                     LOG.warn("Join column not found in right-hand table: " + rcolumn);
                     continue;
                  }

                  lindex += colOffset; // index in joined table

                  lcolumns.add(lindex);
                  rcolumns.add(rindex);
                  joins.add(type);

                  if(!op.isKey()) {
                     eqJoin = false;
                  }
                  else {
                     eqOp = eqOp | type;
                  }
               }
            }

            if(shouldProcess && crossJoin) {
               table = PostProcessor.crossJoin(table, table2);
               continue join1;
            }

            TableLens ntable;

            // inner join?
            if(eqJoin) {
               int[] larr = new int[lcolumns.size()];
               int[] rarr = new int[rcolumns.size()];

               for(int j = 0; j < larr.length; j++) {
                  larr[j] = lcolumns.get(j);
               }

               for(int j = 0; j < rarr.length; j++) {
                  rarr[j] = rcolumns.get(j);
               }

               ntable = PostProcessor.join(table, table2, larr, rarr, eqOp);
            }
            // cross join?
            else {
               ntable = PostProcessor.crossJoin(table, table2);
            }

            // non-equal joins
            if(!eqJoin) {
               SelfJoinTableLens stable = new SelfJoinTableLens(ntable);

               for(int j = 0; j < joins.size(); j++) {
                  int lindex = lcolumns.get(j);
                  int rindex = rcolumns.get(j);
                  int type = joins.get(j);

                  rindex += table.getColCount();
                  stable.addJoin(lindex, type, rindex);
               }

               ntable = stable;
            }

            table = ntable;
         }

         for(int i = 0; i < table.getColCount(); i++) {
            table.setColumnIdentifier(i, ids.get(i));
         }

         return table;
      }
      catch(CrossJoinCellCountBeyondLimitException ex) {
         if(getTable() != null) {
            ex.setTableName(getTable().getName());
         }

         throw ex;
      }
      finally {
         // @by stephenwebster, Reclaim resources to prevent thread leak.
         if(!executor.isShutdown()) {
            executor.shutdown();
         }
      }
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
         nquery.setMaxRows(Integer.parseInt(SreeEnv.getProperty("join.table.maxrows", "0")));
         nquery.setName(box.getWSName() + "." + getTableDescription(table.getName()));
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
         LOG.warn("Failed to get the query for the uniform SQL", ex);
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

      boolean ptable = true;

      for(AssetQuery query : queries) {
         if(!(query instanceof PhysicalBoundQuery)) {
            ptable = false;
            break;
         }

         PhysicalBoundQuery pquery = (PhysicalBoundQuery) query;

         if(!((PhysicalBoundQuery) query).isPhysicalTable() || pquery.getMaxRows(false) > 0) {
            ptable = false;
            break;
         }
      }

      // all the sub queries may be treated as physical tables?
      // do not enable vpm in sub queries but in new query
      for(AssetQuery query : queries) {
         if(ptable) {
            ((PhysicalBoundQuery) query).setVPMEnabled(false);
         }

         query.merge(vars);
      }

      super.merge(vars);

      if(!ptable) {
         return;
      }

      // all the sub queries may be treated as physical tables?
      // replace sub queries with physical tables
      UniformSQL nsql = getUniformSQL();

      for(int i = 0; i < nsql.getTableCount(); i++) {
         SelectTable stable = nsql.getSelectTable(i);

         if(!(stable.getName() instanceof UniformSQL)) {
            continue;
         }

         UniformSQL sub = (UniformSQL) stable.getName();
         SelectTable subTable = sub.getSelectTable(0);

         stable.setSchema(subTable.getSchema());
         stable.setCatalog(subTable.getCatalog());
         stable.setName(subTable.getName());
      }

      nquery.setVPMEnabled(box.isVPMEnabled());
      nquery = (JDBCQuery) VpmProcessor.getInstance().applyConditions(nquery, vars, false, box.getUser());
      nquery = (JDBCQuery) VpmProcessor.getInstance().applyHiddenColumns(nquery, vars, box.getUser());
      nquery.setVPMEnabled(false);
   }

   /**
    * Merge from clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean mergeFrom(VariableTable vars) {
      UniformSQL nsql = getUniformSQL();
      List<XJoin> ojoins = new ArrayList<>();

      for(int i = 0; i < queries.length; i++) {
         UniformSQL sql = queries[i].getUniformSQL();
         sql.setSubQuery(true);
         // @by anton, clearing the order-by clause on subqueries can break the semantics of the query if the subquery
         // has a row limit.
//         sql.removeAllOrderByFields();
         sql.setCacheable(true);
         nsql.addTable(tables[i].getName(), sql);

         for(int ri = i + 1; ri < queries.length; ri++) {
            String ltable = tables[i].getName();
            String rtable = tables[ri].getName();
            TableAssemblyOperator top = table.getOperator(ltable, rtable);

            if(top == null) {
               continue;
            }

            int koperation = top.getKeyOperation();

            if(koperation == TableAssemblyOperator.CROSS_JOIN ||
               box.isIgnoreFiltering())
            {
               continue;
            }

            for(int j = 0; j < top.getOperatorCount(); j++) {
               TableAssemblyOperator.Operator op = top.getOperator(j);
               int operation = op.getOperation();

               ColumnRef lcolumn = (ColumnRef) op.getLeftAttribute();
               ColumnRef rcolumn = (ColumnRef) op.getRightAttribute();
               lcolumn = AssetUtil.findColumn(
                  tables[i].getColumnSelection(true), lcolumn);
               rcolumn = AssetUtil.findColumn(
                  tables[ri].getColumnSelection(true), rcolumn);

               if(lcolumn == null || rcolumn == null) {
                  continue;
               }

               DataRef lattr = AssetUtil.getOuterAttribute(ltable, lcolumn);
               DataRef rattr = AssetUtil.getOuterAttribute(rtable, rcolumn);
               ColumnRef lcolumn2 = new ColumnRef(lattr);
               lcolumn2 = findColumn(lcolumn2);
               ColumnRef rcolumn2 = new ColumnRef(rattr);
               rcolumn2 = findColumn(rcolumn2);
               String lcol = getColumn(lcolumn2);
               String rcol = getColumn(rcolumn2);
               // @by billh, delegate the quote function to sql helper
               // lcol = quoteColumn(lcol);
               // rcol = quoteColumn(rcol);

               XExpression lexp = new XExpression(lcol, XExpression.EXPRESSION);
               XExpression rexp = new XExpression(rcol, XExpression.EXPRESSION);
               String symbol;

               switch(operation) {
               case TableAssemblyOperator.INNER_JOIN:
                  symbol = "=";
                  break;
               case TableAssemblyOperator.LEFT_JOIN:
                  symbol = "*=";
                  break;
               case TableAssemblyOperator.RIGHT_JOIN:
                  symbol = "=*";
                  break;
               case TableAssemblyOperator.FULL_JOIN:
                  symbol = "*=*";
                  break;
               case TableAssemblyOperator.NOT_EQUAL_JOIN:
                  symbol = "<>";
                  break;
               case TableAssemblyOperator.GREATER_JOIN:
                  symbol = ">";
                  break;
               case TableAssemblyOperator.GREATER_EQUAL_JOIN:
                  symbol = ">=";
                  break;
               case TableAssemblyOperator.LESS_JOIN:
                  symbol = "<";
                  break;
               case TableAssemblyOperator.LESS_EQUAL_JOIN:
                  symbol = "<=";
                  break;
               default:
                  throw new RuntimeException(
                     "Unsupported operation found: " + operation);
               }

               XJoin join = new XJoin(lexp, rexp, symbol);
               join.setTable1(ltable);
               join.setTable2(rtable);
               ojoins.add(join);
               nsql.addJoin(join);
            }
         }
      }

      if(ojoins.size() > 0) {
         nsql.setOriginalJoins(ojoins);
      }

      return true;
   }

   /**
    * If the merged columns are in a subquery, return the name
    * of the subquery (e.g. select col1 from (select ...) sub1).
    */
   @Override
   protected String getMergedTableName(ColumnRef column) {
      return column.getEntity();
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

      ColumnSelection columns = new ColumnSelection();

      for(int i = 0; i < tables.length; i++) {
         TableAssembly table = tables[i];
         String tname = table.getName();
         queries[i].validateColumnSelection();
         ColumnSelection tcolumns = table.getColumnSelection(true);

         for(int j = 0; j < tcolumns.getAttributeCount(); j++) {
            ColumnRef tcolumn = (ColumnRef) tcolumns.getAttribute(j);
            DataRef attr = AssetUtil.getOuterAttribute(tname, tcolumn);
            String dtype = tcolumn.getDataType();
            ColumnRef column = new ColumnRef(attr);
            column.setDataType(dtype);
            columns.addAttribute(column);
         }

         AssetUtil.fixAlias(columns);
      }

      return columns;
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
      StringBuilder sb = new StringBuilder();
      String desc = catalog.getString("common.joinMerge");
      buffer.append(getVariablesString(null));
      buffer.append(desc);
      QueryNode qnode = (plan != null) ? plan : getQueryNode(buffer.toString());

      for(int i = 0; i < queries.length; i++) {
         QueryNode node = queries[i].getQueryPlan();
         qnode.addNode(node);
         String rightTable = queries[i].getTable().getName();

         if(i > 0) {
            boolean joined = false;
            sb.append(' ');
            TableAssemblyOperator crossJoinOperator = null;

            for(int j = 0; j < i; j++) {
               String leftTable = queries[j].getTable().getName();
               TableAssemblyOperator op = table.getOperator(leftTable, rightTable);

               if(op != null) {
                  // Give priority to non-cross-join operators
                  if(!op.isCrossJoin()) {
                     sb.append(op.getOperatorString()).append(' ');
                     joined = true;
                     break;
                  }
                  else {
                     crossJoinOperator = op;
                  }
               }
            }

            if(!joined) {
               TableAssemblyOperator op = crossJoinOperator != null ?
                  crossJoinOperator : table.getOperator(rightTable);

               if(op != null) {
                  sb.append(op.getOperatorString()).append(' ');
               }
            }
         }

         sb.append(rightTable);
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
      return "/inetsoft/report/gui/composition/images/join.png";
   }

   /**
    * Check if the column should be added to the merged selection.
    */
   @Override
   protected boolean isColumnExists(DataRef column) {
      String table = column.getEntity();

      for(int i = 0; i < tables.length && i < queries.length; i++) {
         if(tables[i].getName().equals(table)) {
            XSelection sel = queries[i].getUniformSQL().getSelection();
            return isColumnOnSelection(column, sel);
         }
      }

      return false;
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

   private AbstractJoinTableAssembly table; // join table assembly
   private TableAssembly[] tables; // base tables
   private AssetQuery[] queries; // base queries
   private JDBCQuery nquery; // new query
   private final Set<String> colset; // columns

   private static final Logger LOG = LoggerFactory.getLogger(JoinQuery.class);
}
