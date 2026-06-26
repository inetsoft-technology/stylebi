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
package inetsoft.util.script.graal;

import org.graalvm.polyglot.Value;
import java.time.Instant;
import java.util.*;

/**
 * Converts GraalJS guest values to host (Java) values and vice-versa.
 * Centralizes the coercion rules that previously lived in
 * JavaScriptEngine.unwrap() and Context.javaToJS().
 */
public final class ScriptValueConverter {
   private ScriptValueConverter() {
   }

   /**
    * Wrap a host value for hand-off to GraalJS. Centralizes the decision of
    * which proxy adapter to use: a {@link ScriptArrayScope} is wrapped as an
    * {@link ArrayProxy} (so it exposes JS array semantics), any other
    * {@link ScriptScope} as a {@link ScopeProxy}, and all other values are
    * returned unchanged (GraalJS auto-wraps them per the HostAccess policy).
    *
    * <p><b>Null contract:</b> a Java {@code null} is returned as-is, which
    * GraalJS surfaces to script as {@code undefined}. This means a member
    * holding a real {@code null} value is indistinguishable from an absent
    * member at the script level — matching the long-standing Rhino behavior
    * where a scope returning {@code null}/{@code NOT_FOUND}/{@code Undefined}
    * all read as undefined. Scripts should use {@code == null} (loose), which
    * treats {@code null} and {@code undefined} alike, rather than
    * {@code === undefined}.
    */
   public static Object toGuest(Object value) {
      if(value instanceof ScriptArrayScope) {
         return new ArrayProxy((ScriptArrayScope) value);
      }

      if(value instanceof ScriptScope) {
         return new ScopeProxy((ScriptScope) value);
      }

      return value;
   }

   /** Convert a guest Value to its host (Java) representation. */
   public static Object toHost(Value v) {
      if(v == null || v.isNull()) {
         return null;
      }

      if(v.isHostObject()) {
         return v.asHostObject();
      }

      if(v.isBoolean()) {
         return v.asBoolean();
      }

      if(v.isString()) {
         return v.asString();
      }

      if(v.isNumber()) {
         double d = v.asDouble();
         // preserve legacy behavior: NaN/Infinity -> null (ScriptUtil.unwrap)
         return (Double.isNaN(d) || Double.isInfinite(d)) ? null : d;
      }

      if(v.isDate() || v.isInstant()) {
         Instant inst = v.asInstant();
         return new Date(inst.toEpochMilli());
      }

      if(v.hasArrayElements()) {
         long n = v.getArraySize();

         if(n > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Array too large to convert: " + n);
         }

         Object[] arr = new Object[(int) n];

         for(int i = 0; i < n; i++) {
            arr[i] = toHost(v.getArrayElement(i));
         }

         return arr;
      }

      // proxy/host wrappers and plain objects: hand back the raw value's
      // host object if present, else the Value itself for member access.
      if(v.isProxyObject()) {
         return v.asProxyObject();
      }

      return v;
   }
}
