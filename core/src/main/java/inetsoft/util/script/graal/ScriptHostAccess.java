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
import inetsoft.uql.viewsheet.internal.FormUtil;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

/**
 * Single audit point for script Java interop security. Builds the HostAccess
 * member policy (annotation-driven + curated target-type mappings) and the
 * class-lookup allow-list that gates Java.type(...).
 */
public final class ScriptHostAccess {
   private ScriptHostAccess() {
   }

   // Deny-list ported verbatim from SecureClassShutter. Checked AFTER the exact
   // allow-list (ALLOWED_CLASSES) but BEFORE the operator-supplied
   // extras (script.java.allowed.classes) and the allowed prefixes — see classFilter().
   // Dangerous packages — any class whose FQCN equals or starts with "<pkg>." is blocked.
   private static final Set<String> BLOCKED_PACKAGES = Set.of(
      "java.lang.reflect",
      "java.lang.invoke",
      "java.security",
      "java.net",
      "java.io",
      "java.nio",
      "java.util.concurrent",
      "javax.script",
      "sun.",
      "com.sun.",
      "jdk.internal.",
      "java.lang.management",
      "javax.management",
      "java.rmi",
      "javax.naming",
      "java.sql",
      "javax.sql",
      "org.xml.sax",
      "javax.xml",
      "java.beans",
      "inetsoft.sree.security",
      "inetsoft.report.internal.license",
      "inetsoft.storage",
      "inetsoft.util.config",
      "inetsoft.util.health",
      "inetsoft.util.log",
      // the GraalJS engine internals themselves — the "inetsoft.util.script."
      // allow-prefix would otherwise expose GraalJavaScriptEngine /
      // ScriptTimeoutGuard / ScriptHostAccess etc. to Java.type(...)
      "inetsoft.util.script.graal",
      // GraalVM/Truffle engine internals — comOrg=true would otherwise permit
      // Java.type('org.graalvm.polyglot.Context'), enabling sandbox escape via
      // Context.create().eval(unrestricted script).
      "org.graalvm",
      "com.oracle"
   );

   // Specific dangerous classes that are blocked by exact name.
   private static final Set<String> BLOCKED_CLASSES = Set.of(
      "java.lang.System",
      "java.lang.Runtime",
      "java.lang.Process",
      "java.lang.ProcessBuilder",
      "java.lang.Class",
      "java.lang.ClassLoader",
      "java.lang.Thread",
      "java.lang.ThreadDeath",
      "java.lang.ThreadGroup",
      "java.lang.ThreadLocal",
      "java.lang.InheritableThreadLocal",
      "java.lang.SecurityManager",
      "java.lang.Package",
      "java.lang.Compiler",
      "java.util.ServiceLoader",
      "java.awt.Desktop",
      "javax.swing.JFileChooser",
      "java.io.File",
      "java.io.FileInputStream",
      "java.io.FileOutputStream",
      "java.io.FileReader",
      "java.io.FileWriter",
      "java.io.RandomAccessFile",
      "java.net.URL",
      "java.net.URLConnection",
      "java.net.HttpURLConnection",
      "java.net.Socket",
      "java.net.ServerSocket",
      "java.net.DatagramSocket",
      "java.net.MulticastSocket",
      "inetsoft.util.ThreadPool",
      "inetsoft.util.Plugins",
      "inetsoft.util.IndexStorage",
      "inetsoft.util.XMLIndexedStorage",
      "inetsoft.util.BlobIndexedStorage"
   );

