/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.viewsheet.internal;

import inetsoft.report.*;
import inetsoft.report.composition.execution.CalcTableVSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.report.internal.table.CalcAttr;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.TextSizeLimitTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.awt.*;
import java.sql.Time;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class TableConditionUtil {
   /**
    * Create calc condition list.
    */
   public static ConditionList createCalcTableConditions(VSAssembly data,
                                                         int[][] rowcols,
                                                         String name,
                                                         ViewsheetSandbox box)
      throws Exception
   {
      ConditionList conds = new ConditionList();
      CalcTableVSAssembly calc = (CalcTableVSAssembly) data;
      TableLens lens = (TableLens) box.getData(name);
      TableLayout layout = calc.getTableLayout();
      Viewsheet vs = box.getViewsheet();

      if(lens == null || layout == null) {
         return conds;
      }

      RuntimeCalcTableLens rlens = (RuntimeCalcTableLens)
         Util.getNestedTable(lens, RuntimeCalcTableLens.class);

      XNode tree = null;
      TableAssembly bound = getTableAssembly(calc, box);

      if(rlens == null || bound == null) {
         return conds;
      }

      ColumnSelection columns = bound.getColumnSelection(false);
      columns = (ColumnSelection) columns.clone();
      String btable = calc.getTableName();

      if(btable != null) {
         CalculateRef[] calcs = vs.getCalcFields(btable);

         if(calcs != null) {
            for(CalculateRef calcf : calcs) {
               if(!columns.containsAttribute(calcf)) {
                  columns.addAttribute(calcf);
               }
            }
         }
      }

      for(int i = 0; i < rowcols.length; i++) {
         int col = rowcols[i][0];
         int row = rowcols[i][1];
         int lr = rlens.getRow(row);
         int lc = rlens.getCol(col);

         if(lr < 0 || lc < 0) {
            continue;
         }

         TableCellBinding binding =
            (TableCellBinding) layout.getCellBinding(lr, lc);

         if(binding == null) {
            continue;
         }

         if(tree == null) {
            TableLayout temp = (TableLayout) layout.clone();
            tree = LayoutTool.buildTree(temp);
         }

         XNode rroot = tree.getChild(0);
         XNode croot = tree.getChild(1);
         XNode rparent = VSLayoutTool.getParentNode(rroot, layout, binding);
         XNode cparent = VSLayoutTool.getParentNode(croot, layout, binding);

         // grand total aggregate?
         if((rparent == null || rparent == rroot) &&
            (cparent == null || cparent == croot) &&
            binding.getType() == CellBinding.BIND_COLUMN &&
            binding.getBType() == CellBinding.SUMMARY)
         {
            DataRef column = findColumn(columns, binding.getValue());

            if(column == null) {
               continue;
            }

            column = getBaseRef(column);

            if((column instanceof CalculateRef)
               && !((CalculateRef) column).isBaseOnDetail()) {
               ExpressionRef eref =
                  (ExpressionRef) ((CalculateRef) column).getDataRef();
               // add the calculate field sub aggregate ref to base table
               String expression = eref.getExpression();
               List<String> matchNames = new ArrayList<>();
               List<AggregateRef> aggs = VSUtil.findAggregate(vs,
                                                              data.getTableName(), matchNames, expression);

               if(aggs != null && aggs.size() == 1) {
                  column = getBaseRef(aggs.get(0));
               }
            }

            Condition cond = new Condition(column.getDataType());
            cond.setConvertingType(false);
            cond.setOperation(Condition.NULL);
            cond.setNegated(true);
            conds.append(new ConditionItem(column, cond, 0));
            conds.append(new ConditionItem(column, cond, 0));
         }
         else {
            List<Point> processed = new ArrayList();
            ConditionList nconds = new ConditionList();
            List namedconds = new ArrayList();

            createCalcTableConditions(rroot, layout, box, rlens, nconds,
                                      row, col, lr, lc, true, binding, columns, processed,
                                      namedconds);

            processed = new ArrayList();
            createCalcTableConditions(croot, layout, box, rlens, nconds,
                                      row, col, lr, lc, false, binding, columns, processed,
                                      namedconds);

            if(!nconds.isEmpty()) {
               nconds.trim();
               namedconds.add(nconds);
            }

            nconds = ConditionUtil.mergeConditionList(
               namedconds, JunctionOperator.AND);
            nconds = nconds == null ? new ConditionList() : nconds;
            namedconds = new ArrayList();

            if(!conds.isEmpty()) {
               conds.trim();
               namedconds.add(conds);
            }

            if(!nconds.isEmpty()) {
               namedconds.add(nconds);
            }

            conds = ConditionUtil.mergeConditionList(
               namedconds, JunctionOperator.OR);
            conds = conds == null ? new ConditionList() : conds;
         }

         conds.trim();

         if(conds.getSize() > 0) {
            conds.append(new JunctionOperator(JunctionOperator.OR, 0));
         }
      }

      conds.trim();
      return conds;
   }

   /**
    * Create the calc table condition.
    * @return true if condition is ok. false if the condition is invalid. A condition
    * is invalid if the parent cell binds to a formula, so it's impossible to find
    * the corresponding column.
    */
   private static boolean createCalcTableConditions(XNode root, TableLayout layout,
                                                   ViewsheetSandbox box,
                                                   RuntimeCalcTableLens lens,
                                                   ConditionList conds, int row,
                                                   int col, int lr, int lc,
                                                   boolean rowGroup,
                                                   TableCellBinding bind,
                                                   ColumnSelection columns,
                                                   List<Point> processed,
                                                   List namedconds)
      throws Exception
   {
      if(bind == null || conds == null || lens == null) {
         return true;
      }

      Point loc = new Point(lc, lr);

      // summary with none formula can support
      if(bind != null && bind.getType() == CellBinding.BIND_COLUMN &&
         bind.getBType() != CellBinding.SUMMARY && !processed.contains(loc))
      {
         String value = bind.getValue();
         DataRef column = findColumn(columns, VSLayoutTool.getOriginalColumn(value));
         boolean ignore = false;

         if(column == null) {
            column = findColumn(columns, value);
         }

         if(column != null) {
            Object val = lens.getObject(row, col);
            Object val2 = null; // for end of range (<)
            OrderInfo order = bind.getOrderInfo(false);

            // named group?
            if(bind.getBType() == CellBinding.GROUP && order != null && !bind.isTimeSeries()) {
               XNamedGroupInfo named = LayoutTool.getNamedGroupInfo(order);
               boolean isNamedGroupValue = false;

               if(val instanceof String) {
                  ConditionList nconds = getNamedConds(value, column, val,
                                                       order.getOthers(), named);

                  if(nconds != null) {
                     isNamedGroupValue = true;
                     namedconds.add(nconds);
                     ignore = true;
                  }
               }

               if(!isNamedGroupValue && named != null) {
                  for(String group : named.getGroups()) {
                     ConditionList groupCondition = named.getGroupCondition(group);

                     if(groupCondition == null || groupCondition.isEmpty()) {
                        continue;
                     }

                     ConditionList clone = groupCondition.clone();
                     clone.negate();

                     if(named.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF ||
                        named.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO)
                     {
                        syncConditionList(clone, value, val, false);
                     }

                     namedconds.add(clone);
                  }

                  if(!namedconds.isEmpty()) {
                     final ConditionList ncond = new ConditionList();
                     Condition nullCond = new Condition(column.getDataType());
                     nullCond.setConvertingType(false);
                     nullCond.setOperation(Condition.NULL);
                     ncond.append(new ConditionItem(column, nullCond, 0));

                     final ConditionList andedNamedConds =
                        ConditionUtil.mergeConditionList(namedconds, JunctionOperator.AND);

                     namedconds.clear();
                     namedconds.add(ConditionUtil.mergeConditionList(Arrays.asList(andedNamedConds, ncond),
                                                                     JunctionOperator.OR));
                  }
               }
            }

            // date column? may be need to use date range to support
            if(XSchema.isDateType(column.getDataType()) &&
               bind.getBType() == CellBinding.GROUP)
            {
               if(order != null) {
                  int opt = order.getOption();
                  double interval = order.getInterval();

                  if(interval > 1) {
                     // calculate the end of range (inclusive)
                     if(val instanceof java.util.Date) {
                        val2 = dateAdd((java.util.Date) val, opt, (int) (interval - 1));
                     }
                     else if(val instanceof Number) {
                        val2 = (int) (((Number) val).intValue() + interval - 1);
                     }
                  }

                  DataRef base = getBaseRef(column);
                  DataRef attr = new AttributeRef(base);
                  String name = base.getAttribute();
                  name = DateRangeRef.getName(name, opt);
                  DateRangeRef date = new DateRangeRef(name, attr);
                  date.setOriginalType(base.getDataType());
                  date.setDateOption(opt);
                  ColumnRef ncolumn = new ColumnRef(date);
                  ColumnRef ocol = (ColumnRef) columns.findAttribute(ncolumn);

                  if(ocol == null) {
                     ncolumn.setVisible(false);
                     columns.addAttribute(ncolumn);
                     column = ncolumn;
                  }
                  else {
                     column = ocol;
                  }
               }
            }

            TopNInfo topN = bind.getTopN(false);

            if(bind.getBType() == CellBinding.GROUP && topN != null &&
               val instanceof String)
            {
               String str = (String) val;
               boolean others = topN.isOthers() &&
                  str.startsWith(Catalog.getCatalog().getString("Others"));

               if(others) {
                  ignore = true;
                  createOthersCondition(conds, row, col, lens, column,
                                        !rowGroup, layout, bind, root);
               }
            }

            if(!ignore) {
               column = getBaseRef(column);
               Condition cond = new Condition(column.getDataType());
               cond.setConvertingType(false);
               cond.addValue(val);

               if(val == null || val.toString().length() == 0) {
                  cond.setOperation(Condition.NULL);
               }
               else if(val2 != null) {
                  cond.setOperation(Condition.BETWEEN);
                  cond.addValue(val2);
               }
               else {
                  cond.setOperation(Condition.EQUAL_TO);
               }

               conds.append(new ConditionItem(column, cond, 0));
               conds.append(new JunctionOperator(JunctionOperator.AND, 0));
            }
         }
      }

      processed.add(loc);
      XNode parent = VSLayoutTool.getParentNode(root, layout, bind);

      if(parent == null || parent == root) {
         return true;
      }

      Point position = getParentPosition(parent, row, col, layout, lens);
      CalcAttr pattr = (CalcAttr) parent.getValue();
      int plr = pattr.getRow();
      int plc = pattr.getCol();
      TableCellBinding pbind = (TableCellBinding) layout.getCellBinding(plr, plc);

      if(position.x < 0 || position.y < 0) {
         return true;
      }

      if(pbind != null && pbind.getType() == CellBinding.BIND_FORMULA) {
         return false;
      }

      return createCalcTableConditions(root, layout, box, lens, conds, position.x,
                                       position.y, plr, plc, rowGroup, pbind, columns, processed, namedconds);
   }

   /**
    * Create the dimension condition for a crosstab.
    */
   public static void createCrosstabConditions(CrosstabDataVSAssembly table,
                                               ConditionList conds,
                                               DataRef[] cheaders, int idx,
                                               TableLens lens, boolean iscol,
                                               String cubeType, int offset,
                                               TableDataPath path, boolean xmla)
   {
      createCrosstabConditions(table, conds, cheaders, idx, lens, iscol, cubeType, offset, path,
                               xmla, 0);
   }

   /**
    * Create the dimension condition for a crosstab.
    */
   public static void createCrosstabConditions(CrosstabDataVSAssembly table,
                                               ConditionList conds,
                                               DataRef[] cheaders, int idx,
                                               TableLens lens, boolean iscol,
                                               String cubeType, int offset,
                                               TableDataPath path, boolean xmla, int level)
   {
      VSCrosstabInfo crosstabInfo = table.getVSCrosstabInfo();
      String total = Catalog.getCatalog().getString("Total");
      String others = Catalog.getCatalog().getString("Others");
      XCube cube = table.getXCube();

      for(int j = 0; j < cheaders.length; j++) {
         DataRef column0 = cheaders[j];
         Object val;
         DCMergeCell dcObj = null;
         int row, col;

         if(iscol) {
            row = j;
            col = idx;
         }
         else {
            row = idx;
            col = j + offset;
         }

         val = getData(lens, row, col);

         if(val instanceof DCMergeCell) {
            dcObj = (DCMergeCell) val;
            val = ((DCMergeCell) val).getOriginalData();
         }

         if(total.equals(val)) {
            continue;
         }

         DataRef column = fixColumn(column0, cubeType);

         if("".equals(val) && !isParentExpanded(table, lens, iscol, row, col, column)) {
            continue;
         }

         if(!isInTableDataPath(path, lens, column) && crosstabInfo.isMergeSpan()) {
            continue;
         }

         if(column0 instanceof VSDimensionRef && ((VSDimensionRef) column0).isGroupOthers() &&
            others.equals(val))
         {
            createOthersCondition(conds, row, col, lens, column, iscol, null, null, null);
            continue;
         }

         if(column instanceof VSAggregateRef) {
            VSAggregateRef agg = (VSAggregateRef) column;
            column = agg.getDataRef();
         }
         else if(column instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) column;
            column = dim.getDataRef();
         }

         if(dcObj != null && column0 instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) column0;
            createTempDateCondition(conds, table, dim, level, dcObj);
         }

         Condition cond = new Condition(column.getDataType());
         cond.setConvertingType(false);
         int op = getOperation(val);
         cond.setOperation(op);

         if(op == Condition.LIKE) {
            val = "%" + val;
         }

         // number in xmla query causes exception, needs to be string
         if(xmla && !(val instanceof Date)) {
            val = Tool.toString(val);
         }

         if(Condition.NULL != cond.getOperation()) {
            cond.addValue(val);
         }

         conds.append(new ConditionItem(column, cond, level));
         conds.append(new JunctionOperator(JunctionOperator.AND, level));
      }
   }

   /**
    * Create the table condition.
    */
   public static ConditionList createTableConditions(TableVSAssembly tv,
                                                     ConditionList conds,
                                                     int row, int col,
                                                     TableLens lens, boolean afterGroup)
   {
      String tname = tv.getTableName();
      Viewsheet vs2 = tv.getViewsheet();
      Worksheet ws = vs2.getBaseWorksheet();
      TableAssembly tassembly = (TableAssembly) ws.getAssembly(tname);

      if(tassembly instanceof MirrorTableAssembly) {
         tassembly = ((MirrorTableAssembly) tassembly).getTableAssembly();
      }

      ColumnSelection columns = tv.getColumnSelection();
      AggregateInfo ainfo = tassembly.getAggregateInfo();
      ColumnSelection columns2 = tassembly.getColumnSelection(true);
      DataRef acolumn = col >= columns.getAttributeCount() ? null : columns.getAttribute(col);

      if(acolumn == null || !lens.moreRows(row)) {
         return conds;
      }

      ConditionList nconds = new ConditionList();
      int cidx = 0;

      for(int j = 0; j < columns.getAttributeCount(); j++) {
         DataRef column = columns.getAttribute(j);
         DataRef column2 = columns2.getAttribute(column.getName());

         if(column instanceof ColumnRef && !((ColumnRef) column).isVisible()) {
            continue;
         }

         cidx++;

         if(column2 == null && column instanceof ColumnRef) {
            ColumnRef cref = (ColumnRef) column;
            DataRef bref = cref.getDataRef();

            if(bref instanceof AliasDataRef) {
               bref = ((AliasDataRef) bref).getDataRef();
            }

            column2 = columns2.getAttribute(bref.getName());
         }

         if(column2 == null) {
            column2 = vs2.getCalcField(tname, column.getName());
         }

         if(column2 == null) {
            continue;
         }

         if(ainfo != null && !ainfo.containsGroup(column2) && !ainfo.isEmpty()) {
            continue;
         }

         if(AssetUtil.findColumn(lens, column) == -1) {
            continue;
         }

         GroupRef grp = ainfo.getGroup(column2);
         Object val = lens.getObject(row, cidx - 1);
         ConditionList namedConds = null;

         if(grp != null) {
            namedConds = getXNamedConds(grp, val, ws, afterGroup);

            if(namedConds != null) {
               List list = new ArrayList();
               nconds.trim();
               namedConds.trim();
               list.add(nconds);
               list.add(namedConds);
               nconds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
            }
         }

         if(namedConds == null && grp != null && isDateOfGroup(grp) &&
            // could be date part grouping
            val instanceof java.util.Date)
         {
            DateRangeRef drr = new DateRangeRef(
               "_daterange_", ((ColumnRef) column).getDataRef());
            drr.setOriginalType(column.getDataType());
            Condition cond = new Condition(drr.getDataType());
            cond.setConvertingType(false);
            int dgroup = grp.getDateGroup();
            Calendar cal = Calendar.getInstance();
            cal.setTime(getSQLDate(val));

            if(dgroup == XConstants.MONTH_OF_YEAR_DATE_GROUP) {
               drr.setDateOption(DateRangeRef.MONTH_INTERVAL);
               cond.addValue(cal.get(Calendar.MONTH));
            }
            else if(dgroup == XConstants.QUARTER_OF_YEAR_DATE_GROUP) {
               drr.setDateOption(DateRangeRef.QUARTER_INTERVAL);
               cond.addValue((cal.get(Calendar.MONTH)) / 3 + 1);
            }
            else if(dgroup == XConstants.WEEK_OF_YEAR_DATE_GROUP) {
               drr.setDateOption(DateRangeRef.WEEK_INTERVAL);
               cond.addValue(cal.get(Calendar.WEEK_OF_YEAR));
            }
            else if(dgroup == XConstants.DAY_OF_WEEK_DATE_GROUP) {
               drr.setDateOption(DateRangeRef.DAY_OF_WEEK_PART);
               cond.addValue(cal.get(Calendar.DAY_OF_WEEK));
            }

            ColumnRef cr = new ColumnRef(drr);
            cond.setOperation(Condition.EQUAL_TO);
            nconds.append(new ConditionItem(cr, cond, 0));
         }
         else if(namedConds == null) {
            Condition cond = new Condition(column.getDataType());
            cond.setConvertingType(false);
            cond.addValue(val);
            boolean dateGroup = grp != null && val != null;

            if(dateGroup && grp.getDateGroup() == XConstants.QUARTER_DATE_GROUP) {
               Calendar cal = Calendar.getInstance();
               cal.setTime(getSQLDate(val));
               cal.add(Calendar.MONTH, 3);
               cond.addValue(cal.getTime());
               cond.setOperation(Condition.BETWEEN);
            }
            else if(dateGroup && grp.getDateGroup() == XConstants.YEAR_DATE_GROUP) {
               Calendar cal = Calendar.getInstance();
               cal.setTime(getSQLDate(val));
               cal.add(Calendar.YEAR, 1);
               cal.add(Calendar.DATE, -1);
               cond.addValue(cal.getTime());
               cond.setOperation(Condition.BETWEEN);
            }
            else if(dateGroup && grp.getDateGroup() == XConstants.MONTH_DATE_GROUP) {
               Calendar cal = Calendar.getInstance();
               cal.setTime(getSQLDate(val));
               cal.add(Calendar.MONTH, 1);
               cond.addValue(cal.getTime());
               cond.setOperation(Condition.BETWEEN);
            }
            else if(dateGroup && grp.getDateGroup() == XConstants.WEEK_DATE_GROUP) {
               Calendar cal = Calendar.getInstance();
               cal.setTime(getSQLDate(val));
               cal.add(Calendar.DATE, 7);
               cond.addValue(cal.getTime());
               cond.setOperation(Condition.BETWEEN);
            }
            else {
               cond.setOperation(val == null ? Condition.NULL : Condition.EQUAL_TO);
            }

            nconds.append(new ConditionItem(column2, cond, 0));
         }

         nconds.append(new JunctionOperator(JunctionOperator.AND, 0));
      }

      nconds.trim();
      List list = new ArrayList();
      list.add(conds);
      list.add(nconds);
      ConditionList clist =
         ConditionUtil.mergeConditionList(list, JunctionOperator.OR);
      return clist == null ? conds : clist;
   }

   private static TableAssembly getTableAssembly(CalcTableVSAssembly calc,
                                                 ViewsheetSandbox box)
      throws Exception
   {
      String name = calc.getAbsoluteName();
      int dot = name.lastIndexOf(".");

      if(dot >= 0) {
         box = box.getSandbox(name.substring(0, dot));
         name = name.substring(dot + 1);
      }

      CalcTableVSAQuery cquery = new CalcTableVSAQuery(box, name, false);
      return cquery.getTableAssembly();
   }

   /**
    * Find column.
    */
   private static DataRef findColumn(ColumnSelection columns, String name) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref = columns.getAttribute(i);

         if(name.equals(ref.getAttribute())) {
            return ref;
         }
      }

      return null;
   }

   /**
    * Get the base ref of an aggregate ref.
    */
   private static DataRef getBaseRef(DataRef ref) {
      if(ref instanceof VSAggregateRef) {
         ref = ((VSAggregateRef) ref).getDataRef();

         if(ref instanceof ColumnRef) {
            ColumnRef cref = (ColumnRef) ref;
            DataRef bref = cref.getDataRef();

            if(bref instanceof AliasDataRef) {
               ref = ((AliasDataRef) bref).getDataRef();
            }
         }
      }

      return ref;
   }

   private static ConditionList getXNamedConds(GroupRef group, Object val,
                                               Worksheet ws, boolean afterGroup)
   {
      String named = group.getNamedGroupAssembly();

      if(named == null) {
         return null;
      }

      Object assembly = ws.getAssembly(named);

      if(!(assembly instanceof NamedGroupAssembly)) {
         return null;
      }

      NamedGroupAssembly nassembly = (NamedGroupAssembly) assembly;
      NamedGroupInfo ginfo = nassembly.getNamedGroupInfo();

      if(ginfo == null || ginfo.isEmpty()) {
         return null;
      }

      DataRef base = group.getDataRef();
      return getNamedConds(base.getName(), base, val, ginfo.getOthers(), ginfo, afterGroup);
   }

   private static ConditionList getNamedConds(String cname, DataRef column,
                                              Object val, int otherType,
                                              XNamedGroupInfo named)
   {
      return getNamedConds(cname, column, val, otherType, named, false);
   }

   private static ConditionList getNamedConds(String cname, DataRef column,
                                              Object val, int otherType,
                                              XNamedGroupInfo named, boolean afterGroup)
   {
      if(named == null || named.isEmpty()) {
         return null;
      }

      String str = val instanceof String ? (String) val : null;
      boolean others = Catalog.getCatalog().getString("Others").equals(str.trim());
      ConditionList nconds = str == null ? null : named.getGroupCondition(str);

      // process leave others
      if((nconds == null || nconds.isEmpty()) && !others) {
         nconds = new ConditionList();
         column = getBaseRef(column);
         Condition cond = new Condition(column.getDataType());
         cond.setConvertingType(false);
         cond.setOperation(val == null ?
                              Condition.NULL : Condition.EQUAL_TO);
         cond.addValue(AbstractCondition.getObject(column.getDataType(), str));
         nconds.append(new ConditionItem(column, cond, 0));
      }

      // others?
      if(others) {
         String[] grps = named.getGroups();
         List allconds = new ArrayList();

         for(String grp : grps) {
            nconds = named.getGroupCondition(grp);
            nconds = (ConditionList) nconds.clone();

            if(named.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF ||
               named.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO)
            {
               syncConditionList(nconds, cname, str, afterGroup);
            }

            allconds.add(nconds);
         }

         nconds = ConditionUtil.mergeConditionList(
            allconds, JunctionOperator.OR);

         final ConditionList nullCondList = new ConditionList();
         Condition nullCond = new Condition(column.getDataType());
         nullCond.setConvertingType(false);
         nullCond.setOperation(Condition.NULL);
         nullCond.setNegated(true);
         nullCondList.append(new ConditionItem(column, nullCond, 0));

         nconds = ConditionUtil.mergeConditionList(Arrays.asList(nconds, nullCondList), JunctionOperator.AND);

         if(!afterGroup) {
            nconds = (ConditionList) ConditionUtil.not(nconds);
         }
      }
      else if(named.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF ||
         named.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO)
      {
         nconds = (ConditionList) nconds.clone();
         syncConditionList(nconds, cname, str, afterGroup);
      }
      else if(nconds != null) {
         nconds = (ConditionList) nconds.clone();
      }

      return nconds;
   }

   /**
    * Synchronize condition list.
    */
   private static void syncConditionList(ConditionList list, String name, Object val,
                                         boolean afterGroup)
   {
      for(int i = 0; i < list.getConditionSize(); i += 2) {
         ConditionItem item = list.getConditionItem(i);
         DataRef ref = item.getAttribute();
         String attr = ref.getAttribute().trim();

         if(Catalog.getCatalog().getString("this").equals(attr) ||
            "this".equals(attr))
         {
            item.setAttribute(new ColumnRef(new AttributeRef(name)));
            XCondition xCondition = item.getXCondition();

            // fix Bug #35669 change the condition to col is equals to groupping name.
            if(afterGroup && xCondition instanceof Condition) {
               Condition condition = (Condition) xCondition;
               condition.setOperation(XCondition.EQUAL_TO);
               condition.setNegated(false);
               condition.setEqual(true);
               condition.setType(XSchema.STRING);
               List<Object> values = new ArrayList<>();
               values.add(val);
               condition.setValues(values);
            }
         }
      }
   }

   /**
    * Add the number of intervals to the date.
    */
   private static java.util.Date dateAdd(java.util.Date obj, int doption, int n) {
      switch(doption) {
      case DateRangeRef.YEAR_INTERVAL:
         return dateAdd(Calendar.YEAR, n, obj);
      case DateRangeRef.QUARTER_INTERVAL:
         return dateAdd(Calendar.MONTH, n * 3, obj);
      case DateRangeRef.MONTH_INTERVAL:
         return dateAdd(Calendar.MONTH, n, obj);
      case DateRangeRef.WEEK_INTERVAL:
         return dateAdd(Calendar.WEEK_OF_YEAR, n, obj);
      case DateRangeRef.DAY_INTERVAL:
         return dateAdd(Calendar.DAY_OF_YEAR, n, obj);
      case DateRangeRef.HOUR_INTERVAL:
         return dateAdd(Calendar.HOUR_OF_DAY, n, obj);
      case DateRangeRef.MINUTE_INTERVAL:
         return dateAdd(Calendar.MINUTE, n, obj);
      case DateRangeRef.SECOND_INTERVAL:
         return dateAdd(Calendar.SECOND, n, obj);
      }

      return obj;
   }

   // add the number of intervals to the date
   private static java.util.Date dateAdd(int field, int amount, java.util.Date date) {
      GregorianCalendar cal = new GregorianCalendar();

      cal.setTime(date);
      cal.add(field, amount);

      if(date instanceof Time) {
         return new Time(cal.getTimeInMillis());
      }

      return cal.getTime();
   }

   /**
    * Create the one-of condition for Others group.
    */
   private static void createOthersCondition(ConditionList conds, int row,
                                             int col, TableLens lens,
                                             DataRef column, boolean iscol,
                                             TableLayout layout,
                                             TableCellBinding bind, XNode root)
   {
      Condition cond = new Condition(column.getDataType());
      String others = Catalog.getCatalog().getString("Others");
      TableDataPath path = lens.getDescriptor().getCellDataPath(row, col);
      cond.setConvertingType(false);
      cond.setOperation(Condition.ONE_OF);
      cond.setNegated(true);
      int cnt = iscol ? lens.getColCount() : lens.getRowCount();
      final OrderInfo orderInfo = bind == null ? null : bind.getOrderInfo(false);
      final XNamedGroupInfo named = orderInfo != null ?  LayoutTool.getNamedGroupInfo(orderInfo) : null;
      boolean hasNull = false;
      Set<Object> values = new HashSet<>();
      final Set<String> groups = named != null ? new HashSet<>(Arrays.asList(named.getGroups())) :
         Collections.emptySet();

      for(int idx = 0; idx < cnt; idx++) {
         boolean same = iscol
            ? lens.getDescriptor().isCellDataPath(row, idx, path)
            : lens.getDescriptor().isCellDataPath(idx, col, path);
         Object val = iscol ? getData(lens, row, idx) : getData(lens, idx, col);

         if(!same || (val != null && val.toString().startsWith(others)) ||
            (orderInfo != null && val instanceof String && groups.contains(val)))
         {
            continue;
         }

         if(!isDifferentCalcGroup(lens, root, layout, bind, iscol, row, col, idx) &&
            !isDifferentCrosstabGroup(lens, path, iscol, row, col, idx))
         {
            val = "".equals(val) ? null : val;

            if(val == null) {
               hasNull = true;
            }

            values.add(val);
         }
      }

      if(hasNull && values.size() == 1) {
         cond.setOperation(Condition.NULL);
         conds.append(new ConditionItem(column, cond, 0));
         conds.append(new JunctionOperator(JunctionOperator.AND, 0));
         return;
      }

      if(!values.isEmpty()) {
         Condition cond0 = new Condition(column.getDataType());
         cond0.setConvertingType(false);
         cond0.setOperation(Condition.NULL);
         cond0.setNegated(hasNull);
         conds.append(new ConditionItem(column, cond0, 0));
         int level = lens instanceof CrossFilter && ((CrossFilter) lens).getRowHeaderCount() > 1 ? 1 : 0;
         conds.append(new JunctionOperator(hasNull ? JunctionOperator.AND : JunctionOperator.OR, level));
         values.remove(null);

         values.forEach(cond::addValue);
         conds.append(new ConditionItem(column, cond, 0));
         conds.append(new JunctionOperator(JunctionOperator.AND, 0));
      }
   }

   private static boolean isDifferentCrosstabGroup(TableLens lens, TableDataPath path,
                                                   boolean iscol, int row, int col, int idx)
   {
      int pathLength = path.getPath().length;

      if(lens instanceof HiddenRowColFilter) {
         HiddenRowColFilter filter = (HiddenRowColFilter) lens;

         if(filter.getTable() instanceof CrossFilter) {
            row = filter.getBaseRowIndex(row);
            col = filter.getBaseColIndex(col);
            lens = filter.getTable();
         }
      }

      boolean isCrosstab = lens instanceof TextSizeLimitTableLens &&
         ((TextSizeLimitTableLens) lens).getTable() instanceof CrossFilter;

      if((lens instanceof CrossFilter || isCrosstab) && pathLength > 1) {
         CrossFilter crosstab = lens instanceof CrossFilter ? (CrossFilter) lens :
            (CrossFilter) ((TextSizeLimitTableLens) lens).getTable();

         if(iscol) {
            for(int i = 0; i < pathLength - 1 && i < crosstab.getColHeaderCount(); i++) {
               Object othersValue = getData(lens, i, col);
               Object value = getData(lens, i, idx);

               if(!Objects.equals(othersValue, value)) {
                  return true;
               }
            }
         }
         else {
            for(int i = 0; i < pathLength - 1; i++) {
               Object othersValue = getData(lens, row, i);
               Object value = getData(lens, idx, i);

               if(!Objects.equals(othersValue, value)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean isDifferentCalcGroup(TableLens lens, XNode root, TableLayout layout,
                                               TableCellBinding bind, boolean iscol, int row,
                                               int col, int idx)
   {
      if(bind != null && lens instanceof RuntimeCalcTableLens) {
         XNode parent = VSLayoutTool.getParentNode(root, layout, bind);

         if(parent == null || parent == root) {
            return false;
         }

         RuntimeCalcTableLens calc = (RuntimeCalcTableLens) lens;
         int lr = calc.getRow(iscol ? row : idx);
         int lc = calc.getCol(iscol ? idx : col);
         TableCellBinding nbind =
            (TableCellBinding) layout.getCellBinding(lr, lc);

         if(!hasSameGroup(root, layout, bind, nbind)) {
            return true;
         }

         if(!hasSameParentValue(parent, layout, row, col, lr, lc, idx, iscol, root, calc)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check the cell binding is have same group.
    */
   private static boolean hasSameGroup(XNode root, TableLayout layout,
                                       TableCellBinding obind, TableCellBinding nbind)
   {
      if(obind == null || nbind == null) {
         return false;
      }

      XNode oparent = VSLayoutTool.getParentNode(root, layout, obind);
      XNode nparent = VSLayoutTool.getParentNode(root, layout, nbind);

      if(!Tool.equals(oparent, nparent)) {
         return false;
      }

      return true;
   }

   /**
    * Check the cell binding is have same group value.
    */
   private static boolean hasSameParentValue(XNode p, TableLayout layout,
                                             int row, int col, int lr, int lc,
                                             int idx, boolean iscol, XNode root,
                                             RuntimeCalcTableLens lens)
   {
      Object value1 = getParentCellValue(p, lr, lc, row, col, layout, lens);
      Object value2 = getParentCellValue(p, lr, lc,
                                         iscol ? row : idx, iscol ? idx : col, layout, lens);

      if(!Tool.equals(value1, value2)) {
         return false;
      }

      p = p.getParent();

      if(p == null || p == root) {
         return true;
      }

      Point position = getParentPosition(p, row, col, layout, lens);
      CalcAttr pattr = (CalcAttr) p.getValue();
      row = position.x;
      col = position.y;
      lr = pattr.getRow();
      lc = pattr.getCol();

      if(!hasSameParentValue(p, layout, row, col, lr, lc, idx,
                             iscol, root, lens))
      {
         return false;
      }

      return true;
   }

   /**
    * Get parent cell value.
    */
   private static Object getParentCellValue(XNode parent, int lr, int lc,
                                            int row, int col, TableLayout layout, RuntimeCalcTableLens lens)
   {
      Point position = getParentPosition(parent, row, col, layout, lens);

      if(position.x < 0 || position.y < 0) {
         return null;
      }

      return lens.getObject(position.x, position.y);
   }

   /**
    * Get parent cell position.
    */
   private static Point getParentPosition(XNode parent, int row, int col,
                                          TableLayout layout,
                                          RuntimeCalcTableLens lens)
   {
      CalcAttr pattr = (CalcAttr) parent.getValue();
      int plr = pattr.getRow();
      int plc = pattr.getCol();
      TableCellBinding pbind =
         (TableCellBinding) layout.getCellBinding(plr, plc);
      String cellName = layout.getRuntimeCellName(pbind);
      Point position = lens.getFieldRowCol(cellName, row, col);
      return position == null ? new Point(-1, -1) : position;
   }

   /**
    * Get the cell data.
    */
   private static Object getData(TableLens lens, int row, int col) {
      return lens instanceof DataTableLens
         ? ((DataTableLens) lens).getData(row, col)
         : lens.getObject(row, col);
   }

   /**
    * Create ColumnRef for crosstab with date grouping.
    */
   private static DataRef fixColumn(DataRef column, String cubeType) {
      if(column instanceof VSDimensionRef) {
         VSDimensionRef dimension = (VSDimensionRef) column;

         if(dimension.isDateTime() || XCube.SQLSERVER.equals(cubeType)) {
            GroupRef gr = dimension.createGroupRef(null);

            if(gr != null) {
               return gr.getDataRef();
            }
         }
      }

      return column;
   }

   /**
    * Check if a the cell's parent is expand.
    */
   private static boolean isParentExpanded(CrosstabDataVSAssembly table,
                                           TableLens lens, boolean iscol, int row, int col, DataRef column) {
      CrosstabTree ctree = ((CrosstabVSAssembly) table).getCrosstabTree();

      if(ctree == null) {
         return true;
      }

      if(!iscol) {
         return ctree.isParentsExpanded(lens, row, col);
      }

      if(row < 1) {
         return true;
      }

      Object pval = getData(lens, row - 1, col);
      return ctree.isParentExpanded(column.getName(), pval);
   }

   /**
    * Check if a column in TableDataPath.
    */
   private static boolean isInTableDataPath(TableDataPath path,
                                            TableLens lens, DataRef column) {
      lens = Util.getNestedTable(lens, CrossTabFilter.class);

      if(!(lens instanceof CrossFilter)) {
         return false;
      }

      TableLens baselens = ((CrossTabFilter) lens).getTable();

      if(baselens == null) {
         return false;
      }

      int idx = -1;

      if(column instanceof VSDimensionRef) {
         GroupRef gref = ((VSDimensionRef) column).createGroupRef(null);
         idx = AssetUtil.findColumn(baselens, gref.getDataRef());
      }

      if(idx < 0) {
         idx = AssetUtil.findColumn(baselens, column);
      }

      if(idx < 0 && column instanceof ColumnRef &&
         ((ColumnRef) column).getDataRef() instanceof DateRangeRef)
      {
         DateRangeRef range = (DateRangeRef) ((ColumnRef) column).getDataRef();

         // if not pushdown aggregates in CrosstabVSAQuery, then the none level date range won't be
         // added into the columnselection, so the column header in tablelens has no "None" group
         // prefix, here need check data path with the column name without "None()" to find the
         // column index.
         if(range.getDateOption() == DateRangeRef.NONE_INTERVAL) {
            idx = AssetUtil.findColumn(baselens, range.getDataRef());
         }
      }

      if(idx < 0) {
         return false;
      }

      List headers = Arrays.asList(path.getPath());
      String header = Util.getHeader(baselens, idx).toString();
      String header2 = NamedRangeRef.getBaseName(header);

      if(headers.contains(header) || headers.contains(header2)) {
         return true;
      }

      boolean duplicateMatch = headers.stream()
         .filter(String.class::isInstance)
         .filter(h -> Pattern.matches("^.+\\.\\d+$", (String) h))
         .map(h -> ((String) h).substring(0, ((String) h).lastIndexOf('.')))
         .anyMatch(h -> Tool.equals(h, header) || Tool.equals(h, header2));

      if(duplicateMatch) {
         return true;
      }

      if(lens instanceof CrossTabCubeFilter) {
         CrossTabCubeFilter cube = (CrossTabCubeFilter) lens;
         DataRef col = cube.getColumn(header);

         if(headers.contains(col.getAttribute()) ||
            headers.contains(col.getName())) {
            return true;
         }
      }

      return false;
   }

   private static void createTempDateCondition(ConditionList conds, CrosstabDataVSAssembly cross,
                                               VSDimensionRef column, int level, DCMergeCell val)
   {
      String dc = DateComparisonUtil.getDcPartCol(cross.getVSCrosstabInfo());

      if(!Tool.equals(dc, column.getFullName())) {
         return;
      }

      CrosstabVSAssemblyInfo info = (CrosstabVSAssemblyInfo) cross.getVSAssemblyInfo();
      XDimensionRef[] dims = info.getTempDateGroupRef();

      if(!(val instanceof DCMergeDatePartFilter.MergePartCell)) {
         return;
      }

      ColumnSelection cols = getColumnSelection(cross);

      if(cols == null) {
         return;
      }

      DCMergeDatePartFilter.MergePartCell cell = (DCMergeDatePartFilter.MergePartCell) val;

      for(int i = 0; i < dims.length; i++) {
         Object value = cell.getValue(i);
         DataRef col = dims[i];

         if(dims[i] instanceof VSDimensionRef) {
            VSDimensionRef dim = (VSDimensionRef) dims[i];

            if(dim.isIgnoreDcTemp()) {
               continue;
            }

            col = dim.createGroupRef(cols);

            if(col instanceof GroupRef) {
               col = ((GroupRef) col).getDataRef();
            }
         }

         Condition cond = new Condition(col.getDataType());
         cond.setConvertingType(false);
         int op = getOperation(value);
         cond.setOperation(op);

         if(op == Condition.LIKE) {
            value = "%" + value;
         }

         if(Condition.NULL != cond.getOperation()) {
            cond.addValue(value);
         }

         conds.append(new ConditionItem(col, cond, level));
         conds.append(new JunctionOperator(JunctionOperator.AND, level));
      }
   }

   private static ColumnSelection getColumnSelection(CrosstabDataVSAssembly assembly) {
      String tname = assembly == null ? null : assembly.getTableName();

      if(tname == null || tname.equals("")) {
         return null;
      }

      Viewsheet vs = assembly.getViewsheet();
      Worksheet ws = vs == null ? null : vs.getBaseWorksheet();
      TableAssembly table = ws == null ? null : (TableAssembly) ws.getAssembly(tname);

      if(table == null) {
         return null;
      }

      table = (TableAssembly) table.clone();
      ColumnSelection cols = table.getColumnSelection();

      CrosstabVSAssemblyInfo vinfo = (CrosstabVSAssemblyInfo) assembly.getVSAssemblyInfo();
      VSCrosstabInfo vsCrosstabInfo = vinfo.getVSCrosstabInfo();
      XDimensionRef[] dims = vsCrosstabInfo.getDcTempGroups();

      if(dims == null || dims.length == 0) {
         return cols;
      }

      SourceInfo sourceInfo = vinfo.getSourceInfo();
      String source = sourceInfo.getSource();
      CalculateRef[] calcs = vs.getCalcFields(source);

      if(calcs == null || calcs.length == 0) {
         return cols;
      }

      for(int i = 0; i < calcs.length; i++) {
         if(calcs[i].isDcRuntime()) {
            DataRef old = cols.getAttribute(calcs[i].getName());

            if(old != null) {
               cols.removeAttribute(old);
            }

            calcs[i].setVisible(true);
            cols.addAttribute((CalculateRef) calcs[i].clone());
            VSUtil.addCalcBaseRefs(cols, null, Arrays.asList(calcs));
         }
      }

      return cols;
   }

   private static int getOperation(Object condVal) {
      if(condVal == null || condVal.toString().length() == 0) {
         return Condition.NULL;
      }
      else {
         return Condition.EQUAL_TO;
      }
   }

   /**
    * Check is date of group or not.
    */
   private static boolean isDateOfGroup(GroupRef gr) {
      return gr.getDateGroup() == XConstants.QUARTER_OF_YEAR_DATE_GROUP ||
         gr.getDateGroup() == XConstants.MONTH_OF_YEAR_DATE_GROUP ||
         gr.getDateGroup() == XConstants.DAY_OF_WEEK_DATE_GROUP ||
         gr.getDateGroup() == XConstants.WEEK_OF_YEAR_DATE_GROUP;
   }

   // convert java.util.Date to java.sql.Date
   private static java.sql.Date getSQLDate(Object val) {
      return (val instanceof java.sql.Date)
         ? (java.sql.Date) val
         : (val instanceof java.util.Date
         ? new java.sql.Date(((java.util.Date) val).getTime())
         : null);
   }
}
