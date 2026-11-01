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
package inetsoft.web.admin.sheet.vs;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.admin.content.repository.RepletRegistryService;
import inetsoft.web.admin.sheet.Sheet;
import inetsoft.web.admin.sheet.SheetList;
import inetsoft.web.security.auth.ResourceExistsException;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.StaticApplicationContext;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the viewsheet management logic that was extracted out of the enterprise
 * {@code ViewsheetApiService} so it can also be used, org-agnostically, by the community
 * admin-ai plugin.
 */
@Tag("core")
class ViewsheetServiceTest {
   @BeforeEach
   void setUp() {
      assetRepository = mock(AssetRepository.class);
      repletRegistryService = mock(RepletRegistryService.class);
      repletRegistryManager = mock(RepletRegistryManager.class);
      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      when(orgManager.getCurrentOrgID(any(Principal.class))).thenReturn("host-org");

      // SRPrincipal's constructor calls XSessionService.getService().createSessionID(), which
      // otherwise requires a real Spring bean.
      xSessionServiceStatic = mockStatic(XSessionService.class);
      xSessionServiceStatic.when(XSessionService::getService).thenReturn(mock(XSessionService.class));

      // SUtil.getActionRecord() (addFolder/removeFolder) and SUtil.isDefaultVSGloballyVisible()
      // (getViewsheets) both resolve properties/security state through Spring, which otherwise
      // requires a real application context.
      context = new StaticApplicationContext();
      context.getBeanFactory().registerSingleton("propertiesEngine", mock(PropertiesEngine.class));
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      context.getBeanFactory().registerSingleton("securityEngine", securityEngine);
      context.refresh();
      ConfigurationContext.getContext().setApplicationContext(context);

      service = new ViewsheetService(assetRepository, repletRegistryService, repletRegistryManager);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      xSessionServiceStatic.close();

      if(context != null) {
         context.close();
      }

      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @Test
   void getViewsheetsUsesCallersCurrentOrganization() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      when(assetRepository.getAllEntries(
         any(AssetEntry.class), any(Principal.class), any(ResourceAction.class),
         any(AssetEntry.Selector.class)))
         .thenReturn(new AssetEntry[0]);

      SheetList list = service.getViewsheets(principal);

      assertNotNull(list.getSheets());
      assertTrue(list.getSheets().isEmpty());
      verify(orgManager).getCurrentOrgID(principal);
   }

   @Test
   void addFolderRejectsOwnerInAnotherOrganizationForNonSiteAdmin() {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      IdentityID otherOrgOwner = new IdentityID("user2", "other-org");
      when(orgManager.isSiteAdmin((Principal) any())).thenReturn(false);

      assertThrows(UnauthorizedAccessException.class, () ->
         service.addFolder("/", "folder", otherOrgOwner, principal));
   }

   @Test
   void removeFolderRejectsRootFolder() {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      when(orgManager.isSiteAdmin((Principal) any())).thenReturn(true);

      assertThrows(IllegalArgumentException.class, () ->
         service.removeFolder("/", null, principal));
   }

   // Bug #79257: renaming through the service built a bare target entry, so the alias and the
   // "description" property of the sheet were silently reset.
   @Test
   void renameViewsheetKeepsAliasAndDescription() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      AssetEntry oldEntry = stubStoredEntry("AliasX", "DescX", "original");

      service.renameViewsheet(oldEntry.toIdentifier(), "renamed", true, null, principal);