   // Tier 1: curated exact-match safe classes. Finalized by audit in Task 6.4,
   // aligned with the proven SecureClassShutter baseline. Note some of these
   // live in packages that appear in BLOCKED_PACKAGES (e.g. java.sql,
   // inetsoft.sree.security) — the exact-allow check in classFilter() runs
   // BEFORE the package deny check so these specific classes still load.
   private static final Set<String> ALLOWED_CLASSES = Set.of(
      "java.lang.Math", "java.lang.String", "java.lang.Integer", "java.lang.Long",
      "java.lang.Double", "java.lang.Float", "java.lang.Boolean", "java.lang.Character",
      "java.lang.Byte", "java.lang.Short", "java.lang.Number", "java.lang.Object",
      "java.util.ArrayList", "java.util.HashMap", "java.util.HashSet", "java.util.LinkedList",
      "java.util.TreeMap", "java.util.TreeSet", "java.util.List", "java.util.Arrays",
      "java.util.Date", "java.util.Calendar", "java.util.GregorianCalendar", "java.util.TimeZone",
      "java.util.Locale", "java.util.UUID", "java.util.regex.Pattern", "java.util.regex.Matcher",
      "java.text.SimpleDateFormat", "java.text.DecimalFormat", "java.text.NumberFormat",
      "java.math.BigDecimal", "java.math.BigInteger",
      "java.sql.Date", "java.sql.Time", "java.sql.Timestamp",
      "inetsoft.sree.web.HttpServiceRequest",
      "inetsoft.sree.security.DestinationUserNameProviderPrincipal",
      "inetsoft.util.XTimestamp"
   );

   // Tier 2: our own API package prefixes.
   private static final List<String> ALLOWED_PREFIXES = List.of(
      "inetsoft.graph.", "inetsoft.report.", "inetsoft.uql.",
      "inetsoft.sree.script.", "inetsoft.util.audit.templates.",
      "inetsoft.util.script.", "inetsoft.analytic.composition.event."
   );

   // Tier 3: broad JDK package prefixes (ported from SecureClassShutter /
   // JavaScriptEngine.initScope final stage). Reached only after the basic-class
   // filters above; for java.util/java.text these catch the spi fall-throughs,
   // while java.awt admits the full package (minus exact BLOCKED_CLASSES like
   // java.awt.Desktop). This restores the main-branch Rhino allow-list that the
   // initial GraalJS cutover narrowed away (e.g. java.awt.Color). (#75423)
   private static final String[] DEFAULT_JAVA_PKGS = { "java.awt", "java.text", "java.util" };

   private static final Set<String> PRIMITIVE_ARRAY_SIGNATURES = Set.of(
      "[B", "[S", "[I", "[J", "[F", "[D", "[C", "[Z"
   );

   private static volatile HostAccess hostAccess;

