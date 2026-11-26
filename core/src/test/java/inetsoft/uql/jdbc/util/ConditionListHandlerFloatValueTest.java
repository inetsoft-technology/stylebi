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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.jdbc.XExpression;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82410: a Float condition value was bound to the SQL as is, i.e. as a REAL
 * 0.15000000596..., which Derby compares at that precision against a DECIMAL 0.15 and so
 * matches no row. It must be bound as the decimal the user typed for a DECIMAL, NUMERIC,
 * DOUBLE or FLOAT field, and left alone otherwise.
 */
@Tag("core")
class ConditionListHandlerFloatValueTest {
   @ParameterizedTest
   @ValueSource(ints = { Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT })
   void floatIsBoundAsItsDecimalForDecimalAndDoubleFields(int sqlType) {
      Object value = ConditionListHandler.toDecimalValue(field(sqlType), 0.15f);
      assertEquals(new BigDecimal("0.15"), value);
   }

   @ParameterizedTest
   @ValueSource(ints = { Types.REAL, Types.INTEGER, Types.VARCHAR })
   void floatIsUnchangedForOtherFields(int sqlType) {
      Object value = 0.15f;
      assertSame(value, ConditionListHandler.toDecimalValue(field(sqlType), value));
   }

   @Test
   void floatIsUnchangedWithoutSqlType() {
      Object value = 0.15f;
      assertSame(value, ConditionListHandler.toDecimalValue(new XExpression("discount", XExpression.FIELD), value));
      assertSame(value, ConditionListHandler.toDecimalValue(null, value));
   }

   @Test
   void otherValuesAreUnchanged() {
      XExpression field = field(Types.DECIMAL);

      for(Object value : new Object[] { 0.15d, new BigDecimal("0.15"), "0.15", 1, null,
                                        Float.NaN, Float.POSITIVE_INFINITY, 3f, 1234567936f })
      {
         assertSame(value, ConditionListHandler.toDecimalValue(field, value), String.valueOf(value));
      }
   }

   // one of with a multi-value variable: the value is one array; the condition's own array
   // must not change (and a Float[] can't hold a BigDecimal)
   @Test
   void floatArrayIsConvertedIntoANewArray() {
      Float[] value = { 0.15f, 3f, 0.2f };
      Object result = ConditionListHandler.toDecimalValue(field(Types.NUMERIC), value);

      assertNotSame(value, result);
      assertArrayEquals(new Object[] { new BigDecimal("0.15"), 3f, new BigDecimal("0.2") },
                        (Object[]) result);
      assertArrayEquals(new Float[] { 0.15f, 3f, 0.2f }, value);
   }

   @Test
   void arrayWithoutConvertibleFloatIsUnchanged() {
      Object[] value = { 0.15d, 3f, "0.2" };
      assertSame(value, ConditionListHandler.toDecimalValue(field(Types.DECIMAL), value));

      Object[] floats = { 0.15f, 0.2f };
      assertSame(floats, ConditionListHandler.toDecimalValue(field(Types.REAL), floats));
   }

   private static XExpression field(int sqlType) {
      XExpression field = new XExpression("discount", XExpression.FIELD);
      field.setSqlType(sqlType);
      return field;
   }
}
