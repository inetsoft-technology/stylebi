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
import java.util.LinkedHashSet;
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

   // The CALC function scope (case-insensitive member lookup). Installed as the
   // __scope__ proxy's case-insensitive last-resort so unqualified CALC/statistical
   // functions resolve regardless of case, matching Rhino (see installGlobalScope,
   // ensureScopeProxy). (#75685)
   private ScriptScope calcScope;

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
            bindings.putMember(e.getKey(), ScriptValueConverter.toGuest(e.getValue()));
         }
      }

      // legacy Rhino-interop shim (package roots, importClass/importPackage).
      // Installed before library functions so their bodies can navigate package
      // roots; gated live by the javascript.legacy.compatibility property.
      LegacyJavaShim.install(context, context.getBindings("js"), classFilter);

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

         // Rhino set the Calc scope as the global scope's prototype
         // (globalscope.setPrototype(new Calc())), and Calc's member lookup is
         // case-insensitive (funcmap.get(id.toLowerCase())). So unqualified
         // CALC/statistical functions resolved regardless of case, e.g.
         // NthMostFrequent, PthPercentile, Sum. GraalJS global bindings are
         // case-sensitive, so the lowercase copies above only match exact-case
         // names. Expose the Calc scope to the __scope__ proxy so a name with no
         // exact global binding (JS builtins and the lowercase copies above
         // still win) resolves case-insensitively as a last resort. (#75685)
         calcScope = calc;

         if(scopeProxy != null) {
            scopeProxy.setBuiltinScope(calc);
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
         installMapTypeConstants(scope);
         bindings.putMember(name, ScriptValueConverter.toGuest(scope));
      }
      catch(Throwable ex) {
         LOG.warn("Failed to install constant object " + name, ex);
      }
   }

   /**
    * Register the dynamic {@code MAP_TYPE_<TYPE>} constants (e.g.
    * {@code MAP_TYPE_U.S.} = "U.S.") derived from the installed map data. These
    * are not {@code public static final} fields, so the reflected
    * {@link ConstantScope} does not pick them up; Rhino added them explicitly to
    * both the {@code Chart} and {@code StyleConstant} scopes, so restore them
    * here to keep {@code mapType = Chart["MAP_TYPE_U.S."]} working. (#75679)
    */
   private void installMapTypeConstants(ConstantScope scope) {
      try {
         for(String type : inetsoft.report.internal.graph.MapData.getMapTypes()) {
            scope.putConstant("MAP_TYPE_" + type.toUpperCase(), type);
         }
      }
      catch(Throwable ex) {
         LOG.warn("Failed to install map type constants", ex);
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
               // Strip "use strict" directives — strict mode forbids with statements,
               // so the wrapper would cause a SyntaxError and the function would be
               // silently dropped.
               String wrapped = stripStrictDirectives(source);
               context.eval(Source.newBuilder(
                  "js", "with(__scope__){\n" + wrapped + "\n}", "<lib:" + name + ">")
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
      String body = stripStrictDirectives(cmd);

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
      // without the declaration hoist). Block-vs-object completion semantics
      // (e.g. a bare `{a:1}`) are identical to the eval form because the body is
      // still evaluated in statement position, not wrapped in `return (...)`.
      if(!THIS_REF.matcher(body).find()) {
         return Source.newBuilder("js", "with(__scope__){\n" + body + "\n}", "<cmd>")
            .buildLiteral();
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
      // eval) are guarded by `typeof` and simply skipped.
      //
      // A top-level `return` in the script body is not a case this needs to
      // handle: GraalJS rejects it with a SyntaxError ("Invalid return
      // statement") at eval time, unlike V8/SpiderMonkey/Rhino which permit
      // `return` inside a direct eval nested in a function. So the hoist can
      // never be skipped by an early return here — the eval throws before the
      // hoist statement would matter either way, and that throw is pre-existing
      // behavior unrelated to this fix.
      String hoist = buildDeclarationHoist(body);
      return Source.newBuilder("js",
         "(function(){with(__scope__){var " + RESULT_VAR + "=eval(" + toJsStringLiteral(body) +
            ");" + hoist + "return " + RESULT_VAR + ";}}).call(__scope__)", "<cmd>")
         .buildLiteral();
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

            // Do not retain the PolyglotException as the cause: it is not
            // serializable (PolyglotException.writeObject throws), which would
            // mask the real script error when this exception is marshalled
            // across the cluster (e.g. an Ignite affinity-call response). The
            // message already carries the JS error text and line; copy the
            // merged host/guest stack trace so nothing useful is lost. (#75555)
            ScriptException se = new ScriptException(ex.getMessage() + loc);
            se.setStackTrace(ex.getStackTrace());
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
         scopeProxy.setBuiltinScope(calcScope);
         context.getBindings("js").putMember("__scope__", scopeProxy);
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
         // leading comments are inert, so discarding them is harmless).
         s = s.substring(end);
      }

      return s;
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
    */
   private static String buildDeclarationHoist(String body) {
      Set<String> names = collectTopLevelDeclarations(body);

      if(names.isEmpty()) {
         return "";
      }

      StringBuilder sb = new StringBuilder();

      for(String name : names) {
         sb.append("try{if(typeof ").append(name).append("!==\"undefined\"){globalThis[")
            .append(toJsStringLiteral(name)).append("]=").append(name)
            .append(";}}catch(").append(HOIST_ERR_VAR).append("){}");
      }

      return sb.toString();
   }

   /**
    * Scan {@code body} for identifiers introduced by {@code var} and
    * {@code function} declarations. This is a deliberately lightweight lexical
    * scan (not a full parser): it strips strings/comments first, then collects
    * names following {@code var}/{@code function} tokens. It may over-collect
    * (e.g. names declared inside a nested function) — those are harmless because
    * the emitted copy is {@code typeof}-guarded — and it does not attempt to
    * handle destructuring patterns. Only {@code var}/{@code function} are
    * considered, because {@code let}/{@code const} at an eval's top level are
    * confined to the eval and never persist anyway.
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
         !name.equals(RESULT_VAR) && !name.equals(HOIST_ERR_VAR))
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
    * typeof-guarded) rather than dropping one.
    */
   private static String stripStringsAndComments(String s) {
      int n = s.length();
      StringBuilder sb = new StringBuilder(n);
      // Code-brace depth at which each open template substitution (`${`) began;
      // the matching `}` at that depth resumes template scanning.
      java.util.Deque<Integer> templateStack = new java.util.ArrayDeque<>();
      int braceDepth = 0;
      char prevSig = 0;   // previous significant code char (regex/division hint)
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
         if(c == '/' && regexAllowed(s, i, prevSig)) {
            int end = scanRegexEnd(s, i);

            if(end > 0) {
               for(int k = i; k < end; k++) {
                  sb.append(isLineBreak(s.charAt(k)) ? s.charAt(k) : ' ');
               }

               i = end;
               prevSig = ')';   // a regex literal ends an expression (division next)
               continue;
            }
         }

         // single/double-quoted string
         if(c == '"' || c == '\'') {
            char quote = c;
            sb.append(' ');
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
            continue;
         }

         // template-literal start
         if(c == '`') {
            sb.append(' ');
            i = scanTemplateBody(s, i + 1, sb, braceDepth, templateStack);
            prevSig = ')';
            continue;
         }

         // '}' that closes an open template substitution -> resume the template
         if(c == '}' && !templateStack.isEmpty() && braceDepth == templateStack.peek()) {
            templateStack.pop();
            sb.append(' ');
            i = scanTemplateBody(s, i + 1, sb, braceDepth, templateStack);
            prevSig = ')';
            continue;
         }

         if(c == '{') {
            braceDepth++;
         }
         else if(c == '}' && braceDepth > 0) {
            braceDepth--;
         }

         sb.append(c);

         if(!Character.isWhitespace(c)) {
            prevSig = c;
         }

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

      if(isIdentPart(prevSig)) {
         // end of an identifier/number: a regex only follows a keyword
         return REGEX_PRECEDING_KEYWORDS.contains(precedingWord(s, slashIndex));
      }

      // a value-ender (), ], and — via prevSig=')' — a prior string/regex/template)
      // means the '/' is division; anything else is a regex position.
      return prevSig != ')' && prevSig != ']';
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
