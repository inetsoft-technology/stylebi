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
 * Both members return a JS primitive where the value has one, so JS formats it: a 50.0 value
 * reads as {@code "50"}, not Java's {@code "50.0"}. An array value (a check box's selected
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

   private static Object toStringResult(Object value) {
      if(value == null || value instanceof String || value instanceof Boolean) {
         return value;
      }

      if(value instanceof Number num) {
         // JS formats the number: 50.0 -> "50"
         return num.doubleValue();
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

   private static String numberToString(double d) {
      if(d == Math.rint(d) && Math.abs(d) < 1e15) {
         return Long.toString((long) d);
      }

      return Double.toString(d);
   }

   private static final String VALUE_OF = "valueOf";
   private static final String TO_STRING = "toString";
}
