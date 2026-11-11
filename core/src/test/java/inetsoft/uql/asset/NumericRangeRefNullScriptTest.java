/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.asset;

import inetsoft.test.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.util.Catalog;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #81105: a null value must fall into "Others" in the script expression of a numeric
 * range group, as it does in the SQL expression, in every segment and inclusion mode. The
 * top ("last value and above") segment used to lack the null check, and in JavaScript
 * null >= 0 and null > -1 are true.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class NumericRangeRefNullScriptTest {
   @ParameterizedTest
   @EnumSource(InclusiveType.class)
   void nullIsOthersWhenLastValueIsNegative(InclusiveType type) {
      assertEquals(others(), evalNull(type, true, true, -10, -5, -1));
   }

   @ParameterizedTest
   @EnumSource(InclusiveType.class)
   void nullIsOthersWhenLastValueIsZero(InclusiveType type) {
      assertEquals(others(), evalNull(type, true, true, -10, -5, 0));
   }

   @ParameterizedTest
   @EnumSource(InclusiveType.class)
   void nullIsOthersWhenLastValueIsPositive(InclusiveType type) {
      assertEquals(others(), evalNull(type, true, true, 0, 5, 10));
   }

   @ParameterizedTest
   @EnumSource(InclusiveType.class)
   void nullIsOthersWithoutBottomSegment(InclusiveType type) {
      assertEquals(others(), evalNull(type, false, true, -10, -5, 0));
   }

   @ParameterizedTest
   @EnumSource(InclusiveType.class)
   void nullIsOthersWithoutTopSegment(InclusiveType type) {
      assertEquals(others(), evalNull(type, true, false, -10, -5, 0));
   }

   @ParameterizedTest
   @EnumSource(InclusiveType.class)
   void nonNullValuesStillGoToTopSegment(InclusiveType type) {
      assertEquals(type == InclusiveType.LOWER ? ">=0" : ">0", eval(type, 5, -10, -5, 0));
   }

   private static String others() {
      return Catalog.getCatalog().getString("Others");
   }

   private static String evalNull(InclusiveType type, boolean bottom, boolean top,
                                  double... values)
   {
      return eval(type, bottom, top, null, values);
   }

   private static String eval(InclusiveType type, Object value, double... values) {
      return eval(type, true, true, value, values);
   }

   private static String eval(InclusiveType type, boolean bottom, boolean top, Object value,
                              double[] values)
   {
      ValueRangeInfo info = new ValueRangeInfo();
      info.setValues(values);
      info.setShowBottomValue(bottom);
      info.setShowTopValue(top);
      info.setInclusiveType(type);

      NumericRangeRef ref = new NumericRangeRef("grp", new AttributeRef(null, "x"));
      ref.setValueRangeInfo(info);

      String script = "var field = {'x': " + value + "};\n" + ref.getScriptExpression();

      try(Context cx = Context.create("js")) {
         return cx.eval("js", script).asString();
      }
   }
}
