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
package inetsoft.report.composition.execution;

import inetsoft.report.*;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.viewsheet.CalcTableVSAScriptable;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78493: a named cell ($name) of a freehand table used in a cell's format expression was
 * a ReferenceError, and the error aborted the whole table. The named cell must resolve, and a
 * failing format expression must only lose its own property.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSFormatTableLensCalcRefTest {
   private static final Color RED = new Color(0xFF0000);
   private static final Color GREEN = new Color(0x00FF00);

   @BeforeEach
   void setUp() {
      env = new GraalJavaScriptEnv();
      env.init();
   }

   @Test
   void namedCellResolvesInFormatExpression() throws Exception {
      VSFormatTableLens lens = formatLens("=$val > 15 ? 0xFF0000 : 0x00FF00");

      assertEquals(GREEN, lens.getForeground(1, 2));
      assertEquals(RED, lens.getForeground(2, 2));
      assertEquals(RED, lens.getForeground(3, 2));
   }

   @Test
   void rowAndColStillComeFromTheAssembly() throws Exception {
      // row/col must not be shadowed by the table scope's own row
      VSFormatTableLens lens = formatLens("=row == 2 && col == 2 ? 0xFF0000 : 0x00FF00");

      assertEquals(GREEN, lens.getForeground(1, 2));
      assertEquals(RED, lens.getForeground(2, 2));
      assertEquals(GREEN, lens.getForeground(3, 2));
   }

   @Test
   void failingFormatExpressionKeepsTheData() throws Exception {
      VSFormatTableLens lens = formatLens("=$nosuchcell > 15 ? 0xFF0000 : 0x00FF00");

      for(int r = 1; r <= 3; r++) {
         // no exception, the data is still readable, and the default format is used
         assertDoesNotThrow(() -> lens.getForeground(1, 2));
         assertNotEquals(RED, lens.getForeground(r, 2));
         assertNotEquals(GREEN, lens.getForeground(r, 2));
      }

      assertEquals(30.0, ((Number) lens.getObject(3, 2)).doubleValue());
   }

   @Test
   void failureDoesNotLeakThePreviousCellValue() throws Exception {
      // fails only for the last row; the value computed for the row before must not stick
      VSFormatTableLens lens = formatLens(
         "=row == 3 ? $nosuchcell : (row == 2 ? 0xFF0000 : 0x00FF00)");

      assertEquals(RED, lens.getForeground(2, 2));
      Color failed = lens.getForeground(3, 2);
      assertNotEquals(RED, failed);
      assertNotEquals(GREEN, failed);
   }

   private VSFormatTableLens formatLens(String foreground) {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly assembly = new CalcTableVSAssembly(vs, "Calc1");
      TableLayout layout = assembly.getTableLayout();
      layout.setColCount(3);

      for(int r = 0; r < 2; r++) {
         for(int c = 0; c < 3; c++) {
            TableCellBinding cell = new TableCellBinding(
               r == 0 ? CellBinding.BIND_TEXT : CellBinding.BIND_FORMULA, "x");
            layout.setCellBinding(r, c, cell);
         }
      }

      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getScriptEnv()).thenReturn(env);
      when(elem.getTableLayout()).thenReturn(layout);
      when(elem.getID()).thenReturn("Calc1");
      when(elem.getScriptTable()).thenReturn(new DefaultTableLens(new Object[][] {
         { "name", "value" }, { "a", 1 } }));

      // layout row 0 is the header; row 1: n = [1,2,3] (expand down), val = $n * 10, $val
      CalcTableLens calc = new CalcTableLens(2, 3);
      calc.setElement(elem);
      calc.setObject(0, 0, "n");
      calc.setObject(0, 1, "v");
      calc.setObject(0, 2, "v2");
      calc.setObject(1, 0, new CalcTableLens.Formula("[1, 2, 3]"));
      calc.setExpansion(1, 0, CalcTableLens.EXPAND_VERTICAL);
      calc.setCellName(1, 0, "n");
      calc.setObject(1, 1, new CalcTableLens.Formula("$n * 10"));
      calc.setCellName(1, 1, "val");
      calc.setObject(1, 2, new CalcTableLens.Formula("$val"));
      RuntimeCalcTableLens runtime = calc.process();

      VSCompositeFormat vfmt = new VSCompositeFormat();
      vfmt.getUserDefinedFormat().setForegroundValue(foreground);
      TableDataPath path = ((inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo()).getCellDataPath(1, 2);
      assembly.getFormatInfo().setFormat(path, vfmt);
      vs.addAssembly(assembly);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "Fmt78493", null);
      ViewsheetSandbox box = new ViewsheetSandbox(
         vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      assertNotNull(box.getScope().getVSAScriptable("Calc1"));
      return new VSFormatTableLens(box, "Calc1", runtime, false);
   }

   private GraalJavaScriptEnv env;
}
