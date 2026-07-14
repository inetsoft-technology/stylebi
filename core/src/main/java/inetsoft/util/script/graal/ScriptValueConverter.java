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

import org.graalvm.polyglot.Context;
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

      // Restore Rhino-style bean-property access for graph objects that chart
      // scripts manipulate directly (EGraph/GraphElement); GraalJS's HostAccess
      // exposes only the raw getX/isX/setX accessor methods. (#75577)
      if(HostBeanProxy.shouldWrap(value)) {
         return HostBeanProxy.wrap(value);
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

      // Our own adapters first (the inverse of toGuest): ArrayProxy implements
      // both ProxyArray and ProxyObject, so it also satisfies hasArrayElements()
      // below — it must be unwrapped back to its ScriptArrayScope here, before
      // the generic array branch would flatten it into a plain Object[] copy.
      // Keeps toHost symmetric with toGuest so a published scope global reads
      // back as the real ScriptScope/ScriptArrayScope (e.g. senv.get("viewsheet")
      // returns the real ViewsheetScope in CalcTableLens, not the proxy bridge).
      if(v.isProxyObject()) {
         Object proxy = v.asProxyObject();

         if(proxy instanceof ScopeProxy scopeProxy) {
            return scopeProxy.getScope();
         }

         if(proxy instanceof ArrayProxy arrayProxy) {
            return arrayProxy.getScope();
         }

         // unwrap the graph bean proxy back to its host object (#75577)
         if(proxy instanceof HostBeanProxy) {
            return ((HostBeanProxy) proxy).getTarget();
         }

         return proxy;
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

      return v;
   }

   /**
    * If {@code value} is a foreign polyglot value representing a date/time,
    * return it as a {@link Date}; otherwise return {@code null}.
    *
    * <p>When a JS {@code Date} is coerced to an {@code Object} target (e.g. an
    * element of the {@code Object[][]} passed to
    * {@code new DefaultDataSet([["Date","Qty"],[new Date(),200]])}), GraalJS
    * hands it to host code as a foreign polyglot object (a {@code PolyglotMap}
    * object view) rather than a {@link Value} or a {@link Date}, so
    * {@link #toHost(Value)} never sees it and the value's date-ness is lost — a
    * {@code TimeScale} built over such a column then finds no dates and renders a
    * degenerate axis. Re-wrapping the object through the current context recovers
    * a {@link Value} whose {@code isDate()/isInstant()} report the date, matching
    * the Rhino behavior where a native JS Date unwrapped to a {@link Date}.
    * Requires an active polyglot context (script execution); returns {@code null}
    * when none is present. (#75633)
    */
   public static Date toHostDate(Object value) {
      // Only a foreign polyglot value needs date recovery (a JS Date coerced to an
      // Object target arrives as a com.oracle.truffle.polyglot.* object). Everything
      // else — nulls, scalars, ordinary host objects — is skipped cheaply so the
      // common non-script call sites never pay for (nor risk) a context lookup;
      // Context.getCurrent() throws when no context is entered. (#75633)
      if(value == null || !value.getClass().getName().startsWith("com.oracle.truffle.")) {
         return null;
      }

      try {
         Value v = Context.getCurrent().asValue(value);

         if(v.isDate() && v.isInstant()) {
            return new Date(v.asInstant().toEpochMilli());
         }
      }
      catch(Exception ignore) {
         // no context entered, or not a date-like value; fall through
      }

      return null;
   }
}
