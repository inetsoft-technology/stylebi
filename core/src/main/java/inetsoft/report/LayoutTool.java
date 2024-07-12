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
package inetsoft.report;

import inetsoft.report.BaseLayout.Region;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.internal.TableElementDef;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.layout.GroupableTool;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.script.formula.NamedCellRange;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.reflect.Array;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utilities API for change table layout.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class LayoutTool {
   /**
    * Check if the layout contains script binding.
    */
   public static boolean hasScriptBinding(TableLayout layout) {
      return hasScriptOrStaticBinding(layout, false);
   }

   private static boolean hasScriptOrStaticBinding(TableLayout layout, boolean isStaticText) {
      if(layout == null) {
         return false;
      }

      int bindingType = isStaticText ? CellBinding.BIND_TEXT : CellBinding.BIND_FORMULA;

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region region = layout.getRegion(i);

         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               CellBinding bind = region.getCellBinding(r, c);
               boolean empty = false;

               if(bind != null && isStaticText && "".equals(bind.getValue())) {
                  empty = true;
               }

               if(bind != null && bind.getType() == bindingType && !empty) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Check if the layout contains static text binding.
    */
   public static boolean hasStaticTextBinding(TableLayout layout) {
      return hasScriptOrStaticBinding(layout, true);
   }

   /**
    * Create data path mapping.
    */
   public static void createDataPathMapping(TableLayout layout, TableLens lens) {
      if(lens == null) {
         return;
      }

      Map<TableDataPath, TableDataPath> r2dmap = createDataPathMapping(lens, layout);
      Map<TableDataPath, TableDataPath> d2rmap = getDesign2RuntimeMap(lens, layout);
      layout.setPathMapping(r2dmap, d2rmap);
   }

   /**
    * Create named group mapping array spec for mapList().
    */
   public static boolean isSimpleNamedGroup(XNamedGroupInfo info) {
      if(info == null) {
         return true;
      }

      String[] groups = info.getGroups();

      for(String group : groups) {
         ConditionList clist = info.getGroupCondition(group);

         if(clist == null || clist.getSize() == 0) {
            continue;
         }

         for(int i = 0; i < clist.getSize(); i += 2) {
            XCondition cond = clist.getXCondition(i);

            if(!(cond instanceof Condition) || cond.isNegated()) {
               return false;
            }

            JunctionOperator op = clist.getJunctionOperator(i + 1);

            if(cond.getOperation() != Condition.ONE_OF &&
               cond.getOperation() != Condition.EQUAL_TO ||
               op != null && op.getJunction() != JunctionOperator.OR)
            {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Count regions row count.
    */
   public static int getRowCount(BaseLayout.Region[] regions) {
      return getRowCount(regions, false);
   }

   /**
    * Count regions row count.
    */
   public static int getRowCount(BaseLayout.Region[] regions,
                                 boolean ignoreInvisible) {
      int r = 0;

      for(int i = 0; i < regions.length; i++) {
         if(ignoreInvisible && !regions[i].isVisible()) {
            continue;
         }

         r += regions[i].getRowCount();
      }

      return r;
   }

   /**
    * Find the cell binding source attr.
    */
   public static SourceAttr findSource(TableCellBinding cell,
                                       SourceAttr source)
   {
      if(cell.getSource() == null) {
         return source;
      }

      SourceAttr sattr = null;

      if(checkSource(source, cell)) {
         sattr = source;
      }
      else {
         for(int i = 0; i < source.getJoinSourceCount(); i++) {
            SourceAttr src = source.getJoinSource(i);

            if(checkSource(src, cell)) {
               sattr = src;
               break;
            }
         }
      }

      return sattr;
   }

   /**
    * Check srouce attr.
    */
   public static boolean checkSource(XSourceInfo source, TableCellBinding cell)
   {
      if(cell == null || cell.getSource() == null ||
         Tool.equals(source.getSource(), cell.getSource()) &&
         (Tool.equals(source.getPrefix(), cell.getSourcePrefix()) ||
         "".equals(source.getPrefix())) &&
         source.getType() == cell.getSourceType())
      {
         return true;
      }

      return false;
   }

   public static JoinInfo getCommonDimension(TableCellBinding cell,
                                             SourceAttr source)
   {
      if(source == null) {
         return null;
      }

      JoinInfo common = null;
      SourceAttr sattr = findSource(cell, source);
      String value = cell.getValue();

      for(int i = 0; i < source.getJoinRelationCount(); i++) {
         JoinInfo info = source.getJoinRelation(i);

         if(cell.getType() == CellBinding.BIND_FORMULA) {
            String var = getExpressionVar(cell, source);
            value = getGroupName(value, var);
         }

         for(int j = 0; j < info.getSourceCount(); j++) {
            if(info.getSource(j).equals(sattr) &&
               info.getField(j).getName().equals(value))
            {
               common = info;
               break;
            }
         }

         if(common != null) {
            break;
         }
      }

      return common;
   }

   /**
    * Format cross table symbol, for example, expansion, group or aggregate.
    */
   protected static void formatCrossSymbol(CalcGroup[] grps,
                                           CalcAggregate[] sums,
                                           TableLayout layout,
                                           TableLens lens)
   {
      if(lens == null) {
         return;
      }

      TableDataDescriptor desc = lens.getDescriptor();
      int colheader = lens.getHeaderColCount();

      for(int i = 0; i < lens.getRowCount(); i++) {
         TableDataPath rpath = desc.getRowDataPath(i);
         BaseLayout.Region region = layout.getRegion(rpath);
         int grow = layout.locateRow(rpath);
         int rrow = layout.convertToRegionRow(grow);

         for(int j = 0; j < lens.getColCount(); j++) {
            if(lens.getObject(i, j) != null) {
               TableDataPath cell = desc.getCellDataPath(i, j);
               int type = cell.getType();
               String[] path = cell.getPath();
               Object val = lens.getObject(i, j);
               String header = path.length > 0 ? path[path.length - 1] :
                  (val == null ? null : val.toString());
               CellBinding bind = region == null ? null :
                  region.getCellBinding(rrow, j);

               if(!(bind instanceof TableCellBinding) ||
                  bind.getType() != CellBinding.BIND_COLUMN)
               {
                  continue;
               }

               TableCellBinding cbind = (TableCellBinding) bind;

               if(type == HEADER) {
                  GroupableTool.formatDetailSymbol(cbind,
                     GroupableCellBinding.EXPAND_NONE);
               }
               else if(type == DETAIL) {
                  GroupableTool.formatDetailSymbol(cbind,
                     GroupableCellBinding.EXPAND_V);
               }
               else if(type == G_HEADER) {
                  if(path.length == 0) {
                     GroupableTool.formatDetailSymbol(cbind,
                        GroupableCellBinding.EXPAND_NONE);
                  }
                  else {
                     GroupableTool.formatGroupSymbol(cbind, j >= colheader ?
                        GroupableCellBinding.EXPAND_H :
                        GroupableCellBinding.EXPAND_V);
                  }
               }
               else if(type == G_FOOTER) {
                  // group summary total cell
                  if(findFieldIndex(grps, header) >= 0) {
                     GroupableTool.formatDetailSymbol(cbind,
                        GroupableCellBinding.EXPAND_NONE);
                  }
                  // summary cell
                  else {
                     GroupableTool.formatSummarySymbol(cbind);
                  }
               }
               else if(type == FOOTER) {
                  // grand summary total cell
                  if(findFieldIndex(sums, header) >= 0) {
                     GroupableTool.formatSummarySymbol(cbind);
                  }
                  // grand total cell
                  else {
                     GroupableTool.formatDetailSymbol(cbind,
                        GroupableCellBinding.EXPAND_NONE);
                  }
               }
            }
         }
      }
   }

   /**
    * Add layout Region.
    */
   protected static void addLayoutRegion(int start, int end,
                                         TableLens lens,
                                         TableLayout layout)
   {
      if(lens == null) {
         return;
      }

      TableDataDescriptor desc = lens.getDescriptor();

      for(int i = start; i < end; i++) {
         TableDataPath rpath = desc.getRowDataPath(i);
         BaseLayout.Region oregion = layout.getRegion(rpath);

         if(oregion != null) {
            oregion.setRowCount(oregion.getRowCount() + 1);
            continue;
         }

         BaseLayout.Region region = layout.new Region();
         region.setRowCount(1);
         layout.addRegion(rpath, region);
      }
   }

   /**
    * Create crosstab cell binding.
    * @param metadata crosstab metadata table
    * @param sample same as metadata or CrossCalcFilter (with calc headers).
    */
   protected static void createCrosstabCellBinding(TableLens metadata, TableLens sample,
                                                   TableLayout layout)
   {
      int colheader = metadata.getHeaderColCount();
      int rowheader = metadata.getHeaderRowCount();
      CrossTabFilter crosstab = Util.getCrosstab(metadata);
      TableDataDescriptor desc = metadata.getDescriptor();
      List<String> measureHeaders = crosstab.getMeasureHeaders(false);

       // generate cell binding
      for(int i = 0; i < metadata.getRowCount(); i++) {
         TableDataPath rpath = desc.getRowDataPath(i);
         int rtype = rpath.getType();
         BaseLayout.Region region = layout.getRegion(rpath);

         for(int j = 0; j < metadata.getColCount(); j++) {
            TableDataPath cpath = desc.getColDataPath(j);
            int ctype = cpath.getType();
            Object val = metadata.getObject(i, j);
            String value = val == null ? "" : val.toString();

            TableDataPath cell = desc.getCellDataPath(i, j);
            String[] path = cell.getPath();
            String header = (j >= colheader || i >= rowheader) &&
               path.length > 0 ? path[path.length - 1] : value;

            // cell binding type
            int celltype = getBindingType(rpath, cpath);

            if(celltype != CellBinding.BIND_TEXT) {
               celltype = crosstab.isTotalCell(i, j) ? CellBinding.BIND_TEXT : celltype;
            }

            String header0 = header;

            if((cell.getType() == TableDataPath.SUMMARY || cell.getType() == TableDataPath.TRAILER) &&
               celltype == CellBinding.BIND_COLUMN && header != null)
            {
               header = getRealMeasureHeader(header, measureHeaders);
            }

            String data = header;

            if(celltype == CellBinding.BIND_TEXT) {
               data = value;

               if(sample != null) {
                  Object sampleVal = sample.getObject(i, j);

                  // calc table only support %-of and other trend/comparisons are ignored.
                  // so only copy the headers for % (47825, 47921).
                  if(sampleVal != null && sampleVal.toString().startsWith("% of")) {
                     data = sampleVal.toString();
                  }
               }
            }

            TableCellBinding binding = new TableCellBinding(celltype, data);

            if(celltype == CellBinding.BIND_COLUMN) {
               binding.setBType(rtype == G_HEADER || ctype == G_HEADER ?
                  TableCellBinding.GROUP : TableCellBinding.SUMMARY);

               if(!Tool.equals(header, header0)) {
                  binding.setValue0(header0);
               }
            }

            // crosstab every region contains only one row
            region.setCellBinding(0, j, binding);
         }
      }

      // set span
      for(int r = 0; r < metadata.getRowCount(); r++) {
         for(int c = 0; c < metadata.getColCount(); c++) {
            layout.setSpan(r, c, metadata.getSpan(r, c));
         }
      }
   }

   /**
    * @return the real measure header for the duplicated measure.
    */
   private static String getRealMeasureHeader(String header, List<String> measureHeaders) {
      if(header == null || measureHeaders == null || measureHeaders.size() == 0) {
         return header;
      }

      if(measureHeaders.indexOf(header) != -1) {
         return header;
      }

      int idx = header.lastIndexOf(".");

      if(idx != -1) {
         String tail = header.substring(idx + 1);

         try {
            Integer.parseInt(tail);
            header = header.substring(0, idx);
         }
         catch(Exception e) {
         }

         if(measureHeaders.indexOf(header) != -1) {
            return header;
         }
      }

      idx = header.indexOf(":");

      if(idx != -1) {
         header = header.substring(idx + 1).trim();
      }

      return header;
   }

   /**
    * Find group index.
    */
   public static int findFieldIndex(DataRef[] groups, String name) {
      for(int i = 0; i < groups.length; i++) {
         String gname = guessPreferName(groups, groups[i], i);

         if(gname.equals(name)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Guess a preferred name.
    */
   public static String guessPreferName(
      DataRef[] fields, DataRef cfield, int lvl)
   {
      int dup = findDupCount(fields, cfield, lvl);
      return BindingTool.getFieldName(cfield) + (dup > 0 ? "." + dup : "");
   }

   /**
    * Find a field dup times.
    */
   public static int findDupCount(DataRef[] fields, DataRef cfield, int lvl) {
      int dup = 0;
      DataRef pfield = BindingTool.getPureField(cfield);

      for(int i = 0; i < fields.length; i++) {
         if(pfield.equals(BindingTool.getPureField(fields[i])) && i < lvl) {
            dup++;
         }
      }

      return dup;
   }

   /**
    * Get all Detail fields from a calc table
    * @param btype   the TableCellBinding type to filter by
    */
   public static List<TableCellBinding> getTableCellBindings(TableLayout layout, int btype) {
      List<TableCellBinding> cells = new ArrayList<>();

      for(int reg = 0; reg < layout.getRegionCount(); reg++) {
         Region region = layout.getRegion(reg);
         for(int row = 0; row < region.getRowCount(); row++) {
            for(int col = 0; col < region.getColCount(); col++) {
               TableCellBinding binding = (TableCellBinding) region.getCellBinding(row, col);
               if(binding != null && binding.getType() == CellBinding.BIND_COLUMN &&
                  (binding.getBType() == btype || btype == 0))
               {
                  cells.add(binding);
               }
            }
         }
      }

      return cells;
   }

   /**
    * Get all Detail fields from a calc table
    * @param btype   the TableCellBinding type to filter by
    */
   protected static List<TableCellBinding> getTableCellBindings(
      FormulaTable table, int btype)
   {
      TableLayout layout = table.getTableLayout();
      return getTableCellBindings(layout, btype);
   }

   /**
    * Create detail field match the cell.
    */
   public static DataRef createDetailField(TableCellBinding cell, FormulaTable table) {
      DataRef ref = VSLayoutTool.findAttribute(table, cell, null);

      if(ref == null) {
         ref = new ColumnRef(new AttributeRef(cell.getValue()));
      }

      return ref;
   }

   /**
    * Create a AggregateField that matches the cellbinding.
    */
   public static CalcAggregate createAggregateField(TableCellBinding cell,
                                                    FormulaTable table)
   {
      DataRef ref = VSLayoutTool.findAttribute(table, cell, null);
      DataRef ref2 = null;

      if(ref == null) {
         ref = new AttributeRef(cell.getValue());
      }

      FormulaStorage fstore = parseFormula(cell.getFormula());

      if(fstore != null && fstore.secondf != null) {
         ref2 = new ColumnRef(new AttributeRef(fstore.secondf));
      }

      String fname = "none";

      if(fstore != null) {
         fname = fstore.formula;

         if(fname.equals("countDistinct")) {
            fname = "DistinctCount";
         }
      }

      AggregateFormula f = AggregateFormula.getFormula(fname.toLowerCase());
      AggregateRef aref = new AggregateRef(ref, ref2, f);

      if(fstore != null) {
         aref.setPercentageOption(fstore.percent);

         if(fstore.n != null) {
            try {
               aref.setN(Integer.parseInt(fstore.n));
            }
            catch(Exception ex) {
               LOG.warn("N/P value is not an integer: " + fstore.n, ex);
            }
         }
      }

      return aref;
   }

   protected static void fillCalcTableLens(FormulaTable table,
                                           VariableTable vars,
                                           boolean crossTabSupported)
   {
      syncCalcTopN(table);
      TableLayout layout = table.getTableLayout();
      layout = (TableLayout) layout.clone();
      layout.replaceVariables(vars);
      int col = layout.getColCount();
      int row = layout.getVisibleRowCount();
      CalcTableLens clens = new CalcTableLens(row, col);
      clens.setEditMode(layout.getCalcEditMode());

      CalcTableLens olens = table.getBaseTable() instanceof CalcTableLens ?
         (CalcTableLens) table.getBaseTable() : null;

      if(olens != null) {
         clens.setHeaderColCount(olens.getHeaderColCount());
         clens.setTrailerColCount(olens.getTrailerColCount());
      }

      BaseLayout.Region[] headers = layout.getRegions(HEADER);
      int hrcnt = getRowCount(headers, true);
      clens.setHeaderRowCount(hrcnt);
      BaseLayout.Region[] footers = layout.getRegions(TableDataPath.TRAILER);
      int trcnt = getRowCount(footers, true);
      clens.setTrailerRowCount(trcnt);
      layout = applyDefaultGroups(layout);

      Map<String, CellHolder> n2c = null;
      Map<String, Point> c2p = null; // cell binding --> position

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region tregion = layout.getRegion(i);

         if(!tregion.isVisible()) {
            continue;
         }

         for(int rr = 0; rr < tregion.getRowCount(); rr++) {
            int r = convertToGlobalRow(layout, tregion, rr, true);

            clens.setRowHeight(r, tregion.getRowHeight(rr));

            for(int j = 0; j < col; j++) {
               TableCellBinding bind =
                  (TableCellBinding) tregion.getCellBinding(rr, j);

               if(bind == null) {
                  continue;
               }

               clens.setCellName(r, j, layout.getRuntimeCellName(bind));
               clens.setRowGroup(r, j, bind.getRowGroup());
               clens.setColGroup(r, j, bind.getColGroup());
               clens.setMergeCells(r, j, bind.isMergeCells());
               clens.setMergeRowGroup(r, j, bind.getMergeRowGroup());
               clens.setMergeColGroup(r, j, bind.getMergeColGroup());
               clens.setPageAfter(r, j, bind.isPageAfter());
               clens.setOrderInfo(r, j, bind.getOrderInfo(true));
               clens.setTopN(r, j, bind.getTopN(true));
               clens.setBound(r, j, bind.getType() != CellBinding.BIND_TEXT ||
                              bind.getValue() != null);

               Rectangle span = tregion.findSpan(rr, j);

               // the cell is inside a span? ignore it
               if(bind.isEmpty() || span != null && (span.x != 0 || span.y != 0)) {
                  continue;
               }

               // only set expansion for those visible cells,
               // so CalcTableLens.getDefaultGroup can find
               // a useable cell as its parent
               // fix bug1293519379193
               clens.setExpansion(r, j, bind.getExpansion());

               if(bind.getType() == CellBinding.BIND_TEXT) {
                  clens.setObject(r, j, bind.getValue());
               }
               else if(bind.getType() == CellBinding.BIND_COLUMN) {
                  if(n2c == null) {
                     n2c = createNameMap(layout);
                  }

                  if(c2p == null) {
                     c2p = createPositionMap(layout);
                  }

                  boolean sortOthersLast = true;

                  if(table instanceof CalcTableVSAssembly) {
                     CalcTableVSAssemblyInfo info =
                        (CalcTableVSAssemblyInfo) ((CalcTableVSAssembly) table).getVSAssemblyInfo();

                     if(info.isSortOthersLastEnabled()) {
                        sortOthersLast = info.isSortOthersLast();
                     }
                  }

                  String exp = createCalcBindingExpression(table, n2c, layout,
                     bind, c2p, new Point(r, j), crossTabSupported, sortOthersLast);
                  clens.setFormula(r, j, exp);
               }
               else if(bind.getType() == CellBinding.BIND_FORMULA) {
                  clens.setFormula(r, j, bind.getValue());
               }
            }
         }
      }

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region region = layout.getRegion(i);

         if(!region.isVisible()) {
            continue;
         }

         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               clens.setSpan(convertToGlobalRow(layout, region, r, true), c,
                  region.getSpan(r, c));
            }
         }
      }

      table.setTable(clens);
   }

   /**
    * Create tree node for the layout, return a root node, which contains two
    * child nodes, one for row group, one for column group.
    */
   public static XNode buildTree(TableLayout layout) {
      XNode root = new XNode("__ROOT__");
      XNode rroot = new XNode("__ROW_ROOT__");
      root.addChild(rroot);
      XNode croot = new XNode("__COL_ROOT__");
      root.addChild(croot);

      if(layout == null) {
         return root;
      }

      // apply default groups, so it can exactly match the result of fillCalcTableLens
      layout = applyDefaultGroups(layout);
      int col = layout.getColCount();
      Map<String, CellHolder> n2c = null;
      Map<String, Point> c2p = null; // cell binding --> position

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region tregion = layout.getRegion(i);

         if(!tregion.isVisible()) {
            continue;
         }

         for(int rr = 0; rr < tregion.getRowCount(); rr++) {
            int r = convertToGlobalRow(layout, tregion, rr, true);

            for(int j = 0; j < col; j++) {
               TableCellBinding bind = (TableCellBinding) tregion.getCellBinding(rr, j);

               if(bind == null || bind.isEmpty() ||
                  bind.getType() != CellBinding.BIND_COLUMN &&
                     bind.getType() != CellBinding.BIND_FORMULA)
               {
                  continue;
               }

               Rectangle span = tregion.findSpan(rr, j);

               // the cell is inside a span? ignore it
               if(span != null && (span.x != 0 || span.y != 0)) {
                  continue;
               }

               CellHolder holder = new CellHolder(r, j, bind);

               if(n2c == null) {
                  n2c = createNameMap(layout);
               }

               if(c2p == null) {
                  c2p = createPositionMap(layout);
               }

               List<CellHolder> rholders = new ArrayList<>();
               List<CellHolder> cholders = new ArrayList<>();
               List<Point> locs = new ArrayList<>();
               Set<String> processed = new HashSet<>();
               buildGroupList(layout, bind.getRowGroup(), rholders,
                              n2c, c2p, locs, processed, true, null);
               processed = new HashSet<>();
               buildGroupList(layout, bind.getColGroup(), cholders,
                              n2c, c2p, locs, processed, false, null);
               addNodes(rroot, rholders, holder, true);
               addNodes(croot, cholders, holder, false);
            }
         }
      }

      return root;
   }

   private static void addNodes(XNode root, List<CellHolder> groups,
                                CellHolder cell, boolean row)
   {
      if(groups.size() <= 0) {
         if(row && cell.cell.getExpansion() == TableCellBinding.EXPAND_V ||
            !row && cell.cell.getExpansion() == TableCellBinding.EXPAND_H)
         {
            addChild(root, null, cell);
         }
         // aggregate cell? add to root directly
         else if(cell.cell.getBType() == CellBinding.SUMMARY) {
            addChild(root, null, cell);
         }

         return;
      }

      CellHolder parent = null;

      for(CellHolder group : groups) {
         addChild(root, parent, group);
         parent = group;
      }

      addChild(root, parent, cell);
   }

   private static void addChild(XNode root, CellHolder parent,
                                CellHolder child)
   {
      XNode pnode = null;

      // if parent group is null, use root
      if(parent == null) {
         pnode = root;
      }
      else {
         // find the parent node in the tree
         String pname = getNodeName(parent);
         pnode = findNode(root, pname);

         // if parent node does not exist, add to root
         if(pnode == null) {
            pnode = createNode(parent);
            root.addChild(pnode);
         }
      }

      String cname = getNodeName(child);
      XNode cnode = findNode(root, cname);

      // if child node does not exist, add to parent
      if(cnode == null) {
         cnode = createNode(child);
      }
      // if child node already exists, remove from the current parent and
      // add to new group
      else {
         cnode.getParent().removeChild(cnode, false);
      }

      pnode.addChild(cnode);
   }

   private static XNode findNode(XNode node, String name) {
      if(name.equals(node.getName())) {
         return node;
      }

      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);
         XNode val = findNode(child, name);

         if(val != null) {
            return val;
         }
      }

      return null;
   }

   private static XNode createNode(CellHolder holder) {
      XNode node = new XNode(getNodeName(holder));
      node.setValue(new CalcAttr(holder.row, holder.col));
      return node;
   }

   private static String getNodeName(CellHolder holder) {
      return holder.row + "X" + holder.col;
   }

   /**
    * Create aggregate expression for calc.
    */
   public static String createAggExpression(CalcGroup[] gfields,
                                            String[] cellnames,
                                            int level, CalcAggregate afield,
                                            String var, int position,
                                            TableCellBinding cell)
   {
      level = Math.min(level, gfields.length);
      String expression = cell.getExpression();

      if(expression == null) {
         FormulaStorage fstore = parseFormula(afield);
         String exp = fstore.formula + "(" + var + "[\'" +
            escapeColName(BindingTool.getFieldName(afield));
         String cond = createGroupCond(gfields, cellnames, 0, level, true);

         exp += cond;
         exp += "\']";

         if(fstore.secondf != null) {
            exp += ", " + var + "[\'" + fstore.secondf + cond + "\']";
         }

         if(fstore.n != null) {
            exp += ", " + fstore.n;
         }
         else if(afield instanceof AggregateRef) {
            AggregateRef aref = (AggregateRef) afield;

            if(aref.getN() > 0) {
               exp += ", " + aref.getN();
            }
         }

         exp += ")";

         if(fstore.percent > 0) {
            String percent = createPercentageExpression(gfields, cellnames,
               level, afield, var, position, cell, fstore);

            if(percent != null) {
               exp += "/" + percent;
            }
         }

         return exp;
      }

      ExpressionRef eref = new ExpressionRef();
      eref.setExpression(cell.getExpression());

      ExpressionRef.AttributeEnumeration e =
         (ExpressionRef.AttributeEnumeration) eref.getAttributes();

      // prepare expression mapping
      while(e.hasMoreElements()) {
         e.nextElement();
      }

      Map<String, String> e2c = e.getExpToColMapping();
      expression = expression.trim();
      String hasArithmetic = ".+[+\\-\\*/%].+";

      for(String key : e2c.keySet()) {
         String value = e2c.get(key);
         expression = generateRuntimeScript(expression, key, value);
         TableCellBinding ncell = (TableCellBinding) cell.clone();
         CalcAggregate nafield = afield;
         buildAggFormula(value, ncell, nafield);
         String exp = createAggExpression(gfields, cellnames, level, nafield, var, position, ncell);
         key = replaceSymbol(value);
         expression = expression.replace(key, exp.matches(hasArithmetic) ? "(" + exp + ")" : exp);
      }

      return expression;
   }

   /**
    * Generate runtime scirpt.
    */
   private static String generateRuntimeScript(
      String exp, String key, String value)
   {
      String runtimeFormula = exp.toUpperCase();
      char[] runtimes = exp.toCharArray();
      key = key.toUpperCase();
      String nname = replaceSymbol(key);
      replaceExpression(runtimeFormula, runtimes, key, nname, 0);
      value = replaceSymbol(value);
      runtimeFormula = new String(runtimes);
      runtimeFormula = runtimeFormula.replace(nname, value);

      return runtimeFormula;
   }

   /*
    * replace expession string.
    */
   private static String replaceSymbol(String value) {
      String nname = value.toUpperCase();
      nname = nname.replace('(', '_');
      nname = nname.replace(')', '_');
      nname = nname.replace('\'', '_');
      nname = nname.replace('\'', '_');
      nname = nname.replace('[', '_');
      nname = nname.replace(']', '_');
      nname = nname.replace(':', '_');
      // two column aggregate will introduce the ' ' and ','
      nname = nname.replace(' ', '_');
      nname = nname.replace(',', '_');
      nname = nname.replace('.', '_');
      return nname;
   }

   /*
    * replace expession string.
    */
   private static void replaceExpression(String runtimeFormula, char[] runtimes,
                                         String key, String value, int index)
   {
      int idx = runtimeFormula.indexOf(key, index);

      if(idx >= 0) {
         int nextidx = idx + key.length();

         for(int i = idx; i < nextidx; i++) {
            value.getChars(0, value.length(), runtimes, idx);
         }

         //need to replace again if exp has next substring.
         replaceExpression(runtimeFormula, runtimes, key, value, nextidx);
      }
   }

   /**
    * Build vs aggregate formula string.
    */
   private static void buildAggFormula(String col, TableCellBinding cell,
                                       CalcAggregate afield)
   {
      String formulaName = BindingTool.getFormulaString(col);
      String original = VSLayoutTool.getOriginalColumn(col);
      original = Tool.replaceAll(original, "[", "");
      original = Tool.replaceAll(original, "]", "");
      String[] columns = Tool.split(original, ',');
      String formula = formulaName + "<" + afield.getPercentageType() + ">";

      if(columns.length == 2) {
         formula += "(" + columns[1].trim() + ")";
      }

      cell.setExpression(null);
      cell.setValue(columns[0]);
      cell.setFormula(formula);

      if(afield instanceof AggregateRef) {
         ((AggregateRef) afield).setDataRef(new AttributeRef(columns[0]));
         afield.setFormulaName(formula);
      }
   }

   /**
    * Create percentage expression.
    */
   private static String createPercentageExpression(CalcGroup[] gfields,
                                                    String[] cellnames,
                                                    int level,
                                                    CalcAggregate afield,
                                                    String var, int position,
                                                    TableCellBinding cell,
                                                    FormulaStorage fstore)
   {
      // fix bug1283224779814, do not apply percent of group on total value
      if(((fstore.percent == StyleConstants.PERCENTAGE_OF_ROW_GROUP ||
         fstore.percent == StyleConstants.PERCENTAGE_OF_ROW_GRANDTOTAL)) ||
         ((fstore.percent == StyleConstants.PERCENTAGE_OF_COL_GROUP ||
         fstore.percent == StyleConstants.PERCENTAGE_OF_COL_GRANDTOTAL)) ||
         fstore.percent == StyleConstants.PERCENTAGE_OF_GRANDTOTAL ||
         fstore.percent == StyleConstants.PERCENTAGE_OF_GROUP && level > 0)
      {
         String aname = BindingTool.getFieldName(afield);
         int start = 0;
         int end = 0;
         String cond = "";

         if(fstore.percent == StyleConstants.PERCENTAGE_OF_GROUP) {
            end = level - 1;
            cond = createGroupCond(gfields, cellnames, start, end, true);
            aname += cond;
         }
         else if(fstore.percent == StyleConstants.PERCENTAGE_OF_ROW_GROUP) {
            end = level - 1;
            cond = createGroupCond(gfields, cellnames, start, end, true);
            aname += cond;
         }
         else if(fstore.percent == StyleConstants.PERCENTAGE_OF_ROW_GRANDTOTAL)
         {
            end = position;
            cond = createGroupCond(gfields, cellnames, start, end, true);
            aname += cond;
         }
         else if(fstore.percent == StyleConstants.PERCENTAGE_OF_COL_GROUP) {
            if(position - 1 >= 0) {
               ArrayList<CalcGroup> groups = new ArrayList<>();

               for(int i = 0; i < gfields.length; i++) {
                  if(i == position - 1) {
                     continue;
                  }

                  groups.add(gfields[i]);
               }

               ArrayList<String> names = new ArrayList<>();

               for(int i = 0; i < cellnames.length; i++) {
                  if(i == position - 1) {
                     continue;
                  }

                  names.add(cellnames[i]);
               }

               gfields = groups.toArray(new CalcGroup[0]);
               cellnames = names.toArray(new String[0]);
               level = gfields.length;
            }

            end = level;
            cond = createGroupCond(gfields, cellnames, start, end, true);
            aname += cond;
         }
         else if(fstore.percent == StyleConstants.PERCENTAGE_OF_COL_GRANDTOTAL) {
            start = position;
            end = level;
            cond = createGroupCond(gfields, cellnames, start, end, true);
            aname += cond;
         }
         else {
            // necessary for adding inGroups for topn
            cond = createGroupCond(gfields, cellnames, start, end, true);
            aname += cond;
         }

         String exp = fstore.formula + "(" + var + "[\'" + aname + "\']";

         if(fstore.secondf != null) {
            exp += ", " + var + "[\'" + fstore.secondf + cond + "\']";
         }

         return exp + ")";
      }

      return null;
   }

   /**
    * Get the cell path for crosstab cross the row path and column path.
    */
   protected static int getBindingType(TableDataPath rpath, TableDataPath cpath)
   {
      int rtype = rpath.getType();
      int ctype = cpath.getType();

      // corner? text binding
      if((rtype == G_HEADER || rtype == TableDataPath.SUMMARY_HEADER) &&
         (ctype == G_HEADER || ctype == TableDataPath.SUMMARY_HEADER))
      {
         return CellBinding.BIND_TEXT;
      }
      // summary header? text binding
      else if(rtype == TableDataPath.SUMMARY_HEADER ||
         ctype == TableDataPath.SUMMARY_HEADER)
      {
         return CellBinding.BIND_TEXT;
      }
      // grand total cell? text binding
      else if(rtype == FOOTER && ctype == G_HEADER ||
         ctype == FOOTER && rtype == G_HEADER)
      {
         return CellBinding.BIND_TEXT;
      }
      // total binding?

      return CellBinding.BIND_COLUMN;
   }

   /**
    * Create cell binding.
    */
   protected static TableCellBinding createCellBinding(int stype, String value)
   {
      return createCellBinding(CellBinding.BIND_COLUMN, stype,
         TableCellBinding.EXPAND_V, value);
   }

   /**
    * Parse formula expression.
    */
   protected static FormulaStorage parseFormula(CalcAggregate afield) {
      String formula = afield == null ? null : afield.getFormulaName();
      return parseFormula(formula);
   }

   public static FormulaStorage parseFormula(String formula) {
      if(formula == null) {
         return null;
      }

      String param = null;
      int percent = -1;

      String formulaName = formula;
      int param1 = formula.indexOf("(");
      int param2 = formula.lastIndexOf(")");

      if(param1 > 0 && param2 > 0 && param1 < param2) {
         param = formula.substring(param1 + 1, param2);
         formulaName = formula.substring(0, param1);
      }

      int pdot1 = formula.indexOf("<");
      int pdot2 = formula.indexOf(">");

      if(pdot1 > 0 && pdot2 > 0 && pdot1 < pdot2) {
         String pstr = formula.substring(pdot1 + 1, pdot2);

         // For report script, it is like this: Correlation(Calc_double)<-1>
         // For viewsheet script, it it like this: Correlation<-1>(Calc_double)
         if(formulaName.equals(formula) || pdot1 < param1) {
            formulaName = formula.substring(0, pdot1);
         }

         try {
            percent = Integer.parseInt(pstr);
         }
         catch(Exception ex) {
            LOG.warn("Invalid formula percent value: " + pstr, ex);
         }
      }

      if("DistinctCount".equals(formulaName)) {
         formulaName = "countDistinct";
      }
      // @by davyc, why?
      /*
      else if("Correlation".equals(formula)) {
         formula = "correl";
      }
      else if("Covariance".equals(formula)) {
         formula = "covar";
      }
      else if("WeightedAverage".equals(formula)) {
         formula = "weightedavg";
      }
      */

      // first letter to lower
      String first = formulaName.substring(0, 1);
      formulaName = formulaName.substring(1);
      formulaName = first.toLowerCase() + formulaName;
      return new FormulaStorage(formulaName, param, percent);
   }

   /**
    * Get interval option.
    */
   protected static String getInterval(OrderInfo info) {
      if(info == null) {
         return null;
      }

      int option = info.getOption();

      // support interval?
      if(option == XConstants.YEAR_DATE_GROUP ||
         option == XConstants.QUARTER_DATE_GROUP ||
         option == XConstants.MONTH_DATE_GROUP ||
         option == XConstants.WEEK_DATE_GROUP ||
         option == XConstants.DAY_DATE_GROUP ||
         option == XConstants.HOUR_DATE_GROUP ||
         option == XConstants.MINUTE_DATE_GROUP ||
         option == XConstants.SECOND_DATE_GROUP ||
         option == XConstants.MILLISECOND_DATE_GROUP)
      {
         double interval = info.getInterval();
         return "interval=" + interval;
      }

      return null;
   }

   /**
    * Create group condition.
    */
   protected static String createGroupCond(CalcGroup[] gfields,
                                           String[] cellnames, int start,
                                           int end, boolean subtopn)
   {
      return createGroupCond(gfields, cellnames, start, end, gfields, cellnames, subtopn);
   }

   protected static String createGroupCond(CalcGroup[] gfields,
                                           String[] cellnames, int start,
                                           int end, CalcGroup[] topnfields,
                                           String[] topnnames, boolean subtopn)
   {
      String cond = "";
      StringBuffer expr = new StringBuffer("");

      if(gfields != null) {
         for(int i = start; i < end; i++) {
            String ccond = getGroupCond(cond, expr, gfields[i], cellnames[i]);

            if(ccond != null && ccond.length() > 0) {
               cond += (cond.length() > 0 ? ";" : "") + ccond;
            }
         }
      }

      if(subtopn) {
         addSubTopNCond(expr, topnfields, topnnames);
      }

      if(cond.length() > 0) {
         cond = "@" + cond;
      }

      if(expr.length() > 0) {
         cond += "?" + expr;
      }

      return cond;
   }

   /**
    * Create group expression for calc.
    */
   protected static String createGroupExpression(CalcGroup[] fields,
                                                 String[] cellnames, int idx,
                                                 CalcAggregate[] aggrs,
                                                 String var, JoinInfo info,
                                                 XSourceInfo source,
                                                 TableCellBinding cell,
                                                 List<TableCellBinding> list,
                                                 FormulaTable table,
                                                 List<Point> locs, Point point,
                                                 boolean sortOthersLast)
   {
      String exp = null;
      String opt = "";
      String name = escapeColName(BindingTool.getPureField(fields[idx]).getName());
      String group = name;

      // handle topN and sortByValue
      TopNInfo topn = fields[idx].getTopN();
      OrderInfo order = fields[idx].getOrderInfo();
      aggrs = getTopAggregetes(aggrs, table);
      CalcAggregate sorton = null; // sort on field, for topn
      CalcAggregate sorton2 = null; // sort on field, for sort by val
      boolean needsort = !order.isSortNone() && !order.isOriginal();
      boolean asc = order.isAsc();
      int n = 0;
      XNamedGroupInfo namedGroup = order.getRealNamedGroupInfo();
      XSourceInfo asource = null;
      XSourceInfo asource2 = null;

      if(topn != null && !topn.isBlank() && aggrs != null &&
         topn.getTopNSummaryCol() < aggrs.length)
      {
         needsort = true;
         sorton = aggrs[topn.getTopNSummaryCol()];

         if(sorton != null && table != null) {
            asource = source;
         }

         n = topn.getTopN();
         asc = topn.isTopNReverse();

         if(topn.isOthers()) {
            // the space is needed to distinguish Others of topn from named group
            String others = Catalog.getCatalog().getString("Others") + " ";
            opt += "remainder=" + others + ",";
            opt += "sortotherslast=" + sortOthersLast + ",";
         }
      }

      if(order.isSortByVal() && aggrs != null) {
         needsort = true;

         if(asc != order.isAsc()) {
            asc = !asc;
            n = -n;
         }

         int sortByCol = order.getSortByCol();
         sorton2 = sortByCol < aggrs.length && sortByCol >= 0 ? aggrs[sortByCol] : null;

         if(sorton2 != null && table != null) {
            asource2 = source;
         }
      }

      if(needsort) {
         if(order.isSpecific() && order.getManualOrder() != null &&
            !order.getManualOrder().isEmpty())
         {
            opt += "manualvalues=" + escapeOptionValue(order);

            // bottom n
            if(asc && n > 0) {
               n = -n;
            }
         }
         else {
            opt += "sort=" + (asc ? "asc" : "desc");

            // not sort by value but exist ranking n, then sort option was used for ranking
            // direction, so need add sort2 to make the sort order.
            if(!order.isSortByVal() && n != 0) {
               opt += ",sort2=" + (order.isAsc() ? "asc" : "desc");
            }
         }
      }
      else {
         opt += "sort=false";
      }

      if(sorton != null && isCalcFields(BindingTool.getFieldName(sorton), table)) {
         sorton = null;
      }

      if(sorton != null) {
         FormulaStorage fstore = parseFormula(sorton);
         opt += ",field=" + name;
         opt += ",sorton=" + fstore.formula + "(" + trimFormula(sorton);

         if(fstore.secondf != null) {
            opt += "," + fstore.secondf;
         }
         else if(fstore.n != null) {
            opt += "," + fstore.n;
         }

         opt += ")";

         if(n != 0) {
            opt += ",maxrows=" + n;
         }
      }

      if(sorton2 != null && isCalcFields(BindingTool.getFieldName(sorton2), table)) {
         sorton2 = null;
      }

      if(sorton2 != null) {
         FormulaStorage fstore = parseFormula(sorton2);

         if(sorton == null) {
            opt += ",field=" + name;
         }

         opt += ",sorton2=" + fstore.formula + "(" + trimFormula(sorton2);

         if(fstore.secondf != null) {
            opt += "," + fstore.secondf;
         }
         else if(fstore.n != null) {
            opt += "," + fstore.n;
         }

         opt += ")";
      }

      boolean timeSeries = false;

      // add date option
      if(isDate(fields[idx])) {
         int option = order == null ? -1 : order.getOption();
         String dexp = DATEOPT.get(option);

         if(dexp != null) {
            opt += "," + dexp;
            String interval = getInterval(order);

            if(interval != null) {
               opt = opt + "," + interval;

               if(cell != null && cell.isTimeSeries() && (topn == null || topn.isBlank()) &&
                  option != DateRangeRef.NONE_INTERVAL)
               {
                  opt += ",timeseries=true";
                  timeSeries = true;
               }
            }
         }
      }

      // time series has higher precedence on gui
      boolean isNamedGroup = !timeSeries && namedGroup != null && isSimpleNamedGroup(namedGroup);

      if(isNamedGroup) {
         exp = "mapList(";
         opt = createMapOptions(opt, order, namedGroup);

         if(order.getOthers() == OrderInfo.GROUP_OTHERS) {
            if(opt.length() > 0) {
               opt += ",";
            }

            opt += "sortotherslast=" + sortOthersLast;
         }
      }
      else {
         exp = "toList(";
      }

      boolean sortByVal = !(sorton == null && sorton2 == null);

      if(info == null) {
         // construct data
         var = var == null ? getExpressionVar(cell, source) : var;
         String data = var + "[\'" + (!sortByVal ? name : "*");

         if(idx > 0) {
            data += createGroupCond(fields, cellnames, 0, idx, false);
         }

         data += "\']";
         exp = exp + data;
      }
      else {
         for(int i = 0; i < info.getSourceCount(); i++) {
            if(i <= info.getSourceCount() - 2) {
               exp += info.isUnion() ? "union(" : "intersect(";
            }
         }

         String field = "";

         for(int i = 0; i < info.getSourceCount(); i++) {
            var = "data";
            SourceAttr jsource = info.getSource(i);
            Field jfield = info.getField(i);
            name = BindingTool.getPureField(jfield).getName();
            SourceAttr sattr = (SourceAttr) source;
            fixGroupField(fields, sattr, list, jsource, locs, point);

            if(!jsource.equals(sattr)) {
               for(int j = 0; j < sattr.getJoinSourceCount(); j++) {
                  if(sattr.getJoinSource(j).equals(jsource)) {
                     var = String.format(var + "%d", j + 1);
                     break;
                  }
               }
            }

            String data = var + "[\'" + (!sortByVal ? name : "*");

            if(idx > 0) {
               data += createGroupCond(fields, cellnames, 0, idx, false);
            }

            name = BindingTool.getPureField(fields[idx]).getName();
            data += "\']";
            exp += data;

            if(i == 0) {
               field = name;
               group = name;
            }

            if(i > 0) {
               if(sortByVal) {
                  if("".equals(field)) {
                     field = group;
                  }

                  field += ":" + name;
                  String options = "'fields=" + field;

                  if(jsource.equals(asource) || jsource.equals(asource2)) {
                      options += ",exchange=true'";
                  }
                  else {
                     options += "'";
                  }

                  exp += "," + options;
               }

               exp += ")";
               field = "";
            }

            if(i < info.getSourceCount() - 1) {
               exp += ",";
            }
         }
      }

      if(isNamedGroup) {
         exp = exp + "," + createMapListMap(namedGroup);
      }

      if(opt.length() > 0) {
         exp += ",'" + opt + "'";
      }

      exp += ")";

      return exp;
   }

   private static String escapeOptionValue(OrderInfo order) {
      return Tool.escapeJavascript(Tool.encodeNL(getManualValuesString(order))).replace(",", "\\\\,");
   }

   /**
    * Return aggregates list for topn and sort.
    */
   private static CalcAggregate[] getTopAggregetes(CalcAggregate[] aggrs, FormulaTable table) {
      if(aggrs == null || aggrs.length == 0) {
         return new CalcAggregate[0];
      }

      List<CalcAggregate> list = Arrays.stream(aggrs).filter(aggr -> {
         return !isCalcFields(BindingTool.getFieldName(aggr), table);
      })
      .collect(Collectors.toList());

      return list.toArray(new CalcAggregate[list.size()]);
   }

   private static boolean isCalcFields(String value, FormulaTable table) {
      if(!(table instanceof TableDataVSAssembly)) {
         return false;
      }

      TableDataVSAssembly assembly = (TableDataVSAssembly) table;
      String tname = assembly.getTableName();
      Viewsheet vs = assembly.getViewsheet();

      if(vs == null) {
         return false;
      }

      return vs.getCalcField(tname, value) == null ? false : true;
   }

   /**
    * Check is date group.
    */
   protected static boolean isDate(CalcGroup group) {
      if(group instanceof GroupField || group instanceof GroupRef) {
         return group.isDate();
      }

      DataRef ref = group.getDataRef();

      while(ref instanceof DataRefWrapper) {
         if(ref instanceof DateRangeRef) {
            return true;
         }

         ref = ((DataRefWrapper) ref).getDataRef();
      }

      return group.isDate();
   }

   /**
    * Get group condition.
    * @param cond condition (following ? in the qualifier).
    */
   private static String getGroupCond(String exp, StringBuffer cond,
                                      CalcGroup gfield, String cname)
   {
      if(gfield == null) {
         return "";
      }

      DataRef f = BindingTool.getPureField(gfield);
      String name = BindingTool.getFieldName(f);
      TopNInfo topn = gfield.getTopN();
      String expQualifier = "";
      String gval = createScalarGroupExpression(gfield); // group value expression

      if(topn != null && !topn.isBlank() && topn.isOthers() ||
         gval.startsWith("toList(") || gval.startsWith("mapList("))
      {
         expQualifier = "=";
      }
      else {
         gval = escapeColName(name);
      }

      cname = Tool.escapeJavascript(cname);

      if(topn != null && !topn.isBlank() && topn.isOthers()) {
         if(cond.length() > 0) {
            cond.append(" && ");
         }

         // fix bug1287458669377, add a blank string to separate top n others
         // from named group others
         String others = Catalog.getCatalog().getString("Others") + " ";
         cond.append("(" + wrap(gfield, "$" + cname) + " == \"" + others +
                     "\" ? !inArray($" + cname + "[\"*\"], " + gval +
                     ") : " + wrap(gfield, gval) + " == " +
                     wrap(gfield, "$" + cname + "[\".\"]") + ")");
      }
      else {
         if(gfield instanceof GroupRef) {
            gval = Tool.replaceAll(gval, ":", LayoutTool.SCRIPT_ESCAPED_COLON);
         }

         return expQualifier + gval + ":$" + cname;
      }

      return "";
   }

   /**
    * Create cell binding.
    */
   private static TableCellBinding createCellBinding(int type, int stype,
                                                     int exp, String value) {
      TableCellBinding binding = new TableCellBinding(type, value);
      binding.setBType(stype);
      binding.setExpansion(exp);
      return binding;
   }

   /**
    * Wrap the value, cause the java script can not compare data except
    * Number, String, Boolean and Scriptable.
    */
   private static String wrap(CalcGroup field, String value) {
      DataRef bfield = field.getDataRef();

      if(isDate(field)) {
         return "String(" + value + ")";
      }

      return value;
   }

   /**
    * Create a group expression to be used for condition in a qualifier, e.g.
    * toList(orderdate, "date=year")
    */
   private static String createScalarGroupExpression(CalcGroup gfield) {
      DataRef f = BindingTool.getPureField(gfield);
      String name = BindingTool.getFieldName(f);
      OrderInfo order = gfield.getOrderInfo();
      XNamedGroupInfo namedGroup = order.getRealNamedGroupInfo();
      TopNInfo topn = gfield.getTopN();
      // name used in expression (don't use name directly in case it's a
      // builtin name, e.g. Date)
      String expname = "rowValue[\"" + Tool.escapeJavascript(name) + "\"]";
      String opt = "";
      String gval = expname;
      boolean timeSeries = false;

      if(isDate(gfield)) {
         OrderInfo info = gfield.getOrderInfo();
         int option = info == null ? -1 : info.getOption();
         String dexp = DATEOPT.get(option);
         String interval = getInterval(info);

         if(dexp != null) {
            opt += dexp;

            if(interval != null) {
               opt += "," + interval;
            }
         }

         if(gfield.isTimeSeries() && (topn == null || topn.isBlank()) &&
            option != DateRangeRef.NONE_INTERVAL)
         {
            opt += ", timeseries=true";
            timeSeries = true;
         }
      }

      // time series has higher precedence on gui
      if(!timeSeries && namedGroup != null && isSimpleNamedGroup(namedGroup)) {
         String spec = createMapListMap(namedGroup);
         gval = "mapList(" + expname + ", " + spec + ", \"" +
            createMapOptions(opt, order, namedGroup) + "\")";
      }
      else if(isDate(gfield)) {
         gval = "toList(" + expname;

         if(opt.length() > 0) {
            gval += ", \"" + opt + "\"";
         }

         gval += ")";
      }

      return gval;
   }

   /**
    * Append condition to restricted the rows to the topN values in a group.
    */
   private static void addSubTopNCond(StringBuffer cond, CalcGroup[] gfields,
                                      String[] cnames)
   {
      boolean topn = false;

      for(int i = 0; i < gfields.length; i++) {
         if(gfields[i] == null) {
            continue;
         }

         TopNInfo tinfo = gfields[i].getTopN();

         if(tinfo != null && !tinfo.isBlank() && !tinfo.isOthers()) {
            topn = true;
            break;
         }
      }

      if(topn) {
         if(cond.length() > 0) {
            cond.append(" && ");
         }

    // the space is needed to distinguish Others of topn from named group
         String others = Catalog.getCatalog().getString("Others") + " ";
         final LinkedHashSet<String> uniqueGroups = new LinkedHashSet<>();

         for(int i = 0; i < gfields.length; i++) {
            if(gfields[i] == null) {
               continue;
            }

            String gexpr = createScalarGroupExpression(gfields[i]);
            uniqueGroups.add("\"" + cnames[i] + "\"," + gexpr);
         }

         cond.append("inGroups([");
         cond.append(uniqueGroups.stream().collect(Collectors.joining(",")));
         cond.append("], \"" + others + "\")");
      }
   }

   /**
    * Create group expression for calc.
    */
   private static String createGroupExpression(CalcGroup[] fields,
                                               String[] cellnames, int idx,
                                               CalcAggregate[] aggrs,
                                               TableCellBinding cell,
                                               XSourceInfo source,
                                               List<TableCellBinding> list,
                                               FormulaTable table,
                                               List<Point> locs, Point point,
                                               boolean sortOthersLast)
   {
      if(fields[idx] == null) {
         return "";
      }

      JoinInfo info = null;

      return createGroupExpression(fields, cellnames, idx, aggrs, null, info,
         source, cell, list, table, locs, point, sortOthersLast);
   }

   private static void fixGroupField(CalcGroup[] fields,
                                     SourceAttr source,
                                     List<TableCellBinding> list,
                                     SourceAttr jsource,
                                     List<Point> locs, Point point)
   {
      for(int i = 0; i < fields.length; i++) {
         if(fields[i] == null || jsource == null) {
            continue;
         }

         TableCellBinding gcell = list.get(i);
         JoinInfo info = getCommonDimension(gcell, source);
         GroupField field = (GroupField) fields[i];

         if(info == null) {
            if(field.getSource(true) != null &&
               !jsource.getSource().equals(field.getSource(true)))
            {
               LOG.warn("Group cell[" + locs.get(i).x + ", " +
                  locs.get(i).y + "] " + "is not suitable for cell[" +
                  point.x + ", " + point.y + "], " +
                  "please make sure they are from the same source or create a " +
                  "common group for the group field between the two sources.");
            }

            continue;
         }

         boolean found = false;

         for(int j = 0; j < info.getSourceCount(); j++) {
            if(info.getSource(j).equals(jsource)) {
               if(field.getName().equals(info.getField(j).getName())) {
                  found = true;
                  break;
               }

               GroupField group = new GroupField(info.getField(j));
               group.setOrderInfo(field.getOrderInfo());
               group.setTopN(field.getTopN());
               fields[i] = group;
               found = true;
               break;
            }
         }

         if(!found) {
            LOG.warn("Can not find the Cell[" +
               point.x + ", " + point.y + "]" +
               "'s source in common group, please add common group.");
         }
      }
   }

   /**
    * Create named group mapping array spec for mapList().
    */
   @SuppressWarnings("deprecation")
   private static String createMapListMap(XNamedGroupInfo info) {
      String[] groups = info.getGroups();
      String spec = "";

      for(String group : groups) {
         ConditionList clist = info.getGroupCondition(group);

         if(clist == null || clist.getSize() == 0) {
            continue;
         }

         if(spec.length() > 0) {
            spec += ",";
         }

         List<Object> values = new ArrayList<>();

         for(int i = 0; i < clist.getSize(); i += 2) {
            Condition cond = clist.getCondition(i);

            if(cond == null) {
               continue;
            }

            // use getValue to convert value to correct data type
            for(int j = 0; j < cond.getValueCount(); j++) {
               values.add(cond.getValue(j));
            }
         }

         spec += "[";

         for(int i = 0; i < values.size(); i++) {
            if(i > 0) {
               spec += ",";
            }

            String value = quoteValue(values.get(i));
            value = Tool.replaceAll(value,":", LayoutTool.SCRIPT_ESCAPED_COLON);
            spec += value;
         }

         String groupStr = Tool.escapeJavascript(group);
         groupStr = Tool.replaceAll(groupStr,":", LayoutTool.SCRIPT_ESCAPED_COLON);
         spec += "],\"" + Tool.escapeJavascript(groupStr) + "\"";
      }

      return "[" + spec + "]";
   }

   /**
    * Create the options for mapList.
    */
   private static String createMapOptions(String opt, OrderInfo order,
                                          XNamedGroupInfo ninfo) {
      if(isLeaveOthers(order, ninfo)) {
         if(opt.length() > 0) {
            opt += ",";
         }

         opt += "others=leaveothers";
      }

      return opt;
   }

   private static boolean isLeaveOthers(OrderInfo order, XNamedGroupInfo ninfo) {
      int others = order.getOthers();

      if(ninfo instanceof AssetNamedGroupInfo) {
         others = ((AssetNamedGroupInfo) ninfo).getOthers();
      }
      else if(ninfo instanceof NamedGroupInfo) {
         others = ((NamedGroupInfo) ninfo).getOthers();
      }

      return others == OrderInfo.LEAVE_OTHERS;
   }

   /**
    * Convert to javascript value format.
    */
   private static String quoteValue(Object val) {
      // for number and boolean object, convert to same data type for
      // java script by use function Number(val) and Boolean(val)
      if(val instanceof Number) {
         return "Number(" + val.toString() + ")";
      }
      if(val instanceof Boolean) {
         return "Boolean(" + val + ")";
      }
      else if(val instanceof Date) {
         return "new Date(" + ((Date) val).getTime() + ")";
      }
      else if(val != null && val.getClass().isArray()) {
         StringBuilder builder = new StringBuilder();

         for(int i = 0; i < Array.getLength(val); i++) {
            String element = quoteValue(Array.get(val, i));
            builder.append(i > 0 ? "," + element : element);
         }

         return builder.toString();
      }
      else if(val instanceof UserVariable) {
         val = PARAM_PREFIX + ((UserVariable) val).getName();
         return Tool.escapeJavascript(val + "");
      }

      return '"' + Tool.escapeJavascript(val + "") + '"';
   }

   /**
    * Create the design time table data paths to runtime time table data paths
    * mapping from table layout.
    * @param table the specified table lens.
    * @param layout the specified table layout.
    * @param type the specified type defined in <tt>TableElementDef</tt>.
    * @return the table data path mapping.
    */
   private static Map<TableDataPath, TableDataPath> getTableDataPathsMapping(
      TableLens table, TableLayout layout, String type)
   {
      Map<TableDataPath, TableDataPath> d2rmap = new Hashtable<>();
      TableDataDescriptor desc = table.getDescriptor();
      table.moreRows(TableLens.EOT);
      int bltype = layout.getBackLayoutType();

      for(int i = 0; i < table.getRowCount(); i++) {
         TableDataPath rpath = desc.getRowDataPath(i);

         if(rpath != null && isSupportedTableDataPath(table, rpath, type) &&
            d2rmap.containsKey(rpath) &&
            (bltype != 2 || i != table.getHeaderRowCount() - 1))
         {
            continue;
         }

         Region region = layout.getRegion(rpath);

         if(region == null) {
            continue;
         }

         int r = 0;

         if(bltype == 2 && i == table.getHeaderRowCount() - 1) {
            r = i;
         }
         else {
            r = Math.max(0, rpath.getIndex());
         }

         d2rmap.put(region.getRowDataPath(r), rpath);

         for(int j = 0; j < table.getColCount(); j++) {
            rpath = desc.getCellDataPath(i, j);

            if(rpath != null && isSupportedTableDataPath(table, rpath, type) &&
               d2rmap.containsKey(rpath))
            {
               continue;
            }

            d2rmap.put(region.getCellDataPath(r, j), rpath);
         }
      }

      for(int i = 0; i < table.getColCount(); i++) {
         TableDataPath rpath = desc.getColDataPath(i);

         if(rpath != null && isSupportedTableDataPath(table, rpath, type) &&
            d2rmap.containsKey(rpath))
         {
            continue;
         }

         d2rmap.put(layout.getColDataPath(i), rpath);
      }

      return d2rmap;
   }

   /**
    * Check if a table data path is supported of a type.
    * @param path the specified table data path.
    * @param type the specified type, namely format, highlight, hyperlink, etc.
    * @return true if yes, false otherwise.
    */
   private static boolean isSupportedTableDataPath(TableLens table, TableDataPath path, String type) {
      if(TableElementDef.TABLE_FORMAT.equals(type)) {
         return TableFormatAttr.supportsFormat(table, path);
      }
      else if(TableElementDef.TABLE_HIGHLIGHT.equals(type)) {
         return TableHighlightAttr.supportsHighlight(table, path);
      }
      else if(TableElementDef.TABLE_HYPERLINK.equals(type)) {
         return TableHyperlinkAttr.supportsHyperlink(table, path);
      }
      else {
         return true;
      }
   }

   private static class CellHolder {
      public CellHolder(int r, int c, TableCellBinding cell) {
         this.row = r;
         this.col = c;
         this.cell = cell;
      }

      public String toString() {
         return "[" + row + "," + col + "]";
      }

      private int row, col;
      private TableCellBinding cell;
   }

   /**
    * Create name map.
    */
   private static Map<String, CellHolder> createNameMap(TableLayout layout) {
      Map<String, CellHolder> nmap = new HashMap<>();

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region region = layout.getRegion(i);

         for(int r = 0; r < region.getRowCount(); r++) {
            int lr = layout.convertToGlobalRow(region, r);

            for(int c = 0; c < region.getColCount(); c++) {
               Rectangle span = region.findSpan(r, c);

               // ignore invisible cell
               if(span == null || span.x == 0 && span.y == 0) {
                  TableCellBinding binding = (TableCellBinding) region.getCellBinding(r, c);

                  if(binding != null && !binding.isEmpty()) {
                     String name = layout.getRuntimeCellName(binding);

                     if(name == null) {
                        name = "[" + c + ", " + lr + "]";
                     }

                     nmap.put(name, new CellHolder(lr, c, binding));
                  }
               }
            }
         }
      }

      return nmap;
   }

   /**
    * Create name map.
    */
   private static Map<String, Point> createPositionMap(TableLayout layout) {
      Map<String, Point> nmap = new HashMap<>();

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region region = layout.getRegion(i);

         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               Rectangle span = region.findSpan(r, c);

               // ignore invisible cell
               if(span == null || span.x == 0 && span.y == 0) {
                  TableCellBinding binding =
                     (TableCellBinding) region.getCellBinding(r, c);

                  if(binding != null && !binding.isEmpty()) {
                     int lr = layout.convertToGlobalRow(region, r);
                     nmap.put(buildUniqueName(binding), new Point(c, lr));
                  }
               }
            }
         }
      }

      return nmap;
   }

   /**
    * Convert a row in region to row in layout.
    */
   public static int convertToGlobalRow(TableLayout layout,
                                         BaseLayout.Region region, int rr,
                                         boolean ignoreInvisible) {
      int r = 0;
      BaseLayout.Region[] regions = layout.getRegions();

      for(int i = 0; i < regions.length; i++) {
         if(region == regions[i]) {
            break;
         }

         if(!ignoreInvisible || regions[i].isVisible()) {
            r += regions[i].getRowCount();
         }
      }

      return r + rr;
   }

   /**
    * Create the expression script for a calc cell column binding.
    */
   private static String createCalcBindingExpression(FormulaTable table,
      Map<String, CellHolder> n2c, TableLayout layout,
      TableCellBinding cell, Map<String, Point> c2p, Point point,
      boolean crossTabSupported, boolean sortOthersLast)
   {
      if(cell == null || cell.getType() != CellBinding.BIND_COLUMN || cell.isEmpty()) {
         return "";
      }

      int position = -1;
      List<CellHolder> holders = new ArrayList<>();
      List<Point> locs = new ArrayList<>();
      Set<String> processed = new HashSet<>();
      buildGroupList(layout, cell.getRowGroup(), holders, n2c, c2p, locs, processed, true, table);

      if(cell.getBType() == TableCellBinding.SUMMARY) {
         position = holders.size();
      }

      processed = new HashSet<>();
      buildGroupList(layout, cell.getColGroup(), holders, n2c, c2p, locs, processed, false, table);
      position = holders.size() - position;
      List<TableCellBinding> list = new ArrayList<>();

      for(CellHolder holder : holders) {
         list.add(holder.cell);
      }

      // If cell is grouped, then move it to the end of the list.
      if(cell.getBType() == TableCellBinding.GROUP) {
         for(int i = 0; i < list.size(); i++) {
            if(list.get(i) == cell) {
               list.remove(i);
               locs.remove(i);
               break;
            }
         }

         list.add(cell);
         locs.add(c2p.get(buildUniqueName(cell)));
      }

      CalcGroup[] groups = new CalcGroup[list.size()];
      String[] gnames = new String[list.size()];

      for(int i = 0; i < groups.length; i++) {
         groups[i] = createGroupField(list.get(i), table);
         gnames[i] = layout.getRuntimeCellName(list.get(i));
      }

      XSourceInfo source = table.getXSourceInfo();
      String var = getExpressionVar(cell, source);
      boolean joined = (source instanceof SourceAttr) &&
         ((SourceAttr) source).getJoinSourceCount() > 0;
      String exp = null;

      if(crossTabSupported) {
         exp = createCrosstabCalcExpression(list, groups, gnames, cell, var,
            cell.getBType() == TableCellBinding.GROUP, sortOthersLast);
      }
      else if(cell.getBType() == TableCellBinding.DETAIL) {
         exp = createDetailExpression(groups, gnames, createDetailField(cell, table), var);

         // use noEmpty to avoid a row disappearing when there is not avoid
         // this should only be necessary when there is multi-source join and
         // the list is qualified by a condition, e.g. state:$state
         if(joined && gnames.length > 0) {
            exp = "noEmpty(" + exp + ")";
         }
      }
      else if(cell.getBType() == TableCellBinding.GROUP) {
         CalcAggregate[] afields = getCalcAggregateFields(table);
         exp = createGroupExpression(groups, gnames, groups.length - 1,
            afields, cell, source, list, table, locs, point, sortOthersLast);

         // see above
         if(joined && gnames.length > 1) {
            exp = "noEmpty(" + exp + ")";
         }
      }
      else {
         CalcAggregate agg = createAggregateField(cell, table);
         exp = createAggExpression(groups, gnames, groups.length, agg, var, position, cell);
      }

      return exp;
   }

   private static String escapeColName(String name) {
      return name.chars().mapToObj(cc -> {
            if(isSpecialMarker((char) cc)) {
               return "\\\\" + (char) cc;
            }
            else if(cc == '\'') {
               return "\\" + (char) cc;
            }

            return "" + (char) cc;
      }).reduce("", (s1, s2) -> s1 + s2);
   }

   private static boolean isSpecialMarker(char c) {
      switch(c) {
      case '?': case '@': case '{': case '}': case ',': case '=': case ';': case '&':
         return true;
      default:
         return false;
      }
   }

   /**
    * Create calc for crosstab supported expression.
    */
   private static String createCrosstabCalcExpression(List<TableCellBinding> list,
                                                      CalcGroup[] groups,
                                                      String[] gnames,
                                                      TableCellBinding bind,
                                                      String var,
                                                      boolean isGroup,
                                                      boolean sortOthersLast)
   {
      String exp = "";

      if(isGroup) {
         exp += "toList(";
      }
      else {
         exp += "none(";
      }

      exp += var + "['" + escapeColName(bind.getValue());

      int len = isGroup ? list.size() - 1 : list.size();

      if(len > 0) {
         exp += "@";
      }

      CalcGroup group = groups.length > 0 ? groups[groups.length - 1] : null;

      for(int i = 0; i < len; i++) {
         if(i > 0) {
            exp += ";";
         }

         String value = escapeColName(list.get(i).getValue());
         value = Tool.replaceAll(value, ":", LayoutTool.SCRIPT_ESCAPED_COLON);
         exp += value + ":$" + escapeColName(Tool.escapeJavascript(gnames[i]));
      }

      exp += "']";

      // no order, order has been processed by crosstab
      if(isGroup && group != null) {
         String dexp = null;

         if(bind.isTimeSeries() && bind.getTopN(true).isBlank()) {
            int option = bind.getOrderInfo(true).getOption();

            if(option != DateRangeRef.NONE_INTERVAL) {
               dexp = DATEOPT.get(option);
            }
         }

         if(dexp != null) {
            exp += ",'sort=false,timeseries=true," + dexp + "'";
         }
         else {
            exp += ",'sort=false";

            final OrderInfo orderInfo = bind.getOrderInfo(false);

            if(orderInfo != null && orderInfo.isSpecific() && orderInfo.getManualOrder() != null) {
               exp += ",manualvalues=" + escapeOptionValue(orderInfo);

               TopNInfo topn = bind.getTopN(true);

               if(topn != null && !topn.isBlank() && sortOthersLast) {
                  String others = Catalog.getCatalog().getString("Others");
                  exp += ", remainder=" + others + ", sortOthersLast=" + sortOthersLast + "'";
               }
               else {
                  exp += "'";
               }
            }
            else {
               exp += "'";
            }
         }
      }

      exp += ")";

      return exp;
   }

   private static String getManualValuesString(OrderInfo orderInfo) {
      StringBuilder buffer = new StringBuilder();
      List<String> values = orderInfo.getManualOrder();

      if(values == null || values.size() == 0) {
         return null;
      }

      for(int i = 0; i < values.size(); i++) {
         String value = values.get(i);
         value = Tool.replaceAll(value, ":", LayoutTool.SCRIPT_ESCAPED_COLON);
         buffer.append(value == null || "".equals(value) ? Tool.FAKE_NULL : value);

         if(i != values.size() - 1) {
            buffer.append(";");
         }
      }

      return buffer.toString();
   }

   /**
    * Build the unique string for the cell binding.
    */
   private static String buildUniqueName(TableCellBinding cell) {
      if(cell == null) {
         return null;
      }

      StringBuilder buffer = new StringBuilder();
      buffer.append(cell.getValue());

      if(cell.getSource() != null) {
         buffer.append("^");
         buffer.append(cell.getSource());
      }

      if(cell.getSourcePrefix() != null) {
         buffer.append("^");
         buffer.append(cell.getSourcePrefix());
      }

      if(cell.getSourceType() >= 0) {
         buffer.append("^");
         buffer.append(cell.getSourceType());
      }

      return buffer.toString();
   }

   /**
    * Add all parents as GroupField to the list.
    * @param n2c cell name to CellBinding map.
    */
   private static void buildGroupList(TableLayout layout, String parent,
                                      List<CellHolder> list, Map<String, CellHolder> n2c,
                                      Map<String, Point> c2p, List<Point> locs,
                                      Set<String> processed, boolean row, FormulaTable table)
   {
      if(parent == null || processed.contains(parent)) {
         return;
      }

      processed.add(parent);
      CellHolder holder = n2c.get(parent);
      TableCellBinding cell = holder == null ? null : holder.cell;

      if(cell == null || cell.isEmpty()) {
         return;
      }

      if(cell.getExpansion() == TableCellBinding.EXPAND_NONE) {
         return;
      }

      if(cell.getType() == CellBinding.BIND_COLUMN && cell.getBType() == TableCellBinding.GROUP) {
         // this is explicit group, it's ok
      }
      // 1: text not as a valid group
      // 2: column binding, but not group, not treat as group
      // 3: no cell name, not treat as group
      else if(cell.getType() != CellBinding.BIND_FORMULA ||
              layout.getRuntimeCellName(cell) == null)
      {
         return;
      }
      // if expression is mixed with column binding, using expression as a group condition
      // with cell binding will generate invalid condition clause. (55537)
      // since this method is only used for generating script for column binding,
      // ignore the expression here.
      // cell converted to expression from a column binding will still be treated as a
      // column binding if the cell name is a column in the data table. (56387)
      else if(table != null && !isTableColumn(cell, table)) {
         return;
      }

      list.add(0, holder);
      locs.add(0, c2p.get(buildUniqueName(cell)));

      if(row) {
         buildGroupList(layout, cell.getRowGroup(), list, n2c, c2p, locs, processed, row, table);
      }
      else {
         buildGroupList(layout, cell.getColGroup(), list, n2c, c2p, locs, processed, row, table);
      }
   }

   private static boolean isTableColumn(TableCellBinding gcell, FormulaTable table) {
      if(gcell.getType() != CellBinding.BIND_FORMULA) {
         return true;
      }

      String cname = gcell.getName();
      return VSLayoutTool.findAttribute(table, gcell, cname) != null;
   }

   protected static String getExpressionVar(TableCellBinding cell, XSourceInfo source) {
      String var = "data";

      if(source instanceof SourceAttr) {
         SourceAttr sattr = (SourceAttr) source;

         if(cell.getSource() != null && sattr != null) {
            for(int i = 0; i < sattr.getJoinSourceCount(); i++) {
               SourceAttr src = sattr.getJoinSource(i);

               if(checkSource(src, cell)) {
                  var += Integer.toString(i + 1);
                  break;
               }
            }
         }

         return var;
      }
      else {
         return var;
      }
   }

   /**
    * Get table cell binding structure type.
    */
   protected static int getBType(BaseLayout.Region region) {
      TableDataPath path = region == null ? null : region.getPath();
      int type = path == null ? -1 : path.getType();

      switch(type) {
      case DETAIL:
         return TableCellBinding.DETAIL;
      case G_HEADER:
         return TableCellBinding.GROUP;
      case G_FOOTER:
         return TableCellBinding.SUMMARY;
      default:
         return -1;
      }
   }

   public static String createDetailExpression(CalcGroup[] fields,
                                               String[] cellnames,
                                               DataRef field, String var)
   {
      String name = BindingTool.getFieldName(field);
      String exp = var + "[\'" + escapeColName(name);
      exp += createGroupCond(fields, cellnames, 0, fields.length, false);
      exp += "\']";
      return exp;
   }

   /**
    * Create a GroupField that matches the cellbinding.
    */
   protected static CalcGroup createGroupField(TableCellBinding gcell,
                                               FormulaTable table)
   {
      String cname = gcell.getValue();
      OrderInfo orderInfo0 = gcell.getOrderInfo(true);
      int dateOp = orderInfo0.getOption();
      double interval = 1;
      String istr = orderInfo0.getInterval() + "";
      int sort = OrderInfo.SORT_ASC;
      String sstr = orderInfo0.getOrder() + "";
      OrderInfo orderInfo = gcell.getOrderInfo(false);
      TopNInfo topN = gcell.getTopN(false);
      XSourceInfo sattr = table.getXSourceInfo();

      // @davidd, For backwards compatibility before v11.1, interpret the
      // properties and formula looking for encoded OrderInfo data.
      // For v11.1 and beyond, OrderInfo and TopN are stored explicitly in
      // TableCellBinding.
      if(gcell.getType() == CellBinding.BIND_FORMULA) {
         orderInfo = orderInfo == null ? new OrderInfo() : orderInfo;
         String var = getExpressionVar(gcell, sattr);
         Map<String, String> options = getOptions(cname, var + "[");

         if(options.containsKey("dateOp")) {
            String dstr = options.get("dateOp");

            // find date option
            for(Integer op : DATEOPT.keySet()) {
               if(dstr.equals(DATEOPT.get(op))) {
                  dateOp = op;
                  break;
               }
            }
         }

         if(options.containsKey("interval")) {
            istr = options.get("interval");
         }

         if(options.containsKey("sort")) {
            sstr = options.get("sort");
         }

         try {
            interval = Double.parseDouble(istr);
         }
         catch(Exception ex) {
            // ignore it
         }

         try {
            sort = Integer.parseInt(sstr);
         }
         catch(Exception ex) {
            // ignore it
         }

         cname = getGroupName(cname, var);
         orderInfo.setInterval(interval, dateOp);
         orderInfo.setOrder(sort);
      }

      if(cname == null || cname.length() == 0) {
         return null;
      }

      DataRef ref = VSLayoutTool.findAttribute(table, gcell, cname);

      if(ref == null) {
         ref = new AttributeRef(cname);
      }

      CalcGroup group = new GroupRef(ref);

      group.setSource(gcell.getSource());
      group.setSourcePrefix(gcell.getSourcePrefix());
      group.setSourceType(gcell.getSourceType());
      group.setOrderInfo(orderInfo);
      group.setTopN(topN);
      group.setTimeSeries(gcell.isTimeSeries());

      return group;
   }

   private static String getGroupName(String cname, String var) {
      // string literal is double escaped in toList() so we need to convert to single escape
      cname = cname.replaceAll("\\\\\\\\", "\\\\");
      cname = NamedCellRange.encodeEscaped(cname);
      int idx1 = cname.indexOf(var + "[");

      if(idx1 >= 0) {
         char quote = cname.charAt(idx1 + var.length() + 1);
         cname = cname.substring(idx1 + 2 + var.length()); // skip quote

         // find field, date['*@ or date['col@
         if(cname.startsWith("*")) {
            int idx = cname.indexOf("field=");

            if(idx < 0) {
               return null;
            }

            cname = cname.substring(idx + 6);
         }

         int idx;

         for(idx = 0; idx < cname.length(); idx++) {
            char c = cname.charAt(idx);

            // vs side, logic model, name like:
            // toList(data['Customer:State'],'sort=asc')
            // @by stephenwebster, For Bug #3142
            // Support space and number symbol in column name
            if(c == quote || isSpecialMarker(c)) {
               break;
            }
         }

         cname = cname.substring(0, idx);
      }

      return NamedCellRange.decodeEscaped(cname);
   }

   /**
    * Get all Aggregate fields from a calc table
    */
   public static CalcAggregate[] getCalcAggregateFields(FormulaTable table) {
      List<TableCellBinding> cells =
         getTableCellBindings(table, TableCellBinding.SUMMARY);
      CalcAggregate[] afields = new CalcAggregate[cells.size()];

      for(int i = 0; i < cells.size(); i++) {
         afields[i] = createAggregateField(cells.get(i), table);
      }

      return afields;
   }

   /**
    * Get options from formula, such as date, internal and sort.
    */
   private static Map<String, String> getOptions(String formula, String exp) {
      // toList(data['orderdateg=toList(orderdate,
      // "date=year,interval=1.0"):$orderdate'],'sort=asc,date=month')
      Map<String, String> options = new HashMap<>();
      int idx = formula.lastIndexOf(exp);
      int end = formula.lastIndexOf("]");

      if(idx < 0 || end < 0) {
         return options;
      }

      String opt = formula.substring(end + 1);

      // date option
      String dateOp = getOption(opt, "date");

      if(dateOp != null) {
         options.put("dateOp", dateOp);
      }

      // rounddate
      dateOp = getOption(opt, "rounddate");

      if(dateOp != null) {
         options.put("dateOp", dateOp);
      }

      // interval
      String interval = getOption(opt, "interval");

      if(interval != null) {
         options.put("interval", interval);
      }

      // sort
      String sort = getOption(opt, "sort");

      if(sort != null) {
         options.put("sort", sort);
      }

      return options;
   }

   /**
    * Get option.
    */
   private static String getOption(String options, String name) {
      // e.g. 'sort=asc,rounddate=year,interval=2.0')
      int idx = options.indexOf(name);

      if(idx < 0) {
         return null;
      }

      String opt = options.substring(idx);
      int end = opt.indexOf(",");

      if(end < 0) {
         end = opt.indexOf("'");
      }

      opt = end >= 0 ? opt.substring(0, end) : opt;
      String[] splits = opt.split("=");

      if(splits.length != 2) {
         return null;
      }

      return splits[1].trim();
   }

   /**
    * Create runtime layout, null, freehand or calc layout.
    */
   private static Map<TableDataPath, TableDataPath> getDesign2RuntimeMap(
      TableLens table, TableLayout layout)
   {
      return getTableDataPathsMapping(table, layout, null);
   }

   /**
    * Create runtime layout, null, freehand or calc layout.
    */
   private static Map<TableDataPath, TableDataPath> createDataPathMapping(
      TableLens toplens, TableLayout layout)
   {
      Map<TableDataPath, TableDataPath> pathmap = new Hashtable<>();
      TableDataDescriptor desc = toplens.getDescriptor();
      toplens.moreRows(TableLens.EOT);
      int bltype = layout.getBackLayoutType();

      for(int i = 0; toplens.moreRows(i); i++) {
         TableDataPath npath = desc.getRowDataPath(i);

         if(pathmap.containsKey(npath) &&
            (bltype != 2 || i != toplens.getHeaderRowCount() - 1))
         {
            continue;
         }

         BaseLayout.Region region = layout.getRegion(npath);

         if(region == null) {
            continue;
         }

         int r = 0;

         if(bltype == 2 && i == toplens.getHeaderRowCount() - 1) {
            r = i;
         }
         else {
            r = Math.max(0, npath.getIndex());
         }

         pathmap.put(npath, region.getRowDataPath(r));

         for(int j = 0; j < toplens.getColCount(); j++) {
            TableDataPath cpath = desc.getCellDataPath(i, j);

            if(pathmap.containsKey(cpath)) {
               continue;
            }

            pathmap.put(cpath, region.getCellDataPath(r, j));
         }
      }

      // vertical region only contains one column
      for(int i = 0; i < toplens.getColCount(); i++) {
         TableDataPath npath = desc.getColDataPath(i);

         if(pathmap.containsKey(npath)) {
            continue;
         }

         pathmap.put(npath, layout.getColDataPath(i));
      }

      return pathmap;
   }

   /**
    * Replace the default groups with actual group names.
    * Default group means any group cell (currently defined as any cell that is
    * named) to the left or above the cell is treated as parent, which has two
    * consequences:<br>
    * 1. The parent groups are added to the condition (@ or ?) clause of the
    * table column expression, e.g. toList(data["col@parent:$parent"])
    * 2. The parent is set as the parent group for expension.
    */
   private static TableLayout applyDefaultGroups(TableLayout layout) {
      // validate the layout row group and col group
      // fix bug1287489354909
      validateLayout(layout);

      String[] defColGroups = new String[layout.getColCount()];
      String[] defRowGroups = new String[layout.getRowCount()];
      layout = (TableLayout) layout.clone();
      Map<String, String> rparent = new HashMap<>();
      Map<String, String> cparent = new HashMap<>();
      createParentMap(layout, rparent, cparent);

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region tregion = layout.getRegion(i);

         for(int rr = 0; rr < tregion.getRowCount(); rr++) {
            int r = layout.convertToGlobalRow(tregion, rr);

            for(int j = 0; j < defColGroups.length; j++) {
               Rectangle span0 = tregion.findSpan(rr, j);

               if(span0 != null && (span0.x != 0 || span0.y != 0)) {
                  continue;
               }

               TableCellBinding bind = (TableCellBinding) tregion.getCellBinding(rr, j);

               if(bind == null || bind.isEmpty()) {
                  continue;
               }

               String cellname = layout.getRuntimeCellName(bind);
               String rowGroup = bind.getRowGroup();
               String colGroup = bind.getColGroup();
               String mergeRowGroup = bind.getMergeRowGroup();
               String mergeColGroup = bind.getMergeColGroup();
               int expand = bind.getExpansion();

               if(TableCellBinding.DEFAULT_GROUP.equals(rowGroup)) {
                  rowGroup = defRowGroups[r];

                  if(rowGroup == null && expand == TableCellBinding.EXPAND_V) {
                     rowGroup = CalcTableLens.ROOT_GROUP;
                  }
               }

               if(TableCellBinding.DEFAULT_GROUP.equals(colGroup)) {
                  colGroup = defColGroups[j];

                  if(colGroup == null && expand == TableCellBinding.EXPAND_H) {
                     colGroup = CalcTableLens.ROOT_GROUP;
                  }
               }

               if(TableCellBinding.DEFAULT_GROUP.equals(mergeRowGroup)) {
                  mergeRowGroup = cellname;

                  if(bind.isMergeCells() && cellname == null) {
                     mergeRowGroup = CalcAttr.getCellID(r, j);
                  }
               }

               if(TableCellBinding.DEFAULT_GROUP.equals(mergeColGroup)) {
                  mergeColGroup = cellname;

                  if(bind.isMergeCells() && cellname == null) {
                     mergeColGroup = CalcAttr.getCellID(r, j);
                  }
               }

               if(isAncestor(cellname, rowGroup, rparent)) {
                  rowGroup = CalcTableLens.ROOT_GROUP;
               }

               if(cellname != null && rowGroup != null) {
                  rparent.put(cellname, rowGroup);
               }

               bind.setRowGroup(rowGroup);

               if(isAncestor(cellname, colGroup, cparent)) {
                  colGroup = CalcTableLens.ROOT_GROUP;
               }

               if(cellname != null && colGroup != null) {
                  cparent.put(cellname, colGroup);
               }

               bind.setColGroup(colGroup);
               bind.setMergeRowGroup(mergeRowGroup);
               bind.setMergeColGroup(mergeColGroup);
               // if cell default can be used as group,
               // we treat it as group
               // fix bug1405479005815
               boolean canAsGroup = bind.getType() == CellBinding.BIND_FORMULA ||
                  bind.getType() == CellBinding.BIND_COLUMN &&
                  bind.getBType() == CellBinding.GROUP;

               if(cellname != null && canAsGroup) {
                  Dimension span = layout.getSpan(r, j);

                  if(expand == TableCellBinding.EXPAND_H) {
                     defColGroups[j] = cellname;

                     if(span != null) {
                        for(int k = 1; k < span.width; k++) {
                           defColGroups[j + k] = cellname;
                        }
                     }
                  }

                  if(expand == TableCellBinding.EXPAND_V) {
                     defRowGroups[r] = cellname;

                     if(span != null) {
                        for(int k = 1; k < span.height; k++) {
                           defRowGroups[r + k] = cellname;
                        }
                     }
                  }
               }
            }
         }
      }

      // revalidate the default group option, if there still any cell is
      // use default group, we change it to null
      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region tregion = layout.getRegion(i);

         for(int r = 0; r < tregion.getRowCount(); r++) {
            for(int c = 0; c < tregion.getColCount(); c++) {
               TableCellBinding bind = (TableCellBinding) tregion.getCellBinding(r, c);

               if(bind == null) {
                  continue;
               }

               String rowGroup = bind.getRowGroup();

               if(TableCellBinding.DEFAULT_GROUP.equals(rowGroup)) {
                  bind.setRowGroup(null);
               }

               String colGroup = bind.getColGroup();

               if(TableCellBinding.DEFAULT_GROUP.equals(colGroup)) {
                  bind.setColGroup(null);
               }

               String mergeRowGroup = bind.getMergeRowGroup();

               if(TableCellBinding.DEFAULT_GROUP.equals(mergeRowGroup)) {
                  bind.setMergeRowGroup(null);
               }

               String mergeColGroup = bind.getMergeColGroup();

               if(TableCellBinding.DEFAULT_GROUP.equals(mergeColGroup)) {
                  bind.setMergeColGroup(null);
               }
            }
         }
      }

      // clear option in spans
      clearInnerBindingOptions(layout);

      return layout;
   }

   /**
    * Clear options in the span.
    */
   private static void clearInnerBindingOptions(TableLayout layout) {
      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            Dimension span = layout.getSpan(r, c);

            if(span != null) {
               for(int ir = r; ir < r + span.height; ir++) {
                  for(int ic = c; ic < c + span.width; ic++) {
                     if(ir == r && ic == c) {
                        continue;
                     }

                     TableCellBinding cell = (TableCellBinding)
                        layout.getCellBinding(ir, ic);

                     // clear the row/column group in the inner cell,
                     // otherwise its span will wrong
                     // fix bug1293087970106
                     if(cell != null) {
                        cell.setRowGroup(null);
                        cell.setColGroup(null);
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Create parent map.
    */
   private static void createParentMap(TableLayout layout,
                                       Map<String, String> rparent,
                                       Map<String, String> cparent)
   {
      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region tregion = layout.getRegion(i);

         for(int rr = 0; rr < tregion.getRowCount(); rr++) {
            for(int j = 0; j < tregion.getColCount(); j++) {
               TableCellBinding bind =
                  (TableCellBinding) tregion.getCellBinding(rr, j);

               if(bind == null) {
                  continue;
               }

               String cellname = layout.getRuntimeCellName(bind);
               String rowGroup = bind.getRowGroup();
               String colGroup = bind.getColGroup();

               if(cellname != null && rowGroup != null) {
                  rparent.put(cellname, rowGroup);
               }

               if(cellname != null && colGroup != null) {
                  cparent.put(cellname, colGroup);
               }
            }
         }
      }
   }


   private static boolean isAncestor(String cell, String parent,
                                     Map<String, String> pmap) {
      if(cell == null || parent == null ||
         CalcTableLens.ROOT_GROUP.equals(parent))
      {
         return false;
      }

      // set self as parent
      if(cell.equals(parent)) {
         return false;
      }

      Set<String> sets = new HashSet<>();
      sets.add(cell);
      sets.add(parent);
      String p = pmap.get(parent);

      while(p != null && !CalcTableLens.ROOT_GROUP.equals(p)) {
         if(p.equals(cell)) {
            return true;
         }

         // find a loop? just break it
         if(sets.contains(p)) {
            break;
         }

         sets.add(p);
         p = pmap.get(p);
      }

      return false;
   }

   /**
    * Validate the layout row/column group option, if the row/column group can
    * not find in layout now, for example, a column is deleted, we reset the
    * cell's row/column to default group.
    */
   private static void validateLayout(TableLayout layout) {
      String[] cnames = layout.getCellNames(true);
      List<String> allnames = Arrays.asList(cnames);

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region region = layout.getRegion(i);

         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               TableCellBinding cell = (TableCellBinding)
                  region.getCellBinding(r, c);

               if(cell == null || cell.isEmpty()) {
                  continue;
               }

               StringBuilder log = new StringBuilder();
               int grow = layout.convertToGlobalRow(region, r);
               String rgrp = cell.getRowGroup();
               String cgrp = cell.getColGroup();
               String mrgrp = cell.getMergeRowGroup();
               String mcgrp = cell.getMergeColGroup();

               if(!isDefaultName(rgrp) && !allnames.contains(rgrp)) {
                  log.append("Row group \"" + rgrp + "\"");
                  rgrp = TableCellBinding.DEFAULT_GROUP;
               }

               if(!isDefaultName(cgrp) && !allnames.contains(cgrp)) {
                  log.append(log.length() > 0 ? ", " : "");
                  log.append("Column group \"" + cgrp + "\"");
                  cgrp = TableCellBinding.DEFAULT_GROUP;
               }

               if(!isDefaultName(mrgrp) && !allnames.contains(mrgrp)) {
                  log.append(log.length() > 0 ? ", " : "");
                  log.append("Merge Row group \"" + mrgrp + "\"");
                  mrgrp = TableCellBinding.DEFAULT_GROUP;
               }

               if(!isDefaultName(mcgrp) && !allnames.contains(mcgrp)) {
                  log.append(log.length() > 0 ? ", " : "");
                  log.append("Merge Column group \"" + mcgrp + "\"");
                  mcgrp = TableCellBinding.DEFAULT_GROUP;
               }

               if(log.length() > 0) {
                  log.append(" not found for cell[" + c + ", " + grow + "]," +
                     " use default group.");
                  LOG.warn(log.toString());
               }

               cell.setRowGroup(rgrp);
               cell.setColGroup(cgrp);
               cell.setMergeRowGroup(mrgrp);
               cell.setMergeColGroup(mcgrp);
            }
         }
      }
   }

   private static boolean isDefaultName(String name) {
      return name == null || "".equals(name) ||
             TableCellBinding.DEFAULT_GROUP.equals(name);
   }

   /**
    * Sync topn in the calc table.
    */
   private static void syncCalcTopN(FormulaTable table) {
      List<TableCellBinding> groupCells =
         getTableCellBindings(table, TableCellBinding.GROUP);
      List<TableCellBinding> aggCells =
         getTableCellBindings(table, TableCellBinding.SUMMARY);

      for(TableCellBinding binding : groupCells) {
         OrderInfo order = binding.getOrderInfo(false);
         syncCalcTopN(binding, order, aggCells);
         syncNamedGroup(order);
      }
   }

   private static void syncCalcTopN(TableCellBinding binding, OrderInfo order,
                                    List<TableCellBinding> aggCells)
   {
      TopNInfo topn = binding.getTopN(false);

      if(topn != null && topn.getTopN() != 0) {
         int scol = topn.getTopNSummaryCol();

         if(aggCells.size() == 0 || scol < 0 || scol > aggCells.size() - 1) {
            topn.setTopN(0);
            topn.setTopNSummaryCol(-1);
            topn.setTopNReverse(false);
         }
      }

      if(order != null) {
         int col = order.getSortByCol();

         if(!order.isSortByValAsc() && !order.isSortByValDesc()) {
            return;
         }

         if(aggCells.size() == 0 || col < 0 || col > aggCells.size() - 1) {
            order.setSortByCol(0);
            order.setOrder(order.isSortByValAsc() ? OrderInfo.SORT_ASC :
               OrderInfo.SORT_DESC);
         }
      }
   }

   public static void syncNamedGroup(OrderInfo order) {
      if(order == null) {
         return;
      }

      XNamedGroupInfo groupInfo = order.getNamedGroupInfo();

      if(groupInfo == null || !(groupInfo instanceof AssetNamedGroupInfo)) {
         return;
      }

      AssetNamedGroupInfo assetGroup = (AssetNamedGroupInfo) groupInfo;
      AssetEntry entry = assetGroup.getEntry();
      NamedGroupAssembly namedGroupAssembly = getNamedGroupAssembly(entry);

      if(namedGroupAssembly == null) {
         return;
      }

      if(order.getCustomerNamedGroupInfo() instanceof AssetNamedGroupInfo) {
         order.setCustomerNamedGroupInfo(namedGroupAssembly.getNamedGroupInfo());
      }
      else if(order.getNamedGroupInfo() instanceof AssetNamedGroupInfo) {
         AssetNamedGroupInfo group = new AssetNamedGroupInfo(entry, namedGroupAssembly);
         order.setNamedGroupInfo(group);
      }
      else {
         order.setNamedGroupInfo(namedGroupAssembly.getNamedGroupInfo());
      }
   }

   public static XNamedGroupInfo getNamedGroupInfo(OrderInfo order) {
      XNamedGroupInfo named = order.getNamedGroupInfo();

      if(named == null || !(named instanceof AssetNamedGroupInfo)) {
         return named;
      }

      AssetNamedGroupInfo namedGroupInfo = (AssetNamedGroupInfo) named;
      AssetEntry entry = namedGroupInfo.getEntry();
      NamedGroupAssembly namedGroupAssembly = getNamedGroupAssembly(entry);

      if(namedGroupAssembly == null) {
         return named;
      }

      AssetNamedGroupInfo ninfo = new AssetNamedGroupInfo(entry, namedGroupAssembly);

      return ninfo.equalsContent(named) ? named : ninfo;
   }

   public static NamedGroupAssembly getNamedGroupAssembly(AssetEntry entry) {
      AssetRepository rep = AssetUtil.getAssetRepository(false);
      Worksheet ws = null;

      try {
         ws = (Worksheet) rep.getSheet(entry, null, false, AssetContent.ALL);
      }
      catch(Exception ex) {
         LOG.error("Worksheet not found: {0}",  entry);
      }

      if(ws == null) {
         return null;
      }

      Assembly assembly = ws.getPrimaryAssembly();

      if(!(assembly instanceof NamedGroupAssembly)) {
         return null;
      }

      return (NamedGroupAssembly) assembly;
   }

   /**
    * Get the aggregate name without formula.
    */
   protected static String trimFormula(CalcAggregate agg) {
      String name = BindingTool.getFieldName(agg);
      String formula = BindingTool.getFormulaString(agg.getFormulaName());

      if(formula == null || formula.length() == 0) {
         return name;
      }
      else if(name.startsWith(formula)) {
         return name.substring(formula.length() + 1, name.length() - 1);
      }

      return name;
   }

   /**
    * Get original column.
    */
   public static String getOriginalColumn(String col) {
      if(col == null) {
         return null;
      }

      int left = col.indexOf("(");

      if(left >= 0) {
         col = col.substring(left + 1);
      }

      int right = col.lastIndexOf(")");

      if(right >= 0) {
         return col.substring(0, right);
      }

      return col;
   }

   public static String getFieldNameByScript(String value) {
      String prefix = "data['";

      if(value != null && !value.startsWith(prefix)) {
         value = getOriginalColumn(value);
      }

      // like data['Customer:State'],'sort=asc'
      if(value != null && value.startsWith(prefix)) {
         int idx = value.indexOf("']");

         if(idx == -1) {
            return null;
         }

         String fname = value.substring(prefix.length(), idx);

         // find field, date['*@ or date['col@
         if(fname.startsWith("*")) {
            idx = value.indexOf("field=");

            if(idx == -1) {
               idx = value.indexOf("fields=");
            }

            if(idx != -1) {
               fname = value.substring(idx + 6);
               idx = fname.indexOf(",");

               if(idx != -1) {
                  fname = fname.substring(0, idx);
               }
            }
            else {
               idx = fname.indexOf("rowValue[\"");

               if(idx != -1) {
                  fname = fname.substring(idx + "rowValue[\"".length());
                  idx = fname.indexOf("\"]");

                  if(idx != -1) {
                     fname = fname.substring(0, idx);
                  }
               }
            }
         }
         else if(fname.indexOf("@") != -1) {
            idx = fname.indexOf("@");

            if(idx != -1) {
               fname = fname.substring(0, idx);
            }
         }

         return fname;
      }

      return null;
   }

   public static void fixDuplicateCellBinding(TableLayout layout, TableCellBinding binding) {
      if(layout == null || binding == null) {
         return;
      }

      TableCellBinding oldBinding = (TableCellBinding) Tool.clone(binding);
      fixDuplicateName(layout, binding);
      fixMergeRowColGroup(binding, oldBinding);
   }

   public static void fixDuplicateName(TableLayout layout, TableCellBinding binding) {
      String rname = binding.getRuntimeCellName();
      Set<String> cellNames = layout.getCellNames0(true);

      if(!cellNames.contains(rname)) {
         return;
      }

      int idx = 1;

      while(cellNames.contains(rname + "_" + idx)) {
         idx++;
      }

      binding.setCellName(rname + "_" + idx);
   }

   private static void fixMergeRowColGroup(TableCellBinding binding, TableCellBinding oldBinding) {
      if(binding.getMergeRowGroup() != null &&
         Tool.equals(binding.getMergeRowGroup(), oldBinding.getCellName()))
      {
         binding.setMergeRowGroup(binding.getCellName());
      }

      if(binding.getMergeColGroup() != null &&
         Tool.equals(binding.getMergeColGroup(), oldBinding.getCellName()))
      {
         binding.setMergeColGroup(binding.getCellName());
      }
   }

   protected static final Map<Integer, String> DATEOPT = new HashMap<>();

   static {
      DATEOPT.put(DateRangeRef.YEAR_INTERVAL, "rounddate=year");
      DATEOPT.put(DateRangeRef.QUARTER_INTERVAL, "rounddate=quarter");
      DATEOPT.put(DateRangeRef.MONTH_INTERVAL, "rounddate=month");
      DATEOPT.put(DateRangeRef.WEEK_INTERVAL, "rounddate=week");
      DATEOPT.put(DateRangeRef.DAY_INTERVAL, "rounddate=day");
      DATEOPT.put(DateRangeRef.HOUR_INTERVAL, "rounddate=hour");
      DATEOPT.put(DateRangeRef.MINUTE_INTERVAL, "rounddate=minute");
      DATEOPT.put(DateRangeRef.SECOND_INTERVAL, "rounddate=second");
      DATEOPT.put(DateRangeRef.QUARTER_OF_YEAR_PART, "date=quarter");
      DATEOPT.put(DateRangeRef.MONTH_OF_YEAR_PART, "date=month");
      DATEOPT.put(DateRangeRef.WEEK_OF_YEAR_PART, "date=week");
      DATEOPT.put(DateRangeRef.DAY_OF_MONTH_PART, "date=day");
      DATEOPT.put(DateRangeRef.DAY_OF_WEEK_PART, "date=weekday");
      DATEOPT.put(DateRangeRef.HOUR_OF_DAY_PART, "date=hour");
      DATEOPT.put(DateRangeRef.MINUTE_OF_HOUR_PART, "date=minute");
      DATEOPT.put(DateRangeRef.SECOND_OF_MINUTE_PART, "date=second");
      DATEOPT.put(DateRangeRef.NONE_INTERVAL, "rounddate=none");
   }

   protected static final int HEADER = TableDataPath.HEADER;
   protected static final int G_HEADER = TableDataPath.GROUP_HEADER;
   protected static final int DETAIL = TableDataPath.DETAIL;
   protected static final int G_FOOTER = TableDataPath.SUMMARY;
   protected static final int FOOTER = TableDataPath.GRAND_TOTAL;
   public static final String SCRIPT_ESCAPED_COLON = "^_^";
   public static final String PARAM_PREFIX = "parameter.";

   private static final Logger LOG =
      LoggerFactory.getLogger(LayoutTool.class);

   /**
    * Aggregate formula parse result storage.
    */
   public static class FormulaStorage {
      FormulaStorage(String formula, String param, int percent) {
         this.formula = formula;

         if(formula != null && ("correlation".equalsIgnoreCase(formula) ||
            "covariance".equalsIgnoreCase(formula) ||
            "weightedAverage".equalsIgnoreCase(formula)))
         {
            secondf = param;
         }
         else {
            try {
               Integer.parseInt(param);
               n = param;
            }
            catch(Exception ex) {
               secondf = param;
            }
         }

         this.percent = percent;
      }

      public String formula;
      public String secondf;
      public String n;
      public int percent = -1;
   }

   /**
    * Flag indicating that the top border should be drawn.
    */
   public static final int BORDER_TOP = 1;

   /**
    * Flag indicating that the bottom border should be drawn.
    */
   public static final int BORDER_BOTTOM = 2;

   /**
    * Flag indicating that the left border should be drawn.
    */
   public static final int BORDER_LEFT = 4;

   /**
    * Flag indicating that the right border should be drawn.
    */
   public static final int BORDER_RIGHT = 8;

   /**
    * Flag indicating that all borders should be drawn.
    */
   public static final int BORDER_ALL =
      BORDER_TOP | BORDER_BOTTOM | BORDER_LEFT | BORDER_RIGHT;

   /**
    * Enumeration of the binding regions in a crosstab table.
    *
    * @author InetSoft Technology
    * @since  10.3
    */
   public static enum CrosstabRegion {
      /**
       * Indicates a row header cell.
       */
      ROW_HEADER,

      /**
       * Indicates a column header cell.
       */
      COL_HEADER,

      /**
       * Indicates an aggregate cell.
       */
      AGGREGATE
   }

   /**
    * Enumeration of the binding operations on a crosstab table.
    *
    * @author InetSoft Technology
    * @since  10.3
    */
   public static enum CrosstabOperation {
      /**
       * Indicates that a cell should be inserted.
       */
      INSERT,

      /**
       * Indicates that a cell should be appended.
       */
      APPEND,

      /**
       * Indicates that a cell should be replaced.
       */
      REPLACE
   }

   /**
    * Data structure that encapsulates information about the drag source or drop
    * target corresponding to a cell of a crosstab table.
    *
    * @author InetSoft Technology
    * @since  10.3
    */
   public static final class CrosstabTarget {
      /**
       * The binding region.
       */
      public CrosstabRegion region = null;

      /**
       * The binding operation.
       */
      public CrosstabOperation op = null;

      /**
       * The index of the group or aggregate field.
       */
      public int index = -1;

      /**
       * The border of the DnD indicator.
       */
      public int border = -1;

      /**
       * The targeted table cell.
       */
      public Point cell = null;

      /**
       * The selected cells for the targeted region.
       */
      public Insets selection = null;
   }
}
