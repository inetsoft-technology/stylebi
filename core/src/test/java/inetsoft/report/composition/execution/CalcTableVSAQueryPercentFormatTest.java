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
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78092: a freehand table summary cell with a percentage (e.g. {@code Sum<16>}) must get
 * the percent format by default, not the source column's format. The query is driven through
 * the real {@link CalcTableVSAQuery#getTableLens()}; only the base-table fetch is replaced.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalcTableVSAQueryPercentFormatTest {
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "Sum<16>", "Sum<4>", "Count<16>", "DistinctCount<16>" })
   void percentageSummaryGetsPercentFormat(String formula) throws Exception {
      TableLens lens = runQuery(formula);

      for(int r = 0; r < 3; r++) {
         XFormatInfo finfo = getFormat(lens, r);
         assertNotNull(finfo, "row " + r);
         assertEquals(TableFormat.PERCENT_FORMAT, finfo.getFormat(), "row " + r);
      }
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "Sum", "Correlation<-1>(Year)" })
   void nonPercentageSummaryKeepsColumnFormat(String formula) throws Exception {
      TableLens lens = runQuery(formula);

      for(int r = 0; r < 3; r++) {
         XFormatInfo finfo = getFormat(lens, r);
         assertNotNull(finfo, "row " + r);
         assertEquals(TableFormat.DECIMAL_FORMAT, finfo.getFormat(), "row " + r);
         assertEquals(COLUMN_FORMAT, finfo.getFormatSpec(), "row " + r);
      }
   }

   private static XFormatInfo getFormat(TableLens lens, int r) {
      TableDataDescriptor desc = lens.getDescriptor();
      XMetaInfo minfo = desc.getXMetaInfo(desc.getCellDataPath(r, 1));
      assertNotNull(minfo, "row " + r + " meta");
      return minfo.getXFormatInfo();
   }

   private static TableLens runQuery(String formula) throws Exception {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(cassembly);

      TableLayout layout = cassembly.getTableLayout();
      TableCellBinding group = TableCellBinding.getGroupBinding("Year");
      group.setExpansion(GroupableCellBinding.EXPAND_V);
      layout.setCellBinding(0, 0, group);
      TableCellBinding summary = new TableCellBinding(CellBinding.BIND_COLUMN, "Total");
      summary.setBType(CellBinding.SUMMARY);
      summary.setFormula(formula);
      layout.setCellBinding(0, 1, summary);

      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "Year", "Total" }, { 2020, 100.0 }, { 2021, 200.0 }, { 2022, 150.0 } });
      XMetaInfo minfo = new XMetaInfo();
      minfo.setXFormatInfo(new XFormatInfo(TableFormat.DECIMAL_FORMAT, COLUMN_FORMAT));

      for(int r = 1; r < data.getRowCount(); r++) {
         data.setXMetaInfo(r, 1, minfo);
      }

      ViewsheetSandbox box = mock(ViewsheetSandbox.class, RETURNS_DEEP_STUBS);
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      when(box.getID()).thenReturn("vs1");
      when(box.getScope().getScriptEnv()).thenReturn(env);
      TableAssembly table = mock(TableAssembly.class);

      CalcTableVSAQuery query = new CalcTableVSAQuery(box, "Calc1", false) {
         @Override
         public TableAssembly getTableAssembly() {
            return table;
         }

         @Override
         protected TableLens getTableLens(TableAssembly table) {
            return data;
         }
      };

      TableLens lens = query.getTableLens();
      assertNotNull(lens);
      // one row per year, then the second row of the default 2x2 layout
      assertTrue(lens.getRowCount() >= 3, "rows: " + lens.getRowCount());
      return lens;
   }

   // the Orders model's Total format, which renders a share as "$0"
   private static final String COLUMN_FORMAT = "$#,##0";
}
