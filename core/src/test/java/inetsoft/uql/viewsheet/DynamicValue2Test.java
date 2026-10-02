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
package inetsoft.uql.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #77583: a non-integer design value (variable, script, decimal, junk) must fall back to the
 * default instead of throwing NumberFormatException, so the viewsheet stays saveable.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DynamicValue2Test {
   @ParameterizedTest(name = "\"{0}\" -> {1}")
   @CsvSource(delimiter = '|', value = {
      "50|50",
      "0|0",
      "-7|-7",
      "+50|50",
      "' 7 '|7",
      "2147483647|2147483647",
      "-2147483648|-2147483648",
      "50.0|50",
      "-0.0|0",
      "1e2|100",
      "''|100",
      "'   '|100",
      "$(x)|100",
      "=x|100",
      "=50|100",
      "50.5|100",
      "2147483648|100",
      "-2147483649|100",
      "1e10|100",
      "-1e10|100",
      "2147483647.5|100",
      "10(|100",
      "*-1|100",
      "abc|100",
      "NaN|100",
      "Infinity|100",
   })
   void getIntValueDesign(String dvalue, int expected) {
      assertEquals(expected, new DynamicValue2(dvalue).getIntValue(true, 100));
   }

   @ParameterizedTest
   @ValueSource(strings = { "abc", "10(", "50.0", "1e10" })
   void getIntValueRuntimeLiteral(String dvalue) {
      // a literal dValue is copied into the rvalue, so the runtime side sees the same string
      int expected = "50.0".equals(dvalue) ? 50 : 9;
      assertEquals(expected, new DynamicValue2(dvalue).getIntValue(false, 9));
   }

   @Test
   void getIntValueRuntimeUnevaluatedDynamic() {
      assertEquals(9, new DynamicValue2("$(x)").getIntValue(false, 9));
      assertEquals(9, new DynamicValue2("=x").getIntValue(false, 9));
   }

   @Test
   void getIntValueRuntimeNumericRValue() {
      DynamicValue2 value = new DynamicValue2("=5");
      value.setRValue(7);
      assertEquals(7, value.getIntValue(false, 9));
      // a script result may come back as a double
      value.setRValue(7.0);
      assertEquals(7, value.getIntValue(false, 9));
      value.setRValue(7.5);
      assertEquals(9, value.getIntValue(false, 9));
   }

   @ParameterizedTest(name = "\"{0}\" -> {1}")
   @CsvSource(delimiter = '|', value = {
      "1.5|1.5",
      "50|50.0",
      "-2.25|-2.25",
      "1e3|1000.0",
      "NaN|NaN",
      "Infinity|Infinity",
      "-Infinity|-Infinity",
      "''|3.0",
      "abc|3.0",
      "$(x)|3.0",
      "=x|3.0",
      "10(|3.0",
   })
   void getDoubleValueDesign(String dvalue, double expected) {
      assertEquals(expected, new DynamicValue2(dvalue).getDoubleValue(true, 3.0));
   }

   @Test
   void getDoubleValueNaNDefault() {
      // SingleTimeInfo uses a NaN default; blank and junk must still resolve to it
      assertEquals(Double.NaN, new DynamicValue2("").getDoubleValue(true, Double.NaN));
      assertEquals(Double.NaN, new DynamicValue2("abc").getDoubleValue(true, Double.NaN));
   }
}
