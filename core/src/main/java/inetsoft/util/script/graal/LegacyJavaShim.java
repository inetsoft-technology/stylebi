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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Rhino-compatibility shim that restores the legacy Java-interop syntax under
 * GraalJS: package-root navigation ({@code java.awt.Color}, {@code inetsoft.uql...}),
 * constructor calls without {@code new} ({@code java.awt.Color(0xaed581)}),
 * static member access, and {@code importClass}/{@code importPackage}.
 *
 * <p>Every leaf class resolution is gated by the same {@link ScriptHostAccess}
 * allow-list that governs {@code Java.type(...)}, so the shim widens the interop
 * <em>syntax</em> surface, never the reachable-class set.
 *
 * <p>The whole shim is controlled by the live {@value #GATE_PROPERTY} property
 * (default on). It is read from {@link SreeEnv} on every entry, so toggling it
 * takes effect without a restart. When off, package roots and the import
 * functions throw a clear error and scripts must use {@code Java.type(...)}.
 *
 * <p>Qualified access and {@code importClass} work in any script. {@code
 * importPackage}'s <em>unqualified</em> resolution applies to scripts run under
 * {@code with(__scope__)} (viewsheet/worksheet/VPM/schedule/formula); inside a
 * library-function body only qualified access and {@code importClass} apply,
 * matching Rhino's lexical-scope behavior. (#75423)
 */
public final class LegacyJavaShim {
   public static final String GATE_PROPERTY = "javascript.legacy.compatibility";

   /** Package roots exposed as globals (Packages == the empty-prefix root). */
   private static final String[] ROOTS = { "java", "javax", "inetsoft", "com", "org" };

   private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

   /** JS-internal member names that must never resolve to a package/class. */
   private static final Set<String> JS_META = Set.of(
      "then", "toString", "valueOf", "constructor", "prototype",
      "__proto__", "hasOwnProperty", "length", "name", "call", "apply"
   );

   /** Context-independent cache of class lookups (Class is context-agnostic). */
   private static final Map<String, Optional<Class<?>>> CLASS_CACHE = new ConcurrentHashMap<>();

   /**
    * Bounded LRU cache of names that did <em>not</em> resolve to a class. Package
    * navigation probes many non-class names (e.g. {@code java}, {@code awt}, bare
    * identifiers), and each failed {@link Class#forName} scans the entire classpath
    * (every JAR) before throwing {@code ClassNotFoundException} -- the most expensive
    * classloading outcome. Remembering misses collapses repeat probes to a single
    * lookup; the LRU bound keeps the set from growing without limit under the
    * exploratory navigation the positive cache alone can't guard against.
    */
   private static final int NEGATIVE_CACHE_MAX = 10_000;
   private static final Map<String, Boolean> NEGATIVE_CACHE =
      Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
         @Override
         protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > NEGATIVE_CACHE_MAX;
         }
      });

   private LegacyJavaShim() {
   }

   /** Live read of the shim gate. Default on; off only when explicitly "false". */
   public static boolean isEnabled() {
      try {
         return !"false".equalsIgnoreCase(SreeEnv.getProperty(GATE_PROPERTY, "true"));
      }
      catch(Throwable ignore) {
         // SreeEnv may be unavailable outside a full server context (tests).
         return true;
      }
   }

   private static IllegalStateException disabled() {
      return new IllegalStateException(
         "Legacy Java compatibility shim is disabled (" + GATE_PROPERTY +
         "=false); use Java.type(...) for Java interop.");
   }

   /**
    * Install the shim globals into the engine bindings. Always installed (the
    * gate is enforced lazily per access), so toggling the property is live.
    * The {@code context} is captured so leaf classes can be resolved via
    * {@code Java.type} and the current {@code __scope__} located, without relying
    * on {@code Context.getCurrent()}.
    */
   public static void install(Context context, Value bindings, Predicate<String> filter) {
      for(String root : ROOTS) {
         bindings.putMember(root, new JavaPackageProxy(root, filter, context));
      }

      // Rhino's Packages root: Packages.com.foo.Bar navigates from the top.
      bindings.putMember("Packages", new JavaPackageProxy("", filter, context));

      bindings.putMember("importPackage", (ProxyExecutable) args -> {
         if(!isEnabled()) {
            throw disabled();
         }

         BindingRootProxy root = currentRoot(context);
         String prefix = args.length > 0 ? packagePrefixOf(args[0]) : null;

         if(root != null && prefix != null && !prefix.isEmpty()) {
            root.imports().addPackage(prefix);
         }

         return null;
      });

      bindings.putMember("importClass", (ProxyExecutable) args -> {
         if(!isEnabled()) {
            throw disabled();
         }

         BindingRootProxy root = currentRoot(context);
         String fqcn = args.length > 0 ? classNameOf(args[0]) : null;

         if(root != null && fqcn != null) {
            int dot = fqcn.lastIndexOf('.');
            String simple = dot < 0 ? fqcn : fqcn.substring(dot + 1);
            root.imports().addClass(simple, fqcn);
         }

         return null;
      });
   }

   /**
    * Resolve {@code prefix.name} as either a class leaf (allow-list gated) or a
    * deeper package proxy — mirroring Rhino's {@code NativeJavaPackage} "try the
    * class, else it's a sub-package" behavior. No classpath scanning.
    */
   static Object navigate(String prefix, String name, Predicate<String> filter, Context context) {
      if(!isEnabled()) {
         throw disabled();
      }

      if(!isResolvableName(name)) {
         return null;
      }

      String fqcn = prefix.isEmpty() ? name : prefix + "." + name;
      Class<?> cls = tryLoad(fqcn);

      if(cls != null) {
         if(filter != null && !filter.test(fqcn)) {
            throw new IllegalStateException("Class not permitted in scripts: " + fqcn);
         }

         return makeClassProxy(fqcn, context);
      }

      // not a class -> treat as a (possibly deeper) package.
      return new JavaPackageProxy(fqcn, filter, context);
   }

   /**
    * Last-resort unqualified resolution for {@code with(__scope__)} scripts,
    * driven by importClass/importPackage. Returns a class proxy or {@code null}.
    * Real JS globals/builtins always win (never shadowed by an import).
    */
   static Object resolveImport(ImportScope imports, String name, Predicate<String> filter,
                               Context context)
   {
      if(imports == null || !isEnabled() || !isResolvableName(name) || context == null) {
         return null;
      }

      Object cached = imports.cache.get(name);

      if(cached != null) {
         return cached == NONE ? null : cached;
      }

      // do not shadow a real JS global / builtin / library function.
      if(context.getBindings("js").hasMember(name)) {
         imports.cache.put(name, NONE);
         return null;
      }

      String fqcn = imports.classes.get(name);

      if(fqcn == null) {
         for(String pkg : imports.packages) {
            String cand = pkg + "." + name;

            if(tryLoad(cand) != null && (filter == null || filter.test(cand))) {
               fqcn = cand;
               break;
            }
         }
      }

      if(fqcn == null || (filter != null && !filter.test(fqcn))) {
         imports.cache.put(name, NONE);
         return null;
      }

      Object proxy = makeClassProxy(fqcn, context);
      imports.cache.put(name, proxy);

      return proxy;
   }

   private static JavaClassProxy makeClassProxy(String fqcn, Context context) {
      Value hostType = context.getBindings("js").getMember("Java").getMember("type").execute(fqcn);

      return new JavaClassProxy(hostType, fqcn);
   }

   static Class<?> tryLoad(String fqcn) {
      Optional<Class<?>> cached = CLASS_CACHE.get(fqcn);

      if(cached != null) {
         return cached.orElse(null);
      }

      // get() (not containsKey) so a hit refreshes the entry's LRU access order.
      if(NEGATIVE_CACHE.get(fqcn) != null) {
         return null;
      }

      ClassLoader cl = Thread.currentThread().getContextClassLoader();

      if(cl == null) {
         cl = LegacyJavaShim.class.getClassLoader();
      }

      try {
         // initialize=false: navigation must not run static initializers.
         Class<?> cls = Class.forName(fqcn, false, cl);
         CLASS_CACHE.put(fqcn, Optional.of(cls));
         return cls;
      }
      catch(ClassNotFoundException | LinkageError ex) {
         // Remember the miss so repeat probes don't rescan the whole classpath.
         // Bounded (LRU, see NEGATIVE_CACHE) to avoid unbounded growth from
         // exploratory package navigation.
         NEGATIVE_CACHE.put(fqcn, Boolean.TRUE);
         return null;
      }
   }

   static boolean isResolvableName(String name) {
      return name != null && !JS_META.contains(name) && IDENTIFIER.matcher(name).matches();
   }

   private static BindingRootProxy currentRoot(Context context) {
      if(context == null) {
         return null;
      }

      Value scope = context.getBindings("js").getMember("__scope__");

      if(scope != null && scope.isProxyObject()) {
         Object p = scope.asProxyObject();

         if(p instanceof BindingRootProxy) {
            return (BindingRootProxy) p;
         }
      }

      return null;
   }

   /** Extract a package prefix from an importPackage argument. */
   private static String packagePrefixOf(Value v) {
      if(v == null) {
         return null;
      }

      if(v.isProxyObject() && v.asProxyObject() instanceof JavaPackageProxy) {
         return ((JavaPackageProxy) v.asProxyObject()).prefix();
      }

      if(v.isString()) {
         return v.asString();
      }

      return null;
   }

   /** Extract a fully-qualified class name from an importClass argument. */
   private static String classNameOf(Value v) {
      if(v == null) {
         return null;
      }

      if(v.isProxyObject() && v.asProxyObject() instanceof JavaClassProxy) {
         return ((JavaClassProxy) v.asProxyObject()).className();
      }

      if(v.isString()) {
         return v.asString();
      }

      return null;
   }

   /** Sentinel for a cached negative import lookup. */
   private static final Object NONE = new Object();

   /**
    * Per-execution import state populated by importPackage/importClass and
    * consulted by {@link BindingRootProxy} for unqualified name resolution.
    * Created fresh per exec (so imports never leak across script runs).
    */
   public static final class ImportScope {
      final List<String> packages = new ArrayList<>();
      final Map<String, String> classes = new HashMap<>();
      final Map<String, Object> cache = new HashMap<>();

      void addPackage(String prefix) {
         if(!packages.contains(prefix)) {
            packages.add(prefix);
            cache.clear(); // a new prefix may resolve previously-negative names
         }
      }

      void addClass(String simpleName, String fqcn) {
         classes.put(simpleName, fqcn);
         cache.remove(simpleName);
      }
   }
}
