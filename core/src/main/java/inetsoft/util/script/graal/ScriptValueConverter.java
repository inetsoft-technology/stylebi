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

import inetsoft.util.script.graal.pool.ForeignRef;
import inetsoft.util.script.graal.pool.WsValueCopier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

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
      if(value == OwnedVarScope.UNDEFINED) {
         return undefinedValue();
      }

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

      // The session principal (parameter.__principal__ / ThreadContext principal)
      // is handed to scripts read-only: its public setters and the mutable
      // internals its getters expose would otherwise let a script change the live
      // identity the rest of the session -- permission checks, the query sandbox,
      // VPM -- trusts. (Bug #77255, Bug #77256)
      if(value instanceof Principal) {
         return ReadOnlyPrincipalProxy.wrap((Principal) value);
      }

      // inside a pooled worksheet exec, a value of another context is marked foreign (bug
      // #76960, spec §14.11); with no pooled exec on this thread it is returned unchanged
      if(value instanceof Value v) {
         return WsValueCopier.markForeign(v);
      }

      // a class value gives a script the class's statics and constructors, so it is
      // handed out only for a class the script could look up by name (Bug #77521)
      if(value instanceof Class<?> || value instanceof Class<?>[]) {
         checkClassValue(value);
      }

      return value;
   }

   private static void checkClassValue(Object value) {
      Class<?>[] types = value instanceof Class<?>[] array ?
         array : new Class<?>[] { (Class<?>) value };
      Predicate<String> filter = null;

      for(Class<?> type : types) {
         if(type == null) {
            continue;
         }

         if(filter == null) {
            filter = ScriptHostAccess.classFilter();
         }

         if(!ScriptHostAccess.isClassValueVisible(type, filter)) {
            throw new SecurityException(
               "Access to host class " + type.getName() + " is not allowed.");
         }
      }
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

         // a script that passes the principal back to one of our functions, or
         // stores it in a scope, gets the real principal on the host side; only
         // the script's own reads/writes are constrained. (Bug #77255, #77256)
         if(proxy instanceof ReadOnlyPrincipalProxy principalProxy) {
            return principalProxy.getTarget();
         }

         // a pooled worksheet context's reference to another context's value is that live
         // value, never a copy (bug #76960, spec §14.11); pool-off never creates one
         if(proxy instanceof ForeignRef ref) {
            return WsValueCopier.foreign(ref);
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

      // a pooled worksheet context's plain object becomes a host copy (bug #76960, spec
      // §14.12 A1/A3); everywhere else, and for functions/non-plain objects, main's raw Value
      return WsValueCopier.detach(v);
   }

   /**
    * Convert a guest value that host code keeps (a scope, array or chain write). Outside a
    * pooled worksheet context this is {@link #toHost(Value)}; inside one, a function or
    * non-plain object, which cannot be kept apart from its context, is rejected with a clear
    * error (bug #76960, spec §14.12 A2).
    */
   public static Object toHostStored(Value v) {
      return WsValueCopier.checkStorable(toHost(v));
   }

   /**
    * The stored form of a script write of a var an {@link OwnedVarScope} owns (Testing
    * #77123): a primitive as its Java value, a number as a {@code Double} that keeps NaN and
    * Infinity (which {@link #toHost} turns into null), a host or proxy value unwrapped, and a
    * script object (Date, array, object, function) as the guest value itself, so it keeps its
    * identity and in-place changes. A guest value is valid only on its own context.
    */
   public static Object toOwnedVar(Value v) {
      if(v == null) {
         return null;
      }

      // undefined stays undefined (Testing #77123 B-1): both read isNull()
      if(v.isNull()) {
         return isUndefined(v) ? OwnedVarScope.UNDEFINED : null;
      }

      if(v.isBoolean() || v.isString() || v.isHostObject() || v.isProxyObject()) {
         return toHost(v);
      }

      if(v.isNumber() && v.fitsInDouble()) {
         return v.asDouble();
      }

      return v;
   }

   /**
    * @return whether {@code v} is JS {@code undefined}, not {@code null}: both are
    * {@link Value#isNull()}, only their string forms differ.
    */
   static boolean isUndefined(Value v) {
      return v.isNull() && "undefined".equals(v.toString());
   }

   /**
    * @return the {@code undefined} of the context executing on this thread, so a
    * proxy member reads as {@code undefined} and not as {@code null} (a Java
    * {@code null} member reads as {@code null}); {@code null} when no context is entered.
    */
   private static Object undefinedValue() {
      try {
         return Context.getCurrent().getBindings("js").getMember("undefined");
      }
      catch(IllegalStateException ex) {
         return null;
      }
   }

   /**
    * Convert an exec result, which lenses and conditions keep (spec §14.12 A4).
    */
   public static Object toHostResult(Value v) {
      return toHostStored(v);
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
