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
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.report.lens.DefaultTableLens;
import java.util.concurrent.atomic.AtomicInteger;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.VSLayoutTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78405: a calc table whose layout has no column/formula cell must not run the raw base
 * table query when it is evaluated (set_table_source on a slow source timed out).
 * {@link CalcTableVSAQuery#getTableLens()} takes the no-base-table path exactly when
 * {@code needsBaseData(layout)} is false (and the table is not a detail table).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalcTableVSAQueryNeedsBaseDataTest {
   @Test
   void defaultLayoutNeedsNoBaseData() {
      assertFalse(CalcTableVSAQuery.needsBaseData(VSLayoutTool.createDefaultLayout()));
   }

   @Test
   void textOnlyLayoutNeedsNoBaseData() {
      TableLayout layout = VSLayoutTool.createDefaultLayout();
      TableCellBinding text = new TableCellBinding(CellBinding.BIND_TEXT, "hello");
      layout.setCellBinding(0, 0, text);
      layout.setCellBinding(1, 1, new TableCellBinding(CellBinding.BIND_TEXT, "world"));
      assertFalse(CalcTableVSAQuery.needsBaseData(layout));
   }

   @Test
   void detailColumnNeedsBaseData() {
      TableLayout layout = VSLayoutTool.createDefaultLayout();
      layout.setCellBinding(1, 1, TableCellBinding.getDetailBinding("Name"));
      assertTrue(CalcTableVSAQuery.needsBaseData(layout));
   }

   @Test
   void groupColumnNeedsBaseData() {
      TableLayout layout = VSLayoutTool.createDefaultLayout();
      layout.setCellBinding(1, 0, TableCellBinding.getGroupBinding("Region"));
      assertTrue(CalcTableVSAQuery.needsBaseData(layout));
   }

   @Test
   void summaryColumnNeedsBaseData() {
      TableLayout layout = VSLayoutTool.createDefaultLayout();
      layout.setCellBinding(1, 1, TableCellBinding.getSummaryBinding("Total"));
      assertTrue(CalcTableVSAQuery.needsBaseData(layout));
   }

   @Test
   void formulaNeedsBaseData() {
      TableLayout layout = VSLayoutTool.createDefaultLayout();
      layout.setCellBinding(1, 1, new TableCellBinding(CellBinding.BIND_FORMULA, "field['a']"));
      assertTrue(CalcTableVSAQuery.needsBaseData(layout));
   }

   @Test
   void missingLayoutNeedsBaseData() {
      assertTrue(CalcTableVSAQuery.needsBaseData(null));
   }

   @Test
   void noBaseTablePathYieldsProcessedLensLikeUnsourcedTable() {
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(new Viewsheet(), "Calc1");
      VSLayoutTool.createCalcLens(cassembly, null, new VariableTable(), false);
      CalcTableLens clens = (CalcTableLens) cassembly.getBaseTable();
      RuntimeCalcTableLens lens = clens.process();

      assertNotNull(lens);
      assertEquals(2, lens.getRowCount());
      assertEquals(2, lens.getColCount());
   }

   private static int baseFetches(CalcTableVSAssembly cassembly, Viewsheet vs, boolean detail)
      throws Exception
   {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class, RETURNS_DEEP_STUBS);
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      when(box.getID()).thenReturn("vs1");
      TableAssembly table = mock(TableAssembly.class);
      AtomicInteger fetches = new AtomicInteger();

      CalcTableVSAQuery query = new CalcTableVSAQuery(box, "Calc1", false) {
         @Override
         public TableAssembly getTableAssembly() {
            return table;
         }

         @Override
         public boolean isDetail() {
            return detail;
         }

         @Override
         protected TableLens getTableLens(TableAssembly table) {
            fetches.incrementAndGet();
            return new DefaultTableLens(new Object[][] { { "col" }, { "a" } });
         }
      };

      assertNotNull(query.getTableLens());
      return fetches.get();
   }

   private static int baseFetches(TableCellBinding cell) throws Exception {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(cassembly);

      if(cell != null) {
         cassembly.getTableLayout().setCellBinding(1, 1, cell);
      }

      return baseFetches(cassembly, vs, false);
   }

   @Test
   void sourcedCalcTableWithStaticLayoutNeverRunsBaseQuery() throws Exception {
      assertEquals(0, baseFetches((TableCellBinding) null));
   }

   @Test
   void sourcedCalcTableWithTextOnlyLayoutNeverRunsBaseQuery() throws Exception {
      assertEquals(0, baseFetches(new TableCellBinding(CellBinding.BIND_TEXT, "x")));
   }

   @Test
   void detailCellStillRunsBaseQuery() throws Exception {
      assertEquals(1, baseFetches(TableCellBinding.getDetailBinding("col")));
   }

   @Test
   void formulaCellStillRunsBaseQuery() throws Exception {
      assertEquals(1,
                   baseFetches(new TableCellBinding(CellBinding.BIND_FORMULA, "field['col']")));
   }

   @Test
   void detailQueryWithStaticLayoutStillRunsBaseQuery() throws Exception {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(cassembly);
      assertEquals(1, baseFetches(cassembly, vs, true));
   }
}
