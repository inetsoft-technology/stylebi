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
package inetsoft.report;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression guard for #75747 / #75801: converting a crosstab to a freehand table
 * generates a {@code toList(data['Col'], ...)} expression per row/column GROUP cell,
 * where "Col" is the physical column name backing the regenerated crosstab's data.
 * {@code LayoutTool.getGroupColumnName()} decides that name from a cell binding's
 * value, which can hold either the physical column name or the dimension's display
 * full name (e.g. "None(Month(ndate))") depending on how the dimension was bound.
 * Guessing wrong collapses the group cell's expansion to zero rows (#75747), and
 * "fixing" it by always stripping the wrapper breaks dimensions whose physical
 * column genuinely is named with its display form, such as "Year(Date)" (#75801).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LayoutToolTest {
   @Test
   void usesRawValueWhenItIsAlreadyTheOuterDateDimensionsPhysicalColumn() throws Exception {
      // #75801 (CroTable7): "Year(Date)" is a sole/outer date GROUP dimension whose
      // crosstab column is genuinely named with its display form -- must be used as-is.
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "Year(Date)", "Sum(Total)" },
         { "2022", 100.0 }
      });

      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "Year(Date)");
      assertEquals("Year(Date)", getGroupColumnName(base, cell));
   }

   @Test
   void unwrapsDisplayNameForNestedDateDimension() throws Exception {
      // #75747 (GroupAndTab): "None(Month(ndate))" is the display full name for a
      // date dimension nested under another group; the crosstab's actual column is
      // the unwrapped "Month(ndate))".
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "state", "Month(ndate)", "Sum(eid)" },
         { "Others", "2022-01", 5.0 }
      });

      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "None(Month(ndate))");
      assertEquals("Month(ndate)", getGroupColumnName(base, cell));
   }

   @Test
   void unwrapsDisplayNameForMultipleNestedPassthroughDateDimensions() throws Exception {
      // #75801 (Special2): several nested date/passthrough dimensions, each whose
      // display full name must be unwrapped to its own physical column.
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "NDateTime", "NDate", "NTime", "Sum(x)" },
         { "2022-01-01", "2022-01-01", "10:00", 3.0 }
      });

      assertEquals("NDateTime", getGroupColumnName(base,
         new TableCellBinding(CellBinding.BIND_COLUMN, "None(NDateTime)")));
      assertEquals("NDate", getGroupColumnName(base,
         new TableCellBinding(CellBinding.BIND_COLUMN, "None(NDate)")));
      assertEquals("NTime", getGroupColumnName(base,
         new TableCellBinding(CellBinding.BIND_COLUMN, "None(NTime)")));
   }

   @Test
   void returnsRawValueUncheckedWhenBaseIsNull() throws Exception {
      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "None(Month(ndate))");
      assertEquals("None(Month(ndate))", getGroupColumnName(null, cell));
   }

   @Test
   void returnsRawValueWhenNeitherFormMatchesAnyColumn() throws Exception {
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "unrelated_column" },
         { "x" }
      });

      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "None(Month(ndate))");
      assertEquals("None(Month(ndate))", getGroupColumnName(base, cell));
   }

   private static String getGroupColumnName(TableLens base, TableCellBinding cell) throws Exception {
      Method method = LayoutTool.class.getDeclaredMethod(
         "getGroupColumnName", TableLens.class, TableCellBinding.class);
      method.setAccessible(true);
      return (String) method.invoke(null, base, cell);
   }
}
