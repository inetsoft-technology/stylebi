/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

/*
 * Test strategy
 *
 * RepositoryFolderController has two endpoints:
 *
 *   GET /api/em/content/repository/folder/ (getRepositoryFolderSetting)
 *     — validates current org via OrganizationManager + SecurityProvider; throws
 *       InvalidOrgException when org is null; parses owner key to IdentityID; delegates
 *       to repositoryFolderService.getSettings() on success.
 *
 *   POST /api/em/content/repository/edit/folder (setRepositoryFolderSettings)
 *     — pure delegation: parses owner key to IdentityID and calls
 *       repositoryFolderService.applySettings(ownerID, model, principal).
 *
 * Excluded: @Secured/@RequiredPermission annotations (Spring Security; E2E coverage).
 *
 * Static singletons (OrganizationManager, Catalog) are intercepted with
 * mockStatic() using lenient() to suppress UnnecessaryStubbingException.
 */

import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class RepositoryFolderControllerTest {

   @Mock private RepositoryFolderService repositoryFolderService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private Organization organization;
   @Mock private Catalog catalog;
   @Mock private OrganizationManager orgManager;
   @Mock private Principal principal;
   @Mock private RepositoryFolderSettingsModel settingsModel;
   @Mock private SetRepositoryFolderSettingsModel setSettingsModel;

   private RepositoryFolderController controller;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Catalog> catalogStatic;

   @BeforeEach
   void setUp() {
      controller = new RepositoryFolderController(repositoryFolderService, securityEngine);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());

      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      lenient().when(securityProvider.getOrganization("host-org")).thenReturn(organization);
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("msg");
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      catalogStatic.close();
   }

   // -------------------------------------------------------------------------
   // GET /api/em/content/repository/folder/ — getRepositoryFolderSetting
   // -------------------------------------------------------------------------

   // [invalid org] org lookup returns null → InvalidOrgException; service never called
   @Test
   void getFolderSetting_invalidOrg_throwsInvalidOrgException() throws Exception {
      when(securityProvider.getOrganization("host-org")).thenReturn(null);

      assertThrows(InvalidOrgException.class,
         () -> controller.getRepositoryFolderSetting("/reports", false, null, principal));

      verify(repositoryFolderService, never()).getSettings(anyString(), anyBoolean(), any(), any());
   }

   // [valid org] valid org → service.getSettings() called with parsed ownerID; result returned
   @Test
   void getFolderSetting_validOrg_delegatesToService() throws Exception {
      String owner = new IdentityID("alice", "org").convertToKey();
      when(repositoryFolderService.getSettings(eq("/reports"), eq(false), any(IdentityID.class), eq(principal)))
         .thenReturn(settingsModel);

      RepositoryFolderSettingsModel result =
         controller.getRepositoryFolderSetting("/reports", false, owner, principal);

      assertSame(settingsModel, result);
      verify(repositoryFolderService).getSettings(eq("/reports"), eq(false), any(IdentityID.class), eq(principal));
   }

   // -------------------------------------------------------------------------
   // POST /api/em/content/repository/edit/folder — setRepositoryFolderSettings
   // -------------------------------------------------------------------------

   // [pure delegation] parses owner key to IdentityID; calls applySettings with correct args
   @Test
   void setFolderSettings_delegatesToService() throws Exception {
      String owner = new IdentityID("alice", "org").convertToKey();
      when(repositoryFolderService.applySettings(
         argThat(id -> "alice".equals(id.name)), eq(setSettingsModel), eq(principal)))
         .thenReturn(settingsModel);

      RepositoryFolderSettingsModel result =
         controller.setRepositoryFolderSettings(owner, setSettingsModel, principal);

      assertSame(settingsModel, result);
      verify(repositoryFolderService).applySettings(
         argThat(id -> "alice".equals(id.name)), eq(setSettingsModel), eq(principal));
   }
}
