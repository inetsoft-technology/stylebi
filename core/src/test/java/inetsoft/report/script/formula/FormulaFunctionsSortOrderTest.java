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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #81071: the trailing sort of toList/mapList (applied after maxrows) must follow the
 * sort option unless sort2 is given explicitly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FormulaFunctionsSortOrderTest {
   private static Object[] input() {
      return new Object[] { 4, 1, 3, 2 };
   }

   private static List<Object> toList(String options) {
      return Arrays.asList((Object[]) FormulaFunctions.toList(input(), options));
   }

   private static List<Object> mapList(String options) {
      return Arrays.asList(
         (Object[]) FormulaFunctions.mapList(input(), new Object[] { 9, 9 }, options));
   }

   @Test
   void toListDescMaxRowsKeepsDescendingOrder() {
      assertEquals(List.of(4, 3), toList("sort=desc,maxrows=2"));
   }

   @Test
   void toListAscMaxRows() {
      assertEquals(List.of(1, 2), toList("sort=asc,maxrows=2"));
   }

   @Test
   void toListExplicitSort2Wins() {
      assertEquals(List.of(3, 4), toList("sort=desc,maxrows=2,sort2=asc"));
      assertEquals(List.of(4, 3), toList("sort=desc,maxrows=2,sort2=desc"));
   }

   @Test
   void toListNegativeMaxRows() {
      // sorted desc: 4,3,2,1; last two are 2,1 and keep the descending order
      assertEquals(List.of(2, 1), toList("sort=desc,maxrows=-2"));
   }

   @Test
   void toListSortFalseKeepsInputOrder() {
      assertEquals(List.of(4, 1), toList("sort=false,maxrows=2"));
   }

   @Test
   void mapListDescMaxRowsKeepsDescendingOrder() {
      assertEquals(List.of(4.0, 3.0), mapList("sort=desc,maxrows=2,others=leaveothers"));
   }

   @Test
   void mapListExplicitSort2Wins() {
      assertEquals(List.of(3.0, 4.0), mapList("sort=desc,maxrows=2,sort2=asc,others=leaveothers"));
      assertEquals(List.of(4.0, 3.0), mapList("sort=desc,maxrows=2,sort2=desc,others=leaveothers"));
   }

   @Test
   void mapListSortFalseKeepsInputOrder() {
      assertEquals(List.of(4.0, 1.0), mapList("sort=false,maxrows=2,others=leaveothers"));
   }
}
