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

import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.admin.content.repository.RepletRegistryService;
import inetsoft.web.admin.sheet.SheetList;
import inetsoft.web.security.auth.MissingResourceException;
import org.junit.jupiter.api.*;
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

      context = new StaticApplicationContext();
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
