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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.util.TableLoadException;
import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.pool.WsExecContext;
import inetsoft.util.swap.DataUnavailable;
import inetsoft.util.swap.SwapReadInterruptedException;
import org.graalvm.polyglot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * GraalJS-based script engine. Replaces JavaScriptEngine (Rhino).
 * One shared Engine (process-wide code cache); one Context per instance,
 * guarded by a lock.
 */
public class GraalJavaScriptEngine implements AutoCloseable {
   private static final Engine SHARED_ENGINE = Engine.newBuilder()
      .allowExperimentalOptions(true)
      .option("engine.WarnInterpreterOnly", "false")
      .build();

   protected Context context;
   // lendable so a condition filter holding it can let a lens worker run while it
   // waits for that worker (bug #76938), see LendableReentrantLock
   protected final LendableReentrantLock lock = new LendableReentrantLock();
   protected final ScriptTimeoutGuard timeoutGuard = new ScriptTimeoutGuard();
   protected boolean sql;

   /**
    * The class-lookup allow-list, built once per init and shared by both the
    * GraalJS Java.type host-class lookup and the legacy compatibility shim, so
    * both honor exactly the same reachable-class policy.
    */
   protected java.util.function.Predicate<String> classFilter;

   // Reusable per-exec scope proxy, bound once as __scope__ and swapped per call
   // (see exec). Recreated on (re)init because it is bound to the current context.
   private BindingRootProxy scopeProxy;

   // The CALC function scope (case-insensitive member lookup). Installed as the
   // __scope__ proxy's case-insensitive last-resort so unqualified CALC/statistical
   // functions resolve regardless of case, matching Rhino (see installGlobalScope,
   // ensureScopeProxy). (#75685)
   private ScriptScope calcScope;

   // Bug #77181: the HOST_GLOBALS_VAR guest object of the current context, and the
   // names in it (so a repeated put() costs one Java set lookup, and exec can test a
   // PieceScript's reset names without a guest call, #77331).
   // Rebuilt on (re)init; guarded by lock.
   private Value hostGlobals;
   private final Set<String> hostGlobalNames = new java.util.HashSet<>();

   // Bug #77595: the per-scope var stores of the current context (see localsFor), the
   // frozen empty object a script reads as its store when its vars stay globals, and the
   // function that makes a store. Rebuilt on (re)init; guarded by lock.
   // Bug #77866: a scope with a ScopeLocals holds its own store (keyed by this engine),
   // ownedLocals only names those scopes, to release their stores of a closed context.
   private final Map<ScriptScope, LocalsEntry> localStores = new WeakHashMap<>();
   private final Set<ScriptScope> ownedLocals = java.util.Collections.newSetFromMap(
      new WeakHashMap<>());
   private Value noLocals;
   private Value newLocals;
   private Value setLocalsParent;

   // These config props are read on the per-script hot path (exec runs hundreds
   // of thousands of times for data-driven worksheet formula columns), and a raw
   // SreeEnv lookup per call is a measurable cost. SreeEnv.Value caches the value
   // and only re-reads after the TTL — within the window get() is just a
   // currentTimeMillis compare, so it stays cheap AND live (a property change
   // takes effect within the TTL, no restart needed). (#75423)
   private static final SreeEnv.Value TIMEOUT_PROP =
      new SreeEnv.Value("script.execution.timeout", 10000);
   private static final SreeEnv.Value MAX_ERRORS_PROP =
      new SreeEnv.Value("script.max.errors", 10000);

   // Names used by the compile() wrapper to (a) hold the script completion value
   // and (b) name the catch binding of the per-declaration hoist guard. Chosen to
   // be unlikely to collide with any user-declared identifier. (#75596)
   private static final String RESULT_VAR = "__inetsoft_script_result__";
   private static final String HOIST_ERR_VAR = "__inetsoft_hoist_err__";
   // Holds each top-level statement's value in the multi-statement completion
   // wrapper (#75688). Same collision-avoidance rationale as RESULT_VAR.
   private static final String VALUE_VAR = "__inetsoft_script_value__";
   // Bug #77181: the global holding the names this engine itself defines (JS
   // builtins, init-installed globals, put() names), which the plain-path reset
   // of a rewritten initializer-less let/const must never touch.
   private static final String HOST_GLOBALS_VAR = "__inetsoft_host_globals__";
   // Bug #77595: the expression that reads the var store of the scope a script runs in
   // (or NO_LOCALS_VAR's object, see localsFor), the frozen empty object itself, and the
   // function that declares a script's top-level var names in its store.
   private static final String LOCALS_VAR = "__scope__." + BindingRootProxy.LOCALS_MEMBER;
   private static final String OWN_LOCALS_VAR =
      "__scope__." + BindingRootProxy.OWN_LOCALS_MEMBER;
   private static final String NO_LOCALS_VAR = "__inetsoft_no_locals__";
   private static final String DECLARE_FN = "__inetsoft_declare__";
   // Names the #77181 reset never emits, whatever the runtime set says: the
   // engine's own wrapper globals (resetting __scope__ breaks every later script).
   private static final Set<String> NEVER_RESET = Set.of(
      "__scope__", HOST_GLOBALS_VAR, RESULT_VAR, VALUE_VAR, HOIST_ERR_VAR,
      BindingRootProxy.LOCALS_MEMBER, BindingRootProxy.OWN_LOCALS_MEMBER, NO_LOCALS_VAR,
      DECLARE_FN,
      "globalThis", "undefined", "NaN", "Infinity", "eval", "arguments");

   // Bug #75625: matches the `this` keyword as an identifier token. Used to decide
   // whether a script body needs the (slower) direct-eval wrapper that binds
   // top-level `this` to the scope (#75550). The match is deliberately
   // conservative — a `this` inside a string literal or comment also matches and
   // merely routes the body to the eval form, which is correct, just not the fast
   // path. A `this`-binding cannot be used without writing the `this` token, so
   // there are no false negatives.
   private static final java.util.regex.Pattern THIS_REF =
      java.util.regex.Pattern.compile("\\bthis\\b");

   // JS reserved words that must never be emitted as an unguarded identifier in
   // the generated hoist statements (typeof <keyword> is a SyntaxError, which
   // would break compilation of the whole script). A declared name can never be
   // one of these in valid source, but the lightweight scanner is defensive.
   private static final Set<String> RESERVED_WORDS = Set.of(
      "break", "case", "catch", "class", "const", "continue", "debugger",
      "default", "delete", "do", "else", "enum", "export", "extends", "false",
      "finally", "for", "function", "if", "import", "in", "instanceof", "new",
      "null", "return", "super", "switch", "this", "throw", "true", "try",
      "typeof", "var", "void", "while", "with", "yield", "let", "static",
      "await", "async", "implements", "interface", "package", "private",
      "protected", "public");

   // Keywords after which a `/` begins a regular-expression literal rather than a
   // division operator (used by the declaration scanner to skip regex bodies).
   private static final Set<String> REGEX_PRECEDING_KEYWORDS = Set.of(
      "return", "typeof", "instanceof", "in", "of", "new", "delete", "void",
      "do", "else", "case", "yield", "await", "throw");

   /**
    * Per-Source error counts. Keyed by compiled Source identity; WeakHashMap
    * allows entries to be GC'd when the Source is no longer referenced.
    * Must only be accessed while holding {@code lock}.
    */
   private final Map<Object, Integer> errorCounts = new WeakHashMap<>();

   private static final ScriptScope EMPTY_SCOPE = new ScriptScope() {
      public Object getMember(String n) { return null; }
      public boolean hasMember(String n) { return false; }
      public void putMember(String n, Object v) { }
      public Object[] getMemberKeys() { return new Object[0]; }
   };