   public static HostAccess hostAccess() {
      if(hostAccess == null) {
         synchronized(ScriptHostAccess.class) {
            if(hostAccess == null) {
               HostAccess.Builder builder = HostAccess.newBuilder()
                  // allow @Export-annotated instance members and all public access
                  // on class-filter-allowed types (e.g. Java.type('java.lang.Math').max)
                  .allowAccessAnnotatedBy(HostAccess.Export.class)
                  .allowPublicAccess(true)
                  .allowArrayAccess(true)
                  .allowListAccess(true)
                  .allowMapAccess(true)
                  .allowIterableAccess(true)
                  .allowIteratorAccess(true)
                  // Rhino parity: Rhino auto-adapted a JS function to a single-method
                  // Java interface, so scripts could pass a lambda where a functional
                  // interface is expected (e.g. Stream.filter(Predicate),
                  // Stream.forEach(Consumer) via graph shape scripting). GraalJS refuses
                  // this by default ("Unsupported target type"); allow a guest function
                  // to implement any @FunctionalInterface-annotated type. This is the
                  // same allowance the built-in HostAccess.ALL/EXPLICIT presets use, and
                  // it does not widen class reachability (still gated by classFilter) or
                  // permit implementing arbitrary/abstract types. (#75690)
                  .allowImplementationsAnnotatedBy(FunctionalInterface.class)
                  // FIX 2: Deny reflective escape paths even when allowPublicAccess(true) is set.
                  // denyAccess takes precedence over allowPublicAccess for the listed classes.
                  // denyAccess(Object.class, false) blocks only methods declared on Object itself
                  // (getClass, wait, notify, etc.) without affecting methods declared on subclasses.
                  // This prevents d.getClass().getClassLoader().loadClass(...) escapes.
                  .denyAccess(Object.class, false)
                  .denyAccess(Class.class)
                  .denyAccess(ClassLoader.class)
                  .denyAccess(java.lang.reflect.Method.class)
                  .denyAccess(java.lang.reflect.Field.class)
                  .denyAccess(java.lang.reflect.Constructor.class)
                  .denyAccess(java.lang.reflect.AccessibleObject.class)
                  .denyAccess(System.class)
                  .denyAccess(Runtime.class)
                  .denyAccess(Process.class)
                  .denyAccess(ProcessBuilder.class)
                  .denyAccess(Thread.class)
                  // legacy convenience: scripts pass JS numbers to Java APIs.
                  // The range check matters: Double::intValue narrows by Java
                  // cast, which CLAMPS anything past the int range to
                  // Integer.MAX_VALUE/MIN_VALUE rather than failing, so without
                  // it a whole 1e30 would silently arrive as 2147483647.
                  .targetTypeMapping(Double.class, Integer.class,
                                     d -> d != null && d == Math.floor(d) && !d.isInfinite()
                                        && fitsInInt(d),
                                     Double::intValue)
                  // Rhino parity: ToNumber(jsDate) yielded epoch millis, so scripts
                  // pass a JS Date where a numeric coordinate is expected (e.g.
                  // LabelForm.setTuple(double[]) with a date on a time axis). GraalJS
                  // refuses Date->double by default; map a Date/Instant to its epoch
                  // millis, matching TimeScale.map (Date -> getTime()). (#75423)
                  .targetTypeMapping(Instant.class, Double.class,
                                     inst -> inst != null,
                                     inst -> (double) inst.toEpochMilli())
                  // Rhino parity: a script value bound to a String parameter was
                  // coerced via ScriptRuntime.toString, so scripts pass a number or
                  // boolean where a String is declared -- e.g.
                  // XFormatInfo.setFormat(StyleConstant.NUMBER). GraalJS refuses it
                  // ("Cannot convert '3'(java.lang.Integer) to Java type
                  // 'java.lang.String': Invalid or lossy primitive coercion"), which
                  // broke a chart script on export. ScriptFunction already restores
                  // this for our own scriptable dispatch (#75693), but a call on a
                  // *raw host object* (`new inetsoft.uql.XFormatInfo` then
                  // `setFormat(...)`) goes through GraalJS invokeMember and never
                  // reaches ScriptFunction, so the same coercion is declared here and
                  // shares ScriptFunction.toStringValue so both paths agree -- notably
                  // on "3" rather than "3.0", and on not clamping a whole double
                  // outside the long range.
                  //
                  // LOWEST precedence is deliberate: it is the final pass of
                  // GraalJS overload selection, reached only once every other
                  // conversion tier has left every candidate inapplicable. So a
                  // type with both setX(int) and setX(String) still binds a whole
                  // number to the int overload several tiers earlier, and only a
                  // method whose sole candidate takes a String gets the
                  // conversion. (#76778)
                  .targetTypeMapping(Number.class, String.class,
                                     n -> n != null,
                                     ScriptFunction::toStringValue,
                                     HostAccess.TargetMappingPrecedence.LOWEST)
                  .targetTypeMapping(Boolean.class, String.class,
                                     b -> b != null,
                                     ScriptFunction::toStringValue,
                                     HostAccess.TargetMappingPrecedence.LOWEST)
                  // Rhino parity: Rhino narrowed a JS number to whatever primitive
                  // the selected overload declared, so a script could build a Java
                  // object from a computed, non-integral value. GraalJS refuses
                  // double->float and double->int as a lossy primitive coercion,
                  // which broke unchanged viewsheet scripts:
                  // `new java.awt.Color(0.5686, 0.7961, 0.2431)` -- the 0..1
                  // components Color(float,float,float) is for -- and
                  // `new java.awt.Dimension(60.96, 60.96)`, which has no
                  // non-integral overload at all. Both failed with "Invalid
                  // argument when instantiating 'java.awt.Color'/'java.awt.Dimension'".
                  // ScriptFunction.coerce already restores this for our own
                  // scriptable dispatch, but a host constructor, a call on a raw
                  // host object and a HostBeanProxy setter all go through GraalJS
                  // interop and never reach it, so the coercion is declared here.
                  //
                  // Both tiers are restricted to a finite value WITH a fractional
                  // part, so they are disjoint from the whole-number
                  // Double -> Integer mapping above and cannot change how a whole
                  // number binds.
                  //
                  // LOW for float, so it does take part in overload selection --
                  // java.awt.Color offers only (int,int,int) and
                  // (float,float,float), and without a mapping neither is
                  // applicable. LOW sits below the lossless tier, so a
                  // foo(double) overload is still chosen there and keeps full
                  // precision.
                  //
                  // Caveat worth knowing before extending this: LOW is the LOOSE
                  // tier, which is also where the default conversion to Object
                  // lives -- it is level with this mapping, not above it. So a
                  // type declaring foo(float) and foo(Object) but NO foo(double)
                  // resolves both at that tier, and float wins on specificity:
                  // a fractional argument that used to arrive at foo(Object) as
                  // a Double now arrives at foo(float). No such overload pair
                  // exists in the script-facing API today (inetsoft.graph is
                  // double-valued throughout), which is why LOW is safe here,
                  // but adding one would silently re-target it.
                  .targetTypeMapping(Double.class, Float.class,
                                     ScriptHostAccess::isFractional,
                                     Double::floatValue,
                                     HostAccess.TargetMappingPrecedence.LOW)
                  // LOWEST for the integral types -- the last pass of overload
                  // selection, so it applies only where every other conversion
                  // tier left every candidate inapplicable. It therefore cannot
                  // pull java.awt.Color onto its (int,int,int) constructor ahead
                  // of the float one, which the LOW mapping above already
                  // resolves a tier earlier; it reaches only the case where the
                  // declared parameter is the sole option, which is exactly
                  // java.awt.Dimension(int,int) (60.96 -> 60).
                  //
                  // Truncation is toward zero, matching Rhino (60.96 -> 60, not
                  // 61). Integer carries an explicit range guard so a value too
                  // large for an int fails loudly rather than silently clamping
                  // to Integer.MAX_VALUE; Long needs none, because a value with
                  // a fractional part is always below 2^52.
                  //
                  // Accepted consequence: a fractional number passed to a type
                  // declaring both an integral overload -- foo(int) or foo(long)
                  // -- and foo(String) now reports "Multiple applicable
                  // overloads" instead of quietly binding to foo(String) through
                  // the Number -> String mapping above, because both mappings
                  // land on this same final tier and neither is more specific.
                  // (A foo(float)/foo(String) pair is unaffected: float resolves
                  // a tier earlier.) Only index-or-name accessors have that
                  // shape (XNode.getChild, XSelection.getType, ...), where a
                  // fractional argument is meaningless either way.
                  //
                  // Short and Byte are deliberately omitted: every short/byte
                  // parameter in our API sits beside a double one and so is
                  // already resolved several tiers earlier, and each extra target
                  // type only widens the collision above.
                  .targetTypeMapping(Double.class, Integer.class,
                                     d -> isFractional(d) && fitsInInt(d),
                                     Double::intValue,
                                     HostAccess.TargetMappingPrecedence.LOWEST)
                  .targetTypeMapping(Double.class, Long.class,
                                     ScriptHostAccess::isFractional,
                                     Double::longValue,
                                     HostAccess.TargetMappingPrecedence.LOWEST);

               // A graph object a script holds is a HostBeanProxy (a ProxyObject),
               // which GraalJS cannot convert to its Java type on its own, so it
               // failed every hand-off whose receiver is not itself a
               // HostBeanProxy: `new StackTextFrame(elem, "Quantity")` reported
               // "Invalid argument when instantiating ... [HostBeanProxy,
               // TruffleString]", and likewise for Java.type construction, static
               // methods, instance methods of plain host objects, varargs and
               // arrays. Map a wrapper back to its target wherever a parameter is
               // declared as exactly one of the wrapped types. (#76969)
               //
               // Deliberately no mapping to Object or any other supertype: an
               // Object parameter must keep receiving GraalJS's polyglot view of
               // the wrapper, so an element stored in a Java collection comes back
               // to the script as the same wrapper (=== and bean access intact).
               for(Class<?> type : HostBeanProxy.WRAPPED_TYPES) {
                  addUnwrapMapping(builder, type);
               }

               hostAccess = builder.build();
            }
         }
      }

      return hostAccess;
   }

