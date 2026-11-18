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
package inetsoft.report.lens;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A formula column declared as a narrower type than its script result (bug #81200). Narrowing
 * a number that is out of the range of the declared type used to saturate or wrap silently,
 * e.g. 5.0E9 declared Integer showed 2147483647, and the guard that keeps the result when the
 * conversion loses data never saw it because the cast returns a value instead of null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormulaTableLensNarrowTypeTest {
   /**
    * A number out of the range of the declared type keeps the script result instead of the
    * saturated or wrapped value.
    */
   @ParameterizedTest(name = "{0} <- {1}")
   @CsvSource({
      "Integer, 5000000000.5",
      "Integer, -5000000000.5",
      "Integer, 2147483648",
      "Integer, -2147483649",
      "Short,   70000",
      "Short,   -32769",
      "Byte,    300",
      "Byte,    -129",
      "Long,    1e19",
      "Long,    -1e19"
   })
   void outOfRangeKeepsScriptResult(String type, String script) throws Exception {
      Object expected = Double.valueOf(script);
      Object actual = evaluate(declared(type), script, false);

      assertInstanceOf(Number.class, actual);
      assertEquals(((Number) expected).doubleValue(), ((Number) actual).doubleValue());
   }

   /**
    * The fraction is cut as before, a number whose cut value is in range is converted.
    */
   @ParameterizedTest(name = "{0} <- {1}")
   @CsvSource({
      "Integer, 3.7,          3",
      "Integer, -3.7,         -3",
      "Integer, 2147483647.5, 2147483647",
      "Integer, -2147483648,  -2147483648",
      "Short,   300.9,        300",
      "Short,   32767,        32767",
      "Byte,    127.5,        127",
      "Byte,    -128,         -128",
      "Long,    5000000000,   5000000000"
   })
   void inRangeIsConverted(String type, String script, long expected) throws Exception {
      Class<?> clazz = declared(type);
      Object actual = evaluate(clazz, script, false);

      assertInstanceOf(clazz, actual);
      assertEquals(expected, ((Number) actual).longValue());
   }

   /**
    * When the data is cached a null conversion is written as null, so an out of range number
    * is too instead of a result the typed column cannot hold.
    */
   @Test
   void outOfRangeIsNullWhenTypeIsForced() throws Exception {
      assertNull(evaluate(Integer.class, "5000000000.5", true));
   }

   /**
    * A conversion that loses the value is found for the other number classes too: a long or a
    * big number wraps instead of saturating.
    */
   @Test
   void outOfRangeOfOtherNumberClasses() {
      assertTrue(FormulaTableLens.isOutOfRange(Integer.class, 5000000000L));
      assertTrue(FormulaTableLens.isOutOfRange(Integer.class, new BigDecimal("5000000000")));
      assertTrue(FormulaTableLens.isOutOfRange(Short.class, new BigInteger("70000")));
      assertTrue(FormulaTableLens.isOutOfRange(Byte.class, 128));
      assertTrue(FormulaTableLens.isOutOfRange(Long.class, new BigDecimal("1e19")));
      // the script engine turns NaN and infinity into null, other callers may still pass them
      assertTrue(FormulaTableLens.isOutOfRange(Integer.class, Float.NaN));
      assertTrue(FormulaTableLens.isOutOfRange(Integer.class, Double.POSITIVE_INFINITY));
      assertTrue(FormulaTableLens.isOutOfRange(Long.class, Double.NEGATIVE_INFINITY));

      assertFalse(FormulaTableLens.isOutOfRange(Integer.class, 2147483647L));
      assertFalse(FormulaTableLens.isOutOfRange(Integer.class, -2147483648L));
      assertFalse(FormulaTableLens.isOutOfRange(Short.class, (short) 5));
      assertFalse(FormulaTableLens.isOutOfRange(Long.class, Long.MIN_VALUE));
      assertFalse(FormulaTableLens.isOutOfRange(Long.class, Long.MAX_VALUE));
   }

   /**
    * Types that are not narrowed and values that are not numbers are left to the conversion.
    */
   @Test
   void otherTypesAndValuesAreNotChecked() {
      assertFalse(FormulaTableLens.isOutOfRange(Double.class, 1e300));
      assertFalse(FormulaTableLens.isOutOfRange(Float.class, 1e300));
      assertFalse(FormulaTableLens.isOutOfRange(String.class, 5000000000L));
      assertFalse(FormulaTableLens.isOutOfRange(Integer.class, "5000000000"));
      assertFalse(FormulaTableLens.isOutOfRange(Integer.class, null));
   }

   /**
    * A string that is not a number keeps its result as before.
    */
   @Test
   void unparsableStringKeepsScriptResult() throws Exception {
      assertEquals("abc", evaluate(Integer.class, "'abc'", false));
   }

   private static Class<?> declared(String type) {
      return switch(type) {
         case "Integer" -> Integer.class;
         case "Short" -> Short.class;
         case "Byte" -> Byte.class;
         case "Long" -> Long.class;
         default -> throw new IllegalArgumentException(type);
      };
   }

   // the value of a formula column of a single row table declared as the type
   private static Object evaluate(Class<?> type, String script, boolean forceType)
      throws Exception
   {
      AssetQuerySandbox box = PoolTestSupport.poolBox(false);
      DefaultTableLens base = new DefaultTableLens(new Object[][] { { "v" }, { 1 } });
      FormulaTableLens lens = new FormulaTableLens(
         base, new String[] { "calc" }, new String[] { script }, box.getScriptEnv(),
         box.getScope());
      lens.setColType(base.getColCount(), type);

      Field field = FormulaTableLens.class.getDeclaredField("forceType");
      field.setAccessible(true);
      field.setBoolean(lens, forceType);

      return lens.getObject(1, base.getColCount());
   }
}
