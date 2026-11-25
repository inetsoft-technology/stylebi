/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.report.filter;

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.graph.calc.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;

import java.util.*;

/** Helpers shared by the crosstab filter test classes of this package. */
final class CrossTabFilterTestSupport {
   // ------------------------------------------------------------------------------------
   // Shared helpers of the groups below: a crosstab wired as the viewsheet query wires it,
   // read back as (row tuple, column tuple, value) and compared with loops over the base rows.
   // ------------------------------------------------------------------------------------

   /** One cell of the crosstab body. */
   record Cell(Object[] rowKey, Object[] colKey, String measure, Object value) {
      @Override
      public String toString() {
         return "row=" + Arrays.toString(rowKey) + " col=" + Arrays.toString(colKey) + " " +
            measure + "=" + value;
      }
   }

   /** The body cells of a crosstab, with the tuple of their row and column and measure name. */
   static List<Cell> readCells(CrossTabFilter f) {
      List<Cell> cells = new ArrayList<>();
      TableDataDescriptor desc = f.getDescriptor();

      for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
         for(int c = f.getHeaderColCount(); c < f.getColCount(); c++) {
            TableDataPath path = desc.getCellDataPath(r, c);
            String measure = path == null || path.getPath().length == 0 ? null :
               path.getPath()[path.getPath().length - 1];

            if(measure == null) {
               continue;
            }

            cells.add(new Cell(f.getRowTuple(r).getRow(), f.getColTuple(c).getRow(), measure,
                               f.getObject(r, c)));
         }
      }

      return cells;
   }

   /** The base rows whose first dims columns equal the key (a shorter key matches a group). */
   static List<Object[]> matching(Object[][] data, int[] rowCols, Object[] rowKey,
                                          int[] colCols, Object[] colKey)
   {
      List<Object[]> list = new ArrayList<>();

      for(int i = 1; i < data.length; i++) {
         if(keyMatches(data[i], rowCols, rowKey) && keyMatches(data[i], colCols, colKey)) {
            list.add(data[i]);
         }
      }

      return list;
   }

   static boolean keyMatches(Object[] row, int[] cols, Object[] key) {
      for(int i = 0; i < key.length; i++) {
         if(!Objects.equals(row[cols[i]], key[i])) {
            return false;
         }
      }

      return true;
   }

   static double sumOf(List<Object[]> rows, int col) {
      return rows.stream().filter(r -> r[col] != null)
         .mapToDouble(r -> ((Number) r[col]).doubleValue()).sum();
   }

   /** A crosstab with every group total and both grand totals shown, side by side. */
   static CrossTabFilter crosstab(Object[][] data, int[] rowCols, int[] colCols,
                                          int[] measureCols, Formula[] formulas)
   {
      CrossTabFilter f = new CrossTabFilter(new DefaultTableLens(data), rowCols, colCols,
                                            measureCols, formulas);
      List<String> rowNames = new ArrayList<>();
      List<String> colNames = new ArrayList<>();

      for(int c : rowCols) {
         rowNames.add(String.valueOf(data[0][c]));
      }

      for(int c : colCols) {
         colNames.add(String.valueOf(data[0][c]));
      }

      f.setRowHeaders(rowNames);
      f.setColHeaders(colNames);

      for(int level = 0; level < rowCols.length - 1; level++) {
         f.setSuppressRowGroupTotal(false, level);
      }

      for(int level = 0; level < colCols.length - 1; level++) {
         f.setSuppressColumnGroupTotal(false, level);
      }

      f.setSuppressRowGrandTotal(false);
      f.setSuppressColumnGrandTotal(false);
      f.setSummarySideBySide(true);
      return f;
   }

   static boolean sameNumber(Object expected, Object actual) {
      if(expected == null) {
         return actual == null || "".equals(actual) ||
            actual instanceof Number && ((Number) actual).doubleValue() == 0;
      }

      return actual instanceof Number && Math.abs(
         ((Number) actual).doubleValue() - ((Number) expected).doubleValue()) < 1e-9;
   }

   static Object sumPercent(int percentageType) {
      SumFormula formula = new SumFormula();
      formula.setPercentageType(percentageType);
      return formula;
   }


}