   /**
    * Returns a predicate that gates Java.type(...) class lookups (and the
    * compatibility-shim package-root navigation, which resolves leaf classes
    * through the same filter).
    *
    * <p>Delegates to {@link #isVisibleToScripts}, a faithful port of the proven
    * {@code SecureClassShutter.visibleToScripts} precedence from the main-branch
    * Rhino baseline. Reads the {@code script.java.allowed.classes},
    * {@code javascript.java.packages} and {@code javascript.java.com_org}
    * properties once when the filter is built. The extra class names given by
    * {@code script.java.allowed.classes} are additive only: they are consulted
    * after the deny lists, so they cannot re-enable a class named by
    * {@code BLOCKED_PACKAGES} or {@code BLOCKED_CLASSES}.
    * Dangerous classes (System/Runtime/Class/ClassLoader, threading,
    * inetsoft.report.internal.license.*, engine internals) are NOT on any allow
    * path, so they stay blocked.
    */
   public static Predicate<String> classFilter() {
      // optional SreeEnv extension (off by default): comma-separated extra FQCNs
      String extraProp = null;

      try {
         extraProp = SreeEnv.getProperty("script.java.allowed.classes");
      }
      catch(Exception ignore) {
         // SreeEnv may be unavailable outside of a full server context
      }

      final Set<String> extra = parseExtra(extraProp);

      // custom package whitelist + com/org toggle, read once when the filter is
      // built (mirrors JavaScriptEngine.initScope / SecureClassShutter).
      String[] customPkgs;
      boolean comOrg;

      try {
         String customPkgProp = SreeEnv.getProperty("javascript.java.packages", "");
         customPkgs = customPkgProp.isEmpty() ? new String[0] : customPkgProp.split(",");
         comOrg = !"false".equals(SreeEnv.getProperty("javascript.java.com_org", "true"));
      }
      catch(Exception ignore) {
         customPkgs = new String[0];
         comOrg = true;
      }

      final String[] customPkgsF = customPkgs;
      final boolean comOrgF = comOrg;

      return classFilter(extra, customPkgsF, comOrgF);
   }

