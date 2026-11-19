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
package inetsoft.report.script.viewsheet;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scalar coercion of an output or input assembly object ({@code Text1 + 1}, {@code String(Text1)}). The number
 * formatting is compared with the one of the JavaScript engine itself for a large set of numbers, which the few
 * values of {@code VSAScriptableScalarCoercionTest} cannot cover: the boundaries where the engine switches to
 * exponent form, values one step beside them, numbers with few digits, and random bit patterns.
 */
@Tag("core")
class ScalarCoercionTest {
   private static String jsLiteral(double d) {
      if(Double.isNaN(d)) {
         return "NaN";
      }

      if(Double.isInfinite(d)) {
         return d > 0 ? "Infinity" : "-Infinity";
      }

      return d == 0 && 1 / d < 0 ? "-0" : Double.toString(d);
   }

   /** Formats the numbers with the String() of the JavaScript engine. */
   private static List<String> formatWithEngine(List<Double> values) {
      List<String> result = new ArrayList<>();

      try(Context context = Context.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build()) {
         for(int from = 0; from < values.size(); from += 2000) {
            StringBuilder script = new StringBuilder("[");

            for(int i = from; i < Math.min(values.size(), from + 2000); i++) {
               script.append(i > from ? "," : "").append(jsLiteral(values.get(i)));
            }

            Value array = context.eval("js", script.append("].map(String)").toString());

            for(long i = 0; i < array.getArraySize(); i++) {
               result.add(array.getArrayElement(i).asString());
            }
         }
      }

      return result;
   }

   private static void assertSameAsEngine(List<Double> values) {
      List<String> expected = formatWithEngine(values);
      List<String> wrong = new ArrayList<>();

      for(int i = 0; i < values.size(); i++) {
         String actual = ScalarCoercion.numberToString(values.get(i));

         if(!expected.get(i).equals(actual)) {
            wrong.add(values.get(i) + " -> " + actual + " (engine " + expected.get(i) + ")");

            if(wrong.size() >= 20) {
               break;
            }
         }
      }

      assertEquals(List.of(), wrong);
   }

   @Test
   void theBoundariesOfTheExponentFormAreFormattedAsTheEngineDoes() {
      List<Double> values = new ArrayList<>();

      for(int e = -30; e <= 30; e++) {
         double p = Math.pow(10, e);

         for(double v : new double[] { p, p * 9.99, p * 1.5, p * 5 }) {
            values.add(v);
            values.add(-v);
            values.add(Math.nextUp(v));
            values.add(Math.nextDown(v));
         }
      }

      for(double v : new double[] { 1e21, 1e-6, 1e-7, 999999999999999900000.0, 123456789012345680000.0,
                                    9007199254740992.0, 9007199254740993.0, 0.000001, 0.0000001, 4.35, 0.1 + 0.2,
                                    Double.MIN_VALUE, Double.MAX_VALUE, Double.MIN_NORMAL, 5e-324, 1.7976931348623157e308 })
      {
         values.add(v);
         values.add(Math.nextUp(v));
         values.add(Math.nextDown(v));
      }

      assertSameAsEngine(values);
   }

   @Test
   void wholeAndShortDecimalNumbersAreFormattedAsTheEngineDoes() {
      List<Double> values = new ArrayList<>();

      for(int i = -2000; i <= 2000; i++) {
         values.add((double) i);
         values.add(i / 10.0);
         values.add(i / 100.0);
         values.add(i * 1000.0);
         values.add(i * 1e18);
         values.add(i * 1e-9);
      }

      assertSameAsEngine(values);
   }

   @Test
   void randomNumbersAreFormattedAsTheEngineDoes() {
      Random random = new Random(81430);
      List<Double> values = new ArrayList<>();

      for(int i = 0; i < 40000; i++) {
         double d = Double.longBitsToDouble(random.nextLong());

         if(!Double.isNaN(d)) {
            values.add(d);
         }

         // magnitudes that are typical for data, with 1 to 17 digits
         int digits = 1 + random.nextInt(17);
         double scaled = new BigDecimal(random.nextLong() >>> 1).movePointLeft(random.nextInt(25))
            .round(new java.math.MathContext(digits)).doubleValue();
         values.add(scaled);
         values.add(random.nextGaussian() * Math.pow(10, random.nextInt(40) - 20));
      }

      assertSameAsEngine(values);
   }

