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
package inetsoft.uql;

import inetsoft.test.*;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82410: a Float condition value (a Float variable, 0.15f) was compared with a Double or
 * BigDecimal column value as the widened double 0.15000000596046448, so "= 0.15" dropped the
 * 0.15 rows, "&lt; 0.15" kept them, and BETWEEN / one of failed the same way. The value must be
 * read as the decimal the user typed, for every operator.
 */
// Condition's constructor reads a property through PropertiesEngine, a Spring bean
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionFloatValueTest {
   static Stream<Arguments> columnValues() {
      return Stream.of(
         Arguments.of("Double", 0.15d),
         Arguments.of("BigDecimal", new BigDecimal("0.15")),
         Arguments.of("BigDecimal scale 4", new BigDecimal("0.1500")));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("columnValues")
   void equalTo(String label, Object column) {
      assertTrue(condition(XCondition.EQUAL_TO, false, 0.15f).evaluate(column));
      assertFalse(condition(XCondition.EQUAL_TO, false, 0.2f).evaluate(column));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("columnValues")
   void lessThan(String label, Object column) {
      assertFalse(condition(XCondition.LESS_THAN, false, 0.15f).evaluate(column), "0.15 < 0.15");
      assertTrue(condition(XCondition.LESS_THAN, true, 0.15f).evaluate(column), "0.15 <= 0.15");
      assertTrue(condition(XCondition.LESS_THAN, false, 0.2f).evaluate(column), "0.15 < 0.2");
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("columnValues")
   void greaterThan(String label, Object column) {
      assertTrue(condition(XCondition.GREATER_THAN, true, 0.15f).evaluate(column), "0.15 >= 0.15");
      assertFalse(condition(XCondition.GREATER_THAN, false, 0.15f).evaluate(column), "0.15 > 0.15");
      assertTrue(condition(XCondition.GREATER_THAN, false, 0.1f).evaluate(column), "0.15 > 0.1");
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("columnValues")
   void between(String label, Object column) {
      assertTrue(condition(XCondition.BETWEEN, false, 0.15f, 0.2f).evaluate(column));
      assertTrue(condition(XCondition.BETWEEN, false, 0.1f, 0.15f).evaluate(column));
      assertFalse(condition(XCondition.BETWEEN, false, 0.16f, 0.2f).evaluate(column));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("columnValues")
   void oneOf(String label, Object column) {
      assertTrue(condition(XCondition.ONE_OF, false, 0.1f, 0.15f).evaluate(column));
      assertFalse(condition(XCondition.ONE_OF, false, 0.1f, 0.2f).evaluate(column));
   }

   // the one of list is cached across rows; it must stay right when the rows mix number types
   @Test
   void oneOfAcrossRowsOfMixedNumberTypes() {
      Condition cond = condition(XCondition.ONE_OF, false, 0.15f, 0.3f);
      assertTrue(cond.evaluate(0.15d));
      assertTrue(cond.evaluate(new BigDecimal("0.15")));
      assertFalse(cond.evaluate(0.1d));
      assertTrue(cond.evaluate(0.3d));
      assertFalse(cond.evaluate(new BigDecimal("0.2")));
   }

   // a Float column (a REAL column) already compared at float precision and still does
   @Test
   void floatColumnIsUnchanged() {
      assertTrue(condition(XCondition.EQUAL_TO, false, 0.15f).evaluate(0.15f));
      assertFalse(condition(XCondition.LESS_THAN, false, 0.15f).evaluate(0.15f));
   }

   // widening is exact for an integral float; Float.toString() would round 1234567936f
   @Test
   void integralFloatAgainstIntegerColumnIsUnchanged() {
      assertTrue(condition(XCondition.EQUAL_TO, false, 1234567936f).evaluate(1234567936));
      assertTrue(condition(XCondition.EQUAL_TO, false, 3f).evaluate(3L));
      assertTrue(condition(XCondition.EQUAL_TO, false, 3f).evaluate(3.0d));
   }

   @Test
   void nonFiniteFloatIsUnchanged() {
      assertFalse(condition(XCondition.EQUAL_TO, false, Float.NaN).evaluate(0.15d));
      assertFalse(condition(XCondition.GREATER_THAN, false, Float.POSITIVE_INFINITY)
                     .evaluate(Double.MAX_VALUE));
      assertTrue(condition(XCondition.LESS_THAN, false, Float.POSITIVE_INFINITY)
                    .evaluate(Double.MAX_VALUE));
   }

   private static Condition condition(int op, boolean equal, Object... values) {
      Condition cond = new Condition(XSchema.DOUBLE);
      cond.setOperation(op);
      cond.setEqual(equal);

      for(Object value : values) {
         cond.addValue(value);
      }

      return cond;
   }
}
