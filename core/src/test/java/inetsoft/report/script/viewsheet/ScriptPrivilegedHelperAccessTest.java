/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.report.script.viewsheet;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ReportWorksheetProcessor;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependenciesInfo;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.BookmarkLockManager;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.util.ThreadContext;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Java-side helpers and storage singletons under the script-allowed packages read and
 * write stored state for whatever entry, org id or user name they are passed. A sheet script
 * can't use them, on any route, and the stored state is unchanged afterwards. Library
 * functions, which the engine installs from Java, still work. (Bug #77828)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  ScriptPrivilegedHelperAccessTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class ScriptPrivilegedHelperAccessTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String ORG_A = "orgA77828";
   private static final String ORG_B = "orgB77828";
   private static final String ANN_VS = "4^128^annA~;~" + ORG_A + "^AnnPrivVs^" + ORG_A;
   private static final String BOB_WS = "4^2^bobB~;~" + ORG_B + "^BobPrivWs^" + ORG_B;
   private static final String WS_PATH = "Ws77828";
   private static final String WS_SECRET = "SECRET-orgB-ws-row";
   private static final String DEPENDENCY_KEY = "key77828";
   private static final String TABLE_PATH = "t77828/table";
   private static final String DEVICE_ID = "device77828";
   private static final String LOCK_KEY = "lock77828";
   private static final String UNKNOWN = "Unknown identifier";

   private static final String FUNCS = "var AE = Java.type('inetsoft.uql.asset.AssetEntry');";

   private SecurityTestDataBuilder builder;
   private ViewsheetSandbox sandbox;
   private ViewsheetScope viewsheetScope;
   private String oldProvider;
   private Principal savedContextPrincipal;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      savedPrincipal = ThreadContext.getPrincipal();

      oldProvider = SreeEnv.getProperty("security.provider");
      SreeEnv.setProperty("security.provider", "file");
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_A, ORG_A)
         .addOrg(ORG_B, ORG_B)
         .addUser("aliceA", ORG_A, "password")
         .addUser("annA", ORG_A, "password")
         .addUser("bobB", ORG_B, "password")
         .setup();

      saveAnnViewsheet();
      saveBobWorksheet();
      seedOrgB();

      SRPrincipal alice = builder.principalOf("aliceA", ORG_A);
      ThreadContext.setContextPrincipal(alice);
      ThreadContext.setPrincipal(alice);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
      OrganizationContextHolder.setCurrentOrgId(null);
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);

      if(builder != null) {
         builder.teardown();
      }
   }

   /** Another user's private viewsheet can't be read through DependencyTool. */
   @Test
   void dependencyToolRefused() throws Exception {
      Object result = run("'' + Java.type('inetsoft.uql.asset.sync.DependencyTool')" +
                             ".getAssetElement(AE.createAssetEntry('" + ANN_VS + "'))");

      assertRefused(result);
   }

   /** Another org's named group can't be read through LayoutTool or a subclass, on any route. */
   @Test
   void layoutToolRefusedOnEveryRoute() throws Exception {
      String call = ".getNamedGroupAssembly(AE.createAssetEntry('" + BOB_WS + "'))";
      Object javaType = run("'' + Java.type('inetsoft.report.LayoutTool')" + call);
      Object subclass = run("'' + Java.type('inetsoft.uql.viewsheet.VSLayoutTool')" + call);
      Object legacy = run("'' + inetsoft.report.LayoutTool" + call);

      assertRefused(javaType);
      assertRefused(subclass);
      assertInstanceOf(String.class, legacy, String.valueOf(legacy));
      assertTrue(((String) legacy).startsWith("error: "), String.valueOf(legacy));
      assertFalse(((String) legacy).contains("SECRETGROUP_bob"), String.valueOf(legacy));
   }

   @Test
   void dependencyStorageRefused() throws Exception {
      assertRefused(run("Java.type('inetsoft.uql.asset.sync.DependencyStorageService')" +
                           ".getInstance().removeDependencyStorage('" + ORG_B + "'); 'removed'"));
      assertNotNull(DependencyStorageService.getInstance().getWithOrg(DEPENDENCY_KEY, ORG_B));
   }

   @Test
   void libManagerRefused() throws Exception {
      assertRefused(run("var m = Java.type('inetsoft.report.LibManagerProvider').getInstance()" +
                           ".getManager('" + ORG_B + "');" +
                           "m.setScript('f77828', 'function f77828() { return 666; }');" +
                           "m.save(); 'saved'"));

      // reload the managers from storage
      LibManagerProvider.getInstance().clear();
      assertNull(LibManagerProvider.getInstance().getManager(ORG_B).getScript("f77828"));
   }

   @Test
   void embeddedTableRefused() throws Exception {
      String storage = "Java.type('inetsoft.uql.asset.EmbeddedTableStorage').getInstance()";
      Object read = run("var s = " + storage + ".readTable('" + TABLE_PATH + "', '" + ORG_B +
                           "'); var b = ''; var c; while((c = s.read()) >= 0) {" +
                           " b += String.fromCharCode(c); } b");

      assertRefused(read);
      assertFalse(String.valueOf(read).contains("SECRET"), String.valueOf(read));
      assertRefused(run(storage + ".removeTable('" + TABLE_PATH + "', '" + ORG_B +
                           "'); 'removed'"));
      assertTrue(EmbeddedTableStorage.getInstance().tableExists(TABLE_PATH, ORG_B));
   }

   @Test
   void deviceRegistryRefused() throws Exception {
      assertRefused(run("Java.type('inetsoft.uql.viewsheet.vslayout.DeviceRegistry')" +
                           ".getRegistry().deleteDevice('" + DEVICE_ID + "'); 'deleted'"));
      assertNotNull(DeviceRegistry.getRegistry().getDevice(DEVICE_ID));
   }

   @Test
   void bookmarkLockRefused() throws Exception {
      assertRefused(run("Java.type('inetsoft.uql.viewsheet.BookmarkLockManager').getManager()" +
                           ".lock('" + LOCK_KEY + "', 'bobB~;~" + ORG_B + "', 'rt'); 'locked'"));
      assertNull(BookmarkLockManager.getManager().getLockedBookmarkUser(LOCK_KEY, "someone"));
   }

   @Test
   void assetDataCacheRefused() throws Exception {
      assertRefused(run("'' + Java.type('inetsoft.report.composition.execution.AssetDataCache')" +
                           ".getCache().getLocalEntries().size()"));
   }

   /**
    * Another org's worksheet data can't be read through ReportWorksheetProcessor, which
    * loads the sheet without a permission check for a null user. Java callers
    * (XUtil.runQuery, BrowsedData) still run it.
    */
   @Test
   void worksheetProcessorRefused() throws Exception {
      AssetEntry wsEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                          AssetEntry.Type.WORKSHEET, WS_PATH, null, ORG_B);
      String call = ".execute(AE.createAssetEntry('" + wsEntry.toIdentifier() + "')," +
         " new (Java.type('inetsoft.uql.VariableTable'))(), null);" +
         " '' + t.getObject(1, 0)";
      Object result = run("var t = new (Java.type(" +
         "'inetsoft.report.composition.execution.ReportWorksheetProcessor'))()" + call);

      assertInstanceOf(String.class, result, String.valueOf(result));
      assertTrue(((String) result).startsWith("error: "), String.valueOf(result));
      assertFalse(((String) result).contains(WS_SECRET), String.valueOf(result));

      // a Java caller still runs the worksheet
      XTable table = new ReportWorksheetProcessor().execute(wsEntry, new VariableTable(), null);
      assertNotNull(table);
      assertTrue(table.moreRows(1));
      assertEquals(WS_SECRET, table.getObject(1, 0));
   }

   /** Library functions are installed from Java and still run. */
   @Test
   void libraryFunctionStillRuns() throws Exception {
      LibManager manager = LibManagerProvider.getInstance().getManager();
      manager.setScript("lib77828", "function lib77828() { return 42; }");

      try {
         String assembly = sandbox.getViewsheet().getAssemblies()[0].getAbsoluteName();
         ViewsheetScope scope = sandbox.getScope();
         // the engine installs library sources when it starts
         scope.getScriptEnv().reset();
         assertEquals(42, ((Number) scope.execute("lib77828()", assembly)).intValue());
      }
      finally {
         manager.removeScript("lib77828");
      }
   }

   private static void assertRefused(Object result) {
      assertInstanceOf(String.class, result, String.valueOf(result));
      assertTrue(((String) result).startsWith("error: "), String.valueOf(result));
      assertTrue(((String) result).contains(UNKNOWN), String.valueOf(result));
   }

   private void saveAnnViewsheet() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setDescription("SECRET-ann-vs-description");
      save(AssetEntry.createAssetEntry(ANN_VS), vs, new IdentityID("annA", ORG_A));
   }

   // a private worksheet whose primary assembly is a named group
   private void saveBobWorksheet() throws Exception {
      Worksheet ws = new Worksheet();
      DefaultNamedGroupAssembly group = new DefaultNamedGroupAssembly(ws, "NG1");
      NamedGroupInfo info = new NamedGroupInfo();
      info.setGroupCondition("SECRETGROUP_bob", new ConditionList());
      group.setNamedGroupInfo(info);
      ws.addAssembly(group);
      ws.setPrimaryAssembly(group);
      save(AssetEntry.createAssetEntry(BOB_WS), ws, new IdentityID("bobB", ORG_B));
   }

   private static void save(AssetEntry entry, AbstractSheet sheet, IdentityID owner)
      throws Exception
   {
      OrganizationContextHolder.setCurrentOrgId(owner.getOrgID());

      try {
         AssetUtil.getAssetRepository(false).setSheet(entry, sheet, new XPrincipal(owner), true);
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }
   }

   // org B's worksheet, dependency row and embedded table, and a device
   private static void seedOrgB() throws Exception {
      OrganizationContextHolder.setCurrentOrgId(ORG_B);

      try {
         DependencyStorageService.getInstance().put(DEPENDENCY_KEY, new DependenciesInfo());
         Worksheet ws = new Worksheet();
         EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "E1");
         table.setEmbeddedData(new XEmbeddedTable(
            new String[] { "string" }, new Object[][] { { "col" }, { WS_SECRET } }));
         ws.addAssembly(table);
         ws.setPrimaryAssembly("E1");
         AssetUtil.getAssetRepository(false).setSheet(
            new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, WS_PATH,
                           null, ORG_B), ws, null, true);
         EmbeddedTableStorage.getInstance().writeTable(TABLE_PATH, new ByteArrayInputStream(
            "SECRET-orgB-embedded-data".getBytes(StandardCharsets.UTF_8)));
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }

      DeviceInfo device = new DeviceInfo();
      device.setId(DEVICE_ID);
      device.setName(DEVICE_ID);
      DeviceRegistry.getRegistry().setDevice(device);
   }

   private Object run(String script) throws Exception {
      return viewsheetScope.execute(
         "try { " + FUNCS + script + " } catch(e) { 'error: ' + e }",
         viewsheetScope.getVSAScriptable(ViewsheetScope.VIEWSHEET_SCRIPTABLE), false);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }

   @Configuration
   static class Beans {
      // the constructor is package private
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      @Bean
      public DeviceRegistry deviceRegistry(KeyValueStorageManager storage) {
         return new DeviceRegistry(storage);
      }
   }
}
