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

import inetsoft.uql.XTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

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
}
