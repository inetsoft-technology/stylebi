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
import inetsoft.util.script.FormulaContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
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
      "com.oracle",
      // Bug #77466: JDK packages under the java.awt allowance whose classes load
      // a class named by a string and hand it out
      "java.awt.datatransfer",
      "java.awt.dnd",
      // Bug #77466: library packages that load, construct or call classes and
      // members named by strings, evaluate expressions, or reach files, the
      // network or the cluster. Blocked even when javascript.java.com_org is on
      // or javascript.java.packages names them. Not exhaustive: the set of such
      // libraries is open-ended, which is why com_org is off by default.
      "org.springframework",
      "org.apache.commons.lang3.reflect",
      "org.apache.commons.lang3.builder",
      "org.apache.commons.beanutils",
      "org.apache.commons.collections",
      "org.apache.commons.collections4",
      "org.apache.commons.text",
      "org.apache.commons.io",
      "org.apache.commons.net",
      "org.apache.commons.fileupload",
      "org.codehaus.groovy",
      "org.apache.groovy",
      "org.apache.ignite",
      "org.apache.logging",
      "org.apache.ivy",
      "org.apache.avro",
      "org.apache.lucene",
      "org.apache.derby",
      "org.apache.hc",
      "org.apache.http",
      "org.hsqldb",
      "org.postgresql",
      "org.eclipse",
      "org.hibernate",
      "org.jboss",
      "org.aspectj",
      "org.objenesis",
      "org.objectweb",
      "org.yaml",
      "org.thymeleaf",
      "org.quartz",
      "org.liquibase",
      "org.jdbi",
      "org.jsoup",
      "org.mockito",
      "org.testcontainers",
      "com.fasterxml",
      "com.esotericsoftware",
      "com.google.common.reflect",
      "com.google.inject",
      "com.github.jknack",
      "com.github.spullara",
      "com.github.dockerjava",
      "com.jcraft",
      "com.zaxxer",
      // Bug #77467: c3p0 + mchange-commons build a pooled DataSource from
      // script-supplied settings. HikariCP (com.zaxxer) and jdbi3 (org.jdbi),
      // listed above, are the other connection-pool / SQL-access libraries on the
      // runtime classpath.
      "com.mchange"
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
      "inetsoft.util.BlobIndexedStorage",
      // its statics hand out the raw executing ScriptScope (bypassing ScopeProxy
      // and the read-only principal view) and the thread's restricted flag that
      // gates runQuery from the web. Internal plumbing, never script API. (#77348)
      "inetsoft.util.script.FormulaContext",
      // same for getExecScriptable()/pushExecScriptable(); its script-facing
      // functions are bound as globals, not looked up by name (#77348)
      "inetsoft.util.script.JavaScriptEngine",
      // Bug #77466: resolve class names to classes, or rebuild objects from bytes;
      // the rest of the package is plain string and value helpers
      "org.apache.commons.lang3.ClassUtils",
      "org.apache.commons.lang3.SerializationUtils"
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

   // Replaces Java.type and Java.to with wrappers that test the class name first
   // (see installTypeLookupCheck). A non-string name is refused rather than
   // converted, so the name tested is the name looked up. Java.to's type may also
   // be a type Java.type returned. The wrappers use only what they captured when
   // installed, so a script that changes a built-in prototype cannot change them.
   private static final String TYPE_LOOKUP_CHECK_JS =
      "(function(allowed) {" +
      "  var J = Java, javaType = J.type, javaTo = J.to, isType = J.isType, TE = TypeError;" +
      "  var check = function(name) {" +
      "     if(typeof name !== 'string') {" +
      "        throw new TE('Java.type expects one string argument');" +
      "     }" +
      "     if(!allowed(name)) {" +
      "        throw new TE('Access to host class ' + name + ' is not allowed.');" +
      "     }" +
      "  };" +
      "  Object.defineProperty(J, 'type', {value: function type(name) {" +
      "     check(name); return javaType(name); }," +
      "     writable: false, configurable: false, enumerable: false});" +
      "  Object.defineProperty(J, 'to', {value: function to(value, name) {" +
      "     if(arguments.length < 2) { return javaTo(value); }" +
      "     if(!isType(name)) { check(name); }" +
      "     return javaTo(value, name); }," +
      "     writable: false, configurable: false, enumerable: false});" +
      "})";

   private static final Set<String> PRIMITIVE_TYPES = Set.of(
      "boolean", "byte", "char", "short", "int", "long", "float", "double");

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
                  // Bug #77497: a public static is shared by every script context and by
                  // Java code in the JVM, and a script can reach the statics of a class by
                  // more than one route (Java.type, the legacy package shim, the class
                  // globals). Member access is decided here per declaring type on all of
                  // them, so shared mutable statics are taken out of script reach by type:
                  // - a thread-local carries state from one script run to whatever runs
                  //   next on that thread; scripts cannot name the type already
                  //   (BLOCKED_CLASSES), and no script API hands one out
                  .denyAccess(ThreadLocal.class)
                  // - the constant holders the StyleConstant and Chart scopes are built
                  //   from declare only constants, some of them shared mutable arrays and
                  //   page sizes; scripts read them through those scopes, which hand out
                  //   copies (ConstantScope). Exact type only: the members of the classes
                  //   that implement or extend them are unaffected
                  .denyAccess(inetsoft.report.StyleConstants.class, false)
                  .denyAccess(inetsoft.report.composition.region.ChartConstants.class, false)
                  // Bug #77348: the #77255 read-only principal view is applied only
                  // where the engine converts a value for a script (toGuest). A raw
                  // host call (a Spring holder, VariableTable.get('__principal__'),
                  // a runtime sheet's getUser()) returns the live principal
                  // unconverted, and allowPublicAccess would expose its setters. So
                  // deny, by type, every member of the objects that are or hold the
                  // live session identity, or that decide which identity a session
                  // trusts. Deny is by the member's declaring class, so the extra
                  // interfaces a principal implements are listed too: Graal reports
                  // their methods as declared by the interface, not the principal.
                  .denyAccess(java.security.Principal.class)
                  // SRPrincipal.getClientUserID() is the live ClientInfo IdentityID
                  // that getName()/getIdentityID() are computed from
                  .denyAccess(inetsoft.util.LogPrincipal.class)
                  // readExternal resets an object's whole state; only the
                  // interface-declared methods, not every Externalizable class
                  .denyAccess(java.io.Externalizable.class, false)
                  // DestinationUserNameProviderPrincipal is a Principal, but Graal
                  // reports getDestinationUserName() as declared by this interface
                  .denyAccess(org.springframework.messaging.simp.user
                                 .DestinationUserNameProvider.class, false)
                  // the principal's user identity holder
                  .denyAccess(inetsoft.sree.ClientInfo.class)
                  // setUser/setBaseUser/setVPMUser swap which principal a session
                  // trusts without touching the principal itself
                  .denyAccess(inetsoft.report.composition.execution.ViewsheetSandbox.class)
                  .denyAccess(inetsoft.report.composition.execution.AssetQuerySandbox.class)
                  // every live session's sheet, its user and its sandbox
                  .denyAccess(inetsoft.report.composition.RuntimeSheet.class)
                  // the engine (static WorksheetEngine.getWorksheetService(),
                  // ViewsheetEngine), which hands out every user's sheets on the node
                  .denyAccess(inetsoft.report.composition.WorksheetService.class)
                  // Bug #77827, #77828: the asset engine, its storage and the
                  // Java-side helpers and singletons under the allowed prefixes
                  // that read, write or delete stored state for an entry, org id or
                  // user name the caller supplies, with no principal check of their
                  // own. None is script API, and the deny is by type, so it holds on
                  // every route (Java.type, the legacy shim, an inherited static, an
                  // instance an API returns). Java callers are unaffected, so
                  // runQuery, VSUtil.getBookmarks, library functions and calc-table
                  // rendering still work. A deny does not cover members Graal
                  // attributes to an undenied supertype (AutoCloseable.close,
                  // PropertyChangeListener.propertyChange, DataCache), so no
                  // script-reachable method may return one of these instances;
                  // AssetUtil.getAssetRepository refuses a direct script caller.
                  // - the asset engine (AbstractAssetEngine, RepletEngine,
                  //   AnalyticEngine, RuntimeAssetEngine, StyleCore and so ReportSheet
                  //   and TabularSheet) and the raw storage getStorage() returns
                  .denyAccess(inetsoft.uql.asset.AssetRepository.class)
                  .denyAccess(inetsoft.sree.RepletRepository.class)
                  .denyAccess(inetsoft.util.IndexedStorage.class)
                  // - asset readers that load a stored sheet for a minted entry with
                  //   no principal (LayoutTool covers VSLayoutTool and ReportLayoutTool,
                  //   whose public statics reach getNamedGroupAssembly)
                  .denyAccess(inetsoft.uql.asset.sync.DependencyTool.class)
                  .denyAccess(inetsoft.report.LayoutTool.class)
                  // ReportWorksheetProcessor, the only implementor, runs a stored
                  // worksheet for a minted entry (a null user loads it unchecked) and
                  // returns its data; XUtil.runQuery checks permission before using it
                  .denyAccess(inetsoft.uql.asset.WorksheetProcessor.class)
                  // - storage services keyed by an org id the caller passes
                  .denyAccess(inetsoft.report.LibManagerProvider.class)
                  .denyAccess(inetsoft.report.LibManager.class)
                  .denyAccess(inetsoft.uql.asset.EmbeddedTableStorage.class)
                  .denyAccess(inetsoft.uql.asset.EmbeddedDataCacheHandler.class)
                  .denyAccess(inetsoft.uql.asset.sync.DependencyStorageService.class)
                  .denyAccess(inetsoft.uql.viewsheet.vslayout.DeviceRegistry.class)
                  // - per-user and node-wide state
                  .denyAccess(inetsoft.uql.viewsheet.BookmarkLockManager.class)
                  .denyAccess(inetsoft.report.composition.execution.AssetDataCache.class)
                  .denyAccess(inetsoft.report.composition.execution
                                 .DistributedTableCacheStore.class)
                  // - the dependency and rename machinery
                  .denyAccess(inetsoft.uql.asset.sync.RenameTransformHandler.class)
                  // Bug #77852: the rest of the rename pipeline. DependencyTransformer
                  // covers its public subclasses; RenameTransformTask$Rename and $Remove
                  // don't extend RenameTransformTask, so they are named
                  .denyAccess(inetsoft.uql.asset.sync.DependencyTransformer.class)
                  .denyAccess(inetsoft.uql.asset.sync.UpdateDependencyHandler.class)
                  .denyAccess(inetsoft.uql.asset.sync.RenameTransformTask.class)
                  .denyAccess(inetsoft.uql.asset.sync.RenameTransformTask.Rename.class)
                  .denyAccess(inetsoft.uql.asset.sync.RenameTransformTask.Remove.class)
                  .denyAccess(inetsoft.uql.asset.sync.LoadDependencyStorageTask.class)
                  .denyAccess(inetsoft.uql.asset.sync.RenameTransformQueue.class)
                  // the delete dependency checkers load a stored sheet through
                  // DependencyTool for the entry they are passed. DependencyChecker covers
                  // AssetDependencyChecker and ViewsheetDependencyChecker
                  .denyAccess(inetsoft.uql.asset.delete.DependencyChecker.class)
                  .denyAccess(inetsoft.uql.asset.delete.DeleteDependencyHandler.class)
                  .denyAccess(inetsoft.uql.asset.UpdateAssetDependenciesHandler.class)
                  .denyAccess(inetsoft.uql.asset.DependencyHandler.class)
                  .denyAccess(inetsoft.report.internal.MVInfoClient.class)
                  // XUtil.getXIdentityFinder() resolves every user's roles, groups
                  // and org, and its getters return the live arrays
                  .denyAccess(inetsoft.uql.util.XIdentityFinder.class)
                  // Bug #77421: the driver and data source registries load and
                  // initialize classes by name through plugin class loaders
                  // without consulting classFilter(); neither is script API
                  .denyAccess(inetsoft.uql.util.Drivers.class)
                  .denyAccess(inetsoft.uql.util.Config.class)
                  // JDBCHandler's statics do the same for driver classes and return
                  // live drivers and connections; TabularUtil's view helpers invoke
                  // the methods a view names on whatever bean they are passed. Both
                  // are used by Java callers only. (Bug #77467: the XHandler deny
                  // below now also covers JDBCHandler; this line is not load-bearing.)
                  .denyAccess(inetsoft.uql.jdbc.JDBCHandler.class)
                  .denyAccess(inetsoft.uql.tabular.TabularUtil.class)
                  // Bug #77467: classFilter() gates only the Java.type(...) lookup;
                  // it never consults member access on an object a script already
                  // holds. So once a script reaches any object that is or yields a
                  // live JDBC handle, every public method on it is callable
                  // (getConnection/createStatement/executeQuery/connect) no matter
                  // whether its class is in classFilter. There are many ways a script
                  // can get such an object (the pool factories, a pool library, the
                  // driver manager, a Driver on the classpath), so the fix is at the
                  // member layer: deny, by type, the connection-bearing JDBC types,
                  // which kills the operation on the held object however it was
                  // obtained. Deny on an interface covers its implementors and their
                  // construction/statics (verified against Graal 24.1.2), so the pool
                  // factory interface covers all three factory impls in one line.
                  // java.sql.Statement covers Prepared/CallableStatement (subtypes),
                  // and javax.sql.DataSource covers Hikari's HikariDataSource.
                  // java.sql.Types/Date/Time/Timestamp are value types with no
                  // connection methods and are untouched (they stay in ALLOWED_CLASSES
                  // at the type layer), so the form write-back API (createConnection ->
                  // DBScriptable, which keeps the Connection on the Java side and hands
                  // the script only XTableArray/primitives) still works.
                  .denyAccess(javax.sql.DataSource.class)
                  .denyAccess(javax.sql.ConnectionPoolDataSource.class)
                  .denyAccess(javax.sql.XADataSource.class)
                  .denyAccess(javax.sql.PooledConnection.class)
                  .denyAccess(java.sql.Connection.class)
                  .denyAccess(java.sql.Statement.class)
                  .denyAccess(java.sql.Driver.class)
                  .denyAccess(java.sql.DriverManager.class)
                  // the pool factory interface (covers Default/JNDI/Legacy impls, their
                  // statics and construction) and the Hikari pool types a script could
                  // drive directly (HikariConfig.setDriverClassName instantiates an
                  // arbitrary named class via internal reflection, bypassing classFilter)
                  .denyAccess(inetsoft.uql.jdbc.ConnectionPoolFactory.class)
                  // (HikariDataSource extends HikariConfig, so this covers it too)
                  .denyAccess(com.zaxxer.hikari.HikariConfig.class)
                  // unwrap()/isWrapperFor() let a held JDBC handle whose class is not
                  // public (Graal then reports the interface-declared method) hand
                  // out the vendor API behind it; nothing script-facing is a Wrapper
                  .denyAccess(java.sql.Wrapper.class)
                  // Bug #77467 (R5): the query engine runs a query against the data
                  // source the query carries (XQuery.getDataSource()) with no
                  // data-source permission check. A script that builds a JDBCQuery
                  // over a JDBCDataSource it made itself and passes it to the manager
                  // or engine would run arbitrary SQL inside Java without ever
                  // receiving a Connection, so the member-layer java.sql denies above
                  // would not stop it. Deny the two engine-side execution entry points
                  // reachable from script (getXNodeTableLens/getXNode on the session
                  // manager, and the execute(...) family on the data service that
                  // XRepository/XEngine expose). Both are Java-caller APIs; the
                  // permission-checked, by-name script query path (XUtil.runQuery) is
                  // unaffected because it runs on the Java side.
                  .denyAccess(inetsoft.report.XSessionManager.class)
                  .denyAccess(inetsoft.uql.XDataService.class)
                  // Bug #77467 (round 1): the same cause has more Java-side helpers
                  // than the two entry points above. XAgent/JDBCAgent.getQueryData,
                  // ColumnCache.getColumnData, SQLTypes.getChildMetaData,
                  // JDBCUtil.getTableColumns and the XHandler family each take a data
                  // source or query from the caller and connect or run it with no
                  // permission check, and the pool properties a data source carries
                  // (e.g. connectionInitSql) run SQL on every new connection. Naming
                  // helpers one at a time keeps missing some, so close the source:
                  // scripts must not construct, configure or read data-source and
                  // query objects. A deny on the base classes covers every subclass
                  // (JDBC, XMLA, tabular and plugin connectors), their constructors,
                  // setters, getters (incl. credentials) and clone(). No script API
                  // hands these objects to scripts; the form write-back API
                  // (DBScriptable) holds its data source on the Java side.
                  .denyAccess(inetsoft.uql.XDataSource.class)
                  .denyAccess(inetsoft.uql.XQuery.class)
                  // ...and the Java-side factories that would otherwise build one from
                  // script input without the script touching an XDataSource member:
                  // the XML wrappers (parseXML instantiates and configures the data
                  // source or query an element describes), the registry
                  // (parseXDataSource2 does the same; getDataSource and the
                  // set/remove methods read and write the stored data sources with no
                  // permission check of their own) and the data source listings
                  // (createDataSource() returns a configured data source)
                  .denyAccess(inetsoft.uql.XDataSourceWrapper.class)
                  .denyAccess(inetsoft.uql.XQueryWrapper.class)
                  .denyAccess(inetsoft.uql.service.DataSourceRegistry.class)
                  .denyAccess(inetsoft.uql.DataSourceListing.class)
                  // Defense in depth for a data source or query a script still holds
                  // (e.g. one a Java API returned): deny the helpers that connect or
                  // run it. XAgent covers JDBCAgent/XMLAAgent, and XHandler covers
                  // JDBCHandler/XMLAHandler/TabularHandler. None is script API.
                  .denyAccess(inetsoft.uql.util.XAgent.class)
                  .denyAccess(inetsoft.uql.util.ColumnCache.class)
                  .denyAccess(inetsoft.uql.service.XHandler.class)
                  .denyAccess(inetsoft.uql.jdbc.util.SQLTypes.class)
                  .denyAccess(inetsoft.uql.jdbc.util.JDBCUtil.class)
                  // DefaultMetaDataProvider (the only MetaDataProvider) runs metadata
                  // Java-side against whatever data source it is given, connecting
                  // with its stored credentials and no permission check
                  .denyAccess(inetsoft.uql.util.MetaDataProvider.class)
                  .denyAccess(inetsoft.uql.util.DefaultMetaDataProvider.class)
                  // XUtil.getSecurityProvider(), and the interfaces its providers'
                  // configuration and cache methods are declared by
                  .denyAccess(inetsoft.sree.security.AuthenticationProvider.class)
                  .denyAccess(inetsoft.sree.security.AuthorizationProvider.class)
                  .denyAccess(inetsoft.sree.security.JsonConfigurableProvider.class)
                  .denyAccess(inetsoft.sree.security.CachableProvider.class)
                  .denyAccess(inetsoft.sree.security.AuthenticationChangeListener.class, false)
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

               // Bug #77521: classFilter() is consulted only when a script looks a
               // class up by name. A Class value an allowed API returns (or an
               // instance of the class) gives a script the class's public members,
               // statics and constructors, with no name check. So every class
               // refused by exact name is also denied here by type.
               denyBlockedClasses(builder);

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
    *
    * <p>The returned predicate reads {@link FormulaContext#isRestricted()} on every
    * test. While a script runs restricted (end-user script surfaces), the
    * {@code com.*}/{@code org.*} allowance and the {@code script.java.allowed.classes}
    * grant do not apply; only {@code javascript.java.packages} still grants
    * packages, which mirrors the restricted package roots of the Rhino engine. GraalJS keeps the classes a
    * context has already found, so a lookup is not always tested again;
    * {@link #installTypeLookupCheck} closes that gap for {@code Java.type}.
    * (Bug #77396)
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
         // Bug #77466: off unless an operator turns it on. Any class under com./org.
         // on the server class path would otherwise be visible, including library
         // helpers that reach classes and members the filter refuses
         comOrg = "true".equalsIgnoreCase(
            SreeEnv.getProperty("javascript.java.com_org", "false").trim());
      }
      catch(Exception ignore) {
         customPkgs = new String[0];
         comOrg = false;
      }

      final String[] customPkgsF = customPkgs;
      final boolean comOrgF = comOrg;

      return classFilter(extra, customPkgsF, comOrgF);
   }

   /**
    * Denies, by type, every class of {@link #BLOCKED_CLASSES} on the class path, so
    * the name block holds for a class value or an instance a script gets from an
    * allowed API. A name that doesn't load (e.g. a class removed from the JDK) is
    * skipped. (Bug #77521)
    */
   private static void denyBlockedClasses(HostAccess.Builder builder) {
      for(String name : BLOCKED_CLASSES) {
         Class<?> type;

         try {
            type = Class.forName(name, false, ScriptHostAccess.class.getClassLoader());
         }
         catch(ClassNotFoundException | LinkageError ignore) {
            continue;
         }

         builder.denyAccess(type);
      }
   }

   /** The classes refused by exact name, for tests. */
   static Set<String> blockedClasses() {
      return BLOCKED_CLASSES;
   }

   /**
    * Whether a class value may be handed to a script: when the class filter would
    * admit the class by name. A primitive type is not a class and is always
    * allowed, as in {@code Java.type}. (Bug #77521)
    */
   static boolean isClassValueVisible(Class<?> type, Predicate<String> filter) {
      while(type.isArray()) {
         type = type.getComponentType();
      }

      return type.isPrimitive() || filter.test(type.getName());
   }

   /**
    * Loads a class named by a script for a helper that turns a script-supplied class
    * name into a class or an instance. The name is checked against
    * {@link #classFilter()} first, and the class is loaded without being initialized,
    * so no code of a class the filter refuses runs. (Bug #77421)
    *
    * @param name   the fully qualified class name.
    * @param loader the class loader to load the class from.
    *
    * @return the loaded, uninitialized class.
    *
    * @throws SecurityException      if the class filter refuses the name.
    * @throws ClassNotFoundException if the class could not be found.
    */
   public static Class<?> loadScriptVisibleClass(String name, ClassLoader loader)
      throws ClassNotFoundException
   {
      if(!classFilter().test(name)) {
         throw new SecurityException("Class " + name + " is not allowed in scripts");
      }

      return Class.forName(name, false, loader);
   }

   /**
    * Package-private overload taking the already-parsed property values, so tests
    * can exercise the allow/deny precedence without a SreeEnv context.
    */
   static Predicate<String> classFilter(Set<String> extra, String[] customPkgs,
                                        boolean comOrg)
   {
      // invariant: no unrestricted script may share a Context with restricted surfaces,
      // since a host type it leaves in a global bypasses this filter (bug #77396)
      // a restricted script gets no com/org or extra classes, only the packages an
      // administrator listed in javascript.java.packages, as in Rhino (bug #77396)
      return fqcn -> FormulaContext.isRestricted() ?
         isVisibleToScripts(fqcn, Set.of(), customPkgs, false) :
         isVisibleToScripts(fqcn, extra, customPkgs, comOrg);
   }

   /**
    * Makes {@code Java.type} and {@code Java.to} test a class name against the
    * class filter on every call. GraalJS tests the filter given to
    * {@code allowHostClassLookup} only the first time a context looks up a class
    * and keeps the class after that, so without this check a class found by an
    * unrestricted script would stay reachable from a restricted script that runs
    * later in the same context. The original functions are kept only in a
    * closure, so a script cannot reach them. (Bug #77396)
    *
    * @param context the context to install the check in, before any script runs.
    * @param filter  the class filter the context was built with.
    */
   public static void installTypeLookupCheck(Context context, Predicate<String> filter) {
      Value install = context.eval(Source.create("js", TYPE_LOOKUP_CHECK_JS));
      install.execute((ProxyExecutable) args -> args.length > 0 && args[0].isString() &&
         isTypeNameAllowed(args[0].asString(), filter));
   }

   /**
    * Whether a type name given to Java.type or Java.to passes the class filter. An
    * array type name ("x.Y[]") tests its element type, and a primitive type is not
    * a class, as in the GraalJS lookup itself.
    */
   private static boolean isTypeNameAllowed(String name, Predicate<String> filter) {
      while(name.endsWith("[]")) {
         name = name.substring(0, name.length() - 2);
      }

      return PRIMITIVE_TYPES.contains(name) || filter.test(name);
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
