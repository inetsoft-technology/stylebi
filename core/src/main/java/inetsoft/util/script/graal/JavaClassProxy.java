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
import org.graalvm.polyglot.proxy.*;

/**
 * A resolved Java class leaf produced by the {@link LegacyJavaShim}. Wraps the
 * GraalVM host type (from {@code Java.type}) so that static members and {@code
 * new}-construction delegate to GraalVM's own reflection and overload
 * resolution, while adding Rhino's no-{@code new} construction: calling the
 * class as a function ({@code java.awt.Color(0xaed581)}) instantiates it.
 */
public final class JavaClassProxy implements ProxyObject, ProxyExecutable, ProxyInstantiable {
   private final Value hostType;
   private final String className;

   public JavaClassProxy(Value hostType, String className) {
      this.hostType = hostType;
      this.className = className;
   }

   String className() {
      return className;
   }

   @Override
   public Object getMember(String name) {
      // static fields, static methods, and nested types delegate to the host type.
      return hostType.hasMember(name) ? hostType.getMember(name) : null;
   }

   @Override
   public boolean hasMember(String name) {
      return hostType.hasMember(name);
   }

   @Override
   public Object getMemberKeys() {
      return hostType.getMemberKeys().toArray(new String[0]);
   }

   @Override
   public void putMember(String name, Value value) {
      // static field assignment, when the host type permits it.
      if(hostType.hasMember(name)) {
         hostType.putMember(name, value);
      }
   }

   @Override
   public Object execute(Value... arguments) {
      // Rhino allowed construction without `new`: java.awt.Color(0xaed581).
      return construct(arguments);
   }

   @Override
   public Object newInstance(Value... arguments) {
      return construct(arguments);
   }

   /**
    * Instantiate the host type, retrying once with numeric-string arguments
    * coerced to numbers if GraalVM's overload resolution rejects the call.
    *
    * <p>Rhino's {@code NativeJavaClass.construct} ran every argument through
    * {@code ScriptRuntime.toNumber} when scoring a numeric constructor
    * parameter, so a script could pass a numeric string where an {@code int} was
    * declared -- the common idiom being a hex colour built by concatenation,
    * {@code java.awt.Color('0x' + row['Cell Color'])}, which selected
    * {@code Color(int)}. GraalVM does no such conversion and fails the call with
    * "Invalid argument when instantiating", so the coercion is reproduced here.
    *
    * <p>Applied only as a retry after GraalVM has already rejected the
    * arguments, so a call that resolves natively keeps its existing overload --
    * this can never re-target a working call. The original exception is rethrown
    * if nothing could be coerced or the retry also fails, keeping the reported
    * error about the argument the script actually passed. (#75807)
    */
   private Object construct(Value... arguments) {
      try {
         return hostType.newInstance((Object[]) arguments);
      }
      catch(IllegalArgumentException ex) {
         Object[] coerced = coerceNumericStrings(arguments);

         if(coerced == null) {
            throw ex;
         }

         try {
            return hostType.newInstance(coerced);
         }
         catch(IllegalArgumentException ignore) {
            throw ex;
         }
      }
   }

   /**
    * Copy the arguments with every numeric-string element replaced by its
    * numeric value, or <tt>null</tt> if no element was a numeric string (in
    * which case a retry would be pointless).
    */
   private static Object[] coerceNumericStrings(Value[] arguments) {
      Object[] coerced = null;

      for(int i = 0; i < arguments.length; i++) {
         Value arg = arguments[i];
         Number num = arg != null && arg.isString() ? toNumber(arg.asString()) : null;

         if(num != null) {
            if(coerced == null) {
               // a genuine Object[] -- storing a Number into the Value[] that
               // clone() would return throws ArrayStoreException.
               coerced = new Object[arguments.length];
               System.arraycopy(arguments, 0, coerced, 0, arguments.length);
            }

            coerced[i] = num;
         }
      }

      return coerced;
   }

   /**
    * Parse a string as a JavaScript numeric literal: a {@code 0x}/{@code 0X}
    * prefixed hex integer, or a decimal number. An {@link Integer} is returned
    * when the value is integral and fits, so GraalVM's overload resolution
    * prefers an {@code int} parameter (e.g. {@code Color(int)}) over a
    * {@code float}/{@code double} one.
    *
    * <p>Unlike JavaScript {@code ToNumber}, an empty/blank string is not mapped
    * to {@code 0} and an unparseable string is not mapped to {@code NaN};
    * both return <tt>null</tt> so the argument is left alone and the caller
    * reports GraalVM's original argument-mismatch error rather than silently
    * constructing from a nonsense value.
    *
    * @return the parsed number, or <tt>null</tt> if the string is not numeric.
    */
   private static Number toNumber(String str) {
      String s = str.trim();

      if(s.isEmpty()) {
         return null;
      }

      try {
         // JS treats a leading zero as decimal ("010" is 10), so only an
         // explicit 0x/0X prefix is a radix change -- matching Rhino, which
         // likewise recognized only the unsigned hex form.
         if(s.length() > 2 && s.charAt(0) == '0' && (s.charAt(1) == 'x' || s.charAt(1) == 'X')) {
            return narrow(Long.parseLong(s.substring(2), 16));
         }

         return narrow(Double.parseDouble(s));
      }
      catch(NumberFormatException ex) {
         return null;
      }
   }

   /**
    * Return an integral value in {@code int} range as an {@link Integer}, so it
    * scores against an {@code int} parameter; anything else keeps its width.
    */
   private static Number narrow(double value) {
      if(value == Math.rint(value) && !Double.isInfinite(value) &&
         value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE)
      {
         return (int) value;
      }

      return value;
   }

   private static Number narrow(long value) {
      return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (int) value : value;
   }
}
