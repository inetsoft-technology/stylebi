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

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.viewsheet.CalcTableVSAScriptable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class TableValueListTest {
   private TableValueList tableValueList;

   private static DefaultTableLens defaultTableLens;

   @BeforeEach
   void setUp() {
      defaultTableLens = new DefaultTableLens(objData);
      defaultTableLens.setColumnIdentifier(0, "name");
      defaultTableLens.setColumnIdentifier(1, "id");
      defaultTableLens.setColumnIdentifier(2, "date");
   }

   @Test
   void testSizeWithRightStructure() {
      tableValueList = new TableValueList(defaultTableLens, "name",
                                          false, new int[] {0, 1, 2}, null);
      tableValueList.setSize(4);
      assertEquals(4, tableValueList.size());
   }

   @Test
   void testStructureWithException() {
      Exception exception = assertThrows(RuntimeException.class, () -> {
         tableValueList = new TableValueList(defaultTableLens, "Employee",
                                             false, new int[] {1, 2, 3}, null);
      });

      assertTrue(exception.getMessage().contains("Column not found in table"));
   }

   private static Stream<Arguments> provideGetTestCases() {
      return Stream.of(
         Arguments.of( true, 1 , null), //check idx < 0
         Arguments.of( true, 0 , "name"), //check expression is true
         Arguments.of( false, 2 , "c") //check table is not null
      );
   }

   @ParameterizedTest
   @MethodSource("provideGetTestCases")
   void testGet( Boolean expression, int idx, Object expected) {
      tableValueList = new TableValueList(defaultTableLens, "name",
                                          expression, new int[] {0, -1, 2}, mock(CalcTableVSAScriptable.class));

      assertEquals(expected, tableValueList.get(idx));
   }

   @Test
   void testGetScope() {
      tableValueList = new TableValueList(defaultTableLens, "name",
                                          true, new int[] {0, -1, 2}, mock(CalcTableVSAScriptable.class));
      assertNull(tableValueList.getScope(1));
      assertEquals("name", tableValueList.get(0));
   }

   Object[][] objData = new Object[][]{
      {"name", "id", "date"},
      {"a", 1, new Date(2021 - 1900, 0, 1)},
      {"c", 2, new Date(2023 - 1900, 5, 15)},
      {"a", 3, new Date(2025 - 1900, 11, 31)},
      {"b", 2, new Date(2026 - 1900, 9, 20)}
   };
}
