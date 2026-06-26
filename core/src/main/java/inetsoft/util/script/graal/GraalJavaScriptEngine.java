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
import inetsoft.util.script.ScriptException;
import org.graalvm.polyglot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;

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
   protected final ReentrantLock lock = new ReentrantLock();
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

      try {
         if(context != null) {
            context.close(true);
         }

         classFilter = ScriptHostAccess.classFilter();
         scopeProxy = null; // rebound against the new context on next exec

         context = Context.newBuilder("js")
            .engine(SHARED_ENGINE)
            .allowHostAccess(ScriptHostAccess.hostAccess())
            .allowHostClassLookup(classFilter)
            .allowIO(false)
            .allowCreateThread(false)
            .allowNativeAccess(false)
            .allowCreateProcess(false)
            .allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess.NONE)
            .build();

         // FIX B: reset per-Source error counts on (re)init
         errorCounts.clear();

         initScope(vars);
      }
      finally {
         lock.unlock();
      }
   }

   /** Install engine globals. Overridden/extended by report + viewsheet layers. */
   protected void initScope(Map<String, Object> vars) {
      // engine globals (CALC, StyleConstant, XType, Chart, importExisting vars)
      // are installed here by subclasses / wiring tasks. Base impl publishes
      // the supplied vars.
      if(vars != null) {
         Value bindings = context.getBindings("js");

         for(Map.Entry<String, Object> e : vars.entrySet()) {
            bindings.putMember(e.getKey(), e.getValue());
         }
      }

      // legacy Rhino-interop shim (package roots, importClass/importPackage).
      // Installed before library functions so their bodies can navigate package
      // roots; gated live by the javascript.legacy.compatibility property.
      LegacyJavaShim.install(context, context.getBindings("js"), classFilter);

      installGlobalFunctions();
      installGlobalScope();
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
         LOG.warn("Failed to install setupGoogleMapsPlot", ex);
      }

      // (b) FormulaFunctions: every public static method declared on the class.
      // These intentionally overwrite any (a)-group registration with the same name —
      // FormulaFunctions implementations take priority over the JavaScriptEngine equivalents.
      try {
         addStaticFunctions(bindings, inetsoft.report.script.formula.FormulaFunctions.class);
      }
      catch(Throwable ex) {
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
         inetsoft.util.script.Calc calc = new inetsoft.util.script.Calc();
         bindings.putMember("CALC", ScriptValueConverter.toGuest(calc));

         for(Object key : calc.getMemberKeys()) {
            String name = String.valueOf(key);

            // don't overwrite an explicit (a)/(b) registration on collision
            if(!bindings.hasMember(name)) {
               bindings.putMember(name, calc.getMember(name));
            }
         }
      }
      catch(Throwable ex) {
         LOG.warn("Failed to install CALC functions", ex);
      }

      // (d) constant-holder objects (public static final fields reflected into scopes)
      Class<?>[] chartcls = {
         inetsoft.uql.viewsheet.graph.GraphTypes.class,
         inetsoft.report.composition.region.ChartConstants.class,
         inetsoft.uql.viewsheet.graph.GeographicOption.class
      };

      putConstantScope(bindings, "Chart", chartcls);
      putConstantScope(bindings, "GLine", inetsoft.graph.aesthetic.GLine.class);
      putConstantScope(bindings, "GTexture", inetsoft.graph.aesthetic.GTexture.class);
      putConstantScope(bindings, "GShape", inetsoft.graph.aesthetic.GShape.class);
      putConstantScope(bindings, "SVGShape", inetsoft.graph.aesthetic.SVGShape.class);

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
      bindings.putMember(jsName, new ScriptFunction(null, cls, method, params));
   }

   /**
    * Register every {@code public static} method declared directly on {@code cls}
    * as a callable JS global keyed by its method name. Mirrors the Rhino
    * {@code addFunctions(Class, Scriptable)} enumeration.
    */
   private static void addStaticFunctions(Value bindings, Class<?> cls) {
      for(java.lang.reflect.Method m : cls.getMethods()) {
         if(m.getDeclaringClass() != cls ||
            !java.lang.reflect.Modifier.isStatic(m.getModifiers()) ||
            !java.lang.reflect.Modifier.isPublic(m.getModifiers()))
         {
            continue;
         }

         bindings.putMember(m.getName(), new ScriptFunction(null, m));
      }
   }

   /**
    * Build a read-only constant scope from the given classes' public static final
    * fields and install it under {@code name}. Guarded per-group so a class-load
    * failure doesn't abort engine init.
    */
   private void putConstantScope(Value bindings, String name, Class<?>... classes) {
      try {
         ConstantScope scope = new ConstantScope(classes);
         bindings.putMember(name, ScriptValueConverter.toGuest(scope));
      }
      catch(Throwable ex) {
         LOG.warn("Failed to install constant object " + name, ex);
      }
   }

   /**
    * Install user-defined library script functions as callable JS globals.
    *
    * <p>Each library function is stored as a full JS function declaration (e.g.
    * {@code function myFunc(a, b) { return a + b; }}). Evaluating that
    * declaration at the top level of the Context defines {@code myFunc} as a
    * global, so any subsequently compiled script/formula can call it by name.
    * This replaces the Rhino {@code cx.compileFunction(globalscope, ...)}
    * machinery.
    *
    * <p>A malformed library function must not abort engine init, so each
    * function is compiled in its own try/catch (mirroring the old Rhino
    * per-function guard). The whole step is guarded against {@code LibManager}
    * being unavailable in minimal/test contexts.
    */
   private void installLibraryFunctions() {
      try {
         inetsoft.report.LibManager mgr =
            inetsoft.report.LibManagerProvider.getInstance().getManager();
         java.util.Enumeration<?> names = mgr.getScripts();

         while(names.hasMoreElements()) {
            String name = (String) names.nextElement();
            String source = mgr.getScript(name);

            if(source == null || source.isEmpty()) {
               continue;
            }

            try {
               context.eval(Source.newBuilder("js", source, "<lib:" + name + ">")
                              .buildLiteral());
            }
            catch(PolyglotException ex) {
               // don't let one bad library function break engine init
               LOG.warn("Failed to compile library function " + name, ex);
            }
         }
      }
      catch(Throwable ex) {
         // LibManager/provider unavailable (e.g. minimal/test contexts) — skip
         LOG.debug("Library functions not installed; LibManager unavailable", ex);
      }
   }

   public Object compile(String cmd) throws Exception {
      return compile(cmd, false);
   }

   public Object compile(String cmd, boolean fieldOnly) throws Exception {
      // store raw text; the with-wrap is applied per-exec against the live scope
      return Source.newBuilder("js", "with(__scope__){\n" + cmd + "\n}", "<cmd>")
         .buildLiteral();
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

   public Object exec(Object script, Object scope, Object rscope) throws Exception {
      lock.lock();

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

         // mark this thread as inside script execution (drives isScriptThread()
         // / getExecScriptable(), e.g. PropertiesEngine's env-modification guard).
         // Scoped tightly to the eval and popped in finally so pooled threads are
         // never left flagged. Use a non-null scope so the flag is reliably set.
         inetsoft.util.script.JavaScriptEngine.pushExecScriptable(rootScope);

         Duration timeout = currentTimeout();

         try(ScriptTimeoutGuard.Guard ignored = timeoutGuard.guard(context, timeout)) {
            // FIX B: per-Source error count check (read limit while holding lock)
            int limit = maxErrors();

            if(limit > 0 && errorCounts.getOrDefault(script, 0) >= limit) {
               return null;
            }

            Value result = context.eval((Source) script);
            return ScriptValueConverter.toHost(result);
         }
         catch(PolyglotException ex) {
            // FIX B: increment per-Source error count and warn when limit first crossed
            int limit = maxErrors();

            if(limit > 0) {
               int prev = errorCounts.getOrDefault(script, 0);
               int next = prev + 1;
               errorCounts.put(script, next);

               if(next == limit) {
                  LOG.warn("Script max errors exceeded ({})", limit);
               }
            }

            String loc = "";

            if(ex.getSourceLocation() != null) {
               loc = " (line " + ex.getSourceLocation().getStartLine() + ")";
            }

            throw new ScriptException(ex.getMessage() + loc, ex);
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
         }
      }
      finally {
         lock.unlock();
      }
   }

   /** Lazily create the reusable __scope__ proxy and bind it once. Caller holds lock. */
   private void ensureScopeProxy() {
      if(scopeProxy == null) {
         scopeProxy = new BindingRootProxy(EMPTY_SCOPE,
                                           inetsoft.util.script.FormulaContext::getExecScriptScope,
                                           classFilter, context);
         context.getBindings("js").putMember("__scope__", scopeProxy);
      }
   }

   /**
    * Read the script.max.errors limit from SreeEnv. Returns 0 to mean "no limit".
    * Must be called while holding {@code lock} (result used inline in exec).
    */
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
            context.getBindings("js").putMember(name, value);
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
      }
      finally {
         lock.unlock();
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(GraalJavaScriptEngine.class);
}
