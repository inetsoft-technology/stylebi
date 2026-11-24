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
package inetsoft.util.script.graal;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for #82093: raw host calls refused a numeric string for a
 * float / short / byte parameter and a JS Date for a String parameter, which
 * Rhino coerced. Follows #81889 / #8515.
 */
@Tag("core")
class ScriptHostAccessNarrowNumberDateTest {
   public static class D {
      public String a(String s) { return "S"; }
      public String a(java.time.Instant s) { return "I"; }
      public String b(Object s) { return "O"; }
      public String b(String s) { return "S"; }
      public String c(java.util.Date s) { return "D"; }
      public String c(String s) { return "S"; }
      public String d(Object s) { return "O"; }
      public String e(java.util.Date s) { return "D"; }
      public String f(CharSequence s) { return "CS"; }
      public String f(Object s) { return "O"; }
      public String onlyCs(CharSequence s) { return "CS"; }
      public String onlyF(float f) { return "F:" + f; }
      public String onlyS(String s) { return "S:" + s; }
      public String bs(boolean b) { return "B"; }
      public String bs(String s) { return "S"; }
      public String bf(boolean b) { return "B"; }
      public String bf(float f) { return "F"; }
      public String bsh(boolean b) { return "B"; }
      public String bsh(short s) { return "SH"; }
      public String bby(boolean b) { return "B"; }
      public String bby(byte s) { return "BY"; }
      public String bi(boolean b) { return "B"; }
      public String bi(int s) { return "I"; }
      public String os(Object o) { return "O"; }
      public String os(short s) { return "SH"; }
      public String of(Object o) { return "O"; }
      public String of(float f) { return "F"; }
      public String sd(String s) { return "S"; }
      public String sd(double s) { return "D"; }
   }

   private String str(String js) {
      try(Context c = Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(
            ScriptHostAccess.classFilter(java.util.Set.of(), new String[0], true))
         .build())
      {
         c.getBindings("js").putMember("d", new D());
         Value v = c.eval("js", js);
         return v == null || v.isNull() ? null : v.toString();
      }
   }

   private void refused(String js) {
      assertThrows(PolyglotException.class, () -> str(js), js);
   }

   private static final String MATH = "Java.type('java.lang.Math')";

   @Test
   void reporterCases() {
      assertEquals("0x1.8p0", str("Java.type('java.lang.Float').toHexString('1.5')"));
      assertEquals("1069547520",
                   str("Java.type('java.lang.Float').floatToIntBits('1.5')"));
      assertEquals("1280", str("Java.type('java.lang.Short').reverseBytes('5')"));
      assertEquals("5", str("Java.type('java.lang.Byte').toUnsignedInt('5')"));
      assertEquals(str("String(new Date(0))"),
                   str("new (Java.type('java.lang.StringBuilder'))(new Date(0))"));
   }

   @Test
   void doubleAndIntPathsUnchanged() {
      assertEquals("3.7", str(MATH + ".abs('-3.7')"));
      assertEquals("0.1", str(MATH + ".abs('0.1')"));
      assertEquals("255", str(
         "new (Java.type('java.awt.Color'))('255','0','0').getRed()"));
      assertEquals("5", str("Java.type('java.math.BigDecimal').valueOf('5')"));
   }

   @Test
   void shortAndByteRange() {
      String s = "Java.type('java.lang.Short').reverseBytes";
      String b = "Java.type('java.lang.Byte').toUnsignedInt";
      assertEquals("4096", str(s + "('0x10')"));
      refused(s + "('40000')");
      refused(s + "('1.5')");
      refused(s + "('')");
      refused(s + "('abc')");
      refused(b + "('200')");
      refused(b + "('1.5')");
      refused(b + "('')");
      refused(b + "('NaN')");
   }

   @Test
   void floatOnlyAcceptsExactStrings() {
      assertEquals("F:1.5", str("d.onlyF('1.5')"));
      // documented deviation from 13.8, which rounded these
      refused("d.onlyF('0.1')");
      refused("d.onlyF('16777217')");
      refused("d.onlyF('1e40')");
      refused("d.onlyF('abc')");
      refused("d.onlyF('')");
   }

   @Test
   void exactFloatStringPicksFloatOverloadLikeANumber() {
      // pinned: float wins for an exactly representable string, same as a number
      assertEquals(str(MATH + ".ulp(1.5)"), str(MATH + ".ulp('1.5')"));
   }

   @Test
   void dateToStringOnly() {
      assertEquals("S:" + str("String(new Date(0))"), str("d.onlyS(new Date(0))"));
      refused("d.onlyCs(new Date(0))");
   }

