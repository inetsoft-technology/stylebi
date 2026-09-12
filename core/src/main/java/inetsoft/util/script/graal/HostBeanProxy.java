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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import inetsoft.graph.EGraph;
import inetsoft.graph.element.GraphElement;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restores Rhino-style JavaBeans property access for the graph host objects that
 * chart binding scripts manipulate directly ({@link EGraph} and its
 * {@link GraphElement}s).
 *
 * <p>Rhino's {@code NativeJavaObject} automatically mapped bean syntax
 * ({@code element.endArrow = true} to {@code setEndArrow(true)},
 * {@code element.endArrow} to {@code isEndArrow()}). GraalJS's {@code HostAccess}
 * exposes only the raw accessor methods ({@code isEndArrow}/{@code setEndArrow}) and
 * does <em>not</em> synthesize a {@code endArrow} property, so a Rhino-era script
 * that assigns {@code element.endArrow = true} silently no-ops. This proxy re-adds
 * the getter/setter&harr;property mapping <em>on top of</em> GraalJS's native host
 * member handling. (#75577)
 *
 * <p>Method calls, overload resolution, and argument coercion are delegated to
 * GraalJS (via {@link Value#invokeMember}) so behavior matches a bare host object;
 * this proxy only adds bean-property get/set. Return values that are themselves
 * wrappable graph objects (e.g. {@code graph.getElement(i)}) are wrapped again so
 * bean access is available on them too, and wrapped arguments passed back into a
 * host method (e.g. {@code graph.addElement(graph.getElement(0))}) are unwrapped
 * to their target first. Wrappers are interned per target (identity cache) so
 * repeated access to the same graph object yields the same proxy, preserving JS
 * reference equality ({@code ===}).
 *
 * <p><b>Known limitation:</b> because the object is presented to the script as a
 * {@code ProxyObject} rather than a raw host object, a Java {@code instanceof}
 * check ({@code elem instanceof Java.type('inetsoft.graph.element.LineElement')})
 * evaluates to {@code false} for a wrapped object. The {@code ProxyObject} SPI has
 * no hook to answer host {@code instanceof}. Scripts should branch on behavior
 * (method/property presence) rather than Java type.
 *
 * <p><b>Known limitation:</b> argument unwrapping only inspects the top-level
 * argument {@code Value}. A wrapper nested inside an array/{@code List}/varargs
 * argument would reach the host call still wrapped. This is not exercised by the
 * current API surface (no {@code EGraph}/{@code GraphElement} method takes a
 * collection of graph elements), but a future method that does would need to
 * unwrap collection elements as well.
 */
public final class HostBeanProxy implements ProxyObject {
   // Intern wrappers per underlying object so that repeated access to the same
   // graph object returns the same proxy (JS === / identity). Weak keys (identity
   // comparison) and weak values let transient per-render graph objects and their
   // wrappers be collected once no script/host reference remains.
   private static final Cache<Object, HostBeanProxy> WRAPPERS =
      Caffeine.newBuilder().weakKeys().weakValues().build();

   private final Object target;

   private HostBeanProxy(Object target) {
      this.target = Objects.requireNonNull(target);
   }

   /**
    * Wrap a graph host object, reusing an existing wrapper for the same target so
    * script-level reference equality is preserved.
    */
   public static HostBeanProxy wrap(Object target) {
      return WRAPPERS.get(target, HostBeanProxy::new);
   }

   /** The wrapped host object. */
   public Object getTarget() {
      return target;
   }

   /**
    * Whether a host value is a graph object whose bean properties scripts expect
    * to read/write directly. Kept narrow on purpose: only the objects chart
    * scripts build and mutate ({@link EGraph}, {@link GraphElement}).
    */
   public static boolean shouldWrap(Object value) {
      return value instanceof EGraph || value instanceof GraphElement;
   }

   /** Wrap a host value if it is a graph object; otherwise return it unchanged. */
   private static Object wrapResult(Value result) {
      if(result != null && result.isHostObject()) {
         Object host = result.asHostObject();

         if(shouldWrap(host)) {
            return wrap(host);
         }
      }

      return result;
   }

   /** Unwrap a HostBeanProxy argument back to its target before a host call. */
   private static Object unwrapArg(Value arg) {
      if(arg != null && arg.isProxyObject()) {
         Object proxy = arg.asProxyObject();

         if(proxy instanceof HostBeanProxy) {
            return ((HostBeanProxy) proxy).getTarget();
         }
      }

      return arg;
   }

   /** GraalJS-native view of the target, for delegating methods/fields. */
   private Value hostValue() {
      return Context.getCurrent().asValue(target);
   }

   @Override
   public boolean hasMember(String key) {
      Beans beans = Beans.of(target.getClass());
      return hostValue().hasMember(key) || beans.getters.containsKey(key) ||
         beans.setters.containsKey(key);
   }

   @Override
   public Object getMember(String key) {
      Value host = hostValue();

      // A native member that is a method: return an executable that delegates to
      // the host method (preserving overload resolution + coercion) and wraps the
      // result. Reads unwrap any proxied arguments first.
      if(host.hasMember(key) && host.getMember(key).canExecute()) {
         return (ProxyExecutable) args -> {
            Object[] hostArgs = new Object[args.length];

            for(int i = 0; i < args.length; i++) {
               hostArgs[i] = unwrapArg(args[i]);
            }

            return wrapResult(host.invokeMember(key, hostArgs));
         };
      }

      // Bean read: element.endArrow -> isEndArrow()/getEndArrow().
      String getter = Beans.of(target.getClass()).getters.get(key);

      if(getter != null) {
         return wrapResult(host.invokeMember(getter));
      }

      // Native public field. Reached only when no bean getter matched above, so a
      // same-named bean accessor deliberately takes precedence over a native field.
      if(host.hasMember(key)) {
         return wrapResult(host.getMember(key));
      }

      return null;
   }

   @Override
   public void putMember(String key, Value value) {
      // Bean write: element.endArrow = true -> setEndArrow(true). Delegated by name
      // so GraalJS resolves the setter overload and coerces the argument.
      String setter = Beans.of(target.getClass()).setters.get(key);

      if(setter != null) {
         hostValue().invokeMember(setter, unwrapArg(value));
         return;
      }

      // Native writable field. Reached only when no bean setter matched above, so a
      // same-named bean accessor deliberately takes precedence over a native field.
      Value host = hostValue();

      if(host.hasMember(key)) {
         host.putMember(key, value);
      }
   }

   @Override
   public Object getMemberKeys() {
      Set<String> keys = new LinkedHashSet<>();
      Set<String> nativeKeys = hostValue().getMemberKeys();

      if(nativeKeys != null) {
         keys.addAll(nativeKeys);
      }

      Beans beans = Beans.of(target.getClass());
      keys.addAll(beans.getters.keySet());
      keys.addAll(beans.setters.keySet());

      return keys.toArray(new String[0]);
   }

   @Override
   public boolean removeMember(String key) {
      // host object members cannot be removed
      return false;
   }

   /**
    * Per-class cache of JavaBeans getter/setter accessor names, keyed by property
    * name. Only accessor <em>names</em> are stored; the actual invocation goes
    * through {@link Value#invokeMember}, which resolves overloads from the real
    * argument types.
    */
   private static final class Beans {
      final Map<String, String> getters;
      final Map<String, String> setters;

      private Beans(Map<String, String> getters, Map<String, String> setters) {
         this.getters = getters;
         this.setters = setters;
      }

      static Beans of(Class<?> cls) {
         return CACHE.computeIfAbsent(cls, Beans::introspect);
      }

      private static Beans introspect(Class<?> cls) {
         Map<String, String> getters = new HashMap<>();
         Map<String, String> setters = new HashMap<>();

         for(Method m : cls.getMethods()) {
            int mod = m.getModifiers();

            if(!Modifier.isPublic(mod) || Modifier.isStatic(mod)) {
               continue;
            }

            // Skip methods declared on Object (notably getClass(), which would
            // otherwise register a "class" bean property; invoking it is blocked
            // by the HostAccess policy and would surface as an error during
            // generic member enumeration, e.g. JSON.stringify). (#75577)
            if(m.getDeclaringClass() == Object.class) {
               continue;
            }

            String name = m.getName();

            if(m.getParameterCount() == 0 && m.getReturnType() != void.class) {
               if(name.length() > 3 && name.startsWith("get")) {
                  getters.putIfAbsent(decapitalize(name.substring(3)), name);
               }
               else if(name.length() > 2 && name.startsWith("is") &&
                  (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class))
               {
                  getters.putIfAbsent(decapitalize(name.substring(2)), name);
               }
            }
            else if(m.getParameterCount() == 1 && name.length() > 3 && name.startsWith("set")) {
               setters.putIfAbsent(decapitalize(name.substring(3)), name);
            }
         }

         return new Beans(getters, setters);
      }

      // JavaBeans decapitalization: leading upper-case run of length >= 2 (an
      // acronym) is left as-is; otherwise the first char is lower-cased.
      private static String decapitalize(String name) {
         if(name.isEmpty() || (name.length() > 1 && Character.isUpperCase(name.charAt(1)) &&
            Character.isUpperCase(name.charAt(0))))
         {
            return name;
         }

         char[] chars = name.toCharArray();
         chars[0] = Character.toLowerCase(chars[0]);
         return new String(chars);
      }

      private static final Map<Class<?>, Beans> CACHE = new ConcurrentHashMap<>();
   }
}
