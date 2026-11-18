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

/*
 * Bug #80956: renaming an identity must move the permission keyed by the identity itself.
 */

import inetsoft.sree.security.*;
import inetsoft.util.Tuple4;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class IdentityServiceMovePermissionTest {
   @BeforeEach
   void setUp() {
      AuthorizationChain chain = mock(AuthorizationChain.class);
      provider = mock(AuthorizationProvider.class);
      when(chain.getProviders()).thenReturn(List.of(provider));
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getAuthorizationChain()).thenReturn(Optional.of(chain));
      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
   }

   @Test
   void orgRole_keyMovedAsIsInItsOrg() {
      IdentityID oldID = new IdentityID("r", "orgA");
      IdentityID newID = new IdentityID("r2", "orgA");
      Permission perm = new Permission();
      when(provider.getPermissions()).thenReturn(List.of(
         new Tuple4<>(ResourceType.SECURITY_ROLE, "orgA", oldID.convertToKey(), perm),
         new Tuple4<>(ResourceType.SECURITY_ROLE, "orgA", "other~;~orgA", new Permission())));

      service.moveIdentityPermission(ResourceType.SECURITY_ROLE, oldID, newID);

      verify(provider).setPermission(ResourceType.SECURITY_ROLE, newID, perm, "orgA");
      verify(provider).removePermission(ResourceType.SECURITY_ROLE, oldID, "orgA");
      verify(provider, times(1)).setPermission(
         any(), any(IdentityID.class), any(), anyString());
   }

   @Test
   void globalRole_keyMovedInEveryOrg() {
      IdentityID oldID = new IdentityID("g", null);
      IdentityID newID = new IdentityID("g2", null);
      Permission permA = new Permission();
      Permission permB = new Permission();
      when(provider.getPermissions()).thenReturn(List.of(
         new Tuple4<>(ResourceType.SECURITY_ROLE, "orgA", oldID.convertToKey(), permA),
         new Tuple4<>(ResourceType.SECURITY_ROLE, "orgB", oldID.convertToKey(), permB)));

      service.moveIdentityPermission(ResourceType.SECURITY_ROLE, oldID, newID);

      verify(provider).setPermission(ResourceType.SECURITY_ROLE, newID, permA, "orgA");
      verify(provider).setPermission(ResourceType.SECURITY_ROLE, newID, permB, "orgB");
      verify(provider).removePermission(ResourceType.SECURITY_ROLE, oldID, "orgA");
      verify(provider).removePermission(ResourceType.SECURITY_ROLE, oldID, "orgB");
   }

   @Test
   void sameNameOrOtherType_nothingMoved() {
      IdentityID id = new IdentityID("r", "orgA");
      when(provider.getPermissions()).thenReturn(List.of(
         new Tuple4<>(ResourceType.SECURITY_GROUP, "orgA", id.convertToKey(), new Permission())));

      service.moveIdentityPermission(ResourceType.SECURITY_ROLE, id, id);
      service.moveIdentityPermission(
         ResourceType.SECURITY_ROLE, id, new IdentityID("r2", "orgA"));

      verify(provider, never()).setPermission(any(), any(IdentityID.class), any(), anyString());
      verify(provider, never()).removePermission(any(), any(IdentityID.class), anyString());
   }

   private AuthorizationProvider provider;
   private IdentityService service;
}