   @Test
   void hostDateToString() {
      assertEquals(new java.util.Date(0).toString(), str(
         "new (Java.type('java.lang.StringBuilder'))" +
         "(new (Java.type('java.util.Date'))(0))"));
   }

   @Test
   void dateDoesNotHijackOtherOverloads() {
      assertEquals("I", str("d.a(new Date(0))"));
      assertEquals("O", str("d.b(new Date(0))"));
      assertEquals("D", str("d.c(new Date(0))"));
      assertEquals("O", str("d.d(new Date(0))"));
      assertEquals("D", str("d.e(new Date(0))"));
      assertEquals("O", str("d.f(new Date(0))"));
   }

   @Test
   void booleanOverloadCollisions() {
      // Rhino never converted a string or a Date to boolean when another overload
      // applied, so these bind the non-boolean overload (main bound boolean)
      // a Date with a (boolean|String) pair is the one documented regression:
      // Date -> String stays LOWEST (a LOW one would make String|Instant ambiguous)
      refused("Java.type('java.lang.Boolean').valueOf(new Date(0))");
      refused("d.bs(new Date(0))");
      // numeric strings with a boolean overload: documented ambiguity, as
      // boolean|int (#8515); Rhino bound short/float, main bound boolean
      refused("d.bf('1.5')");
      refused("d.bsh('5')");
      refused("d.bby('5')");
      refused("d.bi('5')");
      assertEquals("B", str("d.bf('abc')"));
   }

   @Test
   void dateWithStringAndDoubleOverloads() {
      // #75423: the Instant -> double mapping still wins over Date -> String
      assertEquals("D", str("d.sd(new Date(0))"));
   }

   @Test
   void nextUpPinned() {
      assertEquals(str(MATH + ".nextUp(1.5)"), str(MATH + ".nextUp('1.5')"));
   }

   @Test
   void boundaries() {
      String s = "Java.type('java.lang.Short').reverseBytes";
      String b = "Java.type('java.lang.Byte').toUnsignedInt";
      assertEquals("128", str(s + "('-32768')"));
      refused(s + "('-32769')");
      assertEquals("-129", str(s + "('32767')")); // 0x7FFF reversed is 0xFF7F
      refused(s + "('32768')");
      assertEquals("128", str(b + "('-128')"));
      refused(b + "('-129')");
      assertEquals("127", str(b + "('127')"));
      refused(b + "('128')");
      assertEquals("255", str(b + "('-1')"));
      assertEquals("0x1.9p6", str("Java.type('java.lang.Float').toHexString('1e2')"));
   }

   @Test
   void objectOverloadStillWins() {
      assertEquals("1.50", str("Java.type('java.lang.String').valueOf('1.50')"));
      assertEquals("O", str("d.os('5')"));
      assertEquals("O", str("d.of('1.5')"));
   }

   @Test
   void numberAndStringAreNotConvertedToDate() {
      // #82139: documented refusal, pass new Date(n) instead
      refused("d.e(45000)");
      refused("d.e(1.5)");
      refused("d.e(NaN)");
      refused("d.e('2024-03-05')");
      assertEquals("D", str("d.e(new Date(45000))"));
   }

   @Test
   void numberIsNotInstanceOfDate() {
      assertEquals("false", str("0 instanceof Java.type('java.util.Date')"));
      assertEquals("false", str("45000 instanceof Java.type('java.util.Date')"));
      assertEquals("true", str("new (Java.type('java.util.Date'))(0) instanceof " +
                               "Java.type('java.util.Date')"));
   }

   @Test
   void timeScaleWithNumberFallsBackToFieldsNotARange() {
      // on current main (Date->String and Number->String at LOWEST) the call binds the
      // String... overload: a scale over fields, min/max never set, no Date range
      String ts = "new (Java.type('inetsoft.graph.scale.TimeScale'))(new Date(0), 45000)";
      assertEquals("0", str(ts + ".getMax()"));
      assertNotEquals("45000", str(ts + ".getMax()"));
   }

   @Test
   void timeScaleWithDatesIsTheWorkaround() {
      assertEquals("45000", str(
         "new (Java.type('inetsoft.graph.scale.TimeScale'))(new Date(0), " +
         "new Date(45000)).getMax()"));
   }

   @Test
   void dateAndStringPairKeepsStringForNumber() {
      assertEquals("S", str("d.c(45000)"));
      assertEquals("D", str("d.c(new Date(0))"));
   }
}
