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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.internal.table.TableTool;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.script.ScriptException;
import inetsoft.web.wiz.binding.CalcTableService;

import java.util.Optional;

/**
 * Reads a table's rendered {@link VSTableLens} for the wiz writes that address a column, header
 * or cell by what it renders as, and turns "the table did not render" into a named 400.
 *
 * <p>A table whose script or condition throws while it loads has no lens. The sandbox reports that
 * two ways: the first read after the table was last cleared rethrows the {@link ScriptException},
 * and every later read returns {@code null} (the failed load is cached as NULL until the data map
 * is cleared). Dereferenced as-is the first is a raw 500 and the second an NPE or an
 * {@code IllegalStateException}, all of which {@code WizControllerErrorHandler} masks as an
 * unexpected server error; an {@link IllegalArgumentException} is the one it relays as a 400 with
 * its message.
 */
public final class TableLensGuard {
   private TableLensGuard() {
   }

   /**
    * @param displayName what the agent called the assembly, for the message.
    * @param purpose     what could not be done without the lens, e.g. {@code "its columns cannot
    *                    be resolved"}.
    *
    * @return the rendered lens, never {@code null}.
    *
    * @throws IllegalArgumentException when there is no sandbox, or the table did not render
    *         because of a {@link ScriptException} or a cached failed load. Any other failure is
    *         rethrown unchanged so it keeps its own handling.
    */
   public static VSTableLens requireRenderedLens(RuntimeViewsheet rvs, VSAssembly assembly,
                                                 String displayName, String purpose)
      throws Exception
   {
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box == null || box.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + displayName + "' has no active render sandbox right now, so " + purpose + ".");
      }

      String oname = assembly.getAbsoluteName();
      boolean detail = oname.startsWith(Assembly.DETAIL);

      if(detail) {
         oname = oname.substring(Assembly.DETAIL.length());
      }

      ScriptException failure = null;
      VSTableLens lens = null;

      try {
         lens = box.get().getVSTableLens(oname, detail);
      }
      catch(Exception e) {
         failure = scriptFailure(e);

         if(failure == null) {
            throw e;
         }
      }

      if(lens != null) {
         return lens;
      }

      throw new IllegalArgumentException(
         "'" + displayName + "' did not render" +
         (failure == null
            ? " (its last load failed, e.g. on a script or condition error)"
            : " (its script failed while the table loaded: " + failure.getMessage() + ")") +
         ", so " + purpose + ". Fix the table's script or conditions and retry.");
   }

   /** A cell of a table's rendered lens. */
   public record Cell(int row, int col) {}

   /**
    * The rendered cell a highlight or hyperlink at {@code (row, col)} addresses, in the lens
    * coordinates {@code HighlightDialogService}/{@code HyperlinkDialogService} take (bug #78719).
    *
    * <p>Those services trust row/col because the Composer only sends a clicked cell. Out of range,
    * the lens's descriptor returns a null path (a plain table's column past the end, which every
    * such column then shares as one stored slot), the trailer path (a row past the end), a
    * synthetic {@code Cell [r,c]} no rendered cell has (a calc table), or throws (a negative
    * index). So an out-of-range cell is refused here, naming the valid range.
    *
    * <p>A plain table or crosstab is addressed by its rendered (runtime) row/col. A freehand
    * table is addressed by LAYOUT row/col, as {@code get_calc_layout} lists them and every other
    * wiz calc-cell route takes them ({@code CalcTableService.requireInGrid}). The native services
    * take a runtime cell and store the result under that cell's layout path, so a layout cell is
    * translated to its first runtime instance; on a table whose groups expand the two differ,
    * and passing the layout cell through would write onto whichever layout cell renders there.
    * A layout cell with no runtime instance right now (a group that expands to no values) is
    * refused by name.
    */
   public static Cell requireRenderedCell(RuntimeViewsheet rvs, TableDataVSAssembly assembly,
                                          String displayName, int row, int col, String purpose)
      throws Exception
   {
      if(assembly instanceof CalcTableVSAssembly calc && calc.getTableLayout() != null) {
         CalcTableService.requireInGrid(calc.getTableLayout(), row, col);
      }

      VSTableLens lens = requireRenderedLens(rvs, assembly, displayName, purpose);
      RuntimeCalcTableLens runtime = assembly instanceof CalcTableVSAssembly
         ? (RuntimeCalcTableLens) Util.getNestedTable(lens, RuntimeCalcTableLens.class) : null;

      if(runtime != null) {
         return runtimeCell(lens, runtime, displayName, row, col, purpose);
      }

      if(row < 0 || col < 0 || col >= lens.getColCount() || !lens.moreRows(row)) {
         int rows = lens.getRowCount();
         String rowRange = rows < 0 ? "at least " + (-rows - 1) : String.valueOf(rows);

         throw new IllegalArgumentException(
            "Cell [" + row + "," + col + "] is outside '" + displayName + "', which renders " +
            rowRange + " row(s) by " + lens.getColCount() + " column(s) (row and col count " +
            "from 0, header rows included), so " + purpose + ". Pass the row/col of a rendered " +
            "cell.");
      }

      return new Cell(row, col);
   }

   /** The first runtime instance of a freehand table's layout cell. */
   private static Cell runtimeCell(VSTableLens lens, RuntimeCalcTableLens runtime,
                                   String displayName, int row, int col, String purpose)
   {
      int runtimeRow = -1;
      int runtimeCol = -1;

      for(int r = 0; runtimeRow < 0 && lens.moreRows(r); r++) {
         int base = TableTool.getBaseRowIndex(lens, runtime, r);

         if(base >= 0 && runtime.getRow(base) == row) {
            runtimeRow = r;
         }
      }

      for(int c = 0; runtimeCol < 0 && c < lens.getColCount(); c++) {
         int base = TableTool.getBaseColIndex(lens, runtime, c);

         if(base >= 0 && runtime.getCol(base) == col) {
            runtimeCol = c;
         }
      }

      if(runtimeRow < 0 || runtimeCol < 0) {
         throw new IllegalArgumentException(
            "Cell [" + row + "," + col + "] of '" + displayName + "' is in the layout but " +
            "renders nowhere right now: its " + (runtimeRow < 0 ? "row" : "column") +
            " is a group that expands to no values, so " + purpose + ". Pick a layout cell " +
            "whose row and column render, or retry once the group has data.");
      }

      return new Cell(runtimeRow, runtimeCol);
   }

   /**
    * The lens, or {@code null} when the table did not render -- including a {@link
    * ScriptException} on the first read, so a caller that already has a {@code null} branch
    * handles both outcomes the same way. Any other failure is rethrown.
    */
   public static VSTableLens lensOrNull(ViewsheetSandbox box, String name, boolean detail)
      throws Exception
   {
      try {
         return box.getVSTableLens(name, detail);
      }
      catch(Exception e) {
         if(scriptFailure(e) == null) {
            throw e;
         }

         return null;
      }
   }

   private static ScriptException scriptFailure(Throwable error) {
      for(Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
         if(t instanceof ScriptException script) {
            return script;
         }
      }

      return null;
   }
}
