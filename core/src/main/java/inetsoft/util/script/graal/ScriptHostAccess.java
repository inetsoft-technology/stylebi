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
import org.graalvm.polyglot.HostAccess;
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
      "inetsoft.util.log"
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
                  .build();
            }
         }
      }

      return hostAccess;
   }

   /**
    * Returns a predicate that gates Java.type(...) class lookups.
    *
    * <p>Precedence (Task 6.4, ported from the proven SecureClassShutter baseline):
    * <ol>
    *   <li><b>exact allow wins</b> — a class in ALLOWED_CLASSES (or the SreeEnv
    *       extension) is permitted even if its package is in BLOCKED_PACKAGES
    *       (e.g. java.sql.Date, inetsoft.sree.security.DestinationUserNameProviderPrincipal);</li>
    *   <li><b>deny</b> — BLOCKED_CLASSES exact match, then BLOCKED_PACKAGES prefix match;</li>
    *   <li><b>allowed prefixes</b> — ALLOWED_PREFIXES match;</li>
    *   <li><b>default deny</b>.</li>
    * </ol>
    * Dangerous classes (System/Runtime/Class/ClassLoader, inetsoft.report.internal.license.*)
    * are NOT in the exact allow-list, so they fall through to the deny step and stay blocked.
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
      Set<String> extra = parseExtra(extraProp);

      return fqcn -> {
         // --- 1. exact allow wins (Task 6.4 precedence) ---
         // A curated exact safe class is permitted even if its package is denied.
         if(ALLOWED_CLASSES.contains(fqcn) || extra.contains(fqcn)) {
            return true;
         }

         // --- 2. deny: exact blocked classes, then blocked packages ---
         if(BLOCKED_CLASSES.contains(fqcn)) {
            return false;
         }

         for(String blockedPkg : BLOCKED_PACKAGES) {
            // Match exact package ("java.io") or sub-package/class ("java.io.File").
            // The "sun." entry already contains a trailing dot, so handle both styles.
            if(fqcn.equals(blockedPkg) ||
               fqcn.startsWith(blockedPkg.endsWith(".") ? blockedPkg : blockedPkg + "."))
            {
               return false;
            }
         }

         // --- 3. allowed prefixes ---
         for(String prefix : ALLOWED_PREFIXES) {
            if(fqcn.startsWith(prefix)) {
               return true;
            }
         }

         // --- 4. default deny ---
         return false;
      };
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
