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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78930: the cell data path type (HEADER/TRAILER/DETAIL) a format is stored under comes from
 * the assembly info's header/trailer counts, so the lens {@link CalcTableVSAQuery#getTableLens()}
 * returns must use the same counts on every path, otherwise the format is never matched at render.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalcTableVSAQueryInfoCountsTest {
   private enum Mode { SOURCELESS, FALLBACK, SOURCED }

   private static TableLens lens(Mode mode, int hrow, int trow, boolean formula,
                                 CalcTableVSAssembly[] out) throws Exception
   {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(cassembly);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) cassembly.getInfo();
      info.setHeaderRowCount(hrow);
      info.setTrailerRowCount(trow);
      TableLayout layout = cassembly.getTableLayout();

      if(formula) {
         layout.setCellBinding(1, 0, new TableCellBinding(CellBinding.BIND_FORMULA, "1+1"));
      }
      else {
         layout.setCellBinding(1, 0, new TableCellBinding(CellBinding.BIND_TEXT, "x"));
      }

      if(mode == Mode.SOURCED) {
         layout.setCellBinding(1, 1, TableCellBinding.getDetailBinding("col"));
      }

      out[0] = cassembly;
      ViewsheetSandbox box = mock(ViewsheetSandbox.class, RETURNS_DEEP_STUBS);
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      when(box.getID()).thenReturn("vs1");
      TableAssembly table = mode == Mode.SOURCELESS ? null : mock(TableAssembly.class);

      CalcTableVSAQuery query = new CalcTableVSAQuery(box, "Calc1", false) {
         @Override
         public TableAssembly getTableAssembly() {
            return table;
         }

         @Override
         protected TableLens getTableLens(TableAssembly t) {
            return mode == Mode.FALLBACK ? null :
               new DefaultTableLens(new Object[][] { { "col" }, { "a" } });
         }
      };

      return query.getTableLens();
   }

   private static void assertTypesMatchInfo(Mode mode, int hrow, int trow, boolean formula,
                                            int expectRow, int expectType) throws Exception
   {
      CalcTableVSAssembly[] a = new CalcTableVSAssembly[1];
      TableLens lens = lens(mode, hrow, trow, formula, a);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) a[0].getInfo();

      for(int r = 0; r < 2; r++) {
         assertEquals(info.getCellDataPath(r, 0).getType(),
                      lens.getDescriptor().getCellDataPath(r, 0).getType(), "row " + r);
      }

      assertEquals(expectType, info.getCellDataPath(expectRow, 0).getType());
      assertEquals(expectType, lens.getDescriptor().getCellDataPath(expectRow, 0).getType());
   }

   @Test
   void sourcelessTrailerRowTextCell() throws Exception {
      assertTypesMatchInfo(Mode.SOURCELESS, 1, 1, false, 1, TableDataPath.TRAILER);
   }

   @Test
   void sourcelessTrailerRowFormulaCell() throws Exception {
      assertTypesMatchInfo(Mode.SOURCELESS, 1, 1, true, 1, TableDataPath.TRAILER);
   }

   @Test
   void sourcelessHeaderRowCountTwo() throws Exception {
      assertTypesMatchInfo(Mode.SOURCELESS, 2, 0, false, 1, TableDataPath.HEADER);
   }

   @Test
   void fallbackPathTrailerRow() throws Exception {
      assertTypesMatchInfo(Mode.FALLBACK, 1, 1, false, 1, TableDataPath.TRAILER);
   }

   @Test
   void sourcedPathTrailerRowUnchanged() throws Exception {
      assertTypesMatchInfo(Mode.SOURCED, 1, 1, true, 1, TableDataPath.TRAILER);
   }
}
