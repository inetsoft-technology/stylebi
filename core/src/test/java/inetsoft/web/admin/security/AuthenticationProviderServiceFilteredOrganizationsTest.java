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
package inetsoft.web.admin.security;

import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78046: the EM organization identity list must key each org by its stored name and id,
 * because the org pane (UserTreeService.getOrganizationModel) rejects a key whose name is not
 * the org's stored name.
 */
@Tag("core")
class AuthenticationProviderServiceFilteredOrganizationsTest {
   private AuthenticationProvider provider;
   private SecurityProvider securityProvider;
   private AuthenticationProviderService service;
   private final Principal principal = mock(Principal.class);

   @BeforeEach
   void setUp() {
      provider = mock(AuthenticationProvider.class);
      securityProvider = mock(SecurityProvider.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(any(Principal.class), eq(ResourceType.SECURITY_ORGANIZATION),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);

      service = spy(new AuthenticationProviderService(securityEngine, null, null, null));
      doReturn(provider).when(service).getProviderByName("p");
   }

   @Test
   void listsEachOrganizationWithItsStoredName() {
      Organization hostOrg = Organization.getDefaultOrganization();
      String hostId = hostOrg.getId();
      when(provider.getOrganizationIDs()).thenReturn(new String[] { "same", "orgA", hostId });
      when(provider.getOrganization("same")).thenReturn(org("same", "same"));
      when(provider.getOrganization("orgA")).thenReturn(org("Renamed", "orgA"));
      when(provider.getOrganization(hostId)).thenReturn(hostOrg);

      List<IdentityID> result = service.getFilteredOrganizations("p", principal);

      assertTrue(result.contains(new IdentityID("Renamed", "orgA")), result.toString());
      assertTrue(result.contains(new IdentityID(hostOrg.getName(), hostId)), result.toString());
      assertTrue(result.contains(new IdentityID("same", "same")), result.toString());
      assertEquals(3, result.size());

      // each key passes the org pane's lookup: getOrganization(orgID).getName() equals name
      for(IdentityID id : result) {
         assertTrue(Tool.equals(provider.getOrganization(id.orgID).getName(), id.name),
                    "key rejected by the org pane: " + id.convertToKey());
      }
   }

   @Test
   void fallsBackToIdWhenOrganizationIsMissing() {
      when(provider.getOrganizationIDs()).thenReturn(new String[] { "gone" });
      when(provider.getOrganization("gone")).thenReturn(null);

      List<IdentityID> result = service.getFilteredOrganizations("p", principal);

      assertEquals(List.of(new IdentityID("gone", "gone")), result);
      verify(provider, never()).getOrgNameFromID(anyString());
   }

   @Test
   void permissionIsStillCheckedOnTheOrganizationId() {
      when(provider.getOrganizationIDs()).thenReturn(new String[] { "orgA", "otherOrg" });
      when(provider.getOrganization("orgA")).thenReturn(org("Renamed", "orgA"));
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            "otherOrg", ResourceAction.ADMIN))
         .thenReturn(false);

      List<IdentityID> result = service.getFilteredOrganizations("p", principal);

      assertEquals(List.of(new IdentityID("Renamed", "orgA")), result);
      verify(securityProvider).checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                               "orgA", ResourceAction.ADMIN);
      verify(provider, never()).getOrganization("otherOrg");
   }

   private static Organization org(String name, String id) {
      return new Organization(name, id, new String[0], "", true);
   }
}
