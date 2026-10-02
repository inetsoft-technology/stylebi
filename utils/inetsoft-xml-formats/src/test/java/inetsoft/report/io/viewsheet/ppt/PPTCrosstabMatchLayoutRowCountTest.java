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
package inetsoft.report.io.viewsheet.ppt;

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
 * Bug #77287: in a match-layout PowerPoint export, a crosstab whose data rows all contain
 * a blank (sparse) measure cell must be clamped to its design height exactly like the
 * equivalent freehand table. The bug #53192 blank-row exemption in
 * {@code AbstractVSExporter.getRegionRowCount()} used to leave the crosstab unclamped, and
 * PowerPoint's own pixel walk (which ignores the title) then let one extra row through.
 */
class PPTCrosstabMatchLayoutRowCountTest {
   @ParameterizedTest
   @EnumSource(Shape.class)
   void powerPointClampsSparseCrosstabLikeFreehand(Shape shape) {
      TableLens data = sparseLens(shape);

      int crosstab = new TestPPTExporter().regionRowCount(crosstab(shape), data);
      int calc = new TestPPTExporter().regionRowCount(calc(shape), data);

      assertEquals(shape.expectedRows, calc, "freehand table row count for " + shape);
      assertEquals(calc, crosstab,
         "PowerPoint must clamp the sparse crosstab to the same row count as the freehand table");
   }

   private static final class TestPPTExporter extends PPTVSExporter {
      TestPPTExporter() {
         super(Mockito.mock(PPTContext.class), new ByteArrayOutputStream());
         setMatchLayout(true);
      }

      int regionRowCount(TableDataVSAssembly table, TableLens data) {
         return getRegionRowCount(table, data);
      }
   }
}
