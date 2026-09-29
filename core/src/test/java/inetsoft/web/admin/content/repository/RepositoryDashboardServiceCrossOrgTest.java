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
package inetsoft.web.admin.content.repository;

import inetsoft.report.internal.Util;
import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.model.RepositoryDashboardSettingsModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77258: the EM dashboard settings must not let a caller bind, and later delete, a
 * viewsheet the caller cannot access (e.g. another org's user viewsheet).
 */
@Tag("core")
class RepositoryDashboardServiceCrossOrgTest {
   private static final IdentityID ALICE = new IdentityID("alice", "orgA");
   private static final IdentityID BOB = new IdentityID("bob", "orgB");
   private static final String DASH = "myDash";

   private final AssetEntry aliceVs = new AssetEntry(
      AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, "aliceVs", ALICE, "orgA");
   private final AssetEntry aliceVs2 = new AssetEntry(
      AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, "aliceVs2", ALICE, "orgA");
   private final AssetEntry bobVs = new AssetEntry(
      AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, "bobComposedDash", BOB, "orgB");

   private MockedStatic<SUtil> sutil;
   private MockedStatic<Audit> audit;
   private MockedStatic<AssetUtil> assetUtil;
   private MockedStatic<Util> util;

   private AssetRepository engine;
   private DashboardRegistry registry;
   private RepositoryDashboardService service;
   private Principal alice;
   private VSDashboard stored;

   @BeforeEach
   void setUp() throws Exception {
      sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenReturn(mock(ActionRecord.class));
      audit = mockStatic(Audit.class);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      engine = mock(AssetRepository.class);
      assetUtil = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(engine);
      util = mockStatic(Util.class, CALLS_REAL_METHODS);
      util.when(() -> Util.getObjectFullPath(anyInt(), any(), any(), any())).thenReturn("x");

      alice = mock(Principal.class);
      when(alice.getName()).thenReturn(ALICE.convertToKey());

      // like the real engine, alice has no access to bob's (other org) viewsheet
      doThrow(new MessageException("no read")).when(engine)
         .checkAssetPermission(same(alice), eq(bobVs), any(ResourceAction.class));
      doThrow(new MessageException("no read")).when(engine)
         .checkAssetPermission(same(alice), eq(bobVs), any(ResourceAction.class), anyBoolean());

      // every viewsheet is a composed dashboard, so removeDashboardViewsheet would delete it
      Viewsheet composed = mock(Viewsheet.class);
      ViewsheetInfo info = mock(ViewsheetInfo.class);
      when(info.isComposedDashboard()).thenReturn(true);
      when(composed.getViewsheetInfo()).thenReturn(info);
      when(engine.getSheet(any(AssetEntry.class), any(), anyBoolean(), any()))
         .thenReturn(composed);

      stored = dashboard(aliceVs);

      registry = mock(DashboardRegistry.class);
      when(registry.getDashboard(DASH)).thenAnswer(inv -> stored);
      doAnswer(inv -> {
         stored = inv.getArgument(1);
         return null;
      }).when(registry).addDashboard(eq(DASH), any());

      DashboardRegistryManager registryManager = mock(DashboardRegistryManager.class);
      when(registryManager.getRegistry(ALICE)).thenReturn(registry);

      DashboardManager dashboardManager = mock(DashboardManager.class);
      when(dashboardManager.getDashboards(any(Identity.class))).thenReturn(new String[0]);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      service = new RepositoryDashboardService(
         mock(ResourcePermissionService.class), mock(SecurityProvider.class),
         mock(ContentRepositoryTreeService.class), dashboardManager, securityEngine,
         mock(DependencyHandler.class), registryManager, mock(RenameTransformHandler.class));
   }

   @AfterEach
   void tearDown() {
      util.close();
      assetUtil.close();
      audit.close();
      sutil.close();
   }

   @Test
   void setSettings_bindUnreadableViewsheet_isRejected() {
      assertThrows(MessageException.class,
                   () -> service.setSettings(DASH, model(bobVs), ALICE, alice));
      assertEquals(aliceVs.toIdentifier(), stored.getViewsheet().getIdentifier());
   }

   @Test
   void setSettings_rebind_removesOldViewsheetAsCaller() throws Exception {
      // a dashboard already bound to bob's viewsheet (e.g. persisted before this fix)
      stored = dashboard(bobVs);

      try {
         service.setSettings(DASH, model(aliceVs2), ALICE, alice);
      }
      catch(Exception ignore) {
         // unrelated collaborators downstream of the removal are not mocked
      }

      verify(engine).checkAssetPermission(alice, aliceVs2, ResourceAction.READ);
      verify(engine).getSheet(eq(bobVs), same(alice), eq(false), any());
      verify(engine, never()).removeSheet(eq(bobVs), argThat(p -> p != alice), anyBoolean());
   }

   @Test
   void setSettings_unchangedViewsheet_skipsReadCheck() throws Exception {
      try {
         service.setSettings(DASH, model(aliceVs), ALICE, alice);
      }
      catch(Exception ignore) {
      }

      verify(engine, never()).checkAssetPermission(any(), eq(aliceVs), any(ResourceAction.class));
      verify(engine, never()).removeSheet(any(), any(), anyBoolean());
   }

   @Test
   void delete_removesViewsheetAsCaller() throws Exception {
      stored = dashboard(bobVs);

      service.delete(DASH, ALICE, alice);

      verify(engine).getSheet(eq(bobVs), same(alice), eq(false), any());
      verify(engine).removeSheet(eq(bobVs), same(alice), eq(false));
   }

   private static VSDashboard dashboard(AssetEntry vs) {
      VSDashboard dashboard = new VSDashboard();
      ViewsheetEntry ve = new ViewsheetEntry(vs.getPath(), vs.getUser());
      ve.setIdentifier(vs.toIdentifier());
      dashboard.setViewsheet(ve);
      return dashboard;
   }

   private static RepositoryDashboardSettingsModel model(AssetEntry vs) {
      return RepositoryDashboardSettingsModel.builder()
         .name(DASH)
         .oname(DASH)
         .viewsheet(vs.toIdentifier())
         .enable(true)
         .visible(true)
         .build();
   }
}
