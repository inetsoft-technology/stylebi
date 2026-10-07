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
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.MessageException;
import inetsoft.util.Tuple4;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77798: the permission writers throw when the storage write fails. The identity rename,
 * delete and org migrations that call updateIdentityPermissions have later steps, so it stays
 * best-effort per permission: a failed write is logged, the other permissions are still
 * migrated, and nothing is thrown to the caller.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityServiceUpdatePermissionsWriteFailureTest {
   @Test
   void renameFirstPermissionWriteFails_otherPermissionsStillMigrated() {
      IdentityID oldId = new IdentityID("alice", ORG);
      IdentityID newId = new IdentityID("alice2", ORG);
      List<Tuple4<ResourceType, String, String, Permission>> perms = new ArrayList<>();

      for(String vs : new String[] { "vs1", "vs2", "vs3" }) {
         Permission p = new Permission();
         p.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), ORG);
         perms.add(new Tuple4<>(ResourceType.VIEWSHEET, ORG, vs, p));
      }

      AuthorizationProvider authz = mock(AuthorizationProvider.class);
      when(authz.getPermissions()).thenReturn(perms);
      AtomicInteger writes = new AtomicInteger();
      doAnswer(inv -> {
         if(writes.incrementAndGet() == 1) {
            throw new MessageException("simulated, may not have been saved");
         }

         return null;
      }).when(authz).setPermission(any(ResourceType.class), anyString(), any(), any());

      IdentityService service = newService(authz);

      assertDoesNotThrow(() -> service.updateIdentityPermissions(
         Identity.USER, oldId, newId, ORG, ORG, true));
      assertEquals(perms.size(), writes.get(), "every permission was not attempted");
   }

   @Test
   void deleteRemoveFails_otherPermissionsStillUpdated() {
      IdentityID oldId = new IdentityID("alice", ORG);
      List<Tuple4<ResourceType, String, String, Permission>> perms = new ArrayList<>();

      // permissions on the deleted user and another user, keyed by the identity key
      for(String user : new String[] { "alice", "bob" }) {
         Permission p = new Permission();
         p.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("alice"), ORG);
         perms.add(new Tuple4<>(ResourceType.SECURITY_USER, ORG,
                                new IdentityID(user, ORG).convertToKey(), p));
      }

      AuthorizationProvider authz = mock(AuthorizationProvider.class);
      when(authz.getPermissions()).thenReturn(perms);
      doThrow(new MessageException("simulated, may not have been saved")).when(authz)
         .removePermission(any(ResourceType.class), anyString(), any());

      IdentityService service = newService(authz);

      assertDoesNotThrow(() -> service.updateIdentityPermissions(
         Identity.USER, oldId, null, ORG, ORG, true));
      // the deleted user's own key is only removed (Bug #77834), the other user's is still updated
      verify(authz).removePermission(ResourceType.SECURITY_USER, oldId.convertToKey(), ORG);
      verify(authz, never())
         .setPermission(any(ResourceType.class), eq(oldId.convertToKey()), any(), any());
      verify(authz).setPermission(eq(ResourceType.SECURITY_USER),
                                  eq(new IdentityID("bob", ORG).convertToKey()), any(), any());
   }

   private static IdentityService newService(AuthorizationProvider authz) {
      AuthorizationChain chain = mock(AuthorizationChain.class);
      when(chain.getProviders()).thenReturn(List.of(authz));
      SecurityProvider sp = mock(SecurityProvider.class);
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.getSecurityProvider()).thenReturn(sp);
      when(engine.getAuthorizationChain()).thenReturn(Optional.of(chain));

      IdentityService service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", engine);
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));
      return service;
   }

   private static final String ORG = Organization.getDefaultOrganizationID();
}
