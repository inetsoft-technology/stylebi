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
package inetsoft.mv.data;

import inetsoft.mv.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.jdbc.XFilterNode;
import inetsoft.uql.jdbc.util.ConditionListHandler;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * MVQueryBuilder, the builder generates MVQuery and its SubMVQuery. Then the
 * MVQuery will be executed at server node, and the SubMVQuery will be executed
 * at data nodes within a distributed file system.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class MVQueryBuilder {
   /**
    * Create an instance of MVQueryBuilder.
    */
   public MVQueryBuilder() {
      super();
   }

   /**
    * Create an instance of MVQueryBuilder.
    */
   public MVQueryBuilder(TableAssembly table, MV mv, VariableTable vars, XPrincipal user, String orgID) {
      this();
      init(table, mv, vars, orgID);
   }

   /**
    * Create the parent aggregate ref.
    */
   private static AggregateRef createParentAggregate(AggregateRef aref) {
      AggregateFormula form = aref.getFormula();
      ColumnRef ncolumn = getChildRef((ColumnRef) aref.getDataRef());
      AggregateFormula nform = form.getParentFormula();

      if(nform != null && nform.isTwoColumns()) {
         ColumnRef column2 = getChildRef((ColumnRef) aref.getSecondaryColumn());
         return new AggregateRef(ncolumn, column2, nform);
      }

      return new AggregateRef(ncolumn, nform);
   }

   private static ColumnRef getChildRef(ColumnRef column) {
      if(column == null) {
         return null;
      }

      String name = column.getAlias();

      if(name == null || name.length() == 0) {
         name = column.getAttribute();
      }

      String entity = column.getEntity();
      ExpressionRef eref = new ExpressionRef(null, entity, name);
      eref.setExpression("");
      eref.setVirtual(true);
      return new ColumnRef(eref);
   }

   /**
    * Check if the specified aggregate info is empty
    */
   private static boolean isEmpty(AggregateInfo ainfo) {
      if(ainfo.isEmpty()) {
         return true;
      }

      // check if only none formula exists without any group
      int gcnt = ainfo.getGroupCount();

      if(gcnt != 0) {
         return false;
      }

      int acnt = ainfo.getAggregateCount();

      for(int i = 0; i < acnt; i++) {
         AggregateRef aref = ainfo.getAggregate(i);
         AggregateFormula form = aref.getFormula();

         if(form != null && !form.equals(AggregateFormula.NONE)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Initialize MVQuery and SubMVQuery with the specified table, mv and vars.
    */
   private void init(TableAssembly table, MV mv, VariableTable vars, String orgID) {
      MVDef def = mv.getDef(false, orgID);
      final MVQueryTransformation queries = transform(table, mv, def, vars);
      query = queries.query;
      subquery = queries.subQuery;
   }

   /**
    * Get mv columns.
    */
   public static String[][] getQueryColumns(TableAssembly table, MVDef def) {
      final MVQueryTransformation queries = transform(table, null, def, null);
      SubMVQuery query = queries.subQuery;
      String[] groups = new String[query.groups.length];

      for(int i = 0; i < groups.length; i++) {
         DataRef ref = GroupedTableBlock.getDataRef(query.groups[i]);
         groups[i] = MVDef.getMVHeader(ref);
      }

      String[] aggregates = SubMVQuery.getAggregates(query.aggregates);
      return new String[][] {groups, aggregates};
   }

   private static MVQueryTransformation transform(TableAssembly table, MV mv, MVDef def,
                                                  VariableTable vars)
   {
      AggregateInfo ainfo = table.getAggregateInfo();
      boolean detail = isEmpty(ainfo) || (def.isWSMV() && !ainfo.isCrosstab() &&
         !"true".equals(table.getProperty("aggregate.down")));

      // aggregate info is empty, we need to build aggregate info to
      // support distinct and sort, which is required by VSAQuery
      if(detail) {
         ColumnSelection pubcols = table.getColumnSelection(true);
         ColumnSelection privcols = table.getColumnSelection(false);
         ColumnSelection oprivcols = privcols.clone(true);
         int clen = pubcols.getAttributeCount();
         ainfo = new AggregateInfo();

         if(clen == 1 && table.isDistinct()) {
            DataRef col = pubcols.getAttribute(0);
            String attr = MVDef.getMVHeader(col);
            MVColumn mcol = def.getColumn(attr, true);

            if(mcol != null) {
               detail = false;
            }
         }

         for(int i = 0; i < clen; i++) {
            DataRef col = pubcols.getAttribute(i);

            if(def.isWSMV()) {
               if(col instanceof ColumnRef && ((ColumnRef) col).getAlias() != null) {
                  col = VSUtil.getVSColumnRef((ColumnRef) col);
               }
            }

            DataRef bcol = MVTransformer.getBaseRef(col);
            String mvheader = MVDef.getMVHeader(bcol);
            MVColumn mvcol = def.getColumn(mvheader, true);
            mvcol = mvcol != null ? mvcol : def.getColumn(mvheader, false);

            // If both fail, try using the name of the base column for MVColumn.
            // This is needed for a "detail" query on mv table with an aggregate,
            // which is called by DefaultMVIncremental.updateDictionaryRanges().
            mvcol = mvcol != null ? mvcol : def.getColumn(col.getName(), false);

            // if column not found, we just remove it, but continue process,
            // because this column may be hidden by vpm
            // fix bug1312430745813
            if(mvcol == null) {
               // this column is removed by vpm? ignore it.
               // calculate ref handled in post processing, ignore. (54071)
               if(def.isRemovedColumn(mvheader) || isCalculateRef(col, table)) {
                  pubcols.removeAttribute(i);
                  i--;
                  clen--;
                  if(LOG.isDebugEnabled()) {
                     LOG.debug("Column not found in materialized view: " + col +
                                  ", " + def.getName());
                  }
                  continue;
               }

               // is not a removed column? throw exception to recreate mv
               throw new RuntimeException("Column not found in mv: " +
                                          col + ", " + def.getName() +
                                          ": " + def.getColumns());
            }

            // if the column is an aggregate, but we try to get the detailed
            // data to be post-processed (e.g. partial drill of a crosstab),
            // we need to change the column from the aggregate to the detail
            // column name, and make sure it exists on the column selection.
            // @by larryl, use original privcols to support multiple aggregates
            // on same base column
            if(!oprivcols.containsAttribute(bcol)) {
               DataRef ref2 = oprivcols.getAttribute(bcol.getName(), true);

               if(ref2 instanceof ColumnRef) {
                  ColumnRef col2 = (ColumnRef) ref2.clone();
                  col2.setAlias(col.getName());
                  col2.setDataType(col.getDataType());
                  pubcols.addAttribute(col2);
                  privcols.removeAttribute(col2);
                  privcols.addAttribute(0, col2);
                  bcol = col2;
               }
            }

            // treat all detail as group instead of aggregate, or a measure column
            // would be reduced to a single value
            GroupRef group = new GroupRef(bcol);

            // If using the base column for MVColumn, then use that for GroupRef.
            // This is needed for a "detail" query on mv table with an aggregate,
            // which is called by DefaultMVIncremental.updateDictionaryRanges().
            if(def.getColumn(mvheader, true) == null && def.getColumn(mvheader, false) == null) {
               group = new GroupRef(col);
            }

            // don't remove 'duplicate', allow multiple aggregate on same column
            ainfo.addGroup(group, false);

            // maintain the date group option so MV knows whether to
            // return timestamp (MVQuery.isTimestampColumn)
            if(bcol instanceof ColumnRef) {
               DataRef col2 = ((ColumnRef) bcol).getDataRef();

               if(col2 instanceof DateRangeRef) {
                  group.setDateGroup(((DateRangeRef) col2).getDateOption());
               }
            }
         }

         table.resetColumnSelection();
      }

      if(detail && table.isDistinct()) {
         detail = false;
      }

      SortInfo sinfo = table.getSortInfo();
      List<GroupRef> grouplist = new ArrayList<>();
      List<Boolean> orderlist = new ArrayList<>();
      table.resetColumnSelection();
      StringBuilder buf = new StringBuilder();
      boolean first = true;

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef gref = ainfo.getGroup(i);
         SortRef sref = sinfo.getSort(gref.getDataRef());

         // Handle columns with aliases in the ws table. This recreates the column ref with the
         // alias name as the attribute
         if(def.isWSMV()) {
            DataRef cref = gref.getDataRef();

            if(cref instanceof ColumnRef && ((ColumnRef) cref).getAlias() != null) {
               cref = VSUtil.getVSColumnRef((ColumnRef) cref);
               gref.setDataRef(cref);
            }
         }

         if(!AssetUtil.isNullExpression(gref)) {
            grouplist.add(gref);
            orderlist.add(sref == null || sref.getOrder() != XConstants.SORT_DESC);
         }
         else {
            if(!first) {
               buf.append("__^_^__");
            }

            first = false;
            buf.append(gref.getName());
         }
      }

      if(buf.length() > 0 && vars != null) {
         vars.put("__mvassetquery_null_exp_hint__", buf.toString());
      }

      List<AggregateRef> aggrlist = new ArrayList<>();

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         if(!AssetUtil.isNullExpression(ainfo.getAggregate(i))) {
            aggrlist.add(ainfo.getAggregate(i));
         }
      }

      GroupRef[] groups = grouplist.toArray(new GroupRef[0]);
      boolean[] order = new boolean[grouplist.size()];
      AggregateRef[] aggregates = aggrlist.toArray(new AggregateRef[0]);

      for(int i = 0; i < order.length; i++) {
         order[i] = orderlist.get(i);
      }

      Set<AggregateRef> subAggrs = new LinkedHashSet<>();

      for(AggregateRef aref : aggregates) {
         if(aref instanceof CompositeAggregateRef) {
            CompositeAggregateRef caggr = (CompositeAggregateRef) aref;
            List<AggregateRef> saggregates = caggr.getChildAggregates();

            for(AggregateRef saggr : saggregates) {
               AggregateRef nsaggr = createParentAggregate(saggr);
               nsaggr.setComposite(true);
               subAggrs.add(nsaggr);
            }
         }
         else {
            Collection<AggregateRef> collection = aref.getSubAggregates();
            subAggrs.addAll(collection);
         }
      }

      AggregateRef[] subrefs = new AggregateRef[subAggrs.size()];
      subAggrs.toArray(subrefs);
      List<XNode> filters = null;
      XFilterNode filter = null;
      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();

      if(wrapper != null && mv != null) {
         ConditionListHandler handler = new MVConditionListHandler(table, mv, vars, def);
         ConditionList cond = wrapper.getConditionList();

         if(cond != null) {
            cond = cond.clone();
            cond = ConditionUtil.splitDateRangeCondition(cond);
            XUtil.convertDateCondition(cond, vars, true);
            cond.validate(false);
         }

         filter = handler.createXFilterNode(cond, new UniformSQL(), vars);
         filters = mv.fixFilter(filter);
      }

      MVQuery query = new MVQuery(mv, groups, order, aggregates, subrefs, filter);
      query.setDetail(detail);
      query.setSQLHelper(AssetUtil.getSQLHelper(table));

      SubMVQuery subquery = new SubMVQuery(groups, order, subrefs, filters);
      subquery.setDetail(detail);
      subquery.setMaxRows(table.getMaxRows());

      return new MVQueryTransformation(query, subquery);
   }

   // recursively check if a column is a calculate ref.
   public static boolean isCalculateRef(DataRef col, TableAssembly table) {
      ColumnSelection cols = table.getColumnSelection(false);
      DataRef ref = cols.getAttribute(col.getName(), true);

      if(ref instanceof CalculateRef) {
         return true;
      }

      if(ref != null && table instanceof MirrorTableAssembly) {
         TableAssembly child = ((MirrorTableAssembly) table).getTableAssembly();
         return isCalculateRef(ref, child);
      }

      return false;
   }

   /**
    * Get the created query to be executed at server node.
    */
   public MVQuery getQuery() {
      return query;
   }

   /**
    * Get the created sub query to be executed at data node.
    */
   public SubMVQuery getSubQuery() {
      return subquery;
   }

   /**
    * Data class for storing transformation result.
    */
   private static class MVQueryTransformation {
      private MVQueryTransformation(MVQuery query, SubMVQuery subQuery) {
         this.query = query;
         this.subQuery = subQuery;
      }

      final MVQuery query;
      final SubMVQuery subQuery;
   }

   private MVQuery query;
   private SubMVQuery subquery;

   private static final Logger LOG = LoggerFactory.getLogger(MVQueryBuilder.class);
}
