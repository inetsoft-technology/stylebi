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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for the fractional-number coercion gap: a script passing a
 * computed, non-integral number to a host constructor or method went through
 * GraalJS's own interop (not {@link ScriptFunction#execute}), which refuses
 * {@code double -> float} and {@code double -> int} as a lossy primitive
 * coercion. Rhino narrowed such a value to whatever primitive the selected
 * overload declared, so viewsheet scripts doing
 * {@code new java.awt.Color(0.57, 0.80, 0.24)} or
 * {@code new java.awt.Dimension(60.96, 60.96)} failed with
 * "Invalid argument when instantiating ... with arguments [java.lang.Double, ...]".
 */
@Tag("core")
class ScriptHostAccessNumberCoercionTest {
   /** float and double overloads of the same name -- the double must still win. */
   public static class NumericOverloads {
      public String got;

      public void foo(double d) {
         got = "double:" + d;
      }

      public void foo(float f) {
         got = "float:" + f;
      }
   }

   /** A single float signature, the common shape in report/uql APIs. */
   public static class FloatParam {
      public float got = Float.NaN;

      public void setWidth(float w) {
         got = w;
      }
   }

   /** A single int signature -- the java.awt.Dimension shape. */
   public static class IntParam {
      public int got = -1;

      public void setWidth(int w) {
         got = w;
      }
   }

   /** The #76778 shape: int and String overloads of the same name. */
   public static class IntOrString {
      public String got;

      public void v(int i) {
         got = "int:" + i;
      }

      public void v(String s) {
         got = "String:" + s;
      }
   }

   /** A single double signature -- must keep every digit. */
   public static class DoubleParam {
      public double got = Double.NaN;

      public void setRatio(double r) {
         got = r;
      }
   }

   /** A single long signature. */
   public static class LongParam {
      public long got = -1;

      public void setTime(long t) {
         got = t;
      }
   }

   private Context context() {
      return Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(ScriptHostAccess.classFilter(Set.of(), new String[0], true))
         .build();
   }

   /** Evaluate and read the members inside the Context, which closes on exit. */
   private int[] evalInts(String js, String... members) {
      try(Context c = context()) {
         Value v = c.eval("js", js);
         int[] got = new int[members.length];

         for(int i = 0; i < members.length; i++) {
            String m = members[i];
            got[i] = m.endsWith("()")
               ? v.invokeMember(m.substring(0, m.length() - 2)).asInt()
               : v.getMember(m).asInt();
         }

         return got;
      }
   }

   private void eval(String js, Object target) {
      try(Context c = context()) {
         c.getBindings("js").putMember("t", target);
         c.eval("js", js);
      }
   }

