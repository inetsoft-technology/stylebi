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

import inetsoft.util.script.graal.ScriptValueConverter;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.lang.reflect.Array;
import java.math.*;
import java.util.function.Supplier;

/**
 * Callable {@code valueOf}/{@code toString} members that let an output or input assembly
 * object used as a scalar ({@code Text1 >= 10}, {@code Text1 - 1}, {@code '' + Text1},
 * {@code String(Text1)}) coerce to the assembly's value. Under Rhino this was the
 * {@code getDefaultValue(Class)} override of the output and input scriptables; GraalJS coerces
 * a proxy object only through callable {@code valueOf}/{@code toString} members, so without
 * these the object coerced to its class@hash text, a NaN number (#78000). Same pattern as
 * {@code CalcRef} (#75593).
 * <p>
 * {@code valueOf} returns a JS primitive where the value has one, so {@code Text1 >= 10} and
 * {@code Text1 - 1} compare and compute on the number. {@code toString} always returns a string,
 * because the same member also answers an explicit {@code Text1.toString()} call, which a script
 * expects to give a string ({@code .length}, {@code .indexOf(...)}): a number is formatted as JS
 * formats it (a 50.0 value reads as {@code "50"}, not Java's {@code "50.0"}), and a null value
 * reads as {@code "null"}, as {@code String(null)} does. An array value (a check box's selected
 * objects) is joined with "," like a JS array, instead of leaking a Java array class@hash.
 */
final class ScalarCoercion {
   private ScalarCoercion() {
   }

   /**
    * Check if a member name is one of the coercion members.
    */
   static boolean isCoercionMember(String name) {
      return VALUE_OF.equals(name) || TO_STRING.equals(name);
   }

   /**
    * Create the coercion member. The value is read each time the member is called, so the
    * read sees the current value and any exception of the read reaches the script as for an
    * explicit {@code .value} read.
    *
    * @param name  {@code valueOf} or {@code toString}.
    * @param value reads the assembly's value.
    */
   static ProxyExecutable member(String name, Supplier<Object> value) {
      if(TO_STRING.equals(name)) {
         return args -> toStringResult(value.get());
      }

      return args -> toValueOfResult(value.get());
   }

   private static Object toValueOfResult(Object value) {
      if(value instanceof Number num) {
         return num.doubleValue();
      }

      if(value instanceof Character) {
         return value.toString();
      }

      if(value != null && value.getClass().isArray()) {
         return join(value);
      }

      // a non-primitive (e.g. a date) is not a primitive for JS, which then calls toString
      return ScriptValueConverter.toGuest(value);
   }

   private static String toStringResult(Object value) {
      if(value == null) {
         return "null";
      }

      if(value instanceof String str) {
         return str;
      }

      if(value instanceof Number num) {
         return numberToString(num.doubleValue());
      }

      if(value.getClass().isArray()) {
         return join(value);
      }

      return String.valueOf(value);
   }

   /**
    * Join the elements of an array as JS Array.prototype.toString does.
    */
   private static String join(Object array) {
      StringBuilder str = new StringBuilder();
      int len = Array.getLength(array);

      for(int i = 0; i < len; i++) {
         if(i > 0) {
            str.append(',');
         }

         Object elem = Array.get(array, i);

         if(elem == null) {
            continue;
         }

         if(elem instanceof Number num) {
            str.append(numberToString(num.doubleValue()));
         }
         else if(elem.getClass().isArray()) {
            str.append(join(elem));
         }
         else {
            str.append(elem);
         }
      }

      return str.toString();
   }

   /**
    * Format a number as JS Number::toString (ECMA-262 6.1.6.1.20) does: 50.0 -> "50",
    * 1e15 -> "1000000000000000", 1e21 -> "1e+21", 1e-7 -> "1e-7". Java's Double.toString
    * gives the shortest digits that round-trip (JDK 19+), which are the digits JS uses, except
    * that it writes two digits where one would do; otherwise only the layout differs.
    */
   static String numberToString(double d) {
      if(Double.isNaN(d)) {
         return "NaN";
      }

      if(d == 0) {
         return "0"; // also -0
      }

      if(Double.isInfinite(d)) {
         return d > 0 ? "Infinity" : "-Infinity";
      }

      if(d < 0) {
         return "-" + numberToString(-d);
      }

      BigDecimal dec = new BigDecimal(Double.toString(d)).stripTrailingZeros();

      // Double.toString writes at least two digits (d.d) even where one digit round-trips,
      // e.g. 4.9E-324 for Double.MIN_VALUE where JS writes 5e-324
      if(dec.precision() == 2) {
         BigDecimal one = dec.round(new MathContext(1, RoundingMode.HALF_EVEN));

         if(one.doubleValue() == d) {
            dec = one.stripTrailingZeros();
         }
      }

      String digits = dec.unscaledValue().toString();
      int k = digits.length();
      // the value is 0.digits * 10^n
      int n = k - dec.scale();

      if(k <= n && n <= 21) {
         return digits + "0".repeat(n - k);
      }

      if(0 < n && n <= 21) {
         return digits.substring(0, n) + "." + digits.substring(n);
      }

      if(-6 < n && n <= 0) {
         return "0." + "0".repeat(-n) + digits;
      }

      int exp = n - 1;
      String mantissa = k == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
      return mantissa + "e" + (exp < 0 ? "-" : "+") + Math.abs(exp);
   }

   private static final String VALUE_OF = "valueOf";
   private static final String TO_STRING = "toString";
}
