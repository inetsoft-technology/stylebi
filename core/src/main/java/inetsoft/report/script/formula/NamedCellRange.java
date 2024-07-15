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
package inetsoft.report.script.formula;

import inetsoft.report.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.table.CalcCellContext;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.uql.XTable;
import inetsoft.util.Tool;
import inetsoft.util.script.FormulaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;

/**
 * A cell range handling the colname[@(group:value)*[?condition]] syntax.
 *
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public class NamedCellRange extends CellRange {
   /**
    * Parse a cell range. The supported syntax is:
    * colname[@(group:value)*[?condition]]
    */
   public NamedCellRange(String range) throws Exception {
      this.range = range;
      range = encodeEscaped(range);

      int at = range.indexOf('@');
      int q = range.indexOf('?', Math.max(0, at));

      if(at >= 0) {
         colexpr = range.substring(0, at).trim();
      }
      else if(q >= 0) {
         colexpr = range.substring(0, q).trim();
      }
      else {
         colexpr = range.trim();
      }

      range = range.substring(colexpr.length()).trim();

      if(range.startsWith("@")) {
         range = range.substring(1);
      }
      // colname removed from the range at this point

      if(colexpr.startsWith("{") && colexpr.endsWith("}")) {
         summary = true;
         colexpr = colexpr.substring(1, colexpr.length() - 1);
      }
      else if(colexpr.startsWith("=")) {
         expression = true;
         colexpr = colexpr.substring(1);
      }

      colexpr = Tool.replaceAll(colexpr, LayoutTool.SCRIPT_ESCAPED_COLON, ":");

      String gstr = ""; // group spec

      q = range.indexOf('?');

      if(q < 0) {
         condition = null;
         gstr = range;
      }
      else {
         condition = range.substring(q + 1).trim();
         gstr = range.substring(0, q).trim();
      }

      if(gstr.length() > 0) {
         // handle escape in string. This is a cruel and simple solution which
         // may need to be rewritten to be more robust
         String esc_semicolon = "#&^_ESCAPED_SEMI_COLON_^&#";

         gstr = Tool.replaceAll(gstr, "\\;", esc_semicolon);
         String[] arr = Tool.split(gstr, ';');

         for(int i = 0; i < arr.length; i++) {
            arr[i] = Tool.replaceAll(arr[i], esc_semicolon, ";");
            int idx = arr[i].indexOf(':');

            if(idx < 0) {
               throw new Exception("Group value missing from range: " + this.range);
            }

            String group = decodeEscaped(arr[i].substring(0, idx).trim());
            group = Tool.replaceAll(group, LayoutTool.SCRIPT_ESCAPED_COLON, ":");
            String value = decodeEscaped(arr[i].substring(idx + 1).trim());
            groupspecs.put(group, new GroupSpec(value));
         }
      }

      colexpr = decodeEscaped(colexpr);
      condition = decodeEscaped(condition);
   }

   private static final String ESCAPE_PREFIX = "__ESCAPED_CHAR_";

   /**
    * convert escaped character to a special string
    * @hidden
    */
   public static String encodeEscaped(String str) {
      StringBuffer buf = new StringBuffer();

      for(int i = 0; i < str.length(); i++) {
         char ch = str.charAt(i);

         if(ch == '\\' && i < str.length() - 1) {
            buf.append(ESCAPE_PREFIX + (int) str.charAt(i + 1) + "___");
            i++;
         }
         else {
            buf.append(ch);
         }
      }

      return buf.toString();
   }

   /**
    * decode encoded string to escaped character
    * @hidden
    */
   public static String decodeEscaped(String str) {
      if(str == null) {
         return str;
      }

      int idx;

      while((idx = str.indexOf(ESCAPE_PREFIX)) >= 0) {
         int idx2 = str.indexOf("___", idx + ESCAPE_PREFIX.length());

         if(idx2 < 0) {
            break;
         }

         String sub = str.substring(idx + ESCAPE_PREFIX.length(), idx2);
         str = str.substring(0, idx) + (char) Integer.parseInt(sub) + str.substring(idx2 + 3);
      }

      return str;
   }

   /**
    * Get all cells in the range.
    * @param table table to extract cells. The table should be the
    * FilterTable from a groupable.
    * @param position true to return cell position (Point), false to return value.
    */
   @Override
   public Collection getCells(XTable table, boolean position) {
      TableDataDescriptor desc = (table instanceof TableLens) ? table.getDescriptor() : null;

      if(summary ||
         desc != null && !processCalc &&
         (desc.getType() == TableDataDescriptor.GROUPED_TABLE ||
          desc.getType() == TableDataDescriptor.CROSSTAB_TABLE))
      {
         table = findSummaryTable(table);
      }

      if(table instanceof GroupedTable) {
         return getGroupedTableCells((GroupedTable) table, position);
      }
      else if(table instanceof CrossFilter) {
         return getCrosstabCells((CrossFilter) table, position);
      }
      else if(table instanceof RuntimeCalcTableLens) {
         return getCalcCells((RuntimeCalcTableLens) table, position);
      }
      else if(table instanceof DataTableLens) {
         // add the logic for the CubeFilter, when a crosstab binding a cube
         // data the tablelens should process as normal tablelens.
      }

      return getTableCells(table, position);
   }

   /**
    * Find the nested group or crosstab table.
    */
   private XTable findSummaryTable(XTable table) {
      XTable tbl = table;

      while(true) {
         if(tbl instanceof GroupedTable || tbl instanceof CrossTabFilter) {
            return tbl;
         }

         if(tbl instanceof TableFilter) {
            tbl = ((TableFilter) tbl).getTable();
         }
         else {
            break;
         }
      }

      return table;
   }

   /**
    * Adjust the row and column index when row/col is moved.
    * @param row row position before the insert/remove.
    * @param col column position before the insert/remove.
    * @param rdiff amount row is moved. Negative is moving up.
    * @param cdiff amount column is moved. Negative is moving left.
    */
   @Override
   public void adjustIndex(int row, int col, int rdiff, int cdiff) {
      // no adjustment for named range
   }

   /**
    * Get the column name or expression.
    */
   public String getColumn() {
      return colexpr;
   }

   /**
    * Check if the column is specified as an expression.
    */
   public boolean isExpression() {
      return expression;
   }

   /**
    * Get the condition string.
    */
   public String getCondition() {
      return condition;
   }

   /**
    * Resolve the variables and index in group specification.
    */
   public Map getRuntimeGroups() {
      Map<String, GroupSpec> groups = new HashMap<>(groupspecs);
      RuntimeCalcTableLens calc = null;
      XTable table = (XTable) FormulaContext.getTable();

      if(table instanceof RuntimeCalcTableLens) {
         calc = (RuntimeCalcTableLens) table;
      }

      for(String gname : groupspecs.keySet()) {
         GroupSpec spec = groupspecs.get(gname);

         if(spec.isByPosition()) {
            // positional reference only supported in calc table
            if(calc != null) {
               Point loc = FormulaContext.getCellLocation();
               CalcCellContext context = calc.getCellContext(loc.y, loc.x);

               if(context == null) {
                  LOG.warn(
                     "Hint: if you are trying to specify " +
                     "a range based on value, quote the number " +
                     "with single quotes so it's not treated " +
                     "as a group position.");
                  throw new RuntimeException("Reference in wrong context: " + range);
               }

               CalcCellContext.Group group = context.getGroup(gname);

               if(group == null) {
                  LOG.warn(
                     "Hint: if you are trying to specify " +
                     "a range based on value, quote the number " +
                     "with single quotes so it's not treated " +
                     "as a group position.");
                  throw new RuntimeException("Group is not in cell context: " +
                                             gname + " in " + range);
               }

               int idx = spec.getIndex();

               if(spec.isRelative()) {
                  idx += group.getPosition();
               }

               groups.put(gname, new GroupSpec(context, group, idx));
            }
         }
         else {
            Object value = spec.getValue();

            // process $name used as group value
            if(value instanceof String) {
               String str = (String) value;

               // named cell is only supported in calc table
               if(calc != null && str.startsWith("$")) {
                  CalcRef ref = new CalcRef(calc, str.substring(1));

                  groups.put(gname, new GroupSpec(ref.unwrap()));
               }
               else if(str.startsWith("=")) {
                  Object rc = FormulaEvaluator.exec(str.substring(1), null);

                  groups.put(gname, new GroupSpec(rc));
               }
            }
         }
      }

      return groups;
   }

   /**
    * Get all cells matching the range specification from the table.
    */
   private Collection getGroupedTableCells(GroupedTable table, boolean position) {
      Vector cells = new Vector();
      RangeSelector selector = null;
      int startRow = table.getHeaderRowCount();
      int endRow = table.getRowCount();

      if(endRow < 0) {
         endRow = Integer.MAX_VALUE;
      }

      if(summary) {
         selector = new SummaryRowSelector(-1);
      }
      else {
         selector = new DetailRowSelector(false, -1);
      }

      if(groupspecs.size() > 0) {
         Map specs = getRuntimeGroups();
         GroupRowSelector groupsel = new GroupRowSelector(table, specs);

         if(summary) {
            groupsel.setSummary(true);

            // set the summary level to include
            ((SummaryRowSelector) selector).setLevel(groupsel.getInnerLevel());
         }

         if(!groupsel.isWildCard()) {
            selector = new CompositeRangeSelector(selector, groupsel);
         }
      }

      TableRangeProcessor proc = new TableRangeProcessor(table, FormulaContext.getScope());

      proc.selectCells(cells, colexpr, expression, startRow, endRow, 1,
                       selector, condition, position);

      return cells;
   }

   /**
    * Get all cells matching the range specification from the table.
    * @param position true to return cell position (Point), false to return value.
    */
   private Collection getCrosstabCells(CrossFilter table, boolean position) {
      if(expression) {
         throw new RuntimeException("Expression reference not supported in crosstab: " + range);
      }

      Vector cells = new Vector();
      Map specs = getRuntimeGroups();
      RangeSelector selector = CrosstabGroupSelector.getSelector(
              Tool.replaceAll(colexpr, LayoutTool.SCRIPT_ESCAPED_COLON, ":"), table, specs);
      CrosstabRangeProcessor proc = new CrosstabRangeProcessor(table, FormulaContext.getScope());
      proc.setProcessCalc(processCalc);
      proc.selectCells(cells, selector, condition, position);

      return cells;
   }

   /**
    * Get all cells matching the range specification from the table.
    * @param position true to return cell position (Point), false to return value.
    */
   private Collection getCalcCells(RuntimeCalcTableLens table, boolean position) {
      Vector cells = new Vector();
      Map specs = getRuntimeGroups();
      RangeSelector selector = new CalcGroupSelector(table, specs);
      CalcRangeProcessor proc = new CalcRangeProcessor(table, FormulaContext.getScope());

      // if it's a calc cell reference, the colexpr is always an named cell
      // since $name notation does not allow expression to be used
      proc.selectCells(cells, colexpr, selector, condition, position);

      return cells;
   }

   /**
    * Get all cells matching the range specification from the table.
    * @param position true to return cell position (Point), false to return value.
    */
   private Collection getTableCells(XTable table, boolean position) {
      if(summary) {
         throw new RuntimeException("Summary reference is not supported in " +
                                    "non-grouped table: " + table.getClass());
      }

      Vector cells = new Vector();

      if(table != null) {
         CachedRowSelector selector = null;
         int startrow = table.getHeaderRowCount();
         int endrow = Integer.MAX_VALUE;

         try {
            if(groupspecs.size() > 0) {
               Map specs = getRuntimeGroups();
               //selector = new ValueRowSelector(table, specs);
               // optimization
               selector = CachedRowSelector.getSelector(table, specs);
               selector.prepare(specs);
               startrow = Math.max(startrow, selector.getStartRow());
               endrow = Math.min(endrow, selector.getEndRow() + 1);
            }

            TableRangeProcessor proc = new TableRangeProcessor(
               table, FormulaContext.getScope());

            proc.selectCells(cells, colexpr, expression, startrow, endrow, 1,
                             selector, condition, position);
         }
         finally {
            if(selector != null) {
               selector.endProcess();
            }
         }
      }

      return cells;
   }

   /**
    * Group value specification, an index or a group value.
    */
   static class GroupSpec {
      public GroupSpec(String str) {
         if(str.startsWith("\"") && str.endsWith("\"") ||
            str.startsWith("'") && str.endsWith("'"))
         {
            value = str.substring(1, str.length() - 1);
            byValue = true;
         }
         else {
            try {
               if(!str.isEmpty() && str.charAt(0) == '$') {
                  value = str;
                  byValue = true;
               }
               else {
                  boolean neg = str.startsWith("-");
                  boolean pos = str.startsWith("+");

                  index = Integer.parseInt(pos ? str.substring(1) : str);
                  value = str;
                  relative = neg || pos;
                  byPosition = true;
               }
            }
            catch(Exception ex) {
               value = str;
               byValue = true;
            }
         }
      }

      public GroupSpec(CalcCellContext context, CalcCellContext.Group group) {
         value = group.getValue(context);
         this.index = group.getPosition();
         // in CalcTable, using position is preferred. It's up to the selectors
         // to decide whether to use positional or value selection if both
         // are enabled
         byValue = true;
         byPosition = true;
      }

      public GroupSpec(CalcCellContext context, CalcCellContext.Group group,
                       int idx) {
         value = group.getValue(context, idx);
         this.index = idx;
         byValue = true;
         byPosition = true;
      }

      public GroupSpec(Object value) {
         this.value = value;
         byValue = true;
      }

      /**
       * Get the group value.
       */
      public Object getValue() {
         return value;
      }

      /**
       * Get the group index or relative position.
       */
      public int getIndex() {
         return index;
      }

      /**
       * Check if the index is a relative position.
       */
      public boolean isRelative() {
         return relative;
      }

      /**
       * Check if the group specification is by value.
       */
      public boolean isByValue() {
         return byValue;
      }

      /**
       * Check if the group specification is by index.
       */
      public boolean isByPosition() {
         return byPosition;
      }

      /**
       * Check if this is a wildcard specification.
       */
      public boolean isWildCard() {
         return "*".equals(value);
      }

      public String toString() {
         return "GroupSpec[" + value + "," + index + "," + relative + "," +
            byValue + "," + byPosition + "]";
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof GroupSpec)) {
            return false;
         }

         GroupSpec spec = (GroupSpec) obj;

         return Tool.equals(value, spec.value) &&
            index == spec.index &&
            relative == spec.relative &&
            byValue == spec.byValue &&
            byPosition == spec.byPosition;
      }

      private Object value = null; // group value
      private int index = 0; // index or offset
      private boolean relative = false;
      private boolean byValue = false;
      private boolean byPosition = false;
   }

   private String range;
   private String colexpr, condition;
   private boolean summary; // true if selecting summary cell only
   private boolean expression; // true if colexpr is an express
   private Map<String, GroupSpec> groupspecs = new HashMap<>(); // group -> GroupSpec

   private static final Logger LOG =
      LoggerFactory.getLogger(NamedCellRange.class);
}
