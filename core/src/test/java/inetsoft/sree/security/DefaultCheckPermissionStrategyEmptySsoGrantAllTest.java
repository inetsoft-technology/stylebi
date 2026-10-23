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
package inetsoft.sree.security;

import inetsoft.sree.internal.SUtil;
import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/**
 * Bug #79036, the My Dashboards READ default for a principal with no roles, no groups and no
 * local user holds only while no grant-all permission is configured on My Dashboards (the same
 * condition as the default of a principal with an identity), and a role-less local user keeps
 * the existing default for every action.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class DefaultCheckPermissionStrategyEmptySsoGrantAllTest {
   private static final String ORG = "org0";

   @BeforeAll
   static void before() {
      SUtil.setMultiTenant(true);
   }

   @Test
   void configuredGrantAllDeniesEmptySsoPrincipal() {
      DefaultCheckPermissionStrategy strategy = strategy(false, true);
      SRPrincipal emptySso = principal("ssoUser");

      assertNull(emptySso.createUser());
      assertFalse(strategy.checkPermission(
         emptySso, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ));
   }

   @Test
   void withoutGrantAllEmptySsoPrincipalReadsMyDashboards() {
      assertTrue(strategy(false, false).checkPermission(
         principal("ssoUser"), ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ));
   }

   @Test
   void roleLessLocalUserKeepsExistingDefault() {
      DefaultCheckPermissionStrategy strategy = strategy(true, false);
      SRPrincipal local = principal("localUser");

      for(ResourceAction action : new ResourceAction[]{ ResourceAction.READ, ResourceAction.WRITE }) {
         assertTrue(strategy.checkPermission(
            local, ResourceType.MY_DASHBOARDS, "*", action), action.toString());
      }

      assertFalse(strategy.checkPermission(
         local, ResourceType.REPORT, "Reports", ResourceAction.READ));
   }

   private static SRPrincipal principal(String name) {
      return new SRPrincipal(new IdentityID(name, ORG), new IdentityID[0], new String[0], ORG,
                             Tool.getSecureRandom().nextLong());
   }

   private static DefaultCheckPermissionStrategy strategy(boolean localUser, boolean grantAll) {
      SecurityProvider provider = Mockito.mock(SecurityProvider.class);
      lenient().when(provider.getRoles(any())).thenReturn(new IdentityID[0]);
      lenient().when(provider.getUserGroups(any())).thenReturn(new String[0]);
      lenient().when(provider.getAllGroups(any(IdentityID[].class)))
         .thenAnswer(i -> i.getArgument(0));
      lenient().when(provider.getAllRoles(any(IdentityID[].class)))
         .thenAnswer(i -> i.getArgument(0));
      lenient().when(provider.getAuthenticationProvider())
         .thenReturn(Mockito.mock(AuthenticationProvider.class));
      Organization organization = Mockito.mock(Organization.class);
      lenient().when(organization.getOrganizationID()).thenReturn(ORG);
      lenient().when(organization.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(provider.getOrganization(anyString())).thenReturn(organization);

      if(localUser) {
         IdentityID id = new IdentityID("localUser", ORG);
         User user = Mockito.mock(User.class);
         lenient().when(user.getIdentityID()).thenReturn(id);
         lenient().when(user.getName()).thenReturn("localUser");
         lenient().when(user.getOrganizationID()).thenReturn(ORG);
         lenient().when(user.getGroups()).thenReturn(new String[0]);
         lenient().when(user.getRoles()).thenReturn(new IdentityID[0]);
         lenient().when(provider.getUser(eq(id))).thenReturn(user);
      }

      if(grantAll) {
         Permission permission = new Permission();
         permission.setOrgEditedGrantAll(Map.of(ORG, true));
         lenient().when(provider.getPermission(eq(ResourceType.MY_DASHBOARDS), anyString(),
                                               anyString())).thenReturn(permission);
         lenient().when(provider.getPermission(eq(ResourceType.MY_DASHBOARDS), anyString()))
            .thenReturn(permission);
      }

      return new DefaultCheckPermissionStrategy(provider);
   }
}
