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

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import org.graalvm.polyglot.HostAccess;
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
   // allow-list (ALLOWED_CLASSES) but BEFORE the allowed prefixes — see classFilter().
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
      "inetsoft.util.script.graal"
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
               hostAccess = HostAccess.newBuilder()
                  // allow @Export-annotated instance members and all public access
                  // on class-filter-allowed types (e.g. Java.type('java.lang.Math').max)
                  .allowAccessAnnotatedBy(HostAccess.Export.class)
                  .allowPublicAccess(true)
                  .allowArrayAccess(true)
                  .allowListAccess(true)
                  .allowMapAccess(true)
                  .allowIterableAccess(true)
                  .allowIteratorAccess(true)
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
                  // legacy convenience: scripts pass JS numbers to Java APIs
                  .targetTypeMapping(Double.class, Integer.class,
                                     d -> d != null && d == Math.floor(d) && !d.isInfinite(),
                                     Double::intValue)
                  // Rhino parity: ToNumber(jsDate) yielded epoch millis, so scripts
                  // pass a JS Date where a numeric coordinate is expected (e.g.
                  // LabelForm.setTuple(double[]) with a date on a time axis). GraalJS
                  // refuses Date->double by default; map a Date/Instant to its epoch
                  // millis, matching TimeScale.map (Date -> getTime()). (#75423)
                  .targetTypeMapping(Instant.class, Double.class,
                                     inst -> inst != null,
                                     inst -> (double) inst.toEpochMilli())
                  .build();
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
    * Rhino baseline. Reads the {@code javascript.java.packages} and
    * {@code javascript.java.com_org} properties once when the filter is built.
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

      return fqcn -> isVisibleToScripts(fqcn, extra, customPkgsF, comOrgF);
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

      // java.sql is permitted only when the FORM component is licensed (matches main).
      if(fqcn.startsWith("java.sql") && isFormLicensed()) {
         return true;
      }

      // exact allow wins: curated classes, SreeEnv extras, primitive arrays, jdk proxies.
      if(ALLOWED_CLASSES.contains(fqcn) || extra.contains(fqcn) ||
         isPrimitiveArrayType(fqcn) || fqcn.startsWith("jdk.proxy"))
      {
         return true;
      }

      // deny: blocked packages, then blocked classes.
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

      if(fqcn.startsWith("java.text.") && !fqcn.contains("spi")) {
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

   private static boolean isFormLicensed() {
      try {
         return LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM);
      }
      catch(Throwable ignore) {
         // LicenseManager may be unavailable outside a full server context (tests).
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