      AssetEntry newEntry = captureRenamedEntry();
      assertEquals("AliasX", newEntry.getAlias());
      assertEquals("DescX", newEntry.getProperty("description"));
   }

   @Test
   void renameViewsheetWithBlankAliasLeavesAliasEmpty() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      AssetEntry oldEntry = stubStoredEntry("  ", "DescX", "original");

      service.renameViewsheet(oldEntry.toIdentifier(), "renamed", true, null, principal);

      AssetEntry newEntry = captureRenamedEntry();
      assertNull(newEntry.getAlias());
      assertEquals("DescX", newEntry.getProperty("description"));
   }

   // The rename rollback calls renameViewsheet again with the original path, so it restores
   // alias and description through the same carry-over.
   @Test
   void renameViewsheetBackKeepsAliasAndDescription() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      AssetEntry renamed = stubStoredEntry("AliasX", "DescX", "renamed");

      service.renameViewsheet(renamed.toIdentifier(), "original", true, null, principal);

      AssetEntry back = captureRenamedEntry();
      assertEquals("original", back.getPath());
      assertEquals("AliasX", back.getAlias());
      assertEquals("DescX", back.getProperty("description"));
   }

   // Bug #79898: changeSheet() silently overwrites whatever sits at the destination, so the
   // service must refuse a rename onto an occupied path before calling it.
   @Test
   void renameViewsheetOntoExistingPathIsRefusedAndDoesNotOverwrite() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      AssetEntry oldEntry = stubStoredEntry("AliasX", "DescX", "original");
      when(assetRepository.containsEntry(argThat(e -> e != null && e.getType() == AssetEntry.Type.VIEWSHEET
         && "taken".equals(e.getPath()) && "host-org".equals(e.getOrgID())))).thenReturn(true);

      assertThrows(ResourceExistsException.class, () ->
         service.renameViewsheet(oldEntry.toIdentifier(), "taken", true, null, principal));
      verify(assetRepository, never()).changeSheet(
         any(AssetEntry.class), any(AssetEntry.class), any(Principal.class), anyBoolean(),
         anyBoolean(), anyBoolean());
   }

   @Test
   void renameViewsheetOntoAFolderPathIsRefused() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      AssetEntry oldEntry = stubStoredEntry("AliasX", "DescX", "original");
      when(assetRepository.containsEntry(argThat(e -> e != null && e.getType() == AssetEntry.Type.REPOSITORY_FOLDER
         && "taken".equals(e.getPath())))).thenReturn(true);

      assertTrue(service.isRenameTargetTaken(oldEntry.toIdentifier(), "taken", true, null, principal));
   }

   @Test
   void renameViewsheetProbeUsesTheSourceOrganizationNotTheCallers() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      AssetEntry oldEntry = AssetEntry.createAssetEntry(
         "1^128^__NULL__^original^other-org");
      when(assetRepository.getAssetEntry(any(AssetEntry.class))).thenReturn(oldEntry);
      when(assetRepository.containsEntry(argThat(e -> e != null && "taken".equals(e.getPath())
         && "other-org".equals(e.getOrgID())))).thenReturn(true);

      assertTrue(service.isRenameTargetTaken(oldEntry.toIdentifier(), "taken", true, null, principal));
   }

   @Test
   void renameViewsheetToItsOwnCurrentPathIsNotACollision() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      AssetEntry oldEntry = stubStoredEntry("AliasX", "DescX", "original");
      when(assetRepository.containsEntry(any(AssetEntry.class))).thenReturn(true);

      assertFalse(service.isRenameTargetTaken(oldEntry.toIdentifier(), "original", true, null, principal));
      service.renameViewsheet(oldEntry.toIdentifier(), "original", true, null, principal);
      verify(assetRepository).changeSheet(
         any(AssetEntry.class), any(AssetEntry.class), any(Principal.class), eq(true), eq(true),
         eq(false));
   }

   // Bug #79898: the probe must look in the owner's user scope, not the global one.
   @Test
   void renameViewsheetOntoExistingPathInUserScopeIsRefused() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      IdentityID owner = new IdentityID("bob", "host-org");
      AssetEntry oldEntry = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, "original", owner, "host-org");
      when(assetRepository.getAssetEntry(any(AssetEntry.class))).thenReturn(oldEntry);
      when(assetRepository.containsEntry(argThat(e -> e != null && e.getType() == AssetEntry.Type.VIEWSHEET
         && e.getScope() == AssetRepository.USER_SCOPE && owner.equals(e.getUser())
         && "taken".equals(e.getPath())))).thenReturn(true);

      assertTrue(service.isRenameTargetTaken(
         oldEntry.toIdentifier(), "taken", false, owner, principal));
      assertThrows(ResourceExistsException.class, () ->
         service.renameViewsheet(oldEntry.toIdentifier(), "taken", false, owner, principal));
      verify(assetRepository, never()).changeSheet(
         any(AssetEntry.class), any(AssetEntry.class), any(Principal.class), anyBoolean(),
         anyBoolean(), anyBoolean());
      // a global-scope sheet at the same path is a different namespace
      assertFalse(service.isRenameTargetTaken(
         oldEntry.toIdentifier(), "free", false, owner, principal));
   }

   private AssetEntry stubStoredEntry(String alias, String description, String path)
      throws Exception
   {
      AssetEntry requested = AssetEntry.createAssetEntry(
         "1^128^__NULL__^" + path + "^host-org");
      AssetEntry stored = AssetEntry.createAssetEntry(requested.toIdentifier());
      stored.setAlias(alias);
      stored.setProperty("description", description);
      when(assetRepository.getAssetEntry(any(AssetEntry.class))).thenReturn(stored);
      return requested;
   }

   private AssetEntry captureRenamedEntry() throws Exception {
      ArgumentCaptor<AssetEntry> captor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(assetRepository).changeSheet(
         any(AssetEntry.class), captor.capture(), any(Principal.class), eq(true), eq(true),
         eq(false));
      return captor.getValue();
   }

   private static SRPrincipal createPrincipal(String name, String orgId) {
      IdentityID id = new IdentityID(name, orgId);
      return new SRPrincipal(id, new IdentityID[0], new String[0], orgId, 0L);
   }

   private AssetRepository assetRepository;
   private RepletRegistryService repletRegistryService;
   private RepletRegistryManager repletRegistryManager;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<XSessionService> xSessionServiceStatic;
   private StaticApplicationContext context;
   private ViewsheetService service;
}
