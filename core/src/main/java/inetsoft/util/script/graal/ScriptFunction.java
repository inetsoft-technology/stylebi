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
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A callable script function backed by a Java method, invoked reflectively.
 * Replaces the Rhino FunctionObject/FunctionObject2 native-binding mechanism.
 *
 * <p>If the bound method is static it is invoked with a null receiver; if it
 * is an instance method it is invoked on the supplied target object (typically
 * the scope object that registered the function via
 * {@code addFunctionProperty}).
 */
public class ScriptFunction implements ProxyExecutable {
   /**
    * @param target the receiver for instance methods (may be null for static).
    * @param cls    the class declaring the method.
    * @param name   the method name.
    * @param params the method parameter types.
    */
   public ScriptFunction(Object target, Class<?> cls, String name, Class<?>... params) {
      this.target = target;
      this.method = findMethod(cls, name, params);
      this.name = name;
   }

   /**
    * Create a script function from an already-resolved method.
    *
    * @param target the receiver for instance methods (may be null for static).
    * @param method the method to invoke.
    */
   public ScriptFunction(Object target, Method method) {
      this.target = target;
      this.method = method;
      this.name = method == null ? null : method.getName();
   }

   private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
      try {
         return cls.getMethod(name, params);
      }
      catch(NoSuchMethodException e) {
         LOG.error("Failed to get method: " + name + " in " + cls, e);
         return null;
      }
   }

   @Override
   public Object execute(Value... arguments) {
      if(method == null) {
         throw new IllegalStateException("Script function not found: " + name);
      }

      try {
         Class<?>[] ptypes = method.getParameterTypes();
         Object[] args = new Object[ptypes.length];

         for(int i = 0; i < ptypes.length; i++) {
            Object host = (i < arguments.length)
               ? ScriptValueConverter.toHost(arguments[i]) : null;
            args[i] = coerce(host, ptypes[i]);
         }

         Object receiver = Modifier.isStatic(method.getModifiers()) ? null : target;
         Object result = method.invoke(receiver, args);
         return ScriptValueConverter.toGuest(result);
      }
      catch(Exception ex) {
         throw new RuntimeException("Failed to invoke script function: " + name, ex);
      }
   }

   /**
    * Coerce a host argument to the declared parameter type. GraalJS surfaces all
    * script numbers as {@code Double} (via {@link ScriptValueConverter#toHost}),
    * so a method declaring a narrower primitive (e.g. {@code int}, {@code long},
    * {@code float}) would otherwise fail reflective {@code Method.invoke} with an
    * argument-type mismatch. This narrows a {@code Number} to the target numeric
    * primitive/wrapper; other values are passed through unchanged.
    *
    * <p>A {@code null} value for a primitive parameter is coerced to that
    * primitive's Java default ({@code false}, {@code 0}, {@code '\0'}) rather
    * than passed through. {@code null} reaches here when a script omits a
    * trailing argument or passes {@code undefined}/{@code null} (e.g. a library
    * function calling {@code setActionVisible('Edit')} on the two-arg
    * {@code setActionVisible(String, boolean)}); reflective invocation cannot
    * unbox {@code null} to a primitive, so this mirrors Rhino's FunctionObject
    * argument conversion, where a missing/undefined boolean meant {@code false}
    * (Bug #75525).
    */
   private static Object coerce(Object value, Class<?> ptype) {
      if(value == null) {
         if(ptype == boolean.class) {
            return false;
         }
         else if(ptype == int.class) {
            return 0;
         }
         else if(ptype == long.class) {
            return 0L;
         }
         else if(ptype == double.class) {
            return 0d;
         }
         else if(ptype == float.class) {
            return 0f;
         }
         else if(ptype == short.class) {
            return (short) 0;
         }
         else if(ptype == byte.class) {
            return (byte) 0;
         }
         else if(ptype == char.class) {
            return '\0';
         }

         return null;
      }

      // A numeric parameter may receive a value that is not already a Number,
      // e.g. the numeric string "3" from CALC.edate(date, '3'). Rhino's
      // FunctionObject coerced such arguments via ScriptRuntime.toNumber; without
      // this, reflective invocation fails with an argument-type mismatch
      // ("Failed to invoke script function"). Only convert when the declared
      // parameter is numeric so string/Object parameters keep their value.
      Number n = (value instanceof Number) ? (Number) value
         : (isNumericType(ptype) ? toNumber(value) : null);

      if(n != null) {
         if(ptype == int.class || ptype == Integer.class) {
            return n.intValue();
         }
         else if(ptype == long.class || ptype == Long.class) {
            return n.longValue();
         }
         else if(ptype == double.class || ptype == Double.class) {
            return n.doubleValue();
         }
         else if(ptype == float.class || ptype == Float.class) {
            return n.floatValue();
         }
         else if(ptype == short.class || ptype == Short.class) {
            return n.shortValue();
         }
         else if(ptype == byte.class || ptype == Byte.class) {
            return n.byteValue();
         }
         else if(ptype == char.class || ptype == Character.class) {
            return (char) n.intValue();
         }
      }

      return value;
   }

   /**
    * Whether the given parameter type is a numeric primitive or wrapper that a
    * script number (or numeric string/boolean) should be coerced to.
    */
   private static boolean isNumericType(Class<?> ptype) {
      return ptype == int.class || ptype == Integer.class ||
         ptype == long.class || ptype == Long.class ||
         ptype == double.class || ptype == Double.class ||
         ptype == float.class || ptype == Float.class ||
         ptype == short.class || ptype == Short.class ||
         ptype == byte.class || ptype == Byte.class ||
         ptype == char.class || ptype == Character.class;
   }

   /**
    * Convert a non-Number host value to a Number using JavaScript {@code toNumber}
    * semantics (mirroring Rhino's {@code ScriptRuntime.toNumber}): a boolean maps
    * to 1/0; a string is parsed ("" → 0, unparseable → NaN). Any other type
    * returns {@code null} so the caller passes the value through unchanged.
    */
   private static Number toNumber(Object value) {
      if(value instanceof Number) {
         return (Number) value;
      }
      else if(value instanceof Boolean) {
         return ((Boolean) value) ? 1 : 0;
      }
      else if(value instanceof CharSequence) {
         String s = value.toString().trim();

         if(s.isEmpty()) {
            return 0;
         }

         try {
            return Double.parseDouble(s);
         }
         catch(NumberFormatException ex) {
            return Double.NaN;
         }
      }

      return null;
   }

   private final Object target;
   private final Method method;
   private final String name;

   private static final Logger LOG = LoggerFactory.getLogger(ScriptFunction.class);
}