   /**
    * Package-private overload taking the already-parsed property values, so tests
    * can exercise the allow/deny precedence without a SreeEnv context.
    */
   static Predicate<String> classFilter(Set<String> extra, String[] customPkgs,
                                        boolean comOrg)
   {
      return fqcn -> isVisibleToScripts(fqcn, extra, customPkgs, comOrg);
   }

   /**
    * Faithful port of the proven {@code SecureClassShutter.visibleToScripts}
    * precedence (the main-branch Rhino baseline). Restores the broad package
    * allow-list (java.awt/text/util, com/org, custom packages, java.sql under a
    * FORM license, the basic java.lang/util/math/text class families) that the
    * initial GraalJS cutover narrowed away — e.g. {@code java.awt.Color}. The
    * dangerous deny-lists (threading, reflection, IO, net, process, engine
    * internals) are unchanged, so the sandbox boundary is preserved. (#75423)
    */
   private static boolean isVisibleToScripts(String fqcn, Set<String> extra,
                                             String[] customPkgs, boolean comOrg)
   {
      if(fqcn == null || fqcn.isEmpty()) {
         return false;
      }

      // java.sql is permitted only when form (data write-back) is enabled (matches main).
      if(fqcn.startsWith("java.sql") && isFormEnabled()) {
         return true;
      }

      // exact allow wins: curated classes (audited, and deliberately allowed to
      // override the package deny), primitive arrays, jdk proxies.
      if(ALLOWED_CLASSES.contains(fqcn) ||
         isPrimitiveArrayType(fqcn) || fqcn.startsWith("jdk.proxy"))
      {
         return true;
      }

      // deny: blocked packages, then blocked classes. Checked BEFORE the
      // operator-supplied extras so script.java.allowed.classes cannot re-enable
      // a class the sandbox blocks.
      for(String blockedPkg : BLOCKED_PACKAGES) {
         // Match exact package ("java.io") or sub-package/class ("java.io.File").
         // The "sun." entry already contains a trailing dot, so handle both styles.
         if(fqcn.equals(blockedPkg) ||
            fqcn.startsWith(blockedPkg.endsWith(".") ? blockedPkg : blockedPkg + "."))
         {
            return false;
         }
      }

      if(BLOCKED_CLASSES.contains(fqcn)) {
         return false;
      }

      // operator-supplied extras (script.java.allowed.classes): additive only, so
      // this runs after the deny checks above and cannot cross them.
      if(extra.contains(fqcn)) {
         return true;
      }

      // arrays of an allowed object type.
      if(isAllowedObjectArray(fqcn, extra, customPkgs, comOrg)) {
         return true;
      }

      // basic JDK class families (restrictive whitelists, ported from main).
      if(fqcn.startsWith("java.lang.") && !fqcn.contains("$")) {
         return isBasicJavaLangClass(fqcn);
      }

      if(fqcn.startsWith("java.util.") && !fqcn.contains("concurrent")) {
         return isBasicUtilClass(fqcn);
      }

      if(fqcn.startsWith("java.math.")) {
         return isBasicMathClass(fqcn);
      }

      if(fqcn.startsWith("java.text.")) {
         if(fqcn.contains("spi")) {
            return false;
         }
         return isBasicTextClass(fqcn);
      }

      // our own API prefixes.
      for(String prefix : ALLOWED_PREFIXES) {
         if(fqcn.startsWith(prefix)) {
            return true;
         }
      }

      // broad package whitelist: java.awt/text/util + custom packages + com/org.
      for(String pkg : DEFAULT_JAVA_PKGS) {
         if(fqcn.startsWith(pkg)) {
            return true;
         }
      }

      for(String pkg : customPkgs) {
         String t = pkg.trim();

         if(!t.isEmpty() && fqcn.startsWith(t)) {
            return true;
         }
      }

      if(comOrg && (fqcn.startsWith("com.") || fqcn.startsWith("org."))) {
         return true;
      }

      // default deny.
      return false;
   }

