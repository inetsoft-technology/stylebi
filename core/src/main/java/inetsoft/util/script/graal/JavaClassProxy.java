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
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.CharBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * A resolved Java class leaf produced by the {@link LegacyJavaShim}. Wraps the
 * GraalVM host type (from {@code Java.type}) so that static members and {@code
 * new}-construction delegate to GraalVM's own reflection and overload
 * resolution, while adding Rhino's no-{@code new} construction: calling the
 * class as a function ({@code java.awt.Color(0xaed581)}) instantiates it.
 */
public final class JavaClassProxy implements ProxyObject, ProxyExecutable, ProxyInstantiable {
   // the numeric literal forms toNumber() accepts -- JS Number()'s grammar,
   // less the NaN/Infinity words (see toNumber).
   private static final Pattern HEX_LITERAL = Pattern.compile("0[xX][0-9a-fA-F]+");
   private static final Pattern DECIMAL_LITERAL =
      Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");

   // the polyglot Engines whose shared constructor node is uncached (#82259).
   private static final Set<Engine> UNCACHED_ENGINES =
      Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
   private static final Logger LOG = LoggerFactory.getLogger(JavaClassProxy.class);

   private final Value hostType;
   private final String className;

   public JavaClassProxy(Value hostType, String className) {
      this.hostType = hostType;
      this.className = className;
   }

   String className() {
      return className;
   }

   /**
    * Make every later constructor call on the context's polyglot {@link Engine}
    * select its overload afresh, instead of reusing a cached selection that can
    * be wrong. Called once per Context build; does the work once per Engine.
    *
    * <p>Every {@link Value#newInstance} on a host class (this proxy's
    * {@link #instantiate}, the legacy shim's {@code new pkg.Cls(...)}) runs
    * through one shared Truffle host-call node per Engine, i.e. per JVM for
    * the shared script engine. That node's overload cache
    * ({@code HostExecuteNode.doOverloadedCached}, truffle 24.1.2) is unsafe
    * with our lenient String target mappings ({@link ScriptHostAccess}: Number,
    * Boolean and Date to String, #76778/#81889): when a call binds a varargs
    * overload such as {@code TimeScale(String...)} only through such a mapping
    * ({@code new TimeScale(date, 45000)}, #82139), the cached entry's argument
    * guard accepts any value a String mapping accepts, and the guard is built
    * without looking at the fixed-arity candidates. A later
    * {@code new TimeScale(date1, date2)} anywhere in the JVM then reuses the
    * {@code String...} constructor (getMax() 0, an AssertionError under -ea)
    * instead of {@code TimeScale(Date, Date)}. (#82259)
    *
    * <p>Truffle keeps at most {@code HostExecuteNode.LIMIT} (3 in truffle
    * 24.1.2) cached entries per node; one more distinct call shape makes the
    * DSL replace the cached specialization with
    * {@code doOverloadedUncached} for good, which runs the same overload
    * selection as a first call on every call. This drives the node past the
    * limit with eight constructions of {@link StringBuilder}, each with a
    * different argument type -- well above the limit, to leave a margin. The
    * work rests on that Truffle internal: revisit it when GraalVM is upgraded
    * ({@code JavaClassProxyOverloadCacheTest} fails once the limit clearly
    * outgrows the warm-up). The constructions must go through a {@code Java.type} value of
    * this context's JS language, as {@link #instantiate} does, to reach the
    * same node.
    *
    * <p>The Engine is recorded only when the warm-up succeeded, so a failure is
    * retried on the next Context build; it is logged and never fails the init.
    */
   static void forceUncachedConstructorSelection(Context context) {
      Engine engine = context.getEngine();

      if(!UNCACHED_ENGINES.add(engine)) {
         return;
      }

      boolean done = false;

      try {
         Value type = context.getBindings("js").getMember("Java").getMember("type")
            .execute(StringBuilder.class.getName());
         // more distinct argument types than HostExecuteNode.LIMIT (3)
         Object[] shapes = {
            16, (short) 16, (byte) 16, 16L, "a", new StringBuffer("b"),
            new StringBuilder("c"), CharBuffer.wrap("d")
         };

         for(Object arg : shapes) {
            type.newInstance(arg);
         }

         done = true;
      }
      catch(RuntimeException ex) {
         LOG.warn("Failed to force uncached constructor overload selection, a script " +
                  "constructor call may reuse a wrong overload", ex);
      }
      finally {
         if(!done) {
            UNCACHED_ENGINES.remove(engine);
         }
      }
   }

