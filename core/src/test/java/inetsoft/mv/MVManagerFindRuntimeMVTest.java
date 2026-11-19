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
package inetsoft.mv;

import inetsoft.analytic.composition.event.CheckMissingMVEvent;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The MV options read when a viewsheet looks for the MV of a table
 * ({@link MVManager#findRuntimeMV}): {@code mv.required}, {@code mv.metadata} and
 * {@code mv.ondemand}, and the runtime MV made for an association MV (Bug #62332).
 * The manager's own lookups ({@code findMV}, {@code getSubMV}) are stubbed, so that a table has
 * the MV a case gives it; the rest of the method is the product's.
 *
 * <p>An entry marked {@code mv_creation_failed} and the table {@code __anyt__} is what a
 * viewsheet asks when an on-demand creation failed: the MV is missing and will not be made.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                 MVManagerFindRuntimeMVTest.StorageConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVManagerFindRuntimeMVTest {
   /**
    * The storage the manager's map of definitions is kept in (the lookups are stubbed), and the
    * drivers, whose query cache is off.
    */
   @Configuration
   static class StorageConfiguration {
      @Bean
      IndexedStorage indexedStorage(BlobStorageManager manager) {
         return new BlobIndexedStorage(manager);
      }

      @Bean
      Drivers drivers() {
         return mock(Drivers.class);
      }
   }

   private static final String[] PROPERTIES = {
      "mv.required", "mv.metadata", "mv.ondemand", "mv.freshness", "mv.maxAge" };

   @BeforeEach
   void setUp() {
      for(String name : PROPERTIES) {
         saved.put(name, SreeEnv.getProperty(name));
         SreeEnv.remove(name);
      }

      manager = spy(new MVManager(mock(Cluster.class)));
      doReturn(null).when(manager).findMV(any(), any(), any(), any(), any());
      doReturn(null).when(manager).findMV(any(), any(), any(), any());
      doReturn(null).when(manager).getSubMV(any(), any(), any(), any());
      doReturn(false).when(manager).containsSubMV(any(), any(), any());
      vs = new Viewsheet();
      box = mock(ViewsheetSandbox.class);
      user = new XPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()));
   }

   @AfterEach
   void tearDown() {
      for(Map.Entry<String, String> entry : saved.entrySet()) {
         if(entry.getValue() == null) {
            SreeEnv.remove(entry.getKey());
         }
         else {
            SreeEnv.setProperty(entry.getKey(), entry.getValue());
         }
      }
   }

   // ---------------------------------------------------------------- mv.required, mv.metadata

   @Test
   void requiredFailsAViewerWithTheMissingMessage() {
      SreeEnv.setProperty("mv.required", "true");
      AssetEntry entry = failedEntry();

      MessageException missing = assertThrows(MessageException.class, () ->
         manager.findRuntimeMV(entry, vs, "__anyv__", "__anyt__", user, box, true));

      assertEquals(Catalog.getCatalog().getString("vs.mv.missing"), missing.getMessage());
      assertNull(entry.getProperty("mv_ignored"));
   }

   @Test
   void notRequiredRefreshesAViewerWithLiveData() {
      SreeEnv.setProperty("mv.required", "false");
      assertRefreshesLive(failedEntry(), true);
      assertFalse(vs.getViewsheetInfo().isMetadata());
   }

   @Test
   void requiredDoesNotStopTheDesigner() {
      // mv.required is for viewers only
      SreeEnv.setProperty("mv.required", "true");
      assertRefreshesLive(failedEntry(), false);
   }

   @Test
   void metadataOffKeepsTheDesignerOnLiveData() {
      SreeEnv.setProperty("mv.metadata", "false");
      assertRefreshesLive(failedEntry(), false);
      assertFalse(vs.getViewsheetInfo().isMetadata());
   }

   @Test
   void metadataNeverSwitchesAViewer() {
      SreeEnv.setProperty("mv.metadata", "true");
      assertRefreshesLive(failedEntry(), true);
      assertFalse(vs.getViewsheetInfo().isMetadata());
   }

   @Test
   void metadataSwitchesTheDesignerToMetadataOnly() {
      SreeEnv.setProperty("mv.metadata", "true");
      AssetEntry entry = failedEntry();

      // the entry is not passed on to processMissingMV, so the designer gets the missing
      // message, not the background-creation confirmation (Bug #80878, rejected)
      MessageException missing = assertThrows(MessageException.class, () ->
         manager.findRuntimeMV(entry, vs, "__anyv__", "__anyt__", user, box, false));

      assertEquals(Catalog.getCatalog().getString("vs.mv.missing"), missing.getMessage());
      assertTrue(vs.getViewsheetInfo().isMetadata());
      assertNull(entry.getProperty("mv_ignored"), "the designer is not refreshed with live data");
   }

   @Test
   void anIgnoredEntryIsNotAskedAgain() {
      SreeEnv.setProperty("mv.required", "true");
      AssetEntry entry = failedEntry();
      entry.setProperty("mv_ignored", "true");

      assertNull(manager.findRuntimeMV(entry, vs, "__anyv__", "__anyt__", user, box, true));
   }

   // ---------------------------------------------------------------- mv.ondemand

   @Test
   void onDemandStartsTheCreationAndHoldsTheOpen() throws Exception {
      SreeEnv.setProperty("mv.ondemand", "true");
      vs.getViewsheetInfo().setMVOnDemand(true);
      AssetEntry entry = entry();

      try(MockedStatic<SecurityEngine> security = permission(true)) {
         ConfirmException progress = assertThrows(ConfirmException.class, () ->
            manager.findRuntimeMV(entry, vs, "", "Orders", user, box, true));
         assertEquals(ConfirmException.PROGRESS, progress.getLevel());
      }

      // the job runs in the background; the viewsheet has no table to make an MV for, and the
      // entry is marked so that the open is not held up again
      waitUntilIdle(entry);
      assertTrue("true".equals(entry.getProperty("mv_creation_failed")) ||
                    "true".equals(entry.getProperty("mv_ignored")),
                 "the background MV job did not run");
   }

   @Test
   void onDemandNeedsTheViewsheetOption() {
      SreeEnv.setProperty("mv.ondemand", "true");
      vs.getViewsheetInfo().setMVOnDemand(false);
      assertNoJob(entry(), true);
   }

   @Test
   void onDemandNeedsTheProperty() {
      SreeEnv.setProperty("mv.ondemand", "false");
      vs.getViewsheetInfo().setMVOnDemand(true);
      assertNoJob(entry(), true);
   }

   @Test
   void onDemandNeedsTheMaterializationPermission() {
      SreeEnv.setProperty("mv.ondemand", "true");
      vs.getViewsheetInfo().setMVOnDemand(true);
      assertNoJob(entry(), false);
   }

   @Test
   void onDemandIsNotRetriedAfterAFailure() {
      SreeEnv.setProperty("mv.ondemand", "true");
      vs.getViewsheetInfo().setMVOnDemand(true);
      AssetEntry entry = entry();
      entry.setProperty("mv_creation_failed", "true");
      assertNoJob(entry, true);
   }

   @Test
   void aNewViewsheetIsNeverMaterializedOnDemand() {
      SreeEnv.setProperty("mv.ondemand", "true");
      vs.getViewsheetInfo().setMVOnDemand(true);
      AssetEntry entry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "Untitled-1", null);
      assertNoJob(entry, true);
   }

   // ---------------------------------------------------------------- the runtime MV

   @Test
   void anMVOfTheTableIsPhysical() {
      MVDef def = def("mv-orders", false);
      doReturn(def).when(manager).findMV(any(), eq("Orders"), any(), any(), any());

      RuntimeMV rmv = manager.findRuntimeMV(entry(), null, "", "Orders", user, box, true, true);

      assertTrue(rmv.isPhysical());
      assertEquals("mv-orders", rmv.getMV());
      verify(manager, never()).getSubMV(any(), any(), any(), any());
   }

   @Test
   void aSubMVMarksTheTableWithoutAnMV() {
      doReturn(def("mv-sub", false)).when(manager).getSubMV(any(), eq("Mirror1"), any(), any());

      RuntimeMV rmv = manager.findRuntimeMV(entry(), null, "", "Mirror1", user, box, true, true);

      // the MVs are on the tables beneath this one
      assertNotNull(rmv);
      assertFalse(rmv.isPhysical());
   }

   @Test
   void anAssociationSubMVIsUsedAsAPhysicalMV() {
      // Bug #62332: the association MV of a sub-MV table is the cache of that table
      doReturn(def("mv-association", true)).when(manager)
         .getSubMV(any(), eq("V_Mirror1"), any(), any());

      RuntimeMV rmv = manager.findRuntimeMV(entry(), null, "", "V_Mirror1", user, box, true,
                                            true);

      assertTrue(rmv.isPhysical());
      assertEquals("mv-association", rmv.getMV());
   }

   @Test
   void anMVThatIsNotCreatedIsNotUsed() {
      MVDef def = def("mv-orders", false);
      when(def.isSuccess()).thenReturn(false);
      doReturn(def).when(manager).findMV(any(), eq("Orders"), any(), any(), any());
      MVDef sub = def("mv-sub", true);
      when(sub.isSuccess()).thenReturn(false);
      doReturn(sub).when(manager).getSubMV(any(), eq("Mirror1"), any(), any());

      assertNull(manager.findRuntimeMV(entry(), null, "", "Orders", user, box, true, true));
      assertNull(manager.findRuntimeMV(entry(), null, "", "Mirror1", user, box, true, true));
   }

   // ---------------------------------------------------------------- the pending job

   @Test
   void cancellingAPendingJobStopsIt() throws Exception {
      AssetEntry entry = entry();
      MVCreator job = mock(MVCreator.class);
      Field field = MVManager.class.getDeclaredField("pending");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<Object, MVCreator> pending = (Map<Object, MVCreator>) field.get(manager);
      pending.put(entry + ":" + user, job);
      XPrincipal other = new XPrincipal(
         new IdentityID("other", Organization.getDefaultOrganizationID()));

      // the job is per user
      assertTrue(manager.isPending(entry, user));
      assertFalse(manager.isPending(entry, other));
      manager.cancelMV(entry, other);
      verify(job, never()).cancel();

      manager.cancelMV(entry, user);
      verify(job).cancel();
      assertFalse(manager.isPending(entry, user));
   }

   // ---------------------------------------------------------------- helpers

   private void assertRefreshesLive(AssetEntry entry, boolean runtime) {
      ConfirmException refresh = assertThrows(ConfirmException.class, () ->
         manager.findRuntimeMV(entry, vs, "__anyv__", "__anyt__", user, box, runtime));
      assertEquals(ConfirmException.PROGRESS, refresh.getLevel());
      assertInstanceOf(CheckMissingMVEvent.class, refresh.getEvent());
      assertEquals("true", entry.getProperty("mv_ignored"));
   }

   private void assertNoJob(AssetEntry entry, boolean permitted) {
      try(MockedStatic<SecurityEngine> security = permission(permitted)) {
         assertNull(manager.findRuntimeMV(entry, vs, "", "Orders", user, box, true));
      }

      assertFalse(manager.isPending(entry, user));
      assertNotEquals("true", entry.getProperty("mv_ignored"));
   }

   /** The security engine, with or without the access to the Materialized Views resource. */
   private MockedStatic<SecurityEngine> permission(boolean permitted) {
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.checkPermission(any(), eq(ResourceType.MATERIALIZATION), eq("*"),
                                    eq(ResourceAction.ACCESS))).thenReturn(permitted);
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.getSecurityProvider()).thenReturn(provider);
      MockedStatic<SecurityEngine> security = mockStatic(SecurityEngine.class);
      security.when(SecurityEngine::getSecurity).thenReturn(engine);
      return security;
   }

   private void waitUntilIdle(AssetEntry entry) throws InterruptedException {
      long deadline = System.currentTimeMillis() + 30000L;

      while(manager.isPending(entry, user) && System.currentTimeMillis() < deadline) {
         Thread.sleep(20L);
      }

      assertFalse(manager.isPending(entry, user));
   }

   private static AssetEntry entry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "mvtest/Orders", null);
   }

   private static AssetEntry failedEntry() {
      AssetEntry entry = entry();
      entry.setProperty("mv_creation_failed", "true");
      return entry;
   }

   private static MVDef def(String name, boolean association) {
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn(name);
      when(def.isSuccess()).thenReturn(true);
      when(def.isAssociationMV()).thenReturn(association);
      return def;
   }

   private final Map<String, String> saved = new HashMap<>();
   private MVManager manager;
   private Viewsheet vs;
   private ViewsheetSandbox box;
   private XPrincipal user;
}