   private static boolean isFormEnabled() {
      try {
         return FormUtil.isFormEnabled();
      }
      catch(Throwable ignore) {
         // SreeEnv may be unavailable outside a full server context (tests).
         return false;
      }
   }

   private static boolean isPrimitiveArrayType(String className) {
      if(PRIMITIVE_ARRAY_SIGNATURES.contains(className)) {
         return true;
      }

      int dimension = 0;

      while(dimension < className.length() && className.charAt(dimension) == '[') {
         dimension++;
      }

      // multidimensional primitive array (e.g. "[[I").
      if(dimension > 0 && className.length() == dimension + 1) {
         return PRIMITIVE_ARRAY_SIGNATURES.contains(className.substring(dimension));
      }

      return false;
   }

   private static boolean isAllowedObjectArray(String className, Set<String> extra,
                                               String[] customPkgs, boolean comOrg)
   {
      String componentType = className;
      boolean objArray = false;

      while(componentType.startsWith("[")) {
         if(componentType.startsWith("[L")) {
            componentType = componentType.substring(2);

            if(!componentType.endsWith(";")) {
               break;
            }

            objArray = true;
            componentType = componentType.substring(0, componentType.length() - 1);

            return objArray && isVisibleToScripts(componentType, extra, customPkgs, comOrg);
         }

         componentType = componentType.substring(1);
      }

      return false;
   }