   /**
    * Whether {@link #forceUncachedConstructorSelection} has completed for the
    * Engine.
    */
   static boolean isUncachedConstructorSelection(Engine engine) {
      return UNCACHED_ENGINES.contains(engine);
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
         return wrapConstructed(instantiate((Object[]) arguments));
      }
      catch(IllegalArgumentException ex) {
         Object[] coerced = coerceNumericStrings(arguments);

         if(coerced == null) {
            throw ex;
         }

         try {
            return wrapConstructed(instantiate(coerced));
         }
         catch(IllegalArgumentException ignore) {
            throw ex;
         }
      }
   }

   /**
    * Call {@link Value#newInstance} on the host type, turning its refusal of a
    * type GraalVM cannot instantiate (a HostAccess type deny, an abstract class,
    * a class with no public constructor) into a host exception the script can
    * catch.
    *
    * <p>{@code Value.newInstance} refuses with an {@link UnsupportedOperationException}.
    * GraalVM maps a UOE leaving a proxy method to "message not supported", but this
    * proxy always reports itself instantiable and executable, so under {@code -ea}
    * Truffle's interop asserts turn that into an internal {@code AssertionError}
    * the script's {@code try/catch} never sees, and without {@code -ea} the
    * {@code TypeError} names this proxy rather than the class. An exception thrown
    * by the constructor body arrives as a {@code PolyglotException}, not a UOE, so
    * it is not affected. (#77919)
    */
   private Value instantiate(Object... arguments) {
      try {
         return hostType.newInstance(arguments);
      }
      catch(UnsupportedOperationException ex) {
         throw new IllegalStateException(
            "instantiate on " + className + " failed due to: Message not supported.");
      }
   }

   /**
    * Route a freshly constructed host object through the same guest-value
    * conversion a value reaching script via a scope/binding/method-return
    * already gets ({@link ScriptValueConverter#toGuest}), so a script's own
    * {@code new EGraph()}/{@code new LineElement(...)} is bean-property-wrapped
    * exactly like one handed to the script by the platform. GraalVM's native
    * constructor interop ({@link Value#newInstance}, above) never passes
    * through {@code toGuest} on its own -- this was the one construction path
    * in the {@code graal} package that didn't. (#76915)
    */
   private static Object wrapConstructed(Value result) {
      if(result != null && result.isHostObject()) {
         return ScriptValueConverter.toGuest(result.asHostObject());
      }

      return result;
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
    * constructing from a nonsense value. The {@code NaN} and {@code Infinity}
    * words are left alone for the same reason, even though {@code ToNumber}
    * does accept signed {@code Infinity}.
    *
    * @return the parsed number, or <tt>null</tt> if the string is not numeric.
    */
   // package-private: ScriptHostAccess reuses this grammar for host-call arguments. (#81889)
   static Number toNumber(String str) {
      String s = str.trim();

      // the literal grammar is matched before parsing because the Java parsers
      // accept forms JS Number() rejects, and each would otherwise silently
      // coerce a non-number: a sign after the 0x prefix ("0x-5" is -5 to
      // Long.parseLong, which permits a sign for any radix), and the d/f width
      // suffixes ("12F"). The NaN and Infinity words are excluded as well --
      // Number() does accept signed Infinity as a literal, but a data-derived
      // "Infinity" is a nonsense constructor argument, so it follows the
      // leave-it-alone rule rather than building from a non-finite value. An
      // empty string matches neither pattern, so it is also left alone.
      try {
         // JS treats a leading zero as decimal ("010" is 10), so only an
         // explicit 0x/0X prefix is a radix change -- matching Rhino, which
         // likewise recognized only the unsigned hex form.
         if(HEX_LITERAL.matcher(s).matches()) {
            return narrow(Long.parseLong(s.substring(2), 16));
         }

         if(DECIMAL_LITERAL.matcher(s).matches()) {
            return narrow(Double.parseDouble(s));
         }
      }
      catch(NumberFormatException ignore) {
         // a hex literal too wide for long
      }

      return null;
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
