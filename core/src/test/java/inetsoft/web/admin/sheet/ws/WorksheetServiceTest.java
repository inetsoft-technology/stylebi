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
package inetsoft.web.admin.sheet.ws;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.admin.content.repository.RepletRegistryService;
import inetsoft.web.admin.sheet.SheetList;
import inetsoft.web.security.auth.MissingResourceException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.support.StaticApplicationContext;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the worksheet management logic that was extracted out of the enterprise
 * {@code WorksheetApiService} so it can also be used, org-agnostically, by the community
 * admin-ai plugin.
 */
@Tag("core")
class WorksheetServiceTest {
   @BeforeEach
   void setUp() {
      assetRepository = mock(AssetRepository.class);
      repletRegistryService = mock(RepletRegistryService.class);
      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // SRPrincipal's constructor calls XSessionService.getService().createSessionID(), which
      // otherwise requires a real Spring bean.
      xSessionServiceStatic = mockStatic(XSessionService.class);
      xSessionServiceStatic.when(XSessionService::getService).thenReturn(mock(XSessionService.class));

      // ActionRecord (renameWorksheet) resolves the host name and security state through
      // Spring, which otherwise requires a real application context.
      context = new StaticApplicationContext();
      context.getBeanFactory().registerSingleton("propertiesEngine", mock(PropertiesEngine.class));
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      context.getBeanFactory().registerSingleton("securityEngine", securityEngine);
      context.refresh();
      ConfigurationContext.getContext().setApplicationContext(context);

      service = new WorksheetService(assetRepository, repletRegistryService);
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
   void getWorksheetsReturnsEmptyListWhenNoEntries() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      when(assetRepository.getAllEntries(
         any(AssetEntry.class), any(Principal.class), any(ResourceAction.class),
         any(AssetEntry.Selector.class)))
         .thenReturn(new AssetEntry[0]);

      SheetList list = service.getWorksheets(principal);

      assertNotNull(list.getSheets());
      assertTrue(list.getSheets().isEmpty());
   }

   @Test
   void getWorksheetSettingsMetadataThrowsWhenAssetMissing() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      String identifier = "1^2^__NULL__^/some/worksheet^host-org";
      when(assetRepository.getAssetEntry(any(AssetEntry.class))).thenReturn(null);

      assertThrows(MissingResourceException.class, () ->
         service.getWorksheetSettingsMetadata(identifier, principal));
   }

   // Bug #79257: renaming through the service built a bare target entry, so the alias and the
   // "description" property of the sheet were silently reset.
   @Test
   void renameWorksheetKeepsAliasAndDescription() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      AssetEntry oldEntry = stubStoredEntry("AliasX", "DescX", "original");

      service.renameWorksheet(oldEntry.toIdentifier(), "renamed", true, null, principal);

      AssetEntry newEntry = captureRenamedEntry();
      assertEquals("AliasX", newEntry.getAlias());
      assertEquals("DescX", newEntry.getProperty("description"));
   }

   @Test
   void renameWorksheetWithBlankAliasLeavesAliasEmpty() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      AssetEntry oldEntry = stubStoredEntry("  ", "DescX", "original");

      service.renameWorksheet(oldEntry.toIdentifier(), "renamed", true, null, principal);

      AssetEntry newEntry = captureRenamedEntry();
      assertNull(newEntry.getAlias());
      assertEquals("DescX", newEntry.getProperty("description"));
   }

   // The rename rollback calls renameWorksheet again with the original path, so it restores
   // alias and description through the same carry-over.
   @Test
   void renameWorksheetBackKeepsAliasAndDescription() throws Exception {
      SRPrincipal principal = createPrincipal("user1", "host-org");
      AssetEntry renamed = stubStoredEntry("AliasX", "DescX", "renamed");

      service.renameWorksheet(renamed.toIdentifier(), "original", true, null, principal);

      AssetEntry back = captureRenamedEntry();
      assertEquals("original", back.getPath());
      assertEquals("AliasX", back.getAlias());
      assertEquals("DescX", back.getProperty("description"));
   }

   private AssetEntry stubStoredEntry(String alias, String description, String path)
      throws Exception
   {
      AssetEntry requested = AssetEntry.createAssetEntry(
         "1^2^__NULL__^" + path + "^host-org");
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
      return new SRPrincipal(
         new IdentityID(name, orgId), new IdentityID[0], new String[0], orgId, 1L);
   }

   private AssetRepository assetRepository;
   private RepletRegistryService repletRegistryService;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<XSessionService> xSessionServiceStatic;
   private StaticApplicationContext context;
   private WorksheetService service;
}
