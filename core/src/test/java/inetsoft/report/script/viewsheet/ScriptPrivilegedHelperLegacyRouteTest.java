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

import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.uql.asset.sync.DependenciesInfo;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.viewsheet.BookmarkLockManager;
import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.util.ThreadContext;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Java-side storage singletons can't be reached through the legacy package route
 * (no Java.type), or through the ReportLayoutTool subclass, and a script can't use them to
 * release another user's existing bookmark lock or to write its own org's library with no
 * SCRIPT permission check. (Bug #77828)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  ScriptPrivilegedHelperAccessTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class ScriptPrivilegedHelperLegacyRouteTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String ORG_A = "orgA77828v";
   private static final String ORG_B = "orgB77828v";
   private static final String BOB = "bobB~;~" + ORG_B;
   private static final String DEPENDENCY_KEY = "key77828v";
   private static final String TABLE_PATH = "t77828v/table";
   private static final String DEVICE_ID = "device77828v";
   private static final String LOCK_KEY = "lock77828v";
   private static final String UNKNOWN = "Unknown identifier";

   private SecurityTestDataBuilder builder;
   private ViewsheetScope viewsheetScope;
   private String oldProvider;
   private Principal savedContextPrincipal;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      viewsheetScope = new ViewsheetScope(rvs.getViewsheetSandbox().orElseThrow(), false);
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      savedPrincipal = ThreadContext.getPrincipal();

      oldProvider = SreeEnv.getProperty("security.provider");
      SreeEnv.setProperty("security.provider", "file");
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_A, ORG_A)
         .addOrg(ORG_B, ORG_B)
         .addUser("aliceA", ORG_A, "password")
         .addUser("bobB", ORG_B, "password")
         .setup();

      OrganizationContextHolder.setCurrentOrgId(ORG_B);

      try {
         DependencyStorageService.getInstance().put(DEPENDENCY_KEY, new DependenciesInfo());
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
      // bob holds an edit lock on one of his bookmarks
      BookmarkLockManager.getManager().lock(LOCK_KEY, BOB, "rtB");

      SRPrincipal alice = builder.principalOf("aliceA", ORG_A);
      ThreadContext.setContextPrincipal(alice);
      ThreadContext.setPrincipal(alice);
   }

   @AfterEach
   void tearDown() {
      BookmarkLockManager.getManager().unlockAll(BOB, "rtB");
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
      OrganizationContextHolder.setCurrentOrgId(null);
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);

      if(builder != null) {
         builder.teardown();
      }
   }

   @Test
   void libManagerLegacyRouteRefused() throws Exception {
      assertRefused(run("var m = inetsoft.report.LibManagerProvider.getInstance()" +
                           ".getManager('" + ORG_B + "');" +
                           "m.setScript('f77828v', 'function f77828v() { return 666; }');" +
                           "m.save(); 'saved'"));

      LibManagerProvider.getInstance().clear();
      assertNull(LibManagerProvider.getInstance().getManager(ORG_B).getScript("f77828v"));
   }

   /** Writing the caller's own org library from script skips the SCRIPT write check. */
   @Test
   void ownOrgLibraryWriteRefused() throws Exception {
      assertRefused(run("var m = Java.type('inetsoft.report.LibManagerProvider').getInstance()" +
                           ".getManager(); m.setScript('own77828v', 'function own77828v() {}');" +
                           "'saved'"));
      assertNull(LibManagerProvider.getInstance().getManager(ORG_A).getScript("own77828v"));
   }

   @Test
   void embeddedTableLegacyRouteRefused() throws Exception {
      String storage = "inetsoft.uql.asset.EmbeddedTableStorage.getInstance()";
      Object list = run("'' + " + storage + ".listBlobs('" + ORG_B + "')");
      Object read = run("var s = " + storage + ".readTable('" + TABLE_PATH + "', '" + ORG_B +
                           "'); var b = ''; var c; while((c = s.read()) >= 0) {" +
                           " b += String.fromCharCode(c); } b");

      assertRefused(list);
      assertRefused(read);
      assertFalse(String.valueOf(read).contains("SECRET"), String.valueOf(read));
      assertRefused(run(storage + ".removeTable('" + TABLE_PATH + "', '" + ORG_B +
                           "'); 'removed'"));
      assertTrue(EmbeddedTableStorage.getInstance().tableExists(TABLE_PATH, ORG_B));
   }

   @Test
   void dependencyStorageLegacyRouteRefused() throws Exception {
      String service = "inetsoft.uql.asset.sync.DependencyStorageService.getInstance()";
      assertRefused(run("'' + " + service + ".getWithOrg('" + DEPENDENCY_KEY + "', '" +
                           ORG_B + "')"));
      assertRefused(run(service + ".removeDependencyStorage('" + ORG_B + "'); 'removed'"));
      assertNotNull(DependencyStorageService.getInstance().getWithOrg(DEPENDENCY_KEY, ORG_B));
   }

   @Test
   void deviceRegistryLegacyRouteRefused() throws Exception {
      assertRefused(run("inetsoft.uql.viewsheet.vslayout.DeviceRegistry.getRegistry()" +
                           ".deleteDevice('" + DEVICE_ID + "'); 'deleted'"));
      assertNotNull(DeviceRegistry.getRegistry().getDevice(DEVICE_ID));
   }

   /** Another user's existing lock can't be released, on either route. */
   @Test
   void otherUsersLockKept() throws Exception {
      String unlock = ".getManager().unlockAll('" + BOB + "', 'rtB'); 'unlocked'";
      assertRefused(run("Java.type('inetsoft.uql.viewsheet.BookmarkLockManager')" + unlock));
      assertRefused(run("inetsoft.uql.viewsheet.BookmarkLockManager" + unlock));
      assertEquals(BOB, BookmarkLockManager.getManager().getLockedBookmarkUser(LOCK_KEY, "x"));
   }

   @Test
   void assetDataCacheLegacyRouteRefused() throws Exception {
      assertRefused(run("'' + inetsoft.report.composition.execution.AssetDataCache" +
                           ".getCache().getLocalEntries().size()"));
   }

   /** ReportLayoutTool inherits the LayoutTool deny; DependencyTool on the legacy route. */
   @Test
   void assetReadersRefusedOnOtherRoutes() throws Exception {
      String call = ".getNamedGroupAssembly(Java.type('inetsoft.uql.asset.AssetEntry')" +
         ".createAssetEntry('4^2^" + BOB + "^BobWs^" + ORG_B + "'))";
      assertRefused(run("'' + Java.type('inetsoft.report.internal.table.ReportLayoutTool')" +
                           call));
      assertRefused(run("'' + inetsoft.uql.asset.sync.DependencyTool.getAssetElement(" +
                           "Java.type('inetsoft.uql.asset.AssetEntry').createAssetEntry(" +
                           "'4^2^" + BOB + "^BobWs^" + ORG_B + "'))"));
   }

   private static void assertRefused(Object result) {
      assertInstanceOf(String.class, result, String.valueOf(result));
      assertTrue(((String) result).startsWith("error: "), String.valueOf(result));
      assertTrue(((String) result).contains(UNKNOWN), String.valueOf(result));
   }

   private Object run(String script) throws Exception {
      return viewsheetScope.execute(
         "try { " + script + " } catch(e) { 'error: ' + e }",
         viewsheetScope.getVSAScriptable(ViewsheetScope.VIEWSHEET_SCRIPTABLE), false);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }
}