   private static Object valueOf(Object value) {
      return call("valueOf", () -> value);
   }

   private static Object toString(Object value) {
      return call("toString", () -> value);
   }

   private static Object call(String name, Supplier<Object> value) {
      return ScalarCoercion.member(name, value).execute();
   }

   @Test
   void coercionMembersAreValueOfAndToStringOnly() {
      assertTrue(ScalarCoercion.isCoercionMember("valueOf"));
      assertTrue(ScalarCoercion.isCoercionMember("toString"));
      assertFalse(ScalarCoercion.isCoercionMember("value"));
      assertFalse(ScalarCoercion.isCoercionMember(null));
      assertFalse(ScalarCoercion.isCoercionMember("ValueOf"));
   }

   @Test
   void membersReadTheValueEachTimeTheyAreCalled() {
      int[] counter = { 0 };
      ProxyExecutable member = ScalarCoercion.member("valueOf", () -> ++counter[0]);

      assertEquals(1.0, member.execute());
      assertEquals(2.0, member.execute());
   }

   @Test
   void anExceptionOfTheReadReachesTheCaller() {
      ProxyExecutable member = ScalarCoercion.member("toString", () -> {
         throw new IllegalStateException("read failed");
      });

      assertThrows(IllegalStateException.class, member::execute);
   }

   @Test
   void valueOfGivesANumberForEveryNumberType() {
      assertEquals(5.0, valueOf(5));
      assertEquals(5.0, valueOf(5L));
      assertEquals(2.5, valueOf(2.5f));
      assertEquals(2.5, valueOf(new BigDecimal("2.5")));
      assertEquals(7.0, valueOf((short) 7));
      assertEquals(3.0, valueOf((byte) 3));
      assertEquals(Double.NaN, valueOf(Double.NaN));
      assertEquals(Double.POSITIVE_INFINITY, valueOf(Double.POSITIVE_INFINITY));
   }

   @Test
   void valueOfGivesTheTextOfACharacterAndTheJoinedTextOfAnArray() {
      assertEquals("x", valueOf('x'));
      assertEquals("1,2.5,3", valueOf(new Object[] { 1, 2.5, "3" }));
      assertEquals("1,2", valueOf(new int[] { 1, 2 }));
      assertEquals("", valueOf(new Object[0]));
   }

   @Test
   void valueOfKeepsTextAndBooleanAndNull() {
      assertEquals("abc", valueOf("abc"));
      assertEquals(Boolean.TRUE, valueOf(true));
      assertNull(valueOf(null));
   }

   @Test
   void toStringIsAlwaysAString() {
      assertEquals("null", toString(null));
      assertEquals("abc", toString("abc"));
      assertEquals("true", toString(true));
      assertEquals("x", toString('x'));
      assertEquals("50", toString(50.0));
      assertEquals("2.5", toString(2.5f));
      assertEquals("1e+21", toString(1e21));
      assertEquals("0.1", toString(new BigDecimal("0.1")));
      assertEquals("NaN", toString(Double.NaN));
      assertEquals("-Infinity", toString(Double.NEGATIVE_INFINITY));
   }

   @Test
   void arraysAreJoinedLikeAJavaScriptArray() {
      assertEquals("1,2.5,a", toString(new Object[] { 1, 2.5, "a" }));
      // a null element is empty, as in [1,null,2].toString()
      assertEquals("1,,2", toString(new Object[] { 1, null, 2 }));
      // nested arrays are flattened by the join
      assertEquals("1,2,3", toString(new Object[] { 1, new int[] { 2, 3 } }));
      assertEquals("1,2,3", toString(new Object[] { new Object[] { 1, 2 }, 3 }));
      assertEquals("0.5,50", toString(new double[] { 0.5, 50.0 }));
      assertEquals("true,false", toString(new boolean[] { true, false }));
      assertEquals("", toString(new Object[0]));
   }
}