   /**
    * Assert the script fails because GraalJS could not bind the argument, not
    * for some unrelated reason. A bare assertThrows(Exception.class) would also
    * pass on a typo in the script or an unbound receiver, which would make these
    * guards silently worthless -- they exist to pin the cases where the mapping
    * must NOT fire.
    */
   private void assertRejected(String js, Object target, String expected) {
      PolyglotException ex =
         assertThrows(PolyglotException.class, () -> eval(js, target));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains(expected),
                 "expected a host-interop failure mentioning \"" + expected
                    + "\", got: " + ex.getMessage());
   }

   // ---------------------------------------------------------------- reported

   @Test
   void fractionalComponentsSelectTheFloatColorConstructor() {
      // gallery/Maintenance Dashboard Static, Text1: Color(float,float,float)
      // takes 0..1 components, which is exactly what these are.
      int[] rgb = evalInts(
         "var C = Java.type('java.awt.Color');" +
            "new C(0.5686274509803921, 0.796078431372549, 0.24313725490196078)",
         "getRed()", "getGreen()", "getBlue()");
      assertArrayEquals(new int[]{ 145, 203, 62 }, rgb);
   }

   @Test
   void fractionalArgumentsNarrowToTheOnlyIntConstructor() {
      // gallery/Insurance Fullline, Chart2: Dimension has no (double,double)
      // overload, so 60.96 must narrow to int as it did under Rhino.
      int[] size = evalInts("var D = Java.type('java.awt.Dimension'); new D(60.96, 60.96)",
                            "width", "height");
      assertArrayEquals(new int[]{ 60, 60 }, size);
   }

   // ------------------------------------------------------------ host methods

   @Test
   void fractionalValueBindsToAFloatParameter() {
      // 0.1 does not round-trip through a float, so GraalJS refused it outright
      FloatParam t = new FloatParam();
      eval("t.setWidth(0.1);", t);
      assertEquals(0.1f, t.got);
   }

   @Test
   void fractionalValueNarrowsToAnIntParameter() {
      IntParam t = new IntParam();
      eval("t.setWidth(60.96);", t);
      assertEquals(60, t.got);
   }

   @Test
   void narrowingToAnIntTruncatesTowardZero() {
      // Rhino truncated; it did not round. 60.96 -> 60, -60.96 -> -60
      IntParam t = new IntParam();
      eval("t.setWidth(-60.96);", t);
      assertEquals(-60, t.got);
   }

   @Test
   void fractionalValueNarrowsToALongParameter() {
      LongParam t = new LongParam();
      eval("t.setTime(60.96);", t);
      assertEquals(60L, t.got);
   }

   @Test
   void valueTooLargeForAnIntIsRejectedRatherThanClamped() {
      // without the range guard Double::intValue would silently hand the method
      // Integer.MAX_VALUE, which is worse than the loud failure it replaces
      assertRejected("t.setWidth(6e10 + 0.5);", new IntParam(),
                     "Invalid or lossy primitive coercion");
   }

   @Test
   void wholeValueTooLargeForAnIntIsRejectedRatherThanClamped() {
      // the pre-existing whole-number Double -> Integer mapping had no range
      // check, so a whole 1e30 was handed to the method as Integer.MAX_VALUE by
      // Java's narrowing cast -- silent corruption of the same kind the
      // fractional mapping guards against
      assertRejected("t.setWidth(1e30);", new IntParam(),
                     "Invalid or lossy primitive coercion");
   }

   // -------------------------------------------------------- regression guard

   @Test
   void wholeNumbersStillSelectTheIntColorConstructor() {
      // the whole-number Double -> Integer mapping still owns this case; a
      // fractional-only predicate on the new mappings is what keeps them apart
      int[] rgb = evalInts("var C = Java.type('java.awt.Color'); new C(255, 0, 0)",
                           "getRed()", "getGreen()");
      assertArrayEquals(new int[]{ 255, 0 }, rgb);
   }

   @Test
   void aFloatAndDoubleOverloadPairResolvesAsItDidBefore() {
      // Both literals here are float-exact, so both overloads are applicable at
      // the lossless tier and GraalJS picks the narrower one -- as it already
      // did before the new mappings existed. Note this does NOT exercise the LOW
      // float mapping, which only fires for a value that is not float-exact;
      // aNonFloatExactValueStillPrefersADoubleOverloadOverAFloatOne is the test
      // that covers that. This one just pins the pre-existing behaviour so a
      // change to it would be visible.
      NumericOverloads t = new NumericOverloads();
      eval("t.foo(1.5);", t);
      assertEquals("float:1.5", t.got);

      t = new NumericOverloads();
      eval("t.foo(2);", t);
      assertEquals("float:2.0", t.got);
   }

   @Test
   void aDoubleOnlyParameterKeepsFullPrecision() {
      // the float mapping must not reach a parameter that can take the value as
      // it stands
      DoubleParam t = new DoubleParam();
      eval("t.setRatio(0.1234567890123);", t);
      assertEquals(0.1234567890123, t.got);
   }

   @Test
   void aNonFloatExactValueStillPrefersADoubleOverloadOverAFloatOne() {
      // the case the LOW precedence exists for: 60.96 does not round-trip
      // through a float, so only the double candidate is applicable at the
      // lossless tier and the float mapping is never reached
      NumericOverloads t = new NumericOverloads();
      eval("t.foo(60.96);", t);
      assertEquals("double:60.96", t.got);
   }

   @Test
   void wholeNumberStillBindsToAStringOnlyMethod() {
      // #76778 must survive the new LOWEST mappings
      IntOrString t = new IntOrString();
      eval("t.v(7);", t);
      assertEquals("int:7", t.got);

      t = new IntOrString();
      eval("t.v('abc');", t);
      assertEquals("String:abc", t.got);
   }

   @Test
   void fractionalValueAgainstIntAndStringOverloadsIsAmbiguous() {
      // Accepted behaviour change, pinned so it stays a conscious decision: the
      // new Double -> Integer mapping now makes foo(int) applicable at the same
      // final tier as the Number -> String mapping, so neither wins. Previously
      // this quietly called v("1.5"). Only index-or-name accessors have this
      // shape, where a fractional argument is meaningless either way.
      // must be the AMBIGUITY error specifically -- "no applicable overload
      // found" would mean the mappings stopped firing altogether
      assertRejected("t.v(1.5);", new IntOrString(), "Multiple applicable overloads");
   }

   @Test
   void nonFiniteValuesAreStillRejected() {
      // NaN/Infinity are excluded from the predicate on purpose: silently
      // narrowing them to 0 would hide a broken formula
      assertRejected("t.setWidth(0/0);", new IntParam(),
                     "Invalid or lossy primitive coercion");
      assertRejected("t.setWidth(1/0);", new IntParam(),
                     "Invalid or lossy primitive coercion");
   }
}
