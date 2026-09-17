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
package inetsoft.web.admin.ai.viewsheet;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.RepletRegistryService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.context.support.StaticApplicationContext;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for Track B (Redmine #76604): {@code ViewsheetService.updateMetadata}/
 * {@code WorksheetService.updateMetadata} both delegate straight to {@code RepletRegistryService
 * .updateSheet(id, null, null, alias, description, principal)} on the theory that {@code
 * newName == null} short-circuits its rename block, making an alias/description-only update a
 * zero-rename-risk field-level write (03-reconcile.md's central, previously-unverified finding
 * for this track -- no test exercised the real {@code updateSheet} body before this one). This
 * test runs the real method body (not a mock of it) and asserts the asset's identifier/path is
 * byte-for-byte unchanged after the call, for both a viewsheet and a worksheet.
 */
@Tag("core")
class RepletRegistryServiceUpdateSheetRenameSafetyTest {
   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      // SRPrincipal's constructor calls XSessionService.getService().createSessionID(), which
      // requires a Spring context; mock it to avoid that dependency (same as ViewsheetApiServiceTest).
      XSessionService mockSessionService = mock(XSessionService.class);
      when(mockSessionService.createSessionID(anyString(), any())).thenReturn("session-id");
      xSessionServiceStatic = mockStatic(XSessionService.class);
      xSessionServiceStatic.when(XSessionService::getService).thenReturn(mockSessionService);

      // ActionRecord's constructor resolves the local host via SreeEnv -> PropertiesEngine,
      // which requires a Spring context (same as ViewsheetApiServiceTest).
      context = new StaticApplicationContext();
      context.getBeanFactory().registerSingleton("propertiesEngine", mock(PropertiesEngine.class));
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      context.getBeanFactory().registerSingleton("securityEngine", securityEngine);
      context.refresh();
      ConfigurationContext.getContext().setApplicationContext(context);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      assetRepository = mock(AssetRepository.class);
      assetUtilStatic = mockStatic(AssetUtil.class);
      assetUtilStatic.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(assetRepository);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));

      service = new RepletRegistryService(
         mock(SecurityEngine.class), mock(ScheduleManager.class), mock(DependencyHandler.class),
         mock(RenameTransformHandler.class), mock(RepletRegistryManager.class));
      principal = new SRPrincipal(
         new IdentityID("user1", "organization0"), new IdentityID[0], new String[0],
         "organization0", 1L);
   }

   @AfterEach
   void tearDown() {
      assetUtilStatic.close();
      auditStatic.close();
      orgManagerStatic.close();
      xSessionServiceStatic.close();

      if(context != null) {
         context.close();
      }

      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @Test
   void updateSheetLeavesViewsheetIdentifierUnchangedWhenOnlyAliasIsUpdated() throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "Examples/Census", null);
      String identifier = entry.toIdentifier();
      stubLookup(entry);
      Viewsheet vs = mock(Viewsheet.class);
      ViewsheetInfo vsInfo = mock(ViewsheetInfo.class);
      when(vs.getViewsheetInfo()).thenReturn(vsInfo);
      when(assetRepository.getSheet(entry, principal, false, AssetContent.ALL)).thenReturn(vs);

      AssetEntry updated = service.updateSheet(
         identifier, null, null, "New Alias", "New Description", principal);

      assertEquals(identifier, updated.toIdentifier());
      assertEquals("New Alias", updated.getAlias());
      verifyNoRename();
   }

   @Test
   void updateSheetLeavesWorksheetIdentifierUnchangedWhenOnlyAliasIsUpdated() throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "Examples/CensusData", null);
      String identifier = entry.toIdentifier();
      stubLookup(entry);
      Worksheet ws = mock(Worksheet.class);
      when(assetRepository.getSheet(entry, principal, false, AssetContent.ALL)).thenReturn(ws);

      AssetEntry updated = service.updateSheet(
         identifier, null, null, "New Alias", "New Description", principal);

      assertEquals(identifier, updated.toIdentifier());
      assertEquals("New Alias", updated.getAlias());
      assertEquals("New Description", updated.getProperty("description"));
      verifyNoRename();
   }

   private void stubLookup(AssetEntry entry) throws Exception {
      AssetEntry parent = entry.getParent();
      when(assetRepository.getEntries(
         eq(parent), eq(principal), any(), any(AssetEntry.Selector.class)))
         .thenReturn(new AssetEntry[] { entry });
   }

   private void verifyNoRename() throws Exception {
      verify(assetRepository, never()).changeSheet(
         any(AssetEntry.class), any(AssetEntry.class), any(Principal.class),
         anyBoolean(), anyBoolean(), anyBoolean());
      verify(assetRepository, never()).changeSheet(
         any(AssetEntry.class), any(AssetEntry.class), any(Principal.class),
         anyBoolean(), anyBoolean());
      verify(assetRepository, never()).changeSheet(
         any(AssetEntry.class), any(AssetEntry.class), any(Principal.class), anyBoolean());
   }

   private AssetRepository assetRepository;
   private RepletRegistryService service;
   private Principal principal;
   private StaticApplicationContext context;
   private MockedStatic<AssetUtil> assetUtilStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<XSessionService> xSessionServiceStatic;
}
