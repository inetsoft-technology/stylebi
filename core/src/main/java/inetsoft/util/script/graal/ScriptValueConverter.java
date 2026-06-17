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
         // preserve legacy behavior: NaN -> 0
         return Double.isNaN(d) ? 0.0 : d;
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
