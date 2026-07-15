/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.script.formula;

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableLens;
import inetsoft.report.filter.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class NamedCellRangeTest {

   @ParameterizedTest
   @CsvSource({
      "columnName, columnName",
      "columnName@group:value?condition, columnName",
      "columnName\\@group:value, columnName@group:value",
      "=expression, expression",
      "columnName@group:*, columnName" //test for*
   })
   void testNameCellRangeStructure(String input, String expectedColumn) {
      try {
         NamedCellRange range = new NamedCellRange(input);
         assertEquals(expectedColumn, range.getColumn());
      } catch (Exception e) {
         fail("Initialization failed: " + e.getMessage());
      }
   }

   @Test
   void testThrowExceptionWhenMissValue() {
      Exception exception = assertThrows(Exception.class, () -> {
         new NamedCellRange("columnName@group");
      });
      assertTrue(exception.getMessage().contains("Group value missing from range"));
   }

   /**
    * #75663: the base-table-reference flag defaults off and is settable, and drives
    * whether a by-name worksheet-table column read bypasses grouped/crosstab
    * summary routing in getCells.
    */
   @Test
   void testBaseTableReferenceFlag() throws Exception {
      NamedCellRange range = new NamedCellRange("col");
      assertFalse(range.isBaseTableReference());
      range.setBaseTableReference(true);
      assertTrue(range.isBaseTableReference());
   }

   /**
    * test when summary is true will  throw exception
    */
   @Test
   void testGetCellsWithSummaryTrue() throws Exception {
      NamedCellRange range = new NamedCellRange("{sales}@region:US?year>2020");

      Exception exception = assertThrows(Exception.class, () -> {
         range.getCells(mock(XTable.class), true);
      });

      assertTrue(exception.getMessage().contains("Summary reference is not supported in"));
   }

   /**
    * #75663: getCells must bypass the grouped/crosstab summary routing when the
    * range is a by-name worksheet-table reference (baseTableRef=true). This
    * reproduces the reported pollution structure: a top lens that still exposes
    * the table's flat detail columns but reports a CROSSTAB_TABLE descriptor and
    * nests a CrossTabFilter -- as when a crosstab-optimized freehand table pushes
    * aggregate info down into a shared base table. With the flag off, getCells
    * walks down to the nested crosstab and returns summary cells; with it on, it
    * reads the column's flat detail values instead.
    */
   @Test
   void testGetCellsBypassesNestedCrosstabForBaseTableReference() throws Exception {
      Object[][] data = {
         { "col1", "col2", "col3" },
         { "a", 1, 5.0 },
         { "a", 1, 5.0 }, // duplicate group (a,1) so the crosstab sum (10.0) differs from detail
         { "b", 3, 10.0 }
      };

      CrossTabFilter crosstab = new CrossTabFilter(
         new DefaultTableLens(data), new int[]{ 0 }, new int[]{ 1 }, new int[]{ 2 },
         new Formula[]{ new SumFormula() });
      // the crosstab genuinely reports a CROSSTAB_TABLE descriptor
      assertEquals(TableDataDescriptor.CROSSTAB_TABLE, crosstab.getDescriptor().getType());

      TableLens polluted = new PollutedCrosstabLens(new DefaultTableLens(data), crosstab);

      NamedCellRange range = new NamedCellRange("col3");

      // flag off (default): routed down to the nested crosstab -> summary cells
      Collection<?> summaryCells = range.getCells(polluted, false);

      // flag on: reads the flat detail column values, skipping the crosstab routing
      range.setBaseTableReference(true);
      Collection<?> flatCells = range.getCells(polluted, false);

      assertEquals(Arrays.asList(5.0, 5.0, 10.0), new ArrayList<>(flatCells));
      assertNotEquals(new ArrayList<>(flatCells), new ArrayList<>(summaryCells));
   }

   /**
    * Test double for the crosstab-pollution structure: its own cell data is the
    * flat detail table (inherited from DefaultTableFilter), but it reports the
    * nested crosstab's descriptor and returns the crosstab from getTable() so
    * NamedCellRange.findSummaryTable can reach it.
    */
   private static final class PollutedCrosstabLens extends DefaultTableFilter {
      private final CrossTabFilter crosstab;

      PollutedCrosstabLens(TableLens detail, CrossTabFilter crosstab) {
         super(detail);
         this.crosstab = crosstab;
      }

      @Override
      public TableDataDescriptor getDescriptor() {
         return crosstab.getDescriptor();
      }

      @Override
      public TableLens getTable() {
         return crosstab;
      }
   }
}
