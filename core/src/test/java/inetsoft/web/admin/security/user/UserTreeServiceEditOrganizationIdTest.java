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
package inetsoft.web.admin.security.user;

/*
 * Bug #77136: renaming an organization to a reserved or unsafe id must be rejected in the EM
 * edit path before any organization property is saved, so a rejected rename leaves nothing
 * behind.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.PropertyModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceEditOrganizationIdTest {
   @BeforeEach
   void setUp() {
      orgA = new FSOrganization(new IdentityID("Org A", "orgA"));

      provider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      when(provider.getOrganizationId("Org A")).thenReturn("orgA");
      when(provider.getOrganization("orgA")).thenReturn(orgA);
      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "orgA" });
      when(securityProvider.getOrganizationNames()).thenReturn(new String[]{ "Org A" });
      when(securityProvider.getOrganization("orgA")).thenReturn(orgA);
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      SystemAdminService systemAdminService = mock(SystemAdminService.class, withSettings().lenient());
      when(systemAdminService.hasSysAdmin(any())).thenReturn(true);
      when(systemAdminService.hasOrgAdmin(any())).thenReturn(true);

      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      orgManagerStatic = mockStatic(OrganizationManager.class,
                                    withSettings().defaultAnswer(CALLS_REAL_METHODS).strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));

      identityService = mock(IdentityService.class);
      principal = mock(Principal.class);

      service = new UserTreeService(
         providerService, systemAdminService, identityService, null, securityEngine, null, null,
         null, null, null, null, null, null, null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      sreeEnvStatic.close();
   }

   @ParameterizedTest
   @ValueSource(strings = { "backup", "Status", "..", "a/b" })
   void renameToReservedOrUnsafeId_rejectedBeforeAnyPropertySave(String newId) throws Exception {
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .name("Org A")
         .oldName("Org A")
         .id(newId)
         .theme(null)
         .properties(List.of(PropertyModel.builder().name("max.row.count").value("10").build()))
         .build();

      MessageException thrown = assertThrows(MessageException.class,
                                             () -> service.editOrganization(model, "Primary", principal));

      assertEquals(Catalog.getCatalog().getString("em.security.reservedOrganizationID", newId),
                   thrown.getMessage());
      sreeEnvStatic.verify(SreeEnv::save, never());
      sreeEnvStatic.verify(() -> SreeEnv.setProperty(anyString(), any(), anyBoolean()), never());
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   private FSOrganization orgA;
   private EditableAuthenticationProvider provider;
   private IdentityService identityService;
   private Principal principal;
   private UserTreeService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
}