   public void init(Map<String, Object> vars) throws Exception {
      lock.lock();
      // Testing #77123: a caller's cancel (the thread's interrupt flag) is kept, but it must not
      // stop the init: Graal would raise it at the init's first guest safepoint and clear it,
      // and the catches below would swallow it, leaving a half-built engine for its life.
      // Clear it while the init runs and set it again after
      boolean cancelled = Thread.interrupted();

      try {
         if(context != null) {
            context.close(true);
         }

         classFilter = ScriptHostAccess.classFilter();
         scopeProxy = null; // rebound against the new context on next exec
         hostGlobals = null; // rebuilt against the new context by installHostGlobals
         // the var stores belong to the old context (#77595)
         releaseLocals();
         noLocals = newLocals = setLocalsParent = null;

         context = Context.newBuilder("js")
            .engine(polyglotEngine())
            .allowHostAccess(hostAccessPolicy())
            .allowHostClassLookup(classFilter)
            .allowIO(false)
            .allowCreateThread(false)
            .allowNativeAccess(false)
            .allowCreateProcess(false)
            .allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess.NONE)
            .build();
         // test every Java.type name, not just a context's first lookup (bug #77396)
         ScriptHostAccess.installTypeLookupCheck(context, classFilter);

         // FIX B: reset per-Source error counts on (re)init
         resetErrorCounts();

         initScope(vars);
         installHostGlobals();
      }
      finally {
         if(cancelled) {
            Thread.currentThread().interrupt();
         }

         lock.unlock();
      }
   }

   /**
    * Bug #77181: snapshot every own property of the global (JS builtins such as
    * {@code Math}, the LegacyJavaShim roots, global functions, {@code CALC},
    * library functions, the init vars and any subclass globals) into the
    * {@link #HOST_GLOBALS_VAR} object, which {@link #put} and
    * {@link #markHostGlobal} extend at run time. The plain-path reset of a
    * rewritten initializer-less {@code let}/{@code const} (see
    * {@link #buildLexicalReset}) skips every name in it, so a script never wipes
    * a global the engine itself defined. Taken after {@link #initScope}, so
    * script-created globals (the stale values the reset exists to clear) are
    * never in it. Caller holds {@code lock}.
    * <p>
    * An interrupt that stops it would leave the engine without the set for its life, so the
    * install, our own bounded JS, is retried without the interrupt flag on any interrupt (a
    * cancel or a timeout of a guard of the thread); only a cancel's flag is set again after
    * (Testing #77123).
    */
   private void installHostGlobals() {
      boolean cancelled = false;

      try {
         for(int attempt = 1; ; attempt++) {
            try {
               installHostGlobals0();
               return;
            }
            catch(PolyglotException ex) {
               if(attempt < 3 && ScriptTimeoutGuard.isInterrupt(ex)) {
                  cancelled |= ScriptTimeoutGuard.isCancel(ex, null);
                  Thread.interrupted();
                  continue;
               }

               ScriptTimeoutGuard.keepCancel(ex, null);
               // without the set every reset is skipped (the emitted guard checks it)
               LOG.warn("Failed to install the host global names", ex);
               return;
            }
         }
      }
      finally {
         if(cancelled) {
            Thread.currentThread().interrupt();
         }
      }
   }

   private void installHostGlobals0() {
      hostGlobalNames.clear();
      hostGlobals = null;
      installLocals();
      context.eval(Source.newBuilder("js",
         "(function(g){var h=Object.create(null),k=Object.getOwnPropertyNames(g);" +
         "for(var i=0;i<k.length;i++){h[k[i]]=true;}h['" + HOST_GLOBALS_VAR + "']=true;" +
         "Object.defineProperty(g,'" + HOST_GLOBALS_VAR +
         "',{value:h,writable:false,enumerable:false,configurable:true});})(globalThis)",
         "<host-globals>").buildLiteral());
      Value names = context.getBindings("js").getMember(HOST_GLOBALS_VAR);
      hostGlobalNames.addAll(names.getMemberKeys());
      hostGlobals = names;
   }

   /**
    * Bug #77595: install the globals of the per-scope var stores (see
    * {@link #localsFor}): {@link #NO_LOCALS_VAR}, a frozen empty object, which a script
    * reads as its store when its vars stay globals, and {@link #DECLARE_FN}, which
    * declares a script's top-level var names in its store as Rhino declared them on the
    * scope the script ran in (a name the store already has keeps its value) and returns
    * the store. Run before the host globals snapshot, so the names are host globals.
    * Idempotent, as the snapshot may be retried. Caller holds {@code lock}.
    */
   private void installLocals() {
      if(noLocals != null) {
         return;
      }

      Value fns = context.eval(Source.newBuilder("js",
         "(function(g){var none=Object.freeze(Object.create(null));" +
         "var own=Object.prototype.hasOwnProperty;" +
         "Object.defineProperty(g,'" + NO_LOCALS_VAR +
         "',{value:none,writable:false,enumerable:false,configurable:false});" +
         "Object.defineProperty(g,'" + DECLARE_FN + "',{value:function(o,n){" +
         "if(o!==none){for(var i=0;i<n.length;i++){if(!own.call(o,n[i])){o[n[i]]=void 0;}}}" +
         "return o;}," +
         "writable:false,enumerable:false,configurable:false});" +
         "var sp=Object.setPrototypeOf;" +
         "return [none,function(p){return Object.create(p===undefined?null:p);}," +
         "function(o,p){sp(o,p===undefined?null:p);}];" +
         "})(globalThis)", "<locals>").buildLiteral());
      newLocals = fns.getArrayElement(1);
      setLocalsParent = fns.getArrayElement(2);
      noLocals = fns.getArrayElement(0);
   }

   /**
    * Bug #77181: record {@code name} as a global the engine defines, so the
    * plain-path reset of a rewritten initializer-less {@code let}/{@code const}
    * never clears it. Called by {@link #put} and by the pooled worksheet context
    * when it sets an env variable directly. The caller holds {@code lock}
    * ({@link #put}) or is the owning thread of the pooled slot whose context
    * this is ({@code Slot.applyOwn}, which runs under the slot's claim rather
    * than {@code lock}); no other thread may call it.
    */
   protected final void markHostGlobal(String name) {
      if(hostGlobals != null && name != null && hostGlobalNames.add(name)) {
         hostGlobals.putMember(name, true);
      }
   }

   /**
    * The polyglot Engine this engine's Contexts share. Pooled worksheet contexts use their own
    * engine, since every Context of one Engine must use an identical HostAccess (bug #76960).
    */
   protected Engine polyglotEngine() {
      return SHARED_ENGINE;
   }

   /**
    * The HostAccess of this engine's Contexts.
    */
   protected HostAccess hostAccessPolicy() {
      return ScriptHostAccess.hostAccess();
   }

   /** Install engine globals. Overridden/extended by report + viewsheet layers. */
   protected void initScope(Map<String, Object> vars) {
      // engine globals (CALC, StyleConstant, XType, Chart, importExisting vars)
      // are installed here by subclasses / wiring tasks. Base impl publishes
      // the supplied vars.
      if(vars != null) {
         Value bindings = context.getBindings("js");

         for(Map.Entry<String, Object> e : vars.entrySet()) {
            bindings.putMember(e.getKey(), ScriptValueConverter.toGuest(e.getValue()));
         }
      }

      // legacy Rhino-interop shim (package roots, importClass/importPackage).
      // Installed before library functions so their bodies can navigate package
      // roots; gated live by the javascript.legacy.compatibility property.
      LegacyJavaShim.install(context, context.getBindings("js"), classFilter);
      LegacyJavaShim.installStringCompat(context);

      installGlobalFunctions();
      installGlobalScope();

      // Bind the __scope__ proxy before installing library functions: their
      // bodies are wrapped in with(__scope__){...} so unqualified names resolve
      // through the proxy at call time (see installLibraryFunctions).
      ensureScopeProxy();
      installLibraryFunctions();
   }

   /**
    * Install the built-in global script functions that were registered by the
    * Rhino {@code JavaScriptEngine.initFunction(Scriptable)} (e.g. {@code isNull},
    * {@code dateAdd}, {@code datePart}, {@code formatDate}, the FormulaFunctions,
    * etc.). Each is exposed as a callable JS global so unqualified
    * formula/expression scripts can invoke them by name.
    *
    * <p>Each group is guarded so a single reflection/class-load failure does not
    * abort engine init (mirroring {@link #installLibraryFunctions}).
    */
   private void installGlobalFunctions() {
      Value bindings = context.getBindings("js");
      Class<?> jse = inetsoft.util.script.JavaScriptEngine.class;

      // (a) static utility functions on JavaScriptEngine. JS-name -> (method, params)
      try {
         putFunction(bindings, "newInstance", jse, "newInstance", String.class);
         putFunction(bindings, "isNull", jse, "isNull", Object.class);
         putFunction(bindings, "isArray", jse, "isArray", Object.class);
         putFunction(bindings, "indexOf", jse, "indexOf", Object.class, Object.class);
         putFunction(bindings, "getDate", jse, "getDate", Object.class);
         putFunction(bindings, "isDate", jse, "isDate", Object.class);
         putFunction(bindings, "isNumber", jse, "isNumber", Object.class);
         putFunction(bindings, "formatDate", jse, "formatDate", Object.class, String.class);
         putFunction(bindings, "formatNumber", jse, "formatNumber",
                     double.class, String.class, Object.class);
         putFunction(bindings, "parseDate", jse, "parseDate", String.class, Object.class);
         putFunction(bindings, "dateAdd", jse, "dateAdd", String.class, int.class, Object.class);
         putFunction(bindings, "dateDiff", jse, "dateDiff",
                     String.class, Object.class, Object.class);
         putFunction(bindings, "datePart", jse, "datePart",
                     String.class, Object.class, boolean.class);
         putFunction(bindings, "datePartForceWeekOfMonth", jse, "datePartForceWeekOfMonth",
                     String.class, Object.class, boolean.class, int.class);
         putFunction(bindings, "trim", jse, "trim", String.class);
         putFunction(bindings, "ltrim", jse, "ltrim", String.class);
         putFunction(bindings, "rtrim", jse, "rtrim", String.class);
         putFunction(bindings, "split", jse, "split", String.class, Object.class, Object.class);
         putFunction(bindings, "log", jse, "log", Object.class);
         putFunction(bindings, "alert", jse, "alert", Object.class, Object.class);
         putFunction(bindings, "confirm", jse, "confirm", String.class);
         // JS name getImage -> method getImageJS
         putFunction(bindings, "getImage", jse, "getImageJS", Object.class);
         putFunction(bindings, "numberToString", jse, "numberToString", Object.class);
      }
      catch(Throwable ex) {
         // a cancel that landed during the init is kept (Testing #77123)
         ScriptTimeoutGuard.keepCancel(ex, null);
         LOG.warn("Failed to install global utility functions", ex);
      }

      // setupGoogleMapsPlot (static on GoogleMapsFunctions)
      try {
         putFunction(bindings, "setupGoogleMapsPlot",
                     inetsoft.util.script.GoogleMapsFunctions.class, "setupGoogleMapsPlot",
                     Object.class, String.class, Object.class, String.class, String.class,
                     int.class, int.class, int.class, int.class);
      }
      catch(Throwable ex) {
         // a cancel that landed during the init is kept (Testing #77123)
         ScriptTimeoutGuard.keepCancel(ex, null);
         LOG.warn("Failed to install setupGoogleMapsPlot", ex);
      }

      // (b) FormulaFunctions: every public static method declared on the class.
      // These intentionally overwrite any (a)-group registration with the same name —
      // FormulaFunctions implementations take priority over the JavaScriptEngine equivalents.
      try {
         addStaticFunctions(bindings, inetsoft.report.script.formula.FormulaFunctions.class);
      }
      catch(Throwable ex) {
         // a cancel that landed during the init is kept (Testing #77123)
         ScriptTimeoutGuard.keepCancel(ex, null);
         LOG.warn("Failed to install FormulaFunctions", ex);
      }
   }

   /**
    * Install the CALC math/stat/date functions and the constant-holder objects
    * (Chart, GLine, GTexture, GShape, SVGShape, StyleConstant) as JS globals.
    * Mirrors the Rhino {@code initScope()} setup.
    */
   private void installGlobalScope() {
      Value bindings = context.getBindings("js");

      // (c) CALC: install the object as CALC, plus each function unqualified
      // (Rhino set a Calc as the global prototype so functions resolve directly).
      try {
         // stateless, so one instance serves every Context (see SharedHostObjects)
         inetsoft.util.script.Calc calc = SharedHostObjects.calc();
         bindings.putMember("CALC", ScriptValueConverter.toGuest(calc));

         for(Object key : calc.getMemberKeys()) {
            String name = String.valueOf(key);

            // don't overwrite an explicit (a)/(b) registration on collision
            if(!bindings.hasMember(name)) {
               bindings.putMember(name, calc.getMember(name));
            }
         }

         // Rhino set the Calc scope as the global scope's prototype
         // (globalscope.setPrototype(new Calc())), and Calc's member lookup is
         // case-insensitive (funcmap is a TreeMap ordered by
         // String.CASE_INSENSITIVE_ORDER). So unqualified
         // CALC/statistical functions resolved regardless of case, e.g.
         // NthMostFrequent, PthPercentile, Sum. GraalJS global bindings are
         // case-sensitive, so the lowercase copies above only match exact-case
         // names. Expose the Calc scope to the __scope__ proxy so a name with no
         // exact global binding (JS builtins and the lowercase copies above
         // still win) resolves case-insensitively as a last resort. The proxy is
         // (re)created and wired with this scope in ensureScopeProxy(), which
         // always runs after this method during initScope(). (#75685)
         calcScope = calc;
      }
      catch(Throwable ex) {
         // a cancel that landed during the init is kept (Testing #77123)
         ScriptTimeoutGuard.keepCancel(ex, null);
         LOG.warn("Failed to install CALC functions", ex);
      }

      // (d) constant-holder objects (public static final fields reflected into scopes)
      Class<?>[] chartcls = {
         inetsoft.uql.viewsheet.graph.GraphTypes.class,
         inetsoft.report.composition.region.ChartConstants.class,
         inetsoft.uql.viewsheet.graph.GeographicOption.class
      };

      putConstantScope(bindings, "Chart", chartcls);
      // GLine/GTexture/GShape/SVGShape are Java classes with both public
      // constructors and public static final constants. Rhino exposed them as a
      // NativeJavaClass, so scripts both construct them (new GLine(3), used by
      // elem.setLineFrame(new StaticLineFrame(new GLine(3)))) and read their
      // constants (GLine.THIN_LINE). A ConstantScope only surfaces the constants
      // and is not instantiable ("instantiate on ScopeProxy ... Message not
      // supported"), so register them as JavaClassProxy, which allowPublicAccess
      // makes serve both the static constants and `new`.
      putClassProxy(bindings, "GLine", "inetsoft.graph.aesthetic.GLine");
      putClassProxy(bindings, "GTexture", "inetsoft.graph.aesthetic.GTexture");
      // GShape also exposes GShape.ImageShape (a public static nested class).
      putClassProxy(bindings, "GShape", "inetsoft.graph.aesthetic.GShape");
      putClassProxy(bindings, "SVGShape", "inetsoft.graph.aesthetic.SVGShape");

      installChartClasses(bindings);

      // StyleConstant: StyleConstants + ReportSheet + TableLens + VSFormat + TimeInfo
      // + the chart constants (GLine/GTexture/GShape deliberately excluded — they
      // shadow numeric constants in StyleConstants; see Rhino initScope()).
      java.util.List<Class<?>> all = new java.util.ArrayList<>();
      java.util.Collections.addAll(all, chartcls);
      all.add(inetsoft.report.StyleConstants.class);
      all.add(inetsoft.report.ReportSheet.class);
      all.add(inetsoft.report.TableLens.class);
      all.add(inetsoft.uql.viewsheet.VSFormat.class);
      all.add(inetsoft.uql.viewsheet.TimeInfo.class);
      putConstantScope(bindings, "StyleConstant", all.toArray(new Class<?>[0]));
   }

   /**
    * Register a single Java method as a callable JS global under {@code jsName}.
    */
   private static void putFunction(Value bindings, String jsName, Class<?> cls,
                                   String method, Class<?>... params)
   {
      bindings.putMember(jsName, SharedHostObjects.function(cls, method, params));
   }

   /**
    * Register every {@code public static} method declared directly on {@code cls}
    * as a callable JS global keyed by its method name. Mirrors the Rhino
    * {@code addFunctions(Class, Scriptable)} enumeration.
    */
   private static void addStaticFunctions(Value bindings, Class<?> cls) {
      for(Map.Entry<String, ScriptFunction> func : SharedHostObjects.staticFunctions(cls)) {
         bindings.putMember(func.getKey(), func.getValue());
      }
   }

   /**
    * Build a read-only constant scope from the given classes' public static final
    * fields and install it under {@code name}. Guarded per-group so a class-load
    * failure doesn't abort engine init.
    */
   private void putConstantScope(Value bindings, String name, Class<?>... classes) {
      try {
         // read-only, so one instance serves every Context (see SharedHostObjects)
         ConstantScope scope = SharedHostObjects.constants(classes);
         bindings.putMember(name, ScriptValueConverter.toGuest(scope));
      }
      catch(Throwable ex) {
         // a cancel that landed during the init is kept (Testing #77123)
         ScriptTimeoutGuard.keepCancel(ex, null);
         LOG.warn("Failed to install constant object " + name, ex);
      }
   }

   /**
    * Install user-defined library script functions as callable JS globals.
    *
    * <p>Each library function is stored as a full JS function declaration (e.g.
    * {@code function myFunc(a, b) { return a + b; }}). The declaration is
    * evaluated wrapped in {@code with(__scope__){ ... }}: in sloppy mode the
    * function declaration is still hoisted to a global (so any subsequently
    * compiled script/formula can call it by name), but its closure now captures
    * the {@code __scope__} object-environment. This restores the Rhino behavior
    * where a library function's unqualified names (e.g. {@code setActionVisible},
    * {@code drillEnabled}) resolve dynamically against the currently executing
    * assembly scope at call time — without the wrapper such names would be
    * unresolvable and throw a ReferenceError (Bug #75525). This replaces the
    * Rhino {@code cx.compileFunction(globalscope, ...)} machinery.
    *
    * <p>A malformed library function must not abort engine init, so each
    * function is compiled in its own try/catch (mirroring the old Rhino
    * per-function guard). The whole step is guarded against {@code LibManager}
    * being unavailable in minimal/test contexts.
    */
   private void installLibraryFunctions() {
      // the top level of a library runs here, and the globals it leaves are seen by every
      // script of this context, restricted ones included, so it runs restricted. A function
      // body still runs under its caller's flag (bug #77396)
      boolean restricted = inetsoft.util.script.FormulaContext.isRestricted();
      inetsoft.util.script.FormulaContext.setRestricted(true);

      try {
         installLibraryFunctions0();
      }
      finally {
         inetsoft.util.script.FormulaContext.setRestricted(restricted);
      }
   }

   private void installLibraryFunctions0() {
      Map<String, String> sources = librarySources();

      if(sources != null) {
         for(Map.Entry<String, String> entry : sources.entrySet()) {
            installLibraryFunction(entry.getKey(), entry.getValue());
         }

         return;
      }

      try {
         inetsoft.report.LibManager mgr =
            inetsoft.report.LibManagerProvider.getInstance().getManager();
         java.util.Enumeration<?> names = mgr.getScripts();

         while(names.hasMoreElements()) {
            String name = (String) names.nextElement();
            installLibraryFunction(name, mgr.getScript(name));
         }
      }
      catch(Throwable ex) {
         // LibManager/provider unavailable (e.g. minimal/test contexts) — skip
         ScriptTimeoutGuard.keepCancel(ex, null);
         LOG.debug("Library functions not installed; LibManager unavailable", ex);
      }
   }

   /**
    * Install one library function; a malformed one is logged and skipped.
    */
   private void installLibraryFunction(String name, String source) {
      if(source == null || source.isEmpty()) {
         return;
      }

      try {
         // Strip "use strict" directives — strict mode forbids with statements,
         // so the wrapper would cause a SyntaxError and the function would be
         // silently dropped. Bug #76980: rewrite top-level const/let to var
         // as compile() does, so a library constant is visible to other
         // scripts (Rhino put it on the scope) instead of being confined to
         // the with-block. Bug #77322: no line break after the opening brace, so
         // an error reports the function source's own line number.
         String wrapped = rewriteTopLevelLexicalDeclarations(
            stripStrictDirectives(rewriteJavaLengthCalls(source)));
         context.eval(Source.newBuilder(
            "js", "with(__scope__){" + wrapped + "\n}", "<lib:" + name + ">")
                         .buildLiteral());
      }
      catch(PolyglotException ex) {
         // don't let one bad library function break engine init
         // a cancel that landed during the init is kept (Testing #77123)
         ScriptTimeoutGuard.keepCancel(ex, null);
         LOG.warn("Failed to compile library function " + name, ex);
      }
   }

   /**
    * The library script sources to install, by name, in install order. {@code null} (the
    * default) reads the LibManager at install time. A pooled worksheet engine returns the
    * snapshot its env took, so every one of its contexts gets the same library (bug #76960).
    */
   protected Map<String, String> librarySources() {
      return null;
   }

   /**
    * The per-Source error counts ({@code script.max.errors}). Only accessed while holding
    * {@code lock}, unless an override returns a thread-safe map. A pooled worksheet engine
    * returns the map its env owns (spec §6.9).
    */
   protected Map<Object, Integer> errorCounts() {
      return errorCounts;
   }

   /**
    * Clear the error counts on (re)init. A pooled worksheet engine keeps its env's counts.
    */
   protected void resetErrorCounts() {
      errorCounts.clear();
   }

   /**
    * Rewrite Rhino-era {@code .length()} calls into a form GraalJS can evaluate
    * (#76780).
    *
    * <p>Rhino's {@code WrapFactory} wraps Java primitives by default, so a
    * {@code java.lang.String} handed to script — e.g. an element of the
    * {@code String[]} returned by {@code Calendar1.selectedObjects} — arrived as a
    * host object and {@code s.length()} resolved to the Java method. GraalJS
    * surfaces it as a guest string, where {@code length} is the spec-mandated own
    * numeric property, so the same call throws
    * {@code TypeError: ... length is not a function} and takes the whole script
    * with it.
    *
    * <p>It cannot be fixed on {@code String.prototype}: the own {@code length}
    * property shadows the prototype, and {@code s.length} must keep returning the
    * number. So rewrite the call site instead —
    * <pre>X.length()  -&gt;  X.{@value LegacyJavaShim#LENGTH_HELPER}('length')</pre>
    * a <em>call-expression</em> shape: {@code X} is never relocated or
    * re-evaluated, it stays exactly where it already was as the receiver of a
    * method call, so the helper's {@code this} is always {@code X} itself.
    * (Bug #77184: the original shape, {@code X.length.__jlen()}, evaluated
    * {@code X.length} first as a bare property read — which detaches the
    * property value from {@code X} — and then invoked the detached value; for a
    * user JS object whose {@code length} is a function that reads {@code this},
    * that function ran with the wrong receiver, either throwing or silently
    * returning data read off the JS global object instead of {@code X}.) The
    * helper, installed once on {@code Object.prototype} (see
    * {@link LegacyJavaShim#installStringCompat}), re-derives {@code X.length}
    * itself and invokes it {@code .call(this)} only when it is a function,
    * otherwise returns it as-is — so a guest string/array's numeric
    * {@code length}, a host {@code CharSequence} such as {@code StringBuilder}'s
    * bound Java method, and a plain JS object's own {@code length} method are
    * all handled correctly, with no knowledge of what {@code X} is.
    *
    * <p>Only the exact token sequence {@code .length} + {@code (} + {@code )} is
    * rewritten, and only outside string/template/regex literals and comments, so
    * a {@code ".length()"} inside a message string is left alone. An identifier
    * merely ending in {@code length} is not matched, since the rewrite requires
    * the preceding {@code .} and a non-identifier character after {@code length}.
    * Optional chaining ({@code ?.length()}) is not rewritten; it did not exist in
    * the Rhino-era scripts this restores.
    *
    * <p>Bug #77184: the regex-vs-division decision below also tracks
    * {@code afterHead} the same way {@link #scanTopLevel} and
    * {@link #skipInitializer} do (#76980) — a {@code /} immediately after an
    * {@code if}/{@code while}/{@code for}/{@code with} head's closing {@code )}
    * starts a regex literal, not a division, so its source text (which may
    * itself contain a {@code .length()}-shaped substring) is skipped as an
    * opaque unit instead of being scanned into and corrupted.
    *
    * @return the rewritten source, or {@code cmd} unchanged when the legacy gate
    * is off or there is nothing to rewrite.
    */
   static String rewriteJavaLengthCalls(String cmd) {
      if(cmd == null || cmd.indexOf(".length") < 0 || !LegacyJavaShim.isEnabled()) {
         return cmd;
      }

      StringBuilder out = null;   // allocated only once something is rewritten
      int n = cmd.length();
      int copied = 0;
      char prevSig = 0;
      String prevWord = null;   // previous identifier/keyword token, else null
      // one entry per open bracket: whether it is the `(` of an if/while/for/with
      // head, whose `)` is followed by a statement (so a `/` there is a regex) —
      // mirrors scanTopLevel/skipInitializer (#76980)
      Deque<Boolean> brackets = new ArrayDeque<>();
      boolean afterHead = false;   // the previous token closed a control-flow head
      int i = 0;

      while(i < n) {
         char c = cmd.charAt(i);

         if(c == '/' && i + 1 < n && cmd.charAt(i + 1) == '/') {
            i += 2;

            while(i < n && !isLineBreak(cmd.charAt(i))) {
               i++;
            }

            continue;
         }

         if(c == '/' && i + 1 < n && cmd.charAt(i + 1) == '*') {
            i += 2;

            while(i + 1 < n && !(cmd.charAt(i) == '*' && cmd.charAt(i + 1) == '/')) {
               i++;
            }

            i = Math.min(i + 2, n);
            continue;
         }

         if(c == '/' &&
            (afterHead || regexAllowed(cmd, i, prevSig == LITERAL_END ? ')' : prevSig)))
         {
            int end = scanRegexEnd(cmd, i);

            if(end > 0) {
               i = end;
               prevSig = LITERAL_END;
               prevWord = null;
               afterHead = false;
               continue;
            }
         }

         if(c == '"' || c == '\'') {
            i = skipStringLiteral(cmd, i + 1, c);
            prevSig = LITERAL_END;
            prevWord = null;
            afterHead = false;
            continue;
         }

         if(c == '`') {
            i = skipTemplateLiteral(cmd, i + 1);
            prevSig = LITERAL_END;
            prevWord = null;
            afterHead = false;
            continue;
         }

         int end = matchLengthCall(cmd, i);

         if(end > 0) {
            if(out == null) {
               out = new StringBuilder(n + 32);
            }

            out.append(cmd, copied, i).append('.')
               .append(LegacyJavaShim.LENGTH_HELPER).append("('length')");
            copied = end;
            i = end;
            prevSig = ')';
            prevWord = null;
            afterHead = false;
            continue;
         }

         if(isIdentStart(c)) {
            int start = i;
            i++;

            while(i < n && isIdentPart(cmd.charAt(i))) {
               i++;
            }

            prevWord = prevSig == '.' ? null : cmd.substring(start, i);
            prevSig = cmd.charAt(i - 1);
            afterHead = false;
            continue;
         }

         if(Character.isWhitespace(c)) {
            i++;
            continue;
         }

         boolean closedHead = false;

         if(c == '(' || c == '[' || c == '{') {
            brackets.push(c == '(' && prevWord != null && CONTROL_HEAD_KEYWORDS.contains(prevWord));
         }
         else if(c == ')' || c == ']' || c == '}') {
            if(!brackets.isEmpty()) {
               closedHead = brackets.pop() && c == ')';
            }
         }

         prevSig = c;
         prevWord = null;
         afterHead = closedHead;
         i++;
      }

      if(out == null) {
         return cmd;
      }

      out.append(cmd, copied, n);
      String rewritten = out.toString();

      LOG.debug("Rewrote Rhino-style .length() call(s) to .{}('length') for GraalJS; " +
                   "disable with {}=false",
                LegacyJavaShim.LENGTH_HELPER, LegacyJavaShim.GATE_PROPERTY);

      return rewritten;
   }

   /**
    * If {@code cmd} has {@code .length} immediately followed by an empty argument
    * list at {@code i} (the index of the {@code .}), return the index just past
    * the closing paren; otherwise {@code -1}. Whitespace is allowed between the
    * name and the parens and inside them, as anywhere else in a call.
    */
   private static int matchLengthCall(String cmd, int i) {
      final String NAME = ".length";
      int n = cmd.length();

      if(cmd.charAt(i) != '.' || !cmd.startsWith(NAME, i)) {
         return -1;
      }

      int j = i + NAME.length();

      // "lengthy" / "length2" are different identifiers, not a length() call
      if(j < n && isIdentPart(cmd.charAt(j))) {
         return -1;
      }

      j = skipWhitespace(cmd, j);

      if(j >= n || cmd.charAt(j) != '(') {
         return -1;
      }

      j = skipWhitespace(cmd, j + 1);

      // java's String.length() takes no arguments; anything else is not it
      return j < n && cmd.charAt(j) == ')' ? j + 1 : -1;
   }

   private static int skipWhitespace(String s, int i) {
      while(i < s.length() && Character.isWhitespace(s.charAt(i))) {
         i++;
      }

      return i;
   }

   public Object compile(String cmd) throws Exception {
      return compile(cmd, false);
   }

   public Object compile(String cmd, boolean fieldOnly) throws Exception {
      // Rhino parity: Context.evaluateString(scope, ...) bound the top-level
      // `this` to the scope object, so dashboard scripts routinely reference
      // assembly properties as `this.position`, `this.scaledPosition`, etc. A
      // bare with(__scope__){ ... } wrapper only fixes *unqualified* name
      // resolution — `this` at the top level of context.eval is globalThis, so
      // `this.<prop>` would read undefined and throw (Bug #75550).
      //
      // Run the body inside a function invoked with __scope__ as its receiver so
      // `this` === the scope. A plain function body would discard the script's
      // completion value, which value/expression bindings depend on (e.g. a Text
      // value binding "=field['Total']"; see ViewsheetSandbox.executeDynamicValue).
      // A *direct* eval preserves the statement-list completion value while
      // inheriting both the `this` receiver and the enclosing with(__scope__)
      // scope chain, so unqualified names still resolve against the live scope.
      //
      // Strip any leading "use strict" prologue: under the old top-level
      // with(__scope__){ ... } wrapper such a directive was an inert string
      // expression (a directive prologue is only recognized at the very start of
      // a script/function body, not inside a block), so scripts ran sloppy. As
      // the first statement of the eval'd body it *would* be recognized and flip
      // the body to strict eval, changing assignment/scope semantics — so remove
      // it to preserve the prior behavior.
      String body = stripStrictDirectives(rewriteJavaLengthCalls(cmd));

      // Bug #76980: Rhino (language version 0) scoped a top-level `const` like
      // `var` — a property of the executing scope, visible to the rest of the
      // script and to later scripts (e.g. an onInit `const` read by an assembly
      // script). GraalJS gives it ES6 block scoping, which the per-piece evals of
      // the #75688 split below confine to one piece (a ReferenceError, or a
      // silently wrong value inside try/catch), and which neither the plain-with
      // path nor the #75596 hoist carries across scripts. Rewrite top-level
      // `const`/`let` to `var` before the split and the hoist scan so every path
      // below sees a `var`.
      String lexicalBody = body;
      body = rewriteTopLevelLexicalDeclarations(body);

      // Bug #75688: Rhino preserved the "last non-empty" statement-list
      // completion value — an `if(false)` with no `else`, or a loop that never
      // entered its body, produced an *empty* completion, so a value-producing
      // statement followed by such a control-flow statement kept the earlier
      // value. GraalJS follows current ECMAScript, where those statements
      // complete with `undefined`, which *overwrites* the earlier value
      // (§14.6.7 IfStatement + the StatementList UpdateEmpty rule). Script-based
      // value and format bindings depend on the old semantics — e.g. a
      // cell-background formula
      //     if(price > 500) { [237,211,237] }  if(row > 1) { ... }
      // whose intended color is the first array whenever the trailing `if` does
      // not itself yield a value (see ViewsheetSandbox.executeDynamicValue).
      //
      // The completion value only diverges from Rhino when a value-producing
      // statement is followed by another top-level statement whose completion can
      // be empty — a control-flow statement whose body is not entered
      // (`if(false)`, an unrun loop, an empty block). Only then does ECMAScript
      // discard the earlier value that Rhino would have kept. A declaration also
      // completes empty but keeps the earlier value (UpdateEmpty), so it needs no
      // boundary (#77076).
      // splitTopLevelStatements breaks the body precisely before such statements
      // (a plain expression statement is never split off, since its value is the
      // completion in both engines). So when it yields more than one piece, at
      // least one such statement follows an earlier one: evaluate each piece in
      // order and keep the last non-`undefined` result, restoring the Rhino
      // behavior. A single-piece body — no top-level statement that can complete
      // empty follows another — falls through to the fast paths below unchanged,
      // so the #75625 parse-once optimization is preserved for the hot path
      // (value/expression bindings, per-row/per-cell formulas). Each piece is
      // parse-validated (piecesAllParse); a body that does not split cleanly
      // falls back to the single-eval path, so this can never turn valid source
      // into an invalid statement piece.
      List<String> statements = splitTopLevelStatements(body);

      if(statements.size() > 1 && piecesAllParse(statements)) {
         // Bug #77249: without `this`, run each piece as its own parsed-once
         // with(__scope__) Source instead of a per-exec direct eval (which GraalJS
         // re-parses on every execution). A `this` body keeps the eval wrapper.
         // A function declared in a nested block keeps the eval wrapper too: as a
         // piece it would keep its function across runs (hasBlockFunctionDeclaration).
         Object pieces = THIS_REF.matcher(body).find() || hasBlockFunctionDeclaration(body) ?
            null : buildPieceScript(body, lexicalBody, statements);

         return pieces != null ? pieces : buildCompletionPreservingSource(body, statements);
      }

      // Bug #75625: the direct-eval wrapper below re-parses the script body on
      // *every* execution — GraalJS does not cache a direct eval's argument — so
      // per-row/per-cell formula evaluation (calc/freehand tables, value and
      // expression bindings) is ~7x slower and can leave a viewsheet "loading"
      // for 10-20s. The eval form exists only to bind top-level `this` to the
      // scope (#75550). When the body does not reference `this`, a plain
      // top-level `with(__scope__){ ... }` script is equivalent and is parsed
      // once and reused: it preserves the statement-list completion value (value/
      // expression bindings) and top-level `var`/`function` declarations persist
      // to the context global across executions naturally (so #75596 holds
      // without the declaration hoist). Bug #77595: a `var` of a script run on a
      // scope other than the sheet's own (an assembly, a calc table) lives in the
      // var store of that scope instead (localsOpen, localsFor), as in Rhino, so
      // it never replaces an onInit/onLoad global. Block-vs-object completion semantics
      // (e.g. a bare `{a:1}`) are identical to the eval form because the body is
      // still evaluated in statement position, not wrapped in `return (...)`.
      //
      // Bug #77181: on this path a rewritten top-level `let r;` is a global
      // `var r` of the Context, and a `var` with no initializer never resets it,
      // so a formula run per row (FormulaTableLens, calc tables, calc fields) or
      // a later script declaring the same name reads the previous run's value,
      // where a native `let r;` would start `undefined`. Reset those names at the
      // start of each run, outside the `with` (so a same-named scope member is
      // never written) and on the first line (so error line numbers keep their
      // offset). The `this` paths (eval wrappers) run the body in a wrapper
      // function whose `var`s are fresh per run, so they need no reset; the
      // this-free split path (buildPieceScript, #77249) resets on its first piece.
      //
      // Bug #77322: the body starts on the wrapper's first line (no line break
      // after the brace), so a runtime error on body line N reports line N. The
      // closing brace stays on its own line so a trailing // comment cannot
      // swallow it.
      //
      // Bug #77321: the #77181 reset skips a name the engine itself defines (a
      // lower-case CALC function such as `value`/`count`, a JS builtin, a put()
      // name), so for such a name the rewritten var *is* that global: it keeps
      // the previous run's value and an assignment replaces the engine global.
      // A script with a reset name is therefore a PlainScript, which exec runs as
      // a native block `let` inside the with (buildPlainScript) on a Context
      // where one of its names is a host global.
      if(!THIS_REF.matcher(body).find()) {
         Set<String> resetNames = collectInitializerlessLexicalNames(lexicalBody);
         // Bug #77595: the vars are declared in the store of the exec scope, so the
         // reset (inside its with) resets the store, or the global if there is none
         Source plain = Source.newBuilder("js", localsOpen(localNames(body)) +
            buildLexicalReset(resetNames) + "with(__scope__){" + body + "\n}}", "<cmd>")
            .buildLiteral();

         return resetNames.isEmpty() ? plain :
            buildPlainScript(plain, body, lexicalBody, resetNames);
      }

      // Bug #75596: top-level `var`/`function` declarations must persist across
      // executions on the same engine so a later script (e.g. an assembly script
      // referencing a variable/function declared in the viewsheet onInit/onLoad
      // script) can see them. Under the direct-eval-in-a-function wrapper, such
      // declarations hoist into the transient wrapper-function frame and are lost
      // when it returns. After the eval, copy each top-level declared name out to
      // the context global so it survives — restoring the pre-#75550 (and Rhino)
      // behavior while keeping the #75550 `this`-binding and completion-value
      // semantics. Names that were not actually declared at the eval's top level
      // (e.g. inside a nested function, or block-scoped let/const confined to the
      // eval) are guarded by `typeof` and simply skipped. Bug #77595: for a script
      // run on a scope other than the sheet's own, the names are copied to the var
      // store of that scope instead of the global (buildDeclarationHoist).
      //
      // A top-level `return` in the script body is not a case this needs to
      // handle: GraalJS rejects it with a SyntaxError ("Invalid return
      // statement") at eval time, unlike V8/SpiderMonkey/Rhino which permit
      // `return` inside a direct eval nested in a function. So the hoist can
      // never be skipped by an early return here — the eval throws before the
      // hoist statement would matter either way, and that throw is pre-existing
      // behavior unrelated to this fix.
      return buildEvalWrapper(body);
   }

   /**
    * The single-eval wrapper of {@link #compile} for a single-piece body: the body
    * runs as a direct eval inside a function whose {@code this} is the scope
    * (#75550), keeping its completion value, and its top-level declarations are
    * copied to the global afterwards (#75596).
    */
   private static Source buildEvalWrapper(String body) {
      String hoist = buildDeclarationHoist(body);
      // Bug #77595: inside the store's with, so the body reads the vars an earlier run
      // left in the store of its scope, as the hoist copies them there
      return Source.newBuilder("js", "with(" + LOCALS_VAR + "){" +
         "(function(){with(__scope__){var " + RESULT_VAR + "=eval(" + toJsStringLiteral(body) +
            ");" + hoist + "return " + RESULT_VAR + ";}}).call(__scope__)}", "<cmd>")
         .buildLiteral();
   }

   /**
    * Bug #77321: compile a this-free single-piece body that has initializer-less
    * top-level {@code let}/{@code const} names (the #77181 reset names) to a
    * {@link PlainScript}. Besides the plain Source it keeps a second Source for a
    * Context on which one of those names is a global the engine defines (a
    * lower-case CALC function such as {@code value}, {@code count}, {@code sum} or
    * {@code year}, a global function, a JS builtin, a {@code put()} or pooled env
    * name). There the #77181 reset skips the name, so the rewritten {@code var} is
    * that global: a run reads the engine function or the previous run's value, and
    * an assignment replaces the engine global for every later script. The second
    * Source keeps each top-level declaration that has an initializer-less name a native
    * {@code let} inside the {@code with} block ({@code const} is written as
    * {@code let  }, a native {@code const} needs an initializer and forbids a later
    * assignment), the pre-#76980 shape: the name starts undefined on every run and
    * the engine global is never read or written.
    *
    * <p>Behavior changes, only for a script run as the second Source:
    * <ul>
    *   <li>a read of the name before its declaration throws a TDZ
    *       {@code ReferenceError}, where it read the engine global;</li>
    *   <li>a function declared in the body closes over the block {@code let}, not
    *       the global, and a later script does not see the name (#76980 cross-script
    *       visibility), as before #76980;</li>
    *   <li>every declaration with an initializer-less name is kept a native
    *       {@code let}, not only the colliding one, so a non-colliding name of the
    *       same body ({@code total} in {@code let value; let total;}, the {@code x}
    *       of {@code let x = 1, value;}) is block scoped as well; a declaration
    *       with initializers only stays a {@code var}.</li>
    * </ul>
    *
    * <p>Scope limits: a colliding name declared with an initializer
    * ({@code let count = a * 2}) still replaces the engine global, as the #76980
    * rewrite intends for a shared onInit declaration; the {@code this} and
    * multi-piece paths are unchanged (#77331 runs a colliding {@link PieceScript} as
    * the eval wrapper, whose #75596 hoist still copies the value to the global).
    *
    * <p>The second Source is parse-checked here, as it runs: a body that also
    * declares the name with {@code var}, a second {@code let} or a function
    * ({@code let value; var value;}) is an "already declared" early error in a
    * block. Then the eval wrapper ({@link #buildEvalWrapper}) is used instead, whose
    * vars are fresh locals of the wrapper function on every run, never the leaking
    * plain Source. The body stays on the first line (Bug #77322) and nothing is
    * added before the {@code with}, so error line numbers do not move.
    */
   private Object buildPlainScript(Source plain, String body, String lexicalBody,
                                   Set<String> resetNames)
   {
      // Bug #77595: only the declarations that stay a var are declared in the var store;
      // a kept let must not shadow the engine global for the other scripts of the scope
      String kept = keepInitializerlessLexicalDeclarations(lexicalBody);
      Source colliding = Source.newBuilder("js", localsOpen(localNames(kept)) +
         "with(__scope__){" + kept + "\n}}", "<cmd>").buildLiteral();

      if(!sourcesAllParse(new Source[] { colliding })) {
         colliding = buildEvalWrapper(body);
      }

      return new PlainScript(plain, colliding, body, resetNames);
   }

   /**
    * Bug #77321: {@link #rewriteTopLevelLexicalDeclarations} for the second Source
    * of a {@link PlainScript}: a top-level declaration that has an initializer-less
    * name (the names of {@link #collectInitializerlessLexicalNames}) stays a native
    * {@code let} ({@code const} -> {@code let  }), every other one is rewritten to
    * {@code var}. Offsets are kept, as in the rewrite.
    */
   private static String keepInitializerlessLexicalDeclarations(String body) {
      List<Integer> decls = new ArrayList<>();
      scanTopLevel(body, null, decls);
      StringBuilder sb = new StringBuilder(body);

      for(int pos : decls) {
         boolean isConst = body.startsWith("const", pos);
         int len = isConst ? 5 : 3;
         Set<String> names = new LinkedHashSet<>();
         collectDeclaratorNames(body, pos + len, names);

         if(!names.isEmpty()) {
            if(isConst) {
               sb.replace(pos, pos + len, "let  ");
            }
         }
         else {
            sb.replace(pos, pos + len, len == 5 ? "var  " : "var");
         }
      }

      return sb.toString();
   }

   /**
    * Bug #77321: the compiled form of a this-free single-piece body with #77181
    * reset names, see {@link #buildPlainScript}. {@link #exec} runs {@code plain} (the
    * #77181 Source) on a Context whose host globals include none of the names, and
    * {@code colliding} (a native {@code let} Source, or the eval wrapper) otherwise,
    * decided per exec like {@link PieceScript#collidesWith} (#77331), since the
    * compiled script is shared by engines whose host globals differ. Holds Sources
    * only, so it is free of any Context, and it is equal by content, like a Source.
    */
   static final class PlainScript {
      PlainScript(Source plain, Source colliding, String text, Set<String> resetNames) {
         this.plain = plain;
         this.colliding = colliding;
         this.text = text;
         this.resetNames = resetNames.toArray(new String[0]);
      }

      /**
       * The Source to run on a Context whose host globals are {@code hostNames}
       * ({@code null} if it has none, so the #77181 reset is skipped altogether).
       */
      Source source(Set<String> hostNames) {
         return collides(resetNames, hostNames) ? colliding : plain;
      }

      Source plain() {
         return plain;
      }

      Source colliding() {
         return colliding;
      }

      @Override
      public boolean equals(Object obj) {
         return obj instanceof PlainScript other && plain.equals(other.plain) &&
            colliding.equals(other.colliding);
      }

      @Override
      public int hashCode() {
         return plain.hashCode() * 31 + colliding.hashCode();
      }

      @Override
      public String toString() {
         return text;
      }

      private final Source plain;
      private final Source colliding;
      private final String text;
      private final String[] resetNames;
   }

   /**
    * Whether the #77181 reset skips one of {@code names} on a Context whose host
    * globals are {@code hostNames} ({@code null} if it has none, so the reset is
    * skipped altogether).
    */
   private static boolean collides(String[] names, Set<String> hostNames) {
      if(hostNames == null) {
         return names.length > 0;
      }

      for(String name : names) {
         if(hostNames.contains(name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Build a completion-value-preserving {@code Source} for a body that has more
    * than one top-level statement (#75688). Each statement is evaluated in order
    * inside a single {@code with(__scope__)} function whose {@code this} is the
    * scope (matching the {@link #compile} eval wrapper), keeping the last
    * non-{@code undefined} result — the Rhino "last non-empty completion"
    * behavior. Top-level {@code var}/{@code function} declarations still persist
    * across scripts via {@link #buildDeclarationHoist} (#75596), and — because
    * each piece is a direct, non-strict {@code eval} in the same function — such
    * declarations remain visible to later pieces just as they were in a single
    * evaluation. (Top-level {@code let}/{@code const} have already been rewritten
    * to {@code var} by {@link #rewriteTopLevelLexicalDeclarations} (#76980); a
    * top-level {@code class} is still confined to its own piece.)
    */
   private static Object buildCompletionPreservingSource(String body, List<String> statements) {
      StringBuilder sb = new StringBuilder();
      // Bug #77595: inside the store's with, as buildEvalWrapper
      sb.append("with(").append(LOCALS_VAR).append("){");
      sb.append("(function(){with(__scope__){var ").append(RESULT_VAR).append(",")
         .append(VALUE_VAR).append(";");

      int pos = 0;

      for(String stmt : statements) {
         // Bug #77322: pad the piece with the line breaks of the body before it,
         // counted from its offset in the body (a blank piece is dropped by the
         // split, so a running total over the pieces would miss its lines), so an
         // error in its <eval> reports the script's absolute line.
         int at = body.indexOf(stmt, pos);
         String padded = stmt;

         if(at >= 0) {
            padded = "\n".repeat(countLineBreaks(body, 0, at)) + stmt;
            pos = at + stmt.length();
         }

         sb.append(VALUE_VAR).append("=eval(").append(toJsStringLiteral(padded))
            .append(");if(").append(VALUE_VAR).append("!==undefined){")
            .append(RESULT_VAR).append("=").append(VALUE_VAR).append(";}");
      }

      sb.append(buildDeclarationHoist(body));
      sb.append("return ").append(RESULT_VAR).append(";}}).call(__scope__)}");

      return Source.newBuilder("js", sb.toString(), "<cmd>").buildLiteral();
   }

   /**
    * Bug #77249: compile a this-free body that {@link #splitTopLevelStatements} cut
    * into more than one piece (#75688) to a {@link PieceScript}: each piece becomes
    * its own {@code with(__scope__){ piece }} Source, the plain-path shape, which
    * Truffle parses once per Context and reuses, where the eval wrapper of
    * {@link #buildCompletionPreservingSource} re-parsed every piece on every exec.
    *
    * <p>Each piece is a top-level script, so a top-level {@code var} (and a
    * rewritten {@code let}/{@code const}, #76980) becomes a declared global of the
    * Context, visible to the later pieces and to later scripts, as on the plain
    * path; the #75596 hoist is not needed. On the eval wrapper such a var was a
    * fresh binding of the wrapper function on every run, so a formula like
    * {@code var c; if(v > 100) { c = [255,0,0] } c} (a per-cell color or a
    * viewsheet binding run on one shared Context) started with {@code c}
    * undefined each time. To keep that, the first piece starts, outside its
    * {@code with} (so a same-named scope member - including a formula table's
    * owned var, #5806 - is never written), with the #77181 reset of every name the
    * body declares with {@code var} outside a function body plus its
    * initializer-less top-level {@code let}/{@code const} names. As for #77181, a
    * global the engine itself defines is never reset, so a body that declares one
    * (a CALC function such as {@code max}, or a {@code put()} name) runs as the
    * eval wrapper instead, decided per exec against the running Context's host
    * globals (Bug #77331, see {@link PieceScript#collidesWith}).
    *
    * <p>Each piece is preceded by one line break per line break of the body before
    * it, so an error reports the same line as the plain path (the body line,
    * Bug #77322); there is no column padding, as only the line is reported.
    *
    * @return the piece script, or {@code null} if a piece cannot be located in
    *         {@code body} (defensive) or a built piece does not parse as it runs;
    *         the caller then keeps the eval wrapper.
    */
   private Object buildPieceScript(String body, String lexicalBody, List<String> statements) {
      Set<String> resetNames = collectInitializerlessLexicalNames(lexicalBody);
      resetNames.addAll(collectOwnedVarNames(List.of(lexicalBody)));
      resetNames.removeAll(NEVER_RESET);
      String reset = buildLexicalReset(resetNames);
      Set<String> localNames = localNames(body);
      String locals = localsOpen(localNames);
      Source[] pieces = new Source[statements.size()];
      int pos = 0;
      int scanned = 0;
      int lines = 0;

      for(int i = 0; i < pieces.length; i++) {
         String stmt = statements.get(i);
         int at = body.indexOf(stmt, pos);

         if(at < 0) {
            return null;
         }

         // count the line breaks before the piece as the reported line of the plain
         // path does: LF, CRLF and a lone CR; U+2028/U+2029 do not start a line there
         lines += countReportedLineBreaks(body, scanned, at);
         scanned = at;

         StringBuilder sb = new StringBuilder(
            stmt.length() + lines + 40 + (i == 0 ? locals.length() + reset.length() : 0));

         // Bug #77595: every piece runs in the store's with, and the first one declares
         // the body's vars there before the reset, which then resets the store
         if(i == 0) {
            sb.append(locals).append(reset);
         }
         else {
            sb.append(localsReopen(localNames));
         }

         sb.append("with(__scope__){");
         sb.append("\n".repeat(lines));
         sb.append(stmt).append("\n}}");
         pieces[i] = Source.newBuilder("js", sb.toString(), "<cmd>").buildLiteral();
         pos = at + stmt.length();
      }

      // A piece is parse-checked as a script (piecesAllParse) but runs as the block of
      // its with. A block has an early error a script does not: a name declared both
      // lexically and with var. A function declared in a block is lexical (sloppy
      // mode; Annex B relaxes only function-vs-function), so a piece like
      // `function f(){} var f;` (or a rewritten `let`/`const` plus a same-named
      // function) would throw "already declared" on every run, where the eval wrapper
      // (in whose eval code the function is var scoped) runs it. Parse each built
      // piece as it runs, once here at compile time (the caller caches the compiled
      // script, and the Context reuses the parse for the first eval); if one fails,
      // keep the eval wrapper.
      return sourcesAllParse(pieces) ?
         new PieceScript(pieces, body, resetNames, statements) : null;
   }

   /**
    * The number of line breaks (CR, LF, CRLF, U+2028, U+2029; CRLF counts once) in
    * {@code s} from {@code from} (inclusive) to {@code to} (exclusive).
    */
   private static int countLineBreaks(String s, int from, int to) {
      int lines = 0;

      for(int i = from; i < to; i++) {
         char c = s.charAt(i);

         if(c == '\n' || c == '\u2028' || c == '\u2029' ||
            c == '\r' && (i + 1 >= s.length() || s.charAt(i + 1) != '\n'))
         {
            lines++;
         }
      }

      return lines;
   }

   /**
    * The number of line breaks in {@code s} from {@code from} (inclusive) to {@code to}
    * (exclusive) as the reported line of the plain path counts them: LF, CRLF (once)
    * and a lone CR; U+2028/U+2029 do not start a reported line (#77249).
    */
   private static int countReportedLineBreaks(String s, int from, int to) {
      int lines = 0;

      for(int i = from; i < to; i++) {
         char c = s.charAt(i);

         if(c == '\n' || c == '\r' && (i + 1 >= s.length() || s.charAt(i + 1) != '\n')) {
            lines++;
         }
      }

      return lines;
   }

   /**
    * Bug #77249: the compiled form of a this-free multi-piece body (#75688), see
    * {@link #buildPieceScript}. {@link #exec} runs the pieces in order and keeps the
    * last result that is not {@code undefined} ({@code null} counts as a value), as
    * the eval wrapper's {@code V!==undefined}; an exception in a piece skips the
    * rest. Holds Sources only, so like a Source it is not bound to any Context and
    * can be shared by the static script caches, across pooled contexts and engines.
    * Equal by content, like a Source, so the {@code errorCounts} of a recompiled
    * formula carry over.
    *
    * <p>Bug #77331: it also keeps the names its first piece resets and the split body,
    * so {@link #exec} can run the body as the eval wrapper
    * ({@link #buildCompletionPreservingSource}, built on first use, also free of any
    * Context) on a Context where the reset skips one of them.
    */
   static final class PieceScript {
      PieceScript(Source[] pieces, String text, Set<String> resetNames,
                  List<String> statements)
      {
         this.pieces = pieces;
         this.text = text;
         this.resetNames = resetNames.toArray(new String[0]);
         this.statements = List.copyOf(statements);
      }

      /**
       * Whether the reset of the first piece skips one of its names on a Context
       * whose host globals are {@code hostNames} ({@code null} if it has none, so
       * the reset is skipped altogether), leaving that var with the value of the
       * global the engine defines or of the previous run (Bug #77331).
       */
      boolean collidesWith(Set<String> hostNames) {
         return collides(resetNames, hostNames);
      }

      /**
       * The eval wrapper of the body, whose vars are fresh locals of the wrapper
       * function on every run, as before #77249.
       */
      Source wrapper() {
         Source source = wrapper;

         if(source == null) {
            // a racing build yields an equal Source
            wrapper = source = (Source) buildCompletionPreservingSource(text, statements);
         }

         return source;
      }

      Value eval(Context context) {
         Value result = null;
         Value last = null;

         for(Source piece : pieces) {
            last = context.eval(piece);

            if(!ScriptValueConverter.isUndefined(last)) {
               result = last;
            }
         }

         return result != null ? result : last;
      }

      Source[] pieces() {
         return pieces.clone();
      }

      @Override
      public boolean equals(Object obj) {
         return obj instanceof PieceScript other && Arrays.equals(pieces, other.pieces);
      }

      @Override
      public int hashCode() {
         return Arrays.hashCode(pieces);
      }

      @Override
      public String toString() {
         return text;
      }

      private final Source[] pieces;
      private final String text;
      private final String[] resetNames;
      private final List<String> statements;
      private volatile Source wrapper;
   }

   // Keywords that begin a statement whose completion value can be *empty* — the
   // only statements before which the completion wrapper needs a boundary. A
   // plain expression statement is never split off (its value is the completion
   // in both engines), so pieces break only immediately before one of these.
   // Declarations (var/let/const/function/class) are not listed: they complete
   // empty and keep the earlier value in GraalJS as in Rhino, so they never need
   // a boundary, and a boundary before one would send the formula down the
   // per-piece direct-eval path that re-parses on every execution (#77076).
   private static final Set<String> STATEMENT_STARTERS = Set.of(
      "if", "for", "while", "do", "switch", "try", "return", "throw", "with",
      "debugger", "break", "continue", "import", "export");

   // Keywords after which the following token continues the *same* construct or
   // expression rather than beginning a new statement, so no boundary may be
   // placed after them: control keywords that introduce a sub-statement
   // (`else`/`do`), operators that take an operand (`return x`, `new C`,
   // `typeof f`, `a in b`, …), the `async` modifier that precedes a `function`
   // declaration (`async function`), and `case`. Blends with the prevSig/`)`
   // checks in splitTopLevelStatements to avoid mistaking a sub-statement/operand
   // for a sibling statement (e.g. `else if`, `new function(){}`,
   // `return function(){}`, `async function(){}`).
   private static final Set<String> SUPPRESS_BOUNDARY_AFTER = Set.of(
      "return", "throw", "typeof", "void", "delete", "new", "yield", "await",
      "async", "else", "do", "in", "of", "instanceof", "case");

   // Keywords whose parenthesized head is followed by a statement, so the head's
   // closing `)` puts the top-level lexer in regex (not division) context.
   private static final Set<String> CONTROL_HEAD_KEYWORDS = Set.of(
      "if", "while", "for", "with");

   // prevSig sentinel: a string/regex/template literal just ended (a value-ender
   // for both regex-vs-division disambiguation and statement-boundary decisions).
   private static final char LITERAL_END = '\u0001';

   /**
    * Split {@code body} into top-level statements for the completion-value
    * wrapper (#75688), so each can be evaluated in order. This is a lightweight
    * lexer, not a parser: it walks the source tracking string/regex/template
    * literals and comments, brace/paren/bracket depth, and the preceding
    * significant token, and places a boundary immediately before a depth-0
    * {@link #STATEMENT_STARTERS} keyword when the preceding token completed a
    * statement or expression.
    *
    * <p>Consecutive expression statements are deliberately <em>not</em> split
    * from one another — their combined completion value is naturally the last
    * one, so a boundary is only needed before a control-flow statement whose
    * completion can be empty, which is exactly what {@code STATEMENT_STARTERS}
    * enumerates. A declaration keeps the earlier value, so it is not a boundary
    * (#77076). A boundary is suppressed when the
    * preceding significant char is {@code (}…{@code )} (ambiguous with a
    * control-flow header such as {@code if(...)} whose next statement is the
    * body), when the previous token is in {@link #SUPPRESS_BOUNDARY_AFTER}
    * (`else if`, `new function`, …), when a {@code while} closes an open
    * {@code do} (its trailing {@code while}), and after a member {@code .} (a
    * keyword used as a property name).
    *
    * <p>The result is validated by {@link #piecesAllParse} before use, so even a
    * mis-placed boundary can never turn valid source into an invalid piece — it
    * falls back to the single-eval path instead. Under-splitting (leaving pieces
    * joined) likewise only forgoes the fix for that shape; it never corrupts.
    *
    * <p>Residual limitation: a {@code do{...}while(cond)} immediately followed by
    * another top-level control-flow statement is left unsplit, because the
    * do-while's own trailing {@code while(cond)} ends in {@code )}, which
    * {@code boundaryAllowedBefore} excludes. For that shape the completion-value
    * fix does not apply (the pre-#75688 ECMAScript overwrite behavior remains);
    * as above, this only forgoes the fix and never corrupts.
    */
   private static List<String> splitTopLevelStatements(String body) {
      List<String> out = new ArrayList<>();
      scanTopLevel(body, out, null);
      return out;
   }

   /**
    * Bug #76980: rewrite each top-level (depth-0) {@code let}/{@code const}
    * declaration keyword in {@code body} to {@code var}, so the declaration is
    * visible to later statement pieces of the #75688 completion wrapper (each
    * piece is its own direct eval, which confines lexical declarations but not
    * {@code var}) and persists to later scripts via the plain-with path or the
    * #75596 hoist — restoring the Rhino (language version 0) scoping, where a
    * top-level {@code const} was a property of the executing scope.
    *
    * <p>Uses the same lexer as {@link #splitTopLevelStatements}, so strings,
    * template literals, regex literals and comments are never touched, and
    * anything nested in parens/brackets/braces (a {@code for(let ...)} head, a
    * block, a function body) keeps its block scoping. The replacement keeps
    * character offsets so error positions do not move. Applied to every
    * {@link #compile} body and to library sources in
    * {@link #installLibraryFunctions}.
    *
    * <p>Deliberate side effects — the rewritten declaration is a plain
    * {@code var}, and Rhino's own const quirks are not emulated:
    * <ul>
    *   <li>reassigning a rewritten {@code const} takes the new value (Rhino
    *       silently ignored the assignment, unrewritten GraalJS throws
    *       {@code TypeError}); a redeclaration is accepted (Rhino threw
    *       {@code TypeError: redeclaration of const});</li>
    *   <li>under {@code with(__scope__)}, when the scope already has a member of
    *       the same name, the initializer writes to that scope member, as a
    *       {@code var} always has and as Rhino did (e.g. {@code const data = ...}
    *       in an element script now sets the element's {@code data} member when
    *       it has one);</li>
    *   <li>top-level {@code let}/{@code const} names become globals that persist
    *       across scripts on the engine (like the #75596 {@code var} hoist), and
    *       lose TDZ and immutability;</li>
    *   <li>{@code class} is left unchanged: it was a SyntaxError in Rhino, and
    *       {@code var K = class K {}} would change how a following line that
    *       begins with {@code (} or {@code [} parses;</li>
    *   <li>Bug #77181: on the plain {@code with(__scope__)} path of
    *       {@link #compile}, a rewritten declarator with no initializer
    *       ({@code let r;}, {@code let a, b;}, the {@code r} of
    *       {@code let x = 1, r;}) is reset to {@code undefined} at the start of
    *       every run, as a native {@code let} would start. So such a declaration
    *       also clears a same-named global that an earlier script set (an onInit
    *       {@code var total = 5}, then {@code let total;} in another script,
    *       leaves {@code total} undefined for scripts after it), as
    *       {@code let total = 1} already overwrote it. A global the engine
    *       itself defines (JS builtins, init-installed globals, {@link #put}
    *       names) is never reset; on a Context where a single-piece body declares
    *       such a name, the body instead runs with that declaration kept a native
    *       block {@code let} (Bug #77321, see {@link #buildPlainScript}). A
    *       declaration after which the name list cannot be read with certainty
    *       (see {@link #collectInitializerlessLexicalNames}) is not reset
    *       either.</li>
    * </ul>
    */
   private static String rewriteTopLevelLexicalDeclarations(String body) {
      List<Integer> decls = new ArrayList<>();
      scanTopLevel(body, null, decls);

      if(decls.isEmpty()) {
         return body;
      }

      StringBuilder sb = new StringBuilder(body);

      for(int pos : decls) {
         // "let" -> "var"; "const" -> "var  " (padded to keep offsets)
         int len = body.startsWith("const", pos) ? 5 : 3;
         sb.replace(pos, pos + len, len == 5 ? "var  " : "var");
      }

      return sb.toString();
   }

   /**
    * Bug #77181: the names declared without an initializer by the top-level
    * {@code let}/{@code const} declarations that
    * {@link #rewriteTopLevelLexicalDeclarations} rewrites in {@code body} (the
    * body before the rewrite; the rewrite keeps offsets). Covers
    * {@code let r;}, {@code let r, s;}, {@code let x = f(1, 2), r;}, each of
    * several declarations, and a declaration ended by a line break (ASI).
    *
    * <p>The name list is read token by token after the keyword. A name is taken
    * only where it is certainly a binding with no initializer: right after the
    * keyword or after a depth-0 {@code ,} of the same list, and followed by
    * {@code ,}, {@code ;}, the end of the body, or a line break (ASI: a binding
    * name cannot be continued by anything else). An initializer is skipped with
    * its strings, templates, regexes, comments and nested brackets, up to its
    * depth-0 {@code ,} or {@code ;}. Wherever the list cannot be read with
    * certainty (a line break inside an initializer not followed by {@code ,},
    * an unexpected token, a reserved word, an engine-internal name) the rest
    * of that declaration is skipped: a missed name only keeps the old
    * behavior, while a collected name that is not declared would clear a live
    * global.
    */
   private static Set<String> collectInitializerlessLexicalNames(String body) {
      List<Integer> decls = new ArrayList<>();
      scanTopLevel(body, null, decls);
      Set<String> names = new LinkedHashSet<>();

      for(int pos : decls) {
         collectDeclaratorNames(body, pos + (body.startsWith("const", pos) ? 5 : 3), names);
      }

      return names;
   }

   /**
    * Read the declarator list starting at {@code i} (just past a
    * {@code let}/{@code const} keyword) and add each initializer-less binding
    * name to {@code names}; see {@link #collectInitializerlessLexicalNames}.
    */
   private static void collectDeclaratorNames(String body, int i, Set<String> names) {
      int n = body.length();

      while(true) {
         int j = skipWhitespaceAndComments(body, i);

         if(j >= n) {
            return;
         }

         char c = body.charAt(j);
         int afterBinding;

         if(isIdentStart(c)) {
            int k = j + 1;

            while(k < n && isIdentPart(body.charAt(k))) {
               k++;
            }

            String name = body.substring(j, k);

            // a unicode escape or other unusual identifier char: not certain
            if(k < n && body.charAt(k) == '\\' || RESERVED_WORDS.contains(name) ||
               NEVER_RESET.contains(name))
            {
               return;
            }

            int m = skipWhitespaceAndComments(body, k);

            if(m >= n) {
               names.add(name);
               return;
            }

            char d = body.charAt(m);

            if(d == ',') {
               names.add(name);
               i = m + 1;
               continue;
            }

            if(d == ';') {
               names.add(name);
               return;
            }

            if(isAssign(body, m)) {
               afterBinding = m + 1;
            }
            // `let r\n(a > 5) && ...`: the declaration ends at the line break
            else if(containsLineBreak(body, k, m)) {
               names.add(name);
               return;
            }
            else {
               return;
            }
         }
         else if(c == '[' || c == '{') {
            // a destructuring pattern always has an initializer; skip both
            int k = skipBalanced(body, j);
            int m = k < 0 ? n : skipWhitespaceAndComments(body, k);

            if(m >= n || !isAssign(body, m)) {
               return;
            }

            afterBinding = m + 1;
         }
         else {
            return;
         }

         int comma = skipInitializer(body, afterBinding);

         if(comma < 0) {
            return;
         }

         i = comma + 1;
      }
   }

   /** Whether a plain {@code =} (not {@code ==}/{@code ===}/{@code =>}) is at {@code i}. */
   private static boolean isAssign(String s, int i) {
      return s.charAt(i) == '=' &&
         (i + 1 >= s.length() || s.charAt(i + 1) != '=' && s.charAt(i + 1) != '>');
   }

   /** Whether {@code s[from, to)} contains a line break. */
   private static boolean containsLineBreak(String s, int from, int to) {
      for(int i = from; i < to; i++) {
         if(isLineBreak(s.charAt(i))) {
            return true;
         }
      }

      return false;
   }

   /**
    * Skip the bracketed pattern opening at {@code open} ({@code [} or
    * {@code {}), with its strings, templates and comments; returns the index
    * just past the matching close, or -1 if it is not closed.
    */
   private static int skipBalanced(String s, int open) {
      int n = s.length();
      int depth = 0;
      int i = open;

      while(i < n) {
         char c = s.charAt(i);

         if(c == '/' && i + 1 < n && (s.charAt(i + 1) == '/' || s.charAt(i + 1) == '*')) {
            i = skipWhitespaceAndComments(s, i);
            continue;
         }

         if(c == '"' || c == '\'') {
            i = skipStringLiteral(s, i + 1, c);
            continue;
         }

         if(c == '`') {
            i = skipTemplateLiteral(s, i + 1);
            continue;
         }

         if(c == '(' || c == '[' || c == '{') {
            depth++;
         }
         else if(c == ')' || c == ']' || c == '}') {
            if(--depth == 0) {
               return i + 1;
            }
         }

         i++;
      }

      return -1;
   }

   /**
    * Skip a declarator's initializer expression starting at {@code i} (just
    * past its {@code =}). Returns the index of the depth-0 {@code ,} that
    * starts the next declarator, or -1 when the declaration ends here (a
    * depth-0 {@code ;}, the end of the body) or cannot be read with certainty
    * (a depth-0 line break not followed by {@code ,}, where the expression may
    * either continue or end by ASI; an unbalanced close).
    */
   private static int skipInitializer(String s, int i) {
      int n = s.length();
      int depth = 0;
      char prevSig = '=';
      String prevWord = null;   // previous identifier/keyword token, else null
      // as in scanTopLevel: per open bracket, whether it is the `(` of an
      // if/while/for/with head, whose `)` is followed by a statement, so a `/`
      // there starts a regex (e.g. inside a function expression initializer)
      java.util.Deque<Boolean> brackets = new java.util.ArrayDeque<>();
      boolean afterHead = false;

      while(i < n) {
         char c = s.charAt(i);

         if(Character.isWhitespace(c) ||
            c == '/' && i + 1 < n && (s.charAt(i + 1) == '/' || s.charAt(i + 1) == '*'))
         {
            int m = skipWhitespaceAndComments(s, i);

            if(depth == 0 && containsLineBreak(s, i, m)) {
               return m < n && s.charAt(m) == ',' ? m : -1;
            }

            i = m;
            continue;
         }

         if(c == '/' && (afterHead || regexAllowed(s, i, prevSig))) {
            int end = scanRegexEnd(s, i);

            if(end > 0) {
               i = end;
               prevSig = ')';
               prevWord = null;
               afterHead = false;
               continue;
            }
         }

         if(c == '"' || c == '\'') {
            i = skipStringLiteral(s, i + 1, c);
            prevSig = ')';
            prevWord = null;
            afterHead = false;
            continue;
         }

         if(c == '`') {
            i = skipTemplateLiteral(s, i + 1);
            prevSig = ')';
            prevWord = null;
            afterHead = false;
            continue;
         }

         if(isIdentStart(c)) {
            int start = i;

            while(i < n && isIdentPart(s.charAt(i))) {
               i++;
            }

            prevWord = prevSig == '.' ? null : s.substring(start, i);
            prevSig = s.charAt(i - 1);
            afterHead = false;
            continue;
         }

         boolean closedHead = false;

         if(c == '(' || c == '[' || c == '{') {
            depth++;
            brackets.push(c == '(' && prevWord != null && CONTROL_HEAD_KEYWORDS.contains(prevWord));
         }
         else if(c == ')' || c == ']' || c == '}') {
            if(depth == 0) {
               return -1;
            }

            depth--;
            closedHead = brackets.pop() && c == ')';
         }
         else if(depth == 0 && c == ',') {
            return i;
         }
         else if(depth == 0 && c == ';') {
            return -1;
         }

         prevSig = c;
         prevWord = null;
         afterHead = closedHead;
         i++;
      }

      return -1;
   }

   /**
    * Bug #77181: the first-line prefix of the plain {@code with(__scope__)}
    * Source that resets each of {@code names} (the initializer-less rewritten
    * declarations of the body, see {@link #collectInitializerlessLexicalNames})
    * to {@code undefined} before the body runs, skipping every name in the
    * {@link #HOST_GLOBALS_VAR} set (a global the engine defines). The prefix is
    * outside the {@code with}, so it assigns the body's hoisted global
    * {@code var}, never a same-named member of the scope. It has no line break,
    * so error line numbers do not move. Empty when there is nothing to reset.
    */
   /**
    * Bug #77595: the start of the {@code with} block of a script's var store, the store of
    * the scope {@link #exec} runs it in (see {@link #localsFor}), declaring in it
    * {@code names}, the names {@code body} declares with {@code var} outside a function
    * (a top-level {@code let}/{@code const} is a {@code var} by then, #76980). The block
    * is outside {@code with(__scope__)}, so a scope member of the same name still wins,
    * as on the exec scope in Rhino; a name the scope does not have resolves to the store,
    * so the {@code var} never writes a global of the same name, such as an onInit
    * variable. No line break, so error line numbers do not move. Closed by {@code "}"}.
    */
   private static String localsOpen(Set<String> names) {
      if(names.isEmpty()) {
         return localsReopen(names);
      }

      StringBuilder sb = new StringBuilder("with(").append(DECLARE_FN).append('(')
         .append(OWN_LOCALS_VAR).append(",[");
      int i = 0;

      for(String name : names) {
         sb.append(i++ > 0 ? "," : "").append(toJsStringLiteral(name));
      }

      return sb.append("])){").toString();
   }

   /**
    * Bug #77595: the names {@link #localsOpen} declares for {@code body}, the names it
    * declares with {@code var} outside a function.
    */
   private static Set<String> localNames(String body) {
      Set<String> names = collectOwnedVarNames(List.of(body));
      names.removeAll(NEVER_RESET);
      return names;
   }

   /**
    * Bug #77595: the start of the {@code with} block of the var store of a later piece of
    * a script whose first piece opened it with {@link #localsOpen} for {@code names}, or
    * of a script without vars, which only reads its store.
    */
   private static String localsReopen(Set<String> names) {
      return "with(" + (names.isEmpty() ? LOCALS_VAR : OWN_LOCALS_VAR) + "){";
   }

   private static String buildLexicalReset(Set<String> names) {
      if(names.isEmpty()) {
         return "";
      }

      StringBuilder sb = new StringBuilder();
      sb.append("if(typeof ").append(HOST_GLOBALS_VAR).append("==='object'){");

      for(String name : names) {
         sb.append("if(!").append(HOST_GLOBALS_VAR).append('.').append(name).append(')')
            .append(name).append("=void 0;");
      }

      return sb.append('}').toString();
   }

   /**
    * Whether the {@code let}/{@code const} keyword {@code word}, ending at
    * {@code end} at depth 0 (not after a member {@code .}), begins a lexical
    * declaration: it must be at statement position (not after an operator or
    * {@code return}/{@code typeof}/...) and be followed by a binding name,
    * {@code [} or {@code {}. {@code let} is also an identifier in sloppy code
    * (e.g. {@code x = let}, {@code let in o}, {@code let.x}). The same shape is
    * required for {@code const} as defense in depth: should the lexer ever read
    * a regex literal as code, a {@code const} inside it (e.g. {@code /const/})
    * is not followed or preceded by a declaration shape. After a {@code )}
    * (ambiguous with a control-flow header, where {@code if(c) let\ny = 1} is
    * an expression) a {@code let} binding must follow on the same line; a
    * reserved {@code const} cannot be an expression, so no such rule is needed
    * for it. A {@code const} after a line break, and a {@code let} after a
    * postfix {@code ++}/{@code --}, are at statement position by ASI
    * ({@code i++\nconst d = 1}).
    */
   private static boolean isTopLevelLexicalDeclaration(String body, String word, int end,
                                                       char prevSig, String prevWord,
                                                       boolean afterPostfix,
                                                       boolean lineBreak)
   {
      boolean isConst = word.equals("const");

      if(!isConst && !word.equals("let")) {
         return false;
      }

      // a line break ends the previous statement by ASI when the next token
      // cannot continue it: always for the reserved `const`, and after a postfix
      // `++`/`--` for `let` (`i++\nlet d = 1`); elsewhere `let` may still be an
      // operand (`x =\nlet[0]`), so it keeps the stricter check
      boolean statementStart = prevSig == 0 || prevSig == ')' || boundaryAllowedBefore(prevSig) ||
         afterPostfix || isConst && lineBreak;

      if(!statementStart || prevWord != null && SUPPRESS_BOUNDARY_AFTER.contains(prevWord)) {
         return false;
      }

      int j = skipWhitespaceAndComments(body, end);

      if(j >= body.length()) {
         return false;
      }

      if(!isConst && prevSig == ')' &&
         body.substring(end, j).chars().anyMatch(ch -> isLineBreak((char) ch)))
      {
         return false;
      }

      char c = body.charAt(j);

      if(c == '[' || c == '{') {
         return true;
      }

      if(!isIdentStart(c)) {
         return false;
      }

      int k = j + 1;

      while(k < body.length() && isIdentPart(body.charAt(k))) {
         k++;
      }

      String next = body.substring(j, k);
      return !next.equals("in") && !next.equals("instanceof");
   }

   /**
    * The top-level lexer shared by {@link #splitTopLevelStatements} (which
    * collects the statement pieces into {@code statements}) and
    * {@link #rewriteTopLevelLexicalDeclarations} (which collects the offsets of
    * depth-0 {@code let}/{@code const} declaration keywords into
    * {@code lexicalDecls}). Either list may be {@code null}.
    */
   private static void scanTopLevel(String body, List<String> statements,
                                    List<Integer> lexicalDecls)
   {
      List<String> out = statements != null ? statements : new ArrayList<>();
      int n = body.length();
      int depth = 0;
      int start = 0;
      int openDo = 0;          // depth-0 `do`s awaiting their trailing `while`
      char prevSig = 0;        // previous significant char (LITERAL_END for a literal)
      String prevWord = null;  // previous identifier/keyword token, else null
      // one entry per open bracket: whether it is the `(` of an if/while/for/with
      // head, whose `)` is followed by a statement (so a `/` there is a regex)
      java.util.Deque<Boolean> brackets = new java.util.ArrayDeque<>();
      boolean afterHead = false;   // the previous token closed a control-flow head
      boolean afterPostfix = false; // the previous token was a postfix `++`/`--`
      boolean lineBreak = false;   // a line break since the previous token
      int i = 0;

      while(i < n) {
         char c = body.charAt(i);

         // line comment
         if(c == '/' && i + 1 < n && body.charAt(i + 1) == '/') {
            i += 2;

            while(i < n && !isLineBreak(body.charAt(i))) {
               i++;
            }

            continue;
         }

         // block comment
         if(c == '/' && i + 1 < n && body.charAt(i + 1) == '*') {
            i += 2;

            while(i + 1 < n && !(body.charAt(i) == '*' && body.charAt(i + 1) == '/')) {
               lineBreak |= isLineBreak(body.charAt(i));
               i++;
            }

            i = Math.min(i + 2, n);
            continue;
         }

         // regex literal (only where a `/` cannot be division). The `)` closing a
         // control-flow head (`if(x) /re/.test(s)`) is followed by a statement,
         // so a `/` there starts a regex; any other `)` (a call or grouping)
         // ends an expression, so a `/` after it is division.
         if(c == '/' &&
            (afterHead || regexAllowed(body, i, prevSig == LITERAL_END ? ')' : prevSig)))
         {
            int end = scanRegexEnd(body, i);

            if(end > 0) {
               i = end;
               prevSig = LITERAL_END;
               prevWord = null;
               afterHead = afterPostfix = lineBreak = false;
               continue;
            }
         }

         // string literal
         if(c == '"' || c == '\'') {
            i = skipStringLiteral(body, i + 1, c);
            prevSig = LITERAL_END;
            prevWord = null;
            afterHead = afterPostfix = lineBreak = false;
            continue;
         }

         // template literal
         if(c == '`') {
            i = skipTemplateLiteral(body, i + 1);
            prevSig = LITERAL_END;
            prevWord = null;
            afterHead = afterPostfix = lineBreak = false;
            continue;
         }

         if(Character.isWhitespace(c)) {
            lineBreak |= isLineBreak(c);
            i++;
            continue;
         }

         // identifier / keyword
         if(isIdentStart(c)) {
            int s = i;
            i++;

            while(i < n && isIdentPart(body.charAt(i))) {
               i++;
            }

            String word = body.substring(s, i);
            boolean afterDot = prevSig == '.';

            if(depth == 0 && !afterDot) {
               if(lexicalDecls != null &&
                  isTopLevelLexicalDeclaration(body, word, i, prevSig, prevWord,
                                               afterPostfix, lineBreak))
               {
                  lexicalDecls.add(s);
               }

               boolean whileTail = word.equals("while") && openDo > 0;
               // A label (`name:` at statement position, e.g. `outer: for(...)`)
               // begins a statement whose completion can be empty, but the label
               // identifier is not itself a keyword; detect it so the boundary is
               // placed before the label, keeping `label: stmt` together. At
               // depth 0 an identifier directly followed by `:` is unambiguously a
               // label (a ternary `:` is always preceded by `?`, i.e. prevSig
               // there is not boundary-permitting).
               boolean label = nextSignificantChar(body, i) == ':';

               if(((!whileTail && STATEMENT_STARTERS.contains(word)) || label) &&
                  s > start && boundaryAllowedBefore(prevSig) &&
                  (prevWord == null || !SUPPRESS_BOUNDARY_AFTER.contains(prevWord)))
               {
                  addStatement(out, body.substring(start, s));
                  start = s;
               }

               if(word.equals("do")) {
                  openDo++;
               }
               else if(whileTail) {
                  openDo--;
               }
            }

            prevSig = body.charAt(i - 1);
            prevWord = afterDot ? null : word;   // a property name isn't a keyword
            afterHead = afterPostfix = lineBreak = false;
            continue;
         }

         boolean closedHead = false;

         if(c == '(' || c == '[' || c == '{') {
            depth++;
            brackets.push(c == '(' && prevWord != null && CONTROL_HEAD_KEYWORDS.contains(prevWord));
         }
         else if(c == ')' || c == ']' || c == '}') {
            if(depth > 0) {
               depth--;
            }

            if(!brackets.isEmpty()) {
               closedHead = brackets.pop() && c == ')';
            }
         }

         prevSig = c;
         prevWord = null;
         afterHead = closedHead;
         // `x++`/`a[0]--`/`f()++`: the second char of a postfix operator
         afterPostfix = (c == '+' || c == '-') && i >= 2 && body.charAt(i - 1) == c &&
            (isIdentPart(body.charAt(i - 2)) || body.charAt(i - 2) == ')' ||
             body.charAt(i - 2) == ']');
         lineBreak = false;
         i++;
      }

      if(start < n) {
         addStatement(out, body.substring(start));
      }
   }

   /** Add {@code stmt} to {@code out} unless it is blank (whitespace only). */
   private static void addStatement(List<String> out, String stmt) {
      if(!stmt.trim().isEmpty()) {
         out.add(stmt);
      }
   }

   /**
    * Whether the previous significant char {@code prevSig} completed a
    * statement/expression, so a boundary may precede a following statement
    * keyword. True for a literal end, {@code ]}, {@code }}, {@code ;} and an
    * identifier/number char. Notably excludes {@code )} — ambiguous with a
    * control-flow header — and any operator/punctuation that would make the
    * keyword an operand or continuation.
    */
   private static boolean boundaryAllowedBefore(char prevSig) {
      return prevSig == LITERAL_END || prevSig == ']' || prevSig == '}' ||
         prevSig == ';' || isIdentPart(prevSig);
   }

   /**
    * The next significant character at or after {@code from}, skipping whitespace
    * and {@code //}/{@code /* *}{@code /} comments; {@code 0} at end of input.
    * Used to recognize a label ({@code name:}) in the statement splitter.
    */
   private static char nextSignificantChar(String s, int from) {
      int i = from;
      int n = s.length();

      while(i < n) {
         char c = s.charAt(i);

         if(Character.isWhitespace(c)) {
            i++;
         }
         else if(c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
            i += 2;

            while(i < n && !isLineBreak(s.charAt(i))) {
               i++;
            }
         }
         else if(c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
            i += 2;

            while(i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
               i++;
            }

            i = Math.min(i + 2, n);
         }
         else {
            return c;
         }
      }

      return 0;
   }

   /**
    * Skip a single/double-quoted string starting at {@code i} (just past the
    * opening quote {@code q}); returns the index just past the closing quote.
    */
   private static int skipStringLiteral(String s, int i, char q) {
      int n = s.length();

      while(i < n) {
         char c = s.charAt(i);

         if(c == '\\') {
            i += 2;
            continue;
         }

         if(c == q) {
            return i + 1;
         }

         i++;
      }

      return i;
   }

   /**
    * Skip a template literal starting at {@code i} (just past the opening
    * backtick); returns the index just past the closing backtick. Handles
    * {@code ${ ... }} substitutions — including nested braces, strings and nested
    * templates within them — so their code (and any braces it contains) does not
    * disturb the caller's depth tracking.
    */
   private static int skipTemplateLiteral(String s, int i) {
      int n = s.length();

      while(i < n) {
         char c = s.charAt(i);

         if(c == '\\') {
            i += 2;
            continue;
         }

         if(c == '`') {
            return i + 1;
         }

         if(c == '$' && i + 1 < n && s.charAt(i + 1) == '{') {
            i += 2;
            int braces = 1;

            while(i < n && braces > 0) {
               char d = s.charAt(i);

               if(d == '\\') {
                  i += 2;
               }
               else if(d == '{') {
                  braces++;
                  i++;
               }
               else if(d == '}') {
                  braces--;
                  i++;
               }
               else if(d == '`') {
                  i = skipTemplateLiteral(s, i + 1);
               }
               else if(d == '"' || d == '\'') {
                  i = skipStringLiteral(s, i + 1, d);
               }
               else {
                  i++;
               }
            }

            continue;
         }

         i++;
      }

      return i;
   }

   /**
    * Parse-validate every piece of a proposed split (#75688) so a mis-placed
    * boundary can never turn valid source into an invalid statement piece: if any
    * piece fails to parse, the caller falls back to the single-eval path. Parsing
    * is syntax-only (no evaluation) and must hold the engine {@code lock} like
    * every other {@code context} access; {@code compile()} does not hold it, so it
    * is acquired here. If the context is not yet built, validation fails safe
    * (no split).
    */
   private boolean piecesAllParse(List<String> pieces) {
      return allParse(pieces, piece -> context.parse("js", piece));
   }

   /**
    * Parse-validate the built pieces of a {@link PieceScript} exactly as they run
    * (#77249), under the engine {@code lock} like {@link #piecesAllParse}. Fails safe
    * (the caller keeps the eval wrapper) if the context is not yet built.
    */
   private boolean sourcesAllParse(Source[] sources) {
      return allParse(Arrays.asList(sources), source -> context.parse(source));
   }

   // parse each item under the engine lock; false if the context is not built or one fails
   private <T> boolean allParse(List<T> items, java.util.function.Consumer<T> parse) {
      lock.lock();

      try {
         if(context == null) {
            return false;
         }

         for(T item : items) {
            try {
               parse.accept(item);
            }
            catch(Exception ex) {
               return false;
            }
         }

         return true;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Encode a script body as a JavaScript double-quoted string literal for
    * embedding in the {@code eval(...)} wrapper built by {@link #compile}.
    * Escapes the characters that are illegal or ambiguous inside a JS string
    * (quote, backslash, the C0 control set including the line terminators, and
    * the U+2028/U+2029 line/paragraph separators).
    */
   private static String toJsStringLiteral(String s) {
      StringBuilder sb = new StringBuilder(s.length() + 16);
      sb.append('"');

      for(int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);

         switch(c) {
         case '"':      sb.append("\\\""); break;
         case '\\':     sb.append("\\\\"); break;
         case '\n':     sb.append("\\n"); break;
         case '\r':     sb.append("\\r"); break;
         case '\t':     sb.append("\\t"); break;
         case '\b':     sb.append("\\b"); break;
         case '\f':     sb.append("\\f"); break;
         default:
            // C0 controls plus the U+2028/U+2029 line/paragraph separators
            // (illegal unescaped inside a JS string literal) -> \\uXXXX.
            if(c < 0x20 || c == 0x2028 || c == 0x2029) {
               sb.append(String.format("\\u%04x", (int) c));
            }
            else {
               sb.append(c);
            }
         }
      }

      sb.append('"');
      return sb.toString();
   }

   public void checkFunction(String name, String cmd) throws Exception {
      lock.lock();

      try {
         // FIX A: guard against null context before initialization
         if(context == null) {
            throw new IllegalStateException("Engine not initialized");
         }

         context.parse("js", cmd); // parse-only; throws on syntax error
      }
      catch(PolyglotException ex) {
         throw new Exception("Syntax error in " + name + ": " + ex.getMessage(), ex);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * @return this engine's execution lock (see
    * {@link inetsoft.util.script.ScriptEnv#getExecutionLock()}). The lock is
    * reentrant, so a caller pre-acquiring it before calling back into
    * {@link #exec} on the same thread will not self-deadlock.
    */
   public LendableReentrantLock getExecutionLock() {
      return lock;
   }

   public Object exec(Object script, Object scope, Object rscope) throws Exception {
      lock.lock();
      // marks which pooled worksheet context, if any, is executing (bug #76960)
      Object execMark = enterExecContext(script);

      try {
         // FIX A: guard against null context before initialization
         if(context == null) {
            throw new IllegalStateException("Engine not initialized");
         }

         ScriptScope root = (scope instanceof ScriptScope) ? (ScriptScope) scope : null;
         ScriptScope rootScope = root != null ? root : EMPTY_SCOPE;

         // Reuse one proxy bound once as __scope__ (avoids a per-call alloc +
         // putMember/removeMember on the hot per-row path). Swap its root scope
         // and import state for this exec, restoring in finally so reentrant
         // execution (a script that runs another script) is correct. (#75423)
         ensureScopeProxy();

         ScriptScope prevScope = scopeProxy.swapGlobal(rootScope);
         LegacyJavaShim.ImportScope prevImports = scopeProxy.swapImports(null);
         Map<String, Object> prevAssigned = scopeProxy.swapAssigned(null);
         // the var store of the scope the script runs in, made on first read (bug #77595)
         BindingRootProxy.LocalsSource prevLocals =
            scopeProxy.swapLocals(new LocalsSupplier(rootScope, rscope));

         // mark this thread as inside script execution (drives isScriptThread()
         // / getExecScriptable(), e.g. PropertiesEngine's env-modification guard).
         // Scoped tightly to the eval and popped in finally so pooled threads are
         // never left flagged. Use a non-null scope so the flag is reliably set.
         inetsoft.util.script.JavaScriptEngine.pushExecScriptable(rootScope);

         Duration timeout = currentTimeout();

         // created inside the try so a throw from guard(...) still runs the finally cleanup
         ScriptTimeoutGuard.Guard guard = null;

         try {
            guard = timeoutGuard.guard(context, timeout);

            // the inner try-with-resources closes the guard before the catch below runs
            try(ScriptTimeoutGuard.Guard ignored = guard) {
               // FIX B: per-Source error count check (read limit while holding lock)
               int limit = maxErrors();

               if(limit > 0 && errorCounts().getOrDefault(script, 0) >= limit) {
                  return null;
               }

               Value result = script instanceof PieceScript pieces ? evalPieces(pieces)
                  : script instanceof PlainScript plain ?
                     context.eval(plain.source(hostGlobals != null ? hostGlobalNames : null))
                  : context.eval((Source) script);
               return ScriptValueConverter.toHostResult(result);
            }
         }
         catch(PolyglotException ex) {
            // a lock stall or a lost swap file of Java code the script called, e.g. a read of
            // a table, is not a script error: rethrow it so the reader gets it, and do not
            // count it toward the script's errors (bugs #76967, #77910). Rethrow a copy: the
            // instance the Java code threw carries a suppressed Truffle stack trace element,
            // which cannot be serialized, e.g. in the response of a cluster call (bug #78084)
            if(ex.isHostException()) {
               RuntimeException unavailable = DataUnavailable.find(ex.asHostException());

               // but a swap read interrupted on the exec thread means a timeout or a cancel
               // stopped this exec, not that the data is lost: report the stop, with no
               // swap failure in its cause chain, so a reader does not take it for a lost
               // swap and run the script again from the state the stopped exec left, e.g. a
               // var it already changed (bug #78098)
               if(unavailable instanceof SwapReadInterruptedException) {
                  ScriptException se = new ScriptException(ex.getMessage());
                  se.setStackTrace(ex.getStackTrace());
                  se.setStopped(true);
                  throw se;
               }

               if(unavailable != null) {
                  throw DataUnavailable.copy(unavailable);
               }
            }

            // nor is the load failure of a table the script read, for a reader that has to fail,
            // e.g. a scheduled run. It is kept as the cause, for the readers that look for it
            // (a calc table), and other callers get the script error as before (bug #78071)
            TableLoadException loadFailure = ex.isHostException() ?
               TableLoadException.find(ex.asHostException()) : null;

            // FIX B: increment per-Source error count and warn when limit first crossed
            int limit = maxErrors();

            if(limit > 0 && loadFailure == null) {
               int prev = errorCounts().getOrDefault(script, 0);
               int next = prev + 1;
               errorCounts().put(script, next);

               if(next == limit) {
                  LOG.warn("Script max errors exceeded ({})", limit);
               }
            }

            String loc = "";

            if(ex.getSourceLocation() != null) {
               loc = " (line " + ex.getSourceLocation().getStartLine() + ")";
            }

            // Do not retain the PolyglotException as the cause: it is not
            // serializable (PolyglotException.writeObject throws), which would
            // mask the real script error when this exception is marshalled
            // across the cluster (e.g. an Ignite affinity-call response). The
            // message already carries the JS error text and line; copy the
            // merged host/guest stack trace so nothing useful is lost. (#75555)
            // Nor the load failure itself, which carries a suppressed Truffle stack trace
            // element once it crossed the engine: keep a copy of it (bug #78071)
            ScriptException se = loadFailure == null ?
               new ScriptException(ex.getMessage() + loc) :
               new ScriptException(ex.getMessage() + loc, new TableLoadException(
                  loadFailure.getMessage(), loadFailure.getCause()));
            se.setStackTrace(ex.getStackTrace());
            // what the dropped cause said: stopped by a timeout or cancel, not failed. Also
            // when Java code the script called was stopped, e.g. a read of a formula cell
            // that timed out, so the stop is not taken for an error of this script (bug #77949)
            se.setStopped(ex.isInterrupted() || ex.isCancelled() ||
                          ex.isHostException() && ScriptTimeoutGuard.isStop(ex.asHostException()));
            throw se;
         }
         finally {
            // clear the script-execution flag for this thread (balanced with the
            // pushExecScriptable above) so reused threads are not left flagged.
            inetsoft.util.script.JavaScriptEngine.popExecScriptable();

            // restore the prior scope/imports (FIX D: prevents cross-exec bleed;
            // here via swap-restore rather than rebinding, and correct under
            // reentrant exec). __scope__ stays bound to the reused proxy.
            scopeProxy.swapGlobal(prevScope);
            scopeProxy.swapImports(prevImports);
            scopeProxy.swapAssigned(prevAssigned);
            scopeProxy.swapLocals(prevLocals);

            // an interrupt that could not stop this exec leaves the Context in an unknown
            // state (bug #76960, spec §9); the base engine keeps it, a pooled one dooms it
            if(guard != null && guard.interruptTimedOut()) {
               try {
                  onInterruptTimeout();
               }
               catch(Exception ex) {
                  // never let the hook mask the exec's own result or exception
                  LOG.warn("Failed to handle script interrupt timeout", ex);
               }
            }
         }
      }
      finally {
         exitExecContext(execMark);
         lock.unlock();
      }
   }

   /**
    * Called at the start of every exec to mark the pooled worksheet context executing on this
    * thread (bug #76960). This engine is not pooled, so it suspends any mark set by an outer
    * pooled exec: a nested exec of this engine must never see the host-boundary conversions of
    * the outer worksheet context. With the pool off no mark is ever set and this is a no-op.
    *
    * @param script the script about to run; a pooled engine names it in its diagnostics.
    *
    * @return the token {@link #exitExecContext} restores.
    */
   protected Object enterExecContext(Object script) {
      return WsExecContext.suspend();
   }

   /**
    * Restore the mark {@link #enterExecContext} replaced.
    */
   protected void exitExecContext(Object token) {
      WsExecContext.resume(token);
   }

   /**
    * Called when a timeout interrupt of an exec could not stop it within its bound, so this
    * engine's Context is in an unknown state. The base engine keeps using it, as before.
    */
   protected void onInterruptTimeout() {
   }

   /**
    * Run a {@link PieceScript}. Bug #77331: if the reset of its first piece would skip
    * one of its names on this Context (a var named like a global the engine defines,
    * such as the CALC function {@code max} or a {@code put()} name), the var would
    * start each run with that global or the previous run's value, so run the body as
    * the eval wrapper instead, whose vars start undefined on every run, as before
    * #77249. Decided per exec, since a compiled script is shared by engines whose
    * host globals differ. Caller holds lock.
    */
   private Value evalPieces(PieceScript pieces) {
      return pieces.collidesWith(hostGlobals != null ? hostGlobalNames : null) ?
         context.eval(pieces.wrapper()) : pieces.eval(context);
   }

   /**
    * Bug #77595: the var stores of a script run on {@code root} (see {@link #localsFor}),
    * looked up on their first read. Only a script that declares vars reads {@link #own},
    * so a script without vars never makes a store. Read from the guest, under
    * {@code lock}.
    */
   private final class LocalsSupplier implements BindingRootProxy.LocalsSource {
      LocalsSupplier(ScriptScope root, Object rscope) {
         this.root = root;
         this.rscope = rscope;
      }

      @Override
      public Object view() {
         if(own != null) {
            return own;
         }

         if(view == null) {
            view = localsFor(root, rscope, false);
         }

         return view;
      }

      @Override
      public Object own() {
         if(own == null) {
            own = localsFor(root, rscope, true);
         }

         return own;
      }

      private final ScriptScope root;
      private final Object rscope;
      private Value view;
      private Value own;
   }

   /** A var store and the store of the nearest ancestor scope that has one. */
   private static final class LocalsEntry {
      LocalsEntry(Value store, LocalsEntry parent) {
         this.store = store;
         this.parent = parent;
      }

      final Value store;
      LocalsEntry parent;
   }

   /**
    * Bug #77595: the var store of {@code root}, the scope a script runs in. Rhino defined
    * a script's top-level {@code var} on that scope: an assembly script's on the
    * assembly, a calc table cell's on the table, onInit's on the viewsheet scope. So a
    * {@code var} shadowed a variable of the same name of a parent scope (an onInit or
    * onLoad variable) for the script and the scripts of its scope only, and lived as long
    * as its scope. Here every script's top-level vars are globals of the Context, so a
    * script's {@code var n} would replace onInit's {@code n} for every later script.
    *
    * <p>A store is a native object made once per scope object (identity, weakly held, so
    * it lives as long as the scope), whose prototype is the store of the nearest
    * ancestor scope ({@link ScriptScope#getParentScope}) that has one, so a script also
    * reads the vars of its parent scopes' scripts, as through Rhino's scope chain. A
    * function a script declares closes over the store of its run. Unlike Rhino, an
    * assignment without {@code var} to a parent store's var (a calc table cell setting
    * its assembly script's var) makes a copy in the child's store, as for any
    * prototype; the vars of the sheet's own scripts are globals, so this never applies
    * to them. A scope with a {@link ScopeLocals} holds its own store, so the store
    * lives as long as the scope even when a var of it refers back to the scope (#77866);
    * the store of any other scope is held by this engine, weakly keyed by the scope, and
    * one holding a value that refers to its own scope keeps the scope until this engine
    * is re-initialized, closed or dropped.
    *
    * <p>The vars of these scripts stay globals, as before, and the frozen empty
    * {@link #NO_LOCALS_VAR} object is returned:
    * <ul>
    *   <li>a script run on the report or viewsheet scope itself ({@code root == rscope},
    *       onInit) or on a direct child of it ({@code thisViewsheet}, onLoad), whose
    *       declarations every script of the sheet sees (#75596). In Rhino an onLoad
    *       var lived on {@code thisViewsheet}, which every assembly's chain passes
    *       through, so the global gives those scripts the same value, and keeps it
    *       visible to a script run on a scope outside that chain;</li>
    *   <li>a script run without a scope;</li>
    *   <li>a script run on a scope chain with an {@link OwnedVarScope} (a formula table,
    *       Testing #77123), whose top-level vars the table owns.</li>
    * </ul>
    * @param create whether to make the store of {@code root} if it has none; if not, the
    *               store of its nearest ancestor scope that has one is returned instead.
    *
    * Caller holds {@code lock}.
    */
   private Value localsFor(ScriptScope root, Object rscope, boolean create) {
      if(noLocals == null) {
         installLocals();
      }

      if(root == EMPTY_SCOPE || root == rscope ||
         rscope != null && root.getParentScope() == rscope || hasOwnedVarScope(root))
      {
         return noLocals;
      }

      LocalsEntry parent = nearestLocals(root.getParentScope());
      LocalsEntry entry = storedLocals(root);

      if(entry == null && !create) {
         return parent != null ? parent.store : noLocals;
      }

      if(entry == null) {
         entry = new LocalsEntry(
            newLocals.execute(parent != null ? parent.store : null), parent);
         ScopeLocals held = root.getScopeLocals();

         if(held != null) {
            held.put(this, entry);
            ownedLocals.add(root);
         }
         else {
            localStores.put(root, entry);
         }
      }
      else if(entry.parent != parent) {
         // a parent scope ran its first script after this one
         setLocalsParent.execute(entry.store, parent != null ? parent.store : null);
         entry.parent = parent;
      }

      return entry.store;
   }

   /** The store of {@code scope} or of its nearest ancestor that has one, or null. */
   private LocalsEntry nearestLocals(ScriptScope scope) {
      for(int depth = 0; scope != null && depth < MAX_SCOPE_DEPTH; depth++) {
         LocalsEntry entry = storedLocals(scope);

         if(entry != null) {
            return entry;
         }

         scope = scope.getParentScope();
      }

      return null;
   }

   /**
    * The store of {@code scope} itself, or null. Bug #77866: the store of a scope with a
    * {@link ScopeLocals} is held by the scope, not by this engine: a store whose var refers
    * back to its scope (the scope itself, as {@code worksheet} on a query view, or a member
    * object that reaches it, as a calc table's {@code field}) would otherwise keep the
    * scope, a {@link WeakHashMap} key, as long as this engine. Caller holds {@code lock}.
    */
   private LocalsEntry storedLocals(ScriptScope scope) {
      ScopeLocals held = scope.getScopeLocals();
      return held != null ? (LocalsEntry) held.get(this) : localStores.get(scope);
   }

   /** Drop the var stores of the current context (#77595, #77866). Caller holds lock. */
   private void releaseLocals() {
      for(ScriptScope scope : ownedLocals) {
         ScopeLocals held = scope.getScopeLocals();

         if(held != null) {
            held.remove(this);
         }
      }

      ownedLocals.clear();
      localStores.clear();
   }

   private static boolean hasOwnedVarScope(ScriptScope scope) {
      for(int depth = 0; scope != null && depth < MAX_SCOPE_DEPTH; depth++) {
         if(scope instanceof OwnedVarScope) {
            return true;
         }

         scope = scope.getParentScope();
      }

      return false;
   }

   // a bound on a scope chain walk, against a chain that loops
   private static final int MAX_SCOPE_DEPTH = 64;

   /** Lazily create the reusable __scope__ proxy and bind it once. Caller holds lock. */
   private void ensureScopeProxy() {
      if(scopeProxy == null) {
         scopeProxy = new BindingRootProxy(EMPTY_SCOPE,
                                           inetsoft.util.script.FormulaContext::getExecScriptScope,
                                           classFilter, context);
         scopeProxy.setBuiltinScope(calcScope);
         context.getBindings("js").putMember("__scope__", scopeProxy);
      }
   }

   /**
    * Forget cached answers about which names are globals, after globals were deleted from
    * this engine's Context (bug #76960). Caller holds {@code lock}.
    */
   protected void invalidateGlobalBindings() {
      if(scopeProxy != null) {
         scopeProxy.invalidateGlobalBindingCache();
      }
   }

   /**
    * Read the script.max.errors limit from SreeEnv. Returns 0 to mean "no limit".
    * Must be called while holding {@code lock} (result used inline in exec).
    */
   /**
    * Removes leading ECMAScript Directive Prologue entries for {@code "use strict"}
    * so the body can be embedded without silently flipping to strict-mode
    * evaluation (which would forbid {@code with} in the library-function wrapper
    * and change assignment/scope/`this` semantics in the {@code eval(...)} form
    * used by {@link #compile}).
    *
    * <p>A directive prologue is recognized per the spec: it may be preceded by
    * whitespace and line/block comments, and the terminating {@code ;} is
    * optional (ASI). This tolerant scan therefore matches all of
    * {@code "use strict";}, a bare {@code "use strict"} on its own line, and a
    * directive preceded by a comment — not just the semicolon-terminated literal.
    */
   private static String stripStrictDirectives(String src) {
      String s = src;

      while(true) {
         int i = skipWhitespaceAndComments(s, 0);
         int end = matchUseStrictDirective(s, i);

         if(end < 0) {
            break;
         }

         // drop everything up to and including the directive (any skipped
         // leading comments are inert, so discarding them is harmless), but keep
         // its line terminators so later lines keep their numbers (Bug #77322).
         s = lineTerminatorsOf(s, end) + s.substring(end);
      }

      return s;
   }

   /**
    * The line terminator characters ({@code \n}, {@code \r}, U+2028, U+2029) of
    * {@code s} before {@code end}, in order, so a CRLF stays one line break.
    */
   private static String lineTerminatorsOf(String s, int end) {
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < end; i++) {
         char c = s.charAt(i);

         if(c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029') {
            sb.append(c);
         }
      }

      return sb.toString();
   }

   /**
    * Skip leading whitespace, {@code //} line comments and {@code /* *}{@code /}
    * block comments starting at {@code from}; return the index of the first
    * significant character (or {@code s.length()}).
    */
   private static int skipWhitespaceAndComments(String s, int from) {
      int i = from;

      while(i < s.length()) {
         char c = s.charAt(i);

         if(Character.isWhitespace(c)) {
            i++;
         }
         else if(c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
            i += 2;

            while(i < s.length() && s.charAt(i) != '\n' && s.charAt(i) != '\r') {
               i++;
            }
         }
         else if(c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
            i += 2;

            while(i + 1 < s.length() && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
               i++;
            }

            i = Math.min(i + 2, s.length());
         }
         else {
            break;
         }
      }

      return i;
   }

   /**
    * If a {@code "use strict"} / {@code 'use strict'} directive begins at
    * {@code from}, return the index just past it (consuming an optional trailing
    * {@code ;}, ASI-style); otherwise return -1.
    *
    * <p>A directive prologue entry is an ExpressionStatement whose expression is
    * a lone StringLiteral, so the literal must be terminated by {@code ;}, a line
    * break (ASI), {@code }} or EOF. When it is instead followed by a continuation
    * (e.g. {@code "use strict" + x}), the literal is part of a larger expression
    * and must NOT be treated as a directive — otherwise we would corrupt the
    * script by stripping the leading token.
    */
   private static int matchUseStrictDirective(String s, int from) {
      for(String lit : new String[] { "\"use strict\"", "'use strict'" }) {
         if(s.startsWith(lit, from)) {
            int j = from + lit.length();
            int k = j;

            // skip spaces/tabs between the literal and its terminator.
            while(k < s.length() && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) {
               k++;
            }

            if(k >= s.length()) {
               return j;                 // EOF -> ASI terminates the directive
            }

            char c = s.charAt(k);

            if(c == ';') {
               return k + 1;             // explicit terminator (consume it)
            }

            if(c == '\n' || c == '\r' || c == '}') {
               return j;                 // line break (ASI) or end of block
            }

            return -1;                   // continuation -> not a directive
         }
      }

      return -1;
   }

   /**
    * Build the JS snippet, appended inside the compile() wrapper after the eval,
    * that copies each top-level {@code var}/{@code function} declaration in
    * {@code body} out to the context global so it persists for later scripts
    * (#75596). Each copy is guarded by {@code typeof} (so names that turned out
    * not to be reachable at the eval's top level are skipped) and wrapped in a
    * {@code try/catch} so it can never disrupt the user's script. Returns an
    * empty string when the body declares nothing.
    * <p>
    * The copy unconditionally overwrites {@code globalThis[name]}, including any
    * built-in function/constant of the same name (e.g. a script-local
    * {@code var trim = ...;} permanently clobbers the built-in {@code trim} for
    * every later script on this engine/context). This matches the pre-#75550
    * Rhino behavior being restored here and is not a new regression, but the
    * blast radius is wider than the transient-wrapper-frame behavior #75550
    * introduced.
    * <p>
    * A var that an {@link OwnedVarScope} of the executing chain owns (a formula
    * table's var, Testing #77123) is not copied: {@code typeof} reads it from its
    * owner through {@code with(__scope__)}, and a copy would leak it to other tables
    * and later scripts. The owner check is made at run time, since a compiled
    * script is cached and shared by every scope that runs it. It asks {@code this},
    * which both wrappers bind to {@code __scope__}: a {@code __scope__} reference
    * inside {@code with(__scope__)} would first miss through the whole scope chain.
    */
   private static String buildDeclarationHoist(String body) {
      Set<String> names = collectTopLevelDeclarations(body);

      if(names.isEmpty()) {
         return "";
      }

      // Bug #77595: copied to the var store of the exec scope if exec bound one, so
      // the declaration stays in the scope the script ran in, as in Rhino
      StringBuilder sb = new StringBuilder();
      String target = "(" + OWN_LOCALS_VAR + "===" + NO_LOCALS_VAR + "?globalThis:" +
         OWN_LOCALS_VAR + ")";

      for(String name : names) {
         sb.append("try{if(typeof ").append(name).append("!==\"undefined\"&&!this.")
            .append(BindingRootProxy.OWNED_VAR_PROBE).append("(")
            .append(toJsStringLiteral(name)).append(")){").append(target).append("[")
            .append(toJsStringLiteral(name)).append("]=").append(name)
            .append(";}}catch(").append(HOIST_ERR_VAR).append("){}");
      }

      return sb.toString();
   }

   /**
    * The var names a formula table owns for its formulas (Testing #77123): the union of
    * the names each script declares with {@code var} outside any function body (in a block
    * and in a {@code for(var ...)} head too, as {@code var} is function scoped), minus every
    * name any of the scripts declares with a top-level {@code let} or {@code const}. Such a
    * declaration is rewritten to a per-run {@code var} (#76980, #77181) that must stay per
    * run, even if another formula of the table declares a {@code var} of the same name.
    *
    * <p>A lexical scan on the shared tokenizing of {@link #stripStringsAndComments}, like
    * the other declaration scanners. A missed name keeps the per-script behavior; a name
    * collected from a method-shorthand body (no {@code function} keyword) becomes owned by
    * the table, shadowing a same-named global for that table only.
    *
    * @param scripts the scripts, null elements are skipped.
    */
   public static Set<String> collectOwnedVarNames(Collection<String> scripts) {
      Set<String> names = new LinkedHashSet<>();
      Set<String> lexical = new LinkedHashSet<>();

      for(String script : scripts) {
         if(script != null && !script.isEmpty()) {
            collectOwnedVarNames(script, names);
            collectTopLevelLexicalNames(script, lexical);
         }
      }

      names.removeAll(lexical);
      return names;
   }

   /**
    * Add the names {@code script} declares with {@code var} outside any function body.
    */
   private static void collectOwnedVarNames(String script, Set<String> names) {
      String src = stripStringsAndComments(script);
      // per open brace, whether it opens a function body
      Deque<Boolean> braces = new ArrayDeque<>();
      int fdepth = 0;
      boolean pendingFn = false;
      int n = src.length();
      int i = 0;
      char prev = 0;

      while(i < n) {
         char c = src.charAt(i);

         if(isIdentStart(c)) {
            int start = i;
            i++;

            while(i < n && isIdentPart(src.charAt(i))) {
               i++;
            }

            String word = src.substring(start, i);

            // ignore keywords used as member names (obj.var / obj.function)
            if(prev != '.') {
               if(word.equals("var") && fdepth == 0) {
                  i = collectVarNames(src, i, names);
               }
               else if(word.equals("function")) {
                  pendingFn = true;
               }
            }

            prev = src.charAt(i - 1);
            continue;
         }

         // an arrow function with a block body
         if(c == '=' && i + 1 < n && src.charAt(i + 1) == '>') {
            int j = skipWhitespace(src, i + 2);

            if(j < n && src.charAt(j) == '{') {
               pendingFn = true;
            }
         }
         else if(c == '{') {
            braces.push(pendingFn);
            fdepth += pendingFn ? 1 : 0;
            pendingFn = false;
         }
         else if(c == '}' && !braces.isEmpty()) {
            fdepth -= braces.pop() ? 1 : 0;
         }

         if(!Character.isWhitespace(c)) {
            prev = c;
         }

         i++;
      }
   }

   /**
    * Bug #77249: whether {@code script} declares a function in a nested block (or as
    * the statement of an {@code if}/{@code else}/loop) outside any function body,
    * e.g. {@code if(v > 0) { function f(){} }}. Run as a piece of a
    * {@link PieceScript}, such a function is hoisted (Annex B) to a global of the
    * Context, which keeps the previous run's function when the block is not entered;
    * in the eval wrapper it is a fresh binding of the wrapper function on every run.
    * A lexical scan: a false positive only keeps the (correct, slower) eval wrapper,
    * so a {@code function} keyword counts as a declaration unless it is clearly in
    * expression position (after an operator, {@code (}, {@code ,}, {@code [}, an
    * object key's {@code :}, or an expression keyword such as {@code return}); a
    * statement that ends without {@code ;} (ASI) does not hide it.
    */
   static boolean hasBlockFunctionDeclaration(String script) {
      return scanBlockFunctions(script);
   }

   // brace kinds of scanBlockFunctions
   private static final int BLOCK_BRACE = 0;
   private static final int OBJECT_BRACE = 1;
   private static final int FUNCTION_BRACE = 2;

   /**
    * Whether {@code script} declares a function in a nested block outside any function
    * body ({@link #hasBlockFunctionDeclaration}).
    *
    * <p>This scanner serves only that check. It is deliberately not the one that decides
    * the table-owned vars ({@link #collectOwnedVarNames(Collection)} keeps the #5806
    * scanner unchanged): a var this finer brace tracking would take for a function-local
    * one and drop from the owned set is silently shared across rows and tables (#77123),
    * while the older scanner only over-owns a var that is local to its method anyway
    * (#77249 review r4).
    *
    * <p>A brace opens a function body after the {@code function} keyword (at the paren
    * depth of the keyword, so a brace in a default parameter does not count), after
    * {@code =>}, after a {@code class} head (a class body holds no top-level var), and
    * after a {@code name(...)} head directly in an object literal (a method shorthand,
    * getter or setter). An object literal is a brace in expression position. In a block,
    * {@code name(...)} followed by a brace is a call and a block statement (ASI), not a
    * function (#77249). A {@code var}/{@code function}/{@code class} keyword followed
    * by {@code :}, {@code =}, {@code ;} or <code>}</code> is an object key or a class
    * field, not a declaration. A string, template or regex
    * literal counts as a value token.
    */
   private static boolean scanBlockFunctions(String script) {
      String src = stripStringsAndComments(script, true);
      // var names are skipped over (collectVarNames), not used
      Set<String> names = new HashSet<>();
      // per open brace: its kind and the paren depth at which it opened
      Deque<int[]> braces = new ArrayDeque<>();
      // per open paren: its kind (PAREN_*)
      Deque<Integer> parens = new ArrayDeque<>();
      // per pending function: the paren depth of its body brace
      Deque<Integer> pendingFns = new ArrayDeque<>();
      int fdepth = 0;
      boolean pendingClass = false;
      boolean methodHead = false;
      // the previous token is the `)` of an if/while/for/with head
      boolean afterHead = false;
      boolean blockFn = false;
      // the previous token if it is a word, kept across whitespace
      String word = null;
      int n = src.length();
      int i = 0;
      char prev = 0;

      while(i < n) {
         char c = src.charAt(i);
         int enclosing = braces.isEmpty() ? BLOCK_BRACE : braces.peek()[0];

         if(isIdentStart(c)) {
            int start = i;
            i++;

            while(i < n && isIdentPart(src.charAt(i))) {
               i++;
            }

            String before = word;
            boolean head = afterHead;
            word = src.substring(start, i);
            methodHead = false;
            afterHead = false;
            // a keyword used as an object key ({class: 1}, {function: 1}) or a class
            // field ({ class = 1 }, { static function = function(){} }, { function; })
            // is a name: none of `:`, `=`, `;`, `}` can follow the keyword itself
            int after = skipWhitespace(src, i);
            char next = after < n ? src.charAt(after) : 0;
            boolean key = next == ':' || next == ';' || next == '}' ||
               next == '=' && (after + 1 >= n || src.charAt(after + 1) != '=' &&
               src.charAt(after + 1) != '>');

            // ignore keywords used as member names (obj.var / obj.function)
            if(prev != '.' && !key) {
               if(word.equals("var") && fdepth == 0) {
                  i = collectVarNames(src, i, names);
                  word = null;
               }
               else if(word.equals("function")) {
                  // a declaration unless in expression position: a statement that ends
                  // without `;` (ASI) is still a statement. An async function is not
                  // hoisted out of its block, a generator is; over-matching only keeps
                  // the eval wrapper
                  boolean expression = before != null ?
                     FUNCTION_EXPRESSION_AFTER_WORDS.contains(before) :
                     prev == ':' ? enclosing == OBJECT_BRACE :
                     isExpressionOperator(src, start, prev);
                  // a method named `function` ({ function() {} }) directly in an object
                  boolean property = enclosing == OBJECT_BRACE &&
                     parens.size() == braces.peek()[1] && (prev == '{' || prev == ',');
                  boolean nested = !braces.isEmpty() || head ||
                     "else".equals(before) || "do".equals(before);

                  if(fdepth == 0 && !expression && !property && nested) {
                     blockFn = true;
                  }

                  pendingFns.push(parens.size());
               }
               else if(word.equals("class")) {
                  pendingClass = true;
               }
            }

            prev = src.charAt(i - 1);
            continue;
         }

         // an arrow function with a block body
         if(c == '=' && i + 1 < n && src.charAt(i + 1) == '>') {
            int j = skipWhitespace(src, i + 2);

            if(j < n && src.charAt(j) == '{') {
               pendingFns.push(parens.size());
            }
         }
         else if(c == '(') {
            parens.push(word != null && CONTROL_HEAD_KEYWORDS.contains(word) ? PAREN_HEAD :
               word != null && !NON_FUNCTION_HEADS.contains(word) &&
               enclosing == OBJECT_BRACE && parens.size() == braces.peek()[1] ?
               PAREN_METHOD : PAREN_OTHER);
         }
         else if(c == ')' && !parens.isEmpty()) {
            int kind = parens.pop();
            methodHead = kind == PAREN_METHOD;
            afterHead = kind == PAREN_HEAD;
            word = null;
            prev = c;
            i++;
            continue;
         }
         else if(c == '{') {
            int kind;

            if(!pendingFns.isEmpty() && pendingFns.peek() == parens.size()) {
               pendingFns.pop();
               kind = FUNCTION_BRACE;
            }
            else if(methodHead || pendingClass) {
               kind = FUNCTION_BRACE;
            }
            else if(word != null) {
               kind = OBJECT_AFTER_WORDS.contains(word) ? OBJECT_BRACE : BLOCK_BRACE;
            }
            else if(prev == ':') {
               kind = enclosing == OBJECT_BRACE ? OBJECT_BRACE : BLOCK_BRACE;
            }
            else {
               kind = isExpressionOperator(src, i, prev) ? OBJECT_BRACE : BLOCK_BRACE;
            }

            pendingClass = false;
            braces.push(new int[] { kind, parens.size() });
            fdepth += kind == FUNCTION_BRACE ? 1 : 0;
         }
         else if(c == '}' && !braces.isEmpty()) {
            fdepth -= braces.pop()[0] == FUNCTION_BRACE ? 1 : 0;
         }

         if(!Character.isWhitespace(c)) {
            prev = c;
            word = null;
            methodHead = false;
            afterHead = false;
         }

         i++;
      }

      return blockFn;
   }

   // paren kinds of scanBlockFunctions
   private static final int PAREN_OTHER = 0;
   private static final int PAREN_METHOD = 1;
   private static final int PAREN_HEAD = 2;

   /**
    * Whether {@code prev}, the last significant char before {@code pos} of the stripped
    * source, is an operator (or {@code (}, {@code ,}, {@code [}), so the next token is in
    * expression position. A postfix {@code ++}/{@code --} ends a value instead.
    */
   private static boolean isExpressionOperator(String src, int pos, char prev) {
      return prev != 0 && "=(,[?!&|+-*/%<>~^".indexOf(prev) >= 0 &&
         !((prev == '+' || prev == '-') && afterPostfixIncDec(src, pos));
   }

   // words after which `function` is an expression (async: an async function
   // declaration is block scoped, not hoisted out of its block)
   private static final Set<String> FUNCTION_EXPRESSION_AFTER_WORDS = Set.of(
      "return", "typeof", "void", "delete", "new", "in", "of", "instanceof", "throw",
      "case", "yield", "await", "extends", "async");

   // names whose parenthesized head is not a function's parameter list, so a brace
   // after `name(...)` does not open a function body (scanBlockFunctions)
   private static final Set<String> NON_FUNCTION_HEADS = Set.of(
      "if", "for", "while", "switch", "catch", "with", "return", "typeof", "void",
      "delete", "new", "in", "of", "instanceof", "throw", "case", "do", "else", "await",
      "yield");

   // words after which a brace opens an object literal (expression position)
   private static final Set<String> OBJECT_AFTER_WORDS = Set.of(
      "return", "typeof", "void", "delete", "new", "in", "of", "instanceof", "throw",
      "case", "yield", "await", "extends");

   /**
    * Add the names of the top-level {@code let}/{@code const} declarations of
    * {@code script}, the ones {@link #rewriteTopLevelLexicalDeclarations} rewrites.
    */
   private static void collectTopLevelLexicalNames(String script, Set<String> names) {
      List<Integer> decls = new ArrayList<>();
      scanTopLevel(script, null, decls);

      if(decls.isEmpty()) {
         return;
      }

      // offsets are kept, so the declaration positions hold in the stripped source
      String src = stripStringsAndComments(script);

      for(int pos : decls) {
         collectVarNames(src, pos + (script.startsWith("const", pos) ? 5 : 3), names);
      }
   }

   /**
    * Scan {@code body} for identifiers introduced by {@code var} and
    * {@code function} declarations. This is a deliberately lightweight lexical
    * scan (not a full parser): it strips strings/comments first, then collects
    * names following {@code var}/{@code function} tokens. It may over-collect
    * (e.g. names declared inside a nested function) — those are harmless because
    * the emitted copy is {@code typeof}-guarded — and it does not attempt to
    * handle destructuring patterns. Only {@code var}/{@code function} are
    * considered: top-level {@code let}/{@code const} are rewritten to
    * {@code var} before this scan (#76980), and nested ones are block-scoped
    * and never persist anyway.
    */
   private static Set<String> collectTopLevelDeclarations(String body) {
      String src = stripStringsAndComments(body);
      Set<String> names = new LinkedHashSet<>();
      int n = src.length();
      int i = 0;
      char prev = 0;

      while(i < n) {
         char c = src.charAt(i);

         if(isIdentStart(c)) {
            int start = i;
            i++;

            while(i < n && isIdentPart(src.charAt(i))) {
               i++;
            }

            String word = src.substring(start, i);

            // ignore keywords used as member names (obj.var / obj.function)
            if(prev != '.') {
               if(word.equals("var")) {
                  i = collectVarNames(src, i, names);
               }
               else if(word.equals("function")) {
                  i = collectFunctionName(src, i, names);
               }
            }

            prev = src.charAt(i - 1);
            continue;
         }

         if(!Character.isWhitespace(c)) {
            prev = c;
         }

         i++;
      }

      return names;
   }

   /**
    * Collect the identifier(s) in a {@code var} declarator list starting at
    * {@code i} (just past the {@code var} keyword). Handles simple and
    * comma-separated declarators (e.g. {@code var a, b = 1, c}); stops at the end
    * of the statement. Returns the index at which scanning should resume.
    * <p>
    * Known limitation: a line break falling immediately after a bare declarator
    * name and before its own {@code =} or the following {@code ,} (e.g.
    * {@code var a\n = 1, b = 2;}) is treated as end-of-statement, so later
    * declarators in that statement are missed. This is safe — a missed name is
    * simply not hoisted, since the emitted copy is {@code typeof}-guarded — and
    * the pattern is not expected in practice.
    */
   private static int collectVarNames(String src, int i, Set<String> names) {
      int n = src.length();
      int depth = 0;
      boolean expectName = true;

      while(i < n) {
         char c = src.charAt(i);

         if(expectName && depth == 0) {
            if(Character.isWhitespace(c)) {
               i++;
               continue;
            }

            if(isIdentStart(c)) {
               int s = i;
               i++;

               while(i < n && isIdentPart(src.charAt(i))) {
                  i++;
               }

               addName(names, src.substring(s, i));
               expectName = false;
               continue;
            }

            // not a plain identifier (e.g. a destructuring pattern) — stop
            // collecting names but keep scanning to the end of the statement.
            expectName = false;
         }

         if(c == '(' || c == '[' || c == '{') {
            depth++;
         }
         else if(c == ')' || c == ']' || c == '}') {
            if(depth == 0) {
               return i;
            }

            depth--;
         }
         else if(depth == 0) {
            if(c == ';') {
               return i + 1;
            }

            if(c == ',') {
               expectName = true;
               i++;
               continue;
            }

            if(c == '\n' || c == '\r') {
               return i + 1;
            }
         }

         i++;
      }

      return i;
   }

   /**
    * Collect the name of a {@code function} declaration starting at {@code i}
    * (just past the {@code function} keyword). Skips an optional generator
    * {@code *}; adds nothing for an anonymous function expression. Returns the
    * index at which scanning should resume.
    */
   private static int collectFunctionName(String src, int i, Set<String> names) {
      int n = src.length();

      while(i < n && (Character.isWhitespace(src.charAt(i)) || src.charAt(i) == '*')) {
         i++;
      }

      if(i < n && isIdentStart(src.charAt(i))) {
         int s = i;
         i++;

         while(i < n && isIdentPart(src.charAt(i))) {
            i++;
         }

         addName(names, src.substring(s, i));
      }

      return i;
   }

   private static void addName(Set<String> names, String name) {
      // reserved words can never be declared names in valid source; excluding
      // them keeps the generated `typeof <name>` from being a SyntaxError.
      if(!name.isEmpty() && !RESERVED_WORDS.contains(name) &&
         !name.equals(RESULT_VAR) && !name.equals(HOIST_ERR_VAR) &&
         !name.equals(VALUE_VAR))
      {
         names.add(name);
      }
   }

   private static boolean isIdentStart(char c) {
      return Character.isLetter(c) || c == '_' || c == '$';
   }

   private static boolean isIdentPart(char c) {
      return Character.isLetterOrDigit(c) || c == '_' || c == '$';
   }

   /**
    * Replace the contents of string/template literals, comments and
    * regular-expression literals with spaces (line breaks preserved) so a
    * subsequent declaration scan cannot match keywords inside them. Character
    * offsets and overall structure are preserved.
    *
    * <p>This is a small lexer rather than a naive replace: it recognizes
    * template-literal {@code `${ ... }`} substitution nesting (so a backtick
    * inside a substitution does not falsely close the template) and regular
    * expression literals (so slashes inside a regex are not misread as a
    * {@code //} comment). Regex-vs-division is disambiguated by the preceding
    * significant token; when genuinely ambiguous the text is left as code, which
    * at worst over-collects a declaration name (harmless — the emitted copy is
    * typeof-guarded) rather than dropping one. As in {@link #scanTopLevel} and
    * {@link #skipInitializer}, a {@code )} that closes an {@code if}/{@code while}/
    * {@code for}/{@code with} head's condition is followed by a statement, so a
    * {@code /} there starts a regex even though {@link #regexAllowed} alone would
    * read it as division (bug #77305).
    */
   private static String stripStringsAndComments(String s) {
      return stripStringsAndComments(s, false);
   }

   /**
    * {@link #stripStringsAndComments(String)}; with {@code markLiterals} the first
    * blanked char of a string, template (and of each template part after a
    * substitution) and regex literal is {@code 0} instead of a space, so a scan sees
    * the literal as a value token (#77249: {@code s = 'a'} ends a statement).
    */
   private static String stripStringsAndComments(String s, boolean markLiterals) {
      int n = s.length();
      char lit = markLiterals ? '0' : ' ';
      StringBuilder sb = new StringBuilder(n);
      // Code-brace depth at which each open template substitution (`${`) began;
      // the matching `}` at that depth resumes template scanning.
      java.util.Deque<Integer> templateStack = new java.util.ArrayDeque<>();
      int braceDepth = 0;
      char prevSig = 0;   // previous significant code char (regex/division hint)
      String prevWord = null;   // previous identifier/keyword token, else null
      // as in scanTopLevel/skipInitializer: per open bracket, whether it is the
      // `(` of an if/while/for/with head, whose `)` is followed by a statement,
      // so a `/` there starts a regex
      java.util.Deque<Boolean> brackets = new java.util.ArrayDeque<>();
      boolean afterHead = false;   // the previous token closed a control-flow head
      int i = 0;

      while(i < n) {
         char c = s.charAt(i);

         // line comment
         if(c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
            sb.append("  ");
            i += 2;

            while(i < n && !isLineBreak(s.charAt(i))) {
               sb.append(' ');
               i++;
            }

            continue;
         }

         // block comment
         if(c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
            sb.append("  ");
            i += 2;

            while(i < n && !(i + 1 < n && s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
               sb.append(isLineBreak(s.charAt(i)) ? s.charAt(i) : ' ');
               i++;
            }

            if(i + 1 < n) {
               sb.append("  ");
               i += 2;
            }
            else {
               while(i < n) {
                  sb.append(' ');
                  i++;
               }
            }

            continue;
         }

         // regular-expression literal (only where '/' cannot be division)
         if(c == '/' && (afterHead || regexAllowed(s, i, prevSig))) {
            int end = scanRegexEnd(s, i);

            if(end > 0) {
               for(int k = i; k < end; k++) {
                  sb.append(k == i ? lit : isLineBreak(s.charAt(k)) ? s.charAt(k) : ' ');
               }

               i = end;
               prevSig = ')';   // a regex literal ends an expression (division next)
               prevWord = null;
               afterHead = false;
               continue;
            }
         }

         // single/double-quoted string
         if(c == '"' || c == '\'') {
            char quote = c;
            sb.append(lit);
            i++;

            while(i < n) {
               char d = s.charAt(i);

               if(d == '\\') {
                  sb.append("  ");
                  i += 2;
                  continue;
               }

               if(d == quote) {
                  sb.append(' ');
                  i++;
                  break;
               }

               sb.append(isLineBreak(d) ? d : ' ');
               i++;
            }

            prevSig = ')';   // a string ends an expression
            prevWord = null;
            afterHead = false;
            continue;
         }

         // template-literal start
         if(c == '`') {
            sb.append(lit);
            i = scanTemplateBody(s, i + 1, sb, braceDepth, templateStack);
            prevSig = ')';
            prevWord = null;
            afterHead = false;
            continue;
         }

         // '}' that closes an open template substitution -> resume the template
         if(c == '}' && !templateStack.isEmpty() && braceDepth == templateStack.peek()) {
            templateStack.pop();
            sb.append(lit);
            i = scanTemplateBody(s, i + 1, sb, braceDepth, templateStack);
            prevSig = ')';
            prevWord = null;
            afterHead = false;
            continue;
         }

         // whitespace and comments between a control head's `)` and the next
         // token must not reset afterHead (only a real token does) — handled
         // above for // and /* comments (which `continue` without touching
         // afterHead) and here for plain whitespace.
         if(Character.isWhitespace(c)) {
            sb.append(c);
            i++;
            continue;
         }

         // identifier / keyword, tracked so a following `(` can be recognized as
         // an if/while/for/with control-flow head, mirroring scanTopLevel/
         // skipInitializer.
         if(isIdentStart(c)) {
            int start = i;

            while(i < n && isIdentPart(s.charAt(i))) {
               sb.append(s.charAt(i));
               i++;
            }

            prevWord = prevSig == '.' ? null : s.substring(start, i);
            prevSig = s.charAt(i - 1);
            afterHead = false;
            continue;
         }

         boolean closedHead = false;

         if(c == '(' || c == '[' || c == '{') {
            brackets.push(c == '(' && prevWord != null && CONTROL_HEAD_KEYWORDS.contains(prevWord));

            if(c == '{') {
               braceDepth++;
            }
         }
         else if(c == ')' || c == ']' || c == '}') {
            if(c == '}' && braceDepth > 0) {
               braceDepth--;
            }

            if(!brackets.isEmpty()) {
               closedHead = brackets.pop() && c == ')';
            }
         }

         sb.append(c);
         prevSig = c;
         prevWord = null;
         afterHead = closedHead;
         i++;
      }

      return sb.toString();
   }

   private static boolean isLineBreak(char c) {
      return c == '\n' || c == '\r';
   }

   /**
    * Scan a template-literal body starting at {@code i} (just past a backtick or
    * the {@code }} that closed a substitution), blanking each character. Returns
    * the index just past the closing backtick, or — when a {@code ${} is reached
    * — the index just past {@code ${} after recording the current code-brace
    * depth on {@code templateStack}, so the caller resumes scanning the
    * substitution as code.
    */
   private static int scanTemplateBody(String s, int i, StringBuilder sb,
                                       int braceDepth, java.util.Deque<Integer> templateStack)
   {
      int n = s.length();

      while(i < n) {
         char d = s.charAt(i);

         if(d == '\\') {
            sb.append("  ");
            i += 2;
            continue;
         }

         if(d == '`') {
            sb.append(' ');
            return i + 1;
         }

         if(d == '$' && i + 1 < n && s.charAt(i + 1) == '{') {
            sb.append("  ");
            templateStack.push(braceDepth);
            return i + 2;
         }

         sb.append(isLineBreak(d) ? d : ' ');
         i++;
      }

      return i;
   }

   /**
    * Decide whether a {@code /} at {@code slashIndex} begins a regular-expression
    * literal (rather than a division operator), based on the previous significant
    * token. Conservative: only returns true in positions where a regex is
    * unambiguous or a division would be invalid.
    */
   private static boolean regexAllowed(String s, int slashIndex, char prevSig) {
      if(prevSig == 0) {
         return true;   // start of input
      }

      // `i++ / 2`: a postfix `++`/`--` ends a value, so a `/` after it is
      // division (a regex literal can never follow one)
      if((prevSig == '+' || prevSig == '-') && afterPostfixIncDec(s, slashIndex)) {
         return false;
      }

      if(isIdentPart(prevSig)) {
         // end of an identifier/number: a regex only follows a keyword
         return REGEX_PRECEDING_KEYWORDS.contains(precedingWord(s, slashIndex));
      }

      // a value-ender (), ], and — via prevSig=')' — a prior string/regex/template)
      // means the '/' is division; anything else is a regex position.
      return prevSig != ')' && prevSig != ']';
   }

   /**
    * Whether the last token before the {@code /} at {@code slashIndex} (skipping
    * whitespace) is a postfix {@code ++}/{@code --}: the operator's two chars are
    * adjacent and follow a value end (an identifier/number char, {@code )} or
    * {@code ]}) on the same line, as in {@code i++}, {@code a[0]--} or
    * {@code f()++}. Two unary pluses ({@code a + +/re/}) and {@code a+++/re/}
    * ({@code a++ +}) are not matched. A comment between the operator and the
    * {@code /} is not skipped, which keeps the regex reading.
    */
   private static boolean afterPostfixIncDec(String s, int slashIndex) {
      int j = slashIndex - 1;

      while(j >= 0 && Character.isWhitespace(s.charAt(j))) {
         j--;
      }

      if(j < 2) {
         return false;
      }

      char op = s.charAt(j);

      if(op != '+' && op != '-' || s.charAt(j - 1) != op) {
         return false;
      }

      int k = j - 2;

      // `i ++`: the operand is on the same line (after a line break `++` is prefix)
      while(k >= 0 && Character.isWhitespace(s.charAt(k)) && !isLineBreak(s.charAt(k))) {
         k--;
      }

      if(k < 0) {
         return false;
      }

      char v = s.charAt(k);
      return isIdentPart(v) || v == ')' || v == ']';
   }

   /** The identifier/keyword ending just before {@code end} (skipping whitespace). */
   private static String precedingWord(String s, int end) {
      int j = end - 1;

      while(j >= 0 && Character.isWhitespace(s.charAt(j))) {
         j--;
      }

      int wordEnd = j + 1;

      while(j >= 0 && isIdentPart(s.charAt(j))) {
         j--;
      }

      return s.substring(j + 1, wordEnd);
   }

   /**
    * If a regular-expression literal begins at {@code start} (an opening
    * {@code /}), return the index just past its closing {@code /} and flags;
    * otherwise return -1 (unterminated, or spanning a line break — neither is a
    * valid regex literal).
    */
   private static int scanRegexEnd(String s, int start) {
      int n = s.length();
      int j = start + 1;
      boolean inClass = false;

      while(j < n) {
         char c = s.charAt(j);

         if(isLineBreak(c)) {
            return -1;
         }

         if(c == '\\') {
            j += 2;
            continue;
         }

         if(c == '[') {
            inClass = true;
         }
         else if(c == ']') {
            inClass = false;
         }
         else if(c == '/' && !inClass) {
            j++;

            while(j < n && isIdentPart(s.charAt(j))) {
               j++;   // regex flags
            }

            return j;
         }

         j++;
      }

      return -1;
   }

   private int maxErrors() {
      try {
         String prop = MAX_ERRORS_PROP.get();

         if(prop == null || prop.isEmpty()) {
            return 30000;
         }

         int val = Integer.parseInt(prop.trim());
         return val <= 0 ? 0 : val;
      }
      catch(NumberFormatException ex) {
         // property is set but not a valid integer — treat as no limit
         return 30000;
      }
      catch(Exception ex) {
         // SreeEnv unavailable (e.g. in tests)
         return 30000;
      }
   }

   protected Duration currentTimeout() {
      String val;

      try {
         val = TIMEOUT_PROP.get();
      }
      catch(Exception ex) {
         // SreeEnv unavailable (e.g. test context) → no timeout
         return Duration.ZERO;
      }

      // property unset is the normal "no timeout" case — not a warning
      if(val == null || val.isEmpty()) {
         return Duration.ZERO;
      }

      try {
         return Duration.ofSeconds(Long.parseLong(val));
      }
      catch(NumberFormatException ex) {
         // set but not a valid number — warn, then disable the timeout
         LOG.warn("Invalid script.execution.timeout value '{}'; script timeouts disabled", val);
         return Duration.ZERO;
      }
   }

   public void setSQL(boolean sql) { this.sql = sql; }
   public boolean isSQL() { return sql; }

   /**
    * Put a variable in the engine's global bindings.
    */
   public void put(String name, Object value) {
      lock.lock();

      try {
         if(context != null) {
            context.getBindings("js").putMember(name, ScriptValueConverter.toGuest(value));
            markHostGlobal(name);
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Get a variable from the engine's global bindings.
    */
   public Object get(String name) {
      lock.lock();

      try {
         if(context != null) {
            Value v = context.getBindings("js").getMember(name);
            return v == null ? null : ScriptValueConverter.toHost(v);
         }

         return null;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Remove a variable from the engine's global bindings.
    */
   public void remove(String name) {
      lock.lock();

      try {
         if(context != null) {
            context.getBindings("js").removeMember(name);
         }

         // Evict any cached "is a real global" answer for this name, so a
         // subsequent lookup re-probes the (now-removed) global instead of
         // permanently treating it as still present -- which would block the
         // case-insensitive CALC-builtin fallback in BindingRootProxy. (#77008)
         if(scopeProxy != null) {
            scopeProxy.forgetGlobal(name);
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Get all member keys from the global bindings.
    */
   public Object[] getMemberKeys() {
      lock.lock();

      try {
         if(context == null) {
            return new Object[0];
         }

         Value bindings = context.getBindings("js");
         Set<String> keys = bindings.getMemberKeys();
         return keys.toArray();
      }
      finally {
         lock.unlock();
      }
   }

   @Override
   public void close() {
      lock.lock();

      try {
         if(context != null) {
            context.close(true);
            context = null;
         }

         // a long-lived scope must not keep the stores of the closed context (#77866)
         releaseLocals();
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Register a Java class as a callable/instantiable JS global under {@code jsName}.
    * Exposes static fields, static methods, nested types, and allows {@code new}.
    * Guarded so a class-load failure does not abort engine init.
    */
   private void putClassProxy(Value bindings, String jsName, String fqcn) {
      try {
         Value java = bindings.getMember("Java");
         Value hostType = java.getMember("type").execute(fqcn);
         bindings.putMember(jsName, new JavaClassProxy(hostType, fqcn));
      }
      catch(Throwable ex) {
         LOG.warn("Failed to install class proxy {}", jsName, ex);
      }
   }

   /**
    * Register all chart scripting classes that are advertised in
    * {@code VSScriptableController.CHART_CLASSES} but not covered by the
    * constant-scope registrations above. This restores the Rhino behavior
    * where {@code inetsoft.graph.*} classes were reachable by simple name
    * via the package tree (Bug #75524).
    */
   private void installChartClasses(Value bindings) {
      // inetsoft.graph top-level
      putClassProxy(bindings, "EGraph",          "inetsoft.graph.EGraph");
      putClassProxy(bindings, "GraphConstants",   "inetsoft.graph.GraphConstants");
      putClassProxy(bindings, "LegendSpec", "inetsoft.graph.LegendSpec");
      putClassProxy(bindings, "TitleSpec",  "inetsoft.graph.TitleSpec");
      putClassProxy(bindings, "TextSpec",   "inetsoft.graph.TextSpec");
      putClassProxy(bindings, "AxisSpec",   "inetsoft.graph.AxisSpec");
      putClassProxy(bindings, "PlotSpec",   "inetsoft.graph.PlotSpec");

      // elements
      putClassProxy(bindings, "GraphElement",    "inetsoft.graph.element.GraphElement");
      putClassProxy(bindings, "IntervalElement", "inetsoft.graph.element.IntervalElement");
      putClassProxy(bindings, "LineElement",     "inetsoft.graph.element.LineElement");
      putClassProxy(bindings, "SchemaElement",   "inetsoft.graph.element.SchemaElement");
      putClassProxy(bindings, "PointElement",    "inetsoft.graph.element.PointElement");
      putClassProxy(bindings, "AreaElement",     "inetsoft.graph.element.AreaElement");

      // coords
      putClassProxy(bindings, "PolarCoord",    "inetsoft.graph.coord.PolarCoord");
      putClassProxy(bindings, "RectCoord",     "inetsoft.graph.coord.RectCoord");
      putClassProxy(bindings, "Rect25Coord",   "inetsoft.graph.coord.Rect25Coord");
      putClassProxy(bindings, "ParallelCoord", "inetsoft.graph.coord.ParallelCoord");
      putClassProxy(bindings, "TriCoord",      "inetsoft.graph.coord.TriCoord");
      putClassProxy(bindings, "FacetCoord",    "inetsoft.graph.coord.FacetCoord");

      // scales / ranges. Scale is the abstract base class; scripts don't
      // construct it but reference its scale-option constants (Scale.TICKS,
      // Scale.ZERO, ...) passed to Scale.setScaleOption(int) (Bug #75684).
      putClassProxy(bindings, "Scale",            "inetsoft.graph.scale.Scale");
      putClassProxy(bindings, "LinearScale",      "inetsoft.graph.scale.LinearScale");
      putClassProxy(bindings, "LogScale",          "inetsoft.graph.scale.LogScale");
      putClassProxy(bindings, "PowerScale",        "inetsoft.graph.scale.PowerScale");
      putClassProxy(bindings, "TimeScale",         "inetsoft.graph.scale.TimeScale");
      putClassProxy(bindings, "CategoricalScale",  "inetsoft.graph.scale.CategoricalScale");
      putClassProxy(bindings, "LinearRange",       "inetsoft.graph.scale.LinearRange");
      putClassProxy(bindings, "StackRange",        "inetsoft.graph.scale.StackRange");

      // aesthetic frames
      putClassProxy(bindings, "MultiTextFrame",        "inetsoft.graph.aesthetic.MultiTextFrame");
      putClassProxy(bindings, "PieShapeFrame",         "inetsoft.graph.aesthetic.PieShapeFrame");
      putClassProxy(bindings, "BrightnessColorFrame",  "inetsoft.graph.aesthetic.BrightnessColorFrame");
      putClassProxy(bindings, "SaturationColorFrame",  "inetsoft.graph.aesthetic.SaturationColorFrame");
      putClassProxy(bindings, "BipolarColorFrame",     "inetsoft.graph.aesthetic.BipolarColorFrame");
      putClassProxy(bindings, "StaticColorFrame",      "inetsoft.graph.aesthetic.StaticColorFrame");
      putClassProxy(bindings, "CircularColorFrame",    "inetsoft.graph.aesthetic.CircularColorFrame");
      putClassProxy(bindings, "GradientColorFrame",    "inetsoft.graph.aesthetic.GradientColorFrame");
      putClassProxy(bindings, "HeatColorFrame",        "inetsoft.graph.aesthetic.HeatColorFrame");
      putClassProxy(bindings, "RainbowColorFrame",     "inetsoft.graph.aesthetic.RainbowColorFrame");
      putClassProxy(bindings, "CategoricalColorFrame", "inetsoft.graph.aesthetic.CategoricalColorFrame");
      putClassProxy(bindings, "StaticSizeFrame",       "inetsoft.graph.aesthetic.StaticSizeFrame");
      putClassProxy(bindings, "LinearSizeFrame",       "inetsoft.graph.aesthetic.LinearSizeFrame");
      putClassProxy(bindings, "CategoricalSizeFrame",  "inetsoft.graph.aesthetic.CategoricalSizeFrame");
      putClassProxy(bindings, "StaticTextureFrame",    "inetsoft.graph.aesthetic.StaticTextureFrame");
      putClassProxy(bindings, "LeftTiltTextureFrame",  "inetsoft.graph.aesthetic.LeftTiltTextureFrame");
      putClassProxy(bindings, "RGBCubeColorFrame",     "inetsoft.graph.aesthetic.RGBCubeColorFrame");
      putClassProxy(bindings, "StackTextFrame",        "inetsoft.graph.aesthetic.StackTextFrame");
      putClassProxy(bindings, "OrientationTextureFrame",  "inetsoft.graph.aesthetic.OrientationTextureFrame");
      putClassProxy(bindings, "RightTiltTextureFrame",    "inetsoft.graph.aesthetic.RightTiltTextureFrame");
      putClassProxy(bindings, "GridTextureFrame",         "inetsoft.graph.aesthetic.GridTextureFrame");
      putClassProxy(bindings, "CategoricalTextureFrame",  "inetsoft.graph.aesthetic.CategoricalTextureFrame");
      putClassProxy(bindings, "OvalShapeFrame",        "inetsoft.graph.aesthetic.OvalShapeFrame");
      putClassProxy(bindings, "FillShapeFrame",        "inetsoft.graph.aesthetic.FillShapeFrame");
      putClassProxy(bindings, "OrientationShapeFrame", "inetsoft.graph.aesthetic.OrientationShapeFrame");
      putClassProxy(bindings, "PolygonShapeFrame",     "inetsoft.graph.aesthetic.PolygonShapeFrame");
      putClassProxy(bindings, "TriangleShapeFrame",    "inetsoft.graph.aesthetic.TriangleShapeFrame");
      putClassProxy(bindings, "CategoricalShapeFrame", "inetsoft.graph.aesthetic.CategoricalShapeFrame");
      putClassProxy(bindings, "StaticShapeFrame",      "inetsoft.graph.aesthetic.StaticShapeFrame");
      putClassProxy(bindings, "VineShapeFrame",        "inetsoft.graph.aesthetic.VineShapeFrame");
      putClassProxy(bindings, "ThermoShapeFrame",      "inetsoft.graph.aesthetic.ThermoShapeFrame");
      putClassProxy(bindings, "StarShapeFrame",        "inetsoft.graph.aesthetic.StarShapeFrame");
      putClassProxy(bindings, "SunShapeFrame",         "inetsoft.graph.aesthetic.SunShapeFrame");
      putClassProxy(bindings, "BarShapeFrame",         "inetsoft.graph.aesthetic.BarShapeFrame");
      putClassProxy(bindings, "ProfileShapeFrame",     "inetsoft.graph.aesthetic.ProfileShapeFrame");
      putClassProxy(bindings, "DefaultTextFrame",      "inetsoft.graph.aesthetic.DefaultTextFrame");
      putClassProxy(bindings, "StaticLineFrame",       "inetsoft.graph.aesthetic.StaticLineFrame");
      putClassProxy(bindings, "LinearLineFrame",       "inetsoft.graph.aesthetic.LinearLineFrame");
      putClassProxy(bindings, "CategoricalLineFrame",  "inetsoft.graph.aesthetic.CategoricalLineFrame");

      // color palettes
      putClassProxy(bindings, "BluesColorFrame",    "inetsoft.graph.aesthetic.BluesColorFrame");
      putClassProxy(bindings, "BrBGColorFrame",     "inetsoft.graph.aesthetic.BrBGColorFrame");
      putClassProxy(bindings, "BuGnColorFrame",     "inetsoft.graph.aesthetic.BuGnColorFrame");
      putClassProxy(bindings, "BuPuColorFrame",     "inetsoft.graph.aesthetic.BuPuColorFrame");
      putClassProxy(bindings, "GnBuColorFrame",     "inetsoft.graph.aesthetic.GnBuColorFrame");
      putClassProxy(bindings, "GreensColorFrame",   "inetsoft.graph.aesthetic.GreensColorFrame");
      putClassProxy(bindings, "GreysColorFrame",    "inetsoft.graph.aesthetic.GreysColorFrame");
      putClassProxy(bindings, "OrangesColorFrame",  "inetsoft.graph.aesthetic.OrangesColorFrame");
      putClassProxy(bindings, "OrRdColorFrame",     "inetsoft.graph.aesthetic.OrRdColorFrame");
      putClassProxy(bindings, "PiYGColorFrame",     "inetsoft.graph.aesthetic.PiYGColorFrame");
      putClassProxy(bindings, "PRGnColorFrame",     "inetsoft.graph.aesthetic.PRGnColorFrame");
      putClassProxy(bindings, "PuBuColorFrame",     "inetsoft.graph.aesthetic.PuBuColorFrame");
      putClassProxy(bindings, "PuBuGnColorFrame",   "inetsoft.graph.aesthetic.PuBuGnColorFrame");
      putClassProxy(bindings, "PuOrColorFrame",     "inetsoft.graph.aesthetic.PuOrColorFrame");
      putClassProxy(bindings, "PuRdColorFrame",     "inetsoft.graph.aesthetic.PuRdColorFrame");
      putClassProxy(bindings, "PurplesColorFrame",  "inetsoft.graph.aesthetic.PurplesColorFrame");
      putClassProxy(bindings, "RdBuColorFrame",     "inetsoft.graph.aesthetic.RdBuColorFrame");
      putClassProxy(bindings, "RdGyColorFrame",     "inetsoft.graph.aesthetic.RdGyColorFrame");
      putClassProxy(bindings, "RdPuColorFrame",     "inetsoft.graph.aesthetic.RdPuColorFrame");
      putClassProxy(bindings, "RdYlGnColorFrame",   "inetsoft.graph.aesthetic.RdYlGnColorFrame");
      putClassProxy(bindings, "RedsColorFrame",     "inetsoft.graph.aesthetic.RedsColorFrame");
      putClassProxy(bindings, "SpectralColorFrame", "inetsoft.graph.aesthetic.SpectralColorFrame");
      putClassProxy(bindings, "RdYlBuColorFrame",   "inetsoft.graph.aesthetic.RdYlBuColorFrame");
      putClassProxy(bindings, "YlGnBuColorFrame",   "inetsoft.graph.aesthetic.YlGnBuColorFrame");
      putClassProxy(bindings, "YlGnColorFrame",     "inetsoft.graph.aesthetic.YlGnColorFrame");
      putClassProxy(bindings, "YlOrBrColorFrame",   "inetsoft.graph.aesthetic.YlOrBrColorFrame");
      putClassProxy(bindings, "YlOrRdColorFrame",   "inetsoft.graph.aesthetic.YlOrRdColorFrame");

      // data
      putClassProxy(bindings, "DefaultDataSet", "inetsoft.graph.data.DefaultDataSet");

      // schema painters
      putClassProxy(bindings, "BoxPainter",    "inetsoft.graph.schema.BoxPainter");
      putClassProxy(bindings, "CandlePainter", "inetsoft.graph.schema.CandlePainter");
      putClassProxy(bindings, "StockPainter",  "inetsoft.graph.schema.StockPainter");

      // guide
      putClassProxy(bindings, "VLabel", "inetsoft.graph.guide.VLabel");

      // forms and equations; PolynomialLineEquation covers .Linear/.Quadratic/.Cubic
      // via static nested-class access on the proxy
      putClassProxy(bindings, "DefaultForm",               "inetsoft.graph.guide.form.DefaultForm");
      putClassProxy(bindings, "ExponentialLineEquation",   "inetsoft.graph.guide.form.ExponentialLineEquation");
      putClassProxy(bindings, "LineEquation",              "inetsoft.graph.guide.form.LineEquation");
      putClassProxy(bindings, "LogarithmicLineEquation",   "inetsoft.graph.guide.form.LogarithmicLineEquation");
      putClassProxy(bindings, "PolynomialLineEquation",    "inetsoft.graph.guide.form.PolynomialLineEquation");
      putClassProxy(bindings, "PowerLineEquation",         "inetsoft.graph.guide.form.PowerLineEquation");
      putClassProxy(bindings, "LineForm",  "inetsoft.graph.guide.form.LineForm");
      putClassProxy(bindings, "RectForm",  "inetsoft.graph.guide.form.RectForm");
      putClassProxy(bindings, "LabelForm", "inetsoft.graph.guide.form.LabelForm");
      putClassProxy(bindings, "TagForm",   "inetsoft.graph.guide.form.TagForm");
      putClassProxy(bindings, "ShapeForm", "inetsoft.graph.guide.form.ShapeForm");
   }

   private static final Logger LOG = LoggerFactory.getLogger(GraalJavaScriptEngine.class);
}
