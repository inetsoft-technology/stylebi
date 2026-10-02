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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.report.TableLens;
import inetsoft.report.io.viewsheet.SparseCrosstabFixture.Shape;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;

import static inetsoft.report.io.viewsheet.SparseCrosstabFixture.calc;
import static inetsoft.report.io.viewsheet.SparseCrosstabFixture.crosstab;
import static inetsoft.report.io.viewsheet.SparseCrosstabFixture.sparseLens;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #77287: in a match-layout Excel export, a crosstab whose data rows all contain a
 * blank (sparse) measure cell must be clamped to its design height exactly like the
 * equivalent freehand table. The bug #53192 blank-row exemption in
 * {@code AbstractVSExporter.getRegionRowCount()} used to skip every such row from the
 * height budget, so the crosstab region lens was not clamped at all, and Excel (whose
 * writer does not clip at the design pixel height) wrote more rows than the viewer shows.
 */
class PoiExcelCrosstabMatchLayoutRowCountTest {
   @ParameterizedTest
   @EnumSource(Shape.class)
   void excelClampsSparseCrosstabLikeFreehand(Shape shape) {
      TableLens data = sparseLens(shape);

      int crosstab = new TestExcelExporter().regionRowCount(crosstab(shape), data);
      int calc = new TestExcelExporter().regionRowCount(calc(shape), data);

      assertEquals(shape.expectedRows, calc, "freehand table row count for " + shape);
      assertEquals(calc, crosstab,
         "Excel must clamp the sparse crosstab to the same row count as the freehand table");
   }

   private static final class TestExcelExporter extends PoiExcelVSExporter {
      TestExcelExporter() {
         super(Mockito.mock(ExcelContext.class), new ByteArrayOutputStream());
         setMatchLayout(true);
      }

      int regionRowCount(TableDataVSAssembly table, TableLens data) {
         return getRegionRowCount(table, data);
      }
   }
}
