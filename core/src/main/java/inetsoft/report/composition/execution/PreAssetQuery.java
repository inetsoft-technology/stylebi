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

import inetsoft.mv.MVTool;
import inetsoft.mv.MVTransformer;
import inetsoft.report.composition.QueryTreeModel.QueryNode;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.MaxRowsTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.asset.internal.ScriptIterator.ScriptListener;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.ConditionListHandler;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.*;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptException;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Preprocess Asset query executes a table assembly.
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class PreAssetQuery implements Serializable, Cloneable {
   /**
    * Fix the sub query mode.
    */
   public static int fixSubQueryMode(int mode) {
      if(AssetQuerySandbox.isLiveMode(mode)) {
         return mode | AssetQuerySandbox.EMBEDDED_MODE;
      }

      return mode;
   }

   /**
    * Create an asset query.
    */
   public PreAssetQuery(int mode, AssetQuerySandbox box, boolean stable) {
      super();

      this.mode = mode;
      this.box = box;

      this.mergeable = false;
      this.merged = false;
      this.squeryop = -1;
      this.stable = stable;
      this.used = new ColumnSelection();
      this.sinfo = new SortInfo();
      this.ginfo = new AggregateInfo();
      this.gcolumns = new ColumnSelection();
      this.gmerged = false;
      this.preconds = null;
      this.catalog = Catalog.getCatalog(box.getUser());
      this.format = new AttributeFormat2();
   }

   /**
    * Get the table assembly to be executed.
    * @return the table assembly.
    */
   protected abstract TableAssembly getTable();

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   protected abstract boolean isSourceMergeable() throws Exception;

   /**
    * Get the default column selection internally.
    * @return the default column selection.
    */
   protected abstract ColumnSelection getDefaultColumnSelection0();

   /**
    * Get the string representation of an attribute column.
    * @param column the specified attribute column.
    * @return the string representation of the attribute column.
    */
   protected abstract String getAttributeColumn(AttributeRef column);

   /**
    * Get the alias of a column.
    * @param attr the specified column.
    * @return the alias of the column, <tt>null</tt> if not exists.
    */
   protected abstract String getAlias(ColumnRef attr);

   /**
    * Get the target jdbc query to merge into.
    * @return the target jdbc query to merge into.
    */
   public abstract JDBCQuery getQuery() throws Exception;

   /**
    * Get the target jdbc query to merge into.
    * @return the target jdbc query to merge into.
    */
   public JDBCQuery getQuery0() throws Exception {
      return null;
   }

   /**
    * Get the target sql to merge into.
    * @return the target sql to merge into.
    */
   protected abstract UniformSQL getUniformSQL();

   /**
    * Merge from clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   protected abstract boolean mergeFrom(VariableTable vars) throws Exception;

   /**
    * If the merged columns are in a subquery, return the name of the subquery
    * (e.g. select col1 from (select ...) sub1).
    */
   protected abstract String getMergedTableName(ColumnRef column);

   /**
    * Get the catalog.
    * @return the catalog part in sql statement if any.
    */
   public abstract String getCatalog();

   /**
    * Check if the asset query is composite.
    */
   public boolean isComposite() {
      return false;
   }

   /**
    * Check if treated as a sub query.
    * @return <tt>true</tt> if treated as a sub query, <tt>false</tt>
    * otherwise.
    */
   protected boolean isSubQuery() {
      return squery;
   }

   /**
    * Set the sub query flag.
    * @param sub <tt>true</tt> if treated as a sub query, <tt>false</tt>
    * otherwise.
    */
   protected void setSubQuery(boolean sub) {
      this.squery = sub;
   }


   /**
    * Check if treated as a join sub query.
    * @return <tt>true</tt> if treated as a join sub query, <tt>false</tt>
    * otherwise.
    */
   public boolean isJoinSubTable() {
      return joinSubTable;
   }

   /**
    * Set the sub join sub query flag.
    * @param joinSubTable <tt>true</tt> if treated as a join sub query, <tt>false</tt>
    * otherwise.
    */
   public void setJoinSubTable(boolean joinSubTable) {
      this.joinSubTable = joinSubTable;
   }

   /**
    * Check if treated as a sub query in where.
    * @return <tt>true</tt> if treated as a sub query in where, <tt>false</tt>
    * otherwise.
    */
   protected boolean isWhereSubQuery() {
      return wquery;
   }

   /**
    * Set the sub query in where flag.
    * @param wsub <tt>true</tt> if treated as a sub query in where,
    * <tt>false</tt> otherwise.
    */
   protected void setWhereSubQuery(boolean wsub) {
      this.wquery = wsub;
   }

   /**
    * Check if treated as a sub table.
    * @return <tt>true</tt> if treated as a sub table, <tt>false</tt>
    * otherwise.
    */
   public boolean isSubTable() {
      return stable;
   }

   /**
    * Get the query level.
    * @return the query level.
    */
   public int getLevel() {
      return level;
   }

   /**
    * Set the query level.
    * @param level the specified query level.
    */
   public void setLevel(int level) {
      this.level = level;
   }

   /**
    * Get the option of subquery.
    */
   protected int getSubQueryOption() {
      return squeryop;
   }

   /**
    * Set the option of subquery.
    */
   protected void setSubQueryOption(int op) {
      squeryop = op;
   }

   /**
    * Get the plan prefix.
    */
   protected String getPlanPrefix() {
      StringBuilder sb = new StringBuilder();

      if(level != 0) {
         sb.append("[");
         sb.append(level);
         sb.append("]");
      }

      for(int i = 0; i < level; i++) {
         sb.append("...");
      }

      return sb.toString();
   }

   /**
    * Check if requires uppercased alias.
    * @param alias the specified alias.
    * @return <tt>true</tt> if requires uppercased alias.
    */
   protected boolean requiresUpperCasedAlias(String alias) {
      // @by billh, a work around for oracle, an aliased column in a sub query
      // has to be uppercase for the select clause of the main query to locate.

      // not sub query? do not apply uppercase
      if(!isSubQuery()) {
         return false;
      }

      SQLHelper helper = getSQLHelper(getUniformSQL());
      return helper.requiresUpperCasedAlias(alias);
   }

   protected SQLHelper getSQLHelper(UniformSQL sql) {
      return SQLHelper.getSQLHelper(sql, box.getUser());
   }

   /**
    * Return the quote for quoted alias for some db.
    */
   protected String getColumnAliasQuote() {
      SQLHelper helper = getSQLHelper(getUniformSQL());
      helper = helper == null ? new SQLHelper() : helper;
      return helper.getQuote();
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   public final ColumnSelection getDefaultColumnSelection() {
      ColumnSelection columns = box.getDefaultColumnSelection(getTable().getName());

      if(columns == null) {
         columns = getDefaultColumnSelection0();
         box.setDefaultColumnSelection(getTable().getName(), columns);
      }

      return columns;
   }

   /**
    * Merge where clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   protected boolean mergeWhere(VariableTable vars) throws Exception {
      ColumnSelection columns = getTable().getColumnSelection();
      ConditionList conds = getPreConditionList();
      ConditionList oldConds = conds.clone();
      ConditionList newConds = new ConditionList();

      boolean onlyAndJunction = true;
      boolean mergeSuccess = true;

      for(int i = 1; i < conds.getSize(); i += 2) {
         if(conds.getJunction(i) == JunctionOperator.OR) {
            onlyAndJunction = false;
            break;
         }
      }

      //Only separates the condition list if it has no OR junctions
      if(onlyAndJunction) {
         for(int i = 0; i < conds.getSize(); i += 2) {
            ConditionItem item = conds.getConditionItem(i);

            if(!isConditionItemMergeable(item)) {
               conds.remove(i);

               if(i >= 0) {
                  conds.remove(i);
               }

               if(newConds.getSize() != 0) {
                  newConds.append(new JunctionOperator());
               }

               item.setLevel(0);
               newConds.append(item);
               i -= 2;
            }
         }

         conds.trim();
      }

      // make sure the levels are correct after preview remove()
      conds.validate(columns);

      if(isPreConditionListMergeable() && isConditionMergeable(conds)) {
         UniformSQL nsql = getUniformSQL();
         ConditionListHandler handler = new PreConditionListHandler();
         XUtil.convertDateCondition(conds, vars);
         conds = ConditionUtil.splitOneOfCondition(conds, getSQLHelper(nsql));
         XFilterNode fnode = createXFilterNode(handler, conds, nsql, vars);
         XFilterNode oroot = nsql.getWhere();

         if(oroot == null) {
            nsql.setWhere(fnode);
         }
         else {
            XSet nroot = new XSet(XSet.AND);
            nroot.addChild(oroot);
            nroot.addChild(fnode);
            nsql.setWhere(nroot);
         }

         if(onlyAndJunction) {
            preconds = newConds;
         }
         else {
            conds.removeAllItems();
            return true;
         }
      }
      else {
         newConds = oldConds;
         preconds = oldConds;
         mergeSuccess = false;
      }

      // add required columns for post process
      // condition column hide by vpm, do not merge it
      for(int i = 0; i < newConds.getSize(); i += 2) {
         ConditionItem item = newConds.getConditionItem(i);
         ColumnRef column = findColumn(item.getAttribute());
         addUsedColumn(column, columns);
         XCondition xcond = item.getXCondition();

         if(xcond instanceof AssetCondition) {
            AssetCondition acond = (AssetCondition) xcond;
            SubQueryValue sub = acond.getSubQueryValue();

            if(sub != null && sub.isCorrelated()) {
               DataRef mref = sub.getMainAttribute();
               column = findColumn(mref);
               addUsedColumn(column, columns);
            }
         }
      }

      return mergeSuccess;
   }

   private boolean isConditionItemMergeable(ConditionItem item) throws Exception {
      DataRef ref = item.getAttribute();
      ref = findColumn(ref);

      if(!isAttributeMergeable(ref)) {
         return false;
      }

      XCondition xcond = item.getXCondition();

      if((xcond instanceof DateCondition) && !(xcond instanceof DateRange)) {
         return true;
      }

      if(!(xcond instanceof Condition)) {
         return false;
      }

      Condition cond0 = (Condition) xcond;

      // check field value
      DataRef[] refs = cond0.getDataRefValues();

      for(int j = 0; j < refs.length; j++) {
         DataRef ref2 = refs[j];

         if(ref2 != null) {
            ref2 = findColumn(ref2);

            if(!isAttributeMergeable(ref2)) {
               return false;
            }
         }
      }

      if(!(xcond instanceof AssetCondition)) {
         return true;
      }

      AssetCondition cond = (AssetCondition) xcond;

      // check sub query value
      SubQueryValue sub = cond.getSubQueryValue();

      if(sub == null) {
         return true;
      }

      TableAssembly table = sub.getTable();
      String source1 = table.getSource();
      String source2 = getTable().getSource();

      if(source1 == null || !source1.equals(source2)) {
         return false;
      }

      table = (TableAssembly) table.clone();
      AssetQuery query = AssetQuery.createAssetQuery(
         table, fixSubQueryMode(mode), box, true, -1L, false, true);
      query.setSubQuery(true);
      query.plan = PreAssetQuery.this.plan;

      if(!query.isQueryMergeable(true)) {
         return false;
      }

      UniformSQL sql = query.getUniformSQL();
      SQLHelper helper = getSQLHelper(sql);

      if(!helper.supportsOperation(SQLHelper.CONDITION_SUBQUERY)) {
         return false;
      }

      return true;
   }

   private boolean isConditionMergeable(ConditionList conds) {
      if(conds == null || conds.isEmpty()) {
         return true;
      }

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem item = conds.getConditionItem(i);
         DataRef ref = item.getAttribute();

         if(ref instanceof AggregateRef) {
            ref = ((AggregateRef) ref).getDataRef();
         }
         else if(ref instanceof GroupRef) {
            ref = ((GroupRef) ref).getDataRef();
         }

         ColumnRef column = findColumn(ref);

         if(column != null && !column.isExpression() && !isColumnExists(column) ||
            !isMergeableDataType(column))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Merge group by clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   protected boolean mergeGroupBy() throws Exception {
      AggregateInfo info = getAggregateInfo();
      GroupRef[] groups = info.getGroups();
      AggregateRef[] aggregates = info.getAggregates();
      ColumnSelection columns = getTable().getColumnSelection();

      if(info.isEmpty()) {
         return true;
      }

      // add required columns for post process
      if(!isAggregateInfoMergeable()) {
         if(getPostAggregateInfo() != null) {
            info.clear();
            syncAggregateInfoByPostInfo(info);
         }
         else {
            for(int i = 0; i < groups.length; i++) {
               ColumnRef column = (ColumnRef) groups[i].getDataRef();
               column = findColumn(column);
               addUsedColumn(column, columns);
            }

            for(int i = 0; i < aggregates.length; i++) {
               ColumnRef column = (ColumnRef) aggregates[i].getDataRef();

               if(isAggregateExpression(column)) {
                  LOG.warn("Aggregate expression can't be merged in to SQL: " + column);
               }

               column = findColumn(column);
               addUsedColumn(column, columns);
            }
         }

         return false;
      }

      String product = getQuery() == null ? null :
         SQLHelper.getProductName(getQuery().getDataSource());
      boolean informix = "informix".equals(product);
      UniformSQL nsql = getUniformSQL();
      XSelection nselection = nsql.getSelection();

      for(int i = 0; i < columns.getAttributeCount();) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         GroupRef group = info.getGroup(column);

         // @by larryl, always add group column regardless of whether it's
         // visible since informix require group by to be on the select list.
         // VisibleTableLens will filter out the invisible column later.
         if((column.isVisible() || informix) && group != null) {
            mergeColumn(nselection, column);
            gcolumns.addAttribute(column);
         }

         AggregateRef[] aggs = info.getAggregates(column);

         if(column.isVisible() && aggs.length > 0) {
            mergeAggregates(nselection, aggs);
            gcolumns.addAttribute(column);
         }

         // if we have no aggs or one agg for a column, increase the count by 1
         // if we have multiple aggregates for a column, must hop over added
         // cols
         i += Math.max(aggs.length, 1);
      }

      List list = new ArrayList();

      for(int i = 0; i < groups.length; i++) {
         GroupRef group = groups[i];
         ColumnRef column = (ColumnRef) group.getDataRef();
         column = findColumn(column);

         // column hide by vpm?
         if(column != null && !column.isExpression() &&
            !isColumnExists(column))
         {
            continue;
         }

         String col = getColumn(column);
         list.add(col);
      }

      if(list.size() > 0) {
         nsql.setGroupBy(list.toArray(new String[list.size()]));
      }

      // clear the original order by fields for they are useless
      nsql.removeAllOrderByFields();
      gmerged = true;

      // clear aggregate info to avoid post process
      info.clear();
      syncAggregateInfoByPostInfo(info);

      return true;
   }

   private void syncAggregateInfoByPostInfo(AggregateInfo info) {
      AggregateInfo postAggregateInfo = getPostAggregateInfo();

      if(postAggregateInfo != null) {
         info.setGroups(postAggregateInfo.getGroups());
         info.setAggregates(postAggregateInfo.getAggregates());
         info.setCrosstab(postAggregateInfo.isCrosstab());
         ginfo = (AggregateInfo) Tool.clone(info);
      }
   }

   /**
    * Merge having clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   protected boolean mergeHaving(VariableTable vars) throws Exception {
      ColumnSelection columns = getTable().getColumnSelection();
      ConditionListWrapper wrapper = getPostConditionList();
      ConditionList conds = wrapper.getConditionList();
      AggregateInfo aggregateInfo = getAggregateInfo();

      // add required columns for post process
      // condition hide by vpm? do not merge it
      if(!isPostConditionListMergeable() || !isConditionMergeable(conds)) {
         // for crosstab or rotated table, we do not validate the condition list
         if(aggregateInfo.isCrosstab() || (getTable() instanceof RotatedTableAssembly))
         {
            return false;
         }

         for(int i = 0; i < conds.getSize(); i += 2) {
            ConditionItem item = conds.getConditionItem(i);
            DataRef ref = item.getAttribute();

            if(ref instanceof AggregateRef) {
               AggregateRef aggregate = (AggregateRef) item.getAttribute();
               ref = aggregate.getDataRef();
            }

            ColumnRef column = findColumn(ref);
            addUsedColumn(column, columns);
         }

         return false;
      }

      UniformSQL nsql = getUniformSQL();

      ConditionListHandler handler = new PostConditionListHandler();
      XUtil.convertDateCondition(conds, vars);
      XFilterNode fnode = createXFilterNode(handler, conds, nsql, vars);
      XFilterNode oroot = nsql.getHaving();

      if(oroot == null) {
         nsql.setHaving(fnode);
      }
      else {
         XSet nroot = new XSet(XSet.AND);
         nroot.addChild(oroot);
         nroot.addChild(fnode);
         nsql.setHaving(nroot);
      }

      conds.removeAllItems();

      return true;
   }

   /**
    * Build condition tree from condition list.
    */
   protected XFilterNode createXFilterNode(ConditionListHandler handler,
                                           ConditionList conds,
                                           UniformSQL nsql, VariableTable vars)
   {
      return handler.createXFilterNode(conds, nsql, vars);
   }

   /**
    * Merge order by clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   protected boolean mergeOrderBy() throws Exception {
      SortInfo sinfo = getTable() instanceof EmbeddedTableAssembly ?
         (SortInfo) getSortInfo().clone() : getSortInfo();;

      // group by not empty? only sort on group/aggregate columns is permitted
      if(!ginfo.isEmpty()) {
         SortRef[] sorts = sinfo.getSorts();

         for(int i = sorts.length - 1; i >= 0; i--) {
            AggregateRef aggregate = ginfo.getAggregate(sorts[i]);
            GroupRef group = ginfo.getGroup(sorts[i]);

            // normal column?
            if(aggregate == null && group == null) {
               sinfo.removeSort(i);
            }
            // aggregate column?
            else if(group == null && aggregate != null) {
               int order = sorts[i].getOrder();
               SortRef sort = new SortRef(aggregate);

               sinfo.removeSort(i);
               sort.setOrder(order);
               sinfo.addSort(i, sort);
            }
         }
      }

      ColumnSelection columns = getTable().getColumnSelection();
      boolean smergeable = true;

      // group info not mergeable? add group columns as sort columns in order
      if(!ginfo.isEmpty() && !isAggregateInfoMergeable()) {
         this.sinfo = (SortInfo) sinfo.clone();
         GroupRef[] groups = ginfo.getGroups();
         sinfo.clear();

         for(int i = 0; i < groups.length; i++) {
            ColumnRef column = (ColumnRef) groups[i].getDataRef();
            column = findColumn(column);
            SortRef sort = this.sinfo.getSort(column);

            if(sort == null) {
               sort = new SortRef(column);
               sort.setOrder(XConstants.SORT_ASC);
            }

            // group order not mergeable? post process the sort info
            if(!isGroupOrderMergeable(groups[i])) {
               smergeable = false;
            }

            if(!sinfo.containsSort(sort)) {
               sinfo.addSort(sort);
            }
         }
      }

      if(!isSourceMergeable() || !smergeable) {
         return false;
      }

      if(sinfo.isEmpty()) {
         return true;
      }

      SortRef[] sorts = sinfo.getSorts();

      if(!isSortInfoMergeable0(sinfo)) {
         for(int i = 0; i < sorts.length; i++) {
            DataRef ref = sorts[i].getDataRef();
            ColumnRef column = findColumn(ref);
            addUsedColumn(column, columns);
         }

         return false;
      }

      UniformSQL nsql = getUniformSQL();
      SQLHelper helper = getSQLHelper(nsql);
      boolean acorder = helper.supportsOperation(SQLHelper.AGGREGATE_COLUMN_ORDERBY);

      for(int i = sorts.length - 1; i >= 0; i--) {
         DataRef attr = sorts[i].getDataRef();
         String col;
         ColumnRef column = null;

         if(attr instanceof AggregateRef) {
            AggregateRef aggregate = (AggregateRef) attr;
            column = (ColumnRef) aggregate.getDataRef();
            col = !acorder ? getAlias(column) : getAggregateExpression(aggregate);
         }
         else {
            column = (ColumnRef) attr;
            column = findColumn(column);
            col = getColumn(column);
         }

         if(column != null && !column.isExpression() && !isColumnExists(column)) {
            continue;
         }

         int orderType = sorts[i].getOrder();

         if(orderType != XConstants.SORT_NONE) {
            String order = getOrder(orderType);
            nsql.insertOrderBy(0, col, order);
            nsql.clearSQLString();
         }
      }

      sinfo.clear();

      AggregateInfo postAggregateInfo = getPostAggregateInfo();

      if(postAggregateInfo != null) {
         for(GroupRef group : postAggregateInfo.getGroups()) {
            SortRef sortRef =  new SortRef(group.getDataRef());
            sortRef.setOrder(XConstants.SORT_ASC);
            sinfo.addSort(sortRef);
         }
      }

      SortInfo postSortInfo = getPostSortInfo();

      if(postSortInfo != null) {
         for(SortRef sort : postSortInfo.getSorts()) {
            sinfo.addSort(sort);
         }
      }

      return true;
   }

   /**
    * Get the sorting column of table.
    */
   protected SortInfo getSortInfo() throws Exception {
      return getTable().getSortInfo();
   }

   /**
    * Merge select clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   protected boolean mergeSelect() throws Exception {
      UniformSQL nsql = getUniformSQL();
      XSelection nselection = nsql.getSelection();
      XSelection bselection = nsql.getBackupSelection();
      ColumnSelection columns = getTable().getColumnSelection();

      // group by already merged? the selection is ok now
      if(nselection.getColumnCount() > 0) {
         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) columns.getAttribute(i);
            column.setProcessed(true);
         }

         return true;
      }

      boolean all = true;
      ColumnSelection oldUsed = used == null ? new ColumnSelection() : used.clone(true);

      if(used != null) {
         used.clear();
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(!column.isVisible() && !column.isHiddenParameter()) {
            column.setProcessed(true);
            continue;
         }

         boolean added = addUsedColumn(column, columns);

         // support script expression basing on invisible sql expression
         if(!added && column.isExpression()) {
            Enumeration<DataRef> iterator = column.getExpAttributes();

            while(iterator.hasMoreElements()) {
               DataRef ref = iterator.nextElement();
               int index = columns.indexOfAttribute(ref);

               if(index >= 0) {
                  ColumnRef column2 = (ColumnRef) columns.getAttribute(index);

                  if(column2.isExpression() && !column2.isVisible()) {
                     addUsedColumn(column2, columns);
                  }
               }
            }
         }

         all = false;
      }

      for(int i = 0; i < oldUsed.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) oldUsed.getAttribute(i);
         used.addAttribute(column);
      }

      boolean ainfoMergeable = isAggregateInfoMergeable();

      for(int i = 0; i < used.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) used.getAttribute(i);

         if(mergeColumn(nselection, column, ainfoMergeable)) {
            column.setProcessed(true);

            if(!nsql.isParseSQL() || !nsql.isLossy()) {
               // need to make sure newly added columns are in final sql string. (59902)
               nsql.clearSQLString();
            }
         }
      }

      // make sure order-by columns are on the selected if it's an expression
      Object[] orders = nsql.getOrderByFields();

      for(int i = 0; orders != null && i < orders.length; i++) {
         String fld = orders[i].toString();
         int nidx = nselection.indexOfColumn(fld);
         int bidx = bselection.indexOfColumn(fld);

         if(nidx < 0 && bidx >= 0) {
            String alias = bselection.getAlias(bidx);
            String col = bselection.getColumn(bidx);

            // @by mikec, if the orderby item is used in new columnselection
            // but only alias be changed, we should change the orderby item
            // to make it in sync.
            // and if the orderby item is used in old columnselection with alias
            // but not selected in new columnselection, we replace the
            // orderby item with the column name itself.
            int ncol = nselection.indexOfColumn(col);
            String nalias = (ncol >= 0) ? nselection.getAlias(ncol) : col;

            if(nalias != null && Tool.equals(alias, fld) && !Tool.equals(fld, nalias)) {
               String order = nsql.getOrderBy(orders[i]);
               nsql.replaceOrderBy(orders[i], order, nalias, order);
               nsql.clearSQLString();
            }
            else if(!isQualifiedName(col) && !isSubQuery()) {
               nidx = nselection.addColumn(col);
               nselection.setExpression(nidx, bselection.isExpression(bidx));
               nsql.clearSQLString();

               if(alias != null) {
                  nselection.setAlias(nidx, alias, getColumnAliasQuote());
               }
            }
         }
      }

      return all;
   }

   /**
    * Merge others.
    * @param vars the specified variable table.
    */
   protected void mergeOthers(VariableTable vars) throws Exception {
      TableAssembly table = getTable();
      UniformSQL nsql = getUniformSQL();

      // merge distinct
      boolean distinct = table.isDistinct();
      SQLHelper helper = getSQLHelper(nsql);
      boolean disorder = helper.supportsOperation(SQLHelper.DISTINCT_ORDERBY);
      SortInfo sortInfo = getTable().getSortInfo();

      if(!distinct || disorder || sortInfo.isEmpty()) {
         distinct = (nsql.isDistinct() && ginfo.isEmpty()) || distinct;
         nsql.setDistinct(distinct && getAggregateInfo().isEmpty());
         table.setDistinct(false);
      }

      // aggregate info post processed? do not apply max rows
      int max = getMaxRows(false);

      if(max > 0) {
         String wflag = wquery ? SQLHelper.WHERE_SUBQUERY : null;
         boolean correlated =
            (squeryop & XCondition.CORRELATED) == XCondition.CORRELATED;
         squeryop = squeryop & (~XCondition.CORRELATED);

         // top level query maxrows enforced by jdbc connection
         if(helper.supportsOperation(SQLHelper.MAXROWS, wflag) &&
            (isSubQuery() || helper.isApplyMaxRowsToTopLevel()) &&
            (!wquery || (squeryop == XCondition.ONE_OF && !correlated)))
         {
            nsql.setHint(UniformSQL.HINT_OUTPUT_MAXROWS, max + "");
            TableAssemblyInfo info = table.getTableInfo();

            if(info != null) {
               nsql.setHint(UniformSQL.HINT_USER_MAXROWS, info.isUserMaxRows());
            }
         }

         vars.put(XQuery.HINT_MAX_ROWS, max + "");
      }
      else {
         vars.remove(XQuery.HINT_MAX_ROWS);
      }
   }

   /**
    * Check if should ignore max rows setting.
    */
   protected boolean ignoreMaxRows(VariableTable vars) throws Exception {
      return "true".equals(vars.get(XQuery.HINT_IGNORE_MAX_ROWS));
   }

   /**
    * Get the max row count.
    * @param post <tt>true</tt> if is post process, <tt>false</tt>
    * otherwise.
    * @return the max row count.
    */
   protected int getMaxRows(boolean post) throws Exception {
      if(!post && !getAggregateInfo().isEmpty()) {
         return MaxRowsTableLens.ALL;
      }

      if(!post && !getPreConditionList().isEmpty()) {
         return MaxRowsTableLens.ALL;
      }

      if(!post && !getRankingConditionList().isEmpty()) {
         return MaxRowsTableLens.ALL;
      }

      VariableTable vars = box.getVariableTable();
      vars = vars == null ? new VariableTable() : vars;

      if(ignoreMaxRows(vars)) {
         return MaxRowsTableLens.ALL;
      }

      int dmax = 0;
      int max = getDefinedMaxRows();

      // merge max preview/runtime rows from env
      if(!isSubQuery()) {
         if((mode & AssetQuerySandbox.RUNTIME_MODE) != 0) {
            dmax = Util.getQueryRuntimeMaxrow();
         }
         else if(box.getAdditionalVariableProvider() != null) {
            dmax = box.getWorksheet().getWorksheetInfo().getDesignMaxRows();
         }
         // browse data max rows controlled in BrowseDataController
         else if((mode & AssetQuerySandbox.BROWSE_MODE) != AssetQuerySandbox.BROWSE_MODE) {
            dmax = box.getWorksheet().getWorksheetInfo().getPreviewMaxRow();
         }

         try {
            String str = (String) vars.get(XQuery.HINT_MAX_ROWS);

            if(str != null) {
               int max2 = Integer.parseInt(str);
               String defaultFlag = (String) vars.get(XQuery.HINT_DEFAULT_MAX_ROWS);

               if(defaultFlag != null && defaultFlag.equals("true")) {
                  max2 = Math.min(max2, max);
               }

               if(max2 > 0) {
                  max = max2;
               }
            }
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      if(dmax > 0) {
         max = max > 0 ? Math.min(max, dmax) : dmax;
      }

      if(post && mode == AssetQuerySandbox.LIVE_MODE) {
         int max0 = box.getWorksheet().getWorksheetInfo().getDesignMaxRows();

         if(max0 > 0) {
            max = max > 0 ? Math.min(max0, max) : max0;
         }
      }

      // Feature #39140, always respect the global row limit
      return Util.getQueryLocalRuntimeMaxrow(max);
   }

   /**
    * Get the user defined max rows.
    * @return the user defined max rows.
    */
   protected int getDefinedMaxRows() throws Exception {
      return getDefinedMaxRows(true);
   }

   /**
    * Get the user defined max rows.
    *
    * @param maxRowsSupported true if the datasource supports maxrows, false otherwise.
    *
    * @return the user defined max rows.
    */
   protected int getDefinedMaxRows(boolean maxRowsSupported) throws Exception {
      TableAssembly table = getTable();
      boolean runtime = (mode & AssetQuerySandbox.RUNTIME_MODE) != 0;
      boolean livetime = (mode & AssetQuerySandbox.LIVE_MODE) != 0;
      int max = runtime ? table.getMaxRows() : table.getMaxDisplayRows();

      // @by larryl, if runtime limit is set, showing more rows in live view is
      // counter-intuitive and give people the impression it doesn't work.
      if(!runtime) {
         if(max <= 0) {
            max = table.getMaxRows();
         }
         else {
            int runtimeMax = table.getMaxRows();

            if(runtimeMax > 0) {
               max = Math.min(max, runtimeMax);
            }
         }
      }

      // for live time, do not return too many rows to display, for this table
      // is most likely to be a sub table, but end user wants to verify whether
      // data is correct. If too many rows returned, user experience is bad
      // @by larryl, don't force a max row in design mode of vs, otherwise the
      // design mode could be useless for analyzing data. Also, since we
      // already have a setting for max rows in ws, this really shouldn't
      // be necessary.
      if(!runtime && max <= 0 && (!isSubQuery() || !(table instanceof ComposedTableAssembly))) {
         XQuery query = getQuery();
         boolean unsupported = false;

         if(query != null) {
            SQLHelper helper = SQLHelper.getSQLHelper(query.getDataSource(), box.getUser());
            String product = SQLHelper.getProductName(query.getDataSource());
            unsupported = "mysql".equalsIgnoreCase(product);

            if(!unsupported && helper != null) {
               unsupported = !helper.supportsOperation(SQLHelper.MAXROWS);
            }
         }

         // fix bug1323451696384, for live time, we will always use the default maxrows.
         // if source doesn't support maxRows, don't use display max rows in live mode.
         // otherwise the (e.g. union) query will not be mergeable and sql expressions
         // will fail. (50847)
         if(!unsupported || livetime && maxRowsSupported) {
            boolean browse = (mode & AssetQuerySandbox.BROWSE_MODE) == AssetQuerySandbox.BROWSE_MODE;
            boolean vs = box.getAdditionalVariableProvider() != null;

            // in live mode of vs queries, we should use the max rows
            // explicitly set by the user (e.g. 0)
            if(vs && livetime) {
               if(!isDriversDataCached() && maxRowsSupported) {
                  max = box.getWorksheet().getWorksheetInfo().getDesignMaxRows();
               }
            }
            else {
               max = box.getMaxRows();
            }

            // increase max rows in browse mode so values won't be lost in dropdown. (54103)
            if(max > 0 && browse) {
               max *= 100;
            }
         }
      }

      // Feature #39140, always respect the global row limit
      return Util.getQueryLocalRuntimeMaxrow(Math.max(0, max));
   }

   /**
    * Check if the target column is an aggregate expression column.
    */
   protected boolean isAggregateExpression(ColumnRef column) {
      if(!column.isExpression()) {
         return false;
      }

      AggregateInfo ainfo = getAggregateInfo();
      ColumnSelection columns = getTable().getColumnSelection();

      for(AttributeRef attr : getContainedAttributes(column, false)) {
         int idx = columns.indexOfAttribute(attr);

         if(idx < 0) {
            return false;
         }

         ColumnRef tcol = (ColumnRef) columns.getAttribute(idx);

         if(ainfo.getAggregate(tcol) != null) {
            return true;
         }
      }

      return false;
   }

   /**
    * Merge a column to the new selection.
    * @param nselection the specified new selection.
    * @param column the specified attribute.
    */
   protected final void mergeColumn(XSelection nselection, ColumnRef column) {
      mergeColumn(nselection, column, true);
   }

   /**
    * Merge a column to the new selection.
    * @param nselection the specified new selection.
    * @param column the specified attribute.
    */
   protected boolean mergeColumn(XSelection nselection, ColumnRef column,
      boolean isAggregateInfoMergeable)
   {
      if(!isAggregateInfoMergeable && isAggregateExpression(column)) {
         return false;
      }

      // check if the column is removed by vpm
      if(!column.isExpression() && !isColumnExists(column)) {
         return false;
      }

      // check if the contained column is removed by vpm
      if(column.isExpression()) {
         try {
            if(!isSourceMergeable()) {
               return false;
            }
         }
         catch(Exception ignore) {
         }

         ColumnSelection columns = getTable().getColumnSelection();

         for(AttributeRef attr : getContainedAttributes(column, false)) {
            int idx = columns.indexOfAttribute(attr);

            if(idx < 0) {
               return false;
            }

            ColumnRef tcol = (ColumnRef) columns.getAttribute(idx);

            if(!tcol.isExpression() && !isColumnExists(tcol)) {
               return false;
            }
         }
      }

      String col = getColumn(column);

      if(col == null) {
         return false;
      }

      String alias = getAlias(column);
      alias = Tool.equals(col, alias) ? null : alias;
      int index = nselection.addColumn(col);
      nselection.setExpression(index, column.isExpression());

      if(alias != null) {
         nselection.setAlias(index, alias, getColumnAliasQuote());
      }

      if(!column.isExpression() && isQualifiedName(col)) {
         String tname = getMergedTableName(column);

         if(tname != null) {
            String colname = (alias != null) ? alias : col;
            ((JDBCSelection) nselection).setTable(colname, tname);
         }
      }

      XMetaInfo minfo = getXMetaInfo(column, column);
      minfo = fixMetaInfo(minfo, column, column);

      if(minfo != null) {
         nselection.setXMetaInfo(index, minfo);
      }

      DataRef ref = column.getDataRef();

      // must be expression
      if(ref instanceof DataRefWrapper) {
         ref = ((DataRefWrapper) ref).getDataRef();

         if(ref instanceof AttributeRef) {
            AttributeRef bref = (AttributeRef) ref;
            String bcol = getColumn(bref);
            nselection.setBaseColumn(col, bcol);
         }
      }

      return true;
   }

   /**
    * Get the meta info of one column.
    * @param column the specified column ref.
    * @param original the top-level original column ref.
    * @return the meta info of the column ref, <ttt>null</tt> otherwise.
    */
   public XMetaInfo getXMetaInfo(ColumnRef column, ColumnRef original) {
      return null;
   }

   /**
    * Check if the column should be added to the merged selection. This method
    * should return false if the column doesn't exist in the query at all. For
    * example, the column may be removed by vpm and all reference to it should
    * be eliminated in the query.
    */
   protected boolean isColumnExists(DataRef column) {
      return true;
   }

   /**
    * Check if a column is part of a selection.
    */
   protected boolean isColumnOnSelection(DataRef column, XSelection sel) {
      String attr = column.getAttribute();

      if(attr == null) {
         return true;
      }

      attr = attr.toLowerCase();

      for(int j = 0; j < sel.getColumnCount(); j++) {
         String col = sel.getLowerCaseColumn(j);
         String alias = sel.getAlias(j);

         if(col.equals(attr) || col.endsWith("." + attr) || alias != null && alias.equalsIgnoreCase(attr)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Merge an aggregate to the new selection.
    * @param nselection the specified new selection.
    * @param aggregates the specified aggregates.
    */
   protected void mergeAggregates(XSelection nselection, AggregateRef[] aggregates) {
      if(aggregates.length == 0) {
         return;
      }

      ColumnSelection columns = getTable().getColumnSelection();
      int idx = columns.indexOfAttribute(aggregates[0].getDataRef());

      for(int i = 0; i < aggregates.length; i++) {
         AggregateRef aggregate = aggregates[i];
         ColumnRef column = (ColumnRef) aggregate.getDataRef();
         column = findColumn(column);

         // column hide by vpm?
         if(column != null && !column.isExpression() && !isColumnExists(column)) {
            continue;
         }

         String col = getAggregateExpression(aggregate);
         String alias = getAlias(column);
         alias = Tool.equals(col, alias) ? null : alias;

         if(alias == null) {
            alias = column.getAttribute();

            if(requiresUpperCasedAlias(alias)) {
               alias = alias.toUpperCase();
            }
         }

         int index = nselection.addColumn(col);
         nselection.setExpression(index, column.isExpression());
         nselection.setAlias(index, alias);
         ((JDBCSelection) nselection).setAggregate(col, true);
         String ocol = getColumn(column);
         nselection.setOriginalColumn(index, ocol);

         // if there is more than one aggregate for any data ref column,
         // we need to replace the column ref with two alias column refs
         // in the column selection
         if(aggregates.length > 1) {
            if(i == 0) {
               columns.removeAttribute(idx);
            }

            columns.addAttribute(idx + i, column);
         }

         XMetaInfo minfo = getXMetaInfo(column, column);

         // do not apply auto drill on aggregated column
         if(minfo != null) {
            minfo.setXDrillInfo(null);
            nselection.setXMetaInfo(index, minfo);
         }
      }
   }

   /**
    * Add one used column.
    * @param column the specified column.
    * @param columns the specified column selection.
    */
   private boolean addUsedColumn(ColumnRef column, ColumnSelection columns) {
      if(isColumnMergeable(column)) {
         if(!used.containsAttribute(column)) {
            used.addAttribute(column);
         }

         return true;
      }
      else {
         for(AttributeRef attr : getContainedAttributes(column)) {
            int index = columns.indexOfAttribute(attr);

            if(index < 0) {
               continue;
            }

            ColumnRef added = (ColumnRef) columns.getAttribute(index);

            if(!used.containsAttribute(added)) {
               used.addAttribute(added);
            }
         }

         return false;
      }
   }

   /**
    * Check if there is any SQL expression.
    */
   protected List<String> getSQLExpressions() {
      return getTable().getColumnSelection().stream()
         .filter(column -> {
            ColumnRef colRef = (ColumnRef) column;
            return colRef.isSQL() && colRef.isExpression() &&
               colRef.getDataRef() instanceof ExpressionRef &&
               // ignore AliasDataRef
               ((ExpressionRef) colRef.getDataRef()).isExpressionEditable() &&
               ((ExpressionRef) colRef.getDataRef()).getExpression() != null;
         })
         .map(column -> getTable().getName() + "::" + column.getName())
         .collect(Collectors.toList());
   }

   /**
    * Check if the column selection is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   protected boolean isColumnSelectionMergeable() throws Exception {
      if(!isSourceMergeable()) {
         return false;
      }

      ColumnSelection columns = getTable().getColumnSelection();
      boolean existed = false;

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(!column.isVisible()) {
            continue;
         }

         if(!isColumnMergeable(column)) {
            return false;
         }

         existed = true;
      }

      return existed;
   }

   /**
    * Check if a column is mergeable.
    * @param column the specified column.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   protected boolean isColumnMergeable(ColumnRef column) {
      return isAttributeMergeable(column);
   }

   /**
    * Check if an attribute is mergeable.
    * @param attr the specified attribute.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   protected boolean isAttributeMergeable(DataRef attr) {
      ColumnRef column = findColumn(attr);

      if(!isMergeableDataType(column)) {
         return false;
      }

      if(!column.isExpression()) {
         return true;
      }

      if(!column.isSQL()) {
         return false;
      }

      DataRef ref = getBaseAttribute(column);

      if(!(ref instanceof ExpressionRef)) {
         return true;
      }

      ExpressionRef exp = (ExpressionRef) ref;

      if(exp instanceof SQLExpressionRef) {
         SQLExpressionRef sexp = (SQLExpressionRef) exp;
         UniformSQL sql = getUniformSQL();

         if(sql == null) {
            return false;
         }

         SQLHelper helper = getSQLHelper(sql);
         sexp.setDBType(helper.getSQLHelperType());
         sexp.setDBVersion(helper.getProductVersion());
         return sexp.isMergeable();
      }

      if(!isExpressionMergeable(exp)) {
         return false;
      }

      return getExpressionColumn(exp, format, false) != null;
   }

   /**
    * Check if the expression is mergeable.
    * @param expRef the specified expression.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   protected boolean isExpressionMergeable(ExpressionRef expRef) {
      String exp = expRef == null ? "" :
         ExpressionRef.getSQLExpression(true, expRef.getExpression());

      if(exp.length() == 0) {
         return true;
      }

      // do not check too carefully
      /*
      for(int i = 0; i < exp.length(); i++) {
         char c = exp.charAt(i);

         if(c == '[') {
            for(int j = i - FIELD.length; j < i; j++) {
               // not field['xxx'] pattern?
               if(j < 0 || exp.charAt(j) != FIELD[j - i + FIELD.length]) {
                  return false;
               }
            }
         }
      }
      */

      // parameters should run in script not sql
      if(exp.contains("parameter.") && PARAMTER_PATTERN.matcher(exp).find()) {
         return false;
      }

      // check if the contained columns are mergeable
      int start;
      int end;

      while((start = exp.indexOf("field['")) != -1) {
         end = exp.length() <= start + 7 ? -1 : exp.indexOf("']", start + 7);

         if(end == -1) {
            break;
         }

         if(start + 7 > end) {
            break;
         }

         String name = exp.substring(start + 7, end);
         int index = name.lastIndexOf('.');

         String entity = index == -1 ? null : name.substring(0, index);
         String attr = index == -1 ? name : name.substring(index + 1);

         AttributeRef attribute = new AttributeRef(entity, attr);
         DataRef ref = convertAttributeInExpression(attribute, expRef);

         if(ref == null) {
            attribute = new AttributeRef(null, entity + "." + attr);
            ref = convertAttributeInExpression(attribute, expRef);
         }

         if(ref != null && !isAttributeMergeable(ref)) {
            return false;
         }

         exp = exp.substring(end + 2);
      }

      return true;
   }

   /**
    * Get the contained attributes from an attribute.
    * @param attr the specified attribute.
    * @return the contained attributes.
    */
   protected List<AttributeRef> getContainedAttributes(DataRef attr) {
      return getContainedAttributes(attr, true);
   }

   /**
    * Get the contained attributes from an attribute.
    * @param attr the specified attribute.
    * @param vonly <tt>true</tt> if only valid attributes should be returned,
    * <tt>false</tt> otherwise.
    * @return the contained attributes.
    */
   protected List<AttributeRef> getContainedAttributes(DataRef attr, boolean vonly) {
      ColumnRef column = attr instanceof ColumnRef ? (ColumnRef) attr : null;
      attr = getBaseAttribute(attr);
      List<AttributeRef> list = null;

      if(attr.isExpression()) {
         String expr = attr instanceof ExpressionRef ? ((ExpressionRef) attr).getExpression() : null;

         if(expr != null) {
            list = expr2Attrs.get(expr);
         }

         if(list == null) {
            list = getExprAttributes(attr, vonly, column);
         }

         if(expr != null) {
            expr2Attrs.put(expr, list);
         }
      }
      else {
         list = new ArrayList<>();
         list.add((AttributeRef) attr);
      }

      return list;
   }

   private List<AttributeRef> getExprAttributes(DataRef attr, boolean vonly, ColumnRef column) {
      List<AttributeRef> list = new ArrayList<>();
      Enumeration iter = column != null ? column.getExpAttributes() : attr.getAttributes();

      while(iter.hasMoreElements()) {
         DataRef ref = (DataRef) iter.nextElement();

         if(ref instanceof ExpressionRef) {
            list.addAll(getContainedAttributes(ref, vonly));
            continue;
         }

         AttributeRef attr2 = (AttributeRef) ref;

         if(attr2 == null) {
            continue;
         }

         ref = convertAttributeInExpression(attr2, attr);

         if(ref instanceof ExpressionRef && ref != attr) {
            list.addAll(getContainedAttributes(ref, vonly));
            continue;
         }

         if(!(ref instanceof AttributeRef) && vonly) {
            continue;
         }

         attr2 = ref instanceof AttributeRef ? (AttributeRef) ref : null;

         if(attr2 != null) {
            list.add(attr2);
         }
      }

      return list;
   }

   /**
    * Get the base attibute of an attribute.
    * @param attr the specified attribute.
    * @return the base attibute of the attibute.
    */
   protected DataRef getBaseAttribute(DataRef attr) {
      return AssetUtil.getBaseAttribute(attr);
   }

   /**
    * Get attribute from data ref for getting XMetaInfo.
    */
   protected DataRef getRefForXMetaInfo(DataRef column) {
      DataRef ref = getBaseAttribute(column);

      if(ref instanceof AliasDataRef) {
         ref = ((AliasDataRef) ref).getDataRef();
      }

      if(ref instanceof DateRangeRef) {
         ref = ((DateRangeRef) ref).getDataRef();
      }

      if(ref instanceof ExpressionRef) {
         ExpressionRef eref = (ExpressionRef) ref;
         String exp = eref.getExpression();

         if(exp == null) {
            return null;
         }

         // for expression ref, only parse expression matchs "field['%']"
         // fix bug1255160701725
         int start = exp.startsWith("field['") ? 7 : -1;
         int end = exp.endsWith("']") ? exp.length() - 2 : -1;
         String name = start >= 0 && end >= 0 ? exp.substring(start, end) : null;
         if(name == null) {
            return null;
         }

         int dot = name == null ? -1 : name.lastIndexOf('.');
         String entity = dot >= 0 ? name.substring(0, dot) : null;
         String attribute = dot >= 0 ? name.substring(dot + 1) : name;
         ref = new AttributeRef(entity, attribute);
      }

      return ref;
   }

   /**
    * Get the string representation of an attribute.
    * @param attr the specified attribute.
    * @return the string representation of the attribute.
    */
   protected static String getAttributeString(DataRef attr) {
      return AssetUtil.getAttributeString(attr);
   }

   /**
    * Get the string representation of an aggregate.
    * @param aggregate the specified aggregate.
    * @return the string representation of the aggregate.
    */
   private String getAggregateExpression(AggregateRef aggregate) {
      UniformSQL sql = getUniformSQL();
      final SQLHelper helper = (sql == null) ? null : getSQLHelper(sql);

      AggregateHelper ahelper = new AggregateHelper() {
         @Override
         public String getColumnString(DataRef column) {
            return PreAssetQuery.this.getColumnString((ColumnRef) column);
         }

         @Override
         public String getDBType() {
            return helper == null ? null : helper.getSQLHelperType();
         }

         @Override
         public String getAggregateFunction(String func) {
            return helper.getAggregateFunction(func);
         }
      };

      return aggregate.getExpression(ahelper);
   }

   /**
    * Get a property formed (quoted if necessary) string from a column
    * reference.
    */
   private String getColumnString(ColumnRef column) {
      if(column == null) {
         return null;
      }

      column = findColumn(column);
      String col = getColumn(column);

      if(!column.isExpression() && isQualifiedName(col)) {
         col = quoteColumn(col);
      }

      return col;
   }

   /**
    * Get the string representation of an order type.
    * @param order the specified order type.
    * @return the string representation of the order type.
    */
   protected String getOrder(int order) {
      return order == XConstants.SORT_ASC ? "asc" : "desc";
   }

   /**
    * Find a column.
    * @param column the specified column.
    * @return the refreshed column.
    */
   protected final ColumnRef findColumn(DataRef column) {
      ColumnRef ref = colmap.get(column);

      if(ref == null) {
         ColumnSelection columns = getTable().getColumnSelection();
         int index = columns.indexOfAttribute(column);

         if(index < 0) {
            ref = AssetUtil.getColumn(column);

            if(ref == null) {
               ref = new ColumnRef(column);
            }
         }
         else {
            ref = (ColumnRef) columns.getAttribute(index);
         }

         colmap.put(column, ref);
      }

      return ref;
   }

   /**
    * Merge the table assembly definition into the underneath query.
    */
   public void merge(VariableTable vars) throws Exception {
      this.ginfo = (AggregateInfo) getAggregateInfo().clone();
      ColumnSelection columns = getTable().getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         column.setProcessed(false);
      }

      if(!isSourceMergeable() || !isMergePreferred()) {
         mergeOrderBy();
         return;
      }

      JDBCQuery nquery = getQuery();
      UniformSQL nsql = getUniformSQL();

      // vpm condition will be applied in the query generation
      // process, we will not postpone the process for the query
      // might be a composite query contains sub queries, but we
      // should apply vpm condition to the sub queries instead
      nquery.setVPMEnabled(false);

      // clear select clause
      XSelection nselection = nsql.getSelection();
      XSelection bselection = (XSelection) nselection.clone();
      nsql.setBackupSelection(bselection);
      ((JDBCSelection) nselection).clear(false);

      // merge where clause
      mergeWhere(vars);

      // merge group by clause
      mergeGroupBy();

      // merge having clause
      mergeHaving(vars);

      // merge order by clause
      mergeOrderBy();

      // merge select clause
      mergeSelect();

      // merge from clause
      mergeFrom(vars);

      // merge properties
      mergeOthers(vars);
   }

   /**
    * Get pre condition list.
    */
   public ConditionList getPreConditionList() {
      if(preconds == null) {
         ConditionListWrapper pwrapper = getTable().getPreConditionList();
         ConditionList preconds0 = pwrapper.getConditionList();

         // ignore filtering? just remove all the conditions
         if(preconds0 != null && box.isIgnoreFiltering()) {
            preconds0.removeAllItems();
         }

         List list = new ArrayList();
         list.add(preconds0);
         list.add(getPreRuntimeConditionList());
         preconds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
         preconds = preconds == null ? new ConditionList() : preconds;

         validateConditionList(preconds, false);
      }

      return preconds;
   }

   /**
    * Get ranking condition list.
    */
   protected ConditionList getRankingConditionList() {
      if(rankingconds == null) {
         ConditionListWrapper wrapper = getTable().getRankingConditionList();
         ConditionList conds = wrapper == null ? null : wrapper.getConditionList();
         ConditionListWrapper rwrapper = getTable().getRankingRuntimeConditionList();
         ConditionList rconds = rwrapper == null ? null : rwrapper.getConditionList();
         List<ConditionList> list = new ArrayList<>();
         list.add(conds);
         list.add(rconds);
         rankingconds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
         rankingconds = rankingconds == null ? new ConditionList() : rankingconds;
      }

      return rankingconds;
   }

   /**
    * Get pre runtime condition list.
    * @return the pre runtime condition list.
    */
   protected ConditionList getPreRuntimeConditionList() {
      ConditionListWrapper rwrapper = getTable().getPreRuntimeConditionList();

      return rwrapper == null ? null : rwrapper.getConditionList();
   }

   /**
    * Validate the column selection.
    */
   public void validateColumnSelection() {
      boolean novalid = "true".equals(getTable().getProperty("no.validate"));

      if(novalid) {
         return;
      }

      ColumnSelection ncolumns = getDefaultColumnSelection();
      ColumnSelection columns = getTable().getColumnSelection();
      validateColumnSelection(ncolumns, columns, true, true, true, true);
      getTable().setColumnSelection(columns);
   }

   /**
    * Validate the column selection. Remove nonexistent columns and add
    * new columns.
    * @param defcols the new column selection of the table.
    * @param basecols the existing column selection of the table.
    * @param fkept <tt>true</tt> to keep formula, <tt>false</tt> otherwise.
    * @param check <tt>true</tt> to check if valid, <tt>false</tt> otherwise.
    * @param def <tt>true</tt> is the default column selection.
    * @param fixingAlias <tt>true</tt> to fix alias, <tt>false</tt> otherwise.
    */
   protected void validateColumnSelection(final ColumnSelection defcols,
                                          final ColumnSelection basecols,
                                          boolean fkept, final boolean check,
                                          boolean def, boolean fixingAlias)
   {
      if(!validColSel(defcols)) {
         return;
      }

      boolean dvisible = basecols.getAttributeCount() == 0 ||
         "true".equals(defcols.getProperty("public")) || box.isActive();
      AggregateInfo aggregateInfo = getTable().getAggregateInfo();
      boolean mv = MVTransformer.findMVTable(getTable()) != null;

      // remove not existent columns
      for(int i = basecols.getAttributeCount() - 1; i >= 0; i--) {
         ColumnRef column = (ColumnRef) basecols.getAttribute(i);

         if(check) {
            column.setValid(true);
         }

         int nidx = defcols.indexOfAttribute(column);

         if(nidx < 0 && (!column.isExpression() || !fkept)) {
            ColumnRef ncolumn = (ColumnRef)
               defcols.getAttribute(basecols.getAttribute(i).getName(), true);

            // if we are replacing data ref with the default, we try to maintain
            // the column order here, since the order may be very important.
            // such as the case where a table is unioned with another table, and
            // the columns must line up. (51940)
            if(ncolumn != null && !basecols.containsAttribute(ncolumn)) {
               ncolumn = (ColumnRef) ncolumn.clone();
               ncolumn.setVisible(dvisible);

               if(def) {
                  ncolumn.setAlias(null);
               }

               basecols.setAttribute(i, ncolumn);
            }
            else {
               basecols.removeAttribute(i);
            }
         }
         else if(!column.isExpression()) {
            ColumnRef ncolumn = (ColumnRef) defcols.getAttribute(nidx);

            // should use the base column type if this column is not expression,
            // otherwise a string column may be mistakenly changed to double. (56149)
            //if(!def || !XSchema.STRING.equals(ncolumn.getDataType())) {
            column.setDataType(ncolumn.getDataType());
            //}
         }
         else {
            if(check) {
               List<AttributeRef> containedAttributes = getContainedAttributes(column, false);

               if(containedAttributes.size() > 0) {
                  for(AttributeRef attr : containedAttributes) {
                     if(!defcols.containsAttribute(attr)) {
                        column.setValid(false);
                        break;
                     }
                  }
               }

               if(!isValidDateRange(column, defcols, aggregateInfo, mv)) {
                  basecols.removeAttribute(i);
               }
            }

            final ColumnRef ecolumn = column;
            ExpressionRef attr = (ExpressionRef) column.getDataRef();

            if(attr.isExpressionEditable() && !column.isSQL()) {
               String exp = attr.getExpression();

               ScriptIterator iterator = new ScriptIterator(exp);
               ScriptListener listener = (token, pref, cref) -> {
                  if(token.isRef() && !PARAMETER.equals(token.val)
                     && !FIELD2.equals(token.val) &&
                     (pref == null || !FIELD2.equals(pref.val)))
                  {
                     if(!token.val.matches("[0-9]+$")) {
                        Assembly assembly = box.getWorksheet().getAssembly(token.val);

                        if(check && assembly == null) {
                           ecolumn.setValid(false);
                        }
                     }
                  }
               };

               iterator.addScriptListener(listener);
               iterator.iterate();
            }
         }
      }

      // add new columns
      for(int i = 0; i < defcols.getAttributeCount(); i++) {
         ColumnRef ncolumn = (ColumnRef) defcols.getAttribute(i);

         if(!basecols.containsAttribute(ncolumn)) {
            ncolumn = (ColumnRef) ncolumn.clone();
            ncolumn.setVisible(dvisible);

            if(def) {
               ncolumn.setAlias(null);
            }

            // Bug #61431, maintain the index of the columns for a concatenated table
            if(getTable() instanceof ConcatenatedTableAssembly) {
               basecols.addAttribute(i, ncolumn, false);
            }
            else {
               basecols.addAttribute(ncolumn, false);
            }
         }
      }

      // for 10.1 bc
      // @see WS10_1Transformer.processGroupRef
      // don't fix embedded table header, import data should be as is
      boolean bc = "true".equalsIgnoreCase(getTable().getProperty("BC_VALIDATE"));

      if(fixingAlias && !(getTable() instanceof EmbeddedTableAssembly && !bc) &&
         (box.isFixingAlias() || bc) && MVTransformer.findMVTable(getTable()) == null)
      {
         AssetUtil.fixAlias(basecols);
      }
   }

   protected boolean isValidDateRange(ColumnRef column, ColumnSelection ncolumns,
                                      AggregateInfo aggregateInfo, boolean mv)
   {
      return true;
   }

   protected boolean columnSelectionContainsRef(DataRef ref, ColumnSelection defcols) {
      if(defcols.containsAttribute(ref)) {
         return true;
      }

      if(ref instanceof ExpressionRef) {
         Enumeration attributes = ref.getAttributes();

         while(attributes.hasMoreElements()) {
            Object attr = attributes.nextElement();

            if(!(attr instanceof DataRef)) {
               continue;
            }

            if(defcols.containsAttribute((DataRef) attr)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Verify that the column selection is valid.
    */
   protected boolean validColSel(ColumnSelection cols) {
      return cols != null && !(cols instanceof NullColumnSelection) && cols.getAttributeCount() > 0;
   }

   /**
    * Validate the ranking.
    * @param conds the specified ranking.
    */
   protected void validateRanking(ConditionList conds) {
      HierarchyListModel model = new HierarchyListModel(conds);
      ColumnSelection columns = getTable().getColumnSelection();

      for(int i = model.getSize() - 1; i >= 0; i--) {
         if(i >= model.getSize()) {
            continue;
         }

         if(model.isConditionItem(i)) {
            ConditionItem item = (ConditionItem) model.getElementAt(i);
            DataRef attr = item.getAttribute();

            if(!columns.containsAttribute(attr)) {
               model.removeConditionItem(i);
               LOG.warn("Column not found, removing ranking condition: {}", attr);
               continue;
            }

            RankingCondition rcond = (RankingCondition) item.getXCondition();

            if(!rcond.isValid(columns)) {
               model.removeConditionItem(i);
               LOG.warn("Aggregate not found, removing ranking condition: {}",
                        rcond.getDataRef());
            }
         }
      }
   }

   /**
    * Validate the condition list.
    * @param conds the specified condition list.
    * @param post <tt>true</tt> to treat as a post condition list,
    */
   protected final void validateConditionList(ConditionList conds, boolean post) {
      if(conds == null) {
         return;
      }

      // condition pushed down in ws selection down transformer, don't modify it
      // otherwise the condition may be lost or columns won't match
      if("true".equals(getTable().getProperty("selection.down"))) {
         return;
      }

      // condition will apply to the child, some column in child (e.g. calc field) may
      // not be in the parent. those columns will be removed in the validation.
      if(MVTool.isMVConditionParent(getTable())) {
         return;
      }

      HierarchyListModel model = new HierarchyListModel(conds);
      AggregateInfo aggregateInfo = getAggregateInfo();

      if(getPostAggregateInfo() != null) {
         aggregateInfo = getPostAggregateInfo();
      }

      ColumnSelection columns = getTable().getColumnSelection();
      int min = Integer.MAX_VALUE;

      for(int i = 0; i < conds.getSize(); i++) {
         min = Math.min(min, conds.getItem(i).getLevel());
      }

      for(int i = 0; min > 0 && i < conds.getSize(); i++) {
         HierarchyItem item = conds.getItem(i);
         item.setLevel(item.getLevel() - min);
      }

      // for crosstab or rotated table, we do not validate the condition list
      if(aggregateInfo.isCrosstab() || (getTable() instanceof RotatedTableAssembly)) {
         return;
      }

      for(int i = model.getSize() - 1; i >= 0; i--) {
         if(i >= model.getSize()) {
            continue;
         }

         if(!model.isConditionItem(i)) {
            continue;
         }

         ConditionItem item = (ConditionItem) model.getElementAt(i);
         DataRef attr = item.getAttribute();

         // @by larryl, the runtime condition field and the column list may
         // or may not contain the table name as the entity. If we are to
         // remove this logic, we need to check all places and different
         // data sources to make sure the field and the column list are
         // consistent
         if(!columns.containsAttribute(attr)) {
            DataRef attr2 = columns.getAttribute(attr.getAttribute(), true);

            if(attr2 != null) {
               item.setAttribute(attr = attr2);
            }
         }

         if(attr == null || columns.findAttribute(attr) == null &&
            !(attr instanceof CompositeAggregateRef) && !(attr instanceof CalculateRef))
         {
            model.removeConditionItem(i);
            continue;
         }

         if(attr instanceof AggregateRef) {
            AggregateRef[] aggregates = aggregateInfo.getAggregates();
            boolean contained = false;

            for(int j = 0; j < aggregates.length; j++) {
               if(aggregates[j].equalsAggregate(attr)) {
                  contained = true;
                  break;
               }
            }

            if(!contained) {
               model.removeConditionItem(i);
               continue;
            }
         }
         // post condition list? check whether we should replace
         // the original data ref with its corrresponding aggregate
         else if(post) {
            AggregateRef aggregate = aggregateInfo.getAggregate(attr);

            if(aggregate != null) {
               item.setAttribute(aggregate);
            }
         }

         XCondition xcond = item.getXCondition();

         if(xcond instanceof AssetCondition) {
            AssetCondition acond = (AssetCondition) xcond;
            SubQueryValue sub = acond.getSubQueryValue();

            if(sub != null) {
               TableAssembly subTable = sub.getTable();

               if(subTable == null) {
                  model.removeConditionItem(i);
                  continue;
               }

               while(subTable instanceof MirrorTableAssembly) {
                  Assembly baseAssembly =
                     ((MirrorTableAssemblyInfo) subTable.getInfo()).getImpl().getAssembly();

                  if(baseAssembly instanceof TableAssembly) {
                     subTable = (TableAssembly) baseAssembly;
                  }
                  else {
                     break;
                  }
               }

               ColumnSelection scolumns = subTable.getColumnSelection(true);

               try {
                  if(!sub.checkValidity(scolumns, columns)) {
                     model.removeConditionItem(i);
                  }
               }
               catch(Exception ex) {
                  LOG.warn("Failed to check validity of column " +
                     "selections, removing sub-query condition", ex);
                  model.removeConditionItem(i);
                  continue;
               }
            }
         }
      }
   }

   /**
    * Get the string representation of a column.
    * @param column the specified column.
    * @return the string representation of the column.
    */
   protected final String getColumn(DataRef column) {
      DataRef base = getBaseAttribute(column);

      if(base.isExpression()) {
         return getExpressionColumn((ExpressionRef) base, format, true);
      }

      String col = getAttributeColumn((AttributeRef) base);
      UniformSQL nsql = getUniformSQL();

      if(nsql != null && col != null) {
         JDBCSelection selection2 = (JDBCSelection) nsql.getBackupSelection();
         ColumnRef cref = findColumn(column);
         String alias = getAlias(cref);
         alias = Tool.equals(col, alias) ? null : alias;
         boolean found = false;

         for(int i = 0; i < selection2.getColumnCount(); i++) {
            String col2 = selection2.getColumn(i);
            String alias2 = selection2.getAlias(i);

            if(col2.equals(col) && Tool.equals(alias, alias2)) {
               found = true;
               break;
            }
         }

         if(!found) {
            int index = selection2.addColumn(col);
            selection2.setExpression(index, column.isExpression());

            if(alias != null) {
               selection2.setAlias(index, alias);
            }

            if(!column.isExpression() && isQualifiedName(col)) {
               String tname = getMergedTableName(cref);

               if(tname != null) {
                  selection2.setTable(col, tname);
               }
            }
         }
      }

      return col;
   }

   /**
    * Get the string representation of an expression column.
    * @param column the specified expression column.
    * @param format the specified atttribute format.
    * @return the string representation of the expression column.
    */
   protected final String getExpressionColumn(ExpressionRef column,
                                              AttributeFormat format,
                                              boolean quote)
   {
      return getExpressionColumn(column, format, quote, true);
   }

   private String getExpressionColumn(ExpressionRef column, AttributeFormat format, boolean quote,
                                      boolean warnOnNull)
   {
      boolean aliased = column instanceof AliasDataRef;
      ColumnSelection cols = getTable().getColumnSelection();
      UniformSQL sql = getUniformSQL();
      SQLHelper helper = getSQLHelper(sql);

      if(column instanceof SQLExpressionRef) {
         SQLExpressionRef sexp = (SQLExpressionRef) column;

         if(sql != null) {
            sexp.setDBType(helper.getSQLHelperType());
            sexp.setDBVersion(helper.getProductVersion());
         }
      }

      String exp = ExpressionRef.getSQLExpression(true, column.getExpression());

      if(helper != null) {
         exp = helper.fixSQLExpression(exp);
      }

      StringBuilder expstr = new StringBuilder();
      int start;
      int end;

      while((start = exp.indexOf("field['")) != -1) {
         end = exp.length() <= start + 7 ? -1 : exp.indexOf("']", start + 7);

         if(end == -1) {
            break;
         }

         if(start + 7 > end) {
            break;
         }

         String name = exp.substring(start + 7, end);
         expstr.append(exp, 0, start);
         int index = name.lastIndexOf('.');
         int columnLength = -1;

         if(cols.getAttribute(name) != null && cols.getAttribute(name).getEntity() == null &&
            cols.getAttribute(name).getAttribute().contains("."))
         {
            index = -1;
            columnLength = name.length();
         }

         String entity = index == -1 ? null : name.substring(0, index);
         String attr = index == -1 ? name : name.substring(index + 1);
         AttributeRef attribute = new AttributeRef(entity, attr);

         name = format instanceof AttributeFormat2 ?
            ((AttributeFormat2) format).format(attribute, column) :
            format.format(attribute);

         if(columnLength > 0) {
            String table = name.substring(0, name.length() - columnLength - 1);
            String col = name.substring(name.length() - columnLength);
            name = table + "." + helper.getQuote() + col + helper.getQuote();
         }

         if(name == null) {
            if(entity != null) {
               attribute = new AttributeRef(null, entity + "." + attr);
               name = format instanceof AttributeFormat2 ?
                  ((AttributeFormat2) format).format(attribute, column) :
                  format.format(attribute);
            }

            if(name == null) {
               if(aliased) {
                  name = ((AliasDataRef) column).getDataRef().getName();
               }
               else {
                  if(warnOnNull) {
                     LOG.warn(
                        "Failed to get expression column [" + this.getTable().getName() + ":" +
                           column.toView() + "], attribute not found: " + attribute + " in " +
                           column.getExpression());
                  }

                  return null;
               }
            }
         }

         // sub query? not allowed
         if(isSubQuery(name)) {
            return null;
         }
         // an expression? bracket it
         else if(quote) {
            if(!isQualifiedName(name)) {
               expstr.append('(');
               expstr.append(name);
               expstr.append(')');
            }
            else {
               name = !name.contains("\"") ? quoteColumn(name) : name;
               expstr.append(name);
            }
         }

         exp = exp.substring(end + 2);
      }

      expstr.append(exp);
      return expstr.toString();
   }

   /**
    * Convert a string reference to a column to the DataRef. The string could be
    * partial name or alias.
    * @param attr the field referenced in the expression.
    * @param src the expression data ref to be converted.
    * @return the converted attribute.
    */
   protected DataRef convertAttributeInExpression(DataRef attr, DataRef src) {
      Tuple key = Tuple.createIdentityTuple(attr, src);
      Object rc = exprAttrs.get(key);

      // optimization
      if(rc != null) {
         return rc instanceof DataRef ? (DataRef) rc : null;
      }

      DataRef ref = convertAttributeInExpression0(attr, src);
      exprAttrs.put(key, ref != null ? ref : "");
      return ref;
   }

   private DataRef convertAttributeInExpression0(DataRef attr, DataRef src) {
      ColumnSelection columns = getTable().getColumnSelection();

      // @by larryl, the following match sequence seems a little reversed from
      // the logical order. The idea is that the logic matches what a user sees
      // on the GUI as much as possible. If an alias is defined, the alias is
      // used as the column header and it is the most likely (logical) name to
      // be used by a user. If alias is not defined, the attribute part is used
      // and will be seen by users as column header. Fully qualified name is
      // nevery displayed as column header, though it's most precise. For
      // example, if two columns Query1.quantity and Query2.quantity both
      // exists in a join table, the column headers will be quantity and
      // quantity_1. If they are used in expression, we find quantity_1 first
      // base on alias. Finding quantity is a little trickier. If we check
      // for fully qualified name, it will match either Query1 or Query2,
      // depending on the order (random). The middle loop eliminates that
      // problem.

      // check for alias first since the aliases are guaranteed to be
      // unique while the column ref may not be
      int size = columns.getAttributeCount();
      List<DataRef> refs = columns.stream().collect(Collectors.toList());

      for(DataRef ref : refs) {
         ColumnRef column = (ColumnRef) ref;

         if(attr.getName().equals(column.getAlias())) {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      // second we check for column header match where alias is not defined
      for(DataRef ref : refs) {
         ColumnRef column = (ColumnRef) ref;

         if(attr.getEntity() == null && column.getAlias() == null &&
            attr.getAttribute().equals(column.getAttribute()))
         {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      // next we check for fully qualified name
      for(DataRef ref : refs) {
         ColumnRef column = (ColumnRef) ref;

         if(column.equals(attr)) {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
         else if(attr.getEntity() == null &&
            attr.getAttribute().equals(column.getAttribute()))
         {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
         // a.b -> null."a.b"
         else if(column.getEntity() == null &&
            column.getName().equals(attr.getName()))
         {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
         // a.b -> table_O.a.b
         else if(attr.getName().equals(column.getAttribute())) {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      // check alias by ignoring case
      for(int i = 0; i < size; i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(attr.getName().equalsIgnoreCase(column.getAlias())) {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      // check attribute by ignoring case
      for(DataRef ref : refs) {
         ColumnRef column = (ColumnRef) ref;

         if(attr.getEntity() == null && column.getAlias() == null &&
            attr.getAttribute().equalsIgnoreCase(column.getAttribute()))
         {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      // check attribute by ignoring case and ignoring outer entity. Embedded
      // ws's table assembly is renamed so the entity would not match
      for(DataRef ref : refs) {
         ColumnRef column = (ColumnRef) ref;
         String entity = column.getEntity();

         if(entity != null && entity.startsWith("OUTER") &&
            attr.getAttribute().equalsIgnoreCase(column.getAttribute()))
         {
            if(column.getDataRef() != src) {
               return column.getDataRef();
            }
         }
      }

      return null;
   }

   /**
    * Split column to table/column pair.
    */
   protected final String[] splitColumn(String name) {
      int index = name == null ? -1 : name.lastIndexOf('.');
      int idx2 = name == null ? -1 : name.indexOf('.');

      if(index < 0) {
         return null;
      }

      ColumnSelection cols = getTable().getColumnSelection();
      String entity = name.substring(0, index);
      String attr = name.substring(index + 1);
      AttributeRef attribute = new AttributeRef(entity, attr);

      if(!containsAttribute(cols, attribute) && idx2 >= 0 && idx2 != index) {
         String entity2 = name.substring(0, idx2);
         String attr2 = name.substring(idx2 + 1);
         AttributeRef attribute2 = new AttributeRef(entity2, attr2);

         if(containsAttribute(cols, attribute2)) {
            attribute = attribute2;
         }
      }

      return new String[] {attribute.getEntity(), attribute.getAttribute()};
   }

   /**
    * Check if the specified contains attribute.
    */
   private boolean containsAttribute(ColumnSelection cols, AttributeRef attr) {
      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) cols.getAttribute(i);
         DataRef ref = column.getDataRef();

         if(Tool.equals(ref.getEntity(), attr.getEntity()) &&
            Tool.equals(ref.getAttribute(), attr.getAttribute()))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Quote a column.
    * @param column the specified column.
    * @return the quoted column.
    */
   protected String quoteColumn(String column) {
      SQLHelper helper = getSQLHelper(getUniformSQL());
      int idx = column.lastIndexOf('.');

      if(idx > 0) {
         String table = column.substring(0, idx);
         String col = column.substring(idx + 1);
         table = XUtil.quoteAlias(table, helper);
         col = XUtil.quoteNameSegment(col, helper);
         return table + "." + col;
      }

      return XUtil.quoteName(column, helper);
   }

   /**
    * Check if the query is fully mergeable.
    * @param sub true if is a sub query.
    * @return <tt>true</tt> if fully mergeable, <tt>false</tt> otherwise.
    */
   public boolean isQueryMergeable(boolean sub) throws Exception {
      if(!merged) {
         mergeable = isQueryMergeable0(sub);
         merged = true;
      }

      return mergeable;
   }

   /**
    * Validate the query.
    */
   protected void validate() {
      // validate select
      validateColumnSelection();
      ColumnSelection columns = getTable().getColumnSelection();

      // validate where
      ConditionList conds = getPreConditionList();

      // validate group by
      AggregateInfo aggregateInfo = getAggregateInfo();
      aggregateInfo.validate(columns);

      // validate having
      ConditionListWrapper wrapper = getPostConditionList();
      conds = wrapper.getConditionList();

      // validate order by
      SortInfo sortInfo = getTable().getSortInfo();
      sortInfo.validate(columns);

      // validate ranking
      wrapper = getRankingConditionList();
      conds = wrapper.getConditionList();
      validateRanking(conds);
   }

   /**
    * Check if the query is fully mergeable.
    * @param sub true if is a sub query.
    * @return <tt>true</tt> if fully mergeable, <tt>false</tt> otherwise.
    */
   private boolean isQueryMergeable0(boolean sub) throws Exception {
      // source not mergeable?
      if(!isSourceMergeable()) {
         return false;
      }

      // sql is not mergeable as a sub query?
      if(sub && !getTable().isSQLMergeable()) {
         return false;
      }

      // where not mergeable?
      if(!isPreConditionListMergeable()) {
         return false;
      }

      // group by not mergeable?
      if(!isAggregateInfoMergeable()) {
         return false;
      }

      // having not mergeable?
      if(!isPostConditionListMergeable()) {
         return false;
      }

      // order by not mergeable?
      if(!isSortInfoMergeable()) {
         return false;
      }

      // select not mergeable?
      if(!isColumnSelectionMergeable()) {
         return false;
      }

      // ranking not mergeable?
      if(!isRankingMergeable()) {
         return false;
      }

      return true;
   }

   /**
    * Check if the pre condition list is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   public boolean isPreConditionListMergeable() throws Exception {
      if(!isSourceMergeable()) {
         return false;
      }

      ConditionList conds = getPreConditionList();

      return isConditionListMergeable(conds);
   }

   /**
    * Check if a condition list is mergeable.
    * @param conds the specified condition list.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   protected boolean isConditionListMergeable(ConditionList conds)
      throws Exception
   {
      if(!isSourceMergeable()) {
         return false;
      }

      boolean isSQLite = isSQLite();
      SQLHelper sqlHelper = getSQLHelper(getUniformSQL());

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem item = conds.getConditionItem(i);
         DataRef ref = item.getAttribute();
         ref = findColumn(ref);

         if(!isAttributeMergeable(ref)) {
            return false;
         }

         XCondition xcond = item.getXCondition();

         if(xcond != null && sqlHelper != null && "mongo".equals(sqlHelper.getSQLHelperType())) {
            if(xcond.getOperation() == XCondition.ONE_OF) {
               return false;
            }
         }

         if((xcond instanceof DateCondition) && !(xcond instanceof DateRange)) {
            continue;
         }

         if(!(xcond instanceof Condition)) {
            return false;
         }

         Condition cond0 = (Condition) xcond;

         // check field value
         DataRef[] refs = cond0.getDataRefValues();

         for(int j = 0; j < refs.length; j++) {
            DataRef ref2 = refs[j];

            if(ref2 != null) {
               ref2 = findColumn(ref2);

               if(!isAttributeMergeable(ref2)) {
                  return false;
               }
            }
         }

         if(!(xcond instanceof AssetCondition)) {
            continue;
         }

         AssetCondition cond = (AssetCondition) xcond;

         // check sub query value
         SubQueryValue sub = cond.getSubQueryValue();

         if(sub == null) {
            continue;
         }

         TableAssembly table = sub.getTable();
         String source1 = table.getSource();
         String source2 = getTable().getSource();

         if(source1 == null || !source1.equals(source2)) {
            return false;
         }

         table = (TableAssembly) table.clone();
         AssetQuery query = AssetQuery.createAssetQuery(
            table, fixSubQueryMode(mode), box, true, -1L, false, true);
         query.setSubQuery(true);
         query.plan = PreAssetQuery.this.plan;

         if(!query.isQueryMergeable(true)) {
            return false;
         }

         UniformSQL sql = query.getUniformSQL();
         SQLHelper helper = getSQLHelper(sql);

         if(!helper.supportsOperation(SQLHelper.CONDITION_SUBQUERY)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if the group information is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   public boolean isAggregateInfoMergeable() throws Exception {
      if(!isSourceMergeable()) {
         return false;
      }

      // bind group by and having
      return isAggregateInfoMergeable0() && isPostConditionListMergeable0();
   }

   /**
    * Get the group info of the contained table assembly.
    * @return the group info of the contained table assembly.
    */
   public AggregateInfo getAggregateInfo() {
      if(AssetQuerySandbox.isRuntimeMode(mode)
         || AssetQuerySandbox.isEmbeddedMode(mode) || isSubQuery()
         || getTable().isAggregate()
         || getTable().getName().startsWith(Assembly.TABLE_VS))
      {
         return getTable().getAggregateInfo();
      }

      return new AggregateInfo();
   }

   /**
    * Get the AggregateInfo for post process.
    *
    * @return
    */
   protected AggregateInfo getPostAggregateInfo() {
      return null;
   }

   /**
    * Get the SortInfo for post process.
    *
    * @return
    */
   protected SortInfo getPostSortInfo() throws Exception {
      return null;
   }

   /**
    * Get the post condition list of the contained table assembly.
    * @return the post condition list of the contained table assembly.
    */
   public ConditionListWrapper getPostConditionList() {
      if(postconds == null) {
         TableAssembly table = getTable();
         boolean postConditionHasCubeMeasure =
            "true".equals(table.getProperty("Post_Condition_HasCubeMeasure"));

         if(!postConditionHasCubeMeasure && !table.isAggregate()
            && !AssetQuerySandbox.isEmbeddedMode(mode) && !isSubQuery() && ginfo.isEmpty()
            && !AssetQuerySandbox.isRuntimeMode(mode))
         {
            postconds = new ConditionList();
         }
         else {
            ConditionListWrapper pwrapper = table.getPostConditionList();
            ConditionList postconds0 = pwrapper.getConditionList();

            // ignore filtering? just remove all the conditions
            if(postconds0 != null && box.isIgnoreFiltering()) {
               postconds0.removeAllItems();
            }

            ConditionListWrapper rwrapper = getTable().getPostRuntimeConditionList();
            List list = new ArrayList();
            list.add(postconds0);
            list.add(rwrapper == null ? null : rwrapper.getConditionList());
            ConditionList conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
            postconds = conds == null ? new ConditionList() : conds;
            validateConditionList(postconds, true);
         }
      }

      return postconds;
   }

   /**
    * Check if the group information is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   private boolean isAggregateInfoMergeable0() throws Exception {
      AggregateInfo info = getAggregateInfo();

      if(info.isCrosstab()) {
         return false;
      }

      if(info.isEmpty()) {
         return true;
      }

      // where not mergeable?
      if(!isPreConditionListMergeable()) {
         return false;
      }

      if(!isRankingGroupCompatible()) {
         return false;
      }

      ColumnSelection cols = getTable().getColumnSelection(true);

      // if both grouping and distinct is defined on a sub-table, and only a sub list of
      // columns is selected from the parent table, merging it would produce a query like:
      //  select distinct col1, sum(col3) from ...
      // instead of the more semantically correct:
      //  select col1, col3 from (select distinct col1, col2, sum(col3) from ...)
      // to support it correct, we need to introduce a sub-table to capture the distinct with
      // full column list. since this usage is not common, will not consider it for now.
      if(getTable().isDistinct() && isSubTable() && !isJoinSubTable() &&
         info.getGroupCount() + info.getAggregateCount() != cols.getAttributeCount())
      {
         return false;
      }

      GroupRef[] groups = info.getGroups();

      for(int i = 0; i < groups.length; i++) {
         if(!isGroupMergeable(groups[i])) {
            if("true".equals(SreeEnv.getProperty("mv.debug"))) {
               LOG.debug("Group {} is not mergeable", groups[i]);
            }

            return false;
         }
      }

      AggregateRef[] aggregates = info.getAggregates();

      for(int i = 0; i < aggregates.length; i++) {
         if(!isAggregateMergeable(aggregates[i])) {
            if("true".equals(SreeEnv.getProperty("mv.debug"))) {
               LOG.debug("Aggregate {} is not mergeable", aggregates[i]);
            }

            return false;
         }
      }

      // if precondition/expression requires to add non-group column,
      // group by and having should be post processed
      for(int i = 0; !info.isEmpty() && i < used.getAttributeCount(); i++) {
         DataRef attr = used.getAttribute(i);
         GroupRef ref = info.getGroup(attr);

         if(ref == null) {
            if("true".equals(SreeEnv.getProperty("mv.debug"))) {
               LOG.debug("Specified column is not a group column: " + attr);
            }

            return false;
         }
      }

      return true;
   }

   /**
    * If there's a ranking condition with "Group Others" then we
    * shouldn't consider the aggregate info as merged since it
    * will be used later to apply the "Group Others" mainly for
    * chart topN
    */
   private boolean isRankingGroupCompatible() {
      ConditionListWrapper ranking = getRankingConditionList();
      ConditionList conds = ranking.getConditionList();

      for(int i = 0; i < conds.getSize(); i += 2) {
         final ConditionItem conditionItem = conds.getConditionItem(i);
         final XCondition xCondition = conditionItem.getXCondition();

         if(xCondition instanceof RankingCondition) {
            if(((RankingCondition) xCondition).isGroupOthers()) {
               return false;
            }

            AggregateInfo ainfo = getTable().getAggregateInfo();
            List<String> groups = Arrays.stream(ainfo.getGroups())
               .map(a -> a.getName()).collect(Collectors.toList());

            int idx = groups.indexOf(conditionItem.getAttribute().getName());

            // if the ranking column is nested group, can't handle it in RankingTableLens,
            // need to perform the grouping/ranking in SummaryFilter. (43730)
            if(idx > 0) {
               return false;
            }

            // if the ranking is by a data ref. need SummaryFilter to process.
            if(((RankingCondition) xCondition).getDataRef() != null) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Check if a group is mergeable.
    * @param group the specified group.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   private boolean isGroupMergeable(GroupRef group) {
      if(group.isTimeSeries()) {
         return false;
      }

      if(!isAttributeMergeable(group)) {
         if("true".equals(SreeEnv.getProperty("mv.debug"))) {
            LOG.debug("Group attribute is not mergeable: {}", group);
         }

         return false;
      }

      if(!isGroupOrderMergeable(group)) {
         if("true".equals(SreeEnv.getProperty("mv.debug"))) {
            LOG.error("Group order not mergeable: {}", group);
         }

         return false;
      }

      DataRef attr = getBaseAttribute(group);

      if(isSQLExpression(attr)) {
         UniformSQL sql = getUniformSQL();
         SQLHelper helper = sql == null ? null : getSQLHelper(sql);

         if(helper != null && !helper.supportsOperation(SQLHelper.EXPRESSION_GROUPBY)) {
            if("true".equals(SreeEnv.getProperty("mv.debug"))) {
               LOG.error("Expression group not supported: {}", attr);
            }

            return false;
         }
      }

      try {
         return !isSubQuery(getColumn(attr));
      }
      // date grouping function not available in this database
      catch(UnsupportedOperationException ex) {
         LOG.debug("Date grouping is not available in this database", ex);
         return false;
      }
   }

   /**
    * Check if is a sql expression.
    * @param ref the specified base data ref.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   protected boolean isSQLExpression(DataRef ref) {
      if(!ref.isExpression()) {
         return false;
      }

      ColumnRef column = findColumn(ref);

      if(column == null) {
         return false;
      }

      return column.isSQL();
   }

   /**
    * Check if a group order is mergeable.
    * @param group the specified group.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   protected boolean isGroupOrderMergeable(GroupRef group) {
      // named group? order is not mergeable
      return group.getNamedGroupAssembly() == null && isMergeableDataType(group);
   }

   /**
    * Check if an aggregate is mergeable.
    * @param aggregate the specified aggregate.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   protected boolean isAggregateMergeable(AggregateRef aggregate) {
      if(!isAttributeMergeable(aggregate)) {
         return false;
      }

      if(aggregate.isPercentage()) {
         return false;
      }

      UniformSQL sql = getUniformSQL();

      if(sql == null) {
         return false;
      }

      SQLHelper helper = getSQLHelper(sql);

      if(helper == null) {
         return false;
      }

      AggregateFormula formula = aggregate.getFormula();

      if(formula == null || formula == AggregateFormula.MODE ||
         formula == AggregateFormula.FIRST ||
         formula == AggregateFormula.LAST ||
         formula == AggregateFormula.PRODUCT ||
         formula == AggregateFormula.CONCAT ||
         formula == AggregateFormula.NTH_LARGEST ||
         formula == AggregateFormula.NTH_SMALLEST ||
         formula == AggregateFormula.NTH_MOST_FREQUENT ||
         formula == AggregateFormula.PTH_PERCENTILE)
      {
         return false;
      }

      if(formula == AggregateFormula.MEDIAN) {
         return XUtil.supportMedian(helper);
      }

      // for derby, the integer column average will cause precision loss, so post execute.
      if("derby".equals(helper.getSQLHelperType()) && formula == AggregateFormula.AVG &&
         aggregate.getDataRef() != null &&
         XSchema.INTEGER.equals(aggregate.getDataRef().getDataType()))
      {
         return false;
      }

      String name = formula.getFormulaName().toLowerCase();
      return helper.supportsOperation(name);
   }

   /**
    * Check if the post condition list is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   public boolean isPostConditionListMergeable() throws Exception {
      if(!isSourceMergeable()) {
         return false;
      }

      // bind group by and having
      return isAggregateInfoMergeable0() && isPostConditionListMergeable0();
   }

   /**
    * Check if the post condition list is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   protected boolean isPostConditionListMergeable0() throws Exception {
      ConditionListWrapper wrapper = getPostConditionList();
      ConditionList conds = wrapper.getConditionList();
      return isConditionListMergeable(conds);
   }

   /**
    * Check if the sort information is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   public boolean isSortInfoMergeable() throws Exception {
      if(!isSourceMergeable()) {
         return false;
      }

      SortInfo info = getTable().getSortInfo();
      AggregateInfo aggregateInfo = getAggregateInfo();
      SortRef[] sorts = info.getSorts();

      // group by not empty? only sort on group/aggregate columns are permitted
      if(!aggregateInfo.isEmpty()) {
         for(int i = sorts.length - 1; i >= 0; i--) {
            AggregateRef aggregate = aggregateInfo.getAggregate(sorts[i]);
            GroupRef group = aggregateInfo.getGroup(sorts[i]);

            if(aggregate == null && group == null) {
               info.removeSort(sorts[i]);
            }
         }
      }

      return isSortInfoMergeable0(info);
   }

   /**
    * Check if a sort information is mergeable.
    * @param info the specified sort info.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   protected boolean isSortInfoMergeable0(SortInfo info) throws Exception {
      if(info.isEmpty()) {
         return true;
      }

      JDBCQuery query = getQuery();

      if(query == null) {
         return false;
      }

      // for subquery, we will remove all the orderby fields, so here
      // we just ignore distinct_orderby and aggregate_orderby checks
      SQLHelper helper = SQLHelper.getSQLHelper(query.getDataSource(), box.getUser());

      // require explicit sorting if group output is not ordered
      // this is only for h2 so far. this condition can be removed if we change the
      // code to add an explicitly 'order by' along with 'group by'.
      // since this is only limited to h2 which is not used in production, the change
      // seems not justified. we should consider the change if it impact other databases
      if(!helper.supportsOperation(SQLHelper.GROUP_ORDERED)) {
         return false;
      }

      boolean disorder = isSubQuery() || helper.supportsOperation(SQLHelper.DISTINCT_ORDERBY);
      boolean aggorder = isSubQuery() || helper.supportsOperation(SQLHelper.AGGREGATE_ORDERBY);

      if(!disorder && getTable().isDistinct()) {
         return false;
      }

      SortRef[] sorts = info.getSorts();
      AggregateInfo ainfo = getTable().getAggregateInfo();

      if(sorts.length > 0) {
         // first/last is order sensitive, so don't merge the order by to sub-query.
         // otherwise the order on the sub-query will be lost in sql. (55974).
         for(int i = 0; i < ainfo.getAggregateCount(); i++) {
            AggregateFormula formula = ainfo.getAggregate(i).getFormula();

            if(formula == AggregateFormula.FIRST || formula == AggregateFormula.LAST) {
               return false;
            }
         }

         for(int i = 0; i < sorts.length; i++) {
            if(!isSortMergeable(sorts[i])) {
               return false;
            }

            DataRef ref = sorts[i].getDataRef();

            if(!aggorder && ref instanceof AggregateRef) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Check if a sort is mergeable.
    * @param sort the specified sort.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   protected boolean isSortMergeable(SortRef sort) {
      if(!isAttributeMergeable(sort)) {
         return false;
      }

      DataRef attr = getBaseAttribute(sort);

      try {
         return !isSubQuery(getColumn(attr));
      }
      // date grouping function not available in this database
      catch(UnsupportedOperationException ex) {
         LOG.debug("Date grouping is not available in this database", ex);
         return false;
      }
   }

   /**
    * Check if the ranking information is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   public boolean isRankingMergeable() throws Exception {
      if(!isSourceMergeable()) {
         return false;
      }

      ConditionListWrapper ranking = getRankingConditionList();
      ConditionList conds = ranking.getConditionList();
      return conds.getSize() == 0;
   }

   /**
    * Check if is sub query.
    * @param column the specified column.
    * @return <tt>true</tt> if sub query, <tt>false</tt> otherwise.
    */
   protected boolean isSubQuery(String column) {
      column = column == null ? null : column.toLowerCase();
      return column != null && column.startsWith("(select ");
   }

   /**
    * Check if is an qualified name.
    * @param name the specified name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   protected boolean isQualifiedName(String name) {
      return XUtil.isQualifiedName(name);
   }

   /**
    * Check if this query is for plan only.
    * @return <tt>true</tt> if for plan only, <tt>false</tt> otherwise.
    */
   public boolean isPlan() {
      return plan;
   }

   /**
    * Set the plan flag.
    * @param plan <tt>true</tt> if for plan only, <tt>false</tt> otherwise.
    */
   public void setPlan(boolean plan) {
      this.plan = plan;
   }

   /**
    * Get the number of child queries.
    */
   public int getChildCount() {
      return 0;
   }

   /**
    * Get the specified child query.
    */
   public AssetQuery getChild(int idx) {
      return null;
   }

   /**
    * Get a text description of how the query will be executed.
    * @return the text description.
    */
   public QueryNode getQueryPlan() throws Exception {
      setPlan(true);
      VariableTable vars = new VariableTable();

      merge(vars);

      if(!isSourceMergeable() || !isMergePreferred()) {
         if(this instanceof CubeQuery) {
            return getQueryNode(((CubeQuery) this).getCubeDefinition());
         }

         return null;
      }

      Principal user = box.getUser();

      if(user != null) {
         vars.put("_USER_", XUtil.getUserName(user));
         vars.put("_ROLES_", XUtil.getUserRoleNames(user));
         vars.put("_GROUPS_", XUtil.getUserGroups(user));
      }

      JDBCQuery query = getQuery();
      query = (JDBCQuery) XUtil.clearComments(query);
      UniformSQL sql = getUniformSQL();
      XSelection selection = sql.getSelection();
      // for sql plan, we always use the original aliases
      ((JDBCSelection) selection).setPlan(true);
      sql.clearCachedString();
      StringBuilder sb = new StringBuilder();

      sb.append(getVariablesString(query, vars));
      sb.append("[");
      sb.append(sql.toString().trim());
      sb.append("]");

      if(isQueryMergeable(false)) {
         sb.append("\n");
         sb.append(getPlanPrefix());
         sb.append(catalog.getString("common.queryMergeOK"));
      }

      if(getTable().isDistinct()) {
         sb.append("\n");
         sb.append(getPlanPrefix());
         sb.append(catalog.getString("common.distinctMerge"));
      }

      ColumnSelection columns = getTable().getColumnSelection();
      StringBuilder formulae = new StringBuilder();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(column.isExpression() && !column.isProcessed()) {
            if(formulae.length() != 0) {
               formulae.append(", ");
            }

            if(formulae.length() == 0) {
               formulae.append("[");
            }

            formulae.append(column);
         }
      }

      if(formulae.length() != 0) {
         formulae.append("]");
         sb.append("\n");
         sb.append(getPlanPrefix());
         sb.append(catalog.getString("common.formulaMerge",
            formulae));
      }

      ConditionList preconds = getPreConditionList();

      if(preconds.getSize() != 0) {
         sb.append("\n");
         sb.append(getPlanPrefix());
         sb.append(catalog.getString("common.whereMerge"));
      }

      AggregateInfo ainfo = getAggregateInfo();

      if(!ainfo.isEmpty()) {
         sb.append("\n");
         sb.append(getPlanPrefix());
         sb.append(catalog.getString("common.groupByMerge"));
      }

      ConditionListWrapper wrapper = getPostConditionList();
      ConditionList postconds = wrapper.getConditionList();

      if(postconds.getSize() != 0) {
         if(ginfo.isCrosstab() ||
            getTable() instanceof RotatedTableAssembly)
         {
            // for crosstab or rotated table,
            // we do not process the post-aggregate condition list
            sb.append("\n");
            sb.append(getPlanPrefix());
            sb.append(
               catalog.getString("common.havingMergeIgnored"));
         }
         else {
            sb.append("\n");
            sb.append(getPlanPrefix());
            sb.append(catalog.getString("common.havingMerge"));
         }
      }

      if(this.sinfo != null && !this.sinfo.isEmpty()) {
         sb.append("\n");
         sb.append(getPlanPrefix());
         sb.append(catalog.getString("common.orderByMerge"));
      }

      wrapper = getRankingConditionList();
      ConditionList rconds = wrapper.getConditionList();

      if(rconds.getSize() != 0) {
         sb.append("\n");
         sb.append(getPlanPrefix());
         sb.append(catalog.getString("common.rankingMerge"));
      }

      return getQueryNode(sb.toString());
   }

   /**
    * Get query node.
    */
   protected QueryNode getQueryNode(String description) throws Exception {
      QueryNode qnode = new QueryNode(getTable().getName());
      qnode.setDescription(description);
      qnode.setTooltip(getTable().getDescription());
      qnode.setSQLType(isSourceMergeable());
      qnode.setIconPath(getIconPath());

      return qnode;
   }

   /**
    * Get query icon.
    */
   protected String getIconPath() {
      return "/inetsoft/report/gui/composition/images/query.png";
   }

   /**
    * Process variables.
    */
   protected String getVariablesString(XQuery query) throws Exception {
      return getVariablesString(query, null);
   }

   /**
    * Process variables.
    */
   protected String getVariablesString(XQuery query, VariableTable vars)
                                       throws Exception {
      Map<String, String> map = new HashMap<>();
      vars = vars == null ? box.getVariableTable() : vars;

      if(query != null) {
         // we need some build in variables, such as session data
         Enumeration<String> qvars = query.getAllDefinedVariables();

         while(qvars.hasMoreElements()) {
            XVariable xvar = query.getVariable(qvars.nextElement());
            map.put(xvar.getName(), xvar.getSource());
         }
      }

      UserVariable[] uvars = getAllVariables();
      String source0 = getTable().getName() + " condition";

      for(int i = 0; i < uvars.length; i++) {
         String name = uvars[i].getName();

         if(map.containsKey(name)) {
            continue;
         }

         String source = uvars[i].getSource();
         source = source == null ? source0 : source;
         map.put(name, source);
      }

      ArrayList list = new ArrayList(map.keySet());
      Collections.sort(list);
      StringBuilder sb = new StringBuilder();

      if(list.size() > 0) {
         VariableTable vars2 = new VariableTable();
         vars2.addAll(vars);
         vars2.setBaseTable(box.getVariableTable());

         sb.append(catalog.getString("Parameters") + ":\n");

         for(Object name : list) {
            Object varValue = vars2.get("" + name);

            if(varValue instanceof Object[]) {
               varValue = Arrays.toString((Object[]) varValue);
            }

            sb.append("  ");
            sb.append(name);
            sb.append("[");
            sb.append(map.get(name));
            sb.append("]: ");
            sb.append(varValue);
            sb.append("\n");
         }

         sb.append("\n");
      }

      return sb.toString();
   }

   /**
    * Get date group option.
    */
   protected int getDateGroup(GroupRef group) {
      int date = group.getDateGroup();

      if(date != XConstants.NONE_DATE_GROUP) {
         return date;
      }

      DataRef ref0 = group;

      while(ref0 instanceof DataRefWrapper) {
         ref0 = ((DataRefWrapper) ref0).getDataRef();

         if(ref0 instanceof DateRangeRef) {
            return ((DateRangeRef) ref0).getDateOption();
         }
      }

      return date;
   }

   /**
    * Get all varibles of the list.
    *
    * @return the UserVarible array.
    */
   private UserVariable[] getAllVariables() {
      Set vars = new HashSet();
      ConditionList conds = getPreConditionList();

      if(conds != null) {
         UserVariable[] vars0 = conds.getAllVariables();

         if(vars0 != null && vars0.length > 0) {
            vars.addAll(Arrays.asList(vars0));
         }
      }

      ConditionListWrapper wrapper = getPostConditionList();

      if(wrapper != null) {
         conds = wrapper.getConditionList();

         if(conds != null) {
            UserVariable[] vars0 = conds.getAllVariables();

            if(vars0 != null && vars0.length > 0) {
               vars.addAll(Arrays.asList(vars0));
            }
         }
      }

      wrapper = getRankingConditionList();

      if(wrapper != null) {
         conds = wrapper.getConditionList();

         if(conds != null) {
            UserVariable[] vars0 = conds.getAllVariables();

            if(vars0 != null && vars0.length > 0) {
               vars.addAll(Arrays.asList(vars0));
            }
         }
      }

      UserVariable[] uvars = new UserVariable[vars.size()];
      vars.toArray(uvars);

      return uvars;
   }

   /**
    * Check a column is an aggregate column.
    */
   private final AggregateFormula getAggregate(DataRef wref, DataRef ref, PreAssetQuery wquery) {
      if(ref == null) {
         return null;
      }

      AggregateInfo ainfo = gmerged ? ginfo : getAggregateInfo();
      AggregateRef[] aggs = ainfo == null || ainfo.isEmpty() ?
         null : ainfo.getAggregates(ref);
      boolean agg = aggs != null && aggs.length > 0;

      if(agg || wquery == null) {
         return agg ? aggs[0].getFormula() : null;
      }

      return wquery.getAggregate(null, wref, null);
   }

   protected final XMetaInfo fixMetaInfo(XMetaInfo info, DataRef qref, DataRef cref) {
      return fixMetaInfo(info, qref, cref, null);
   }

   /**
    * Fix XMetaInfo, if the column is part date level option, it not support
    * drill.
    * @param info the meta info to be fixed.
    * @param wref the wrapped query's table data ref.
    * @param cref current query table's data ref in column selections.
    * @param wquery the query wraps in current query.
    */
   protected final XMetaInfo fixMetaInfo(XMetaInfo info, DataRef wref,
                                         DataRef cref, PreAssetQuery wquery) {
      DateRangeRef range = XUtil.findDateRage(wref);
      info = info == null ? null : info.clone();

      // apply default format for date range ref properly
      if(range != null) {
         int dateLevel = range.getDateOption();
         String dtype = range.getOriginalType();
         SimpleDateFormat dfmt = XUtil.getDefaultDateFormat(dateLevel, dtype);

         if(dfmt != null) {
            String fmt = TableFormat.DATE_FORMAT;
            String spec = dfmt.toPattern();
            XFormatInfo finfo = new XFormatInfo(fmt, spec);
            info = info == null ? new XMetaInfo() : info;
            info.setXFormatInfo(finfo);
            info.setProperty("autoCreatedFormat", "true");
         }
         else if(info != null && dateLevel != DateRangeRef.NONE_INTERVAL) {
            info.setXFormatInfo(null);
         }
      }

      // set original data type for the meta info, so when in GroupFilter,
      // SummaryFilter and CrossTabFilter will know the base data type is
      // time or not
      if(info != null) {
         info.setProperty("columnDataType",
            range == null ? null : range.getOriginalType());
      }

      int dateLevel = 0;
      AggregateFormula formula = getAggregate(wref, cref, wquery);
      boolean drill = formula == null;

      if(range != null) {
         dateLevel = range.getDateOption();
         drill = range.isApplyAutoDrill();

         // fix bug1260933656319, user created range ref,
         // ignore its drill seems more reasonable
         if(!range.isAutoCreate()) {
            drill = false;
         }
      }

      // part level? neither format nor auto drill is meaningful
      if(info != null && (dateLevel & XConstants.PART_DATE_GROUP) != 0) {
         info.setXDrillInfo(null);
      }

      // do not apply auto drill if date range ref says no
      if(info != null && !drill) {
         info.setXDrillInfo(null);
      }

      // date range ref as aggregate? date format is meaningless
      if(info == null || "true".equalsIgnoreCase(info.getProperty("autoCreatedFormat"))) {
         Util.removeIncompatibleMetaInfo(info, formula == null || formula.getDataType() == null
            ? null : new SumFormula());
      }

      return info;
   }

   /**
    * Execute an expression value (js) for condition.
    */
   public static Object execScriptExpression(String exp, Condition cond,
                                             VariableTable vtable,
                                             AssetQuerySandbox box)
   {
      ScriptEnv senv = box.getScriptEnv();
      Object val = null;
      Scriptable scope = null;

      try {
         ViewsheetSandbox vbox = box.getViewsheetSandbox();
         Viewsheet vs = vbox == null ? null : vbox.getViewsheet();
         val = senv.exec(senv.compile(exp), scope = box.getScope(), null, vs);
      }
      catch(Exception ex) {
         String suggestion = senv.getSuggestion(ex, null, scope);
         String msg = "Script error: " + ex.getMessage() +
            (suggestion != null ? "\nTo fix: " + suggestion : "") +
            "\nScript failed:\n" + XUtil.numbering(exp);

         if(LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            LOG.warn(msg);
         }

         throw new ScriptException(msg);
      }

      return getScriptValue(val, cond);
   }

   /**
    * Get script value for condition.
    * @param val value returned from javascript.
    */
   public static Object getScriptValue(Object val, Condition cond) {
      if(cond.getOperation() == Condition.DATE_IN && val instanceof String) {
         return val;
      }
      else if(val instanceof Object[]) {
         Object[] objs = (Object[]) val;

         for(int j = 0; j < objs.length; j++) {
            objs[j] = getScriptValue(objs[j], cond.getType());
         }
      }
      else if(!(val instanceof ColumnRef)) {
         val = getScriptValue(val, cond.getType());
      }

      return val;
   }

   /**
    * Get the script value object.
    * @param val the value to be get
    * @param type the data type of the value.
    * @return the script value.
    */
   private static Object getScriptValue(Object val, String type) {
      if(val instanceof java.util.Date) {
         val = Condition.getDateObject(type, val);
      }
      else if(val != null) {
         val = Condition.getObject(type, val.toString());
      }

      return val;
   }

   /**
    * Pre condition list handler.
    */
   protected class PreConditionListHandler extends ConditionListHandler {
      /**
       * Constructor.
       */
      public PreConditionListHandler() {
         super();

         // use prefix to avoid name conflict among main query and
         // sub queries, which might be a serious but secret problem
         int random = ((int) (new Random().nextDouble() * 100000));
         prefix = "dataselcond" + random + "_";

         try {
            mergeable = isPreConditionListMergeable();
         }
         catch(Exception ex) {
            mergeable = false;
         }
      }

      /**
       * Get an XExpression. If the value is a DataRef, a field is created.
       * Otherwise the value is added to the VariableTable and a variable
       * expression is returned.
       */
      @Override
      protected XExpression getExpression(Condition cond, Object val,
                                          VariableTable vars, boolean forCondition)
      {
         if(val instanceof ExpressionValue) {
            ExpressionValue eval = (ExpressionValue) val;
            String exp = eval.getExpression();
            VariableTable vtable = box.getVariableTable();
            vtable = vtable == null ? new VariableTable() : vtable;
            String varName = null;
            Object vval = null;

            if(eval.getType().equals(ExpressionValue.SQL)) {
               int idx1 = exp.indexOf("$(");
               int idx2 = exp.indexOf(')');

               if(idx1 >= 0 && idx2 > idx1 + 2) {
                  varName = exp.substring(idx1 + 2, idx2);
                  String valStr = "";

                  try {
                     vval = vtable.get(varName);
                  }
                  catch(Exception ex) {
                     vval = null;
                  }

                  if(vval instanceof Object[]) {
                     Object[] objs = (Object[]) vval;

                     if(objs.length > 0) {
                        for(int i = 0; i < objs.length - 1; i++) {
                           valStr += AbstractCondition.getValueSQLString(
                              objs[i]) + ",";
                        }

                        valStr += AbstractCondition.getValueSQLString(
                           objs[objs.length - 1]);
                     }
                  }
                  else if(vval != null){
                     valStr = AbstractCondition.getValueSQLString(vval);
                  }

                  exp = valStr == null || valStr.length() == 0 ? exp :
                     exp.substring(0, idx1) + valStr + exp.substring(idx2 + 1);
               }

               return new XExpression(parseFieldExpression(exp, format),
                  XExpression.EXPRESSION);
            }
            else {
               val = execScriptExpression(exp, cond, vtable, box);
            }
         }

         if(!isPlan()) {
            return super.getExpression(val, vars, forCondition);
         }

         if(val instanceof DataRef) {
            return dataRefToExpression((DataRef) val);
         }
         else {
            String text = AbstractCondition.getValueSQLString(val);
            return new XExpression(text, XExpression.EXPRESSION);
         }
      }

      /**
       * Parse the fields in expression.
       * @param exp the expression to be parsed.
       * @param format the specified atttribute format.
       * @return the string representation of the expression.
       */
      protected String parseFieldExpression(String exp, AttributeFormat format)
      {
         StringBuilder expstr = new StringBuilder();
         int start;
         int end;

         while((start = exp.indexOf("field['")) != -1) {
            end = exp.length() <= start + 7 ? -1 : exp.indexOf("']", start + 7);

            if(end == -1) {
               break;
            }

            if(start + 7 > end) {
               break;
            }

            String name = exp.substring(start + 7, end);
            expstr.append(exp.substring(0, start));
            int index = name.lastIndexOf('.');
            String entity = index == -1 ? null : name.substring(0, index);
            String attr = index == -1 ? name : name.substring(index + 1);
            AttributeRef attribute = new AttributeRef(entity, attr);
            name = format.format(attribute);

            if(name == null) {
               if(entity != null) {
                  attribute = new AttributeRef(null, entity + "." + attr);
                  name = format.format(attribute);
               }

               if(name == null) {
                  LOG.warn("Failed to parse formula, attribute not found: {} in {}",
                           attribute, exp);
                  return "'" + catalog.getString("Invalid Formula") + "'";
               }
            }

            // sub query? not allowed
            if(isSubQuery(name)) {
               return null;
            }
            // an expression? bracket it
            else if(!isQualifiedName(name)) {
               expstr.append('(');
               expstr.append(name);
               expstr.append(')');
            }
            else {
               expstr.append(quoteColumn(name));
            }

            exp = exp.substring(end + 2);
         }

         expstr.append(exp);
         return expstr.toString();
      }

      /**
       * Get an XExpression. Strip the quotes of a string value.
       * @param sqlTypes use the sqlTypes to strip the quotes.
       * @param value the value to be striped.
       * @return the XExpression of the value.
       */
      @Override
      protected String getStripQuotesString(SQLTypes sqlTypes, Object value) {
         if(value instanceof ExpressionValue) {
            value = ((ExpressionValue) value).getExpression();
         }

         return sqlTypes.stripQuotes(value);
      }

      /**
       * Create XFilterNode Item.
       * @param item the specified condition item.
       * @param vars the specified variable table.
       */
      @Override
      protected XFilterNodeItem createItem(ConditionItem item,
                                           VariableTable vars) {
         ColumnRef column = findColumn(item.getAttribute());
         Condition cond = (Condition) item.getXCondition();
         cond.replaceVariable(vars);
         DataRef base = getBaseAttribute(column);
         String col = PreAssetQuery.this.getColumn(column);
         XExpression field;
         DataRef ref = null;

         if(column != null) {
            ref = column.getDataRef();
         }

         // must be expression
         if(ref instanceof DataRefWrapper) {
            ref = ((DataRefWrapper) ref).getDataRef();

            if(ref instanceof AttributeRef) {
               DataRef bref = ref;
               ColumnSelection cols = PreAssetQuery.this.getTable().getColumnSelection();
               int index = cols.indexOfAttribute(bref);

               if(index >= 0) {
                  bref = cols.getAttribute(index);
               }
               else {
                  DataRef bref2 = cols.getAttribute(bref.getName(), false);

                  if(bref2 != null) {
                     bref = bref2;
                  }
               }

               UniformSQL nsql = PreAssetQuery.this.getUniformSQL();
               XSelection nselection = nsql.getSelection();
               String bcol = PreAssetQuery.this.getColumn(bref);

               if(col == null || bcol == null) {
                  throw new MessageException(col == null ?
                     catalog.getString("common.notFoundBaseColumn", bref.getName()) :
                     catalog.getString("common.notFoundExceptionBaseColumn", bref.getName()));
               }

               nselection.setBaseColumn(col, bcol);
            }
         }

         if(base.isExpression()) {
            field = new XExpression(col, XExpression.EXPRESSION);
         }
         else if(!isQualifiedName(col)) {
            field = new XExpression(col, XExpression.EXPRESSION);
         }
         else {
            field = new XExpression(col, XExpression.FIELD);
         }

         if(ref instanceof AttributeRef attributeRef && attributeRef.isSqlTypeSet()) {
            field.setSqlType(((AttributeRef) ref).getSqlType());
         }
         else if(item.getAttribute() instanceof ColumnRef columnRef && columnRef.isSqlTypeSet()) {
            int sqlType = ((ColumnRef) item.getAttribute()).getSqlType();
            field.setSqlType(sqlType);
         }

         XFilterNode node = createCondNode(cond, field, vars);
         return new XFilterNodeItem(node, item.getLevel());
      }

      /**
       * Create XFilterNode.
       */
      @Override
      protected XFilterNode createCondNode(Condition cond, XExpression field,
                                           VariableTable vars) {
         try {
            SubQueryValue sub = (cond instanceof AssetCondition) ?
               ((AssetCondition) cond).getSubQueryValue() : null;
            UniformSQL subsql = sub == null ? null :
               createSubQueryUniformSQL(sub, vars, cond.getOperation());
            XExpression subexp = subsql == null ? null : new XExpression(
               subsql, XExpression.SUBQUERY);

            if(subexp == null) {
               return super.createCondNode(cond, field, vars);
            }

            XFilterNode condnode;
            String op;

            // operators '=', '>=', '<=', '>', '<' and one of suppport sub query
            switch(cond.getOperation()) {
            case XCondition.LESS_THAN:
               op = cond.isEqual() ? "<=" : "<";
               break;
            case XCondition.GREATER_THAN:
               op = cond.isEqual() ? ">=" : ">";
               break;
            case XCondition.ONE_OF:
            case XCondition.CONTAINS:
               op = "in";
               break;
            default: // EQUAL_TO
               op = "=";
               break;
            }

            condnode = new XBinaryCondition(field, subexp, op);
            condnode.setIsNot(cond.isNegated());
            return condnode;
         }
         catch(Exception ex) {
            if(!ex.getMessage().equals("no parameter set.")) {
               LOG.warn("Failed to create condition", ex);
            }

            // ignore the node
            return new XBinaryCondition(field, field, "=");
         }
      }

      /**
       * Convert a DataRef to an XExpression.
       */
      @Override
      protected XExpression dataRefToExpression(DataRef ref) {
         String col = PreAssetQuery.this.getColumn(ref);

         if(!ref.isExpression() && isQualifiedName(col)) {
            col = quoteColumn(col);
         }

         if(ref instanceof ColumnRef) {
            ref = ((ColumnRef) ref).getDataRef();
         }

         // must be expression
         if(ref instanceof DataRefWrapper) {
            ref = ((DataRefWrapper) ref).getDataRef();

            if(ref instanceof AttributeRef) {
               AttributeRef bref = (AttributeRef) ref;
               UniformSQL nsql = PreAssetQuery.this.getUniformSQL();
               XSelection nselection = nsql.getSelection();
               String bcol = PreAssetQuery.this.getColumn(bref);
               nselection.setBaseColumn(col, bcol);
            }
         }

         return new XExpression(col, XExpression.EXPRESSION);
      }

      /**
       * Get the column.
       */
      private DataRef getColumn(ColumnSelection columns, DataRef ref) {
         int idx = columns.indexOfAttribute(ref);

         if(idx >= 0) {
            return columns.getAttribute(idx);
         }

         // column selection might come from the mirror table. For this case,
         // we need to compare attribute only
         String name = getAttribute(ref);

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef col = (ColumnRef) columns.getAttribute(i);

            if(col.getAttribute().equals(name)) {
               return col;
            }
         }

         return ref;
      }

      private String getAttribute(DataRef ref) {
         if(!(ref instanceof ColumnRef)) {
            return ref.getAttribute();
         }

         ColumnRef column = (ColumnRef) ref;
         String alias = column.getAlias();
         return alias == null || alias.length() == 0 ?
            ref.getAttribute() : alias;
      }

      /**
       * Create the sub query uniform sql of a sub query value.
       * @param sub the specified sub query value.
       * @param vars the specified variable table.
       */
      protected UniformSQL createSubQueryUniformSQL(SubQueryValue sub,
                                                    VariableTable vars,
                                                    int op)
         throws Exception
      {
         TableAssembly table = sub.getTable();
         ColumnSelection columns = table.getColumnSelection(false);
         DataRef subref = sub.getAttribute();
         subref = getColumn(columns, subref);

         // hide the other columns
         if(!sub.isCorrelated()) {
            for(int i = 0; i < columns.getAttributeCount(); i++) {
               ColumnRef column = (ColumnRef) columns.getAttribute(i);
               column.setVisible(column.equals(subref));
            }
         }

         AssetQuery query = AssetQuery.createAssetQuery(
            table, fixSubQueryMode(mode), box, true, -1L, false, true);
         query.setSubQuery(true);

         if(sub.isCorrelated()) {
            op |= XCondition.CORRELATED;
         }

         query.setSubQueryOption(op);
         query.setWhereSubQuery(true);
         query.plan = PreAssetQuery.this.plan;
         UniformSQL sql = null;

         if(!sub.isCorrelated()) {
            query.merge(vars);
            sql = query.getUniformSQL();

            // ordering in subquery is not needed
            sql.removeAllOrderByFields();
         }
         // sub query correlated? change where clause
         else if(sub.isCorrelated()) {
            DataRef subattr = sub.getSubAttribute();
            DataRef mainattr = sub.getMainAttribute();
            ColumnRef subcolumn = (ColumnRef) getColumn(columns, subattr);
            ColumnRef maincolumn = findColumn(mainattr);
            String maincol = PreAssetQuery.this.getColumn(maincolumn);
            query.merge(vars);
            String subcol = query.getColumn(subcolumn);
            UniformSQL basesql = query.getUniformSQL();

            // ordering in subquery is not needed
            basesql.removeAllOrderByFields();

            // create the sub-query
            // we can't just use the base query directly in subquery condition.
            // if the main query selects from the same base query as the
            // subquery, it would result in the subquery table sharing the
            // same table name as the main query, which makes the condition
            // incorrect, e.g.
            //   select ... from (basetbl sql ...) basetbl
            //     where val = (select col from (basetbl sql ...) basetbl
            //                  where basetbl.subcol = basetbl.subcol)
            // but it really should be:
            //   select ... from (basetbl sql ...) basetbl
            //     where val = (select col from (basetbl sql ...) subquery
            //                  where basetbl.subcol = subquery.subcol)
            sql = new UniformSQL();
            sql.addTable(sub.getQuery(), basesql);

            String subRefColumnName = subref.getAttribute();

            if(subref instanceof ColumnRef && ((ColumnRef) subref).getAlias() != null) {
               subRefColumnName = ((ColumnRef) subref).getAlias();
            }

            String subColumnName = subcolumn.getAlias() != null ?
               subcolumn.getAlias() : subcolumn.getAttribute();

            // select the aggregate column
            sql.getSelection().addColumn(
               XUtil.quoteName(subRefColumnName, getSQLHelper(sql)));

            // Need to quote expression column names too and use the table name of the
            // subquery so that the query becomes:
            //   select ... from (basetbl sql ...) basetbl
            //     where val = (select exprCol from (select 'some expression' as exprCol) subquery
            //                  where basetbl.subcol = subquery.exprCol)
            if(subcolumn.isExpression() ||
               (!subcolumn.isExpression() && query.isQualifiedName(subcol)))
            {
               // use the table name of the subquery
               subcol = XUtil.quoteAlias(sub.getQuery(), getSQLHelper(sql)) + "." +
                  XUtil.quoteNameSegment(subColumnName, getSQLHelper(sql));
            }

            XExpression subexp = new XExpression(subcol, XExpression.EXPRESSION);

            // not expression? quote it
            if(!maincolumn.isExpression() && isQualifiedName(maincol)) {
               maincol = quoteColumn(maincol);
            }

            XExpression mainexp = new XExpression(maincol, XExpression.EXPRESSION);
            XFilterNode condition = new XBinaryCondition(subexp, mainexp, "=");

            // create condition
            sql.setWhere(condition);
         }

         // @by larryl, additional columns may be added to the sql
         // (e.g. order by of expression), which would cause sql to fail since
         // a subquery should always be a single colum. This is a safty net
         // that removes any unnecessary columns.
         XSelection cols = sql.getSelection();

         String attr = query.getColumn(sub.getAttribute());
         int idx = cols.indexOf(attr);

         // @by mikec, for column which is used as an aggregate column,
         // should search it in the aggregate info list.
         if(idx < 0) {
            if(query.ginfo != null) {
               AggregateRef[] arefs = query.ginfo.getAggregates();

               for(int i = 0; i < arefs.length; i++) {
                  if(Tool.equals(sub.getAttribute(), arefs[i].getDataRef())) {
                     attr = getAggregateExpression(arefs[i]);
                     idx = cols.indexOf(attr);
                     break;
                  }
               }
            }
         }

         if(idx >= 0) {
            for(int i = cols.getColumnCount() - 1; i >= 0; i--) {
               if(i != idx && idx >= 0) {
                  cols.removeColumn(i);
               }
            }
         }

         return sql;
      }

      private boolean mergeable;
   }

   /**
    * Post condition list handler.
    */
   protected class PostConditionListHandler extends PreConditionListHandler {
      /**
       * Constructor.
       */
      public PostConditionListHandler() {
         super();
      }

      /**
       * Create XFilterNode Item.
       * @param item the specified condition item.
       * @param vars the specified variable table.
       */
      @Override
      protected XFilterNodeItem createItem(ConditionItem item,
                                           VariableTable vars) {
         DataRef ref = item.getAttribute();

         if(!(ref instanceof AggregateRef)) {
            return super.createItem(item, vars);
         }

         AggregateRef aggregate = (AggregateRef) item.getAttribute();
         Condition cond = (Condition) item.getXCondition();
         XExpression field;
         String col = getAggregateExpression(aggregate);
         cond.replaceVariable(vars);

         field = new XExpression(col, XExpression.EXPRESSION);
         XFilterNode node = createCondNode(cond, field, vars);

         return new XFilterNodeItem(node, item.getLevel());
      }

      /**
       * Convert a DataRef to an XExpression.
       */
      @Override
      protected XExpression dataRefToExpression(DataRef ref) {
         ColumnRef column = findColumn(ref);
         AggregateRef aggregate = PreAssetQuery.this.ginfo.getAggregate(column);

         if(aggregate == null) {
            return super.dataRefToExpression(ref);
         }

         String col = getAggregateExpression(aggregate);
         return new XExpression(col, XExpression.EXPRESSION);
      }
   }

   // for 10.1 bc
   private class AttributeFormat2 implements AttributeFormat {
      @Override
      public String format(AttributeRef attr) {
         return format(attr, null);
      }

      public String format(AttributeRef attr, DataRef source) {
         DataRef ref = convertAttributeInExpression(attr, source);

         if(ref instanceof ExpressionRef) {
            return getExpressionColumn((ExpressionRef) ref, this, true);
         }

         attr = (AttributeRef) ref;
         return getAttributeColumn(attr);
      }
   }

   protected boolean isDriversDataCached() {
      if(Drivers.getInstance().isDataCached()) {
         if(Drivers.getInstance().isDesignOnly()) {
            return AssetQuerySandbox.isLiveMode(mode);
         }

         return true;
      }

      return false;
   }

   /**
    * Return false if prefer not to merge processing into sql. This is currently only
    * used by spark cache so the cached data doesn't contain dynamic conditions.
    */
   protected boolean isMergePreferred() {
      // if condition placed by selection-list or range slider, don't push it down if
      // result is cached. otherwise the cached result will not be used on selection change
      if(isDriversDataCached()) {
         if(!isCacheMergeable()) {
            List<String> sqlCols = getAllSQLExpressions();

            // if sql expression exists, they must be pushed down to sql to prevent
            // error (they will fail as js expression). this condition can be removed
            // if we move the selection up like MV transformer.
            if(!sqlCols.isEmpty()) {
               if(getTable().getPreRuntimeConditionList() != null) {
                  LOG.warn("SQL expression defined on table prevented cached data to be used. " +
                              "Consider moving the sql expression to child table, " +
                              "or moving selection list/slider to the table where the expression " +
                              "is defined (or its parents): " + sqlCols);
               }
            }
            else {
               return false;
            }
         }
      }

      return true;
   }

   // get all sql expression (including recursively all parents)
   protected List<String> getAllSQLExpressions() {
      List<String> sqlCols = getSQLExpressions();
      sqlCols.addAll(parentSQLExpressions);
      return sqlCols;
   }

   // Check if merging would cause cached data to not be used.
   private boolean isCacheMergeable() {
      if(getTable().getPreRuntimeConditionList() != null) {
         return false;
      }

      if("true".equals(getTable().getProperty("vs.selection.bound"))) {
         return false;
      }

      String name = getTable().getName();

      if((name.endsWith("_O") || name.startsWith(TableAssembly.TABLE_VS)) &&
         getTable() instanceof MirrorTableAssembly) {
         TableAssembly base = (TableAssembly) ((MirrorTableAssembly) getTable()).getAssembly();

         if(base != null && "true".equals(base.getProperty("vs.selection.bound"))) {
            return false;
         }
      }

      return true;
   }

   protected boolean isSQLite() {
      try {
         if(getQuery() instanceof JDBCQuery) {
            return  Util.isSQLite(getQuery().getDataSource());
         }
      }
      catch(Exception ignore) {
      }

      return false;
   }

   protected boolean isMergeableDataType(DataRef ref) {
      if(ref instanceof GroupRef) {
         ref = ((GroupRef) ref).getDataRef();
      }

      return !isSQLite() || ref != null &&
         !(XSchema.isDateType(ref.getDataType()) || XSchema.BOOLEAN.equals(ref.getDataType()));
   }

   private static final char[] FIELD = { 'f', 'i', 'e', 'l', 'd' };
   private static final String FIELD2 = "field";
   private static final String PARAMETER = "parameter";
   private static final Pattern PARAMTER_PATTERN = Pattern.compile("\\bparameter\\.");

   protected Catalog catalog; // localization catalog
   protected int mode; // asset query mode
   protected AssetQuerySandbox box; // runtime sandbox
   protected boolean squery; // sub query flag(pre process sub query)
   protected boolean wquery; // sub query in where flag
   protected int squeryop;// sub query operation
   protected boolean stable; // sub table flag(post process sub table)
   protected boolean joinSubTable;
   protected ColumnSelection used; // the used columns
   // post sort process might be divided into two parts, the first is
   // processed before group by and having to prepare for them, and the
   // second is processed after group by and having per users' sort will
   protected SortInfo sinfo;
   protected AggregateInfo ginfo;
   protected ColumnSelection gcolumns; // group merged column selection
   protected boolean gmerged; // group merged flag
   protected ConditionList preconds; // pre conditions
   protected ConditionList postconds; // pre conditions
   protected ConditionList rankingconds; // ranking conditions
   protected AttributeFormat format; // attribute format
   protected int level; // query level
   protected boolean plan; // query plan flag
   // list of columns as table::column
   protected List<String> parentSQLExpressions = new ArrayList<>();

   private boolean mergeable; // query mergeable flag
   private boolean merged; // query merged flag
   private Map<DataRef, ColumnRef> colmap = new HashMap<>(); // optimization
   private Map<Tuple, Object> exprAttrs = new Object2ObjectOpenHashMap<>();
   private Map<String, List<AttributeRef>> expr2Attrs = new Object2ObjectOpenHashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(PreAssetQuery.class);
}