   private static boolean isBasicJavaLangClass(String className) {
      return className.matches("java\\.lang\\.(String|Integer|Long|Double|Float|Boolean|Character|Byte|Short|Number|Object|Math|StrictMath|StringBuilder|StringBuffer|Enum|Comparable|Iterable|CharSequence|Appendable|Readable|AutoCloseable|Exception|RuntimeException|Error|Throwable)");
   }

   private static boolean isBasicUtilClass(String className) {
      return !className.contains("concurrent") &&
         !className.contains("spi") &&
         !className.contains("logging") &&
         !className.contains("prefs") &&
         !className.contains("jar") &&
         !className.contains("zip") &&
         !className.contains("ServiceLoader");
   }

   private static boolean isBasicMathClass(String className) {
      return className.matches("java\\.math\\.(BigDecimal|BigInteger|MathContext|RoundingMode)");
   }

   private static boolean isBasicTextClass(String className) {
      return className.matches("java\\.text\\.(DateFormat|SimpleDateFormat|NumberFormat|DecimalFormat|MessageFormat|FieldPosition|ParsePosition|Format|Collator|BreakIterator|Normalizer|AttributedString|AttributedCharacterIterator)");
   }

   /**
    * Whether a script number needs the Rhino-parity narrowing declared in
    * {@link #hostAccess()}: a finite value that has a fractional part. A whole
    * number is excluded because the existing {@code Double -> Integer} mapping
    * already handles it, and NaN/infinity are excluded deliberately -- narrowing
    * either to {@code 0} would silently hide a broken formula, so they keep
    * failing the call as they do now.
    */
   private static boolean isFractional(Double d) {
      return d != null && !d.isNaN() && !d.isInfinite() && d != Math.floor(d);
   }

   /**
    * Whether a double is inside the {@code int} range, so {@link Double#intValue}
    * narrows it rather than clamping it. A Java cast from a double past the int
    * range yields {@code Integer.MAX_VALUE}/{@code MIN_VALUE} silently, which is
    * exactly the kind of quiet corruption these mappings exist to avoid -- an
    * out-of-range value must fail the call instead.
    *
    * <p>At the positive edge this rejects a hair more than it strictly must: a
    * fractional {@code 2147483647.5} would truncate to a valid
    * {@code 2147483647}. The error is in the safe direction (a loud failure, not
    * a wrong number), so the bound is left simple.
    */
   private static boolean fitsInInt(double d) {
      return d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE;
   }

   /**
    * Declares the conversion of a {@link HostBeanProxy} wrapper back to its target
    * for a parameter declared as exactly {@code type}. The predicate also checks
    * the target's type, so e.g. a wrapped {@code EGraph} is never offered to a
    * {@code GraphElement} parameter. (#76969)
    */
   private static <T> void addUnwrapMapping(HostAccess.Builder builder, Class<T> type) {
      builder.targetTypeMapping(Value.class, type,
                                v -> type.isInstance(HostBeanProxy.unwrap(v)),
                                v -> type.cast(HostBeanProxy.unwrap(v)));
   }

   private static Set<String> parseExtra(String prop) {
      if(prop == null || prop.isBlank()) {
         return Set.of();
      }

      Set<String> s = new HashSet<>();

      for(String part : prop.split(",")) {
         String t = part.trim();

         if(!t.isEmpty()) {
            s.add(t);
         }
      }

      return s;
   }
}
