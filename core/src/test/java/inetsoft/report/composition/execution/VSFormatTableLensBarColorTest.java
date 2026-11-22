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
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.painter.Bar2Presenter;
import inetsoft.report.painter.BarPresenter;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A bar presenter with no colour paints in the cell's text colour, which is near-black. On a modern
 * table it takes the data bar colour instead; the author's colour and a classic table are kept.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSFormatTableLensBarColorTest {
   private static final String BAR = "inetsoft.report.painter.BarPresenter";
   private static final String BAR2 = "inetsoft.report.painter.Bar2Presenter";
   private static final Color AUTHOR = new Color(0x123456);

   @BeforeEach
   void setUp() {
      env = new GraalJavaScriptEnv();
      env.init();
   }

   @Test
   void aModernTableBarTakesTheDataBarColour() throws Exception {
      Presenter presenter = lens(BAR, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      assertEquals(VSObjectChromeDefaults.dataBarColor(VizContext.of(VizMark.MODERN_LIGHT)),
                   ((BarPresenter) presenter).getColor());
   }

   @Test
   void aDarkModernTableBarTakesTheDarkColour() throws Exception {
      Presenter presenter = lens(BAR, VizMark.MODERN_DARK).getPresenter(1, 1);

      assertEquals(VSObjectChromeDefaults.dataBarColor(VizContext.of(VizMark.MODERN_DARK)),
                   ((BarPresenter) presenter).getColor());
   }

   @Test
   void aModernTableSignedBarTakesIt() throws Exception {
      Presenter presenter = lens(BAR2, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      assertEquals(VSObjectChromeDefaults.dataBarColor(VizContext.of(VizMark.MODERN_LIGHT)),
                   ((Bar2Presenter) presenter).getColor());
   }

   @Test
   void aClassicTableBarStillFollowsTheTextColour() throws Exception {
      Presenter presenter = lens(BAR, null).getPresenter(1, 1);

      assertNull(((BarPresenter) presenter).getColor());
   }

   @Test
   void anAuthorColourIsKept() throws Exception {
      PresenterRef ref = new PresenterRef(BAR);
      ref.setParameter("color", AUTHOR);
      Presenter presenter = lens(ref, VizMark.MODERN_LIGHT).getPresenter(1, 1);

      assertEquals(AUTHOR, ((BarPresenter) presenter).getColor());
   }

   private VSFormatTableLens lens(String presenter, VizMark mark) {
      return lens(new PresenterRef(presenter), mark);
   }

   private VSFormatTableLens lens(PresenterRef ref, VizMark mark) {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly assembly = new CalcTableVSAssembly(vs, "Calc1");
      assembly.getVSAssemblyInfo().setVizMark(mark);
      TableLayout layout = assembly.getTableLayout();
      layout.setColCount(2);

      for(int r = 0; r < 2; r++) {
         for(int c = 0; c < 2; c++) {
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

      CalcTableLens calc = new CalcTableLens(2, 2);
      calc.setElement(elem);
      calc.setObject(0, 0, "n");
      calc.setObject(0, 1, "v");
      calc.setObject(1, 0, new CalcTableLens.Formula("1"));
      calc.setObject(1, 1, new CalcTableLens.Formula("5"));
      RuntimeCalcTableLens runtime = calc.process();

      VSCompositeFormat vfmt = new VSCompositeFormat();
      vfmt.getUserDefinedFormat().setPresenterValue(ref);
      TableDataPath path =
         ((CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo()).getCellDataPath(1, 1);
      assembly.getFormatInfo().setFormat(path, vfmt);
      vs.addAssembly(assembly);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "BarColor", null);
      ViewsheetSandbox box = new ViewsheetSandbox(
         vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      return new VSFormatTableLens(box, "Calc1", runtime, false);
   }

   private GraalJavaScriptEnv env;
}
