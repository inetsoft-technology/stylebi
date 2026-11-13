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
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.execution.VSFormatTableLens;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.viewsheet.*;
import inetsoft.report.TableDataPath;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.text.Format;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79580: ExportUtil.getCellFormat skipped the unwrap hop for column 0, so a formatted
 * number/date in the first column was exported as the display text instead of a numeric cell.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExportUtilCellFormatTest {
   private static final String PERCENT_PATTERN = "#,##0%";
   private static final String DATE_PATTERN = "yyyy/MM/dd";

   /**
    * Base lens with a header row and two data rows; columns 0/1 percent, 2/3 date, 4 string.
    * The default format is what the format lens turns into the display text.
    */
   private static class FormattedLens extends DefaultTableLens {
      FormattedLens() {
         super(3, 5);
         setHeaderRowCount(1);
         java.sql.Date d = java.sql.Date.valueOf("2023-11-14");

         for(int c = 0; c < 5; c++) {
            setObject(0, c, "h" + c);
         }

         setObject(1, 0, 0.91);
         setObject(1, 1, 0.91);
         setObject(1, 2, d);
         setObject(1, 3, d);
         setObject(1, 4, "007");
         setObject(2, 0, 1.4);
         setObject(2, 1, 1.4);
         setObject(2, 2, d);
         setObject(2, 3, d);
         setObject(2, 4, "008");
      }

      @Override
      public Class<?> getColType(int col) {
         switch(col) {
         case 0:
         case 1:
            return Double.class;
         case 2:
         case 3:
            return java.sql.Date.class;
         default:
            return String.class;
         }
      }

      @Override
      public Format getDefaultFormat(TableDataPath path) {
         if(path == null || path.getType() != TableDataPath.DETAIL ||
            path.getPath().length == 0)
         {
            return null;
         }

         int col = Integer.parseInt(path.getPath()[path.getPath().length - 1].substring(1));

         switch(col) {
         case 0:
         case 1:
            return NumberFormat.getPercentInstance();
         case 2:
         case 3:
            return new SimpleDateFormat(DATE_PATTERN);
         default:
            return null;
         }
      }
   }

   private VSTableLens lens;

   private static ViewsheetSandbox box(String name) {
      Viewsheet vs = mock(Viewsheet.class);
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      when(assembly.getName()).thenReturn(name);
      when(assembly.getFormatInfo()).thenReturn(new FormatInfo());
      when(assembly.getColumnSelection()).thenReturn(new ColumnSelection());
      when(vs.getAssembly(name)).thenReturn(assembly);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getScope()).thenReturn(mock(ViewsheetScope.class));
      return box;
   }

   @BeforeEach
   void setUp() {
      // tfmt=true is what DataVSAQuery passes for table data
      VSFormatTableLens format =
         new VSFormatTableLens(box("Table1"), "Table1", new FormattedLens(), true);
      lens = new VSTableLens(format);
   }

   private String pattern(int r, int c) {
      return ExportUtil.getCellFormat(lens, r, c, true, false);
   }

   private Object value(int r, int c) {
      return ExportUtil.getObject(lens, r, c, pattern(r, c), true);
   }

   @Test
   void percentInFirstColumnMatchesSecondColumn() {
      for(int r = 1; r <= 2; r++) {
         assertEquals(PERCENT_PATTERN, pattern(r, 1));
         assertEquals(pattern(r, 1), pattern(r, 0));
         assertInstanceOf(Double.class, value(r, 1));
         assertInstanceOf(Double.class, value(r, 0));
      }

      assertEquals(0.91, (Double) value(1, 0), 1e-9);
      assertEquals(1.4, (Double) value(2, 0), 1e-9);
   }

   @Test
   void dateInColumnZeroIsDateCell() {
      DefaultTableLens base = new DefaultTableLens(2, 2) {
         @Override
         public Class<?> getColType(int col) {
            return java.sql.Date.class;
         }

         @Override
         public Format getDefaultFormat(TableDataPath path) {
            return path != null && path.getType() == TableDataPath.DETAIL ?
               new SimpleDateFormat(DATE_PATTERN) : null;
         }
      };
      base.setHeaderRowCount(1);
      base.setObject(0, 0, "h0");
      base.setObject(0, 1, "h1");
      base.setObject(1, 0, java.sql.Date.valueOf("2023-11-14"));
      base.setObject(1, 1, java.sql.Date.valueOf("2023-11-14"));
      VSTableLens dateLens = new VSTableLens(new VSFormatTableLens(box("Table2"), "Table2", base, true));

      String p0 = ExportUtil.getCellFormat(dateLens, 1, 0, true, false);
      String p1 = ExportUtil.getCellFormat(dateLens, 1, 1, true, false);
      assertEquals(DATE_PATTERN, p0);
      assertEquals(p1, p0);
      assertInstanceOf(java.util.Date.class, ExportUtil.getObject(dateLens, 1, 0, p0, true));
   }

   @Test
   void stringFirstColumnKeepsValue() {
      DefaultTableLens base = new DefaultTableLens(2, 2) {
         @Override
         public Class<?> getColType(int col) {
            return String.class;
         }
      };
      base.setHeaderRowCount(1);
      base.setObject(0, 0, "h0");
      base.setObject(0, 1, "h1");
      base.setObject(1, 0, "007");
      base.setObject(1, 1, "007");
      VSTableLens strLens = new VSTableLens(new VSFormatTableLens(box("Table3"), "Table3", base, true));

      // column 0 now gets the same text pattern as column 1; the value is unchanged
      assertEquals(ExportUtil.getCellFormat(strLens, 1, 1, true, false),
                   ExportUtil.getCellFormat(strLens, 1, 0, true, false));
      assertEquals("007", ExportUtil.getObject(strLens, 1, 0, "@", true));
   }

   // Bug #80103, the K/M/B pattern follows the unit the dashboard shows for each cell value
   @Test
   void extendedDecimalPatternFollowsDisplayedUnitPerCell() {
      double[] values = { 500, 20250, 317062.69, 2300872.18 };
      DefaultTableLens base = new DefaultTableLens(values.length + 1, 1) {
         @Override
         public Class<?> getColType(int col) {
            return Double.class;
         }

         @Override
         public Format getDefaultFormat(TableDataPath path) {
            return path != null && path.getType() == TableDataPath.DETAIL ?
               new inetsoft.util.ExtendedDecimalFormat("#,##0.#M",
                  new java.text.DecimalFormatSymbols(java.util.Locale.US)) : null;
         }
      };
      base.setHeaderRowCount(1);
      base.setObject(0, 0, "h0");

      for(int i = 0; i < values.length; i++) {
         base.setObject(i + 1, 0, values[i]);
      }

      VSTableLens mlens =
         new VSTableLens(new VSFormatTableLens(box("Table4"), "Table4", base, true));
      assertEquals("#,##0.#", ExportUtil.getCellFormat(mlens, 1, 0, true, false));
      assertEquals("#,##0,.#\"K\"", ExportUtil.getCellFormat(mlens, 2, 0, true, false));
      assertEquals("#,##0,.#\"K\"", ExportUtil.getCellFormat(mlens, 3, 0, true, false));
      assertEquals("#,##0,,.#\"M\"", ExportUtil.getCellFormat(mlens, 4, 0, true, false));
      // the cell keeps the raw number
      assertEquals(317062.69, (Double) ExportUtil.getObject(
         mlens, 3, 0, ExportUtil.getCellFormat(mlens, 3, 0, true, false), true), 1e-9);
   }

   @Test
   void headerRowStaysUnformatted() {
      for(int c = 0; c < 5; c++) {
         assertNull(pattern(0, c), "header cell column " + c);
         assertEquals("h" + c, value(0, c));
      }
   }

   /**
    * A runtime calc table with a header row, two numeric rows and a total label row. Its own
    * trailer row count is 0; the script table it was built from has {@code scriptTrailer}.
    */
   private static RuntimeCalcTableLens runtimeCalc(Integer scriptTrailer) {
      CalcTableLens calc = new CalcTableLens(new Object[][] {
         { "h" }, { 1 }, { 2 }, { "Total" } });
      calc.setHeaderRowCount(1);

      if(scriptTrailer != null) {
         DefaultTableLens script = new DefaultTableLens(new Object[][] {
            { "h" }, { 1 }, { 2 }, { "Total" } });
         script.setHeaderRowCount(1);
         script.setTrailerRowCount(scriptTrailer);
         calc.setScriptTable(script);
      }

      return new RuntimeCalcTableLens(calc);
   }

   private static VSTableLens formatted(RuntimeCalcTableLens runtime, String name) {
      return new VSTableLens(new VSFormatTableLens(box(name), name, runtime, true));
   }

   private static Class<?> colType(VSTableLens lens, int col) throws Exception {
      Method method = ExportUtil.class.getDeclaredMethod("getColType",
                                                         inetsoft.uql.XTable.class, int.class);
      method.setAccessible(true);
      return (Class<?>) method.invoke(null, lens, col);
   }

   @Test
   void columnTypeOfARuntimeCalcTableIgnoresTheTotalLabelRowOfItsScriptTable() throws Exception {
      RuntimeCalcTableLens runtime = runtimeCalc(1);

      assertEquals(0, runtime.getTrailerRowCount());
      // the data rows are numbers; the last row is the label of the total
      assertEquals(Integer.class, colType(formatted(runtime, "Calc1"), 0));
   }

   @Test
   void columnTypeLookupLeavesTheTrailerRowCountOfTheRuntimeTableUnchanged() throws Exception {
      RuntimeCalcTableLens runtime = runtimeCalc(1);

      colType(formatted(runtime, "Calc2"), 0);

      assertEquals(0, runtime.getTrailerRowCount());
   }

   @Test
   void columnTypeOfARuntimeCalcTableWithoutScriptTableIsTheTypeOfTheLens() throws Exception {
      RuntimeCalcTableLens runtime = runtimeCalc(null);

      assertEquals(runtime.getColType(0), colType(formatted(runtime, "Calc3"), 0));
   }
}
