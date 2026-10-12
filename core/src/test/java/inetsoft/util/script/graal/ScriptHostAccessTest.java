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

import org.graalvm.polyglot.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ScriptHostAccessTest {
   private Context newContext() {
      return Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(ScriptHostAccess.classFilter())
         .build();
   }

   /**
    * Builds a context as if script.java.allowed.classes named the given FQCNs,
    * without needing a SreeEnv context. The remaining arguments mirror the
    * production defaults (no javascript.java.packages, com_org off).
    */
   private Context newContext(Set<String> extra) {
      return Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(ScriptHostAccess.classFilter(extra, new String[0], false))
         .build();
   }

   @Test void allowedJavaClassLoads() {
      try(Context ctx = newContext()) {
         Object v = ScriptValueConverter.toHost(
            ctx.eval("js", "Java.type('java.lang.Math').max(2, 5)"));
         assertEquals(5.0, v);
      }
   }

   @Test void deniedJavaClassThrows() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.System').exit(1)"));
      }
   }

   @Test void exportedMethodAccessible() {
      // Note: allowPublicAccess(true) is required for Java.type() static method access;
      // this means all public methods of host objects are also accessible. The @Export
      // annotation pattern still works for selective opt-in but cannot be used to DENY
      // access to other public methods when allowPublicAccess is true. The class filter
      // (allowHostClassLookup) is the primary security boundary.
      try(Context ctx = newContext()) {
         ctx.getBindings("js").putMember("h", new Sample());
         assertEquals("ok", ScriptValueConverter.toHost(ctx.eval("js", "h.allowed()")));
      }
   }

   /** FIX 1: internal blocked class must not be reachable via Java.type(). */
   @Test void blockedInternalClassDenied() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js",
               "Java.type('inetsoft.report.internal.license.LicenseManager')"));
      }
   }

   /** FIX 2: reflection escape via getClass() on a host object must be blocked. */
   @Test void reflectionEscapeBlocked() {
      try(Context ctx = newContext()) {
         ctx.getBindings("js").putMember("d", new java.util.Date());
         // d.getClass() would return java.lang.Class — denyAccess(Class.class) must block it
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "d.getClass()"));
      }
   }

   /**
    * Task 6.4: curated exact safe classes load even when their package is in the
    * block list (java.sql, java.text). java.util.UUID is a plain curated class.
    */
   @Test void curatedExactClassOverBlockedPackage() {
      try(Context ctx = newContext()) {
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.sql.Date')"));
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.text.NumberFormat')"));
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.util.UUID')"));
      }
   }

   /** Task 6.4: dangerous classes remain blocked (not in the exact allow-list). */
   @Test void dangerousClassesStillBlocked() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.System')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.Runtime')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.Class')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.io.File')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js",
               "Java.type('inetsoft.report.internal.license.LicenseManager')"));
      }
   }

   /**
    * Task 6.4: a java.sql class that is NOT in the curated allow-list is still
    * denied by the package block.
    */
   @Test void uncuratedBlockedPackageClassDenied() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.sql.DriverManager')"));
      }
   }

   /**
    * Regression (#75423): the broad main-branch allow-list was narrowed away in
    * the initial GraalJS cutover. java.awt.Color (and the java.awt/text/util
    * families) must be reachable again via Java.type. The com/org families are
    * off by default since #77466.
    */
   @Test void restoredPackageAllowListLoads() {
      try(Context ctx = newContext()) {
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.awt.Color')"));
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.util.ArrayList')"));
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.text.MessageFormat')"));
      }
   }

   /** Threading stays blocked regardless of the restored package allow-list. */
   @Test void threadingStaysBlocked() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.Thread')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.util.concurrent.ConcurrentHashMap')"));
      }
   }

   /**
    * script.java.allowed.classes is additive only: naming a BLOCKED_CLASSES entry
    * must not re-enable it. Java.type resolution is the boundary being asserted
    * here -- java.lang.Runtime is separately denied at the member level by
    * hostAccess(), but java.io.File is not, so the class filter is what stops it.
    */
   @Test void extrasCannotUnblockBlockedClass() {
      try(Context ctx = newContext(Set.of("java.lang.Runtime", "java.io.File"))) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.Runtime')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.io.File')"));
      }
   }

   /** Same for a class reached through a BLOCKED_PACKAGES prefix. */
   @Test void extrasCannotUnblockBlockedPackage() {
      try(Context ctx = newContext(Set.of("java.lang.reflect.Method",
                                          "inetsoft.report.internal.license.LicenseManager")))
      {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.reflect.Method')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js",
               "Java.type('inetsoft.report.internal.license.LicenseManager')"));
      }
   }

   /**
    * The property still does what it is for: a class on neither deny list, and on
    * no default allow path, becomes visible when named. java.time is outside every
    * allowed prefix/package, so it is denied until the property names it.
    */
   @Test void extrasStillAddNonBlockedClass() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.time.LocalDate')"));
      }

      try(Context ctx = newContext(Set.of("java.time.LocalDate"))) {
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.time.LocalDate')"));
      }
   }

   /**
    * Bug #75690: Rhino auto-adapted a JS function to a single-method Java
    * interface, so scripts could pass a lambda to a Java method expecting a
    * functional interface (e.g. Stream.filter(Predicate), Stream.forEach(Consumer)
    * via graph shape scripting). GraalJS refuses this unless the HostAccess policy
    * allows implementing @FunctionalInterface types.
    */
   @Test void jsFunctionAdaptsToFunctionalInterface() {
      try(Context ctx = newContext()) {
         ctx.getBindings("js").putMember("h", new Sample());
         // Predicate via Stream.filter
         Object count = ScriptValueConverter.toHost(
            ctx.eval("js", "h.numbers().filter(n => n > 1).count()"));
         assertEquals(2L, ((Number) count).longValue());
         // Consumer via Stream.forEach
         assertDoesNotThrow(() -> ctx.eval("js",
            "var seen = []; h.numbers().forEach(n => seen.push(n));"));
      }
   }

   /**
    * Bug #75690 (follow-up): the fix is intentionally general (any
    * {@code @FunctionalInterface}), not just Predicate/Consumer. Verify a
    * Comparator — a single-method interface whose method returns int rather than
    * boolean/void — also adapts from a JS function.
    */
   @Test void jsFunctionAdaptsToComparator() {
      try(Context ctx = newContext()) {
         ctx.getBindings("js").putMember("h", new Sample());
         // natural-order Comparator; the JS function's numeric result adapts to int
         Object max = ScriptValueConverter.toHost(
            ctx.eval("js", "h.maxBy((a, b) => a - b)"));
         assertEquals(3, ((Number) max).intValue());
      }
   }

   /**
    * Bug #75690 (follow-up): the allowance is scoped to
    * {@code @FunctionalInterface} types. A JS object must NOT be able to satisfy a
    * plain multi-method interface, confirming the widening did not open arbitrary
    * interface implementation.
    */
   @Test void jsObjectDoesNotSatisfyMultiMethodInterface() {
      try(Context ctx = newContext()) {
         ctx.getBindings("js").putMember("h", new Sample());
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js",
               "h.useMulti({ first: function() { return 'a'; }, " +
               "second: function() { return 'b'; } })"));
      }
   }

   /**
    * Bug #77348 denies java.io.Externalizable for the exact type only, to hide the
    * interface-declared readExternal/writeExternal on principals. Scripts still hold
    * other Externalizable objects (e.g. query result tables such as XSwappableTable),
    * so their own members must stay callable: a subclass-wide deny would hide them.
    */
   @Test void externalizableHostObjectKeepsItsOwnMembers() {
      try(Context ctx = newContext()) {
         ctx.getBindings("js").putMember("t", new ExternalizableBean());
         assertEquals("v:1", ScriptValueConverter.toHost(
            ctx.eval("js", "t.getValue() + ':' + t.getRowCount()")));
         assertEquals(true, ScriptValueConverter.toHost(
            ctx.eval("js", "Object.keys(t).indexOf('getValue') >= 0")));
      }
   }

   /**
    * JavaScriptEngine's statics hand out and replace the raw executing scope, the
    * way FormulaContext's do, so neither may be looked up by name. (#77348)
    */
   @Test void scriptEngineInternalsNotLoadable() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('inetsoft.util.script.JavaScriptEngine')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('inetsoft.util.script.FormulaContext')"));
      }
   }

   /**
    * Instances of the privileged engine, storage and helper types, and a member each
    * declares. None is script API, so the member is hidden on any instance a script
    * holds. (#77827, #77828)
    */
   static Stream<Arguments> privilegedInstanceMembers() {
      return Stream.of(
         Arguments.of(inetsoft.uql.asset.AssetRepository.class, "getSheet"),
         Arguments.of(inetsoft.uql.asset.AssetRepository.class, "clearVSBookmark"),
         Arguments.of(inetsoft.uql.asset.AssetRepository.class, "getStorage"),
         Arguments.of(inetsoft.sree.RepletRepository.class, "removeRepositoryEntry"),
         Arguments.of(inetsoft.util.IndexedStorage.class, "remove"),
         Arguments.of(inetsoft.util.IndexedStorage.class, "getKeys"),
         Arguments.of(inetsoft.uql.asset.sync.DependencyStorageService.class,
                      "removeDependencyStorage"),
         Arguments.of(inetsoft.report.LibManagerProvider.class, "getManager"),
         Arguments.of(inetsoft.report.LibManager.class, "setScript"),
         Arguments.of(inetsoft.uql.asset.EmbeddedTableStorage.class, "removeTable"),
         Arguments.of(inetsoft.uql.viewsheet.vslayout.DeviceRegistry.class, "deleteDevice"),
         Arguments.of(inetsoft.uql.viewsheet.BookmarkLockManager.class, "lock"),
         Arguments.of(inetsoft.report.composition.execution.AssetDataCache.class,
                      "getLocalEntries"),
         Arguments.of(inetsoft.uql.asset.sync.RenameTransformHandler.class, "addTransformTask"),
         Arguments.of(inetsoft.uql.asset.UpdateAssetDependenciesHandler.class, "rebuild"),
         Arguments.of(inetsoft.uql.asset.DependencyHandler.class,
                      "updateDashboardDependencies"),
         Arguments.of(inetsoft.report.internal.MVInfoClient.class, "getDataRefreshedTime"),
         Arguments.of(inetsoft.uql.asset.WorksheetProcessor.class, "execute"),
         Arguments.of(inetsoft.report.composition.execution.ReportWorksheetProcessor.class,
                      "execute"));
   }

   @ParameterizedTest(name = "{0}.{1}")
   @MethodSource("privilegedInstanceMembers")
   void privilegedTypeMembersDenied(Class<?> type, String member) {
      try(Context ctx = newContext()) {
         ctx.getBindings("js").putMember("h", Mockito.mock(type));
         assertEquals("undefined", ctx.eval("js", "typeof h." + member).asString());
      }
   }

   /**
    * The statics that hand out or act through the privileged types, on the Java.type
    * route; a subclass inherits the deny (VSLayoutTool). (#77827, #77828)
    */
   static Stream<Arguments> privilegedStatics() {
      return Stream.of(
         Arguments.of("inetsoft.uql.asset.sync.DependencyTool", "getAssetElement"),
         Arguments.of("inetsoft.uql.asset.sync.DependencyStorageService", "getInstance"),
         Arguments.of("inetsoft.report.LayoutTool", "getNamedGroupAssembly"),
         Arguments.of("inetsoft.uql.viewsheet.VSLayoutTool", "getNamedGroupAssembly"),
         Arguments.of("inetsoft.uql.viewsheet.VSLayoutTool", "createCalcLens"),
         Arguments.of("inetsoft.report.LibManagerProvider", "getInstance"),
         Arguments.of("inetsoft.uql.asset.EmbeddedTableStorage", "getInstance"),
         Arguments.of("inetsoft.uql.asset.EmbeddedDataCacheHandler", "clearOrgCache"),
         Arguments.of("inetsoft.uql.viewsheet.vslayout.DeviceRegistry", "getRegistry"),
         Arguments.of("inetsoft.uql.viewsheet.BookmarkLockManager", "getManager"),
         Arguments.of("inetsoft.report.composition.execution.AssetDataCache", "getCache"),
         Arguments.of("inetsoft.report.composition.execution.DistributedTableCacheStore",
                      "getInstance"),
         Arguments.of("inetsoft.uql.asset.sync.RenameTransformHandler", "getTransformHandler"),
         Arguments.of("inetsoft.uql.asset.UpdateAssetDependenciesHandler", "getInstance"),
         Arguments.of("inetsoft.uql.asset.DependencyHandler", "getInstance"),
         Arguments.of("inetsoft.report.internal.MVInfoClient", "getInstance"),
         Arguments.of("inetsoft.uql.asset.AbstractAssetEngine", "getPortalDataEntries"));
   }

   @ParameterizedTest(name = "{0}.{1}")
   @MethodSource("privilegedStatics")
   void privilegedStaticsDenied(String type, String member) {
      try(Context ctx = newContext()) {
         assertEquals("undefined", ctx.eval(
            "js", "typeof Java.type('" + type + "')." + member).asString());
      }
   }

   /** A script-API type next to the denied ones keeps its members. (#77827, #77828) */
   @Test void scriptApiTypeKeepsMembers() {
      try(Context ctx = newContext()) {
         assertEquals("function", ctx.eval("js",
            "typeof Java.type('inetsoft.uql.asset.AssetEntry').createAssetEntry").asString());
         assertEquals("function", ctx.eval("js",
            "typeof Java.type('inetsoft.uql.asset.internal.AssetUtil').getAssetRepository")
            .asString());
      }
   }

   public static class ExternalizableBean implements java.io.Externalizable {
      public String getValue() { return "v"; }
      public int getRowCount() { return 1; }
      @Override public void writeExternal(java.io.ObjectOutput out) { }
      @Override public void readExternal(java.io.ObjectInput in) { }
   }

   public static class Sample {
      @org.graalvm.polyglot.HostAccess.Export public String allowed() { return "ok"; }
      public String denied() { return "no"; }
      @org.graalvm.polyglot.HostAccess.Export
      public java.util.stream.Stream<Integer> numbers() {
         return java.util.stream.Stream.of(1, 2, 3);
      }
      @org.graalvm.polyglot.HostAccess.Export
      public int maxBy(java.util.Comparator<Integer> cmp) {
         return java.util.stream.Stream.of(1, 2, 3).max(cmp).get();
      }
      @org.graalvm.polyglot.HostAccess.Export
      public String useMulti(MultiMethod m) {
         return m.first() + m.second();
      }
   }

   /** Plain (non-@FunctionalInterface) multi-method interface for the negative case. */
   public interface MultiMethod {
      String first();
      String second();
   }
}
