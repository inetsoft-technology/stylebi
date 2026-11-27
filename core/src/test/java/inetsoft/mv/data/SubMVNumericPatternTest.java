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
package inetsoft.mv.data;

import inetsoft.test.*;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XExpression;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82491: a like, starts with or contains value that is not a number (e.g. 2%) on a
 * numeric MV column reached it as 0 (LIKE 0 returned the 0 row). Kept as a string now, it
 * matches no row, and the negated condition the non-null rows, as the "col <> col" of sql,
 * instead of failing to parse.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SubMVNumericPatternTest {
   private static final double NULL = Tool.NULL_DOUBLE;
   // row 1 is null
   private static final double[] VALS = { 0, NULL, 2, 20, 21, 11, 12 };

   @ParameterizedTest
   @CsvSource({
      "LIKE, 2%", "LIKE, _1", "LIKE, 12%", "STARTSWITH, abc", "CONTAINS, 2%", "LIKE, ''"
   })
   void intColumnNonNumericPatternMatchesNoRow(String op, String value) throws Exception {
      assertEquals(List.of(), eval(intColumn(), op, value, false));
      assertEquals(List.of(0, 2, 3, 4, 5, 6), eval(intColumn(), op, value, true));
   }

   @ParameterizedTest
   @CsvSource({ "LIKE, 12%", "LIKE, 2%", "STARTSWITH, abc", "CONTAINS, _1" })
   void doubleColumnNonNumericPatternMatchesNoRow(String op, String value) throws Exception {
      assertEquals(List.of(), eval(doubleColumn(), op, value, false));
      assertEquals(List.of(0, 2, 3, 4, 5, 6), eval(doubleColumn(), op, value, true));
   }

   @Test
   void numberStillMatches() throws Exception {
      assertEquals(List.of(2), eval(intColumn(), "LIKE", "2", false));
      assertEquals(List.of(0), eval(intColumn(), "LIKE", "0", false));
      assertEquals(List.of(2, 3, 4), eval(intColumn(), "STARTSWITH", "2", false));
      assertEquals(List.of(2), eval(doubleColumn(), "LIKE", 2d, false));
   }

   private static List<Integer> eval(XDimIndex dim, String op, Object val, boolean not)
      throws Exception
   {
      DefaultTableBlock table = mock(DefaultTableBlock.class);
      when(table.indexOfHeader("col")).thenReturn(0);
      SubMV sub = new SubMV(new XDimIndex[] { dim }, table);
      Field f = SubMV.class.getDeclaredField("rtdims");
      f.setAccessible(true);
      f.set(sub, new XDimIndex[] { dim });

      XBinaryCondition cond =
         new XBinaryCondition(new XExpression("col", XExpression.FIELD),
                              new XExpression(val, XExpression.VALUE), op);
      cond.setIsNot(not);
      BitSet set = sub.getRows(cond);
      List<Integer> rows = new ArrayList<>();

      for(int i = 0; i < VALS.length; i++) {
         if(set != null && set.get(i)) {
            rows.add(i);
         }
      }

      return rows;
   }

   private static MVIntColumn intColumn() {
      MVIntColumn col = new MVIntColumn(VALS.length, 0, 21);

      for(int i = 0; i < VALS.length; i++) {
         col.setValue(i, VALS[i]);
      }

      return col;
   }

   private static MVDoubleColumn doubleColumn() {
      MVDoubleColumn col = new MVDoubleColumn(null, 0, null, VALS.length, true);

      for(int i = 0; i < VALS.length; i++) {
         col.setValue(i, VALS[i]);
      }

      return col;
   }
}
