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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.filter.*;
import inetsoft.report.internal.*;
import inetsoft.report.internal.binding.*;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSLayoutTool;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * Table highlight attr contains table highlight attributes.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class TableHighlightAttr extends TableAttr {
   /**
    * Check if a data path in a table data descriptor supports table highlight.
    * @param table the specified table lens
    * @param dpath the specified data path
    * @return true if supports, false otherwise
    */
   public static boolean supportsHighlight(TableLens table, TableDataPath dpath) {
      int type = table.getDescriptor().getType();

      // is cell data path?
      if(!dpath.isRow() && !dpath.isCol()) {
         return true;
      }
      // is row data path and is not crosstab?
      else if(dpath.isRow() && type != TableDataDescriptor.CROSSTAB_TABLE &&
              type != TableDataDescriptor.CALC_TABLE)
      {
         return true;
      }

      return false;
   }

   /**
    * Create a field for a table column.
    */
   private static Field createField(TableLens table, int row, int col) {
      String header = Util.getHeader(table, col).toString();
      Field fld = null;

      if(!header.startsWith(BindingTool.FAKE_PREFIX)) {
         Object obj = table.getObject(row, col);
         Class<?> cls;

         if(obj != null) {
            cls = obj.getClass();
         }
         else {
            cls = table.getColType(col);
         }

         String type = Util.getDataType(cls);
         fld = new BaseField(header);
         fld.setDataType(type);
      }

      return fld;
   }

   /**
    * Get available fields of a table row.
    * @param table the specified table lens
    * @param row the specified row
    */
   public static Field[] getAvailableFields(TableLens table, int row, BindableElement belem) {
      if(table.getDescriptor().getType() == TableDataDescriptor.CROSSTAB_TABLE ||
         !table.moreRows(row) && row >= table.getRowCount())
      {
         return new Field[0];
      }

      table.moreRows(row);
      Map<Integer, Field> flds = new OrderedMap<>();
      Set<Field> fields = new HashSet<>();
      int index = table.getColCount();

      for(int i = 0; i < index; i++) {
         Field fld = createField(table, row, i);

         if(fld != null) {
            fields.add(fld);
            flds.put(i, fld);
         }
      }

      // find hidden columns
      try {
         while(table instanceof TableFilter) {
            TableLens base = ((TableFilter) table).getTable();

            if(((TableFilter) table).getBaseRowIndex(row) != row) {
               break;
            }

            table = base;
            final boolean filter = table instanceof TableFilter;

            for(int i = 0; i < table.getColCount(); i++) {
               if(flds.containsKey(i)) {
                  continue;
               }

               Field fld = createField(table, row, i);

               if(fld == null || flds.containsValue(fld)) {
                  continue;
               }

               int baseCol = filter ? ((TableFilter) table).getBaseColIndex(i) : i;
               flds.put(baseCol, fld);
            }
         }
      }
      catch(Exception ex) {
         // just in case
         LOG.warn("Failed to find hidden fields", ex);
      }

      return flds.values().toArray(new Field[0]);
   }

   /**
    * Get available fields of a table cell.
    * @param table the specified table lens
    * @param row the specified row
    * @param col the specified col
    */
   public static Field[] getAvailableFields(TableLens table, int row, int col) {
      int type = table.getDescriptor().getType();

      if(type == TableDataDescriptor.CROSSTAB_TABLE) {
         // @by billh, dangerous operation!!! Precondition is base row/col
         // index won't be changed, the condition is satisfied at present, but
         // the precondition is relatively fragile...
         row = TableTool.getBaseRowIndex(table, Util.getCrossFilter(table), row);

         if(row < 0) {
            row = 0;
         }

         String[] sflds = Util.getCrossFilter(table).getAvailableFields(row, col);
         BaseField[] flds = new BaseField[sflds.length];

         for(int i = 0; i < sflds.length; i++) {
            int index1 = sflds[i].indexOf("^");
            int index2 = sflds[i].indexOf("_");
            String dtype = sflds[i].substring(0, index1);
            int option = Integer.parseInt(sflds[i].substring(index1 + 1, index2));
            String name = sflds[i].substring(index2 + 1);
            flds[i] = new BaseField(name);
            flds[i].setDataType(dtype);
            flds[i].setOption(XSchema.isDateType(flds[i].getDataType()) || XSchema.isNumericType(flds[i].getDataType()) ? option : 0);
         }

         return flds;
      }
      else if(type == TableDataDescriptor.CALC_TABLE) {
         CalcTableLens calc = (CalcTableLens)
            Util.getNestedTable(table, CalcTableLens.class);
         TableLayout layout = calc.getElement().getTableLayout();
         String[] sflds = layout.getCellNames(true);
         List<Field> flds = new ArrayList<>();

         for(String sfld : sflds) {
            Point cellLocation = calc.getCellLocation(sfld);
            TableCellBinding binding =
               (TableCellBinding) layout.getCellBinding(cellLocation.y, cellLocation.x);
            FormulaTable formulaTable = calc.getElement();
            DataRef ref = VSLayoutTool.findAttribute(formulaTable, binding, sfld);
            BaseField fld = new BaseField(sfld);

            if(ref != null) {
               if(binding.getBType() == CellBinding.SUMMARY &&
                  XSchema.isDateType(ref.getDataType()))
               {
                  String formula = binding.getFormula();

                  if(SummaryAttr.COUNT_FORMULA.equals(formula) ||
                     SummaryAttr.DISTINCTCOUNT_FORMULA.equals(formula))
                  {
                     if(ref instanceof ColumnRef) {
                        ref = (ColumnRef) ref.clone();
                        ((ColumnRef) ref).setDataType(XSchema.INTEGER);
                     }
                     else if(ref instanceof BaseField) {
                        ref = (BaseField) ref.clone();
                        ((BaseField) ref).setDataType(XSchema.INTEGER);
                     }
                  }
               }

               if(binding.getBType() == CellBinding.GROUP &&
                  XSchema.isDateType(ref.getDataType()))
               {
                  // When it is binding time column and create date range ref to none, should not
                  // change time type to date type, should keep time type in highlight condition.
                  if(binding.getDateOption() == DateRangeRef.NONE) {
                     fld.setDataType(ref.getDataType());
                  }
                  else {
                     fld.setDataType(
                        DateRangeRef.getDataType(binding.getDateOption(), ref.getDataType()));
                  }
               }
               else {
                  fld.setDataType(ref.getDataType());
               }

               if(binding.getOrderInfo(false) != null &&
                  binding.getOrderInfo(false).getRealNamedGroupInfo() != null)
               {
                  fld.setDataType(XSchema.STRING);
               }
            }

            flds.add(fld);
         }

         return flds.toArray(new Field[0]);
      }
      else {
         return getAvailableFields(table, row, null);
      }
   }

   /**
    * Get available fields of a table data path.
    * @param path the specified table data path
    */
   public static Field[] getAvailableFields(TableLens table, TableDataPath path) {
      TableDataDescriptor descriptor = table.getDescriptor();

      // is col path?
      if(path.isCol()) {
         return new Field[0];
      }
      // is row path and is crosstab?
      else if(path.isRow() && descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
         return new Field[0];
      }
      // is row path but is not crosstab?
      else if(path.isRow()) {
         for(int i = 0; table.moreRows(i); i++) {
            TableDataPath path2 = descriptor.getRowDataPath(i);

            if(path.equals(path2)) {
               return getAvailableFields(table, i, null);
            }
         }

         return new Field[0];
      }
      // is cell path?
      else {
         for(int i = 0; table.moreRows(i); i++) {
            for(int j = 0; j < table.getColCount(); j++) {
               TableDataPath path2 = descriptor.getCellDataPath(i, j);

               if(path.equals(path2)) {
                  return getAvailableFields(table, i, j);
               }
            }
         }

         return new Field[0];
      }
   }

   /**
    * Create a table highlight attr.
    */
   public TableHighlightAttr() {
      this.hlmap = new HashMap<>();
   }

   /**
    * Check if is null.
    */
   public boolean isNull() {
      return hlmap.size() == 0;
   }

   /**
    * Set condition table for highlight condition evaluation.
    */
   public void setConditionTable(TableLens lens) {
      this.ctable = lens;
   }

   /**
    * Get condition table for highlight condition evaluation.
    */
   public TableLens getConditionTable() {
      return this.ctable;
   }

   /**
    * Create filter from a table lens. TableAttr will apply the attributes
    * to the created filter.
    * @param table the base table lens
    */
   @Override
   public TableLens createFilter(TableLens table) {
      return hlmap.size() == 0 ? table : new HighlightTableLens(table, ctable);
   }

   /**
    * Set a highlight group at the specified table data path.
    * @param tpath the specified table data path
    * @param hg the specified highlight group
    */
   public void setHighlight(TableDataPath tpath, HighlightGroup hg) {
      if(hg != null) {
         hg.validate();

         if(level != null) {
            hg = hg.isEmpty(level) ? null : hg;
         }
         else {
            hg = hg.isEmpty() ? null : hg;
         }
      }

      if(hg == null) {
         hlmap.remove(tpath);
      }
      else {
         hlmap.put(tpath, hg);
      }
   }

   /**
    * Get highlight group at the specified table data path.
    */
   public HighlightGroup getHighlight(TableDataPath tpath) {
      return hlmap.get(tpath);
   }

   /**
    * Get all the keys.
    */
   public Enumeration<TableDataPath> getAllDataPaths() {
      return new IteratorEnumeration<>(hlmap.keySet().iterator());
   }

   /**
    * Get all the values.
    */
   public Enumeration<HighlightGroup> getAllHighlights() {
      return new IteratorEnumeration<>(hlmap.values().stream().filter(a -> a != null).iterator());
   }

   /**
    * Get the map.
    */
   public Map<TableDataPath, HighlightGroup> getHighlightMap() {
      return hlmap;
   }

   /**
    * Sets the binding level of the highlights to use.
    *
    * @param level the name of the binding level.
    */
   public void setLevel(String level) {
      this.level = level;
   }

   /**
    * Gets the binding level of the highlights to use.
    *
    * @return the name of the binding level.
    */
   public String getLevel() {
      return level;
   }

   /**
    * Sync the table highlight attr.
    */
   public void sync(List<TableDataPath> paths, TableLens table) {
      sync(paths, table, true);
   }

   /**
    * Sync the table highlight attr.
    */
   public void sync(List<TableDataPath> paths, TableLens table, boolean removal) {
      int type = table.getDescriptor().getType();
      List<TableDataPath> keys = new ArrayList<>(hlmap.keySet());
      List<TableDataPath> rmlist = new ArrayList<>();

      for(TableDataPath path : keys) {
         boolean replaced = false;

         // @by larryl, for crosstab, keep all header cell setting since the
         // cells are dynamic and can be set in live edit mode
         if(type == TableDataDescriptor.CROSSTAB_TABLE &&
            !path.isRow() && !path.isCol() &&
            path.getType() == TableDataPath.HEADER)
         {
            continue;
         }

         if(!paths.contains(path)) {
            // @by larryl, we allow a detail cell path to remain if there is
            // a group cell of the same path. This is for the detail cell
            // of grouped table with in-place style, where the detail cell
            // may not be on the meta data table
            if(path.getType() == TableDataPath.DETAIL) {
               TableDataPath group = new TableDataPath(
                  0, TableDataPath.GROUP_HEADER, path.getDataType(), path.getPath());

               if(paths.contains(group)) {
                  continue;
               }
            }
            else if(path.getType() == TableDataPath.SUMMARY) {
               TableDataPath path2 = findPathByName(path, paths);

               if(path2 != null && !hlmap.containsKey(path2)) {
                  HighlightGroup obj = hlmap.get(path);

                  if(obj != null) {
                     hlmap.put(path2, obj);
                     replaced = true;
                  }
               }
            }

            if(removal || replaced) {
               rmlist.add(path);
            }
         }
      }

      for(TableDataPath rmpath : rmlist) {
         hlmap.remove(rmpath);
      }
   }

   /**
    * Clear the table highlight attr.
    */
   public void clear() {
      hlmap.clear();
   }

   /**
    * Get the filter attr's variables.
    */
   public UserVariable[] getAllVariables() {
      List<UserVariable> vars = new ArrayList<>();
      Enumeration<HighlightGroup> groups = getAllHighlights();

      while(groups.hasMoreElements()) {
         HighlightGroup group = groups.nextElement();
         UserVariable[] tmpvars = group.getAllVariables();

         Collections.addAll(vars, tmpvars);
      }

      return vars.toArray(new UserVariable[0]);
   }

   /**
    * Replace the variables with the given values.
    */
   public void replaceVariables(VariableTable vars) {
      for(HighlightGroup hg : hlmap.values()) {
         if(hg != null) {
            hg.replaceVariables(vars);
         }
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<tableHighlightAttr>");

      for(TableDataPath dpath : hlmap.keySet()) {
         HighlightGroup hg = hlmap.get(dpath);

         if(hg != null) {
            writer.println("<aHighlightGroup>");
            dpath.writeXML(writer);
            hg.writeXML(writer);
            writer.println("</aHighlightGroup>");
         }
      }

      writer.println("</tableHighlightAttr>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      NodeList hgnodes = Tool.getChildNodesByTagName(tag, "aHighlightGroup");

      for(int i = 0; i < hgnodes.getLength(); i++) {
         Element ahgnode = (Element) hgnodes.item(i);
         Element dpathnode =
            Tool.getChildNodeByTagName(ahgnode, "tableDataPath");
         Element hgnode = Tool.getChildNodeByTagName(ahgnode, "HighlightGroup");

         TableDataPath dpath = new TableDataPath();
         dpath.parseXML(dpathnode);
         HighlightGroup hg = new HighlightGroup();
         hg.parseXML(hgnode);

         hlmap.put(dpath, hg);
      }
   }

   /**
    * Clone the object.
    */
   @Override
   public TableHighlightAttr clone() {
      TableHighlightAttr attr2 = new TableHighlightAttr();
      attr2.hlmap = Tool.deepCloneMap(hlmap);
      return attr2;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object to compare.
    * @return <tt>true</tt> if equals the specified object,
    * <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TableHighlightAttr)) {
         return false;
      }

      TableHighlightAttr attr2 = (TableHighlightAttr) obj;

      return hlmap.equals(attr2.hlmap);
   }

   public String toString() {
      return hlmap.toString();
   }

   /**
    * Find the specified variable is used in higlight one of condition.
    */
   public boolean checkUsed(String variable) {
      Enumeration<HighlightGroup> groups = getAllHighlights();
      String level = this.level == null ? HighlightGroup.DEFAULT_LEVEL : this.level;

      while(groups.hasMoreElements()) {
         HighlightGroup group = groups.nextElement();
         String[] names = group.getNames(level);

         for(String name : names) {
            Highlight h = group.getHighlight(level, name);

            if(h.isConditionEmpty()) {
               continue;
            }

            ConditionList cond = h.getConditionGroup();

            if(AssetEventUtil.checkUsed(cond, variable)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * HighlightTableLens is used to apply table highlight.
    */
   public class HighlightTableLens extends AttributeTableLens implements CachedTableLens {
      /**
       * Create a highlight table lens.
       */
      public HighlightTableLens(TableLens table, TableLens ctable) {
         super(table);
         check = true;
         this.ctable = ctable == null ? table : ctable;
      }

      /**
       * Return the per cell font. Return null to use default font.
       * @param r row number.
       * @param c column number.
       * @return font for the specified cell.
       */
      @Override
      public Font getFont(int r, int c) {
         checkInit();
         Highlight rowh = getHighlight(r);
         Highlight cellh = getHighlight(r, c);

         return mergeFont(super.getFont(r, c),
            rowh == null ? null : rowh.getFont(),
            cellh == null ? null : cellh.getFont());
      }

      /**
       * Return the per cell foreground color. Return null to use default
       * color.
       * @param r row number.
       * @param c column number.
       * @return foreground color for the specified cell.
       */
      @Override
      public Color getForeground(int r, int c) {
         checkInit();
         Highlight rowh = getHighlight(r);
         Highlight cellh = getHighlight(r, c);

         return mergeColor(super.getForeground(r, c),
            rowh == null ? null : rowh.getForeground(),
            cellh == null ? null : cellh.getForeground());
      }

      /**
       * Return the per cell background color. Return null to use default
       * color.
       * @param r row number.
       * @param c column number.
       * @return background color for the specified cell.
       */
      @Override
      public Color getBackground(int r, int c) {
         checkInit();
         Highlight rowh = getHighlight(r);
         Highlight cellh = getHighlight(r, c);

         return mergeColor(super.getBackground(r, c),
            rowh == null ? null : rowh.getBackground(),
            cellh == null ? null : cellh.getBackground());
      }

      @Override
      public Color getBackground(int r, int c, int spanRow) {
         return mergeColor(table.getBackground(r, c, spanRow), getBackground(r, c), null);
      }

      /**
       * Get the object at the specified cell.
       */
      @Override
      public final Object getObject0(int r, int c) {
         return table.getObject(r, c);
      }

      /**
       * Invalidate the table filter forcely, and the table filter will perform
       * filtering calculation to validate itself.
       */
      @Override
      public synchronized void invalidate() {
         inited = false;
         crosstab = null;

         super.invalidate();
      }

      /**
       * Set a cell value.
       */
      @Override
      public void setObject(int r, int c, Object val) {
         // @by billh, don't use cache
         table.setObject(r, c, val);
      }

      /**
       * Merge font.
       */
      private Font mergeFont(Font font1, Font font2, Font font3) {
         return font3 != null ? font3 : (font2 != null ? font2 : font1);
      }

      /**
       * Merge color.
       */
      private Color mergeColor(Color color1, Color color2, Color color3) {
         return color3 != null ? color3 : (color2 != null ? color2 : color1);
      }

      /**
       * Clear all cached data.
       */
      @Override
      public synchronized void clearCache() {
         if(rowcache != null) {
            rowcache.clear();
         }

         if(cellcache != null) {
            cellcache.clear();
         }
      }

      /**
       * Check if inited.
       */
      private synchronized void checkInit() {
         if(!inited) {
            inited = true;
            descriptor = getDescriptor();
            rowcache = new FixedSizeSparseMatrix();
            cellcache = new FixedSizeSparseMatrix();

            if(descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
               crosstab = Util.getCrossFilter(ctable);
               return;
            }

            paths = new TableDataPath[getTable().getColCount()][];
            final int pathsLength = paths.length;
            List<List<TableDataPath>> plist = new ArrayList<>(pathsLength);
            List<TableDataPath> rlist = new ArrayList<>();
            allpath = null;

            for(int i = 0; i < pathsLength; i++) {
               plist.add(new ArrayList<>());
            }

            for(TableDataPath path : hlmap.keySet()) {
               // row data path?
               if(path.isRow()) {
                  rlist.add(path);
               }
               // cell data path?
               else {
                  int[] cols = getColumns(getTable(), path.getPath()[0]);

                  if(cols != null) {
                     for(int col : cols) {
                        if(col < pathsLength) {
                           plist.get(col).add(path);
                        }
                     }
                  }
               }
            }

            for(int i = 0; i < pathsLength; i++) {
               paths[i] = plist.get(i).toArray(new TableDataPath[0]);
            }

            rpath = rlist.toArray(new TableDataPath[0]);
         }
      }

      /**
       * Get highlight of a table row.
       * @param row the specified row
       */
      private synchronized Highlight getHighlight(int row) {
         Object hl = rowcache.get(0, row);

         if(hl == SparseMatrix.NULL) {
            hl = getHighlight0(row);

            rowcache.set(0, row, hl);
         }

         return (Highlight) hl;
      }

      /**
       * Get highlight of a table row.
       * @param row the specified row
       */
      private synchronized Highlight getHighlight0(int row) {
         if(descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
            return null;
         }

         TableDataPath path = getDataPath(row);

         if(path != null) {
            HighlightGroup group = getHighlight0(path);
            group.setQuerySandbox(getQuerySandbox());
            row = TableTool.getBaseRowIndex(getTable(), ctable, row);

            if(row >= 0) {
               if(level == null) {
                  return group.findGroup(ctable, row);
               }
               else {
                  return group.findGroup(level, ctable, row);
               }
            }
         }

         return null;
      }

      /**
       * Get highlight of a table cell.
       * @param row the specified row
       * @param col the specified col
       */
      private synchronized Highlight getHighlight(int row, int col) {
         Object hl = cellcache.get(row, col);

         if(hl == SparseMatrix.NULL) {
            hl = getHighlight0(row, col);

            cellcache.set(row, col, hl);
         }

         return (Highlight) hl;
      }

      /**
       * Get highlight of a table cell.
       * @param row the specified row
       * @param col the specified col
       */
      private synchronized Highlight getHighlight0(int row, int col) {
         TableDataPath path = getDataPath(row, col);

         if(path != null) {
            HighlightGroup group = getHighlight0(path);

            if(group == null) {
               return null;
            }

            group.setQuerySandbox(getQuerySandbox());
            String level = (TableHighlightAttr.this.level == null ?
                            HighlightGroup.DEFAULT_LEVEL :
                            TableHighlightAttr.this.level);
            int type = descriptor.getType();

            if(type == TableDataDescriptor.CROSSTAB_TABLE) {
               // @by billh, dangerous operation!!! Precondition is base row/col
               // index won't be changed, the condition is satisfied at present,
               // but the precondition is relatively fragile...
               // @by larryl, if header rows are inserted to the crosstab,
               // we need to map the rows to the crosstab row. This means the
               // inserted header rows use the same value as header rows
               row = TableTool.getBaseRowIndex(getTable(), crosstab, row);

               if(row < 0) {
                  row = 0;
               }

               col = TableTool.getBaseColIndex(getTable(), crosstab, col);

               if(col < 0) {
                  col = 0;
               }

               return group.findGroup(level, crosstab, row, col);
            }
            else if(type == TableDataDescriptor.CALC_TABLE) {
               RuntimeCalcTableLens calc = (RuntimeCalcTableLens)
                  Util.getNestedTable(table, RuntimeCalcTableLens.class);

               // design time has no RuntimeCalcTableLens
               if(calc != null) {
                  return group.findGroup(level, calc, row, col);
               }
            }
            else if(path == allpath) {
               row = TableTool.getBaseRowIndex(getTable(), ctable, row);
               return group.findGroup(level, ctable, row, col);
            }
            else {
               row = TableTool.getBaseRowIndex(getTable(), ctable, row);

               if(row >= 0) {
                  return group.findGroup(level, ctable, row, col);
               }
            }
         }

         return null;
      }

      /**
       * Get table data path stored in table highlight map of the table row.
       * @param row the specified row
       */
      private synchronized TableDataPath getDataPath(int row) {
         if(descriptor.getType() != TableDataDescriptor.CROSSTAB_TABLE) {
            for(TableDataPath tableDataPath : rpath) {
               if(descriptor.isRowDataPath(row, tableDataPath)) {
                  return tableDataPath;
               }
            }
         }

         return null;
      }

      /**
       * Get table data path stored in table high map of the table cell.
       * @param row the specified row
       * @param col the specified col
       */
      private synchronized TableDataPath getDataPath(int row, int col) {
         if(descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
            for(TableDataPath path : hlmap.keySet()) {
               if(!path.isRow() &&
                  descriptor.isCellDataPathType(row, col, path))
               {
                  return path;
               }
            }
         }
         else {
            TableDataPath[] cpath = paths[col];

            for(TableDataPath tableDataPath : cpath) {
               if(descriptor.isCellDataPathType(row, col, tableDataPath)) {
                  return tableDataPath;
               }
            }
         }

         return allpath;
      }

      /**
       * Get highlight group at the specified table data path.
       */
      private HighlightGroup getHighlight0(TableDataPath tpath) {
         return hlmap.get(tpath);
      }

      /**
       * Get the names of all highlights.
       */
      public List<String> getHighlightNames() {
         List<String> names = new ArrayList<>();
         Enumeration<HighlightGroup> hlgroups = getAllHighlights();

         while(hlgroups.hasMoreElements()) {
            Collections.addAll(names, hlgroups.nextElement().getNames(level));
         }

         return names;
      }

      /**
       * Check if a highlight is matched in the table.
       */
      public boolean isHighlighted(String hlname) {
         checkInit();

         for(int r = 0; moreRows(r); r++) {
            Highlight rowh = getHighlight(r);

            if(rowh != null && rowh.getName().equals(hlname)) {
               return true;
            }

            for(int c = 0; c < getColCount(); c++) {
               Highlight cellh = getHighlight(r, c);

               if(cellh != null && hlname.equals(cellh.getName())) {
                  return true;
               }
            }
         }

         return false;
      }

      /**
       * Getter of asset query sandbox.
       */
      public Object getQuerySandbox() {
         return querySandbox;
      }

      /**
       * Setter of asset query sandbox.
       */
      public void setQuerySandbox(Object box) {
         this.querySandbox = box;
      }

      @Override
      public void setPresenter(String header, Presenter p) {
         if(attritable != null) {
            attritable.setPresenter(header, p);
         }
         else {
            super.setPresenter(header, p);
         }
      }

      @Override
      public void setPresenter(int r, int c, Presenter presenter) {
         if(attritable != null) {
            attritable.setPresenter(r, c, presenter);
         }
         else {
            super.setPresenter(r, c, presenter);
         }
      }

      @Override
      public void setPresenter(int col, Presenter p) {
         if(attritable != null) {
            attritable.setPresenter(col, p);
         }
         else {
            super.setPresenter(col, p);
         }
      }

      private CrossFilter crosstab;
      private FixedSizeSparseMatrix rowcache;
      private FixedSizeSparseMatrix cellcache;
      private TableDataPath[][] paths; // cell data path
      private TableDataPath[] rpath;  // row data path
      private TableDataPath allpath;
      private TableDataDescriptor descriptor;
      private Object querySandbox;
      private boolean inited = false;
      private transient TableLens ctable;
   }

   private Map<TableDataPath, HighlightGroup> hlmap;
   private transient TableLens ctable = null;
   private transient String level = null;

   private static final Logger LOG = LoggerFactory.getLogger(TableHighlightAttr.class);
}
