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

   // Deny-list ported verbatim from SecureClassShutter. Checked BEFORE the allow-list.
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

   // Tier 1: safe java.* utilities. Finalized by audit in Task 6.4.
   private static final Set<String> ALLOWED_CLASSES = Set.of(
      "java.lang.Math", "java.lang.String",
      "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.lang.Boolean",
      "java.text.SimpleDateFormat", "java.text.DecimalFormat",
      "java.util.Date", "java.util.Calendar", "java.util.ArrayList",
      "java.util.HashMap", "java.util.List", "java.util.Arrays"
   );

   // Tier 2: our own API package prefixes.
   private static final List<String> ALLOWED_PREFIXES = List.of(
      "inetsoft.graph.", "inetsoft.report.", "inetsoft.uql."
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
    * The deny-list (BLOCKED_PACKAGES / BLOCKED_CLASSES) is checked FIRST;
    * a class that matches the deny-list is rejected even if it would otherwise
    * satisfy the allow-list.
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
         // --- Deny-first check (FIX 1) ---
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

         // --- Allow-list check ---
         if(ALLOWED_CLASSES.contains(fqcn) || extra.contains(fqcn)) {
            return true;
         }

         for(String prefix : ALLOWED_PREFIXES) {
            if(fqcn.startsWith(prefix)) {
               return true;
            }
         }

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
