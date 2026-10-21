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
import inetsoft.report.script.viewsheet.TableDataVSAScriptable;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import inetsoft.util.CoreTool;
import inetsoft.util.script.graal.ScriptStopTestSupport;
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
class VSFormatTableLensFormatErrorTest {
   private static final Color RED = new Color(0xFF0000);

   @BeforeEach
   void setUp() {
      CoreTool.clearUserMessage();
      env = new GraalJavaScriptEnv();
      env.init();
   }

   @AfterEach
   void tearDown() {
      CoreTool.clearUserMessage();
   }

   @Test
   void namedCellResolvesInBackground() throws Exception {
      VSFormatTableLens lens = formatLens(null, "=$val > 15 ? 0xFF0000 : 0x00FF00");

      assertNotEquals(RED, lens.getBackground(1, 2));
      assertEquals(RED, lens.getBackground(2, 2));
   }

   @Test
   void failingForegroundDoesNotStopBackgroundAndIsReportedOnce() throws Exception {
      VSFormatTableLens lens = formatLens("=$nosuchcell + 1", "=$val > 15 ? 0xFF0000 : 0x00FF00");

      for(int i = 0; i < 3; i++) {
         for(int r = 1; r <= 3; r++) {
            lens.getForeground(r, 2);
            lens.getBackground(r, 2);
         }
      }

      assertEquals(RED, lens.getBackground(3, 2));
      String msg = CoreTool.getUserMessage().getMessage();
      int count = msg.split("table format expression error", -1).length - 1;
      assertEquals(1, count, msg);
   }

   @Test
   void syntaxErrorKeepsTheData() throws Exception {
      VSFormatTableLens lens = formatLens("=if(", null);

      assertDoesNotThrow(() -> lens.getForeground(1, 2));
      assertEquals(30.0, ((Number) lens.getObject(3, 2)).doubleValue());
   }

   @Test
   void scriptStopStillPropagates() throws Exception {
      String previous = ScriptStopTestSupport.setTimeout("1");

      try {
         VSFormatTableLens lens = formatLens("=while(true) {}; 0xFF0000", null);
         Throwable ex = assertThrows(Throwable.class, () -> lens.getForeground(1, 2));
         assertTrue(inetsoft.util.script.graal.ScriptTimeoutGuard.isStop(ex), ex.toString());

         // the row/col of the failed cell are thread-wide, they must not outlive it
         TableDataVSAScriptable scriptable =
            (TableDataVSAScriptable) lastBox.getScope().getVSAScriptable("Calc1");
         assertEquals(-1, scriptable.getRow());
         assertEquals(-1, scriptable.getCol());
      }
      finally {
         Thread.interrupted();
         ScriptStopTestSupport.setTimeout(previous);
      }
   }

   private VSFormatTableLens formatLens(String fg, String bg) {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly assembly = new CalcTableVSAssembly(vs, "Calc1");
      TableLayout layout = assembly.getTableLayout();
      layout.setColCount(3);

      for(int r = 0; r < 2; r++) {
         for(int c = 0; c < 3; c++) {
            layout.setCellBinding(r, c, new TableCellBinding(
               r == 0 ? CellBinding.BIND_TEXT : CellBinding.BIND_FORMULA, "x"));
         }
      }

      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getScriptEnv()).thenReturn(env);
      when(elem.getTableLayout()).thenReturn(layout);
      when(elem.getID()).thenReturn("Calc1");
      when(elem.getScriptTable()).thenReturn(new DefaultTableLens(new Object[][] {
         { "name", "value" }, { "a", 1 } }));

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

      if(fg != null) {
         vfmt.getUserDefinedFormat().setForegroundValue(fg);
      }

      if(bg != null) {
         vfmt.getUserDefinedFormat().setBackgroundValue(bg);
      }

      TableDataPath path = ((CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo()).getCellDataPath(1, 2);
      assembly.getFormatInfo().setFormat(path, vfmt);
      vs.addAssembly(assembly);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "Fmt78493b", null);
      ViewsheetSandbox box = new ViewsheetSandbox(
         vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      lastBox = box;
      return new VSFormatTableLens(box, "Calc1", runtime, false);
   }

   private GraalJavaScriptEnv env;
   private ViewsheetSandbox lastBox;
}
